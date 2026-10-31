package com.pencil.chuangyin.feature.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.printer.ShotTransferBus
import com.pencil.chuangyin.printer.ShotTransferMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.imageprocessing.ColorMode
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/** 已扫描并确认的一页（成品文件与可重现的参数）。 */
data class ScanPage(
    val id: Long,
    val sourceFile: File,
    val outputFile: File,
    val detection: ScanDetection?,
    val autoColorMode: ColorMode,
    val colorMode: ColorMode,
    val rotationDegrees: Int,
)

/**
 * 编辑台：正在确认的新拍页，或从页面列表点开的已扫描页。
 * [isNew] 为 true 时确认后加入列表，关闭则丢弃。
 */
data class ScanEditor(
    val page: ScanPage,
    val bitmap: Bitmap,
    val isNew: Boolean,
)

/**
 * 文稿扫描状态机：
 * 拍摄/导入 → FairScan 检测与增强 → 编辑台（色彩模式/旋转）→ 页面列表 → 完成交给图片打印页。
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val engine: FairScanEngine,
    private val shotBus: ShotTransferBus,
) : ViewModel() {

    private val _pages = MutableStateFlow<List<ScanPage>>(emptyList())
    val pages: StateFlow<List<ScanPage>> = _pages.asStateFlow()

    /** 最近一次实时检测到的纸张四角（0..1 归一化，供取景叠加层绘制）。 */
    var liveQuad by mutableStateOf<List<android.graphics.PointF>?>(null)
        private set

    /** 最近一帧的宽高比（高/宽，竖屏 >1），叠加层按 FILL_CENTER 映射。 */
    var liveFrameAspect by mutableStateOf(1.333f)
        private set

    var isProcessing by mutableStateOf(false)
        private set

    var editor by mutableStateOf<ScanEditor?>(null)
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _completed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completed: SharedFlow<Unit> = _completed.asSharedFlow()

    private var pageSeq = 0L

    /** 取景器实时检测分析器（CameraX ImageAnalysis 用）。 */
    fun createAnalyzer(): ImageAnalysis.Analyzer = LiveDocumentAnalyzer(engine, ::onLiveDetection)

    /** 取景分析帧回调：仅在相机页使用，编辑/查看时停止更新。 */
    private fun onLiveDetection(quad: List<android.graphics.PointF>?, frameAspect: Float) {
        liveQuad = quad
        if (frameAspect > 0f) liveFrameAspect = frameAspect
    }

    /** 相机快门已把 JPEG 写入 [file]，开始识别与增强。 */
    fun onCaptured(file: File) {
        if (isProcessing) return
        processSource(file)
    }

    /** 相册导入：复制到扫描缓存目录后走与拍照相同的管线。 */
    fun importFromUri(uri: Uri) {
        if (isProcessing) return
        isProcessing = true
        viewModelScope.launch(Dispatchers.IO) {
            val dest = File(scanDir(), "src_${System.nanoTime()}.jpg")
            val copied = runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                dest.isFile && dest.length() > 0L
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                if (!copied) {
                    isProcessing = false
                    _messages.tryEmit(appContext.getString(R.string.scanner_import_failed))
                }
            }
            if (copied) processSource(dest)
        }
    }

    private fun processSource(sourceFile: File) {
        isProcessing = true
        viewModelScope.launch(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            var source: Bitmap? = null
            try {
                source = decodeSource(sourceFile)
                if (source == null) {
                    _messages.tryEmit(appContext.getString(R.string.scanner_decode_failed))
                    return@launch
                }
                val detection = engine.detect(source)
                val autoMode = detection?.autoColorMode ?: ColorMode.COLOR
                val id = nextId()
                val output = File(scanDir(), "out_$id.jpg")
                val page = ScanPage(
                    id = id,
                    sourceFile = sourceFile,
                    outputFile = output,
                    detection = detection,
                    autoColorMode = autoMode,
                    colorMode = autoMode,
                    rotationDegrees = 0,
                )
                bitmap = engine.render(source, detection, page.colorMode, page.rotationDegrees)
                writeOutput(bitmap, output)
                val rendered = bitmap
                withContext(Dispatchers.Main) {
                    editor = ScanEditor(page.copy(), bitmap = rendered, isNew = true)
                }
            } catch (_: Throwable) {
                _messages.tryEmit(appContext.getString(R.string.scanner_processing_failed))
            } finally {
                if (source != null && bitmap !== source) runCatching { source.recycle() }
                withContext(Dispatchers.Main) { isProcessing = false }
            }
        }
    }

    /** 切换编辑台的色彩模式并重新渲染成品。 */
    fun setEditorColorMode(mode: ColorMode) {
        val current = editor ?: return
        if (mode == current.page.colorMode) return
        editor = current.copy(page = current.page.copy(colorMode = mode))
        reRenderEditor()
    }

    /** 旋转 90°：真机拍摄常出现横向文字，旋转是最常用的人工修正。 */
    fun rotateEditor() {
        val current = editor ?: return
        val next = (current.page.rotationDegrees + 90) % 360
        editor = current.copy(page = current.page.copy(rotationDegrees = next))
        reRenderEditor()
    }

    private fun reRenderEditor() {
        val snapshot = editor ?: return
        isProcessing = true
        viewModelScope.launch(Dispatchers.IO) {
            var source: Bitmap? = null
            var bitmap: Bitmap? = null
            try {
                source = decodeSource(snapshot.page.sourceFile) ?: return@launch
                bitmap = engine.render(
                    source, snapshot.page.detection, snapshot.page.colorMode, snapshot.page.rotationDegrees,
                )
                writeOutput(bitmap, snapshot.page.outputFile)
                val rendered = bitmap
                withContext(Dispatchers.Main) {
                    // 用户可能已关闭编辑台：只在仍停留在同一页时更新。
                    val now = editor
                    if (now != null && now.page.id == snapshot.page.id) {
                        editor = now.copy(page = snapshot.page, bitmap = rendered)
                    }
                }
            } catch (_: Throwable) {
                _messages.tryEmit(appContext.getString(R.string.scanner_processing_failed))
            } finally {
                if (source != null && bitmap !== source) runCatching { source.recycle() }
                withContext(Dispatchers.Main) { isProcessing = false }
            }
        }
    }

    /** 确认编辑台：新页加入列表；已存在页直接更新参数（成品已写入）。 */
    fun confirmEditor() {
        val current = editor ?: return
        if (current.isNew) {
            _pages.value = _pages.value + current.page
        } else {
            _pages.value = _pages.value.map {
                if (it.id == current.page.id) current.page else it
            }
        }
        editor = null
    }

    /** 关闭编辑台：新页连同源图/成品一起丢弃。 */
    fun discardEditor() {
        val current = editor ?: return
        if (current.isNew) {
            runCatching { current.page.sourceFile.delete() }
            runCatching { current.page.outputFile.delete() }
        }
        editor = null
    }

    /** 从页面列表打开已扫描页查看/调整。 */
    fun openPage(page: ScanPage) {
        if (isProcessing) return
        isProcessing = true
        viewModelScope.launch(Dispatchers.IO) {
            var source: Bitmap? = null
            var bitmap: Bitmap? = null
            try {
                source = decodeSource(page.sourceFile) ?: return@launch
                bitmap = engine.render(source, page.detection, page.colorMode, page.rotationDegrees)
                writeOutput(bitmap, page.outputFile)
                val rendered = bitmap
                withContext(Dispatchers.Main) {
                    editor = ScanEditor(page, rendered, isNew = false)
                }
            } finally {
                if (source != null && bitmap !== source) runCatching { source.recycle() }
                withContext(Dispatchers.Main) { isProcessing = false }
            }
        }
    }

    fun deletePage(page: ScanPage) {
        _pages.value = _pages.value.filterNot { it.id == page.id }
        if (editor?.page?.id == page.id) editor = null
        runCatching { page.sourceFile.delete() }
        runCatching { page.outputFile.delete() }
    }

    /** 完成：把全部成品按扫描顺序交给图片打印页。 */
    fun finish() {
        val files = _pages.value.map { it.outputFile }.filter { it.isFile && it.length() > 0L }
        if (files.isEmpty()) {
            _messages.tryEmit(appContext.getString(R.string.scanner_no_pages))
            return
        }
        shotBus.offer(files, ShotTransferMode.APPEND_ALL)
        _completed.tryEmit(Unit)
    }

    private fun nextId(): Long {
        pageSeq += 1
        return System.nanoTime() + pageSeq
    }

    private fun scanDir(): File =
        File(appContext.cacheDir, SCAN_DIR).apply { runCatching { mkdirs() } }

    private fun writeOutput(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, OUTPUT_JPEG_QUALITY, output)
        }
    }

    /**
     * 解码源图：限制长边、按 EXIF 方向转正。
     * 部分机型 JPEG 只写方向标签而不旋转像素，不处理会让检测与成品整体歪 90°。
     */
    private fun decodeSource(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / 2 >= MAX_SOURCE_EDGE) {
            longest /= 2
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return if (!matrix.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) runCatching { decoded.recycle() } }
        } else {
            decoded
        }
    }

    companion object {
        const val SCAN_DIR = "scan_pages"
        private const val MAX_SOURCE_EDGE = 2600
        private const val OUTPUT_JPEG_QUALITY = 92
    }
}
