package com.pencil.chuangyin.feature.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.feature.image.data.ExtractedImages
import com.pencil.chuangyin.feature.image.data.FileImageSource
import com.pencil.chuangyin.feature.image.data.calculateInSampleSize
import com.pencil.chuangyin.core.ui.data.GallerySaver
import com.pencil.chuangyin.feature.image.data.ImageArchiveImporter
import com.pencil.chuangyin.feature.image.data.ImageSource
import com.pencil.chuangyin.feature.image.data.PdfDocumentBundle
import com.pencil.chuangyin.feature.image.data.PdfPageSource
import com.pencil.chuangyin.feature.image.data.UriImageSource
import com.pencil.chuangyin.feature.image.data.renderPdfThumbnail
import com.pencil.chuangyin.printer.BinarizeAlgorithm
import com.pencil.chuangyin.printer.ConnectionState
import com.pencil.chuangyin.printer.ImagePrintOptions
import com.pencil.chuangyin.printer.ImageProcessOptions
import com.pencil.chuangyin.printer.ImagePreparer
import com.pencil.chuangyin.printer.PrintOptions
import com.pencil.chuangyin.printer.PrintPhase
import com.pencil.chuangyin.printer.PrintProgress
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterController
import com.pencil.chuangyin.printer.PrinterDevice
import com.pencil.chuangyin.printer.PrinterStatus
import com.pencil.chuangyin.printer.ShotTransferBus
import com.pencil.chuangyin.printer.ShotTransferMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 已加入任务的图片页（仅展示信息，真实位图经 [ImageSource] 懒加载）。 */
data class ImagePageInfo(
    val name: String,
)

/** 一次性轻提示事件（文案在 UI 层经 stringResource 映射，VM 不持有硬编码文案）。 */
sealed interface ImageNotice {
    data class Added(val pages: Int) : ImageNotice
    data class Saved(val count: Int) : ImageNotice
    data object ArchiveEmpty : ImageNotice
    data object PdfFailed : ImageNotice
    data object ImportFailed : ImageNotice
    data object SaveFailed : ImageNotice
}

/** 选择页条目：PDF 页或压缩包图片（文案由 UI 层渲染，保持零硬编码）。 */
sealed interface PickItem {
    val key: String

    data class PdfPage(val pageIndex: Int) : PickItem {
        override val key: String get() = "pdf_$pageIndex"
    }

    data class ArchiveImage(val path: String, val fileIndex: Int) : PickItem {
        override val key: String get() = "arc_$fileIndex"
    }

    /** 外部成品（P 站多图下载等）经 ShotTransferBus 传入的待选图片。 */
    data class Transferred(val fileIndex: Int) : PickItem {
        override val key: String get() = "tr_$fileIndex"
    }
}

/** 选择会话种类（UI 据此取标题文案）。 */
enum class PickKind { PDF, ARCHIVE, TRANSFER }

/** 选择页 UI 状态：缩略图按需懒加载进 [thumbnails]。 */
data class PickUiState(
    val kind: PickKind,
    val items: List<PickItem>,
    val selected: Set<String>,
    val thumbnails: Map<String, Bitmap> = emptyMap(),
)

/** 选择会话持有的底层资源（不进 StateFlow）。 */
private sealed interface PickSessionData {
    val items: List<PickItem>

    data class Pdf(
        val bundle: PdfDocumentBundle,
        override val items: List<PickItem.PdfPage>,
    ) : PickSessionData

    data class Archive(
        val extracted: ExtractedImages,
        override val items: List<PickItem.ArchiveImage>,
    ) : PickSessionData

    /** 外部成品文件（P 站下载/拍照），取消选择时不删文件，缓存由来源方自行管理。 */
    data class Transferred(
        val files: List<File>,
        override val items: List<PickItem.Transferred>,
    ) : PickSessionData
}

/** 批量开关开启时的默认份数。 */
private const val DEFAULT_COPIES = 2

data class ImageEditorUiState(
    val pages: List<ImagePageInfo> = emptyList(),
    val currentIndex: Int = 0,
    /** 当前页生效的处理参数（逐页覆盖或沿用第 1 页）。 */
    val processOptions: ImageProcessOptions = ImageProcessOptions(),
    /** 当前页是否有单独调整（仅第 2 页起可能为 true）。 */
    val currentPageCustomized: Boolean = false,
    val batchEnabled: Boolean = false,
    /** 批量份数：批量开关开启时整批任务重复执行的次数。 */
    val copies: Int = DEFAULT_COPIES,
    /** 选择页会话；非 null 时界面整体切换为选择页。 */
    val pick: PickUiState? = null,
    /** 当前页经算法处理后的黑白预览（384 点宽）。 */
    val previewBitmap: Bitmap? = null,
    val isPreparing: Boolean = false,
    val isSaving: Boolean = false,
    val connectedDevice: PrinterDevice? = null,
    val isConnecting: Boolean = false,
    val isPrinting: Boolean = false,
    val progress: PrintProgress? = null,
    val lastResult: PrintResult? = null,
    val printerStatus: PrinterStatus? = null,
    /** 打印机是否正处于过热保护（固件自主冷却中，非瞬时提示）。 */
    val overheatActive: Boolean = false,
    val notice: ImageNotice? = null,
)

@HiltViewModel
class ImageEditorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val printerController: PrinterController,
    private val imagePreparer: ImagePreparer,
    private val gallerySaver: GallerySaver,
    private val shotTransferBus: ShotTransferBus,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ImageEditorUiState> = _state.asStateFlow()

    /** 图片来源序列：与 UI 页列表一一对应，懒加载，刻意留在 StateFlow 之外。 */
    private val sources = mutableListOf<ImageSource>()

    /** PDF 渲染器/FD 与压缩包临时目录：清空/销毁时统一释放。 */
    private val pdfBundles = mutableListOf<PdfDocumentBundle>()
    private val tempDirs = mutableListOf<java.io.File>()

    /** 逐页处理参数覆盖：页号 → 参数；第 0 页为基准，未覆盖页沿用基准。 */
    private val optionOverrides = mutableMapOf<Int, ImageProcessOptions>()

    /** 当前选择会话（PDF/压缩包挑选阶段）。 */
    private var pickSession: PickSessionData? = null
    private val pickItemsByKey = mutableMapOf<String, PickItem>()
    private val thumbInFlight = mutableSetOf<String>()

    private var previewJob: Job? = null

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
    }

    // ---- 导入来源 ----

    /** 相册多选：按选择先后顺序追加。 */
    fun importGallery(uris: List<Uri>, contentResolver: ContentResolver) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val appended = uris.mapNotNull { uri ->
                runCatching {
                    val name = queryDisplayName(uri, contentResolver)
                    UriImageSource(appContext, uri, name)
                }.getOrNull()
            }
            if (appended.isEmpty()) {
                _state.update { it.copy(notice = ImageNotice.ImportFailed) }
                return@launch
            }
            appendSources(appended.map { it.name }, appended)
        }
    }

    /**
     * 系统相机（ACTION_IMAGE_CAPTURE）返回的成品：转入本页缓存并追加一页。
     * 相机的 EXIF 方向在此归一——[FileImageSource] 直接按像素解码，不读方向标签。
     */
    fun importSystemCameraShot(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(appContext.cacheDir, ShotTransferBus.SHOT_DIR).apply { runCatching { mkdirs() } }
            val raw = File(dir, "sys_${System.nanoTime()}.jpg")
            val copied = runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(raw).use { output -> input.copyTo(output) }
                }
                raw.takeIf { it.length() > 0L }
            }.getOrNull()
            if (copied == null) {
                runCatching { raw.delete() }
                _state.update { it.copy(notice = ImageNotice.ImportFailed) }
                return@launch
            }
            val prepared = runCatching { normalizeExif(copied) }.getOrElse { copied }
            if (prepared !== copied) runCatching { copied.delete() }
            appendSources(listOf(prepared.name), listOf(FileImageSource(appContext, prepared, prepared.name)))
        }
    }

    /** EXIF 方向归一：无旋转原样返回，否则旋转像素后写出新文件（避免重编码整张原图）。 */
    private fun normalizeExif(file: File): File {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return file
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
            },
        ) ?: return file
        val rotated = Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height,
            Matrix().apply { postRotate(degrees) }, true,
        )
        val target = File(file.parentFile, "shot_${System.nanoTime()}.jpg")
        FileOutputStream(target).use { rotated.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (rotated !== decoded) runCatching { decoded.recycle() }
        runCatching { rotated.recycle() }
        return target
    }

    /** PDF 导入：先进入页面选择会话，挑选确认后才真正加入任务。 */
    fun importPdf(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bundle = PdfDocumentBundle.open(appContext, uri)
            if (bundle == null || bundle.pageCount <= 0) {
                bundle?.close()
                _state.update { it.copy(notice = ImageNotice.PdfFailed) }
                return@launch
            }
            val items = (0 until bundle.pageCount).map(PickItem::PdfPage)
            openPickSession(PickSessionData.Pdf(bundle, items), PickKind.PDF)
        }
    }

    /** 压缩包导入：扫描整个压缩包的图片，先进入选择会话。 */
    fun importArchive(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val extracted = ImageArchiveImporter.import(appContext, uri)
            if (extracted == null) {
                _state.update { it.copy(notice = ImageNotice.ArchiveEmpty) }
                return@launch
            }
            val items = extracted.images.mapIndexed { index, image ->
                PickItem.ArchiveImage(path = image.path, fileIndex = index)
            }
            openPickSession(PickSessionData.Archive(extracted, items), PickKind.ARCHIVE)
        }
    }

    private fun openPickSession(session: PickSessionData, kind: PickKind) {
        discardPickSession()
        pickSession = session
        pickItemsByKey.clear()
        session.items.forEach { pickItemsByKey[it.key] = it }
        _state.update {
            it.copy(
                pick = PickUiState(
                    kind = kind,
                    items = session.items,
                    selected = session.items.map(PickItem::key).toSet(),
                ),
            )
        }
    }

    private fun discardPickSession() {
        when (val session = pickSession) {
            is PickSessionData.Pdf -> runCatching { session.bundle.close() }
            is PickSessionData.Archive -> ImageArchiveImporter.cleanup(session.extracted.tempDir)
            // 外部成品缓存归来源方（P 站/拍照）管理，这里不删文件。
            is PickSessionData.Transferred -> Unit
            null -> Unit
        }
        pickSession = null
        pickItemsByKey.clear()
        thumbInFlight.clear()
    }

    // ---- 选择页操作 ----

    fun togglePickSelection(key: String) {
        _state.update { state ->
            val pick = state.pick ?: return@update state
            val selected = if (key in pick.selected) pick.selected - key else pick.selected + key
            state.copy(pick = pick.copy(selected = selected))
        }
    }

    /** 全选/取消全选二合一。 */
    fun togglePickSelectAll() {
        _state.update { state ->
            val pick = state.pick ?: return@update state
            val allSelected = pick.items.all { item -> item.key in pick.selected }
            state.copy(
                pick = pick.copy(
                    selected = if (allSelected) emptySet() else pick.items.map(PickItem::key).toSet(),
                ),
            )
        }
    }

    /** 确认选择：把选中项追加为打印任务页，保留底层资源供打印/保存使用。 */
    fun confirmPick() {
        val session = pickSession ?: return
        val pick = _state.value.pick ?: return
        if (pick.selected.isEmpty()) return
        val chosen = session.items.filter { item -> item.key in pick.selected }
        val added: List<ImageSource> = when (session) {
            is PickSessionData.Pdf -> {
                pdfBundles += session.bundle
                chosen.filterIsInstance<PickItem.PdfPage>().map { item ->
                    PdfPageSource(session.bundle.renderer, item.pageIndex, pdfPageName(item.pageIndex))
                }
            }
            is PickSessionData.Archive -> {
                tempDirs += session.extracted.tempDir
                chosen.filterIsInstance<PickItem.ArchiveImage>().map { item ->
                    val image = session.extracted.images[item.fileIndex]
                    FileImageSource(appContext, image.file, image.path)
                }
            }
            is PickSessionData.Transferred -> {
                chosen.filterIsInstance<PickItem.Transferred>().map { item ->
                    val file = session.files[item.fileIndex]
                    FileImageSource(appContext, file, file.name)
                }
            }
        }
        pickSession = null
        pickItemsByKey.clear()
        thumbInFlight.clear()
        _state.update { it.copy(pick = null) }
        viewModelScope.launch(Dispatchers.IO) {
            appendSources(added.map { it.name }, added)
        }
    }

    /** 取消选择：释放会话资源，返回编辑页。 */
    fun cancelPick() {
        discardPickSession()
        _state.update { it.copy(pick = null) }
    }

    /** 网格单元进入可视区时懒加载缩略图（在 IO 线程，去重防重入）。 */
    fun ensurePickThumbnail(key: String) {
        val pick = _state.value.pick ?: return
        if (key in pick.thumbnails || !thumbInFlight.add(key)) return
        val session = pickSession ?: return
        val item = pickItemsByKey[key] ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = when (session) {
                is PickSessionData.Pdf -> {
                    val page = (item as? PickItem.PdfPage)?.pageIndex ?: return@launch run { thumbInFlight.remove(key) }
                    renderPdfThumbnail(session.bundle.renderer, page)
                }
                is PickSessionData.Archive -> {
                    val fileIndex = (item as? PickItem.ArchiveImage)?.fileIndex
                        ?: return@launch run { thumbInFlight.remove(key) }
                    ImageArchiveImporter.decodeThumbnail(session.extracted.images[fileIndex].file)
                }
                is PickSessionData.Transferred -> {
                    val fileIndex = (item as? PickItem.Transferred)?.fileIndex
                        ?: return@launch run { thumbInFlight.remove(key) }
                    ImageArchiveImporter.decodeThumbnail(session.files[fileIndex])
                }
            }
            thumbInFlight.remove(key)
            if (bitmap != null && pickSession == session) {
                _state.update { state ->
                    val current = state.pick ?: return@update state
                    state.copy(pick = current.copy(thumbnails = current.thumbnails + (key to bitmap)))
                }
            } else {
                runCatching { bitmap?.recycle() }
            }
        }
    }

    /**
     * 消费外部成品文件（拍照确认 / P 站下载，ShotTransferBus 一次性传递）。
     * - 单张：直接追加进打印页（拍照成品即用户已确认结果，无需再选）；
     * - 多张：先进入与压缩包导入一致的图片选择会话，用户勾选确认后才加入打印页。
     * 必须且仅应在进入页面时调用一次（drain 语义保证旋转/重组不重复加载）。
     */
    fun consumeTransferredShots() {
        viewModelScope.launch(Dispatchers.IO) {
            val batch = shotTransferBus.drainBatch() ?: return@launch
            val files = batch.files.filter { it.isFile && it.length() > 0L }
            if (files.isEmpty()) return@launch
            // APPEND_ALL：来源页（P 站图库多选）已确定顺序，直接整批按序加入打印页。
            if (files.size == 1 || batch.mode == ShotTransferMode.APPEND_ALL) {
                appendSources(
                    names = files.map { it.name },
                    added = files.map { FileImageSource(appContext, it, it.name) },
                )
                return@launch
            }
            val items = files.indices.map(PickItem::Transferred)
            openPickSession(PickSessionData.Transferred(files, items), PickKind.TRANSFER)
        }
    }

    private suspend fun appendSources(names: List<String>, added: List<ImageSource>) {
        val wasEmpty = sources.isEmpty()
        sources.addAll(added)
        _state.update {
            val pages = it.pages + names.map(::ImagePageInfo)
            it.copy(
                pages = pages,
                currentIndex = if (wasEmpty) 0 else it.currentIndex,
                notice = ImageNotice.Added(added.size),
            )
        }
        if (wasEmpty) schedulePreview()
    }

    /** 删除当前预览页（仅一张）；删完为空时回到空态（不清理外部成品缓存目录）。 */
    fun removeCurrentPage() {
        val snapshot = _state.value
        val index = snapshot.currentIndex
        if (index !in snapshot.pages.indices) return
        previewJob?.cancel()
        runCatching { sources[index].close() }
        sources.removeAt(index)
        // 逐页覆盖参数：先弃被删页自身的覆盖，其后页号整体前移一位。
        optionOverrides.remove(index)
        val shifted = optionOverrides.toSortedMap()
            .mapKeys { (page, _) -> if (page > index) page - 1 else page }
        optionOverrides.clear()
        optionOverrides.putAll(shifted)
        val pages = sources.map { ImagePageInfo(it.name) }
        if (sources.isEmpty()) {
            optionOverrides.clear()
            _state.update {
                it.copy(
                    pages = emptyList(),
                    currentIndex = 0,
                    currentPageCustomized = false,
                    processOptions = ImageProcessOptions(),
                    previewBitmap = null,
                    isPreparing = false,
                )
            }
            return
        }
        val newIndex = index.coerceAtMost(sources.lastIndex)
        _state.update {
            it.copy(
                pages = pages,
                currentIndex = newIndex,
                processOptions = effectiveOptions(newIndex),
                currentPageCustomized = newIndex != 0 && optionOverrides.containsKey(newIndex),
            )
        }
        schedulePreview()
    }

    fun clearAll() {
        previewJob?.cancel()
        sources.forEach { s -> runCatching { s.close() } }
        sources.clear()
        pdfBundles.forEach { b -> runCatching { b.close() } }
        pdfBundles.clear()
        tempDirs.forEach(ImageArchiveImporter::cleanup)
        tempDirs.clear()
        // 清空任务时一并清理拍照打印留下的成品缓存。
        runCatching {
            File(appContext.cacheDir, ShotTransferBus.SHOT_DIR)
                .listFiles()
                ?.forEach { it.delete() }
        }
        optionOverrides.clear()
        _state.update {
            it.copy(
                pages = emptyList(),
                currentIndex = 0,
                currentPageCustomized = false,
                processOptions = ImageProcessOptions(),
                previewBitmap = null,
                isPreparing = false,
            )
        }
    }

    // ---- 算法/参数（逐页覆盖，未调整页沿用第 1 页） ----

    fun setCurrentPage(index: Int) {
        val snapshot = _state.value
        if (index == snapshot.currentIndex || index !in snapshot.pages.indices) return
        _state.update {
            it.copy(
                currentIndex = index,
                processOptions = effectiveOptions(index),
                currentPageCustomized = index != 0 && optionOverrides.containsKey(index),
            )
        }
        schedulePreview()
    }

    fun setAlgorithm(algorithm: BinarizeAlgorithm) {
        updateCurrentOptions { it.copy(algorithm = algorithm) }
    }

    fun setThreshold(value: Int) {
        updateCurrentOptions {
            it.copy(
                threshold = value.coerceIn(
                    ImageProcessOptions.MIN_THRESHOLD,
                    ImageProcessOptions.MAX_THRESHOLD,
                ),
            )
        }
    }

    /** 旋转四档：0/90/180/270。 */
    fun setRotation(value: Int) {
        val step = ImageProcessOptions.ROTATION_STEPS.minByOrNull { step -> kotlin.math.abs(step - value) } ?: 0
        updateCurrentOptions { it.copy(rotationDegrees = step) }
    }

    /** 恢复当前页沿用第 1 页参数。 */
    fun resetPageOptions() {
        val current = _state.value.currentIndex
        if (current == 0) return
        optionOverrides.remove(current)
        _state.update {
            it.copy(processOptions = effectiveOptions(current), currentPageCustomized = false)
        }
        schedulePreview()
    }

    private fun updateCurrentOptions(transform: (ImageProcessOptions) -> ImageProcessOptions) {
        val current = _state.value.currentIndex
        val updated = transform(_state.value.processOptions)
        optionOverrides[current] = updated
        _state.update {
            it.copy(processOptions = updated, currentPageCustomized = current != 0)
        }
        schedulePreview()
    }

    /** 页参数解析：本页覆盖 → 第 1 页基准 → 默认。 */
    private fun effectiveOptions(page: Int): ImageProcessOptions =
        optionOverrides[page] ?: optionOverrides[0] ?: ImageProcessOptions()

    fun setBatchEnabled(enabled: Boolean) {
        _state.update { it.copy(batchEnabled = enabled) }
    }

    fun setCopies(value: Int) {
        _state.update { it.copy(copies = value.coerceIn(PrintOptions.COPIES_RANGE)) }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun clearResult() = _state.update { it.copy(lastResult = null) }

    // ---- 保存 ----

    /** 保存处理后的黑白 PNG 到相册；批量开关开启时保存全部页，否则仅当前页。 */
    fun save() {
        val snapshot = _state.value
        if (snapshot.pages.isEmpty() || snapshot.isSaving || snapshot.isPrinting) return
        val indices = if (snapshot.batchEnabled) {
            snapshot.pages.indices.toList()
        } else {
            listOf(snapshot.currentIndex.coerceIn(0, snapshot.pages.lastIndex))
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true) }
            val outputs = ArrayList<Bitmap>(indices.size)
            try {
                indices.forEach { index ->
                    val (raw, processed) = loadAndProcess(index, effectiveOptions(index))
                        ?: return@forEach
                    if (processed != raw) runCatching { raw.recycle() }
                    outputs += processed
                }
                if (outputs.isEmpty()) {
                    _state.update { it.copy(isSaving = false, notice = ImageNotice.SaveFailed) }
                    return@launch
                }
                val baseName = "pencil_${System.currentTimeMillis()}"
                val count = gallerySaver.save(outputs, baseName)
                outputs.forEach { b -> runCatching { b.recycle() } }
                _state.update {
                    it.copy(
                        isSaving = false,
                        notice = if (count > 0) ImageNotice.Saved(count) else ImageNotice.SaveFailed,
                    )
                }
            } finally {
                outputs.forEach { b -> runCatching { b.recycle() } }
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    // ---- 打印 ----

    /** 主打印：全部页 × 份数（批量开关决定份数是否生效）。 */
    fun print() {
        val snapshot = _state.value
        if (snapshot.isPrinting || snapshot.pages.isEmpty()) return
        val indices = snapshot.pages.indices.toList()
        val copies = if (snapshot.batchEnabled) snapshot.copies else 1
        startPrintJob(indices, copies)
    }

    /** 单张打印：仅当前页，固定一份。 */
    fun printSingle() {
        val snapshot = _state.value
        if (snapshot.isPrinting || snapshot.pages.isEmpty()) return
        startPrintJob(listOf(snapshot.currentIndex.coerceIn(0, snapshot.pages.lastIndex)), copies = 1)
    }

    private fun startPrintJob(indices: List<Int>, copies: Int) {
        if (indices.isEmpty()) return
        val options = ImagePrintOptions(copies = copies)
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isPrinting = true,
                    lastResult = null,
                    progress = PrintProgress(PrintPhase.Preparing),
                )
            }
            val result = printerController.printImagePages(
                pageCount = indices.size,
                options = options,
                onProgress = { p -> _state.update { it.copy(progress = p) } },
                processSelector = { relativeIndex -> effectiveOptions(indices[relativeIndex]) },
            ) { relativeIndex ->
                // 控制器负责回收返回的位图。
                sources[indices[relativeIndex]].load()
            }
            _state.update { it.copy(isPrinting = false, progress = null, lastResult = result) }
        }
    }

    fun cancelPrint() = printerController.cancelPrint()

    // ---- 预览 ----

    /**
     * 防抖重渲染当前页预览：与真实打印共用 [ImagePreparer]（同旋转/缩放/算法），
     * 所见即所得。切页/拖阈值时只保留最后一次任务。
     */
    private fun schedulePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            delay(PREVIEW_DEBOUNCE_MS)
            val snapshot = _state.value
            val index = snapshot.currentIndex
            if (index !in sources.indices) {
                _state.update { it.copy(previewBitmap = null, isPreparing = false) }
                return@launch
            }
            _state.update { it.copy(isPreparing = true) }
            val pair = loadAndProcess(index, snapshot.processOptions)
            if (pair == null) {
                _state.update { it.copy(previewBitmap = null, isPreparing = false) }
                return@launch
            }
            val (raw, processed) = pair
            if (processed != raw) runCatching { raw.recycle() }
            _state.update { it.copy(previewBitmap = processed, isPreparing = false) }
        }
    }

    private fun loadAndProcess(
        index: Int,
        options: ImageProcessOptions,
    ): Pair<Bitmap, Bitmap>? = runCatching {
        val raw = sources[index].load()
        val processed = imagePreparer.prepare(raw, options)
        raw to processed
    }.getOrNull()

    private fun queryDisplayName(uri: Uri, contentResolver: ContentResolver): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx) ?: appContext.getString(R.string.image_fallback_file_name)
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: appContext.getString(R.string.image_fallback_file_name)
    }

    private fun pdfPageName(page: Int): String = appContext.getString(R.string.image_pdf_page_name, page + 1)

    private fun initialState(): ImageEditorUiState {
        val connected = (printerController.connectionState.value as? ConnectionState.Connected)?.device
        return ImageEditorUiState(
            connectedDevice = connected,
            isConnecting = printerController.connectionState.value is ConnectionState.Connecting,
        )
    }

    override fun onCleared() {
        super.onCleared()
        sources.forEach { s -> runCatching { s.close() } }
        sources.clear()
        pdfBundles.forEach { b -> runCatching { b.close() } }
        pdfBundles.clear()
        tempDirs.forEach(ImageArchiveImporter::cleanup)
        tempDirs.clear()
        discardPickSession()
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 150L
    }
}
