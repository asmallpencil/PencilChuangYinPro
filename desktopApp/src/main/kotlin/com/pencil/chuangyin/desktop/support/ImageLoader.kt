package com.pencil.chuangyin.desktop.support

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image

/**
 * 图片解码与缓存（桌面端替代 Android 的 Coil）。
 * - 网络图：OkHttp 拉取后交给 Skia 解码；i.pximg.net 需要 Referer，否则 403。
 * - 本地图：设置页选壁纸后直接读文件。
 */
object ImageMemoryCache {
    private const val MAX_ENTRIES = 320

    private val cache: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
                size > MAX_ENTRIES
        }
    )

    fun get(key: String): ImageBitmap? = cache[key]

    fun put(key: String, value: ImageBitmap) {
        cache[key] = value
    }
}

private val imageHttp: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .followRedirects(true)
        .build()
}

fun decodeBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

fun loadRemoteBitmap(url: String): ImageBitmap? {
    ImageMemoryCache.get(url)?.let { return it }
    val isPixiv = url.contains("pximg.net")
    val request = Request.Builder()
        .url(url)
        .apply { if (isPixiv) header("Referer", "https://app-api.pixiv.net/") }
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PencilChuangYinPro/2.0")
        .build()
    return runCatching {
        imageHttp.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            val bitmap = decodeBitmap(bytes) ?: return null
            ImageMemoryCache.put(url, bitmap)
            bitmap
        }
    }.getOrNull()
}

fun loadLocalBitmap(path: String): ImageBitmap? {
    ImageMemoryCache.get(path)?.let { return it }
    return runCatching {
        val file = File(path)
        if (!file.isFile) return null
        val bitmap = decodeBitmap(file.readBytes()) ?: return null
        ImageMemoryCache.put(path, bitmap)
        bitmap
    }.getOrNull()
}

/** 组合内异步加载网络图（带内存缓存）。 */
@Composable
fun rememberRemoteImage(url: String?): ImageBitmap? {
    val state by produceState<ImageBitmap?>(initialValue = url?.let { ImageMemoryCache.get(it) }, url) {
        if (url == null) {
            value = null
            return@produceState
        }
        value = ImageMemoryCache.get(url) ?: withContext(Dispatchers.IO) { loadRemoteBitmap(url) }
    }
    return state
}

/** 组合内加载本地壁纸。 */
@Composable
fun rememberLocalImageBitmap(path: String?): ImageBitmap? {
    val key = path ?: return null
    return remember(key) { loadLocalBitmap(key) }
}

/**
 * 组合内异步加载本地图片（已保存内容网格 / 多图预览用）。
 * 与 [rememberLocalImageBitmap] 的区别：解码放到 IO 线程，避免首帧卡顿；
 * 同一文件路径命中 [ImageMemoryCache]。
 */
@Composable
fun rememberLocalFileImage(file: File?): ImageBitmap? {
    val key = file?.absolutePath
    val state by produceState<ImageBitmap?>(initialValue = key?.let { ImageMemoryCache.get(it) }, key) {
        if (key == null) {
            value = null
            return@produceState
        }
        value = ImageMemoryCache.get(key) ?: withContext(Dispatchers.IO) { loadLocalBitmap(key) }
    }
    return state
}
