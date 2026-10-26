package com.pencil.chuangyin.desktop.support

import java.io.File

/**
 * 桌面端软件私有目录（替代安卓端的 `context.filesDir` / `getExternalFilesDir`）。
 *
 * 根目录：`%APPDATA%\PencilChuangYinPro`（无 APPDATA 时回退用户主目录），
 * 其下按用途分目录：`agent`（AI 工作区兜底）、`ai_conversations`（会话存档）等。
 */
object DesktopPaths {

    const val APP_DIR_NAME = "PencilChuangYinPro"

    /** 软件数据根目录（不保证已创建）。 */
    fun appDataRoot(): File {
        val appData = System.getenv("APPDATA")
        val base = if (!appData.isNullOrBlank()) File(appData) else File(System.getProperty("user.home"))
        return File(base, APP_DIR_NAME)
    }

    /** 软件数据根目录下的子目录（自动创建，失败返回 null）。 */
    fun subDir(name: String): File? = runCatching {
        val dir = File(appDataRoot(), name)
        if (!dir.exists()) dir.mkdirs()
        dir.takeIf { it.isDirectory }
    }.getOrNull()

    /** 用户主目录。 */
    fun userHome(): File = File(System.getProperty("user.home"))
}
