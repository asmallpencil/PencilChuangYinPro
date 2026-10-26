package com.pencil.chuangyin.feature.pixiv.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 缓存分项占用（字节）。 */
data class PixivCacheSizes(
    val printCacheBytes: Long,
    val imageCacheBytes: Long,
    val downloadsBytes: Long,
) {
    val totalCacheBytes: Long get() = printCacheBytes + imageCacheBytes
}

/**
 * P 站缓存统计与清理（侧边栏「运行缓存」「清除缓存」）。
 * - printCache：打印用原图缓存 cacheDir/pixiv_print（随系统清理可回收）；
 * - imageCache：Coil 磁盘缓存 cacheDir/image_cache；
 * - downloads：filesDir/pixiv_downloads 长期保存的下载图（不随清缓存删除，仅展示）。
 */
@Singleton
class PixivCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun snapshot(): PixivCacheSizes = withContext(Dispatchers.IO) {
        PixivCacheSizes(
            printCacheBytes = File(context.cacheDir, PRINT_DIR).size(),
            imageCacheBytes = File(context.cacheDir, IMAGE_CACHE_DIR).size(),
            downloadsBytes = File(context.filesDir, DOWNLOADS_DIR).size(),
        )
    }

    /** 清除可回收缓存（打印原图 + 图片磁盘缓存），不动长期下载。 */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        File(context.cacheDir, PRINT_DIR).deleteRecursively()
        File(context.cacheDir, IMAGE_CACHE_DIR).deleteRecursively()
        Unit
    }

    private fun File.size(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        return listFiles().orEmpty().sumOf { child ->
            if (child.isDirectory) child.size() else child.length()
        }
    }

    private companion object {
        const val PRINT_DIR = "pixiv_print"
        const val IMAGE_CACHE_DIR = "image_cache"
        const val DOWNLOADS_DIR = "pixiv_downloads"
    }
}
