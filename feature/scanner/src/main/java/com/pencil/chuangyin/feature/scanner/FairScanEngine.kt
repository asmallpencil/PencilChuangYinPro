/*
 * FairScan 文稿扫描算法封装。
 *
 * 算法核心来自 FairScan（https://github.com/pynicolas/FairScan），
 * 依 GPL-3.0 内嵌于 third_party/fairscan-imageprocessing。
 */
package com.pencil.chuangyin.feature.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.Mask
import org.fairscan.imageprocessing.Mode
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.fairscan.imageprocessing.autoColorMode
import org.fairscan.imageprocessing.detectDocumentQuad
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.resizeForMaxPixels
import org.fairscan.imageprocessing.rotate
import org.fairscan.imageprocessing.scaledTo
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一次文档检测的结果。
 *
 * [quad] 位于 [maskWidth] × [maskHeight] 的检测坐标系（检测前会把图像缩到约 100 万像素），
 * 因此 [normalized] 给出与源图尺寸无关的 0..1 四角（TL/TR/BR/BL）。
 */
data class ScanDetection(
    val quad: Quad,
    val maskWidth: Int,
    val maskHeight: Int,
    val autoColorMode: ColorMode,
) {
    fun normalized(): List<PointF> = listOf(
        quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft,
    ).map { PointF((it.x / maskWidth).toFloat(), (it.y / maskHeight).toFloat()) }
}

/** 以 4 个归一化角点还原检测结果（用于从保存的页面参数重新处理）。 */
fun detectionFromNormalized(
    points: List<PointF>,
    autoColorMode: ColorMode,
): ScanDetection? {
    if (points.size != 4) return null
    val quad = Quad(
        topLeft = Point(points[0].x.toDouble(), points[0].y.toDouble()),
        topRight = Point(points[1].x.toDouble(), points[1].y.toDouble()),
        bottomRight = Point(points[2].x.toDouble(), points[2].y.toDouble()),
        bottomLeft = Point(points[3].x.toDouble(), points[3].y.toDouble()),
    )
    return ScanDetection(quad, 1, 1, autoColorMode)
}

/**
 * FairScan 算法在 Android 上的落点：
 * 纸张掩膜（启发式前景分割）→ FairScan 四边形拟合 → 透视校正 → Retinex/局部阈值增强。
 *
 * 所有方法均为重计算，调用方必须在后台线程执行。
 */
@Singleton
class FairScanEngine @Inject constructor() {

    init {
        check(OpenCVLoader.initLocal()) { "OpenCV 本地库初始化失败" }
    }

    /** 检测纸张四角；未检测到返回 null（调用方按整图处理）。 */
    fun detect(bitmap: Bitmap): ScanDetection? {
        val rgba = Mat()
        val bgr = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            return detectFromBgr(bgr)
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { rgba.release() }
            runCatching { bgr.release() }
        }
    }

    private fun detectFromBgr(source: Mat): ScanDetection? {
        val working = resizeForMaxPixels(source, MASK_MAX_PIXELS.toDouble())
        val maskMat = Mat()
        try {
            buildDocumentMask(working, maskMat)
            val width = working.cols()
            val height = working.rows()
            val mask = BinaryMask(width, height, maskMat)
            val quad = detectDocumentQuad(mask, ImageSize(width, height), Mode.CAPTURE) ?: return null
            val auto = runCatching {
                autoColorMode(working, mask, quad)
            }.getOrDefault(ColorMode.COLOR)
            return ScanDetection(quad, width, height, auto)
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { working.release() }
            runCatching { maskMat.release() }
        }
    }

    /**
     * 按检测结果渲染成品位图：透视拉直 + FairScan 增强（彩色 Retinex / 灰度 / 黑白局部阈值）。
     * [detection] 为 null 时只做等比缩放与旋转，保留整幅画面。
     */
    fun render(
        source: Bitmap,
        detection: ScanDetection?,
        colorMode: ColorMode,
        rotationDegrees: Int,
    ): Bitmap {
        val rgba = Mat()
        val bgr = Mat()
        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            val output = if (detection != null) {
                val quad = detection.quad.scaledTo(
                    detection.maskWidth, detection.maskHeight, bgr.cols(), bgr.rows(),
                )
                extractDocument(bgr, quad, rotationDegrees, colorMode, MAX_OUTPUT_PIXELS)
            } else {
                val resized = resizeForMaxPixels(bgr, MAX_OUTPUT_PIXELS.toDouble())
                val rotated = rotate(resized, rotationDegrees)
                runCatching { resized.release() }
                rotated
            }
            try {
                return output.toBitmap()
            } finally {
                runCatching { output.release() }
            }
        } catch (_: Throwable) {
            // 算法失败时退回原图，绝不让用户丢掉这一页。
            return source
        } finally {
            runCatching { rgba.release() }
            runCatching { bgr.release() }
        }
    }

    /** 启发式纸张掩膜：画面中最亮的最大连通区域（白纸在深色桌面上最稳定）。 */
    private fun buildDocumentMask(src: Mat, out: Mat) {
        val gray = Mat()
        val blurred = Mat()
        val bright = Mat()
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(25.0, 25.0))
        val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(13.0, 13.0))
        try {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.threshold(
                blurred, bright, 0.0, 255.0,
                Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU,
            )
            // 纸上的文字会在亮区挖出大量孔洞：闭运算填补，开运算去掉零散噪点。
            Imgproc.morphologyEx(bright, bright, Imgproc.MORPH_CLOSE, closeKernel)
            Imgproc.morphologyEx(bright, bright, Imgproc.MORPH_OPEN, openKernel)
            keepLargestBlob(bright)
            bright.copyTo(out)
        } finally {
            runCatching { gray.release() }
            runCatching { blurred.release() }
            runCatching { bright.release() }
            runCatching { closeKernel.release() }
            runCatching { openKernel.release() }
        }
    }

    private fun keepLargestBlob(mask: Mat) {
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val kept = Mat()
        try {
            val count = Imgproc.connectedComponentsWithStats(
                mask, labels, stats, centroids, 8, CvType.CV_32S,
            )
            if (count <= 1) return
            var best = -1
            var bestArea = 0
            for (label in 1 until count) {
                val area = stats.get(label, Imgproc.CC_STAT_AREA)[0].toInt()
                if (area > bestArea) {
                    bestArea = area
                    best = label
                }
            }
            if (best < 0) return
            Core.compare(labels, Scalar(best.toDouble()), kept, Core.CMP_EQ)
            kept.copyTo(mask)
        } finally {
            runCatching { labels.release() }
            runCatching { stats.release() }
            runCatching { centroids.release() }
            runCatching { kept.release() }
        }
    }

    private fun Mat.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(this, bitmap)
        return bitmap
    }

    /** FairScan 的 [Mask] 契约实现：0/255 单通道二值掩膜。 */
    private class BinaryMask(
        override val width: Int,
        override val height: Int,
        private val mat: Mat,
    ) : Mask {
        override fun toMat(): Mat = mat
    }

    private companion object {
        /** 检测前降采样上限：约 100 万像素，兼顾实时性与角点精度。 */
        const val MASK_MAX_PIXELS = 1_000_000

        /** 成品像素上限（约 300 万，长边约 1700px），足够打印且不撑爆内存。 */
        const val MAX_OUTPUT_PIXELS = 3_000_000L
    }
}
