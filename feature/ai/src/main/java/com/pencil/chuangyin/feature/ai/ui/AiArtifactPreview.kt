package com.pencil.chuangyin.feature.ai.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.pencil.chuangyin.feature.ai.tools.Workspace
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把产物 [AiUiArtifact.path] 解析成「可直接解码」的缓存图片路径。
 *
 * 授权了输出目录时产物真实位置在 SAF 树里，[AiUiArtifact.path] 只是文件名，
 * 不能直接交给 `BitmapFactory.decodeFile`；[Workspace.materialize] 负责把字节取回缓存。
 */
suspend fun AiUiArtifact.previewPath(workspace: Workspace): String? = withContext(Dispatchers.IO) {
    workspace.materialize(File(path))?.absolutePath
}

/** 解码产物图片用于界面预览；失败返回 null。 */
suspend fun AiUiArtifact.decodePreview(workspace: Workspace): ImageBitmap? = withContext(Dispatchers.IO) {
    val readable = workspace.materialize(File(path)) ?: return@withContext null
    runCatching { BitmapFactory.decodeFile(readable.absolutePath)?.asImageBitmap() }.getOrNull()
}
