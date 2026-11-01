package com.pencil.chuangyin.pro.ui.dev

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.agent.config.AiConfigRepository
import com.pencil.chuangyin.printer.ConnectionResult
import com.pencil.chuangyin.printer.ConnectionState
import com.pencil.chuangyin.printer.PrintOptions
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterConnection
import com.pencil.chuangyin.printer.PrinterController
import com.pencil.chuangyin.printer.PrinterDevice
import com.pencil.chuangyin.printer.PrinterDeviceState
import com.pencil.chuangyin.printer.PrinterScanner
import com.pencil.chuangyin.printer.TextDoc
import com.pencil.chuangyin.printer.WriteOutcome
import com.pencil.chuangyin.core.agent.config.ApiProtocol
import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ToolContext
import com.pencil.chuangyin.core.agent.runtime.ToolRegistry
import com.pencil.chuangyin.core.theme.ThemeRepository
import com.pencil.chuangyin.core.ui.data.GallerySaver
import com.pencil.chuangyin.core.ui.data.OutputPathRepository
import com.pencil.chuangyin.feature.ai.AiPrompts
import com.pencil.chuangyin.feature.ai.tools.Workspace
import com.pencil.chuangyin.pro.OnboardingFlagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 开发者调试页状态：全部调试动作的产物（日志 / 扫描结果 / 探测结果 / 文件列表 / 提示词草稿）。
 */
data class DevToolsUiState(
    val logs: List<String> = emptyList(),
    val scanResults: List<PrinterDevice> = emptyList(),
    val scanning: Boolean = false,
    val probing: Boolean = false,
    val networkResult: String? = null,
    val files: List<String> = emptyList(),
    val promptDraft: String = "",
    val savedOverride: String? = null,
    val busy: Boolean = false,
    val downloadResult: String? = null,
    val toolReport: String? = null,
    val sampleImagePath: String? = null,
    val sampleText: String? = null,
)

/**
 * 开发者调试 ViewModel。
 *
 * 高权限：直接持有打印链路（控制器 + 低层连接 + 扫描）、AI 配置仓库（含内置提示词覆盖）、
 * 主题仓库、引导标记与 AI 工作区，覆盖软件平时对用户隐藏的全部能力。
 */
@HiltViewModel
class DevToolsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val printerController: PrinterController,
    private val printerConnection: PrinterConnection,
    private val printerScanner: PrinterScanner,
    private val aiConfigRepository: AiConfigRepository,
    private val themeRepository: ThemeRepository,
    private val onboardingFlags: OnboardingFlagRepository,
    private val workspace: Workspace,
    private val gallerySaver: GallerySaver,
    private val toolRegistry: ToolRegistry,
    private val outputPathRepository: OutputPathRepository,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = printerController.connectionState
    val deviceState: StateFlow<PrinterDeviceState> = printerController.deviceState
    val themeState = themeRepository.state

    private val _uiState = MutableStateFlow(DevToolsUiState())
    val uiState: StateFlow<DevToolsUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        log("开发者调试已就绪（版本 ${versionName()}，SDK ${Build.VERSION.SDK_INT}）")
        refreshFiles()
        _uiState.update { it.copy(promptDraft = aiConfigRepository.systemPromptOverride.value.orEmpty()) }
        viewModelScope.launch {
            aiConfigRepository.systemPromptOverride.collect { value ->
                _uiState.update { it.copy(savedOverride = value) }
            }
        }
    }

    // region 运行环境

    fun versionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    fun environmentSummary(): String = buildString {
        appendLine("应用：${context.packageName}")
        appendLine("版本：${versionName()}（targetSdk ${context.applicationInfo.targetSdkVersion}）")
        appendLine("系统：Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
        appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("ABI：${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("堆上限：${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB")
        appendLine("私有目录：${context.filesDir.absolutePath}")
        appendLine("工作区：${workspace.writeRoot.absolutePath}")
        appendLine("工作区可用：${formatSize(workspace.writeRoot.usableSpace())}")
        appendLine("外部输出目录已授权：${workspace.usesOutputDir}")
    }

    private fun File.usableSpace(): Long = runCatching { StatFs(absolutePath).availableBytes }.getOrDefault(0L)

    fun workspacePath(): String = workspace.writeRoot.absolutePath

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / (1L shl 30))
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1L shl 20))
        bytes >= 1L shl 10 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / (1L shl 10))
        else -> "$bytes B"
    }

    // endregion

    // region 打印机

    fun bondedPrinters(): List<PrinterDevice> = printerScanner.bondedPrinters()

    fun startScan() {
        if (_uiState.value.scanning) return
        _uiState.update { it.copy(scanning = true, scanResults = emptyList()) }
        log("开始蓝牙扫描…")
        printerScanner.startScan(
            onDeviceFound = { device ->
                _uiState.update { state ->
                    if (state.scanResults.any { it.address == device.address }) {
                        state
                    } else {
                        state.copy(scanResults = state.scanResults + device)
                    }
                }
                log("发现设备 ${device.name} / ${device.address}")
            },
            onScanFinished = {
                _uiState.update { it.copy(scanning = false) }
                log("扫描结束")
            },
            onError = { message ->
                _uiState.update { it.copy(scanning = false) }
                log("扫描失败：$message")
            },
        )
    }

    fun stopScan() {
        printerScanner.stopScan()
        _uiState.update { it.copy(scanning = false) }
        log("已停止扫描")
    }

    fun connect(device: PrinterDevice) {
        log("连接 ${device.name} / ${device.address} …")
        printerController.connect(device.name, device.address) { result ->
            when (result) {
                is ConnectionResult.Success -> log("连接成功：${device.name}")
                is ConnectionResult.Failure -> log("连接失败：${result.code} ${result.detail?.message.orEmpty()}")
            }
        }
    }

    fun disconnect() {
        printerController.disconnect()
        log("已断开连接")
    }

    fun emergencyStop() {
        printerConnection.emergencyStop()
        log("已发送紧急停止")
    }

    fun requestStatus() {
        printerConnection.requestStatus()
        log("已请求设备状态（10 FF 40）")
    }

    fun testFeed() {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = printerController.testFeed()
            _uiState.update { it.copy(busy = false) }
            log("走纸自测：${result.describe()}")
        }
    }

    fun printTestText(text: String, thickness: Int, copies: Int) {
        if (text.isBlank()) {
            log("测试文本为空，已取消")
            return
        }
        val options = runCatching {
            PrintOptions(thickness = thickness, copies = copies)
        }.getOrElse { error ->
            log("打印参数非法：${error.message}")
            return
        }
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = printerController.printDocument(
                doc = TextDoc(title = "开发者调试", content = text),
                typeface = null,
                options = options,
            )
            _uiState.update { it.copy(busy = false) }
            log("测试打印（浓度 $thickness / $copies 份）：${result.describe()}")
        }
    }

    /** 下发十六进制裸指令（空格/逗号/连续串均可），用于验证厂商私有命令。 */
    fun sendHex(hex: String) {
        val bytes = parseHex(hex)
        if (bytes == null || bytes.isEmpty()) {
            log("十六进制解析失败：$hex")
            return
        }
        if (!printerController.isConnected) {
            log("未连接打印机，指令未发送")
            return
        }
        val outcome = printerConnection.write(bytes)
        log(
            when (outcome) {
                is WriteOutcome.Success -> "发送 ${bytes.size} 字节成功：${bytes.joinToString(" ") { "%02X".format(it) }}"
                is WriteOutcome.Failure -> "发送失败：${outcome.reason}"
            },
        )
    }

    private fun parseHex(input: String): ByteArray? = runCatching {
        val cleaned = input.replace(",", " ").replace("0x", "", ignoreCase = true).trim()
        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        val bytes = if (tokens.size == 1) {
            val single = tokens.first()
            require(single.length % 2 == 0)
            single.chunked(2).map { it.toInt(16).toByte() }
        } else {
            require(tokens.all { it.length <= 2 })
            tokens.map { it.toInt(16).toByte() }
        }
        bytes.toByteArray()
    }.getOrNull()

    // endregion

    // region 网络

    /** 对任意 URL 做一次真实 GET 探测（状态码 + 耗时 + 响应片段）。 */
    fun probeNetwork(url: String) {
        val target = url.trim()
        if (target.isEmpty()) {
            log("探测地址为空")
            return
        }
        _uiState.update { it.copy(probing = true, networkResult = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val started = System.currentTimeMillis()
                runCatching {
                    val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                        connectTimeout = 10_000
                        readTimeout = 10_000
                        setRequestProperty("User-Agent", "PencilChuangYinPro-DevTools")
                    }
                    val code = connection.responseCode
                    val stream = if (code >= 400) connection.errorStream else connection.inputStream
                    val snippet = stream?.use { it.readBytes().decodeToString() }
                        ?.take(160)
                        ?.replace("\n", " ")
                        .orEmpty()
                    connection.disconnect()
                    code to snippet
                }.map { (code, snippet) ->
                    val elapsed = System.currentTimeMillis() - started
                    "$target → HTTP $code · ${elapsed}ms\n$snippet"
                }
            }
            _uiState.update { it.copy(probing = false, networkResult = outcome.getOrElse { "探测失败：${it.message}" }) }
            log("网络探测完成：${outcome.getOrNull()?.lineSequence()?.firstOrNull() ?: outcome.exceptionOrNull()?.message}")
        }
    }

    fun probeAiEndpoint() {
        val baseUrl = aiConfigRepository.activeModel.value?.baseUrl
        if (baseUrl.isNullOrBlank()) {
            log("当前没有已激活的 AI 模型配置")
            return
        }
        probeNetwork(baseUrl)
    }

    /** 真实发起一次最小 AI 对话请求，验证协议、鉴权与响应解析链路。 */
    fun probeAiChat() {
        val model = aiConfigRepository.activeModel.value
        if (model == null) {
            log("当前没有已激活的 AI 模型配置")
            return
        }
        if (!model.isUsable) {
            log("AI 配置不完整（baseUrl / modelName 为空）")
            return
        }
        _uiState.update { it.copy(probing = true, networkResult = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val started = System.currentTimeMillis()
                    val (endpoint, headers, body) = when (model.protocol) {
                        ApiProtocol.OPENAI -> Triple(
                            model.chatCompletionsUrl(),
                            mapOf(
                                "Content-Type" to "application/json",
                                "Authorization" to "Bearer ${model.apiKey}",
                            ),
                            """{"model":"${model.modelName}","messages":[{"role":"user","content":"ping"}],"max_tokens":16}""",
                        )
                        ApiProtocol.ANTHROPIC -> Triple(
                            model.anthropicMessagesUrl(),
                            mapOf(
                                "Content-Type" to "application/json",
                                "x-api-key" to model.apiKey,
                                "anthropic-version" to "2023-06-01",
                            ),
                            """{"model":"${model.modelName}","max_tokens":16,"messages":[{"role":"user","content":"ping"}]}""",
                        )
                        ApiProtocol.GEMINI -> Triple(
                            model.geminiGenerateUrl(stream = false),
                            mapOf(
                                "Content-Type" to "application/json",
                                "x-goog-api-key" to model.apiKey,
                            ),
                            """{"contents":[{"parts":[{"text":"ping"}]}]}""",
                        )
                    }
                    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        setRequestProperty("User-Agent", "PencilChuangYinPro-DevTools")
                        headers.forEach { (key, value) -> setRequestProperty(key, value) }
                    }
                    connection.outputStream.use { it.write(body.toByteArray()) }
                    val code = connection.responseCode
                    val stream = if (code >= 400) connection.errorStream else connection.inputStream
                    val snippet = stream?.use { it.readBytes().decodeToString() }
                        ?.take(240)
                        ?.replace("\n", " ")
                        .orEmpty()
                    connection.disconnect()
                    val elapsed = System.currentTimeMillis() - started
                    "$endpoint → HTTP $code · ${elapsed}ms\n$snippet"
                }
            }
            _uiState.update {
                it.copy(
                    probing = false,
                    networkResult = result.getOrElse { "AI 请求失败：${it.message}" },
                )
            }
            log("AI 对话请求：${result.getOrElse { "失败 ${it.message}" }.lineSequence().firstOrNull()}")
        }
    }

    // endregion

    // region 下载 / 保存 / 查看器

    /** 真实下载一个 URL 到工作区，验证网络下载链路与落盘。 */
    fun downloadTest(url: String) {
        val target = url.trim()
        if (target.isEmpty()) {
            log("下载地址为空")
            return
        }
        _uiState.update { it.copy(busy = true, downloadResult = null) }
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                        connectTimeout = 15_000
                        readTimeout = 15_000
                        setRequestProperty("User-Agent", "PencilChuangYinPro-DevTools")
                    }
                    val code = connection.responseCode
                    val stream = if (code >= 400) connection.errorStream else connection.inputStream
                    val bytes = stream?.use { it.readCapped() } ?: ByteArray(0)
                    connection.disconnect()
                    val file = workspace.uniqueTarget("devtools_download_${System.currentTimeMillis()}.bin")
                    workspace.writeBytes(file, bytes)
                    val elapsed = System.currentTimeMillis() - started
                    "HTTP $code · ${bytes.size} 字节 · ${elapsed}ms → ${file.name}"
                }
            }
            _uiState.update {
                it.copy(busy = false, downloadResult = result.getOrElse { "下载失败：${it.message}" })
            }
            log("下载测试：${result.getOrElse { "失败 ${it.message}" }}")
            refreshFiles()
        }
    }

    /** 生成一张样例位图并通过相册保存链路真正落盘。 */
    fun saveSampleToGallery() {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val bitmap = sampleBitmap()
            val saved = withContext(Dispatchers.IO) {
                gallerySaver.save(listOf(bitmap), "devtools_sample_${System.currentTimeMillis()}")
            }
            _uiState.update { it.copy(busy = false) }
            log("相册保存：成功 $saved 张（${bitmap.width}×${bitmap.height}）")
        }
    }

    /** 生成查看器样例（PNG + 文本），供图片/文本查看器现场验证。 */
    fun generateViewerSamples() {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val bitmap = sampleBitmap()
            // 写入私有缓存：无论输出目录是否授权都是真实可访问的文件，查看器必然能打开。
            val file = File(context.cacheDir, "devtools_viewer_sample.png")
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    file.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    file.isFile
                }.getOrDefault(false)
            }
            val sampleText = buildString {
                appendLine("开发者调试 · 文件查看器样例")
                appendLine()
                appendLine("用于验证文本查看器的换行、滚动与编码。")
                appendLine("时间：${timeFormat.format(Date())}")
                appendLine("版本：${versionName()}")
                appendLine()
                repeat(8) { index -> appendLine("第 ${index + 1} 行：Pencil ChuangYin Pro 打印预览文本。") }
            }
            _uiState.update {
                it.copy(
                    busy = false,
                    sampleImagePath = if (ok) file.absolutePath else null,
                    sampleText = sampleText,
                )
            }
            log(if (ok) "查看器样例已生成：${file.name}" else "查看器样例生成失败")
            refreshFiles()
        }
    }

    /** 生成一张 512×512 的调试样例图（白底蓝圆 + DEV 字样）。 */
    fun sampleBitmap(): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(android.graphics.Color.WHITE)
        paint.color = android.graphics.Color.rgb(0x2A, 0x6F, 0xD5)
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("DEV", size / 2f, size / 2f + 17f, paint)
        return bitmap
    }

    /** 限长读取，避免下载测试把超大响应全部读进内存。 */
    private fun java.io.InputStream.readCapped(max: Int = 4 * 1024 * 1024): ByteArray {
        val buffer = ByteArray(max)
        var read = 0
        while (read < max) {
            val count = read(buffer, read, max - read)
            if (count <= 0) break
            read += count
        }
        return buffer.copyOf(read)
    }

    // endregion

    // region 文件

    fun refreshFiles() {
        val root = workspace.writeRoot
        val list = runCatching {
            root.listFiles()?.sortedBy { it.name }?.take(40)?.map { file ->
                val kind = if (file.isDirectory) "目录" else "${formatSize(file.length())}"
                "${file.name}  ·  $kind"
            } ?: emptyList()
        }.getOrDefault(emptyList())
        _uiState.update { it.copy(files = list) }
    }

    /** 写入 → 读取 → 删除 三段式存储自检。 */
    fun runFileRoundTrip() {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(workspace.writeRoot, "devtools_probe.txt")
                    val payload = "devtools probe ${System.currentTimeMillis()}"
                    target.writeText(payload)
                    val read = target.readText()
                    target.delete()
                    if (read == payload) "写入/读取/删除均成功（$payload）" else "读回内容不一致：$read"
                }
            }
            _uiState.update { it.copy(busy = false) }
            log("文件自检：${result.getOrElse { "失败 ${it.message}" }}")
            refreshFiles()
        }
    }

    fun listSubDirectory(name: String) {
        val dir = File(workspace.writeRoot, name)
        if (!dir.isDirectory) {
            log("$name 不是目录")
            return
        }
        refreshFiles()
        log("$name 内文件：${dir.listFiles()?.size ?: 0} 个")
    }

    /** 通过系统文件选择器导入一个文件到工作区，验证文件导入链路。 */
    fun importPickedFile(uri: Uri) {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("无法打开所选文件")
                    workspace.importToWorkspace(stream, queryFileName(uri)).name
                }
            }
            _uiState.update { it.copy(busy = false) }
            log("文件导入：${result.getOrElse { "失败 ${it.message}" }}")
            refreshFiles()
        }
    }

    private fun queryFileName(uri: Uri): String {
        val displayName = runCatching {
            context.contentResolver
                .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        if (!displayName.isNullOrBlank()) return displayName
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "imported_file"
    }

    /** 挂载用户选择的输出目录（SAF 持久授权），验证文件挂载链路。 */
    fun mountOutputTree(uri: Uri) {
        viewModelScope.launch {
            outputPathRepository.setOutputTree(uri.toString())
            log("输出目录已挂载：${outputPathRepository.outputTreeDisplayName()}")
        }
    }

    fun unmountOutputTree() {
        viewModelScope.launch {
            outputPathRepository.clearOutputTree()
            log("已撤销输出目录授权，回退到默认目录 / MediaStore")
        }
    }

    fun mountSummary(): String = buildString {
        val tree = outputPathRepository.outputTreeUri.value
        appendLine("SAF 授权：${if (tree.isNullOrBlank()) "未授权" else "已授权"}")
        if (!tree.isNullOrBlank()) appendLine("URI：$tree")
        appendLine("展示名：${outputPathRepository.outputTreeDisplayName()}")
        appendLine("写入根：${workspace.writeRootPath()}")
        appendLine("实际落点：${if (workspace.usesOutputDir) "授权目录" else "应用私有目录"}")
    }

    // endregion

    // region 工具与接口

    /** 列出全部已注册 AI 工具（名称 / 权限 / 描述），并实调用一次只读工具验证链路。 */
    fun runToolSelfCheck() {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val specs = toolRegistry.specs
            val listing = buildString {
                appendLine("已注册工具：${specs.size} 个")
                specs.forEach { spec ->
                    appendLine("· ${spec.name} [${spec.permission}] ${spec.description.take(48)}")
                }
            }
            val probe = runCatching {
                val tool = toolRegistry.find("list_files") ?: return@runCatching "未注册 list_files，跳过实调用"
                val output = tool.invoke(JsonObject(emptyMap()), probeToolContext)
                "list_files 实调用：${if (output.isError) "失败" else "成功"}\n${output.content.take(200)}"
            }.getOrElse { "实调用异常：${it.message}" }
            _uiState.update { it.copy(busy = false, toolReport = listing + "\n" + probe) }
            log("工具自检：${specs.size} 个已注册工具，list_files 实调用${if (probe.startsWith("list_files 实调用：成功")) "成功" else "未成功"}")
        }
    }

    /** 仅用于调试的自调用上下文（无会话转写、进度直接进日志）。 */
    private val probeToolContext = object : ToolContext {
        override val conversationId: String = "devtools"
        override suspend fun transcript(): List<ChatMessage> = emptyList()
        override fun reportProgress(text: String) {
            log("工具进度：$text")
        }
    }

    // endregion

    // region AI

    fun builtInPrompt(): String = AiPrompts.systemPrompt(
        workspacePath = workspace.writeRootPath(),
        readRootPath = workspace.readRootsPath(),
        printerConnected = printerController.isConnected,
        printerName = currentPrinterName(),
    )

    private fun currentPrinterName(): String =
        (connectionState.value as? ConnectionState.Connected)?.device?.name.orEmpty()

    fun updatePromptDraft(value: String) {
        _uiState.update { it.copy(promptDraft = value) }
    }

    fun savePromptDraft() {
        val draft = _uiState.value.promptDraft
        viewModelScope.launch {
            aiConfigRepository.setSystemPromptOverride(draft)
            log(if (draft.isBlank()) "已恢复内置系统提示词" else "已保存自定义系统提示词（${draft.length} 字）")
        }
    }

    fun clearPromptOverride() {
        _uiState.update { it.copy(promptDraft = "") }
        viewModelScope.launch {
            aiConfigRepository.setSystemPromptOverride(null)
            log("已清除提示词覆盖，恢复内置提示词")
        }
    }

    fun activeModelSummary(): String {
        val model = aiConfigRepository.activeModel.value
        return if (model == null) {
            "未配置模型（前往 设置 → AI 配置）"
        } else {
            "${model.displayName} · ${model.protocol} · ${model.baseUrl}\n模型：${model.modelName}"
        }
    }

    // endregion

    // region UI 与重置

    fun toggleDarkTheme() {
        viewModelScope.launch {
            val current = themeRepository.state.firstOrNull()?.darkTheme ?: false
            themeRepository.setDarkTheme(!current)
            log("深色模式已切换为 ${!current}")
        }
    }

    fun setMotionPreset(id: String) {
        viewModelScope.launch {
            val preset = com.pencil.chuangyin.core.theme.MotionPreset.entries
                .firstOrNull { it.id == id } ?: return@launch
            themeRepository.setMotionPreset(preset)
            log("动效预设 → ${preset.id}")
        }
    }

    fun resetTheme() {
        viewModelScope.launch {
            themeRepository.reset()
            log("主题设置已恢复默认")
        }
    }

    /** 重置引导完成标记与教程标记：下次冷启动会重新进入引导/教程。 */
    fun resetOnboarding() {
        onboardingFlags.reset()
        context.getSharedPreferences("pencil_tutorial_flags", Context.MODE_PRIVATE)
            .edit().clear().apply()
        log("引导与教程标记已清除（冷启动后重新展示）")
    }

    // endregion

    // region 日志

    fun log(message: String) {
        val line = "[${timeFormat.format(Date())}] $message"
        _uiState.update { state ->
            state.copy(logs = (state.logs + line).takeLast(120))
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    // endregion

    override fun onCleared() {
        printerScanner.stopScan()
    }

    private fun PrintResult.describe(): String = when (this) {
        is PrintResult.Success -> "成功"
        is PrintResult.Error -> "失败 ${code}${detail?.let { "（$it）" } ?: ""}"
    }
}
