package com.pencil.chuangyin.desktop.pixiv

import java.io.File

/**
 * 桌面端 P 站本地目录约定 —— 对应安卓端的 filesDir / cacheDir：
 * 全部落在 `%APPDATA%\PencilChuangYinPro`（无 APPDATA 时回退到 `~/.config`）。
 */
object PixivPaths {

    private val base: File by lazy {
        val appData = System.getenv("APPDATA")
        val root = if (!appData.isNullOrBlank()) {
            File(appData)
        } else {
            File(System.getProperty("user.home"), ".config")
        }
        File(root, "PencilChuangYinPro")
    }

    /** 手动下载的长期保存目录（清缓存不会删）。 */
    val downloadsDir: File get() = File(base, "pixiv_downloads")

    /** 打印流程自动落盘的缓存（随「清除缓存」回收）。 */
    val printCacheDir: File get() = File(base, "pixiv_print")

    /** 图片磁盘缓存目录（随「清除缓存」回收）。 */
    val imageCacheDir: File get() = File(base, "image_cache")

    private val cacheRoot: File get() = File(base, "cache")

    fun viewDirFor(illustId: Long): File = File(cacheRoot, "pixiv_view_$illustId")

    fun coverDirFor(illustId: Long): File = File(cacheRoot, "pixiv_cover_$illustId")

    fun zipTempDirFor(illustId: Long): File = File(cacheRoot, "pixiv_zip_tmp_$illustId")

    fun ensureBaseDirs() {
        runCatching {
            downloadsDir.mkdirs()
            printCacheDir.mkdirs()
            imageCacheDir.mkdirs()
            cacheRoot.mkdirs()
        }
    }
}

/** 缓存体积快照（对齐安卓端 PixivCacheManager.PixivCacheSizes）。 */
data class PixivCacheSizes(
    val printCacheBytes: Long,
    val imageCacheBytes: Long,
    val downloadsBytes: Long,
) {
    val totalCacheBytes: Long get() = printCacheBytes + imageCacheBytes
}

/**
 * 运行缓存统计与清理 —— 对齐安卓端 PixivCacheManager：
 * 只统计/清理打印原图缓存与图片磁盘缓存；已下载原图长期保存，不参与清理。
 */
object PixivCacheManager {

    fun snapshot(): PixivCacheSizes = PixivCacheSizes(
        printCacheBytes = PixivPaths.printCacheDir.sizeOrZero(),
        imageCacheBytes = PixivPaths.imageCacheDir.sizeOrZero(),
        downloadsBytes = PixivPaths.downloadsDir.sizeOrZero(),
    )

    fun clearCache() {
        runCatching { PixivPaths.printCacheDir.deleteRecursively() }
        runCatching { PixivPaths.imageCacheDir.deleteRecursively() }
        runCatching { PixivPaths.printCacheDir.mkdirs() }
        runCatching { PixivPaths.imageCacheDir.mkdirs() }
    }

    private fun File.sizeOrZero(): Long = size()
}

/** 目录/文件递归体积（不存在记 0）。与安卓端 File.size() 扩展同一算法。 */
internal fun File.size(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return listFiles().orEmpty().sumOf { child ->
        if (child.isDirectory) child.size() else child.length()
    }
}
