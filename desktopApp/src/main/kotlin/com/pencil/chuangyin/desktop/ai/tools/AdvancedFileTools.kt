package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.ai.AgentTool
import com.pencil.chuangyin.desktop.ai.ToolContext
import com.pencil.chuangyin.desktop.ai.ToolOutput
import java.io.File
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * 进阶文件操作：建目录、复制、移动、查看文件详情。
 *
 * 复制源可以是可读根内的任意文件（含产物输出目录、用户目录），目标必须落在 agent 目录内，
 * 因此 AI 能把外部产物「搬进」自己的工作区再处理。
 */
class AdvancedFileTools(private val workspace: Workspace) {

    val tools: List<AgentTool> = listOf(
        agentTool(
            name = ToolNames.MAKE_DIR,
            description = "在 agent 工作目录内创建目录，支持多级（如 reports/2024）。已存在时视为成功。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.PATH),
                properties = mapOf(
                    ToolParams.PATH to ToolSchema.string("要创建的目录路径；相对路径以 agent 工作目录为基准。"),
                ),
            ),
        ) { args, _ -> makeDir(args) },

        agentTool(
            name = ToolNames.COPY_FILE,
            description = "复制一个文件到 agent 工作目录。源可位于 agent 目录或软件存储/用户目录。" +
                "常用于把外部下载的图片、文档复制进工作区。目标已存在时会被覆盖。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.SOURCE, ToolParams.DESTINATION),
                properties = mapOf(
                    ToolParams.SOURCE to ToolSchema.string("源文件路径。"),
                    ToolParams.DESTINATION to ToolSchema.string(
                        "目标路径（必须位于 agent 目录内）。以 / 或 \\ 结尾、或指向已存在目录时，沿用源文件名。",
                    ),
                ),
            ),
        ) { args, _ -> copyFile(args) },

        agentTool(
            name = ToolNames.MOVE_FILE,
            description = "在 agent 工作目录内移动/重命名文件或目录。目标已存在时会被覆盖。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.SOURCE, ToolParams.DESTINATION),
                properties = mapOf(
                    ToolParams.SOURCE to ToolSchema.string("源路径（必须位于 agent 目录内）。"),
                    ToolParams.DESTINATION to ToolSchema.string("目标路径（必须位于 agent 目录内）。"),
                ),
            ),
        ) { args, _ -> moveFile(args) },

        agentTool(
            name = ToolNames.FILE_INFO,
            description = "查看文件或目录的详细信息：类型、大小、修改时间；" +
                "图片额外给出宽高，文本文件给出行数与开头预览。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.PATH),
                properties = mapOf(
                    ToolParams.PATH to ToolSchema.string("目标路径；相对路径以 agent 工作目录为基准。"),
                ),
            ),
        ) { args, _ -> fileInfo(args) },
    )

    private suspend fun makeDir(args: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val path = args.stringOrNull(ToolParams.PATH) ?: return@withContext fileFailure("缺少参数 path")
        runCatching {
            val dir = workspace.makeDirectories(path)
            ToolOutput("已创建目录：${workspace.displayPath(dir)}")
        }.getOrElse { fileFailure(it.message ?: "创建目录失败") }
    }

    private suspend fun copyFile(args: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val source = args.stringOrNull(ToolParams.SOURCE) ?: return@withContext fileFailure("缺少参数 source")
        val destination = args.stringOrNull(ToolParams.DESTINATION) ?: return@withContext fileFailure("缺少参数 destination")
        runCatching {
            val src = workspace.resolveForRead(source)
            if (src.isDirectory) {
                return@runCatching fileFailure("源是目录：${workspace.displayPath(src)}。请用 create_archive 打包，或逐个复制文件。")
            }
            val dst = resolveDestination(workspace, destination, src.name)
            if (dst.exists()) dst.delete()
            src.copyTo(dst, overwrite = true)
            ToolOutput("已复制到 ${workspace.displayPath(dst)}（${formatFileSize(dst.length())}）。")
        }.getOrElse { fileFailure(it.message ?: "复制失败") }
    }

    private suspend fun moveFile(args: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val source = args.stringOrNull(ToolParams.SOURCE) ?: return@withContext fileFailure("缺少参数 source")
        val destination = args.stringOrNull(ToolParams.DESTINATION) ?: return@withContext fileFailure("缺少参数 destination")
        runCatching {
            val src = workspace.resolveForDelete(source)
            val dst = resolveDestination(workspace, destination, src.name)
            if (dst.exists()) dst.delete()
            val moved = if (src.renameTo(dst)) {
                true
            } else {
                // 跨卷时 rename 会失败，退化为复制后删除。
                src.copyRecursively(dst, overwrite = true).also { ok -> if (ok) src.deleteRecursively() }
            }
            if (moved) {
                ToolOutput("已移动到 ${workspace.displayPath(dst)}。")
            } else {
                fileFailure("移动失败：${workspace.displayPath(src)} → ${workspace.displayPath(dst)}")
            }
        }.getOrElse { fileFailure(it.message ?: "移动失败") }
    }

    private suspend fun fileInfo(args: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val path = args.stringOrNull(ToolParams.PATH) ?: return@withContext fileFailure("缺少参数 path")
        val file = runCatching { workspace.resolveForRead(path) }
            .getOrElse { return@withContext fileFailure(it.message ?: "路径无效") }
        val builder = StringBuilder()
        builder.appendLine("名称：${file.name}")
        builder.appendLine("路径：${workspace.displayPath(file)}")
        builder.appendLine("类型：${if (file.isDirectory) "目录" else "文件"}")
        builder.appendLine("修改时间：${FILE_TIME_FORMAT.format(Date(file.lastModified()))}")
        if (file.isDirectory) {
            builder.appendLine("子项数量：${file.listFiles()?.size ?: 0}")
            return@withContext ToolOutput(builder.toString().trim())
        }
        builder.appendLine("大小：${formatFileSize(file.length())}（${file.length()} 字节）")
        val mime = mimeTypeOf(file)
        builder.appendLine("MIME：$mime")
        if (mime.startsWith("image/")) {
            val size = runCatching {
                ImageIO.createImageInputStream(file)?.use { stream ->
                    val readers = ImageIO.getImageReaders(stream)
                    if (!readers.hasNext()) {
                        null
                    } else {
                        val reader = readers.next()
                        try {
                            reader.input = stream
                            "${reader.getWidth(0)}×${reader.getHeight(0)}"
                        } finally {
                            reader.dispose()
                        }
                    }
                }
            }.getOrNull()
            if (size != null) builder.appendLine("尺寸：$size 像素")
        } else if (mime.startsWith("text/") || mime == "application/json") {
            val text = runCatching { file.readText().take(WorkspaceLimits.MAX_READ_CHARS) }.getOrNull()
            if (text != null) {
                builder.appendLine("行数：${text.count { it == '\n' } + 1}")
                builder.appendLine("--- 开头预览 ---")
                builder.appendLine(text.take(PREVIEW_CHARS))
            }
        }
        ToolOutput(builder.toString().trim())
    }

    private companion object {
        const val PREVIEW_CHARS = 400
    }
}

/** 目标路径解析：以 / 或 \ 结尾、或指向已存在目录时，沿用源文件名。 */
internal fun resolveDestination(workspace: Workspace, destination: String, sourceName: String): File {
    val trimmed = destination.trim()
    val asDir = trimmed.endsWith("/") || trimmed.endsWith("\\")
    val existingDir = !asDir && runCatching { workspace.resolveForWrite(trimmed).isDirectory }.getOrDefault(false)
    return if (asDir || existingDir) {
        workspace.resolveForWrite(trimmed.trimEnd('/', '\\') + "/" + sourceName)
    } else {
        workspace.resolveForWrite(trimmed)
    }
}

/** 人类可读的文件大小。 */
internal fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

/** 按扩展名推断 MIME 类型（桌面端用 `URLConnection` 代替安卓的 MimeTypeMap）。 */
internal fun mimeTypeOf(file: File): String {
    val ext = file.extension.lowercase(Locale.US)
    if (ext.isEmpty()) return "application/octet-stream"
    val guessed = runCatching { URLConnection.guessContentTypeFromName(file.name) }.getOrNull()
    return guessed ?: EXTRA_MIME[ext] ?: "application/octet-stream"
}

private val EXTRA_MIME = mapOf(
    "md" to "text/markdown",
    "markdown" to "text/markdown",
    "json" to "application/json",
    "yml" to "text/yaml",
    "yaml" to "text/yaml",
    "kt" to "text/plain",
    "kts" to "text/plain",
    "webp" to "image/webp",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
)

/** 统一的失败输出。 */
internal fun fileFailure(message: String): ToolOutput = ToolOutput(message, isError = true)

internal val FILE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
