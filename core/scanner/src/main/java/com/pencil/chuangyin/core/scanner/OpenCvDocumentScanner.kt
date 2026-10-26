package com.pencil.chuangyin.core.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DocumentScanner] 的 OpenCV 实现。进程内单例，构造时完成 OpenCV 本地库初始化。
 *
 * 所有方法均为重计算，调用方必须放在后台线程。
 */
@Singleton
class OpenCvDocumentScanner @Inject constructor() : DocumentScanner {

    init {
        check(OpenCVLoader.initLocal()) { "OpenCV 本地库初始化失败" }
    }

    override fun scan(src: Bitmap, mode: ScanMode): ScanResult {
        require(!src.isRecycled) { "输入位图已回收" }
        if (mode == ScanMode.Normal) {
            // 常规拍摄：原图直出，不做任何处理（output 即入参，调用方不得回收入参）。
            return ScanResult(output = src, detectedQuad = null, warped = false, confidence = 0f)
        }

        val detected = runCatching { PageDetector.detect(src, PageDetector.DETECT_EDGE) }.getOrNull()
        val quad = detected?.first
        val confidence = detected?.second ?: 0f

        val baseBitmap: Bitmap = if (quad != null) {
            runCatching { ScanPipeline.warpToBitmap(src, quad) }.getOrNull() ?: src
        } else {
            src
        }
        val warped = quad != null && baseBitmap !== src

        val output: Bitmap = if (quad != null) {
            // 命中纸面：白底纯黑白件。
            runCatching { toDocumentBitmap(baseBitmap) }.getOrElse {
                if (baseBitmap === src) src.copy(Bitmap.Config.ARGB_8888, false) else baseBitmap
            }
        } else {
            // 未命中纸边（纸出界/叠纸/全白特写）：只做去阴影的灰度增强，保留背景层次
            // 便于用户辨认并手动裁剪；裁剪确认后会再 documentize 成黑白件。
            runCatching { toEnhancedGrayBitmap(baseBitmap) }.getOrElse {
                if (baseBitmap === src) src.copy(Bitmap.Config.ARGB_8888, false) else baseBitmap
            }
        }
        // warp 产生的中间位图在转换后不再需要。
        if (warped && baseBitmap !== output && baseBitmap !== src) {
            runCatching { baseBitmap.recycle() }
        }
        return ScanResult(output = output, detectedQuad = quad, warped = warped, confidence = confidence)
    }

    override fun detectQuad(src: Bitmap): List<PointF>? =
        // 用高精度档（1080）：调用方既有实时小帧（≤960px，不再缩放）也有拍摄大图
        // （2000px，若用 540 档初始角点误差会被放大近 4 倍）。
        runCatching { PageDetector.detect(src, PageDetector.DETECT_EDGE)?.first }.getOrNull()

    override fun cropWarp(src: Bitmap, quad: List<PointF>): Bitmap {
        require(quad.size == 4) { "裁剪四边形必须为 4 个角点" }
        return ScanPipeline.warpToBitmap(src, ScanPipeline.orderPointsF(quad))
    }

    override fun documentize(src: Bitmap): Bitmap {
        require(!src.isRecycled) { "输入位图已回收" }
        return runCatching { toDocumentBitmap(src) }.getOrDefault(
            src.copy(Bitmap.Config.ARGB_8888, false),
        )
    }

    /** 对给定（可能已校正的）位图执行 光照校正 → 文字提取，输出白底纯黑 Bitmap。 */
    private fun toDocumentBitmap(bitmap: Bitmap): Bitmap {
        val rgba = Mat()
        val gray = Mat()
        var illum: Mat? = null
        var text: Mat? = null
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val corrected = ScanPipeline.correctIllumination(gray)
            illum = corrected
            val strokes = ScanPipeline.extractText(corrected)
            text = strokes
            val out = Bitmap.createBitmap(strokes.cols(), strokes.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(strokes, out)
            return out
        } finally {
            runCatching { rgba.release() }
            runCatching { gray.release() }
            runCatching { illum?.release() }
            runCatching { text?.release() }
        }
    }

    /** 检测失败降级：光照校正后的灰度图（纸白字黑、背景保留层次，便于手动裁剪）。 */
    private fun toEnhancedGrayBitmap(bitmap: Bitmap): Bitmap {
        val rgba = Mat()
        val gray = Mat()
        var corrected: Mat? = null
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            corrected = ScanPipeline.correctIllumination(gray)
            val out = Bitmap.createBitmap(corrected.cols(), corrected.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(corrected, out)
            return out
        } finally {
            runCatching { rgba.release() }
            runCatching { gray.release() }
            runCatching { corrected?.release() }
        }
    }
}
