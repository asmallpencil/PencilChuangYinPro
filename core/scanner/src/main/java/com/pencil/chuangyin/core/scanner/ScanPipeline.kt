package com.pencil.chuangyin.core.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max

/**
 * 文档扫描算法第 2~4 步的纯函数管线：
 * 2) 四点透视校正（warpPerspective）；
 * 3) 非均匀光照校正（大尺度高斯光照场 + 逐像素增益）；
 * 4) 形态学文字提取（自适应阈值 + 黑帽补笔画 + 连通域保守去噪）。
 */
internal object ScanPipeline {

    private const val ILLUMINATION_EPS = 1e-8
    private const val HIST_BINS = 256

    /** 扫描/裁剪输出位图长边上限（打印仅需 384px 宽，2000 已远超需求）。 */
    const val MAX_OUTPUT_EDGE = 2000

    /** warp 前检测框向外扩边比例。 */
    private const val QUAD_MARGIN = 1.015f

    /**
     * 四点透视校正：把 [quad]（TL/TR/BR/BL）映射为轴对齐矩形，框外填白。
     * 在原始分辨率上执行以保留笔画细节；输出长边按 [maxEdge] 封顶，
     * 兼顾打印所需清晰度与编辑期（涂白撤销快照）的内存开销。
     */
    fun warpToBitmap(src: Bitmap, quad: List<PointF>, maxEdge: Int = MAX_OUTPUT_EDGE): Bitmap {
        val raw = orderPointsF(quad)
        // 检测框向外扩 1.5% 边距：吸收角点毫差，避免纸边内容被切（warp 后框外自然填白）。
        val cx = (raw[0].x + raw[1].x + raw[2].x + raw[3].x) / 4f
        val cy = (raw[0].y + raw[1].y + raw[2].y + raw[3].y) / 4f
        val ordered = raw.map {
            PointF(cx + (it.x - cx) * QUAD_MARGIN, cy + (it.y - cy) * QUAD_MARGIN)
        }
        var (targetW, targetH) = targetSize(ordered)
        val longest = maxOf(targetW, targetH)
        if (longest > maxEdge) {
            val s = maxEdge / longest
            targetW *= s
            targetH *= s
        }

        val rgba = Mat()
        val warped = Mat()
        val srcPts = MatOfPoint2f()
        val dstPts = MatOfPoint2f()
        var homography: Mat? = null
        try {
            Utils.bitmapToMat(src, rgba)
            srcPts.fromList(
                listOf(
                    Point(ordered[0].x.toDouble(), ordered[0].y.toDouble()),
                    Point(ordered[1].x.toDouble(), ordered[1].y.toDouble()),
                    Point(ordered[2].x.toDouble(), ordered[2].y.toDouble()),
                    Point(ordered[3].x.toDouble(), ordered[3].y.toDouble()),
                ),
            )
            dstPts.fromList(
                listOf(
                    Point(0.0, 0.0),
                    Point(targetW - 1.0, 0.0),
                    Point(targetW - 1.0, targetH - 1.0),
                    Point(0.0, targetH - 1.0),
                ),
            )
            homography = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            Imgproc.warpPerspective(
                rgba, warped, homography,
                Size(targetW, targetH),
                Imgproc.INTER_CUBIC,
                Core.BORDER_CONSTANT,
                Scalar(255.0, 255.0, 255.0, 255.0),
            )

            val out = Bitmap.createBitmap(targetW.toInt(), targetH.toInt(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warped, out)
            return out
        } finally {
            runCatching { rgba.release() }
            runCatching { warped.release() }
            runCatching { srcPts.release() }
            runCatching { dstPts.release() }
            runCatching { homography?.release() }
        }
    }

    /**
     * 第 3 步：光照场估计 + 增益补偿。
     * I_corr = I × median(L)/(L+ε)，L 为大尺度高斯模糊（σ≈图宽 1/8）。
     * 输入/输出均为 8U 单通道灰度。
     */
    fun correctIllumination(gray8: Mat): Mat {
        val kSize = oddCeil(gray8.cols() / 8, 31)
        val light = Mat()
        Imgproc.GaussianBlur(gray8, light, Size(kSize.toDouble(), kSize.toDouble()), 0.0)

        // 白纸占画面多数：按规格取光照场中位数作为目标亮度（256 桶直方图，代价低）。
        val target = medianGray(light).coerceIn(40.0, 230.0)

        val gray32 = Mat()
        val light32 = Mat()
        val denominator = Mat()
        val gain = Mat()
        val out32 = Mat()
        val out8 = Mat()
        var success = false
        try {
            gray8.convertTo(gray32, CvType.CV_32F)
            light.convertTo(light32, CvType.CV_32F)
            Core.add(light32, Scalar(ILLUMINATION_EPS), denominator)
            // 目标亮度常数图 / (光照场 + ε) = 逐像素增益。
            Mat(gray8.size(), CvType.CV_32F, Scalar(target)).use { base ->
                Core.divide(base, denominator, gain)
            }
            Core.multiply(gray32, gain, out32)
            out32.convertTo(out8, CvType.CV_8U)
            success = true
            return out8
        } finally {
            runCatching { light.release() }
            runCatching { gray32.release() }
            runCatching { light32.release() }
            runCatching { denominator.release() }
            runCatching { gain.release() }
            runCatching { out32.release() }
            if (!success) runCatching { out8.release() }
        }
    }

    /** 8U 灰度（含模糊后）图像的中位数亮度：256 桶直方图取累积中点。 */
    private fun medianGray(gray8: Mat): Double {
        val hist = Mat()
        try {
            Imgproc.calcHist(
                listOf(gray8),
                MatOfInt(0),
                Mat(),
                hist,
                MatOfInt(HIST_BINS),
                MatOfFloat(0f, 256f),
            )
            val total = gray8.rows().toDouble() * gray8.cols().toDouble()
            val half = total / 2.0
            var accumulated = 0.0
            for (bin in 0 until HIST_BINS) {
                accumulated += hist.get(bin, 0)?.get(0) ?: 0.0
                if (accumulated >= half) return bin + 0.5
            }
            return 128.0
        } finally {
            runCatching { hist.release() }
        }
    }

    /**
     * 第 4 步：形态学文字提取，输出 8U 单通道（白底 255 / 黑字 0）。
     *
     * - **双阈值自适应并集**：C=15 抓清晰笔画；C=8 补浅笔画——斜拍/透视残余下
     *   笔画粗细与对比不均，单阈值会把浅字吃掉（表现为"文字缺失"）；
     * - 黑帽变换（闭运算 − 原图）提取比周围暗的细笔画并与主体并集，保住细线；
     * - 连通域仅剔除极小且非细长的椒盐噪点（阈值刻意保守，避免误删笔画）。
     */
    fun extractText(gray8: Mat): Mat {
        val blockSize = oddCeil(max(gray8.cols() / 12, 31))
        val sharpStrokes = Mat()
        Imgproc.adaptiveThreshold(
            gray8, sharpStrokes, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
            blockSize, 15.0,
        )
        val faintStrokes = Mat()
        Imgproc.adaptiveThreshold(
            gray8, faintStrokes, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
            blockSize, 7.0,
        )

        val hatK = oddCeil(max(gray8.cols() / 50, 15))
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT, Size(hatK.toDouble(), hatK.toDouble()),
        )
        val blackHat = Mat()
        Imgproc.morphologyEx(gray8, blackHat, Imgproc.MORPH_BLACKHAT, kernel)
        runCatching { kernel.release() }

        val hatMask = Mat()
        Imgproc.threshold(blackHat, hatMask, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        runCatching { blackHat.release() }

        val strokes = Mat()
        Core.bitwise_or(sharpStrokes, faintStrokes, strokes)
        Core.bitwise_or(strokes, hatMask, strokes)
        runCatching { sharpStrokes.release() }
        runCatching { faintStrokes.release() }
        runCatching { hatMask.release() }

        removeSpeckles(strokes)

        val result = Mat()
        Core.bitwise_not(strokes, result)
        runCatching { strokes.release() }
        return result
    }

    /** 连通域去椒盐：只移除面积 ≤6 且外接框宽高均 ≤3px 的孤立小块，保留细笔画。 */
    private fun removeSpeckles(strokeMask: Mat) {
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        try {
            val count = Imgproc.connectedComponentsWithStats(
                strokeMask, labels, stats, centroids, 8, CvType.CV_32S,
            )
            val keep = BooleanArray(count) { true }
            keep[0] = false // 背景
            for (label in 1 until count) {
                val area = stats.get(label, Imgproc.CC_STAT_AREA)[0].toInt()
                val w = stats.get(label, Imgproc.CC_STAT_WIDTH)[0].toInt()
                val h = stats.get(label, Imgproc.CC_STAT_HEIGHT)[0].toInt()
                keep[label] = !(area <= 6 && w <= 3 && h <= 3)
            }
            val total = strokeMask.rows() * strokeMask.cols()
            val labelBuf = IntArray(total)
            val maskBuf = ByteArray(total)
            labels.get(0, 0, labelBuf)
            strokeMask.get(0, 0, maskBuf)
            for (i in 0 until total) {
                if (!keep[labelBuf[i]]) maskBuf[i] = 0
            }
            strokeMask.put(0, 0, maskBuf)
        } finally {
            runCatching { labels.release() }
            runCatching { stats.release() }
            runCatching { centroids.release() }
        }
    }

    /** PointF 四点排序为 TL/TR/BR/BL。 */
    fun orderPointsF(pts: List<PointF>): List<PointF> {
        val bySum = pts.sortedBy { it.x + it.y }
        val tl = bySum.first()
        val br = bySum.last()
        val byDiff = pts.sortedBy { it.y - it.x }
        val tr = byDiff.first()
        val bl = byDiff.last()
        return listOf(tl, tr, br, bl)
    }

    private fun targetSize(ordered: List<PointF>): Pair<Double, Double> {
        val (tl, tr, br, bl) = ordered
        val widthTop = hypot(tr.x - tl.x, tr.y - tl.y)
        val widthBottom = hypot(br.x - bl.x, br.y - bl.y)
        val heightRight = hypot(br.x - tr.x, br.y - tr.y)
        val heightLeft = hypot(bl.x - tl.x, bl.y - tl.y)
        val w = maxOf(widthTop, widthBottom).coerceAtLeast(1f).toDouble()
        val h = maxOf(heightRight, heightLeft).coerceAtLeast(1f).toDouble()
        return w to h
    }
}
