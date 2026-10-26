package com.pencil.chuangyin.feature.camera.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import com.pencil.chuangyin.core.scanner.DocumentScanner
import com.pencil.chuangyin.core.scanner.ScanMode

/**
 * 取景器实时纸张检测分析器：
 * 降采样帧（由 ImageAnalysis targetResolution 控制）→ 按帧旋转角转正 →
 * 调 [DocumentScanner.detectQuad] → 输出归一化角点（0..1）供叠加层绘制。
 *
 * - KEEP_ONLY_LATEST + 300ms 限频，保证预览流畅、不积压；
 * - 任何异常都吞掉并关闭帧，绝不因分析问题中断相机。
 *
 * 回调参数：归一化角点（0..1）+ 转正后帧的高宽比（竖屏 >1），供叠加层按 FILL_CENTER 映射。
 */
class DocumentAnalyzer(
    private val scanner: DocumentScanner,
    private val modeProvider: () -> ScanMode,
    private val onQuad: (List<Offset>?, Float) -> Unit,
) : androidx.camera.core.ImageAnalysis.Analyzer {

    @Volatile
    private var lastRunMs = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (modeProvider() != ScanMode.Document || now - lastRunMs < MIN_INTERVAL_MS) {
                return
            }
            lastRunMs = now

            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val oriented = if (rotation != 0) rotateBitmap(raw, rotation) else raw
            if (oriented !== raw) runCatching { raw.recycle() }

            val quad = scanner.detectQuad(oriented)
            val normalized = quad?.map {
                Offset(
                    (it.x / oriented.width).coerceIn(0f, 1f),
                    (it.y / oriented.height).coerceIn(0f, 1f),
                )
            }
            val frameAspect = oriented.height.toFloat() / oriented.width.toFloat()
            runCatching { oriented.recycle() }
            onQuad(normalized, frameAspect)
        } catch (_: Throwable) {
            // 单帧检测失败不影响后续帧。
        } finally {
            runCatching { image.close() }
        }
    }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private companion object {
        const val MIN_INTERVAL_MS = 300L
    }
}
