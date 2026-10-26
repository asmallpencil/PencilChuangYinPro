package com.pencil.chuangyin.desktop.text

import com.pencil.chuangyin.desktop.printer.ConnectionState
import com.pencil.chuangyin.desktop.printer.PrinterConnectionStore
import com.pencil.chuangyin.desktop.printer.PrinterController
import com.pencil.chuangyin.desktop.printer.TextPrintRunner
import com.pencil.chuangyin.desktop.support.OutputPathStore
import java.io.File
import java.text.Collator
import java.util.Locale
import java.util.prefs.Preferences
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

/** 已挂载的 TXT 文件（内容不进入输入框，仅呈现名称与字数，并保留导入先后顺序）。 */
data class MountedTxtFile(
    val name: String,
    val charCount: Int,
)

/** 一次性轻提示事件（文案在 UI 层映射，Store 不持有硬编码文案）。 */
sealed interface TextNotice {
    data class Mounted(val count: Int, val chars: Int) : TextNotice
    data object ArchiveEmpty : TextNotice
    data class Saved(val count: Int) : TextNotice
    data object SaveFailed : TextNotice
}

/** 批量开关开启时的默认份数（与安卓端 DEFAULT_COPIES 一致）。 */
private const val DEFAULT_COPIES = 2

/** 文本打印页面状态（字段与安卓端 TextEditorUiState 一一对应）。 */
data class TextEditorUiState(
    val doc: TextDoc = TextDoc(),
    /** 已连接打印机名称（镜像 [PrinterConnectionStore.connectionState]，未连接为 null）。 */
    val connectedName: String? = null,
    val isConnecting: Boolean = false,
    val isPrinting: Boolean = false,
    val progress: PrintProgress? = null,
    val lastResult: PrintResult? = null,
    val customFontName: String? = null,
    val customFontPath: String? = null,
    val printerStatus: PrinterStatus? = null,
    val overheatActive: Boolean = false,
    val options: PrintOptions = PrintOptions(),
    /** 与打印管线同一渲染器产出的 WYSIWYG 预览图（384 点宽，仅顶部有界区域）。 */
    val previewImage: Image? = null,
    val mountedFiles: List<MountedTxtFile> = emptyList(),
    val totalChars: Int = 0,
    val isDocTooLargeForPreview: Boolean = false,
    val editorExpanded: Boolean = false,
    val notice: TextNotice? = null,
    val batchEnabled: Boolean = false,
    val copies: Int = DEFAULT_COPIES,
    val isSaving: Boolean = false,
)

/**
 * 文本打印状态仓库 —— 取代安卓端 `TextEditorViewModel`（桌面无 Hilt / Lifecycle）：
 * 单例 + StateFlow + 自有协程作用域，方法签名与语义逐条对齐。
 *
 * 平台映射：
 *  - `Uri + ContentResolver` → `File`（JFileChooser 选文件）；
 *  - `FontStorage + SharedPreferences` → Preferences + 本机字体目录；
 *  - `GallerySaver` → `OutputPathStore`（PNG 落到输出目录）；
 *  - `PrinterController` → 桌面端 [PrinterController] + [TextPrintRunner]（RFCOMM / SPP 蓝牙通道）。
 */
object TextEditorStore {

    private const val PREVIEW_DEBOUNCE_MS = 200L
    private const val PREVIEW_CHAR_LIMIT = 10_000
    private const val PREVIEW_MAX_HEIGHT_PX = 1_500

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<TextEditorUiState> = _state.asStateFlow()

    private var previewJob: Job? = null

    /** 挂载文件正文：与 [TextEditorUiState.mountedFiles] 下标一一对应，刻意留在 StateFlow 之外。 */
    private val mountedTexts: MutableList<String> = mutableListOf()

    init {
        observePrinter()
        schedulePreview()
    }

    /** 镜像打印机的连接态 / 故障态 / 状态事件（与安卓端 TextEditorViewModel 一致）。 */
    private fun observePrinter() {
        scope.launch {
            PrinterConnectionStore.connectionState.collect { connection ->
                _state.update {
                    it.copy(
                        connectedName = (connection as? ConnectionState.Connected)?.device?.displayName,
                        isConnecting = connection is ConnectionState.Connecting,
                    )
                }
            }
        }
        scope.launch {
            PrinterConnectionStore.deviceState.collect { device ->
                _state.update { it.copy(overheatActive = PrinterStatus.Overheat in device.activeStatuses) }
            }
        }
        scope.launch {
            PrinterConnectionStore.statusEvents.collect { status ->
                _state.update { it.copy(printerStatus = status) }
            }
        }
    }

    // ---- 文档编辑 ----

    fun updateTitle(value: String) = mutateDoc { it.copy(title = value) }

    fun updateContent(value: String) {
        _state.update {
            it.copy(doc = it.doc.copy(content = value), totalChars = effectiveChars(value, mountedTexts))
        }
        schedulePreview()
    }

    fun updateFontSize(value: Float) = mutateDoc {
        it.copy(fontSizeSp = value.coerceIn(TextDoc.FONT_SIZE_RANGE))
    }

    fun updateTitleSize(value: Float) = mutateDoc {
        it.copy(titleSizeSp = value.coerceIn(TextDoc.TITLE_SIZE_RANGE))
    }

    fun updateLineSpacing(value: Float) = mutateDoc {
        it.copy(lineSpacingMultiplier = value.coerceIn(TextDoc.MIN_LINE_SPACING, TextDoc.MAX_LINE_SPACING))
    }

    fun updateAlign(align: PrintAlign) = mutateDoc { it.copy(align = align) }

    fun updateBold(value: Boolean) = mutateDoc { it.copy(bold = value) }

    /** 外部传入文本（P 站小说「打印」跳转）：一次性灌入标题+正文。 */
    fun transferText(title: String, content: String) {
        mountedTexts.clear()
        _state.update {
            it.copy(
                doc = it.doc.copy(title = title, content = content),
                mountedFiles = emptyList(),
                totalChars = content.length,
                isDocTooLargeForPreview = content.length >= PREVIEW_CHAR_LIMIT,
            )
        }
        schedulePreview()
    }

    private inline fun mutateDoc(transform: (TextDoc) -> TextDoc) {
        _state.update {
            val newDoc = transform(it.doc)
            it.copy(doc = newDoc, totalChars = effectiveChars(newDoc.content, mountedTexts))
        }
        schedulePreview()
    }

    fun setEditorExpanded(expanded: Boolean) {
        _state.update { it.copy(editorExpanded = expanded) }
    }

    // ---- 打印参数 ----

    fun updateThickness(value: Int) = updateOptions { it.copy(thickness = value) }

    fun updateFeedAfter(value: Int) = updateOptions { it.copy(feedAfter = value) }

    fun setBatchEnabled(enabled: Boolean) = _state.update { it.copy(batchEnabled = enabled) }

    fun setCopies(value: Int) = _state.update { it.copy(copies = value.coerceIn(PrintOptions.COPIES_RANGE)) }

    private inline fun updateOptions(transform: (PrintOptions) -> PrintOptions) {
        _state.update { it.copy(options = transform(it.options)) }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    /** 保存预览图（PNG）到输出目录，所见即所得。 */
    fun savePreview() {
        val snapshot = _state.value
        val image = snapshot.previewImage ?: return
        if (snapshot.isSaving) return
        scope.launch {
            _state.update { it.copy(isSaving = true) }
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = TextPreviewRenderer.encodePng(image) ?: return@runCatching false
                    val dir = OutputPathStore.ensureDir() ?: return@runCatching false
                    val file = File(dir, "pencil_text_${System.currentTimeMillis()}.png")
                    file.writeBytes(bytes)
                    true
                }.getOrDefault(false)
            }
            _state.update {
                it.copy(
                    isSaving = false,
                    notice = if (saved) TextNotice.Saved(1) else TextNotice.SaveFailed,
                )
            }
        }
    }

    // ---- 导入 TXT：支持多选，按选择先后顺序挂载 ----

    fun importTxt(files: List<File>) {
        if (files.isEmpty()) return
        scope.launch {
            val appended = ArrayList<MountedTxtFile>()
            val appendedTexts = ArrayList<String>()
            withContext(Dispatchers.IO) {
                files.forEach { file ->
                    val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@forEach
                    appended += MountedTxtFile(name = file.name.ifBlank { "TXT 文档" }, charCount = text.length)
                    appendedTexts += text
                }
            }
            appendMounted(appended, appendedTexts, appended.size)
        }
    }

    /**
     * 导入 ZIP 压缩包：自动扫描根目录所有 TXT，按首字母/数字/拼音音序排序后挂载。
     * 中文 ZIP 条目名常见 GBK 编码，UTF-8 失败时自动回退 GBK 重试。
     */
    fun importTxtArchive(file: File) {
        scope.launch {
            val entries = withContext(Dispatchers.IO) {
                runCatching { readArchiveEntries(file) }.getOrNull().orEmpty()
            }
            val txts = entries
                .filter { isRootTxt(it.name) }
                .sortedWith(compareBy(NAME_ORDER) { it.name })
            if (txts.isEmpty()) {
                _state.update { it.copy(notice = TextNotice.ArchiveEmpty) }
                return@launch
            }
            val mounted = txts.map { MountedTxtFile(name = baseName(it.name), charCount = it.text.length) }
            appendMounted(mounted, txts.map { it.text }, mounted.size)
        }
    }

    private data class ArchiveEntry(val name: String, val text: String)

    private fun readArchiveEntries(file: File): List<ArchiveEntry> {
        val bytes = file.readBytes()
        // 第一次按 UTF-8；若没有可读 TXT（条目名乱码），再按 GBK 重试。
        val utf8 = decodeZip(bytes, Charsets.UTF_8)
        val hasReadableTxt = utf8.any { isRootTxt(it.name) && !it.name.contains('\uFFFD') }
        return if (hasReadableTxt) utf8 else decodeZip(bytes, GBK_CHARSET).ifEmpty { utf8 }
    }

    private fun decodeZip(bytes: ByteArray, charset: java.nio.charset.Charset): List<ArchiveEntry> =
        runCatching {
            ZipInputStream(bytes.inputStream(), charset).use { zis ->
                val out = ArrayList<ArchiveEntry>()
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val text = zis.bufferedReader(Charsets.UTF_8).readText()
                        out += ArchiveEntry(entry.name ?: "", text)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                out
            }
        }.getOrDefault(emptyList())

    private fun appendMounted(files: List<MountedTxtFile>, texts: List<String>, addedCount: Int) {
        if (files.isEmpty()) return
        mountedTexts.addAll(texts)
        _state.update {
            val merged = it.mountedFiles + files
            val chars = merged.sumOf(MountedTxtFile::charCount)
            it.copy(
                mountedFiles = merged,
                totalChars = chars,
                isDocTooLargeForPreview = chars >= PREVIEW_CHAR_LIMIT,
                notice = TextNotice.Mounted(addedCount, chars),
            )
        }
        schedulePreview()
    }

    /** 卸载指定序号文件。 */
    fun removeMountedFile(index: Int) {
        if (index !in mountedTexts.indices) return
        mountedTexts.removeAt(index)
        _state.update {
            val merged = it.mountedFiles.toMutableList().apply { removeAt(index) }
            val chars = merged.sumOf(MountedTxtFile::charCount).let { sum ->
                if (merged.isEmpty()) it.doc.content.length else sum
            }
            it.copy(
                mountedFiles = merged,
                totalChars = chars,
                isDocTooLargeForPreview = chars >= PREVIEW_CHAR_LIMIT,
            )
        }
        schedulePreview()
    }

    /** 调整挂载顺序（delta 通常为 -1/+1）。 */
    fun moveMountedFile(index: Int, delta: Int) {
        val target = index + delta
        if (index !in mountedTexts.indices || target !in mountedTexts.indices) return
        java.util.Collections.swap(mountedTexts, index, target)
        _state.update {
            val merged = it.mountedFiles.toMutableList().apply {
                java.util.Collections.swap(this, index, target)
            }
            it.copy(mountedFiles = merged)
        }
        schedulePreview()
    }

    // ---- 自定义字体 ----

    fun importFont(file: File) {
        scope.launch {
            val entry = withContext(Dispatchers.IO) { runCatching { copyFont(file) }.getOrNull() }
            if (entry != null) {
                _state.update { it.copy(customFontName = entry.first, customFontPath = entry.second) }
                schedulePreview()
            }
        }
    }

    private fun copyFont(source: File): Pair<String, String>? {
        val dir = fontsDir()
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val displayName = source.nameWithoutExtension.ifBlank { "自定义字体" }
        val target = File(dir, "${sanitize(displayName)}.ttf")
        source.copyTo(target, overwrite = true)
        runCatching {
            prefs.put(KEY_FONT_NAME, displayName)
            prefs.put(KEY_FONT_PATH, target.absolutePath)
            prefs.flush()
        }
        return displayName to target.absolutePath
    }

    // ---- 打印 ----

    /**
     * 发起打印：分段渲染 → 光栅条带 → 蓝牙通道逐页下发（与安卓端 `TextEditorViewModel.print` 一致）。
     * 未连接时由 [TextPrintRunner] 直接返回 `NotConnected`，页面据此弹失败提示。
     */
    fun print() {
        val snapshot = _state.value
        if (snapshot.isPrinting) return
        val contentText = effectiveContent(snapshot.doc.content, mountedTexts)
        if (contentText.isEmpty()) return
        // 批量 = 份数：未开启批量固定 1 份。
        val options = snapshot.options.copy(
            copies = if (snapshot.batchEnabled) snapshot.copies else 1,
        )
        _state.update {
            it.copy(isPrinting = true, lastResult = null, progress = PrintProgress(PrintPhase.Preparing))
        }
        scope.launch {
            val result = TextPrintRunner.run(
                doc = snapshot.doc,
                content = contentText,
                customFontPath = snapshot.customFontPath,
                options = options,
                onProgress = { p -> _state.update { it.copy(progress = p) } },
            )
            _state.update { it.copy(isPrinting = false, progress = null, lastResult = result) }
        }
    }

    fun cancelPrint() = PrinterController.cancel()

    fun clearResult() {
        _state.update { it.copy(lastResult = null) }
    }

    /** 已提示过的故障状态复位（同一条故障再次出现时仍能弹出提示）。 */
    fun clearPrinterStatus() {
        _state.update { it.copy(printerStatus = null) }
    }

    /**
     * 防抖重渲染预览：与真实打印共用同一渲染器，保证所见即所得。
     * 超长文本（≥ PREVIEW_CHAR_LIMIT）跳过，避免导入大文件时界面卡顿。
     */
    private fun schedulePreview() {
        previewJob?.cancel()
        previewJob = scope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val snapshot = _state.value
            val content = effectiveContent(snapshot.doc.content, mountedTexts)
            val tooLarge = content.length >= PREVIEW_CHAR_LIMIT
            _state.update { it.copy(isDocTooLargeForPreview = tooLarge) }
            if (tooLarge) {
                _state.update { it.copy(previewImage = null) }
                return@launch
            }
            val previewDoc = snapshot.doc.copy(content = content)
            val image = TextPreviewRenderer.renderPreview(previewDoc, snapshot.customFontPath, PREVIEW_MAX_HEIGHT_PX)
            _state.update { it.copy(previewImage = image) }
        }
    }

    private fun initialState(): TextEditorUiState {
        val name = prefs.get(KEY_FONT_NAME, null)
        val path = prefs.get(KEY_FONT_PATH, null)
        val fontValid = path != null && File(path).isFile
        return TextEditorUiState(
            customFontName = if (fontValid) name else null,
            customFontPath = if (fontValid) path else null,
        )
    }

    // 用自定义 getter：字段初始化顺序在本 object 内是「先 state → 后 prefs」，写成 lazy 委托会在
    // initialState() 里访问到尚未赋值的委托对象（NPE），getter 形式与初始化顺序无关。
    private val prefs: Preferences
        get() = Preferences.userRoot().node("com/pencil/chuangyin/pro/text")

    private fun fontsDir(): File {
        val base = System.getenv("LOCALAPPDATA")?.let { File(it) }
            ?: File(System.getProperty("user.home"), "AppData/Local")
        return File(base, "PencilChuangyinPro/fonts")
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "custom_font" }

    private const val KEY_FONT_NAME = "font_name"
    private const val KEY_FONT_PATH = "font_path"

    /** 中文 ZIP 条目名回退字符集。 */
    private val GBK_CHARSET: java.nio.charset.Charset = java.nio.charset.Charset.forName("GBK")

    /** 文件名排序：数字/字母/拼音音序（按简中 Collator 排序）。 */
    private val NAME_ORDER: Comparator<in String> = Collator.getInstance(Locale.SIMPLIFIED_CHINESE)

    /** 仅取压缩包根目录条目：去掉可能的 "./" 前缀后不含路径分隔符。 */
    private fun isRootTxt(entryName: String): Boolean {
        val clean = entryName.removePrefix("./").trimStart('/')
        val isRoot = !clean.contains('/') && !clean.contains('\\')
        return isRoot && clean.lowercase(Locale.US).endsWith(".txt")
    }

    private fun baseName(entryName: String): String =
        entryName.removePrefix("./").trimStart('/').substringAfterLast('/').substringAfterLast('\\')

    private fun effectiveContent(manual: String, mounted: List<String>): String =
        if (mounted.isEmpty()) manual else mounted.joinToString(separator = "\n")

    private fun effectiveChars(manual: String, mounted: List<String>): Int =
        if (mounted.isEmpty()) manual.length else mounted.sumOf { it.length } + mounted.lastIndex.coerceAtLeast(0)
}
