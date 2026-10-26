package com.pencil.chuangyin.desktop.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.pencil.chuangyin.desktop.support.decodeBitmap
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 图片处理管线 —— 逐行移植安卓端 `DefaultImagePreparer` + `DefaultImageBinarizer`：
 * 旋转（0/90/180/270）→ 等比缩放到打印头宽度 384 点 → 五种黑白化算法。
 *
 * 平台映射（安卓 → 桌面）：
 *  - `BitmapFactory` → `ImageIO`（JPG/PNG/BMP/GIF），WebP 等 ImageIO 不支持的格式回退 skia
 *    （`support.decodeBitmap`，与图库/预览共用同一条解码路径）；
 *  - `Bitmap.getPixels` → `BufferedImage.getRGB`；
 *  - `Bitmap.createScaledBitmap/createBitmap(matrix)` → `Graphics2D` 双线性绘制；
 *  - `Bitmap` 预览 → `ImageBitmap`（PNG 回环 + skia 解码，避免手拼 skia 字节序）。
 *
 * 所有算术常量与分支顺序都与安卓端保持一致，保证同一张图在两端输出同一份点阵。
 */
object ImageProcessor {

    /** 打印头每行点数（与安卓端 PrinterSettings.printWidthPx / PrinterCommand.PRINT_WIDTH_PX 一致）。 */
    const val PRINT_WIDTH_PX = 384

    // ===================== 解码 =====================

    /** 把图片字节解码为 ARGB BufferedImage（ImageIO 优先，WebP 等回退 skia）。 */
    fun decode(bytes: ByteArray): BufferedImage? {
        val viaImageIo = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
        if (viaImageIo != null) return toArgb(viaImageIo)
        val bitmap = decodeBitmap(bytes) ?: return null
        return fromImageBitmap(bitmap)
    }

    /** 解码文件并按长边上限降采样（先按 2 的幂子采样，再由 [capEdge] 精确收边）。 */
    fun loadScaled(file: File, maxEdge: Int = ImageSource.MAX_SOURCE_EDGE): BufferedImage? {
        val subsampled = decodeFileSubsampled(file, maxEdge)
        if (subsampled != null) return capEdge(subsampled, maxEdge)
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val decoded = decode(bytes) ?: return null
        return capEdge(decoded, maxEdge)
    }

    /** 解码缩略图（长边 ≤ [edge]），供选择页网格使用。 */
    fun decodeThumbnail(file: File, edge: Int = THUMB_EDGE): BufferedImage? =
        loadScaled(file, edge)

    /**
     * 用 ImageIO 的 ImageReader 按 2 的幂做源级子采样读取：
     * 手机相册常见 4000×3000，直接整幅解码约 48MB，子采样后再缩放内存占用降到 1/4~1/16。
     * 读取器不支持该格式（如 WebP）时返回 null，由调用方回退整幅解码。
     */
    private fun decodeFileSubsampled(file: File, targetEdge: Int): BufferedImage? = runCatching {
        val stream = ImageIO.createImageInputStream(file) ?: return null
        try {
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.input = stream
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                var sample = 1
                var longEdge = maxOf(width, height)
                while (longEdge / 2 >= targetEdge) {
                    longEdge /= 2
                    sample *= 2
                }
                val param = reader.defaultReadParam
                if (sample > 1) param.setSourceSubsampling(sample, sample, 0, 0)
                toArgb(reader.read(0, param))
            } finally {
                runCatching { reader.dispose() }
            }
        } finally {
            runCatching { stream.close() }
        }
    }.getOrNull()

    /** 统一转成 TYPE_INT_ARGB（后续 getRGB/绘制都按 ARGB 处理，透明像素按白底计算）。 */
    private fun toArgb(src: BufferedImage): BufferedImage =
        if (src.type == BufferedImage.TYPE_INT_ARGB) {
            src
        } else {
            BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB).also { out ->
                val g = out.createGraphics()
                try {
                    g.drawImage(src, 0, 0, null)
                } finally {
                    g.dispose()
                }
            }
        }

    /** Compose ImageBitmap → BufferedImage（经 PixelMap 取 ARGB 像素，无字节序猜测）。 */
    private fun fromImageBitmap(bitmap: ImageBitmap): BufferedImage? = runCatching {
        val pixels = bitmap.toPixelMap()
        BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB).also { out ->
            out.setRGB(
                0,
                0,
                pixels.width,
                pixels.height,
                pixels.buffer,
                pixels.bufferOffset,
                pixels.stride,
            )
        }
    }.getOrNull()

    /** 长边超限时等比缩小（对应安卓端 capEdge）。 */
    fun capEdge(src: BufferedImage, maxEdge: Int = ImageSource.MAX_SOURCE_EDGE): BufferedImage {
        val longEdge = maxOf(src.width, src.height)
        if (longEdge <= maxEdge) return src
        val scale = maxEdge.toFloat() / longEdge
        val width = (src.width * scale).toInt().coerceAtLeast(1)
        val height = (src.height * scale).toInt().coerceAtLeast(1)
        return scaleTo(src, width, height)
    }

    // ===================== 变换 =====================

    /**
     * 旋转（仅 0/90/180/270，与安卓端 Matrix.postRotate 一致）。
     * 画布按旋转后外框重建并铺白底，避免正交旋转出现黑角。
     */
    fun rotate(src: BufferedImage, degrees: Int): BufferedImage {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return src
        val radians = Math.toRadians(normalized.toDouble())
        val sin = abs(kotlin.math.sin(radians))
        val cos = abs(kotlin.math.cos(radians))
        val newWidth = (src.width * cos + src.height * sin).roundToInt().coerceAtLeast(1)
        val newHeight = (src.width * sin + src.height * cos).roundToInt().coerceAtLeast(1)
        val out = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, newWidth, newHeight)
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            val tx = AffineTransform()
            tx.translate(newWidth / 2.0, newHeight / 2.0)
            tx.rotate(radians)
            tx.translate(-src.width / 2.0, -src.height / 2.0)
            g.drawImage(src, tx, null)
        } finally {
            g.dispose()
        }
        return out
    }

    /** 等比缩放到指定宽度（对应安卓端 scaleToWidth，小图同样会放大到 384）。 */
    fun scaleToWidth(src: BufferedImage, targetWidth: Int): BufferedImage {
        if (src.width == targetWidth) return src
        val ratio = targetWidth.toFloat() / src.width
        val targetHeight = (src.height * ratio).toInt().coerceAtLeast(1)
        return scaleTo(src, targetWidth, targetHeight)
    }

    private fun scaleTo(src: BufferedImage, width: Int, height: Int): BufferedImage {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            g.drawImage(src, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        return out
    }

    /** 打印前处理：旋转 → 缩放到 384 点宽 → 黑白化（与安卓端 ImagePreparer.prepare 等价）。 */
    fun prepare(src: BufferedImage, options: ImageProcessOptions): BufferedImage {
        var work = src
        if (options.rotationDegrees != 0) work = rotate(work, options.rotationDegrees)
        if (work.width != PRINT_WIDTH_PX) work = scaleToWidth(work, PRINT_WIDTH_PX)
        return binarize(work, options)
    }

    // ===================== 黑白化 =====================

    /** 按算法生成纯黑白位图（对应安卓端 DefaultImageBinarizer.process）。 */
    fun binarize(src: BufferedImage, options: ImageProcessOptions): BufferedImage {
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

    /** 读取像素并转为 0..255 灰度（透明像素按白底 255 处理）。 */
    private fun toGray(src: BufferedImage, width: Int, height: Int): FloatArray {
        val pixels = IntArray(width * height)
        src.getRGB(0, 0, width, height, pixels, 0, width)
        return FloatArray(width * height) { i ->
            val p = pixels[i]
            val alpha = (p ushr 24) and 0xFF
            if (alpha == 0) {
                255f
            } else {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                0.299f * r + 0.587f * g + 0.114f * b
            }
        }
    }

    private fun writeResult(black: BooleanArray, width: Int, height: Int): BufferedImage {
        val out = IntArray(width * height) { i -> if (black[i]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { image ->
            image.setRGB(0, 0, width, height, out, 0, width)
        }
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
                val error = if (isBlack) old else old - 255f
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
                val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1)
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

    // ===================== 输出 =====================

    /** 编码为 PNG 字节（保存到输出目录用）。 */
    fun encodePng(image: BufferedImage): ByteArray? = runCatching {
        ByteArrayOutputStream().use { stream ->
            if (!ImageIO.write(image, "png", stream)) return null
            stream.toByteArray()
        }
    }.getOrNull()

    /** BufferedImage → Compose ImageBitmap（PNG 回环，确保像素顺序与 skia 一致）。 */
    fun toImageBitmap(image: BufferedImage): ImageBitmap? {
        val bytes = encodePng(image) ?: return null
        return decodeBitmap(bytes)
    }

    private const val THUMB_EDGE = 320

    // Sobel 相关
    /** 梯度归一化除数：4√2 ≈ 5.657，把幅值折算到 0..255。 */
    private const val SOBEL_NORM_DIVISOR = 4f * 1.4142135f
    private const val MAX_THRESHOLD_VALUE = 255f

    /** 归一化后梯度门槛映射系数：滑杆 0..255 → 门槛 153..0。 */
    private const val EDGE_CUTOFF_SCALE = 0.6f

    // Sauvola 相关
    private const val SAUVOLA_K = 0.3
    private const val SAUVOLA_R = 128.0
    private const val SAUVOLA_MIN_STD = 8.0
    private const val SAUVOLA_DARK_FORCE = 80f
    private const val SAUVOLA_BIAS_PER_STEP = 0.35
    private const val WINDOW_RATIO = 0.05f
    private const val WINDOW_MIN = 11
    private const val WINDOW_MAX = 41
}
