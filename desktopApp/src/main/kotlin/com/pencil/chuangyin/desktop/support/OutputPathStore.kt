package com.pencil.chuangyin.desktop.support

import java.io.File
import java.util.prefs.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 产物输出目录 —— 对齐安卓端 OutputPathRepository 的对外语义：
 * - 默认目录：`图片/PencilChuangyinPro`（桌面为 `%USERPROFILE%\Pictures\PencilChuangyinPro`）；
 * - 用户可指定完整绝对路径（桌面用 JFileChooser 选目录），清空即回到默认目录。
 *
 * 桌面端用 Preferences 代替 DataStore，行为保持一致。
 */
object OutputPathStore {

    /** 默认输出目录名（与安卓端 OUTPUT_DIR_DEFAULT 一致）。 */
    const val DEFAULT_DIR_NAME = "PencilChuangyinPro"

    private val prefs: Preferences by lazy {
        Preferences.userRoot().node("com/pencil/chuangyin/pro/output")
    }

    private val _customPath = MutableStateFlow(prefs.get(KEY_PATH, "").orEmpty())
    val customPath: StateFlow<String> = _customPath.asStateFlow()

    /** 当前真实可写的输出目录（不保证已创建）。 */
    fun activeDir(): File {
        val custom = _customPath.value.trim()
        return if (custom.isNotEmpty()) File(custom) else defaultDir()
    }

    fun defaultDir(): File = File(System.getProperty("user.home"), "Pictures/$DEFAULT_DIR_NAME")

    /** 展示用路径（未自定义时展示默认目录）。 */
    fun displayPath(): String = activeDir().absolutePath

    fun setCustomPath(path: String) {
        val safe = path.trim()
        _customPath.value = safe
        runCatching {
            prefs.put(KEY_PATH, safe)
            prefs.flush()
        }
    }

    /** 确保目录存在；失败返回 null。 */
    fun ensureDir(): File? = runCatching {
        val dir = activeDir()
        if (!dir.exists()) dir.mkdirs()
        dir.takeIf { it.isDirectory }
    }.getOrNull()

    private const val KEY_PATH = "output_path"
}
