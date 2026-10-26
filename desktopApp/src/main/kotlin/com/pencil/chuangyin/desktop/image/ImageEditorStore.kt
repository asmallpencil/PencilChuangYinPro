package com.pencil.chuangyin.desktop.image

import androidx.compose.ui.graphics.ImageBitmap
import com.pencil.chuangyin.desktop.printer.ConnectionState
import com.pencil.chuangyin.desktop.printer.PrinterConnectionStore
import com.pencil.chuangyin.desktop.printer.PrinterController
import com.pencil.chuangyin.desktop.printer.RasterPage
import com.pencil.chuangyin.desktop.support.OutputPathStore
import com.pencil.chuangyin.desktop.text.PrintError
import com.pencil.chuangyin.desktop.text.PrintOptions
import com.pencil.chuangyin.desktop.text.PrintPhase
import com.pencil.chuangyin.desktop.text.PrintProgress
import com.pencil.chuangyin.desktop.text.PrintResult
import com.pencil.chuangyin.desktop.text.PrinterStatus
import java.io.File
import java.util.Collections
import kotlin.math.abs
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

/**
 * 图片打印状态仓库 —— 取代安卓端 `ImageEditorViewModel`（桌面无 Hilt / Lifecycle）：
 * 单例 + StateFlow + 自有协程作用域，方法签名与语义逐条对齐。
 *
 * 平台映射：
 *  - `Uri + ContentResolver` → `File`（JFileChooser 选文件）；
 *  - `PdfRenderer + ParcelFileDescriptor` → PDFBox（见 [PdfDocumentBundle]）；
 *  - `ImagePreparer + Bitmap` → [ImageProcessor] + `BufferedImage`；
 *  - `GallerySaver` → [OutputPathStore]（PNG 落到输出目录）；
 *  - `PrinterController` → 桌面端 [PrinterController]（RFCOMM / SPP 蓝牙通道，逐页 `RasterPage`）。
 */
object ImageEditorStore {

    /** 预览防抖：滑杆连续拖动时只处理最后一帧。 */
    private const val PREVIEW_DEBOUNCE_MS = 150L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 保护 [sources] / [pdfBundles] / [tempDirs]：预览与保存都在后台线程读。 */
    private val lock = Any()

    private val _state = MutableStateFlow(ImageEditorUiState())
    val state: StateFlow<ImageEditorUiState> = _state.asStateFlow()

    /** 与 `pages` 下标一一对应的来源，刻意留在 StateFlow 之外（大对象不进 Compose 快照）。 */
    private val sources = mutableListOf<ImageSource>()

    /** 已确认加入的 PDF 句柄：多页来源共享同一个文档，清空时统一关闭。 */
    private val pdfBundles = mutableListOf<PdfDocumentBundle>()

    /** 压缩包解压出的临时目录：移除/清空时删除。 */
    private val tempDirs = mutableListOf<File>()

    /** 逐页参数覆盖（key = 页号；0 号页即「全局默认」，其它页未覆盖时沿用它）。 */
    private val optionOverrides = mutableMapOf<Int, ImageProcessOptions>()

    private var pickSession: PickSession? = null

    private val thumbInFlight: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private var previewJob: Job? = null

    init {
        observePrinter()
    }

    /** 镜像打印机的连接态 / 故障态 / 状态事件（与安卓端 ImageEditorViewModel 一致）。 */
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

    // ===================== 选择页会话 =====================

    /** 选择页内部会话：持有尚未交接给页面的 PDF 句柄 / 解压目录。 */
    private sealed interface PickSession {
        val kind: PickKind
        val items: List<PickItem>
    }

    private class PdfPickSession(
        val bundle: PdfDocumentBundle,
        override val items: List<PickItem>,
    ) : PickSession {
        override val kind: PickKind = PickKind.PDF
    }

    private class ArchivePickSession(
        val extracted: ImageArchiveImporter.ExtractedImages,
        override val items: List<PickItem>,
    ) : PickSession {
        override val kind: PickKind = PickKind.ARCHIVE
    }

    // ===================== 来源导入 =====================

    /** 相册：桌面端 = 多选本地图片文件，直接按选择顺序加入。 */
    fun importGallery(files: List<File>) {
        if (files.isEmpty()) return
        val added = files.map { FileImageSource(it, it.name) }
        appendSources(added)
    }

    /** 导入 PDF：打开失败提示「PDF 打开失败」，成功则进入页码选择页。 */
    fun importPdf(file: File) {
        scope.launch {
            val bundle = withContext(Dispatchers.IO) { PdfDocumentBundle.open(file) }
            if (bundle == null) {
                _state.update { it.copy(notice = ImageNotice.PdfFailed) }
                return@launch
            }
            val items = (0 until bundle.pageCount).map { PickItem.PdfPage(it) }
            if (items.isEmpty()) {
                bundle.close()
                _state.update { it.copy(notice = ImageNotice.PdfFailed) }
                return@launch
            }
            startPickSession(PdfPickSession(bundle, items))
        }
    }

    /** 导入压缩包：未找到图片提示「压缩包中未找到图片」，成功则进入图片选择页。 */
    fun importArchive(file: File) {
        scope.launch {
            val extracted = withContext(Dispatchers.IO) {
                ImageArchiveImporter.import(file, ImageArchiveImporter.cacheDir())
            }
            if (extracted == null) {
                // 文件不可读 = 导入失败；能读但没解出图片 = 压缩包里没有图片。
                val notice = if (file.isFile) ImageNotice.ArchiveEmpty else ImageNotice.ImportFailed
                _state.update { it.copy(notice = notice) }
                return@launch
            }
            val items = extracted.images.mapIndexed { index, image ->
                PickItem.ArchiveImage(image.path, index)
            }
            startPickSession(ArchivePickSession(extracted, items))
        }
    }

    // ===================== 选择页交互 =====================

    private fun startPickSession(session: PickSession) {
        discardPickSession()
        pickSession = session
        thumbInFlight.clear()
        _state.update {
            it.copy(
                pick = PickUiState(
                    kind = session.kind,
                    items = session.items,
                    // 与安卓端一致：进入选择页默认全选。
                    selected = session.items.map { item -> item.key }.toSet(),
                ),
            )
        }
    }

    /** 放弃选择页：未交接给页面的句柄 / 目录就地回收。 */
    private fun discardPickSession() {
        val session = pickSession ?: return
        pickSession = null
        thumbInFlight.clear()
        when (session) {
            is PdfPickSession -> {
                val handedOver = synchronized(lock) { pdfBundles.any { it === session.bundle } }
                if (!handedOver) session.bundle.close()
            }

            is ArchivePickSession -> {
                val handedOver = synchronized(lock) { tempDirs.any { it == session.extracted.tempDir } }
                if (!handedOver) ImageArchiveImporter.cleanup(session.extracted.tempDir)
            }
        }
    }

    fun togglePickSelection(key: String) {
        _state.update { current ->
            val pick = current.pick ?: return@update current
            val selected = if (key in pick.selected) pick.selected - key else pick.selected + key
            current.copy(pick = pick.copy(selected = selected))
        }
    }

    fun togglePickSelectAll() {
        _state.update { current ->
            val pick = current.pick ?: return@update current
            val allSelected = pick.items.isNotEmpty() && pick.items.all { it.key in pick.selected }
            val selected = if (allSelected) {
                emptySet()
            } else {
                pick.items.map { it.key }.toSet()
            }
            current.copy(pick = pick.copy(selected = selected))
        }
    }

    /** 确认选择：把选中的条目按原顺序转成来源追加到页面列表。 */
    fun confirmPick() {
        val session = pickSession ?: return
        val selectedKeys = _state.value.pick?.selected.orEmpty()
        if (selectedKeys.isEmpty()) return
        val chosen = session.items.filter { it.key in selectedKeys }
        if (chosen.isEmpty()) return

        val newSources: List<ImageSource> = when (session) {
            is PdfPickSession -> {
                synchronized(lock) { pdfBundles += session.bundle }
                chosen.mapNotNull { item ->
                    (item as? PickItem.PdfPage)?.let { page ->
                        PdfPageSource(session.bundle, page.pageIndex, pdfPageName(page.pageIndex))
                    }
                }
            }

            is ArchivePickSession -> {
                synchronized(lock) { tempDirs += session.extracted.tempDir }
                chosen.mapNotNull { item ->
                    (item as? PickItem.ArchiveImage)
                        ?.let { image -> session.extracted.images.getOrNull(image.fileIndex) }
                        ?.let { entry -> FileImageSource(entry.file, entry.path) }
                }
            }
        }

        pickSession = null
        thumbInFlight.clear()
        _state.update { it.copy(pick = null) }
        if (newSources.isEmpty()) return
        appendSources(newSources)
    }

    /** 取消选择：回收本次会话资源并回到编辑页。 */
    fun cancelPick() {
        discardPickSession()
        _state.update { it.copy(pick = null) }
    }

    /** 选择页网格按需加载缩略图（同一 key 只解一次，失败允许重试）。 */
    fun ensurePickThumbnail(key: String) {
        val session = pickSession ?: return
        val item = session.items.firstOrNull { it.key == key } ?: return
        if (_state.value.pick?.thumbnails?.containsKey(key) == true) return
        if (!thumbInFlight.add(key)) return
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodePickThumbnail(session, item) }
            if (pickSession !== session) return@launch
            if (bitmap == null) {
                thumbInFlight.remove(key)
                return@launch
            }
            _state.update { current ->
                val pick = current.pick ?: return@update current
                if (pick.thumbnails.containsKey(key)) {
                    current
                } else {
                    current.copy(pick = pick.copy(thumbnails = pick.thumbnails + (key to bitmap)))
                }
            }
        }
    }

    private fun decodePickThumbnail(session: PickSession, item: PickItem): ImageBitmap? =
        when (session) {
            is PdfPickSession -> (item as? PickItem.PdfPage)
                ?.let { session.bundle.renderThumbnail(it.pageIndex) }
                ?.let { ImageProcessor.toImageBitmap(it) }

            is ArchivePickSession -> (item as? PickItem.ArchiveImage)
                ?.let { session.extracted.images.getOrNull(it.fileIndex) }
                ?.let { ImageArchiveImporter.decodeThumbnail(it.file) }
        }

    // ===================== 页面列表 =====================

    private fun appendSources(added: List<ImageSource>) {
        if (added.isEmpty()) return
        val wasEmpty = synchronized(lock) {
            sources.addAll(added)
            sources.size == added.size
        }
        _state.update { current ->
            current.copy(
                pages = current.pages + added.map { ImagePageInfo(it.name) },
                currentIndex = if (wasEmpty) 0 else current.currentIndex,
                notice = ImageNotice.Added(added.size),
            )
        }
        if (wasEmpty) schedulePreview()
    }

    /** 删除当前预览页（只删一页）；删空后复位到空态。 */
    fun removeCurrentPage() {
        previewJob?.cancel()
        val current = _state.value
        val index = current.currentIndex
        if (index !in current.pages.indices) return

        synchronized(lock) {
            sources.getOrNull(index)?.close()
            if (index in sources.indices) sources.removeAt(index)
        }
        // 覆盖参数随页号前移；被删页自身的覆盖直接丢弃。
        optionOverrides.remove(index)
        optionOverrides.keys.filter { it > index }.sorted().forEach { key ->
            val value = optionOverrides.remove(key) ?: return@forEach
            optionOverrides[key - 1] = value
        }

        val updatedPages = current.pages.toMutableList().apply { removeAt(index) }
        if (updatedPages.isEmpty()) {
            optionOverrides.clear()
            _state.update {
                it.copy(
                    pages = emptyList(),
                    currentIndex = 0,
                    processOptions = ImageProcessOptions(),
                    currentPageCustomized = false,
                    previewBitmap = null,
                    isPreparing = false,
                )
            }
            return
        }

        val newIndex = index.coerceAtMost(updatedPages.lastIndex)
        _state.update {
            it.copy(
                pages = updatedPages,
                currentIndex = newIndex,
                processOptions = effectiveOptions(newIndex),
                currentPageCustomized = newIndex != 0 && optionOverrides.containsKey(newIndex),
                previewBitmap = null,
                isPreparing = false,
            )
        }
        schedulePreview()
    }

    /** 清空全部页：关闭句柄、删除临时目录、复位状态。 */
    fun clearAll() {
        previewJob?.cancel()
        previewJob = null
        discardPickSession()
        synchronized(lock) {
            sources.forEach { it.close() }
            sources.clear()
            pdfBundles.forEach { it.close() }
            pdfBundles.clear()
            tempDirs.forEach { ImageArchiveImporter.cleanup(it) }
            tempDirs.clear()
        }
        optionOverrides.clear()
        _state.update { ImageEditorUiState() }
    }

    fun setCurrentPage(index: Int) {
        val current = _state.value
        if (index !in current.pages.indices || index == current.currentIndex) return
        _state.update {
            it.copy(
                currentIndex = index,
                processOptions = effectiveOptions(index),
                currentPageCustomized = index != 0 && optionOverrides.containsKey(index),
                previewBitmap = null,
                isPreparing = false,
            )
        }
        schedulePreview()
    }

    // ===================== 处理参数 =====================

    fun setAlgorithm(algorithm: BinarizeAlgorithm) {
        updateCurrentOptions(_state.value.processOptions.copy(algorithm = algorithm))
    }

    fun setThreshold(threshold: Int) {
        val safe = threshold.coerceIn(
            ImageProcessOptions.MIN_THRESHOLD,
            ImageProcessOptions.MAX_THRESHOLD,
        )
        updateCurrentOptions(_state.value.processOptions.copy(threshold = safe))
    }

    fun setRotation(degrees: Int) {
        // 滑杆是连续的，吸附到最近的 90° 档位（与安卓端一致）。
        val snapped = ImageProcessOptions.ROTATION_STEPS.minByOrNull { abs(it - degrees) } ?: 0
        updateCurrentOptions(_state.value.processOptions.copy(rotationDegrees = snapped))
    }

    /** 恢复当前页沿用第 1 页参数（第 1 页自身即默认值，无需恢复）。 */
    fun resetPageOptions() {
        val index = _state.value.currentIndex
        if (index == 0) return
        optionOverrides.remove(index)
        _state.update {
            it.copy(
                processOptions = effectiveOptions(index),
                currentPageCustomized = false,
            )
        }
        schedulePreview()
    }

    private fun updateCurrentOptions(updated: ImageProcessOptions) {
        val index = _state.value.currentIndex
        optionOverrides[index] = updated
        _state.update {
            it.copy(
                processOptions = updated,
                currentPageCustomized = index != 0,
            )
        }
        if (index in _state.value.pages.indices) schedulePreview()
    }

    private fun effectiveOptions(page: Int): ImageProcessOptions =
        optionOverrides[page] ?: optionOverrides[0] ?: ImageProcessOptions()

    fun setBatchEnabled(enabled: Boolean) {
        _state.update { it.copy(batchEnabled = enabled) }
    }

    fun setCopies(copies: Int) {
        _state.update {
            it.copy(copies = copies.coerceIn(PrintOptions.COPIES_RANGE))
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearResult() {
        _state.update { it.copy(lastResult = null) }
    }

    // ===================== 保存到输出目录 =====================

    /** 保存为 PNG：批量开启时保存全部页，否则只保存当前页。 */
    fun save() {
        val snapshot = _state.value
        if (snapshot.pages.isEmpty() || snapshot.isSaving || snapshot.isPrinting) return
        val indices = if (snapshot.batchEnabled) {
            snapshot.pages.indices.toList()
        } else {
            listOf(snapshot.currentIndex.coerceIn(0, snapshot.pages.lastIndex))
        }
        // 参数快照：后台线程不再回读 UI 线程的 optionOverrides。
        val overrides = HashMap(optionOverrides)
        _state.update { it.copy(isSaving = true) }
        scope.launch {
            val count = withContext(Dispatchers.IO) { writePngs(indices, overrides) }
            _state.update {
                it.copy(
                    isSaving = false,
                    notice = if (count > 0) ImageNotice.Saved(count) else ImageNotice.SaveFailed,
                )
            }
        }
    }

    private fun writePngs(
        indices: List<Int>,
        overrides: Map<Int, ImageProcessOptions>,
    ): Int {
        val dir = OutputPathStore.ensureDir() ?: return 0
        val baseName = "pencil_${System.currentTimeMillis()}"
        val multi = indices.size > 1
        var count = 0
        indices.forEachIndexed { order, index ->
            val source = synchronized(lock) { sources.getOrNull(index) } ?: return@forEachIndexed
            val options = overrides[index] ?: overrides[0] ?: ImageProcessOptions()
            val image = runCatching {
                ImageProcessor.prepare(source.load(), options)
            }.getOrNull() ?: return@forEachIndexed
            val bytes = ImageProcessor.encodePng(image) ?: return@forEachIndexed
            val file = File(dir, if (multi) "${baseName}_${order + 1}.png" else "$baseName.png")
            if (runCatching { file.writeBytes(bytes) }.isSuccess) count++
        }
        return count
    }

    // ===================== 打印 =====================

    /** 打印全部页；批量开启时按设定份数，否则固定 1 份（与安卓端一致）。 */
    fun print() {
        val snapshot = _state.value
        if (snapshot.isPrinting || snapshot.pages.isEmpty()) return
        startPrintJob(
            indices = snapshot.pages.indices.toList(),
            copies = if (snapshot.batchEnabled) snapshot.copies else 1,
        )
    }

    /** 单张打印：仅当前页，固定一份。 */
    fun printSingle() {
        val snapshot = _state.value
        if (snapshot.isPrinting || snapshot.pages.isEmpty()) return
        startPrintJob(
            indices = listOf(snapshot.currentIndex.coerceIn(0, snapshot.pages.lastIndex)),
            copies = 1,
        )
    }

    fun cancelPrint() = PrinterController.cancel()

    /** 已提示过的故障状态复位（同一条故障再次出现时仍能弹出提示）。 */
    fun clearPrinterStatus() {
        _state.update { it.copy(printerStatus = null) }
    }

    private fun startPrintJob(indices: List<Int>, copies: Int) {
        if (indices.isEmpty()) return
        // 参数快照：后台线程不再回读 UI 线程的 optionOverrides。
        val overrides = HashMap(optionOverrides)
        _state.update {
            it.copy(
                isPrinting = true,
                lastResult = null,
                progress = PrintProgress(PrintPhase.Preparing),
            )
        }
        scope.launch {
            val pages = withContext(Dispatchers.IO) {
                runCatching {
                    indices.mapNotNull { index ->
                        val source = synchronized(lock) { sources.getOrNull(index) } ?: return@mapNotNull null
                        val options = overrides[index] ?: overrides[0] ?: ImageProcessOptions()
                        RasterPage.from(ImageProcessor.prepare(source.load(), options))
                    }
                }
            }
            if (pages.isFailure) {
                val detail = pages.exceptionOrNull()?.message ?: "图片处理失败"
                _state.update {
                    it.copy(
                        isPrinting = false,
                        progress = null,
                        lastResult = PrintResult.Error(PrintError.RenderFailed, detail),
                    )
                }
                return@launch
            }
            val prepared = pages.getOrDefault(emptyList())
            val result = if (prepared.isEmpty()) {
                PrintResult.Error(PrintError.RenderFailed, "没有可打印的页面")
            } else {
                PrinterController.printPages(
                    pages = prepared,
                    options = PrintOptions(copies = copies),
                    onProgress = { p -> _state.update { it.copy(progress = p) } },
                )
            }
            _state.update { it.copy(isPrinting = false, progress = null, lastResult = result) }
        }
    }

    // ===================== 预览 =====================

    /**
     * 重新生成预览：防抖 → 在后台解码 + 处理当前页 → 回填。
     * 上百页时内存中只保留当前页，翻页才处理新页。
     */
    private fun schedulePreview() {
        previewJob?.cancel()
        previewJob = scope.launch {
            val self = coroutineContext[Job]
            delay(PREVIEW_DEBOUNCE_MS)
            val snapshot = _state.value
            val index = snapshot.currentIndex
            if (index !in snapshot.pages.indices) {
                _state.update { it.copy(previewBitmap = null, isPreparing = false) }
                return@launch
            }
            _state.update { it.copy(isPreparing = true) }
            val processed = loadAndProcess(index, snapshot.processOptions)
            // 处理是不可取消的 CPU 工作：结束后确认自己仍是当前任务，避免旧帧覆盖新帧。
            if (previewJob !== self) return@launch
            _state.update {
                it.copy(
                    previewBitmap = processed,
                    isPreparing = false,
                )
            }
        }
    }

    private fun loadAndProcess(index: Int, options: ImageProcessOptions): ImageBitmap? =
        runCatching {
            val source = synchronized(lock) { sources.getOrNull(index) } ?: return null
            val raw = source.load()
            ImageProcessor.toImageBitmap(ImageProcessor.prepare(raw, options))
        }.getOrNull()

    private fun pdfPageName(pageIndex: Int): String = "PDF-p${pageIndex + 1}"
}
