package com.pencil.chuangyin.feature.text

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.ui.data.GallerySaver
import com.pencil.chuangyin.feature.text.data.FontStorage
import com.pencil.chuangyin.printer.ConnectionState
import com.pencil.chuangyin.printer.PrintAlign
import com.pencil.chuangyin.printer.PrintOptions
import com.pencil.chuangyin.printer.PrintPhase
import com.pencil.chuangyin.printer.PrintProgress
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterController
import com.pencil.chuangyin.printer.PrinterDevice
import com.pencil.chuangyin.printer.PrinterStatus
import com.pencil.chuangyin.printer.TextBitmapRenderer
import com.pencil.chuangyin.printer.TextDoc
import com.pencil.chuangyin.printer.TextTransferBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Collator
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 已挂载的 TXT 文件（内容不进入输入框，仅呈现名称与字数，并保留导入先后顺序）。 */
data class MountedTxtFile(
    val uri: Uri?,
    val name: String,
    val charCount: Int,
)

/** 一次性轻提示事件（文案在 UI 层经 stringResource 映射，VM 不持有硬编码文案）。 */
sealed interface TextNotice {
    data class Mounted(val count: Int, val chars: Int) : TextNotice
    data object ArchiveEmpty : TextNotice
    data class Saved(val count: Int) : TextNotice
    data object SaveFailed : TextNotice
}

/** 批量开关开启时的默认份数。 */
private const val DEFAULT_COPIES = 2

data class TextEditorUiState(
    val doc: TextDoc = TextDoc(),
    val connectedDevice: PrinterDevice? = null,
    val isConnecting: Boolean = false,
    val isPrinting: Boolean = false,
    val progress: PrintProgress? = null,
    val lastResult: PrintResult? = null,
    val customFontName: String? = null,
    val customFontPath: String? = null,
    val printerStatus: PrinterStatus? = null,
    /** 打印机是否正处于过热保护（固件自主冷却中，非瞬时提示）。 */
    val overheatActive: Boolean = false,
    val options: PrintOptions = PrintOptions(),
    /** 与打印管线同一渲染器产出的 WYSIWYG 预览位图（384 点宽，仅顶部有界区域）。 */
    val previewBitmap: Bitmap? = null,
    /** 已挂载的 TXT 文件序列（允许多个，顺序即打印顺序）；非空时正文以文件拼接内容为准。 */
    val mountedFiles: List<MountedTxtFile> = emptyList(),
    /** 实际将打印的总字符数（挂载文件拼接或输入框）。 */
    val totalChars: Int = 0,
    /** 正文是否超长（≥ 阈值则不生成预览，避免卡顿）。 */
    val isDocTooLargeForPreview: Boolean = false,
    /** 全屏输入页是否展开。 */
    val editorExpanded: Boolean = false,
    /** 最近一次轻提示事件。 */
    val notice: TextNotice? = null,
    /** 批量打印开关：开启后整批任务按份数重复执行。 */
    val batchEnabled: Boolean = false,
    /** 打印份数：批量开关开启时生效。 */
    val copies: Int = DEFAULT_COPIES,
    /** 保存预览图到相册进行中。 */
    val isSaving: Boolean = false,
)

@HiltViewModel
class TextEditorViewModel @Inject constructor(
    private val printerController: PrinterController,
    private val fontStorage: FontStorage,
    private val textBitmapRenderer: TextBitmapRenderer,
    private val gallerySaver: GallerySaver,
    private val textTransferBus: TextTransferBus,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<TextEditorUiState> = _state.asStateFlow()

    private var previewJob: Job? = null

    /**
     * 挂载文件正文：与 [TextEditorUiState.mountedFiles] 下标一一对应。
     * 刻意留在 StateFlow 之外，避免 17 万字级文本触发 Compose 重组/卡顿。
     */
    private val mountedTexts: MutableList<String> = mutableListOf()

    init {
        viewModelScope.launch {
            printerController.connectionState.collect { connection ->
                _state.update {
                    it.copy(
                        connectedDevice = (connection as? ConnectionState.Connected)?.device,
                        isConnecting = connection is ConnectionState.Connecting,
                    )
                }
            }
        }
        viewModelScope.launch {
            printerController.statusEvents.collect { status ->
                _state.update { it.copy(printerStatus = status) }
            }
        }
        viewModelScope.launch {
            printerController.deviceState.collect { device ->
                _state.update { it.copy(overheatActive = PrinterStatus.Overheat in device.activeStatuses) }
            }
        }
        schedulePreview()
    }

    // ---- 文档编辑 ----

    /**
     * 消费外部页面（P 站小说）经 [TextTransferBus] 一次性传入的标题+正文。
     * 由界面进入时调用一次；无待处理内容时为空操作。
     */
    fun consumeTransferredText() {
        val payload = textTransferBus.drain() ?: return
        mountedTexts.clear()
        _state.update {
            it.copy(
                doc = it.doc.copy(title = payload.title, content = payload.content),
                mountedFiles = emptyList(),
                totalChars = payload.content.length,
                isDocTooLargeForPreview = payload.content.length >= PREVIEW_CHAR_LIMIT,
            )
        }
        schedulePreview()
    }

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

    /** 保存预览位图到相册（PNG，所见即所得）。位图与 UI 共享，不能 recycle。 */
    fun savePreview() {
        val snapshot = _state.value
        val bitmap = snapshot.previewBitmap ?: return
        if (snapshot.isSaving) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true) }
            val count = gallerySaver.save(listOf(bitmap), "pencil_text_${System.currentTimeMillis()}")
            _state.update {
                it.copy(
                    isSaving = false,
                    notice = if (count > 0) TextNotice.Saved(count) else TextNotice.SaveFailed,
                )
            }
        }
    }

    // ---- 导入 TXT：支持多选，按选择先后顺序挂载 ----
    fun importTxt(uris: List<Uri>, contentResolver: ContentResolver) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val appended = ArrayList<MountedTxtFile>()
            val appendedTexts = ArrayList<String>()
            uris.forEach { uri ->
                val (name, text) = runCatching {
                    queryDisplayName(uri, contentResolver) to
                        (contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader(Charsets.UTF_8).readText()
                        } ?: "")
                }.getOrNull() ?: return@forEach
                appended += MountedTxtFile(uri = uri, name = name, charCount = text.length)
                appendedTexts += text
            }
            appendMounted(appended, appendedTexts, appended.size)
        }
    }

    /**
     * 导入 ZIP 压缩包：自动扫描根目录所有 TXT，按首字母/数字/拼音音序排序后挂载。
     * 中文 ZIP 条目名常见 GBK 编码，UTF-8 失败时自动回退 GBK 重试。
     */
    fun importTxtArchive(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = runCatching {
                readArchiveEntries(uri, contentResolver)
            }.getOrNull().orEmpty()
            val txts = entries
                .filter { isRootTxt(it.name) }
                .sortedWith(compareBy(NameOrder) { it.name })
            if (txts.isEmpty()) {
                _state.update { it.copy(notice = TextNotice.ArchiveEmpty) }
                return@launch
            }
            val files = txts.map { MountedTxtFile(uri = null, name = baseName(it.name), charCount = it.text.length) }
            appendMounted(files, txts.map { it.text }, files.size)
        }
    }

    private data class ArchiveEntry(val name: String, val text: String)

    private fun readArchiveEntries(uri: Uri, contentResolver: ContentResolver): List<ArchiveEntry> {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()
        // 第一次按 UTF-8；若条目名出现替换字符或没有 TXT，再按 GBK 重试。
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

    private fun appendMounted(
        files: List<MountedTxtFile>,
        texts: List<String>,
        addedCount: Int,
    ) {
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

    private fun queryDisplayName(uri: Uri, contentResolver: ContentResolver): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx) ?: context.getString(R.string.text_fallback_file_name)
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: context.getString(R.string.text_fallback_file_name)
    }

    fun importFont(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            val entry = fontStorage.importFont(uri, contentResolver, fallbackName = context.getString(R.string.text_fallback_font_name))
            if (entry != null) {
                _state.update {
                    it.copy(customFontName = entry.name, customFontPath = entry.filePath)
                }
                schedulePreview()
            }
        }
    }

    // ---- 打印 ----
    fun print() {
        val snapshot = _state.value
        if (snapshot.isPrinting) return
        val contentText = effectiveContent(snapshot.doc.content, mountedTexts)
        if (contentText.isEmpty()) return
        val effectiveDoc = snapshot.doc.copy(content = contentText)

        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(isPrinting = true, lastResult = null, progress = PrintProgress(PrintPhase.Preparing))
            }
            val typeface = snapshot.customFontPath?.let { path ->
                runCatching { Typeface.createFromFile(path) }.getOrNull()
            }
            // 批量 = 份数：未开启批量固定 1 份。
            val printOptions = snapshot.options.copy(
                copies = if (snapshot.batchEnabled) snapshot.copies else 1,
            )
            val result = printerController.printDocument(effectiveDoc, typeface, printOptions) { p ->
                _state.update { it.copy(progress = p) }
            }
            _state.update { it.copy(isPrinting = false, progress = null, lastResult = result) }
        }
    }

    fun cancelPrint() {
        printerController.cancelPrint()
    }

    fun clearResult() {
        _state.update { it.copy(lastResult = null) }
    }

    /**
     * 防抖重渲染预览：与真实打印共用 [TextBitmapRenderer]，保证所见即所得。
     * 超长文本（≥ [PREVIEW_CHAR_LIMIT]）跳过，避免导入大文件时界面卡顿。
     */
    private fun schedulePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            delay(PREVIEW_DEBOUNCE_MS)
            val snapshot = _state.value
            val effectiveContent = effectiveContent(snapshot.doc.content, mountedTexts)
            val tooLarge = effectiveContent.length >= PREVIEW_CHAR_LIMIT
            _state.update { it.copy(isDocTooLargeForPreview = tooLarge) }
            if (tooLarge) {
                _state.update { it.copy(previewBitmap = null) }
                return@launch
            }
            val typeface = snapshot.customFontPath?.let { path ->
                runCatching { Typeface.createFromFile(path) }.getOrNull()
            }
            val previewDoc = snapshot.doc.copy(content = effectiveContent)
            val bitmap = runCatching {
                textBitmapRenderer.renderPreview(previewDoc, typeface, PREVIEW_MAX_HEIGHT_PX)
            }.getOrNull()
            _state.update { it.copy(previewBitmap = bitmap) }
        }
    }

    private fun initialState(): TextEditorUiState {
        val font = runCatching { fontStorage.currentFont() }.getOrNull()
        val connected = (printerController.connectionState.value as? ConnectionState.Connected)?.device
        return TextEditorUiState(
            connectedDevice = connected,
            isConnecting = printerController.connectionState.value is ConnectionState.Connecting,
            customFontName = font?.name,
            customFontPath = font?.filePath,
        )
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 200L
        const val PREVIEW_CHAR_LIMIT = 10_000
        const val PREVIEW_MAX_HEIGHT_PX = 1_500

        /** 中文 ZIP 条目名回退字符集（Android 平台保证内置 GBK）。 */
        val GBK_CHARSET: java.nio.charset.Charset = java.nio.charset.Charset.forName("GBK")

        /** 文件名排序：数字/字母/拼音音序（Collator.SIMPLIFIED_CHINESE 按拼音排序）。 */
        val NameOrder: Comparator<in String> = Collator.getInstance(Locale.SIMPLIFIED_CHINESE)

        /** 仅取压缩包根目录条目：去掉可能的 "./" 前缀后不含路径分隔符。 */
        fun isRootTxt(entryName: String): Boolean {
            val clean = entryName.removePrefix("./").trimStart('/')
            val isRoot = !clean.contains('/') && !clean.contains('\\')
            return isRoot && clean.lowercase(Locale.US).endsWith(".txt")
        }

        fun baseName(entryName: String): String =
            entryName.removePrefix("./").trimStart('/').substringAfterLast('/').substringAfterLast('\\')

        fun effectiveContent(manual: String, mounted: List<String>): String =
            if (mounted.isEmpty()) manual else mounted.joinToString(separator = "\n")

        fun effectiveChars(manual: String, mounted: List<String>): Int =
            if (mounted.isEmpty()) manual.length else mounted.sumOf { it.length } + mounted.lastIndex.coerceAtLeast(0)
    }
}
