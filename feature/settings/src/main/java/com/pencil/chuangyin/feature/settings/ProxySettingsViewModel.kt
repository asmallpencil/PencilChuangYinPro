package com.pencil.chuangyin.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.proxy.data.ProxyConfigFetcher
import com.pencil.chuangyin.core.proxy.data.ProxySettingsRepository
import com.pencil.chuangyin.core.proxy.data.ProxyState
import com.pencil.chuangyin.core.proxy.engine.ProxyEngine
import com.pencil.chuangyin.core.proxy.engine.ProxyLogEntry
import com.pencil.chuangyin.core.proxy.model.ClashConfig
import com.pencil.chuangyin.core.proxy.model.ProxyConfigEntry
import com.pencil.chuangyin.core.proxy.model.ProxyImportResult
import com.pencil.chuangyin.core.proxy.model.ProxyMode
import com.pencil.chuangyin.core.proxy.model.ProxyPreferences
import com.pencil.chuangyin.core.proxy.model.ProxyScope
import com.pencil.chuangyin.core.proxy.model.ProxySourceType
import com.pencil.chuangyin.core.proxy.route.RouteDecision
import com.pencil.chuangyin.core.scanner.QrCodeDecoder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 一条一次性提示（导入成功 / 订阅刷新失败等）。
 * 文本在 ViewModel 内按资源格式化，避免界面层为每种来源各写一套映射。
 */
data class ProxyUiMessage(
    val text: String,
    val isError: Boolean = false,
)

/**
 * 「应用内代理」页的 ViewModel。
 *
 * 职责边界：
 * - 仓库（[ProxySettingsRepository]）是唯一数据源，本类不做本地副本；
 * - 所有写操作走仓库，界面立即响应、磁盘异步落盘；
 * - 导入 / 订阅刷新等 I/O 统一在 [viewModelScope] 内串行执行（[busy] 期间拒绝重复触发），
 *   避免并发写配置导致顺序错乱；
 * - 二维码解码与文件读取分别委托 [QrCodeDecoder] 与 [ProxyConfigFetcher]。
 */
@HiltViewModel
class ProxySettingsViewModel @Inject constructor(
    private val repository: ProxySettingsRepository,
    private val engine: ProxyEngine,
    private val fetcher: ProxyConfigFetcher,
    private val qrCodeDecoder: QrCodeDecoder,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val state: StateFlow<ProxyState> = repository.state

    val logs: StateFlow<List<ProxyLogEntry>> = engine.logs

    val latency: StateFlow<Map<String, Long>> = engine.latency

    /** 导入 / 刷新进行中：界面据此禁用入口并展示进度。 */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 最近一次提示；界面展示后调用 [consumeMessage] 清除，避免旋转屏幕后重复弹出。 */
    private val _message = MutableStateFlow<ProxyUiMessage?>(null)
    val message: StateFlow<ProxyUiMessage?> = _message.asStateFlow()

    /** 工具页「路由测试」的结果（只解析路由，不建立连接）。 */
    private val _probe = MutableStateFlow<RouteDecision?>(null)
    val probe: StateFlow<RouteDecision?> = _probe.asStateFlow()

    init {
        // start() 幂等；Application 里已调用，这里兜底保证预览路由能读到最新快照。
        engine.start()
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ---------------- 偏好 ----------------

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setScope(scope: ProxyScope) = update { it.copy(scope = scope) }

    fun setMode(mode: ProxyMode) = update { it.copy(mode = mode) }

    fun setApplyRejectRules(enabled: Boolean) = update { it.copy(applyRejectRules = enabled) }

    fun setProxyPrivateAddresses(enabled: Boolean) = update { it.copy(proxyPrivateAddresses = enabled) }

    fun setGlobalSelection(policy: String) {
        if (policy.isBlank()) return
        viewModelScope.launch { repository.setGlobalSelection(policy) }
    }

    fun selectGroupNode(groupName: String, nodeName: String) {
        if (groupName.isBlank() || nodeName.isBlank()) return
        viewModelScope.launch { repository.selectGroupNode(groupName, nodeName) }
    }

    /** 恢复默认：关闭代理、仅 P 站、规则模式、开启拒绝规则；已导入的配置保留。 */
    fun resetPreferences() = update {
        it.copy(
            enabled = false,
            scope = ProxyScope.PixivOnly,
            mode = ProxyMode.Rule,
            globalSelection = null,
            groupSelections = emptyMap(),
            applyRejectRules = true,
            proxyPrivateAddresses = false,
        )
    }

    // ---------------- 配置管理 ----------------

    fun setActiveConfig(id: String) {
        viewModelScope.launch { repository.setActiveConfig(id) }
    }

    fun renameConfig(id: String, name: String) {
        val trimmed = name.trim()
        if (id.isBlank() || trimmed.isEmpty()) return
        viewModelScope.launch { repository.renameConfig(id, trimmed) }
    }

    fun removeConfig(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch { repository.removeConfig(id) }
    }

    fun clearAllConfigs() {
        viewModelScope.launch {
            repository.clearAllConfigs()
            _probe.value = null
            engine.clearLatency()
        }
    }

    /** 手动粘贴 / 二维码 / 链接的统一入口：自动识别订阅链接。 */
    fun importFromText(rawText: String, name: String? = null) {
        val text = rawText.trim()
        if (text.isEmpty()) {
            notify(context.getString(R.string.settings_proxy_import_empty), isError = true)
            return
        }
        if (!beginBusy()) return
        viewModelScope.launch {
            try {
                if (fetcher.isSubscriptionUrl(text)) {
                    fetchAndAdd(url = text, name = name, source = ProxySourceType.Link)
                } else {
                    notifyResult(repository.addConfig(text, name, ProxySourceType.Manual))
                }
            } finally {
                endBusy()
            }
        }
    }

    /** 从订阅链接导入（明确按链接处理，内容仍是链接时直接下载）。 */
    fun importFromLink(url: String, name: String? = null) {
        val trimmed = url.trim()
        if (!fetcher.isSubscriptionUrl(trimmed)) {
            notify(context.getString(R.string.settings_proxy_link_invalid), isError = true)
            return
        }
        if (!beginBusy()) return
        viewModelScope.launch {
            try {
                fetchAndAdd(url = trimmed, name = name, source = ProxySourceType.Link)
            } finally {
                endBusy()
            }
        }
    }

    fun importFromFile(uri: Uri) {
        if (!beginBusy()) return
        viewModelScope.launch {
            try {
                fetcher.readText(uri).fold(
                    onSuccess = { text -> notifyResult(repository.addConfig(text, null, ProxySourceType.File)) },
                    onFailure = { error ->
                        notify(
                            context.getString(
                                R.string.settings_proxy_file_failed,
                                error.message ?: error.javaClass.simpleName,
                            ),
                            isError = true,
                        )
                    },
                )
            } finally {
                endBusy()
            }
        }
    }

    fun importFromQrCode(uri: Uri) {
        if (!beginBusy()) return
        viewModelScope.launch {
            try {
                val decoded = decodeQr(uri)
                if (decoded.isNullOrBlank()) {
                    notify(context.getString(R.string.settings_proxy_qr_failed), isError = true)
                    return@launch
                }
                val text = decoded.trim()
                if (fetcher.isSubscriptionUrl(text)) {
                    fetchAndAdd(url = text, name = null, source = ProxySourceType.QrCode)
                } else {
                    notifyResult(repository.addConfig(text, null, ProxySourceType.QrCode))
                }
            } finally {
                endBusy()
            }
        }
    }

    fun refreshSubscription(entry: ProxyConfigEntry) {
        val url = entry.subscriptionUrl?.takeIf { it.isNotBlank() } ?: return
        if (!beginBusy()) return
        viewModelScope.launch {
            try {
                fetcher.fetch(url).fold(
                    onSuccess = { body ->
                        when (val result = repository.replaceConfig(entry.id, body)) {
                            is ProxyImportResult.Success ->
                                notify(context.getString(R.string.settings_proxy_refresh_success, result.entry.name))
                            is ProxyImportResult.Failure -> notifyFailure(result, R.string.settings_proxy_refresh_failed)
                        }
                    },
                    onFailure = { error ->
                        notify(
                            context.getString(
                                R.string.settings_proxy_refresh_failed,
                                error.message ?: error.javaClass.simpleName,
                            ),
                            isError = true,
                        )
                    },
                )
            } finally {
                endBusy()
            }
        }
    }

    // ---------------- 测速与日志 ----------------

    fun measure(policy: String) {
        if (policy.isBlank()) return
        viewModelScope.launch { engine.measureLatency(policy) }
    }

    /** 对当前配置里所有组、节点与 DIRECT 并发测速。 */
    fun measureAll() {
        viewModelScope.launch {
            engine.measureAll(policiesForMeasurement(state.value.activeConfig?.config))
        }
    }

    fun clearLatency() = engine.clearLatency()

    fun clearLogs() = engine.clearLogs()

    // ---------------- 路由测试 ----------------

    /** 仪表盘预览：用固定 P 站域名试算，随后再叠加延迟表做展示。 */
    fun previewRoute(): RouteDecision = engine.previewRoute()

    fun testRoute(rawHost: String) {
        val host = normalizeHost(rawHost)
        if (host.isBlank()) {
            _probe.value = null
            return
        }
        _probe.value = engine.routeFor(host)
    }

    fun clearProbe() {
        _probe.value = null
    }

    // ---------------- 内部 ----------------

    private fun update(transform: (ProxyPreferences) -> ProxyPreferences) {
        viewModelScope.launch { repository.updatePreferences(transform) }
    }

    private suspend fun fetchAndAdd(url: String, name: String?, source: ProxySourceType) {
        fetcher.fetch(url).fold(
            onSuccess = { body ->
                notifyResult(repository.addConfig(body, name, source, subscriptionUrl = url))
            },
            onFailure = { error ->
                notify(
                    context.getString(
                        R.string.settings_proxy_import_failed,
                        error.message ?: error.javaClass.simpleName,
                    ),
                    isError = true,
                )
            },
        )
    }

    private fun notifyResult(result: ProxyImportResult) {
        when (result) {
            is ProxyImportResult.Success ->
                notify(context.getString(R.string.settings_proxy_import_success, result.entry.name))
            is ProxyImportResult.Failure -> notifyFailure(result, R.string.settings_proxy_import_failed)
        }
    }

    private fun notifyFailure(result: ProxyImportResult.Failure, formatRes: Int) {
        val detail = buildList {
            add(result.message)
            addAll(result.details)
        }.filter { it.isNotBlank() }.joinToString("；")
        notify(context.getString(formatRes, detail), isError = true)
    }

    private fun notify(text: String, isError: Boolean = false) {
        _message.value = ProxyUiMessage(text = text, isError = isError)
    }

    private fun beginBusy(): Boolean {
        if (_busy.value) {
            notify(context.getString(R.string.settings_proxy_import_busy), isError = false)
            return false
        }
        _busy.value = true
        return true
    }

    private fun endBusy() {
        _busy.value = false
    }

    /** 规范化用户输入：允许粘贴完整 URL，只取主机名参与路由测试。 */
    private fun normalizeHost(raw: String): String = raw.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore(' ')
        .lowercase()

    private fun policiesForMeasurement(config: ClashConfig?): List<String> {
        if (config == null) return listOf(ClashConfig.POLICY_DIRECT)
        val policies = LinkedHashSet<String>()
        policies.add(ClashConfig.POLICY_DIRECT)
        config.groups.forEach { policies.add(it.name) }
        config.nodes.forEach { policies.add(it.name) }
        return policies.toList()
    }

    /** 读取用户选择的图片并解码二维码；读图与解码都不在主线程。 */
    private suspend fun decodeQr(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching null
            try {
                qrCodeDecoder.decode(bitmap)
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }

    /** 按 2 的幂降采样，避免超大照片在解码阶段就耗尽内存。 */
    private fun sampleSize(width: Int, height: Int): Int {
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / sample > MAX_QR_EDGE_PX) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val MAX_QR_EDGE_PX = 1600
    }
}
