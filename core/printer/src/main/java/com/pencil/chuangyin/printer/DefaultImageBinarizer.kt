package com.pencil.chuangyin.printer

import android.graphics.Bitmap
import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * [ImageBinarizer] 默认实现：所有算法先转灰度，再按算法扩散/检测/自适应判定，
 * 最后统一输出纯白底（0xFFFFFFFF）纯黑点（0xFF000000）的 ARGB 位图。
 *
 * 性能约束：输入应先经 [ImagePreparer] 降采样到打印工作分辨率（约 384~1200 px 宽），
 * 本类只做逐像素/积分图计算，时间与内存均为 O(宽×高)。
 */
@Singleton
class DefaultImageBinarizer @Inject constructor() : ImageBinarizer {

    override fun process(src: Bitmap, options: ImageProcessOptions): Bitmap {
        val width = src.width
        val height = src.height
        val gray = toGray(src, width, height)
        val black = when (options.algorithm) {
            BinarizeAlgorithm.FixedThreshold -> fixedThreshold(gray, width, height, options.threshold)
            BinarizeAlgorithm.FloydSteinberg -> floydSteinberg(gray, width, height, options.threshold)
            BinarizeAlgorithm.Atkinson -> atkinson(gray, width, height, options.threshold)
            BinarizeAlgorithm.SobelEdge -> sobelEdge(gray, width, height, options.threshold)
            BinarizeAlgorithm.SauvolaAdaptive -> sauvola(gray, width, height, options.threshold)
        }
        return writeResult(black, width, height)
    }

    /** 读取像素并转为 0..255 灰度（同时处理透明像素为白底）。 */
    private fun toGray(src: Bitmap, width: Int, height: Int): FloatArray {
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        return FloatArray(width * height) { i ->
            val p = pixels[i]
            if (Color.alpha(p) == 0) {
                255f
            } else {
                (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p))
            }
        }
    }

    private fun writeResult(black: BooleanArray, width: Int, height: Int): Bitmap {
        val out = IntArray(width * height) { i -> if (black[i]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    // ---- 1. 固定阈值 ----
    private fun fixedThreshold(gray: FloatArray, w: Int, h: Int, threshold: Int): BooleanArray =
        BooleanArray(w * h) { gray[it] < threshold }

    // ---- 2. Floyd–Steinberg 误差扩散（7/16,3/16,5/16,1/16） ----
    private fun floydSteinberg(gray: FloatArray, w: Int, h: Int, threshold: Int): BooleanArray {
        val buf = gray.copyOf()
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val old = buf[idx]
                val isBlack = old < threshold
                out[idx] = isBlack
                val error = (if (isBlack) old else old - 255f)
                diffuse(buf, w, h, x, y, error, intArrayOf(7, 3, 5, 1), 16f)
            }
        }
        return out
    }

    // ---- 3. Atkinson 误差扩散（6 个邻居各 1/8，保留 2/8 对比度） ----
    private fun atkinson(gray: FloatArray, w: Int, h: Int, threshold: Int): BooleanArray {
        val buf = gray.copyOf()
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val old = buf[idx]
                val isBlack = old < threshold
                out[idx] = isBlack
                val error = (if (isBlack) old else old - 255f) / 8f
                if (x + 1 < w) buf[idx + 1] += error
                if (x + 2 < w) buf[idx + 2] += error
                if (y + 1 < h) {
                    if (x - 1 >= 0) buf[idx + w - 1] += error
                    buf[idx + w] += error
                    if (x + 1 < w) buf[idx + w + 1] += error
                    if (y + 2 < h) buf[idx + 2 * w] += error
                }
            }
        }
        return out
    }

    /** Floyd–Steinberg 单点扩散（按 [weights] 顺序：右、左下、下、右下）。 */
    private fun diffuse(
        buf: FloatArray,
        w: Int,
        h: Int,
        x: Int,
        y: Int,
        error: Float,
        weights: IntArray,
        divisor: Float,
    ) {
        if (x + 1 < w) buf[y * w + x + 1] += error * weights[0] / divisor
        if (y + 1 < h) {
            if (x - 1 >= 0) buf[(y + 1) * w + x - 1] += error * weights[1] / divisor
            buf[(y + 1) * w + x] += error * weights[2] / divisor
            if (x + 1 < w) buf[(y + 1) * w + x + 1] += error * weights[3] / divisor
        }
    }

    // ---- 4. Sobel 算子边缘检测：边缘为黑，背景为白 ----
    private fun sobelEdge(gray: FloatArray, w: Int, h: Int, threshold: Int): BooleanArray {
        val out = BooleanArray(w * h)
        // 梯度幅值归一化到 0..255（理论峰值 4√2·255 ≈ 1442）。
        // 阈值滑杆语义与其它算法一致：值越高 → 黑内容越多（保留越弱的边缘）。
        val cutoff = (MAX_THRESHOLD_VALUE - threshold) * EDGE_CUTOFF_SCALE
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val tl = gray[idx - w - 1]
                val tc = gray[idx - w]
                val tr = gray[idx - w + 1]
                val ml = gray[idx - 1]
                val mr = gray[idx + 1]
                val bl = gray[idx + w - 1]
                val bc = gray[idx + w]
                val br = gray[idx + w + 1]
                val gx = -tl - 2f * ml - bl + tr + 2f * mr + br
                val gy = -tl - 2f * tc - tr + bl + 2f * bc + br
                val normalized = sqrt(gx * gx + gy * gy) / SOBEL_NORM_DIVISOR
                out[idx] = normalized >= cutoff
            }
        }
        return out
    }

    // ---- 5. Sauvola 局部自适应阈值（积分图 O(WH)，文档/试卷增强） ----
    private fun sauvola(gray: FloatArray, w: Int, h: Int, threshold: Int): BooleanArray {
        val out = BooleanArray(w * h)
        // 积分图：sum 为灰度和，sumSq 为灰度平方和，均用 Long 防溢出。
        val sum = LongArray((w + 1) * (h + 1))
        val sumSq = LongArray((w + 1) * (h + 1))
        for (y in 1..h) {
            var rowSum = 0L
            var rowSq = 0L
            for (x in 1..w) {
                val g = gray[(y - 1) * w + (x - 1)].toInt()
                rowSum += g
                rowSq += g.toLong() * g
                sum[y * (w + 1) + x] = sum[(y - 1) * (w + 1) + x] + rowSum
                sumSq[y * (w + 1) + x] = sumSq[(y - 1) * (w + 1) + x] + rowSq
            }
        }

        val window = adaptiveWindow(w, h)
        val half = window / 2
        // 滑杆作为偏置：越高输出越黑，与其它算法方向一致。
        val bias = (threshold - ImageProcessOptions.DEFAULT_THRESHOLD) * SAUVOLA_BIAS_PER_STEP
        for (y in 0 until h) {
            val y0 = (y - half).coerceAtLeast(0)
            val y1 = (y + half).coerceAtMost(h - 1)
            for (x in 0 until w) {
                val x0 = (x - half).coerceAtLeast(0)
                val x1 = (x + half).coerceAtMost(w - 1)
                val count = ((x1 - x0 + 1).toLong()) * (y1 - y0 + 1)
                val s = rectSum(sum, w, x0, y0, x1, y1)
                val s2 = rectSum(sumSq, w, x0, y0, x1, y1)
                val mean = s.toDouble() / count
                val variance = (s2.toDouble() / count) - mean * mean
                val std = sqrt(variance.coerceAtLeast(0.0))
                val g = gray[y * w + x]
                if (std < SAUVOLA_MIN_STD) {
                    // 低方差区（纯纸/均匀底）：防整片噪声变黑，仅保留确定的重墨。
                    out[y * w + x] = g < SAUVOLA_DARK_FORCE
                    continue
                }
                // Sauvola 公式：T = mean * (1 + k * (std/R - 1))，R=128。
                val t = mean * (1.0 + SAUVOLA_K * (std / SAUVOLA_R - 1.0)) + bias
                out[y * w + x] = g < t
            }
        }
        return out
    }

    /** 积分图矩形区域和（坐标闭区间）。 */
    private fun rectSum(integral: LongArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int): Long {
        val stride = w + 1
        return integral[(y1 + 1) * stride + (x1 + 1)] -
            integral[y0 * stride + (x1 + 1)] -
            integral[(y1 + 1) * stride + x0] +
            integral[y0 * stride + x0]
    }

    /** 自适应窗口：约短边 5%，约束在 11..41 且为奇数（文档笔画尺度，防吞细线）。 */
    private fun adaptiveWindow(w: Int, h: Int): Int {
        val raw = (minOf(w, h) * WINDOW_RATIO).toInt().coerceIn(WINDOW_MIN, WINDOW_MAX)
        return if (raw % 2 == 0) raw + 1 else raw
    }

    private companion object {
        // Sobel 相关
        /** 梯度归一化除数：4√2 ≈ 5.657，把幅值折算到 0..255。 */
        const val SOBEL_NORM_DIVISOR = 4f * 1.4142135f
        const val MAX_THRESHOLD_VALUE = 255f

        /** 归一化后梯度门槛映射系数：滑杆 0..255 → 门槛 153..0。 */
        const val EDGE_CUTOFF_SCALE = 0.6f

        // Sauvola 相关
        const val SAUVOLA_K = 0.3
        const val SAUVOLA_R = 128.0
        const val SAUVOLA_MIN_STD = 8.0
        const val SAUVOLA_DARK_FORCE = 80f
        const val SAUVOLA_BIAS_PER_STEP = 0.35
        const val WINDOW_RATIO = 0.05f
        const val WINDOW_MIN = 11
        const val WINDOW_MAX = 41
    }
}
