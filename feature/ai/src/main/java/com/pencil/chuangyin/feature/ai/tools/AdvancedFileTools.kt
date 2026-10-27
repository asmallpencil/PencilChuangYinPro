package com.pencil.chuangyin.feature.ai.tools

import android.webkit.MimeTypeMap
import android.graphics.BitmapFactory
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ToolOutput
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 进阶文件操作：建目录、复制、移动、查看文件详情。
 *
 * 复制源可以是可读根内的任意文件（含 P 站下载目录），目标必须落在 agent 目录内，
 * 因此 AI 能把外部产物「搬进」自己的工作区再处理。
 */
@Singleton
class AdvancedFileTools @Inject constructor(
    private val workspace: Workspace,
) {

    /** 在 agent 工作目录内创建目录，支持多级（如 reports/2024）。已存在时视为成功。 */
    @Tool
    suspend fun make_dir(
        @Param(description = "要创建的目录路径；相对路径以 agent 工作目录为基准。") path: String,
    ): ToolOutput {
        return runCatching {
            val dir = workspace.makeDirectories(path.trim())
            ToolOutput("已创建目录：${workspace.displayPath(dir)}")
        }.getOrElse { fileFailure(it.message ?: "创建目录失败") }
    }

    /**
     * 复制一个文件到 agent 工作目录。源可位于 agent 目录或软件存储目录（含 P 站下载）。
     * 常用于把外部下载的图片/小说复制进工作区。目标已存在时会被覆盖。
     */
    @Tool
    suspend fun copy_file(
        @Param(description = "源文件路径。") source: String,
        @Param(description = "目标路径（必须位于 agent 目录内）。以 / 结尾或指向已存在目录时，沿用源文件名。") destination: String,
    ): ToolOutput {
        return runCatching {
            val src = workspace.resolveForRead(source.trim())
            if (src.isDirectory) return@runCatching fileFailure("源是目录：${workspace.displayPath(src)}。请用 create_archive 打包，或逐个复制文件。")
            val dst = resolveDestination(workspace, destination.trim(), src.name).first
            val copied = withContext(Dispatchers.IO) {
                if (workspace.isInsideWorkspace(dst)) {
                    // 目标在 agent 目录：授权目录下必须走 SAF 写。
                    workspace.readAgent(src)?.use { input ->
                        workspace.writeBytes(dst, input.readBytes())
                    } ?: runCatching { src.copyTo(dst, overwrite = true); true }.getOrDefault(false)
                } else {
                    runCatching { src.copyTo(dst, overwrite = true); true }.getOrDefault(false)
                }
            }
            if (!copied) return@runCatching fileFailure("复制失败：${workspace.displayPath(src)} → ${workspace.displayPath(dst)}")
            ToolOutput("已复制到 ${workspace.displayPath(dst)}（${formatFileSize(src.length())}）。")
        }.getOrElse { fileFailure(it.message ?: "复制失败") }
    }

    /** 在 agent 工作目录内移动/重命名文件或目录。目标已存在时会被覆盖。 */
    @Tool
    suspend fun move_file(
        @Param(description = "源路径（必须位于 agent 目录内）。") source: String,
        @Param(description = "目标路径（必须位于 agent 目录内）。") destination: String,
    ): ToolOutput {
        return runCatching {
            val src = workspace.resolveForDelete(source.trim())
            val dst = resolveDestination(workspace, destination.trim(), src.name).first
            val moved = withContext(Dispatchers.IO) {
                when {
                    // 授权目录下没有真实文件可 rename，按「读流 → 写新文件 → 删旧文件」搬移。
                    workspace.usesOutputDir -> {
                        val bytes = workspace.readAgent(src)?.use { it.readBytes() }
                        if (bytes != null && workspace.writeBytes(dst, bytes)) {
                            workspace.deleteInWorkspace(src)
                        } else {
                            false
                        }
                    }

                    src.renameTo(dst) -> true
                    // 跨卷/跨文件系统时 rename 会失败，退化为复制后删除。
                    else -> src.copyRecursively(dst, overwrite = true).also { ok -> if (ok) src.deleteRecursively() }
                }
            }
            if (moved) {
                ToolOutput("已移动到 ${workspace.displayPath(dst)}。")
            } else {
                fileFailure("移动失败：${workspace.displayPath(src)} → ${workspace.displayPath(dst)}")
            }
        }.getOrElse { fileFailure(it.message ?: "移动失败") }
    }

    /** 查看文件或目录的详细信息：类型、大小、修改时间；图片额外给出宽高，文本文件给出行数与开头预览。 */
    @Tool
    suspend fun file_info(
        @Param(description = "目标路径；相对路径以 agent 工作目录为基准。") path: String,
    ): ToolOutput {
        val file = runCatching { workspace.resolveForRead(path.trim()) }
            .getOrElse { return fileFailure(it.message ?: "路径无效") }
        val readable = workspace.materialize(file) ?: file
        val builder = StringBuilder()
        builder.appendLine("名称：${readable.name}")
        builder.appendLine("路径：${workspace.displayPath(file)}")
        builder.appendLine("类型：${if (readable.isDirectory) "目录" else "文件"}")
        builder.appendLine("修改时间：${FILE_TIME_FORMAT.format(Date(readable.lastModified()))}")
        if (readable.isDirectory) {
            builder.appendLine("子项数量：${readable.listFiles()?.size ?: 0}")
            return ToolOutput(builder.toString().trim())
        }
        builder.appendLine("大小：${formatFileSize(readable.length())}（${readable.length()} 字节）")
        val mime = mimeTypeOf(readable)
        builder.appendLine("MIME：$mime")
        if (mime.startsWith("image/")) {
            val bounds = withContext(Dispatchers.IO) {
                BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { options ->
                    BitmapFactory.decodeFile(readable.absolutePath, options)
                }
            }
            builder.appendLine("尺寸：${bounds.outWidth}×${bounds.outHeight} 像素")
        } else if (mime.startsWith("text/") || mime == "application/json") {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    (workspace.readAgent(file)?.use { it.readBytes().decodeToString() } ?: readable.readText())
                        .take(WorkspaceLimits.MAX_READ_CHARS)
                }.getOrNull()
            }
            if (text != null) {
                builder.appendLine("行数：${text.count { it == '\n' } + 1}")
                builder.appendLine("--- 开头预览 ---")
                builder.appendLine(text.take(PREVIEW_CHARS))
            }
        }
        return ToolOutput(builder.toString().trim())
    }

    private companion object {
        const val PREVIEW_CHARS = 400
    }
}

/** 目标路径解析：以 / 结尾或指向已存在目录时，沿用源文件名。返回目标文件与「是否按目录处理」。 */
internal fun resolveDestination(workspace: Workspace, destination: String, sourceName: String): Pair<File, Boolean> {
    val trimmed = destination.trim()
    val asDir = trimmed.endsWith("/") || trimmed.endsWith("\\")
    val existingDir = !asDir && runCatching { workspace.resolveForWrite(trimmed).isDirectory }.getOrDefault(false)
    return if (asDir || existingDir) {
        workspace.resolveForWrite(trimmed.trimEnd('/', '\\') + "/" + sourceName) to true
    } else {
        workspace.resolveForWrite(trimmed) to false
    }
}

/** 人类可读的文件大小。 */
internal fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

/** 按扩展名推断 MIME 类型。 */
internal fun mimeTypeOf(file: File): String {
    val ext = file.extension.lowercase(Locale.US)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

/** 统一的失败输出。 */
internal fun fileFailure(message: String): ToolOutput = ToolOutput(message, isError = true)

internal val FILE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
