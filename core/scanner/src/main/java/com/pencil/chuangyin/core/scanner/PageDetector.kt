package com.pencil.chuangyin.core.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 纸张四角检测（双路径并行，取高分者）：
 *
 * 路径 A「白纸 Mask」（主）：Otsu 二值分离 白纸/暗背景 → 形态学开闭清理 → 最大外轮廓
 * → 四边形近似/凸包/minAreaRect。对 纸张部分出界（边缘不闭合）、多纸叠放（纸间无对比）、
 * 纸面高占比特写（超过边框法上限）三类此前必失败的场景鲁棒——只要纸比背景亮就能成 mask。
 *
 * 路径 B「Canny 边缘」（辅）：Otsu 派生双阈值 Canny → 闭运算连断边 → 轮廓 → 四边形近似。
 * 对彩色/低对比背景仍是有效补充。
 *
 * 检测在降采样图上进行，输出角点按缩放比映射回原图坐标。
 */
internal object PageDetector {

    /** Canny 路径候选面积占比下限。 */
    private const val MIN_AREA_RATIO = 0.15f

    /** Canny 路径候选面积占比上限（避免选到整幅图像边框）。 */
    private const val MAX_AREA_RATIO = 0.985f

    /** Mask 路径面积下限（白纸大面积区域才可信）。 */
    private const val MASK_MIN_AREA_RATIO = 0.20f

    /** Mask 路径面积上限：允许纸面特写占满画面（0.985 上限会漏掉合法特写）。 */
    private const val MASK_MAX_AREA_RATIO = 0.995f

    /** Canny 路径居中度惩罚系数（放宽：斜拍时纸框质心天然偏中心，过严会误杀合法大框）。 */
    private const val EDGE_CENTER_LAMBDA = 1.6f

    /** Mask 路径居中度惩罚系数（宽松：纸张出界时质心天然偏移）。 */
    private const val MASK_CENTER_LAMBDA = 1.2f

    /** 多边形近似 epsilon 序列（相对周长），逐级放松以提高召回；末档处理出界纸的多边形凸包。 */
    private val EPSILON_RATIOS = doubleArrayOf(0.02, 0.035, 0.05, 0.08, 0.12)

    /**
     * 检测纸张四角。
     *
     * @param src 原始位图。
     * @param maxEdge 检测用降采样长边（取景器实时检测用小值，拍摄后用大值提精度）。
     * @return TL/TR/BR/BL（基于 [src] 原图像素坐标）及置信度；检测不到返回 null。
     */
    fun detect(src: Bitmap, maxEdge: Int = DETECT_EDGE): Pair<List<PointF>, Float>? {
        val full = Mat()
        Utils.bitmapToMat(src, full)
        try {
            val scale = min(1.0, maxEdge.toDouble() / max(full.cols(), full.rows()))
            val small = if (scale < 1.0) {
                Mat().also {
                    Imgproc.resize(
                        full, it,
                        Size(full.cols() * scale, full.rows() * scale),
                        0.0, 0.0, Imgproc.INTER_AREA,
                    )
                }
            } else {
                full
            }
            try {
                val (quadSmall, confidence) = detectOnSmall(small) ?: return null
                val inv = 1.0 / scale
                return quadSmall.map { PointF((it.x * inv).toFloat(), (it.y * inv).toFloat()) } to confidence
            } finally {
                if (small !== full) runCatching { small.release() }
            }
        } finally {
            runCatching { full.release() }
        }
    }

    /** 在（可能已降采样的）RGBA Mat 上执行双路径检测，返回小图坐标四点。 */
    private fun detectOnSmall(rgba: Mat): Pair<List<Point>, Float>? {
        val w = rgba.cols()
        val h = rgba.rows()
        val imageArea = w.toDouble() * h.toDouble()

        val gray = Mat()
        val blurred = Mat()
        val binary = Mat()
        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            val otsu = Imgproc.threshold(
                blurred, binary, 0.0, 255.0,
                Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU,
            )

            // 路径 A：白纸 Mask（局部自适应，对光照不均鲁棒）。
            var best = detectByPaperMask(blurred, imageArea, w, h)

            // 路径 B：Otsu 派生 Canny 边缘。
            val low = max(20.0, otsu * 0.5).coerceAtMost(120.0)
            val high = (otsu * 1.5).coerceIn(80.0, 240.0)
            val edgeCandidate = detectByEdges(blurred, low, high, imageArea, w, h)
            if (edgeCandidate != null && (best == null || edgeCandidate.score > best.score)) {
                best = edgeCandidate
            }

            val quad = best?.let { orderPoints(it.points) } ?: return null
            val valid = quad.none {
                it.x.isNaN() || it.y.isNaN() || it.x < 0 || it.y < 0 || it.x > w || it.y > h
            }
            if (!valid) return null
            return quad to best.score.coerceIn(0f, 1f)
        } finally {
            runCatching { gray.release() }
            runCatching { blurred.release() }
            runCatching { binary.release() }
        }
    }

    /**
     * 路径 A：白纸连通域。
     * 输入 [gray] 为去噪灰度图。核心是**大核局部自适应阈值**：纸永远比其邻域亮，
     * 因此即使照片下部光照偏暗、书页有沟壑，纸面整体仍能入 mask（全局 Otsu 会把
     * 暗端的纸丢掉，导致框罩不住整纸）。开运算去细杂物，闭运算弥合书脊沟与文字洞，
     * 最大外轮廓即纸张（可见部分/整叠）。
     */
    private fun detectByPaperMask(gray: Mat, imageArea: Double, w: Int, h: Int): Candidate? {
        val mask = Mat()
        var closeKernel: Mat? = null
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        try {
            val blockSize = oddSize(max(51.0, min(w, h) / 3.0)).toInt()
            Imgproc.adaptiveThreshold(
                gray, mask, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                blockSize,
                -3.0,
            )

            val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, openKernel)
            runCatching { openKernel.release() }

            val closeSide = oddSize(max(11.0, min(w, h) / 60.0))
            closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(closeSide, closeSide))
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, closeKernel)

            Imgproc.findContours(
                mask, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE,
            )

            var largest: MatOfPoint? = null
            var largestArea = 0.0
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area > largestArea) {
                    largestArea = area
                    largest = contour
                }
            }
            largest ?: return null
            if (largestArea < MASK_MIN_AREA_RATIO * imageArea ||
                largestArea > MASK_MAX_AREA_RATIO * imageArea
            ) {
                return null
            }

            // 出界纸张的凸包是被图像边界裁剪的多边形，末档 eps 压成 4 点；
            // 畸形近似（边长失衡/丢角）被拒绝后由 minAreaRect 稳定兜底。
            val quad = approximateQuad(largest) ?: minAreaRectQuad(largest) ?: return null
            val score = scoreCandidate(quad, largestArea, imageArea, w, h, MASK_CENTER_LAMBDA)
            return if (score > 0f) Candidate(quad, score) else null
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { mask.release() }
            runCatching { closeKernel?.release() }
            runCatching { hierarchy.release() }
            contours.forEach { runCatching { it.release() } }
        }
    }

    /** 路径 B：Canny 边缘 + 轮廓四边形近似。 */
    private fun detectByEdges(
        blurred: Mat,
        low: Double,
        high: Double,
        imageArea: Double,
        w: Int,
        h: Int,
    ): Candidate? {
        val edges = Mat()
        val closed = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        try {
            Imgproc.Canny(blurred, edges, low, high)
            val closeKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE,
                Size(5.0, 5.0),
            )
            Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, closeKernel)
            runCatching { closeKernel.release() }

            Imgproc.findContours(
                closed, contours, hierarchy,
                Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE,
            )

            var best: Candidate? = null
            var largestContour: MatOfPoint? = null
            var largestArea = 0.0
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area > largestArea) {
                    largestArea = area
                    largestContour = contour
                }
                if (area < MIN_AREA_RATIO * imageArea || area > MAX_AREA_RATIO * imageArea) continue
                val quad = approximateQuad(contour) ?: continue
                val score = scoreCandidate(quad, area, imageArea, w, h, EDGE_CENTER_LAMBDA)
                if (score > 0f && (best == null || score > best.score)) {
                    best = Candidate(quad, score)
                }
            }

            // 兜底：最大轮廓的最小外接旋转矩形。
            if (best == null && largestContour != null &&
                largestArea >= MIN_AREA_RATIO * imageArea &&
                largestArea <= MAX_AREA_RATIO * imageArea
            ) {
                minAreaRectQuad(largestContour)?.let { fallback ->
                    val score = scoreCandidate(fallback, largestArea, imageArea, w, h, EDGE_CENTER_LAMBDA)
                    if (score > 0f) best = Candidate(fallback, score)
                }
            }
            return best
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { edges.release() }
            runCatching { closed.release() }
            runCatching { hierarchy.release() }
            contours.forEach { runCatching { it.release() } }
        }
    }

    /** 对单个轮廓尝试直接近似；失败则取凸包再近似，返回有序前的 4 个顶点。 */
    private fun approximateQuad(contour: MatOfPoint): List<Point>? {
        val contour2f = MatOfPoint2f(*contour.toArray())
        val refArea = Imgproc.contourArea(contour)
        try {
            val peri = Imgproc.arcLength(contour2f, true)
            if (peri <= 0) return null
            for (ratio in EPSILON_RATIOS) {
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, ratio * peri, true)
                val pts = approx.toArray().toList()
                runCatching { approx.release() }
                if (pts.size == 4 && isConvex(pts) && isSaneQuad(pts, refArea)) return pts
            }
            // 凸包后再近似：处理边缘带小凹口/被图像边界裁剪的纸张。convexHull 输出为顶点索引。
            val hullIndices = MatOfInt()
            Imgproc.convexHull(contour, hullIndices)
            val contourPts = contour.toArray()
            val hullPts = hullIndices.toArray().map { contourPts[it] }
            runCatching { hullIndices.release() }
            if (hullPts.size < 4) return null
            val hull2f = MatOfPoint2f(*hullPts.toTypedArray())
            try {
                val hullPeri = Imgproc.arcLength(hull2f, true)
                for (ratio in EPSILON_RATIOS) {
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(hull2f, approx, ratio * hullPeri, true)
                    val pts = approx.toArray().toList()
                    runCatching { approx.release() }
                    if (pts.size == 4 && isConvex(pts) && isSaneQuad(pts, refArea)) return pts
                }
            } finally {
                runCatching { hull2f.release() }
            }
            return null
        } finally {
            runCatching { contour2f.release() }
        }
    }

    /**
     * 四边形形状合理性（防"乱的"拉平输出）：
     * ① 边长均衡：最短边 ≥ 最长边 18%，拒绝退化成三角形的畸形近似；
     * ② 面积保真：四边形面积 ≥ 原轮廓面积 60%，拒绝把 L 形/多角轮廓硬压成 4 点时丢角。
     */
    private fun isSaneQuad(pts: List<Point>, refArea: Double): Boolean {
        if (!isNonDegenerate(pts)) return false
        var minEdge = Double.MAX_VALUE
        var maxEdge = 0.0
        for (i in 0 until 4) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            val e = hypot(a.x - b.x, a.y - b.y)
            minEdge = min(minEdge, e)
            maxEdge = max(maxEdge, e)
        }
        if (maxEdge <= 0 || minEdge < 0.18 * maxEdge) return false
        val m2f = MatOfPoint2f(*pts.toTypedArray())
        val quadArea = try {
            Imgproc.contourArea(m2f)
        } finally {
            runCatching { m2f.release() }
        }
        return quadArea >= 0.6 * refArea
    }

    /** OpenCV 的 isContourConvex 要求 [MatOfPoint]（非 2f）。 */
    private fun isConvex(pts: List<Point>): Boolean {
        val mop = MatOfPoint(*pts.toTypedArray())
        return try {
            Imgproc.isContourConvex(mop)
        } finally {
            runCatching { mop.release() }
        }
    }

    /** 排除边长过短或面积趋零的退化四边形。 */
    private fun isNonDegenerate(pts: List<Point>): Boolean {
        if (pts.size != 4) return false
        var minEdge = Double.MAX_VALUE
        for (i in 0 until 4) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            minEdge = min(minEdge, hypot(a.x - b.x, a.y - b.y))
        }
        val mop = MatOfPoint(*pts.toTypedArray())
        val area = Imgproc.contourArea(mop)
        runCatching { mop.release() }
        return minEdge > 24.0 && area > 1000.0
    }

    /** 综合评分：面积充分性 × 矩形度（四边形填充其最小外接矩形的比例）× 居中度。 */
    private fun scoreCandidate(
        pts: List<Point>,
        area: Double,
        imageArea: Double,
        w: Int,
        h: Int,
        centerLambda: Float,
    ): Float {
        val m2f = MatOfPoint2f(*pts.toTypedArray())
        val rectArea = Imgproc.contourArea(m2f)
        runCatching { m2f.release() }
        if (rectArea <= 0) return 0f

        val areaScore = sqrt((area / imageArea).coerceIn(0.05, 1.0)).toFloat()

        var cx = 0.0
        var cy = 0.0
        pts.forEach { cx += it.x; cy += it.y }
        cx /= 4; cy /= 4
        val dx = (cx - w / 2.0) / (w / 2.0)
        val dy = (cy - h / 2.0) / (h / 2.0)
        val centerScore = exp(-centerLambda * hypot(dx, dy)).toFloat()

        val rectM2f = MatOfPoint2f(*pts.toTypedArray())
        val rectBoundArea = try {
            val rect = Imgproc.minAreaRect(rectM2f)
            (rect.size.width * rect.size.height).coerceAtLeast(1.0)
        } finally {
            runCatching { rectM2f.release() }
        }
        val rectScore = (rectArea / rectBoundArea).coerceIn(0.3, 1.0).toFloat()

        return areaScore * centerScore * rectScore
    }

    /** 轮廓最小外接旋转矩形四角兜底。 */
    private fun minAreaRectQuad(contour: MatOfPoint): List<Point>? {
        val m2f = MatOfPoint2f(*contour.toArray())
        return try {
            val rect = Imgproc.minAreaRect(m2f)
            val arr = arrayOfNulls<Point>(4)
            rect.points(arr)
            val pts = arr.requireNoNulls().toList()
            if (isNonDegenerate(pts)) orderPoints(pts) else null
        } finally {
            runCatching { m2f.release() }
        }
    }

    /** 奇数化核尺寸（至少 3）。 */
    private fun oddSize(v: Double): Double = max(3.0, ((v.toInt() / 2) * 2 + 1).toDouble())

    /** 四点排序为 TL / TR / BR / BL。 */
    fun orderPoints(pts: List<Point>): List<Point> {
        val bySum = pts.sortedBy { it.x + it.y }
        val tl = bySum.first()
        val br = bySum.last()
        val byDiff = pts.sortedBy { it.y - it.x }
        val tr = byDiff.first()
        val bl = byDiff.last()
        return listOf(tl, tr, br, bl)
    }

    private data class Candidate(val points: List<Point>, val score: Float)

    /** 拍摄后检测用长边（精度与耗时平衡）。 */
    const val DETECT_EDGE = 1080

    /** 取景器实时检测用长边。 */
    const val PREVIEW_EDGE = 540
}
