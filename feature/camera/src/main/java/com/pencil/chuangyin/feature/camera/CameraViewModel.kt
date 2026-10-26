package com.pencil.chuangyin.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pencil.chuangyin.core.scanner.DocumentScanner
import com.pencil.chuangyin.core.scanner.ScanMode
import com.pencil.chuangyin.feature.camera.camera.DocumentAnalyzer
import com.pencil.chuangyin.printer.ShotTransferBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/** 拍照页阶段：取景预览 → 自动处理 → 角点确认（文档模式）→ 结果确认。 */
sealed interface CameraPhase {
    data object Preview : CameraPhase
    data class Processing(val stage: ProcessStage) : CameraPhase
    /** 角点调整：用户核对/拖动四角后再透视拉直（自动检测仅提供初始建议）。 */
    data object Adjust : CameraPhase
    data object Result : CameraPhase
}

/** 处理动画阶段（仅驱动文案，实际算法一次完成，阶段顺序展示）。 */
enum class ProcessStage { Edge, Warp, Light, Text, Normal }

/**
 * 裁切方式（用户可选三档）：
 * - [Auto] 自动模式：实时橙框提示纸张位置，拍摄后自动找边+拉直+文档化；
 * - [Manual] 手动模式：无实时框，拍摄后进入角点调整页由用户拖动四角（无算法选点）；
 * - [None] 不处理：原图直出结果页。
 */
enum class CameraMode(val scanMode: ScanMode, val showLiveQuad: Boolean) {
    Auto(ScanMode.Document, true),
    Manual(ScanMode.Normal, false),
    None(ScanMode.Normal, false),
}

/** 实时检测到的纸张框：归一化四角（TL/TR/BR/BL）+ 转正分析帧的高宽比。 */
data class LiveQuad(
    val points: List<Offset>,
    val frameAspect: Float,
)

/** 一笔涂白（位图坐标系点列 + 位图像素笔宽）；撤销只存点列，按底图重放，避免多张全幅快照。 */
private data class WhiteStroke(val points: List<Offset>, val widthPx: Float)

@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val scanner: DocumentScanner,
    private val shotBus: ShotTransferBus,
) : ViewModel() {

    var mode by mutableStateOf(CameraMode.Auto)
        private set
    var phase by mutableStateOf<CameraPhase>(CameraPhase.Preview)
        private set
    var liveQuad by mutableStateOf<LiveQuad?>(null)
        private set
    var flashAuto by mutableStateOf(true)
        private set
    var resultBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var canUndo by mutableStateOf(false)
        private set
    /** 结果位图被编辑（涂白/撤销/裁剪）后自增，驱动 Canvas 重绘同一可变位图。 */
    var editGeneration by mutableIntStateOf(0)
        private set
    /** 结果页裁剪进行中（显示处理遮罩、封锁全部操作）。 */
    var cropBusy by mutableStateOf(false)
        private set
    /** 角点调整页"生成"进行中（显示处理遮罩、封锁全部操作）。 */
    var adjustBusy by mutableStateOf(false)
        private set
    /** 角点调整页底图（文档模式的原始拍摄图，生成后释放）。 */
    var adjustBitmap by mutableStateOf<Bitmap?>(null)
        private set
    /** 角点调整页当前四角（归一化 TL/TR/BR/BL）。 */
    var adjustQuad by mutableStateOf(DEFAULT_ADJUST_QUAD)
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    /** 用户确认（已写入传递总线），UI 收此事件后导航到图片打印页。 */
    private val _completed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completed = _completed.asSharedFlow()

    /** 裁剪流程结束：true=成功已换新图，false=失败保留原图与裁剪态。 */
    private val _cropDone = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val cropDone = _cropDone.asSharedFlow()

    private var rawFile: File? = null
    private var processingStartMs = 0L
    private var submitting = false

    /** 撤销重放基准（不可变底图，仅 1 张）+ 已提交笔画（仅点列，KB 级）。 */
    private var pristineBitmap: Bitmap? = null
    private val strokes = ArrayDeque<WhiteStroke>()

    /** 实时检测分析器（仅自动模式启用检测；分析帧经限频回调更新 [liveQuad]）。 */
    fun createAnalyzer(): DocumentAnalyzer =
        DocumentAnalyzer(scanner, modeProvider = { mode.scanMode }) { quad, aspect ->
            if (phase is CameraPhase.Preview) liveQuad = quad?.let { LiveQuad(it, aspect) }
        }

    fun selectMode(newMode: CameraMode) {
        if (mode == newMode) return
        mode = newMode
        liveQuad = null
    }

    fun toggleFlash(imageCapture: ImageCapture?) {
        flashAuto = !flashAuto
        runCatching {
            imageCapture?.flashMode = if (flashAuto) {
                ImageCapture.FLASH_MODE_AUTO
            } else {
                ImageCapture.FLASH_MODE_OFF
            }
        }
    }

    /** 快门：拍照到临时文件，回调后进入扫描管线。 */
    fun capture(imageCapture: ImageCapture) {
        if (phase !is CameraPhase.Preview) return
        beginProcessing()
        val dir = File(appContext.cacheDir, SHOT_DIR).apply { runCatching { mkdirs() } }
        val file = File(dir, "raw_${System.nanoTime()}.jpg")
        rawFile = file
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(appContext),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    viewModelScope.launch { processCapturedFile(file) }
                }

                override fun onError(exception: ImageCaptureException) {
                    failAndReset()
                }
            },
        )
    }

    /** 相册导入：复制为临时文件后走与拍照完全相同的处理管线。 */
    fun importFromUri(uri: Uri) {
        if (phase !is CameraPhase.Preview) return
        beginProcessing()
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) {
                val dir = File(appContext.cacheDir, SHOT_DIR).apply { runCatching { mkdirs() } }
                val file = File(dir, "pick_${System.nanoTime()}.jpg")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                } ?: return@withContext null
                file
            }
            if (copied == null) {
                failAndReset()
            } else {
                processCapturedFile(copied)
            }
        }
    }

    private fun beginProcessing() {
        processingStartMs = System.currentTimeMillis()
        val stage = when (mode) {
            CameraMode.Auto -> ProcessStage.Edge
            // 手动模式无算法选点，动画仅为拍照存盘间隙。
            CameraMode.Manual, CameraMode.None -> ProcessStage.Normal
        }
        phase = CameraPhase.Processing(stage)
        liveQuad = null
    }

    private suspend fun processCapturedFile(file: File) {
        val minDurationMs = if (mode == CameraMode.Auto) DOC_MIN_MS else NORMAL_MIN_MS
        try {
            when (mode) {
                CameraMode.Auto -> {
                    // 自动模式：检测→拉直→文档化一气呵成，直接出结果。
                    val (pristine, mutable) = withContext(Dispatchers.IO) {
                        val source = decodeSampled(file)
                        val scanResult = scanner.scan(source, ScanMode.Document)
                        val base = scanResult.output
                        val pristineCopy = base.copy(Bitmap.Config.ARGB_8888, false)
                        val out = base.copy(Bitmap.Config.ARGB_8888, true)
                        if (base !== source) runCatching { base.recycle() }
                        runCatching { source.recycle() }
                        pristineCopy to out
                    }
                    val elapsed = System.currentTimeMillis() - processingStartMs
                    if (elapsed < minDurationMs) kotlinx.coroutines.delay(minDurationMs - elapsed)
                    clearEdits()
                    pristineBitmap = pristine
                    resultBitmap = mutable
                    editGeneration++
                    phase = CameraPhase.Result
                }
                CameraMode.Manual -> {
                    // 手动模式：不做任何算法选点，直接进角点调整页（拖四角后手动生成）。
                    val source = withContext(Dispatchers.IO) { decodeSampled(file) }
                    val elapsed = System.currentTimeMillis() - processingStartMs
                    if (elapsed < minDurationMs) kotlinx.coroutines.delay(minDurationMs - elapsed)
                    clearEdits()
                    adjustQuad = DEFAULT_ADJUST_QUAD
                    adjustBitmap = source
                    phase = CameraPhase.Adjust
                }
                CameraMode.None -> {
                    // 不处理：原图直出（仅规范化位图，便于涂白/裁剪编辑）。
                    val (pristine, mutable) = withContext(Dispatchers.IO) {
                        val source = decodeSampled(file)
                        val pristineCopy = source.copy(Bitmap.Config.ARGB_8888, false)
                        val out = source.copy(Bitmap.Config.ARGB_8888, true)
                        runCatching { source.recycle() }
                        pristineCopy to out
                    }
                    val elapsed = System.currentTimeMillis() - processingStartMs
                    if (elapsed < minDurationMs) kotlinx.coroutines.delay(minDurationMs - elapsed)
                    clearEdits()
                    pristineBitmap = pristine
                    resultBitmap = mutable
                    editGeneration++
                    phase = CameraPhase.Result
                }
            }
        } catch (_: Throwable) {
            failAndReset()
        } finally {
            runCatching { if (file.exists()) file.delete() }
            rawFile = null
        }
    }

    /**
     * 角点调整页"生成"：按用户四角做透视拉直 + 去阴影 + 黑白文档化，进入结果页。
     * 失败时留在调整页，用户可修正角点重试。
     */
    fun applyAdjust(quad: List<Offset>) {
        val source = adjustBitmap ?: return
        if (adjustBusy) return
        adjustBusy = true
        viewModelScope.launch {
            var success = false
            try {
                val (pristine, mutable) = withContext(Dispatchers.IO) {
                    val pts = quad.map {
                        PointF(
                            (it.x.coerceIn(0f, 1f) * source.width),
                            (it.y.coerceIn(0f, 1f) * source.height),
                        )
                    }
                    val warped = scanner.cropWarp(source, pts)
                    val doc = runCatching { scanner.documentize(warped) }.getOrElse { warped }
                    val base = if (doc !== warped) {
                        runCatching { warped.recycle() }
                        doc
                    } else {
                        doc
                    }
                    val pristineCopy = base.copy(Bitmap.Config.ARGB_8888, false)
                    val out = base.copy(Bitmap.Config.ARGB_8888, true)
                    if (base !== source) runCatching { base.recycle() }
                    pristineCopy to out
                }
                clearEdits()
                pristineBitmap = pristine
                resultBitmap = mutable
                editGeneration++
                adjustBitmap = null
                phase = CameraPhase.Result
                success = true
            } catch (_: Throwable) {
                _messages.tryEmit(
                    appContext.getString(R.string.camera_adjust_failed),
                )
            } finally {
                adjustBusy = false
            }
            _cropDone.tryEmit(success)
        }
    }

    /** 大叉：丢弃结果/角点调整回取景器。 */
    fun retake() {
        clearEdits()
        phase = CameraPhase.Preview
    }

    /**
     * 提交一笔涂白（位图坐标系点列、位图像素笔宽）：
     * 笔画仅记录点列用于撤销重放，白像素直接写入当前可变位图。
     */
    fun commitStroke(points: List<Offset>, strokeWidthPx: Float) {
        val current = resultBitmap ?: return
        if (points.isEmpty()) return
        strokes.addLast(WhiteStroke(points, strokeWidthPx))
        paintStroke(current, points, strokeWidthPx)
        canUndo = true
        editGeneration++
    }

    /** 撤销最近一笔：以底图为基准重放剩余笔画（不保存任何全幅快照）。 */
    fun undo() {
        val base = pristineBitmap ?: return
        val current = resultBitmap ?: return
        if (strokes.isEmpty()) return
        strokes.removeLast()
        val rebuilt = base.copy(Bitmap.Config.ARGB_8888, /* isMutable = */ true)
        strokes.forEach { paintStroke(rebuilt, it.points, it.widthPx) }
        // 旧图 [current] 刚被 UI 绘制过：绝不能 recycle（RenderThread 滞后帧仍持有引用，
        // 回收会导致 "Canvas: trying to use a recycled bitmap" 闪退），直接交给 GC。
        resultBitmap = rebuilt
        canUndo = strokes.isNotEmpty()
        editGeneration++
    }

    /** 裁剪确认：quad 为 4 个归一化角点（TL/TR/BR/BL），在 IO 线程做透视裁剪。 */
    fun applyCrop(quad: List<Offset>) {
        val current = resultBitmap ?: return
        if (cropBusy) return
        cropBusy = true
        viewModelScope.launch {
            var success = false
            try {
                val cropped = withContext(Dispatchers.IO) {
                    val pts = quad.map {
                        PointF(
                            (it.x.coerceIn(0f, 1f) * current.width),
                            (it.y.coerceIn(0f, 1f) * current.height),
                        )
                    }
                    val warped = scanner.cropWarp(current, pts)
                    // 裁剪模式下手动裁剪即收尾：框内已基本只有纸面，直接文档化成黑白件
                    // （对此前检测失败而降级为灰度的图，这一步补上二值化）。
                    // 「不处理」模式裁剪保持原图色彩，不做文档化。
                    val doc = if (mode != CameraMode.None) {
                        runCatching { scanner.documentize(warped) }.getOrNull()
                    } else {
                        null
                    }
                    if (doc != null && doc !== warped) {
                        runCatching { warped.recycle() }
                        doc.copy(Bitmap.Config.ARGB_8888, true).also {
                            if (it !== doc) runCatching { doc.recycle() }
                        }
                    } else {
                        warped.copy(Bitmap.Config.ARGB_8888, true).also {
                            if (it !== warped) runCatching { warped.recycle() }
                        }
                    }
                }
                // 裁剪结果成为新底图：旧笔画与像素全部作废。
                // [current] 是 UI 正在显示的位图、旧 pristine 也可能被 undo 重放引用：
                // 一律不 recycle（RenderThread 滞后帧竞态），交给 GC。
                val newPristine = cropped.copy(Bitmap.Config.ARGB_8888, false)
                pristineBitmap = newPristine
                strokes.clear()
                canUndo = false
                resultBitmap = cropped
                editGeneration++
                success = true
            } catch (_: Throwable) {
                // 裁剪失败保留原图与裁剪态，提示用户重试。
                _messages.tryEmit(
                    appContext.getString(com.pencil.chuangyin.feature.camera.R.string.camera_crop_failed),
                )
            } finally {
                cropBusy = false
            }
            _cropDone.tryEmit(success)
        }
    }

    /** 大勾：结果位图写缓存 PNG → 传递总线 → 通知导航。 */
    fun confirm() {
        val bitmap = resultBitmap
        if (bitmap == null || submitting) return
        submitting = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val dir = File(appContext.cacheDir, SHOT_DIR).apply { runCatching { mkdirs() } }
                    val file = File(dir, "shot_${System.nanoTime()}.png")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    shotBus.offer(listOf(file))
                }
                _completed.tryEmit(Unit)
            } catch (_: Throwable) {
                submitting = false
                _messages.tryEmit(
                    appContext.getString(com.pencil.chuangyin.feature.camera.R.string.camera_process_failed),
                )
            }
        }
    }

    private fun failAndReset() {
        _messages.tryEmit(appContext.getString(com.pencil.chuangyin.feature.camera.R.string.camera_process_failed))
        rawFile?.let { runCatching { if (it.exists()) it.delete() } }
        rawFile = null
        phase = CameraPhase.Preview
    }

    /**
     * 释放编辑态引用（重拍/处理新图/VM 销毁时调用）。
     * 只解除引用，**绝不 recycle**：被替换的位图刚被 Compose（RenderThread）绘制过，
     * 滞后帧仍持有引用，显式回收会造成随机闪退；2000px 位图峰值多滞留 1~2 张
     * （约 24MB），由 GC 兜底，收益远大于崩溃风险。
     */
    private fun clearEdits() {
        resultBitmap = null
        pristineBitmap = null
        adjustBitmap = null
        adjustQuad = DEFAULT_ADJUST_QUAD
        strokes.clear()
        canUndo = false
    }

    override fun onCleared() {
        super.onCleared()
        clearEdits()
    }

    /** 在可变位图上用白色圆头画笔绘制一笔（单点按圆点处理）。 */
    private fun paintStroke(bitmap: Bitmap, points: List<Offset>, strokeWidthPx: Float) {
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = strokeWidthPx
        }
        if (points.size == 1) {
            val dot = Paint(paint).apply { style = Paint.Style.FILL }
            canvas.drawCircle(points[0].x, points[0].y, strokeWidthPx / 2f, dot)
            return
        }
        val path = android.graphics.Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }

    private fun decodeSampled(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / 2 >= MAX_SOURCE_EDGE) {
            longest /= 2
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error(appContext.getString(R.string.camera_decode_failed))
        // 按 EXIF 方向归一化（部分机型 JPEG 只写方向标签而不旋转像素）。
        val orientation = runCatching {
            androidx.exifinterface.media.ExifInterface(file.absolutePath)
                .getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                )
        }.getOrDefault(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
        val matrix = android.graphics.Matrix()
        when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return if (!matrix.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) runCatching { decoded.recycle() } }
        } else {
            decoded
        }
    }

    private companion object {
        const val SHOT_DIR = ShotTransferBus.SHOT_DIR
        const val MAX_SOURCE_EDGE = 2000
        const val DOC_MIN_MS = 1200L
        const val NORMAL_MIN_MS = 400L

        /** 默认角点：四边内缩 8%（无可用自动检测时的起点）。 */
        val DEFAULT_ADJUST_QUAD = listOf(
            Offset(0.08f, 0.08f),
            Offset(0.92f, 0.08f),
            Offset(0.92f, 0.92f),
            Offset(0.08f, 0.92f),
        )
    }
}
