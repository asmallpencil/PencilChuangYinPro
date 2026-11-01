package com.pencil.chuangyin.desktop.image

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 灰度 → 黑白算法插件库（安卓端 `DitherAlgorithms` 的逐行移植）。
 *
 * 设计参考 s_y2printer / libdither 的「算法可插拔」思路：每种算法实现 [DitherStrategy]，
 * 统一由 [DitherAlgorithms.strategyFor] 注册分发，新增算法不影响调用方。
 *
 * - 误差扩散统一走 [ErrorDiffusionStrategy] 引擎（蛇形扫描 + 边界安全扩散），
 *   Floyd–Steinberg / Atkinson / JJN / Stucki 只是不同卷积核；
 * - 结构感知扩散按局部梯度调节扩散方向与强度，抑制「虫状」伪影、保住细节；
 * - 蓝噪声（IGN）与 Bayer 8×8 有序抖动，灰阶过渡更均匀、无低频斑块；
 * - Otsu 全局自动阈值，照片/文档都能自动找到分割点；
 * - 智能自适应按直方图与边缘特征自动选路；
 * - 所有算法的阈值滑杆语义保持一致：值越大 → 黑点越多。
 *
 * 注意：本文件与安卓端 core:printer 的同名实现保持常量与分支顺序一致，
 * 保证同一张图在两端输出同一份点阵。
 */
internal interface DitherStrategy {
    fun process(gray: FloatArray, width: Int, height: Int, threshold: Int): BooleanArray
}

internal object DitherAlgorithms {

    fun strategyFor(algorithm: BinarizeAlgorithm): DitherStrategy = when (algorithm) {
        BinarizeAlgorithm.Auto -> AutoStrategy
        BinarizeAlgorithm.FixedThreshold -> FixedThresholdStrategy
        BinarizeAlgorithm.OtsuThreshold -> OtsuStrategy
        BinarizeAlgorithm.FloydSteinberg -> FloydSteinbergStrategy
        BinarizeAlgorithm.Atkinson -> AtkinsonStrategy
        BinarizeAlgorithm.JarvisJudiceNinke -> JarvisJudiceNinkeStrategy
        BinarizeAlgorithm.Stucki -> StuckiStrategy
        BinarizeAlgorithm.StructureAware -> StructureAwareStrategy
        BinarizeAlgorithm.BayerOrdered -> BayerStrategy
        BinarizeAlgorithm.BlueNoise -> BlueNoiseStrategy
        BinarizeAlgorithm.SobelEdge -> SobelStrategy
        BinarizeAlgorithm.SauvolaAdaptive -> SauvolaStrategy
    }

    // ===================== 智能自适应 =====================
    /**
     * 按图像内容选路，取代「一个阈值套所有图」的死板做法：
     * - 低动态范围（灰蒙蒙、欠曝）：全局 Otsu 先找到正确分割点；
     * - 明显双峰 + 暗部占比小 + 中间调极少 → 文档/试卷/票据，走 Sauvola 局部自适应去阴影；
     * - 其余（照片、插画、截图）→ 结构感知误差扩散，保细节、抗虫状伪影。
     */
    private object AutoStrategy : DitherStrategy {
        override fun process(gray: FloatArray, width: Int, height: Int, threshold: Int): BooleanArray {
            val stats = HistogramStats(gray)
            val otsu = stats.otsu()
            return when {
                stats.valueRange < AUTO_LOW_CONTRAST_RANGE ->
                    OtsuStrategy.process(gray, width, height, threshold)
                otsu.eta >= AUTO_DOC_ETA &&
                    otsu.darkFraction in AUTO_DOC_MIN_DARK..AUTO_DOC_MAX_DARK &&
                    stats.midtoneFraction <= AUTO_DOC_MAX_MIDTONE ->
                    SauvolaStrategy.process(gray, width, height, threshold)
                else -> StructureAwareStrategy.process(gray, width, height, threshold)
            }
        }
    }

    // ===================== 直方图统计 =====================
    private class HistogramStats(gray: FloatArray) {
        val histogram = IntArray(256)
        val total = gray.size
        var minValue = 255
        var maxValue = 0
        var midtoneCount = 0

        init {
            for (value in gray) {
                val v = value.toInt().coerceIn(0, 255)
                histogram[v]++
                if (v < minValue) minValue = v
                if (v > maxValue) maxValue = v
                if (v in MIDTONE_MIN..MIDTONE_MAX) midtoneCount++
            }
        }

        val valueRange: Int get() = maxValue - minValue
        val midtoneFraction: Double get() = midtoneCount.toDouble() / total

        /** Otsu 最大类间方差切分点 + 判别度 η（越接近 1 越像双峰文档）。 */
        fun otsu(): OtsuResult {
            var sumAll = 0.0
            var sumSqAll = 0.0
            for (i in 0..255) {
                val count = histogram[i]
                sumAll += i.toDouble() * count
                sumSqAll += i.toDouble() * i * count
            }
            val meanAll = sumAll / total
            val totalVariance = (sumSqAll / total - meanAll * meanAll).coerceAtLeast(0.0)

            var sumBackground = 0.0
            var weightBackground = 0
            var cutoff = ImageProcessOptions.DEFAULT_THRESHOLD.toDouble()
            var bestVariance = -1.0
            for (i in 0..255) {
                weightBackground += histogram[i]
                if (weightBackground == 0) continue
                val weightForeground = total - weightBackground
                if (weightForeground == 0) break
                sumBackground += i.toDouble() * histogram[i]
                val meanBackground = sumBackground / weightBackground
                val meanForeground = (sumAll - sumBackground) / weightForeground
                val variance = weightBackground.toDouble() * weightForeground *
                    (meanBackground - meanForeground) * (meanBackground - meanForeground)
                if (variance > bestVariance) {
                    bestVariance = variance
                    cutoff = i.toDouble()
                }
            }
            // 类间方差 bestVariance 已乘过 total²，这里折回 Otsu 判别度 η = σB²/σT²。
            val eta = if (totalVariance > 0.0) {
                bestVariance / (totalVariance * total.toDouble() * total)
            } else {
                0.0
            }
            var dark = 0
            for (i in 0 until cutoff.toInt().coerceIn(0, 256)) dark += histogram[i]
            return OtsuResult(cutoff, eta.coerceIn(0.0, 1.0), dark.toDouble() / total)
        }
    }

    private data class OtsuResult(
        val cutoff: Double,
        val eta: Double,
        val darkFraction: Double,
    )

    // ===================== 固定阈值 =====================
    private object FixedThresholdStrategy : DitherStrategy {
        override fun process(gray: FloatArray, width: Int, height: Int, threshold: Int): BooleanArray =
            BooleanArray(width * height) { gray[it] < threshold }
    }

    // ===================== Otsu 全局自动阈值 =====================
    private object OtsuStrategy : DitherStrategy {
        override fun process(gray: FloatArray, width: Int, height: Int, threshold: Int): BooleanArray {
            val best = HistogramStats(gray).otsu().cutoff
            // 滑杆作为偏置：越高越黑，与其它算法方向一致。
            val cutoff = (best + (threshold - ImageProcessOptions.DEFAULT_THRESHOLD) * OTSU_BIAS_PER_STEP)
                .coerceIn(0.0, 255.0)
            return BooleanArray(width * height) { gray[it] < cutoff }
        }
    }

    // ===================== 误差扩散引擎 =====================
    private class ErrorDiffusionStrategy(
        private val taps: List<DiffusionTap>,
        private val divisor: Float,
        private val edgeAware: Boolean = false,
    ) : DitherStrategy {

        override fun process(gray: FloatArray, width: Int, height: Int, threshold: Int): BooleanArray {
            val buf = gray.copyOf()
            val out = BooleanArray(width * height)
            val gradient = if (edgeAware) GradientField(gray, width, height) else null
            val baseSum = taps.sumOf { it.weight.toDouble() }.toFloat()

            for (y in 0 until height) {
                // 蛇形扫描：奇数行从右往左，消除单向扫描形成的方向性纹理。
                val reverse = y % 2 == 1
                val start = if (reverse) width - 1 else 0
                val end = if (reverse) -1 else width
                val step = if (reverse) -1 else 1
                var x = start
                while (x != end) {
                    val idx = y * width + x
                    val old = buf[idx]
                    val isBlack = old < threshold
                    out[idx] = isBlack
                    val error = if (isBlack) old else old - 255f

                    var dampedSum = baseSum
                    if (gradient != null) {
                        dampedSum = taps.sumOf { tap ->
                            (tap.weight * gradient.weightFactor(idx, tap.dx, tap.dy)).toDouble()
                        }.toFloat()
                    }
                    val scale = if (dampedSum > 0.0001f) baseSum / dampedSum else 1f

                    for (tap in taps) {
                        val dx = if (reverse) -tap.dx else tap.dx
                        val nx = x + dx
                        val ny = y + tap.dy
                        if (nx < 0 || nx >= width || ny >= height) continue
                        val factor = gradient?.weightFactor(idx, tap.dx, tap.dy) ?: 1f
                        buf[ny * width + nx] += error * tap.weight * factor * scale / divisor
                    }
                    x += step
                }
            }
            return out
        }
    }

    /** 局部梯度场：用于结构感知扩散的方向/强度调制。 */
    private class GradientField(gray: FloatArray, private val w: Int, private val h: Int) {
        private val gx = FloatArray(w * h)
        private val gy = FloatArray(w * h)

        init {
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
                    gx[idx] = (-tl - 2f * ml - bl + tr + 2f * mr + br) / SOBEL_SCALE
                    gy[idx] = (-tl - 2f * tc - tr + bl + 2f * bc + br) / SOBEL_SCALE
                }
            }
        }

        /**
         * 扩散权重系数：
         * - 强梯度处降低「跨边缘」方向上的扩散，误差留在边缘同侧，避免细节被抹平；
         * - 弱梯度处系数为 1，保持与原始卷积核一致。
         */
        fun weightFactor(idx: Int, dx: Int, dy: Int): Float {
            val ax = abs(gx[idx])
            val ay = abs(gy[idx])
            if (ax < EDGE_MIN || ay < EDGE_MIN) return 1f
            val edge = ((ax + ay) / 2f).coerceIn(0f, 1f)
            val crossesVerticalEdge = dx != 0 && ax > ay
            val crossesHorizontalEdge = dy != 0 && ay > ax
            if (!crossesVerticalEdge && !crossesHorizontalEdge) return 1f
            return 1f / (1f + EDGE_DAMPING * edge)
        }
    }

    // ===================== Bayer 8×8 有序抖动 =====================
    private object BayerStrategy : DitherStrategy {
        override fun process(gray: FloatArray, width: Int, height: Int, threshold: Int): BooleanArray {
            val bias = (threshold - ImageProcessOptions.DEFAULT_THRESHOLD) * ORDERED_BIAS_PER_STEP
            val out = BooleanArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val rank = BAYER_8x8[(y and 7) * 8 + (x and 7)]
                    val level = (rank + 0.5f) / 64f * 255f + bias
                    out[y * width + x] = gray[y * width + x] < level
                }
            }
            return out
        }
    }

    // ===================== 蓝噪声（IGN 阈值场） =====================
    private object BlueNoiseStrategy : DitherStrategy {
        override fun process(gray: FloatArray, width: Int, height: Int, threshold: Int): BooleanArray {
            val bias = (threshold - ImageProcessOptions.DEFAULT_THRESHOLD) * ORDERED_BIAS_PER_STEP
            val out = BooleanArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val phase = 0.06711056f * x + 0.00583715f * y
                    val fract = phase - kotlin.math.floor(phase)
                    val noise = 52.9829189f * fract
                    val value = (noise - kotlin.math.floor(noise)) * 255f + bias
                    out[y * width + x] = gray[y * width + x] < value
                }
            }
            return out
        }
    }

    // ===================== Sobel 边缘 =====================
    private object SobelStrategy : DitherStrategy {
        override fun process(gray: FloatArray, width: Int, height: Int, threshold: Int): BooleanArray {
            val out = BooleanArray(width * height)
            val cutoff = (255f - threshold) * EDGE_CUTOFF_SCALE
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val idx = y * width + x
                    val tl = gray[idx - width - 1]
                    val tc = gray[idx - width]
                    val tr = gray[idx - width + 1]
                    val ml = gray[idx - 1]
                    val mr = gray[idx + 1]
                    val bl = gray[idx + width - 1]
                    val bc = gray[idx + width]
                    val br = gray[idx + width + 1]
                    val gx = -tl - 2f * ml - bl + tr + 2f * mr + br
                    val gy = -tl - 2f * tc - tr + bl + 2f * bc + br
                    val normalized = sqrt(gx * gx + gy * gy) / SOBEL_NORM_DIVISOR
                    out[idx] = normalized >= cutoff
                }
            }
            return out
        }
    }

    // ===================== Sauvola 局部自适应 =====================
    private object SauvolaStrategy : DitherStrategy {
        override fun process(gray: FloatArray, width: Int, height: Int, threshold: Int): BooleanArray {
            val out = BooleanArray(width * height)
            val stride = width + 1
            val sum = LongArray(stride * (height + 1))
            val sumSq = LongArray(stride * (height + 1))
            for (y in 1..height) {
                var rowSum = 0L
                var rowSq = 0L
                for (x in 1..width) {
                    val g = gray[(y - 1) * width + (x - 1)].toInt()
                    rowSum += g
                    rowSq += g.toLong() * g
                    sum[y * stride + x] = sum[(y - 1) * stride + x] + rowSum
                    sumSq[y * stride + x] = sumSq[(y - 1) * stride + x] + rowSq
                }
            }

            val window = adaptiveWindow(width, height)
            val half = window / 2
            val bias = (threshold - ImageProcessOptions.DEFAULT_THRESHOLD) * SAUVOLA_BIAS_PER_STEP
            for (y in 0 until height) {
                val y0 = (y - half).coerceAtLeast(0)
                val y1 = (y + half).coerceAtMost(height - 1)
                for (x in 0 until width) {
                    val x0 = (x - half).coerceAtLeast(0)
                    val x1 = (x + half).coerceAtMost(width - 1)
                    val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1)
                    val s = rectSum(sum, stride, x0, y0, x1, y1)
                    val s2 = rectSum(sumSq, stride, x0, y0, x1, y1)
                    val mean = s.toDouble() / count
                    val variance = (s2.toDouble() / count) - mean * mean
                    val std = sqrt(variance.coerceAtLeast(0.0))
                    val g = gray[y * width + x]
                    if (std < SAUVOLA_MIN_STD) {
                        out[y * width + x] = g < SAUVOLA_DARK_FORCE
                        continue
                    }
                    val t = mean * (1.0 + SAUVOLA_K * (std / SAUVOLA_R - 1.0)) + bias
                    out[y * width + x] = g < t
                }
            }
            return out
        }

        private fun rectSum(integral: LongArray, stride: Int, x0: Int, y0: Int, x1: Int, y1: Int): Long =
            integral[(y1 + 1) * stride + (x1 + 1)] -
                integral[y0 * stride + (x1 + 1)] -
                integral[(y1 + 1) * stride + x0] +
                integral[y0 * stride + x0]

        private fun adaptiveWindow(w: Int, h: Int): Int {
            val raw = (minOf(w, h) * WINDOW_RATIO).toInt().coerceIn(WINDOW_MIN, WINDOW_MAX)
            return if (raw % 2 == 0) raw + 1 else raw
        }
    }

    private data class DiffusionTap(val dx: Int, val dy: Int, val weight: Int)

    // 经典误差扩散卷积核（dx/dy 为相对当前点的偏移，正向扫描时使用）。
    private val FLOYD_STEINBERG = listOf(
        DiffusionTap(1, 0, 7),
        DiffusionTap(-1, 1, 3),
        DiffusionTap(0, 1, 5),
        DiffusionTap(1, 1, 1),
    )

    /** Atkinson：6 个邻居各 1/8，刻意少扩散 2/8 → 高光更干净、对比度更高。 */
    private val ATKINSON = listOf(
        DiffusionTap(1, 0, 1),
        DiffusionTap(2, 0, 1),
        DiffusionTap(-1, 1, 1),
        DiffusionTap(0, 1, 1),
        DiffusionTap(1, 1, 1),
        DiffusionTap(0, 2, 1),
    )

    private val JARVIS_JUDICE_NINKE = listOf(
        DiffusionTap(1, 0, 7),
        DiffusionTap(2, 0, 5),
        DiffusionTap(-2, 1, 3),
        DiffusionTap(-1, 1, 5),
        DiffusionTap(0, 1, 7),
        DiffusionTap(1, 1, 5),
        DiffusionTap(2, 1, 3),
        DiffusionTap(-2, 2, 1),
        DiffusionTap(-1, 2, 3),
        DiffusionTap(0, 2, 5),
        DiffusionTap(1, 2, 3),
        DiffusionTap(2, 2, 1),
    )

    private val STUCKI = listOf(
        DiffusionTap(1, 0, 8),
        DiffusionTap(2, 0, 4),
        DiffusionTap(-2, 1, 2),
        DiffusionTap(-1, 1, 4),
        DiffusionTap(0, 1, 8),
        DiffusionTap(1, 1, 4),
        DiffusionTap(2, 1, 2),
        DiffusionTap(-2, 2, 1),
        DiffusionTap(-1, 2, 2),
        DiffusionTap(0, 2, 4),
        DiffusionTap(1, 2, 2),
        DiffusionTap(2, 2, 1),
    )

    private val BAYER_8x8 = intArrayOf(
        0, 32, 8, 40, 2, 34, 10, 42,
        48, 16, 56, 24, 50, 18, 58, 26,
        12, 44, 4, 36, 14, 46, 6, 38,
        60, 28, 52, 20, 62, 30, 54, 22,
        3, 35, 11, 43, 1, 33, 9, 41,
        51, 19, 59, 27, 49, 17, 57, 25,
        15, 47, 7, 39, 13, 45, 5, 37,
        63, 31, 55, 23, 61, 29, 53, 21,
    )

    /** 误差扩散算法只差卷积核，统一由同一引擎驱动（蛇形扫描 + 边界安全）。 */
    private val FloydSteinbergStrategy = ErrorDiffusionStrategy(taps = FLOYD_STEINBERG, divisor = 16f)

    private val AtkinsonStrategy = ErrorDiffusionStrategy(taps = ATKINSON, divisor = 8f)

    private val JarvisJudiceNinkeStrategy =
        ErrorDiffusionStrategy(taps = JARVIS_JUDICE_NINKE, divisor = 48f)

    private val StuckiStrategy = ErrorDiffusionStrategy(taps = STUCKI, divisor = 42f)

    /** 结构感知：FS 卷积核 + 梯度调制的跨边缘抑制。 */
    private val StructureAwareStrategy =
        ErrorDiffusionStrategy(taps = FLOYD_STEINBERG, divisor = 16f, edgeAware = true)

    // —— 智能自适应判据 ——
    /** 动态范围低于该值时视为低对比图（灰蒙蒙/欠曝），优先全局 Otsu。 */
    private const val AUTO_LOW_CONTRAST_RANGE = 48
    /** Otsu 判别度 η 门槛：越高越接近「白底 + 少量黑字」的双峰分布。 */
    private const val AUTO_DOC_ETA = 0.72
    private const val AUTO_DOC_MIN_DARK = 0.004
    private const val AUTO_DOC_MAX_DARK = 0.45
    /** 中间调占比上限：文档几乎没有灰阶过渡，照片则大量存在。 */
    private const val AUTO_DOC_MAX_MIDTONE = 0.28
    private const val MIDTONE_MIN = 64
    private const val MIDTONE_MAX = 192

    // —— 常量 ——
    private const val SOBEL_NORM_DIVISOR = 4f * 1.4142135f
    private const val SOBEL_SCALE = 4f * 1.4142135f * 255f
    private const val EDGE_CUTOFF_SCALE = 0.6f
    private const val EDGE_MIN = 0.04f
    private const val EDGE_DAMPING = 2.2f
    private const val ORDERED_BIAS_PER_STEP = 0.6f
    private const val OTSU_BIAS_PER_STEP = 0.5f
    private const val SAUVOLA_K = 0.3
    private const val SAUVOLA_R = 128.0
    private const val SAUVOLA_MIN_STD = 8.0
    private const val SAUVOLA_DARK_FORCE = 80f
    private const val SAUVOLA_BIAS_PER_STEP = 0.35
    private const val WINDOW_RATIO = 0.05f
    private const val WINDOW_MIN = 11
    private const val WINDOW_MAX = 41
}
