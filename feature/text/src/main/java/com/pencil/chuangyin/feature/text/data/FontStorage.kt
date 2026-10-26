package com.pencil.chuangyin.feature.text.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 已登记的字体条目：展示名 + 应用私有目录内的绝对路径。 */
data class FontEntry(
    val name: String,
    val filePath: String,
)

/**
 * 字体持久化契约：把导入的 TTF 文件拷贝到应用私有目录并记录「展示名→路径」映射，
 * 供后续打印时以 [Typeface.createFromFile] 载入。
 */
interface FontStorage {

    /** 从 [uri] 导入并登记为当前字体；失败返回 null。 */
    suspend fun importFont(uri: Uri, contentResolver: ContentResolver, fallbackName: String): FontEntry?

    /** 当前已登记的自定义字体；未导入过返回 null。 */
    fun currentFont(): FontEntry?
}

/**
 * 基于应用私有目录 + SharedPreferences 映射的 [FontStorage] 实现。
 * 文件本体放 filesDir/fonts/，映射放 SharedPreferences（轻量，避免额外 DataStore 工程）。
 */
@Singleton
class DefaultFontStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : FontStorage {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun importFont(
        uri: Uri,
        contentResolver: ContentResolver,
        fallbackName: String,
    ): FontEntry? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val displayName = queryDisplayName(contentResolver, uri, fallbackName)
            val dir = File(context.filesDir, FONTS_DIR).apply { mkdirs() }
            val file = File(dir, "${sanitize(displayName)}.ttf")
            file.writeBytes(bytes)
            val entry = FontEntry(displayName, file.absolutePath)
            prefs.edit()
                .putString(KEY_NAME, displayName)
                .putString(KEY_PATH, file.absolutePath)
                .apply()
            entry
        }.getOrNull()
    }

    override fun currentFont(): FontEntry? {
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val path = prefs.getString(KEY_PATH, null) ?: return null
        return FontEntry(name, path)
    }

    private fun queryDisplayName(
        contentResolver: ContentResolver,
        uri: Uri,
        fallback: String,
    ): String {
        val queried = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return queried?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else fallback
            } else {
                fallback
            }
        } ?: fallback
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "custom_font" }

    private companion object {
        const val PREFS_NAME = "pencil_print_fonts"
        const val FONTS_DIR = "fonts"
        const val KEY_NAME = "current_font_name"
        const val KEY_PATH = "current_font_path"
    }
}