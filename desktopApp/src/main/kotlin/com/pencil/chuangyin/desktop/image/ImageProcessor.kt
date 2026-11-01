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

/**
 * 图片处理管线 —— 逐行移植安卓端 `DefaultImagePreparer` + `DefaultImageBinarizer`：
 * 旋转（0/90/180/270）→ 等比缩放到打印头宽度 384 点 → 黑白化（算法见 DitherAlgorithms）。
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
        val black = DitherAlgorithms
            .strategyFor(options.algorithm)
            .process(gray, width, height, options.threshold)
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
}
