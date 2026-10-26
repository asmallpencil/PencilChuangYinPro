package com.pencil.chuangyin.desktop.ai.tools

import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 桌面端位图操作的公共实现（替代安卓的 Bitmap / Canvas / Paint）。
 *
 * 所有函数都返回**新实例**，不修改入参，便于调用方按需释放引用。
 */
internal object ImageOps {

    /** 高质量缩放（双线性）。 */
    fun scale(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.drawImage(source, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        return target
    }

    /** 按角度顺时针旋转（自动扩展画布以容纳旋转后的完整图像）。 */
    fun rotate(source: BufferedImage, degrees: Int): BufferedImage {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return source
        if (normalized == 90 || normalized == 180 || normalized == 270) {
            val swap = normalized == 90 || normalized == 270
            val width = if (swap) source.height else source.width
            val height = if (swap) source.width else source.height
            val target = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val g = target.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                when (normalized) {
                    90 -> {
                        g.translate(width.toDouble(), 0.0)
                        g.rotate(Math.PI / 2)
                    }
                    180 -> {
                        g.translate(width.toDouble(), height.toDouble())
                        g.rotate(Math.PI)
                    }
                    else -> {
                        g.translate(0.0, height.toDouble())
                        g.rotate(3 * Math.PI / 2)
                    }
                }
                g.drawImage(source, 0, 0, null)
            } finally {
                g.dispose()
            }
            return target
        }

        val radians = Math.toRadians(normalized.toDouble())
        val sin = kotlin.math.abs(kotlin.math.sin(radians))
        val cos = kotlin.math.abs(kotlin.math.cos(radians))
        val width = ceil(source.width * cos + source.height * sin).toInt().coerceAtLeast(1)
        val height = ceil(source.width * sin + source.height * cos).toInt().coerceAtLeast(1)
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val transform = AffineTransform().apply {
                translate(width / 2.0, height / 2.0)
                rotate(radians)
                translate(-source.width / 2.0, -source.height / 2.0)
            }
            g.drawImage(source, transform, null)
        } finally {
            g.dispose()
        }
        return target
    }

    /** 裁剪（不修改入参）。 */
    fun crop(source: BufferedImage, x: Int, y: Int, width: Int, height: Int): BufferedImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = target.createGraphics()
        try {
            g.drawImage(source, 0, 0, width, height, x, y, x + width, y + height, null)
        } finally {
            g.dispose()
        }
        return target
    }

    /** 翻转。[horizontal] 为 true 时左右翻转，否则上下翻转。 */
    fun flip(source: BufferedImage, horizontal: Boolean): BufferedImage {
        val target = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val g = target.createGraphics()
        try {
            if (horizontal) {
                g.drawImage(source, source.width, 0, -source.width, source.height, null)
            } else {
                g.drawImage(source, 0, source.height, source.width, -source.height, null)
            }
        } finally {
            g.dispose()
        }
        return target
    }

    /** 灰度化（按亮度加权）。 */
    fun grayscale(source: BufferedImage): BufferedImage {
        val target = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val argb = source.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(0, 255)
                target.setRGB(x, y, (a shl 24) or (gray shl 16) or (gray shl 8) or gray)
            }
        }
        return target
    }

    /** 新建一张带背景色的画布。 */
    fun newCanvas(width: Int, height: Int, background: Color): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.color = background
            g.fillRect(0, 0, width, height)
        } finally {
            g.dispose()
        }
        return image
    }

    /** 创建已配好抗锯齿的画笔上下文。 */
    fun graphicsOf(image: BufferedImage): Graphics2D = image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    }

    /**
     * 保存图片。JPEG 支持质量参数；WebP 因 JDK 无内置编码器，回落为 PNG。
     *
     * @return 实际写出的文件；失败返回 null。
     */
    fun save(image: BufferedImage, target: File, format: String, quality: Int): File? = runCatching {
        target.parentFile?.let { if (!it.exists()) it.mkdirs() }
        when (format) {
            ToolParamValues.FORMAT_JPEG -> {
                val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                val g = rgb.createGraphics()
                try {
                    g.color = Color.WHITE
                    g.fillRect(0, 0, rgb.width, rgb.height)
                    g.drawImage(image, 0, 0, null)
                } finally {
                    g.dispose()
                }
                val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
                try {
                    ImageIO.createImageOutputStream(target).use { stream ->
                        writer.output = stream
                        val param = writer.defaultWriteParam.apply {
                            compressionMode = ImageWriteParam.MODE_EXPLICIT
                            compressionQuality = (quality.coerceIn(1, 100)) / 100f
                        }
                        writer.write(null, IIOImage(rgb, null, null), param)
                    }
                } finally {
                    writer.dispose()
                }
            }
            else -> if (!ImageIO.write(image, "png", target)) return null
        }
        target.takeIf { it.isFile }
    }.getOrNull()

    /** 解析 `#RRGGBB` / `#AARRGGBB` / `0xAARRGGBB` / 颜色名，失败返回 [fallback]。 */
    fun parseColor(raw: String?, fallback: Color): Color {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return fallback
        val hex = text.removePrefix("#").removePrefix("0x").removePrefix("0X")
        val named = runCatching { Color::class.java.getField(text.uppercase()).get(null) as Color }.getOrNull()
        if (named != null) return named
        return runCatching {
            when (hex.length) {
                6 -> Color(hex.toInt(16) or (0xFF shl 24), true)
                8 -> {
                    val value = hex.toLong(16)
                    Color(
                        ((value shr 16) and 0xFF).toInt(),
                        ((value shr 8) and 0xFF).toInt(),
                        (value and 0xFF).toInt(),
                        ((value shr 24) and 0xFF).toInt(),
                    )
                }
                else -> fallback
            }
        }.getOrDefault(fallback)
    }
}

/** 画布宽度上限（与安卓端 ShapeRenderer.WIDTH_RANGE 上界一致）。 */
internal const val MAX_CANVAS_WIDTH = 2048

/** 画布高度上限。 */
internal const val MAX_CANVAS_HEIGHT = 4096
