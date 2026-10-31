package com.pencil.chuangyin.core.proxy.engine

import com.pencil.chuangyin.core.proxy.bridge.LocalSocks5Bridge
import com.pencil.chuangyin.core.proxy.core.SingBoxCore
import com.pencil.chuangyin.core.proxy.data.ProxySettingsRepository
import com.pencil.chuangyin.core.proxy.data.ProxyState
import com.pencil.chuangyin.core.proxy.model.ProxyConfigEntry
import com.pencil.chuangyin.core.proxy.model.ProxyMode
import com.pencil.chuangyin.core.proxy.model.ProxyNode
import com.pencil.chuangyin.core.proxy.model.ProxyPreferences
import com.pencil.chuangyin.core.proxy.model.ProxyScope
import com.pencil.chuangyin.core.proxy.route.Outbound
import com.pencil.chuangyin.core.proxy.route.ProxyRouter
import com.pencil.chuangyin.core.proxy.route.RouteDecision
import com.pencil.chuangyin.core.proxy.transport.OutboundTransportRegistry
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 代理节点的用户名 / 密码。 */
data class ProxyCredential(val username: String, val password: String)

/**
 * 应用内代理引擎。
 *
 * 职责：
 * - 把偏好 + 配置 + 规则解析为 [java.net.Proxy]，供 [AppProxySelector] 查询；
 * - 提供 SOCKS5 账号密码（通过进程内 [Authenticator]）与 HTTP 代理 407 认证；
 * - 记录连接日志、测速，供设置页仪表盘 / 工具页展示。
 *
 * 引擎本身不监听端口、不创建 VPN：它只影响本进程里显式安装了自己的 OkHttp 客户端
 * 与默认 ProxySelector，因此绝不会影响应用之外的流量。
 */
@Singleton
class ProxyEngine @Inject constructor(
    private val repository: ProxySettingsRepository,
    private val singBoxCore: SingBoxCore,
) {

    private val router = ProxyRouter()
    private val transports = OutboundTransportRegistry()

    /**
     * Shadowsocks / Trojan 无法用 java.net.Proxy 表达，统一走本地 SOCKS5 桥接。
     * 桥接只监听回环地址，仅本进程可达。
     */
    private val bridge: LocalSocks5Bridge by lazy {
        LocalSocks5Bridge { host, port -> routeFor(host, port) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _logs = MutableStateFlow<List<ProxyLogEntry>>(emptyList())
    val logs: StateFlow<List<ProxyLogEntry>> = _logs.asStateFlow()

    private val _latency = MutableStateFlow<Map<String, Long>>(emptyMap())
    val latency: StateFlow<Map<String, Long>> = _latency.asStateFlow()

    private val latencyValues = ConcurrentHashMap<String, Long>()
    private val lastLogAtMillis = ConcurrentHashMap<String, Long>()
    private val logIds = AtomicLong(0)

    @Volatile
    private var snapshot = ProxyState()

    @Volatile
    private var started = false

    /** 供 OkHttp / 系统默认 ProxySelector 使用的选择器（单例，避免重复实例）。 */
    val selector: ProxySelector by lazy { AppProxySelector(this) }

    /** 应用启动时调用一次：开始跟随仓库状态，并安装 SOCKS5 认证器。 */
    fun start() {
        if (started) return
        started = true
        installSocksAuthenticator()
        scope.launch {
            repository.state.collect { state ->
                snapshot = state
                // 关闭代理时顺手停掉本地桥接与内核，避免长期占用端口与内存。
                if (!state.preferences.enabled) {
                    bridge.stop()
                    singBoxCore.stop()
                }
            }
        }
    }

    // ---------------- 查询 ----------------

    fun preferences(): ProxyPreferences = snapshot.preferences

    fun activeConfig(): ProxyConfigEntry? = snapshot.activeConfig

    /** 把域名解析成路由决策（不产生连接）。 */
    fun routeFor(host: String, port: Int? = null): RouteDecision =
        router.resolve(
            preferences = snapshot.preferences,
            config = snapshot.activeConfig?.config,
            host = host,
            port = port,
            latencyOf = { name -> latencyValues[name] },
        )

    /**
     * OkHttp / ProxySelector 的入口。
     *
     * 出口不可用时返回指向本机丢弃端口的 Proxy（**失败关闭**），
     * 而不是 `NO_PROXY` —— 后者会在代理失效时静默直连，泄露真实地址。
     */
    fun proxyFor(host: String, port: Int? = null): List<Proxy> {
        val decision = routeFor(host, port)
        recordRoute(host, decision)
        return when (val outbound = decision.outbound) {
            Outbound.Direct -> listOf(Proxy.NO_PROXY)
            Outbound.Reject -> listOf(rejectProxy())
            is Outbound.Node -> {
                val proxy = proxyForNode(outbound.node)
                if (proxy != null) listOf(proxy) else listOf(rejectProxy())
            }
            is Outbound.Unavailable -> listOf(rejectProxy())
        }
    }

    /**
     * 节点 → java.net.Proxy。
     *
     * HTTP / SOCKS5 直接用系统实现；其余协议交给内置 sing-box 内核（每节点一个回环 SOCKS 端口）。
     * 内核不可用时（例如 32 位设备），SS / Trojan 回退到进程内手写协议栈桥接。
     * 返回 null 表示该节点无法承载流量（调用方必须失败关闭，不能静默直连）。
     */
    fun proxyForNode(node: ProxyNode): Proxy? {
        transports.createProxy(node)?.let { return it }
        if (!node.isSupported) return null
        if (node.protocol.handledByCore) {
            val corePort = singBoxCore.ensureRunning(snapshot.activeConfig?.config)?.get(node.name)
            if (corePort != null) {
                return Proxy(Proxy.Type.SOCKS, InetSocketAddress(BRIDGE_HOST, corePort))
            }
        }
        if (!node.protocol.requiresBridge) return null
        val port = bridge.ensureStarted() ?: return null
        return Proxy(Proxy.Type.SOCKS, InetSocketAddress(BRIDGE_HOST, port))
    }

    /** HTTP 代理 407 认证用：按代理地址反查当前配置里的账号密码。 */
    fun credentialsFor(host: String, port: Int): ProxyCredential? {
        val nodes: List<ProxyNode> = snapshot.activeConfig?.config?.nodes ?: return null
        val node = nodes.firstOrNull { candidate ->
            candidate.server.equals(host, ignoreCase = true) &&
                (port <= 0 || candidate.port == port) &&
                !candidate.username.isNullOrEmpty()
        } ?: return null
        val username = node.username ?: return null
        return ProxyCredential(username, node.password.orEmpty())
    }

    /** 仪表盘「当前出口」预览：用一个 P 站域名试算，不影响真实请求。 */
    fun previewRoute(): RouteDecision = routeFor("www.pixiv.net")

    fun onConnectFailed(host: String?, error: Throwable?) {
        if (host.isNullOrBlank()) return
        val state = snapshot
        if (!state.preferences.enabled) return
        val key = "fail|$host"
        val now = System.currentTimeMillis()
        val last = lastLogAtMillis[key] ?: 0L
        if (now - last < FAILURE_THROTTLE_MS) return
        lastLogAtMillis[key] = now
        appendLog(
            ProxyLogEntry(
                id = logIds.incrementAndGet(),
                timeMillis = now,
                host = host,
                policy = "-",
                detail = "连接失败：${error?.message ?: "未知原因"}",
                kind = ProxyLogKind.Failed,
            ),
        )
    }

    // ---------------- 测速 ----------------

    /** 测试单个策略（节点 / 组 / DIRECT）。返回毫秒，失败为 null。 */
    suspend fun measureLatency(policy: String): Long? {
        val value = probePolicy(policy)
        updateLatency(policy, value)
        return value
    }

    /** 并发测试一批策略（节点 + 组 + DIRECT），一次性刷新延迟表。 */
    suspend fun measureAll(policies: Collection<String>): Map<String, Long> = coroutineScope {
        val distinct = policies.filter { it.isNotBlank() }.distinct()
        val semaphore = Semaphore(MAX_CONCURRENT_TESTS)
        distinct.map { policy ->
            async(Dispatchers.IO) {
                semaphore.withPermit { policy to probePolicy(policy) }
            }
        }.awaitAll().forEach { (policy, value) ->
            if (value != null) latencyValues[policy] = value else latencyValues.remove(policy)
        }
        _latency.value = latencyValues.toMap()
        latencyValues.toMap()
    }

    fun clearLatency() {
        latencyValues.clear()
        _latency.value = emptyMap()
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private suspend fun probePolicy(policy: String): Long? = withContext(Dispatchers.IO) {
        val decision = measurementDecision(policy)
        when (val outbound = decision.outbound) {
            is Outbound.Node -> {
                val proxy = proxyForNode(outbound.node) ?: return@withContext null
                probe(proxy)
            }
            Outbound.Direct -> probe(Proxy.NO_PROXY)
            else -> null
        }
    }

    private fun measurementDecision(policy: String): RouteDecision = router.resolve(
        preferences = snapshot.preferences.copy(
            enabled = true,
            scope = ProxyScope.Global,
            mode = ProxyMode.Global,
            globalSelection = policy,
            proxyPrivateAddresses = false,
        ),
        config = snapshot.activeConfig?.config,
        host = LATENCY_TEST_HOST,
        latencyOf = { name -> latencyValues[name] },
    )

    private fun probe(proxy: Proxy): Long? {
        val client = OkHttpClient.Builder()
            .proxy(proxy)
            .proxyAuthenticator(ProxyHttpAuthenticator(this))
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(PROBE_TIMEOUT_SECONDS + 2, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(LATENCY_TEST_URL)
            .header("User-Agent", "PencilChuangYin-ProxyProbe")
            .get()
            .build()
        val startedAt = System.currentTimeMillis()
        return runCatching {
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 300..399
            }
        }.getOrNull()?.let { System.currentTimeMillis() - startedAt }
    }

    private fun updateLatency(policy: String, value: Long?) {
        if (value != null) latencyValues[policy] = value else latencyValues.remove(policy)
        _latency.value = latencyValues.toMap()
    }

    // ---------------- 日志 ----------------

    private fun recordRoute(host: String, decision: RouteDecision) {
        val prefs = snapshot.preferences
        if (!prefs.enabled) return
        val kind = when (decision.outbound) {
            Outbound.Direct -> ProxyLogKind.Direct
            Outbound.Reject -> ProxyLogKind.Rejected
            is Outbound.Node -> ProxyLogKind.Proxied
            is Outbound.Unavailable -> ProxyLogKind.Rejected
        }
        // 纯直连且没有规则命中时不记录：图片流会把日志刷满，价值很低。
        if (kind == ProxyLogKind.Direct && decision.rule == null) return
        val key = "$host|${decision.policy}|$kind"
        val now = System.currentTimeMillis()
        val last = lastLogAtMillis[key] ?: 0L
        if (now - last < LOG_THROTTLE_MS) return
        lastLogAtMillis[key] = now
        val detail = when (val outbound = decision.outbound) {
            is Outbound.Unavailable -> "${decision.detail}（${outbound.reason}）"
            else -> decision.detail
        }
        appendLog(
            ProxyLogEntry(
                id = logIds.incrementAndGet(),
                timeMillis = now,
                host = host,
                policy = decision.policy.ifBlank { "-" },
                detail = detail,
                kind = kind,
            ),
        )
    }

    private fun appendLog(entry: ProxyLogEntry) {
        _logs.update { current -> (listOf(entry) + current).take(MAX_LOG_ENTRIES) }
    }

    // ---------------- 认证 ----------------

    private fun installSocksAuthenticator() {
        // SOCKS5 的用户名/密码由 JDK 的 Socket 实现通过全局 Authenticator 询问；
        // 仅在 requestorType == PROXY 时响应，普通站点认证不受影响。
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                if (requestorType != Authenticator.RequestorType.PROXY) return null
                val host = requestingHost ?: return null
                val credential = credentialsFor(host, requestingPort) ?: return null
                return PasswordAuthentication(credential.username, credential.password.toCharArray())
            }
        })
    }

    private fun rejectProxy(): Proxy =
        Proxy(Proxy.Type.HTTP, InetSocketAddress(REJECT_HOST, REJECT_PORT))

    companion object {
        /** 默认测速地址（gstatic 204，Clash 生态通用）。 */
        const val LATENCY_TEST_URL = "http://www.gstatic.com/generate_204"
        private const val LATENCY_TEST_HOST = "www.gstatic.com"
        private const val MAX_LOG_ENTRIES = 120
        private const val MAX_CONCURRENT_TESTS = 8
        private const val PROBE_TIMEOUT_SECONDS = 4L
        private const val LOG_THROTTLE_MS = 1200L
        private const val FAILURE_THROTTLE_MS = 3000L
        private const val REJECT_HOST = "127.0.0.1"
        private const val REJECT_PORT = 1
        private const val BRIDGE_HOST = "127.0.0.1"
    }
}
