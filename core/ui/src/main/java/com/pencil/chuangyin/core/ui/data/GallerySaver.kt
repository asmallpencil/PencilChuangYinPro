package com.pencil.chuangyin.core.ui.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把处理后的黑白位图保存到系统相册（PNG 无损）。
 *
 * - Android Q+：[MediaStore] + RELATIVE_PATH，无需权限；
 * - Android 9 及以下：写入公共 Pictures 目录（需 WRITE_EXTERNAL_STORAGE，界面负责申请）。
 *
 * 输出目录由 [OutputPathRepository]（软件设置里的「产物输出路径」）决定，
 * 默认位于公共图片目录 PencilChuangyinPro。
 *
 * 返回保存成功的图片数量；单张失败不影响其它张。
 *
 * 归属 core:ui：文字打印与图片打印两个 feature 都要保存预览位图，
 * 收敛到公共层避免跨 feature 依赖（原先在 feature/image/data）。
 */
@Singleton
class GallerySaver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outputPathRepository: OutputPathRepository,
) {
    fun save(bitmaps: List<Bitmap>, baseName: String): Int {
        var saved = 0
        bitmaps.forEachIndexed { index, bitmap ->
            val displayName = if (bitmaps.size == 1) {
                "$baseName.png"
            } else {
                "${baseName}_${index + 1}.png"
            }
            val ok = saveNow(bitmap, displayName)
            if (ok) saved++
        }
        return saved
    }

    /**
     * 优先写入用户自定义的完整绝对路径（可下载到任意可写位置）；写入失败或未设置
     * 自定义路径时，回退到 MediaStore（Q+）/ 公共 Pictures 目录（API 29 及以下）。
     */
    private fun saveNow(bitmap: Bitmap, displayName: String): Boolean {
        val customPath = outputPathRepository.outputPath.value
        if (customPath.isNotBlank()) {
            val ok = runCatching {
                val dir = File(customPath)
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, displayName)).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(bitmap, displayName)
        } else {
            saveLegacy(bitmap, displayName)
        }
    }

    private fun saveViaMediaStore(bitmap: Bitmap, displayName: String): Boolean = runCatching {
        val resolver = context.contentResolver
        val relativePath = requiresRelativePathSuffix(outputPathRepository.relativeMediaStorePath())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_PNG)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun saveLegacy(bitmap: Bitmap, displayName: String): Boolean = runCatching {
        val dir = outputPathRepository.legacyPublicDir()
        if (!dir.exists()) dir.mkdirs()
        FileOutputStream(File(dir, displayName)).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        true
    }.getOrDefault(false)

    /** MediaStore 的 RELATIVE_PATH 需要以 "/" 结尾，否则某些设备不识别。 */
    private fun requiresRelativePathSuffix(path: String): String =
        if (path.endsWith("/")) path else "$path/"

    private companion object {
        private const val MIME_PNG = "image/png"
    }
}
