package com.pencil.chuangyin.desktop.ai

import com.pencil.chuangyin.desktop.image.ImageProcessor
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * 把本地图片文件编码为 OpenAI 兼容的 `data:` URL，供多模态请求使用。
 *
 * 处理策略：按长边上限降采样解码（`ImageIO` 源级子采样，WebP 回退 skia），
 * 再等比缩到 [MAX_EDGE_PX]，最后压成 JPEG。这样即使原图是数千万像素的相机照片，
 * 也不会把整块原图 base64 塞进请求体。
 *
 * 编码结果按「路径 + 大小 + 修改时间」做一个小容量缓存：agent 主循环每轮都要
 * 重新序列化历史消息，缓存可避免同一张图片被反复解码压缩。
 */
internal object InlineImage {

    /** 输出图片的最长边（对齐主流视觉模型推荐的输入边长）。 */
    const val MAX_EDGE_PX = 1568

    /** 默认 JPEG 质量。 */
    const val JPEG_QUALITY = 82

    /** 单张图片编码后的字节上限；超出则逐步降低质量。 */
    private const val MAX_BYTES = 3 * 1024 * 1024

    private const val MIN_QUALITY = 40
    private const val QUALITY_STEP = 20
    private const val CACHE_CAPACITY = 6

    private val cache = object : LinkedHashMap<String, String>(CACHE_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > CACHE_CAPACITY
    }

    /** 编码为 `data:image/jpeg;base64,...`；无法解码时返回 null。 */
    fun toDataUrl(path: String): String? {
        val file = File(path)
        if (!file.isFile) return null
        val key = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        synchronized(cache) { cache[key] }?.let { return it }
        val url = encode(file) ?: return null
        synchronized(cache) { cache[key] = url }
        return url
    }

    private fun encode(file: File): String? = runCatching {
        val source = ImageProcessor.loadScaled(file, MAX_EDGE_PX) ?: return@runCatching null
        val scaled = ImageProcessor.capEdge(source, MAX_EDGE_PX)
        val bytes = compress(scaled)
        val encoded = Base64.getEncoder().encodeToString(bytes)
        "data:image/jpeg;base64,$encoded"
    }.getOrNull()

    /** 按质量阶梯压成 JPEG，直到体积落回上限。 */
    private fun compress(image: BufferedImage): ByteArray {
        val flat = flatten(image)
        var quality = JPEG_QUALITY
        var bytes = writeJpeg(flat, quality)
        while (bytes.size > MAX_BYTES && quality > MIN_QUALITY) {
            quality -= QUALITY_STEP
            bytes = writeJpeg(flat, quality)
        }
        return bytes
    }

    /** 铺白底并转成 RGB：JPEG 不支持透明通道，直接写 ARGB 会变黑底。 */
    private fun flatten(src: BufferedImage): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            g.color = java.awt.Color.WHITE
            g.fillRect(0, 0, src.width, src.height)
            g.drawImage(src, 0, 0, null)
        } finally {
            g.dispose()
        }
        return out
    }

    private fun writeJpeg(image: BufferedImage, quality: Int): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        return ByteArrayOutputStream().use { buffer ->
            ImageIO.createImageOutputStream(buffer).use { output ->
                writer.output = output
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = quality / 100f
                }
                writer.write(null, IIOImage(image, null, null), param)
            }
            writer.dispose()
            buffer.toByteArray()
        }
    }
}
