package com.pencil.chuangyin.core.agent.openai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 把本地图片文件编码为 OpenAI 兼容的 `data:` URL，供多模态请求使用。
 *
 * 处理策略：先只读图片边界计算采样率，再按采样率解码，最后把最长边限制到
 * [MAX_EDGE_PX] 并压成 JPEG。这样即使原图是数千万像素的相机照片，也不会把
 * 整块原图 base64 塞进请求体。
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

    /** 解码前的粗采样上限，避免直接解码超大位图导致 OOM。 */
    private const val DECODE_HEADROOM = 2

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
        val decoded = decodeSampled(file) ?: return@runCatching null
        val scaled = scaleToMaxEdge(decoded, MAX_EDGE_PX)
        val bytes = compress(scaled)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        "data:image/jpeg;base64,$encoded"
    }.getOrNull()

    /** 两趟解码：第一趟只读边界，第二趟按采样率真正解码。 */
    private fun decodeSampled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val limit = MAX_EDGE_PX * DECODE_HEADROOM
        while (bounds.outWidth / sample > limit || bounds.outHeight / sample > limit) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun compress(bitmap: Bitmap): ByteArray {
        val buffer = ByteArrayOutputStream()
        var quality = JPEG_QUALITY
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
        var bytes = buffer.toByteArray()
        while (bytes.size > MAX_BYTES && quality > MIN_QUALITY) {
            quality -= QUALITY_STEP
            buffer.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
            bytes = buffer.toByteArray()
        }
        return bytes
    }
}
