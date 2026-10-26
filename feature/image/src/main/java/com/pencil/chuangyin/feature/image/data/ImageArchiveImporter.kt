package com.pencil.chuangyin.feature.image.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.pencil.chuangyin.feature.image.R
import java.io.File
import java.text.Collator
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 压缩包内一张图片的提取结果。
 *
 * @param path 压缩包内的相对路径（用于展示与排序）。
 * @param file 解压到缓存目录的本地文件。
 */
data class ExtractedImage(
    val path: String,
    val file: File,
)

/**
 * 压缩包图片导入结果：扫描**整个压缩包**（含所有子目录）的图片，
 * 解压到缓存目录并按 首字母/数字/拼音 音序排序。
 */
data class ExtractedImages(
    val images: List<ExtractedImage>,
    val tempDir: File,
)

/**
 * 压缩包图片扫描/解压器。文件名编码 UTF-8 优先，乱码时回退 GBK（国产压缩包常见）。
 * 仅扫描不注册 Bitmap，缩略图与打印图均由调用方按需懒解码。
 */
object ImageArchiveImporter {

    private val IMAGE_SUFFIXES = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private val collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE)

    /** 扫描并解压整个压缩包中的图片；一张都没有时返回 null。 */
    fun import(context: Context, uri: Uri): ExtractedImages? {
        // 先 UTF-8 扫一遍；没有图片或出现替换字符 � 则用 GBK 重试。
        val first = runCatching { extractOnce(context, uri, charset("UTF-8")) }.getOrNull()
        val needGbk = first == null || first.images.isEmpty() || first.images.any { looksGarbled(it.path) }
        val result = if (needGbk) {
            runCatching { extractOnce(context, uri, charset("GBK")) }.getOrNull()
        } else {
            first
        } ?: return null
        val sorted = result.images.sortedWith(compareBy(collator) { it.path })
        if (sorted.isEmpty()) return null
        return ExtractedImages(images = sorted, tempDir = result.tempDir)
    }

    private fun extractOnce(
        context: Context,
        uri: Uri,
        charset: java.nio.charset.Charset,
    ): ExtractedImages {
        val tempDir = File(context.cacheDir, "image_import_${System.nanoTime()}").apply { mkdirs() }
        val images = mutableListOf<ExtractedImage>()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { context.getString(R.string.image_archive_read_failed) }
            ZipInputStream(input, charset).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                var index = 0
                while (entry != null) {
                    val rawName = entry.name
                    if (!entry.isDirectory && isImageEntry(rawName)) {
                        val path = normalizePath(rawName)
                        // 防重名（不同目录同名文件/编码差异）。
                        val target = File(tempDir, "${index}_${path.substringAfterLast('/')}")
                        target.outputStream().use { zip.copyTo(it) }
                        images += ExtractedImage(path = path, file = target)
                        index++
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return ExtractedImages(images = images, tempDir = tempDir)
    }

    /** 任意目录层级均可，只按扩展名过滤。 */
    private fun isImageEntry(rawName: String): Boolean {
        val suffix = rawName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return suffix in IMAGE_SUFFIXES
    }

    private fun normalizePath(rawName: String): String =
        rawName.replace('\\', '/').removePrefix("./").trimStart('/')

    private fun looksGarbled(text: String): Boolean = text.contains('�')

    /** 解码缩略图（长边 ≤ [thumbEdge]），供选择页网格使用。 */
    fun decodeThumbnail(file: File, thumbEdge: Int = THUMB_EDGE): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        var longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / 2 >= thumbEdge && longEdge > 0) {
            longEdge /= 2
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
    }.getOrNull()

    /** 清理临时解压目录（清空导入/页面销毁时调用）。 */
    fun cleanup(dir: File) {
        runCatching { dir.deleteRecursively() }
    }

    private const val THUMB_EDGE = 320
}
