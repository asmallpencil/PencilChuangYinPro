package com.pencil.chuangyin.core.proxy.core

import android.content.Context
import com.pencil.chuangyin.core.proxy.model.ClashConfig
import com.pencil.chuangyin.core.proxy.model.ProxyNode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内置 sing-box 内核的进程管理。
 *
 * 为什么走「独立进程」而不是 JNI：sing-box 官方只发布可执行文件，Android 上把二进制
 * 放进 `jniLibs` 后由系统解压到 `nativeLibraryDir`（该目录允许 exec），
 * 以子进程形式运行即可，进程随本应用退出/关闭代理一起回收；它不注册 VpnService、
 * 不修改系统路由，因此只影响本应用自身。
 *
 * 每个节点分配一个回环 SOCKS 端口，路由仍由 [com.pencil.chuangyin.core.proxy.route.ProxyRouter] 决定。
 */
@Singleton
class SingBoxCore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val lock = Any()

    private val outputLines: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Volatile
    private var process: Process? = null

    @Volatile
    private var ports: Map<String, Int> = emptyMap()

    @Volatile
    private var signature: String? = null

    @Volatile
    private var lastFailureAtMillis: Long = 0L

    @Volatile
    private var failureSignature: String? = null

    /** 最近一次启动失败的内核输出，供设置页排查（只在失败时非空）。 */
    @Volatile
    var lastError: String? = null
        private set

    /** 内核进程是否存活（端口只监听回环地址，仅本应用可访问）。 */
    val isRunning: Boolean
        get() = process?.isAlive == true

    /**
     * 确保内核按 [config] 运行，返回「节点名 → 本地 SOCKS 端口」。
     *
     * 配置未变时直接返回缓存；配置变化会重启进程。任何失败都返回 null，
     * 由调用方按「失败关闭」处理（绝不静默直连）。
     */
    fun ensureRunning(config: ClashConfig?): Map<String, Int>? {
        val nodes = config?.nodes ?: return null
        val candidates = nodes.filter { it.protocol.handledByCore && it.isSupported }
        if (candidates.isEmpty()) return null
        val key = signatureOf(nodes)
        synchronized(lock) {
            if (key == signature && process?.isAlive == true) return ports
            val now = System.currentTimeMillis()
            if (key == failureSignature && now - lastFailureAtMillis < FAILURE_BACKOFF_MS) {
                return null
            }
            stopLocked()
            val result = startLocked(candidates, key)
            if (result == null) {
                failureSignature = key
                lastFailureAtMillis = System.currentTimeMillis()
            } else {
                failureSignature = null
                lastError = null
            }
            return result
        }
    }

    /** 关闭内核进程（代理关闭 / 切换配置时调用）。 */
    fun stop() {
        synchronized(lock) {
            stopLocked()
            signature = null
        }
    }

    // ---------------- 启动 ----------------

    private fun startLocked(candidates: List<ProxyNode>, key: String): Map<String, Int>? {
        val executable = resolveExecutable()
        if (executable == null) {
            lastError = "当前设备架构未打包 sing-box 内核（目前仅含 arm64-v8a）"
            return null
        }
        val workDir = File(context.filesDir, WORK_DIR_NAME).apply { mkdirs() }
        var startPort = BASE_PORT
        repeat(MAX_PORT_ATTEMPTS) { attempt ->
            val built = SingBoxConfigBuilder.build(candidates, startPort)
            if (built.ports.isEmpty()) {
                lastError = "节点缺少必需的认证字段，无法生成内核配置"
                return null
            }
            val configFile = writeConfig(workDir, built.json)
            if (configFile == null) {
                lastError = "写入内核配置失败"
                return null
            }
            val launched = try {
                ProcessBuilder(executable.absolutePath, "run", "-c", configFile.absolutePath)
                    .directory(workDir)
                    .redirectErrorStream(true)
                    .apply { environment()["HOME"] = context.filesDir.absolutePath }
                    .start()
            } catch (t: Throwable) {
                lastError = "内核启动失败：${t.message.orEmpty()}"
                stopLocked()
                return null
            }
            process = launched
            ports = built.ports
            signature = key
            drainOutput(launched)
            if (awaitReady(built.ports.values)) return built.ports
            // 端口被占用或内核自我退出：换一段端口再试。
            stopLocked()
            startPort += PORT_STEP
            if (attempt == MAX_PORT_ATTEMPTS - 1) {
                lastError = "sing-box 内核未能监听本地端口：${recentOutput()}"
            }
        }
        return null
    }

    private fun resolveExecutable(): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir, EXECUTABLE_NAME)
        if (nativeDir.exists()) return nativeDir
        return File(context.filesDir, EXECUTABLE_NAME).takeIf { it.exists() }
    }

    private fun writeConfig(workDir: File, json: String): File? = runCatching {
        val target = File(workDir, CONFIG_FILE_NAME)
        val temp = File(workDir, "$CONFIG_FILE_NAME.tmp")
        temp.writeText(json)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
        target
    }.getOrNull()

    /** 等内核开始监听（探测前几个端口），避免连接在启动窗口内被拒绝。 */
    private fun awaitReady(ports: Collection<Int>): Boolean {
        val deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS
        val probes = ports.take(READY_PROBE_PORTS)
        while (System.currentTimeMillis() < deadline) {
            if (process?.isAlive != true) return false
            if (probes.all { isListening(it) }) return true
            Thread.sleep(READY_POLL_INTERVAL_MS)
        }
        return process?.isAlive == true
    }

    private fun isListening(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(LISTEN_ADDRESS, port), PROBE_TIMEOUT_MS)
        }
        true
    }.getOrDefault(false)

    /** 持续读取内核输出，避免管道写满阻塞子进程；同时留一小段用于失败排查。 */
    private fun drainOutput(launched: Process) {
        outputLines.clear()
        val thread = Thread({
            runCatching {
                launched.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (outputLines.size >= MAX_OUTPUT_LINES) outputLines.removeAt(0)
                        outputLines += line
                    }
                }
            }
        }, "sing-box-log")
        thread.isDaemon = true
        thread.start()
    }

    private fun recentOutput(): String =
        outputLines.joinToString(" | ").take(MAX_ERROR_TEXT_LENGTH).ifBlank { "无输出" }

    private fun stopLocked() {
        val current = process
        process = null
        ports = emptyMap()
        signature = null
        if (current != null) {
            runCatching { current.destroy() }
            runCatching {
                if (!current.waitFor(STOP_GRACE_MS, TimeUnit.MILLISECONDS)) current.destroyForcibly()
            }
        }
    }

    private fun signatureOf(nodes: List<ProxyNode>): String =
        // 配置对象在每次状态刷新时整体重建，「列表实例 + 长度」足以区分版本。
        "${System.identityHashCode(nodes)}:${nodes.size}"

    private companion object {
        const val EXECUTABLE_NAME = "libsingbox.so"
        const val WORK_DIR_NAME = "singbox"
        const val CONFIG_FILE_NAME = "config.json"
        const val LISTEN_ADDRESS = "127.0.0.1"
        const val BASE_PORT = 28900
        const val PORT_STEP = 1000
        const val MAX_PORT_ATTEMPTS = 3
        const val STARTUP_TIMEOUT_MS = 3_000L
        const val READY_PROBE_PORTS = 3
        const val READY_POLL_INTERVAL_MS = 40L
        const val PROBE_TIMEOUT_MS = 60
        const val STOP_GRACE_MS = 800L
        const val FAILURE_BACKOFF_MS = 60_000L
        const val MAX_OUTPUT_LINES = 40
        const val MAX_ERROR_TEXT_LENGTH = 400
    }
}
