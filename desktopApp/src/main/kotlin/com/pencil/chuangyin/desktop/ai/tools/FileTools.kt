package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.ai.AgentTool
import com.pencil.chuangyin.desktop.ai.ToolArtifact
import com.pencil.chuangyin.desktop.ai.ToolArtifactKind
import com.pencil.chuangyin.desktop.ai.ToolContext
import com.pencil.chuangyin.desktop.ai.ToolOutput
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * 存储类工具集合：让 Agent 能像本地客户端一样浏览、读写软件存储目录。
 *
 * 所有路径解析与越界校验统一委托给 [Workspace]；失败以 [ToolOutput.isError] 返回，
 * 由 Agent 循环回灌给模型自行纠错，而不是抛异常中断任务。
 */
class FileTools(private val workspace: Workspace) {

    val tools: List<AgentTool> = listOf(
        agentTool(
            name = ToolNames.LIST_FILES,
            description = "列出目录内容。path 省略时列出工作区根目录；相对路径以工作区为基准。",
            parameters = ToolSchema.parameters(
                required = emptyList(),
                properties = mapOf(
                    ToolParams.PATH to ToolSchema.string("目录的相对或绝对路径，省略则列出工作区根目录。"),
                ),
            ),
        ) { args, _ -> listFiles(args) },

        agentTool(
            name = ToolNames.READ_FILE,
            description = "读取并理解文件内容：文本文件直接返回正文；图片与 PDF 会作为图片提供给你查看；" +
                "Word/Excel/PPT（.docx/.xlsx/.pptx）会提取其中的文字；其余二进制文件返回体积、类型等元信息。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.PATH),
                properties = mapOf(
                    ToolParams.PATH to ToolSchema.string("要读取的文件路径（相对路径以工作区为基准）。"),
                ),
            ),
        ) { args, _ -> readFile(args) },

        agentTool(
            name = ToolNames.WRITE_FILE,
            description = "把文本写入文件（新增或覆盖）。只能写入工作区目录；相对路径以工作区为基准，会自动创建父目录。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.PATH, ToolParams.CONTENT),
                properties = mapOf(
                    ToolParams.PATH to ToolSchema.string("目标文件路径（相对路径以工作区为基准）。"),
                    ToolParams.CONTENT to ToolSchema.string("要写入的文本内容。"),
                ),
            ),
        ) { args, _ -> writeFile(args) },

        agentTool(
            name = ToolNames.DELETE_FILE,
            description = "删除工作区内的文件或目录（目录会递归删除）。仅限工作区，无法删除工作区根目录。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.PATH),
                properties = mapOf(
                    ToolParams.PATH to ToolSchema.string("要删除的文件或目录路径（相对路径以工作区为基准）。"),
                ),
            ),
        ) { args, _ -> deleteFile(args) },

        agentTool(
            name = ToolNames.SEARCH_FILES,
            description = "按关键词搜索文件：同时匹配文件名与小型文本文件的内容（不区分大小写）。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.QUERY),
                properties = mapOf(
                    ToolParams.QUERY to ToolSchema.string("搜索关键词。"),
                    ToolParams.PATH to ToolSchema.string("搜索起始目录，省略时从工作区根目录开始。"),
                ),
            ),
        ) { args, _ -> searchFiles(args) },
    )

    private suspend fun listFiles(args: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val target = runCatching {
            workspace.resolveForRead(args.stringOrNull(ToolParams.PATH) ?: ".")
        }.getOrElse { return@withContext failure(it) }

        if (target.isFile) {
            return@withContext ToolOutput("${workspace.displayPath(target)} 是一个文件（${formatSize(target.length())}）。")
        }
        val children = target.listFiles().orEmpty()
        if (children.isEmpty()) {
            return@withContext ToolOutput("目录 ${workspace.displayPath(target)} 为空。")
        }
        val sorted = children.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() },
        )
        val lines = sorted.take(WorkspaceLimits.MAX_LIST_ENTRIES).map { child ->
            if (child.isDirectory) "[目录] ${child.name}/" else "[文件] ${child.name} (${formatSize(child.length())})"
        }
        val builder = StringBuilder()
        builder.appendLine("目录 ${workspace.displayPath(target)} 共 ${sorted.size} 项：")
        lines.forEach { builder.appendLine("- $it") }
        if (sorted.size > lines.size) builder.appendLine("（仅显示前 ${lines.size} 项）")
        ToolOutput(builder.toString().trim())
    }

    private suspend fun readFile(args: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val path = args.stringOrNull(ToolParams.PATH) ?: return@withContext failure("缺少参数 path")
        val target = runCatching { workspace.resolveForRead(path) }.getOrElse { return@withContext failure(it) }
        if (target.isDirectory) {
            return@withContext ToolOutput("${workspace.displayPath(target)} 是目录，请改用 list_files 查看。", isError = true)
        }
        val display = workspace.displayPath(target)
        when (FileKinds.kindOf(target)) {
            FileKind.Image -> readImage(target, display)
            FileKind.Pdf -> readPdf(target, display)
            FileKind.Ooxml -> readOoxml(target, display)
            FileKind.Text -> readText(target, display)
            FileKind.Binary -> readBinary(target, display)
        }
    }

    private suspend fun writeFile(args: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val path = args.stringOrNull(ToolParams.PATH) ?: return@withContext failure("缺少参数 path")
        val content = args.stringOrNull(ToolParams.CONTENT).orEmpty()
        if (content.length > WorkspaceLimits.MAX_WRITE_CHARS) {
            return@withContext ToolOutput(
                "内容过长（${content.length} 字符，上限 ${WorkspaceLimits.MAX_WRITE_CHARS}），请分段写入。",
                isError = true,
            )
        }
        val target = runCatching { workspace.resolveForWrite(path) }.getOrElse { return@withContext failure(it) }
        if (target.isDirectory) {
            return@withContext ToolOutput("${workspace.displayPath(target)} 是目录，无法写入。", isError = true)
        }
        runCatching { target.writeText(content) }
            .getOrElse { return@withContext ToolOutput("写入失败：${it.message ?: it.javaClass.simpleName}", isError = true) }
        ToolOutput("已写入 ${workspace.displayPath(target)}（${content.length} 字符）。")
    }

    private suspend fun deleteFile(args: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val path = args.stringOrNull(ToolParams.PATH) ?: return@withContext failure("缺少参数 path")
        val target = runCatching { workspace.resolveForDelete(path) }.getOrElse { return@withContext failure(it) }
        val display = workspace.displayPath(target)
        val deleted = runCatching { target.deleteRecursively() }
            .getOrElse { return@withContext ToolOutput("删除失败：${it.message ?: it.javaClass.simpleName}", isError = true) }
        if (deleted) ToolOutput("已删除 $display。") else ToolOutput("删除失败：$display", isError = true)
    }

    private suspend fun searchFiles(args: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val needle = args.stringOrNull(ToolParams.QUERY) ?: return@withContext failure("缺少参数 query")
        val startPath = args.stringOrNull(ToolParams.PATH) ?: "."
        val start = runCatching { workspace.resolveForRead(startPath) }.getOrElse { return@withContext failure(it) }
        val root = if (start.isDirectory) start else start.parentFile ?: workspace.writeRoot

        val matches = ArrayList<String>()
        collectMatches(root, 0, needle.lowercase(), needle, matches)
        if (matches.isEmpty()) {
            return@withContext ToolOutput("未找到与「$needle」匹配的文件。")
        }
        val builder = StringBuilder()
        builder.appendLine("找到 ${matches.size} 个匹配「$needle」的文件：")
        matches.forEach { builder.appendLine("- $it") }
        ToolOutput(builder.toString().trim())
    }

    /** 图片：不解码正文，改为以 IMAGE 产物返回，由引擎作为多模态图片交给模型查看。 */
    private fun readImage(file: File, display: String): ToolOutput {
        val size = runCatching {
            ImageIO.createImageInputStream(file)?.use { stream ->
                val readers = ImageIO.getImageReaders(stream)
                if (!readers.hasNext()) {
                    null
                } else {
                    val reader = readers.next()
                    try {
                        reader.input = stream
                        "${reader.getWidth(0)}×${reader.getHeight(0)} 像素"
                    } finally {
                        reader.dispose()
                    }
                }
            }
        }.getOrNull() ?: "尺寸未知"
        return ToolOutput(
            content = "文件 $display 是一张图片（$size，${formatSize(file.length())}）。" +
                "图片内容已随本次工具结果提供，请直接查看并理解它。",
            artifacts = listOf(imageArtifact(file)),
        )
    }

    /** PDF：逐页渲染为 PNG 产物，交由多模态理解（纯文本抽取对 PDF 不可靠）。 */
    private fun readPdf(file: File, display: String): ToolOutput {
        val outputDir = runCatching { workspace.resolveForWrite(file.nameWithoutExtension + "_pages") }
            .getOrElse { return ToolOutput("PDF 输出目录创建失败：${it.message}", isError = true) }
        val pages = runCatching { DocumentReader.renderPdf(file, outputDir, MAX_PDF_PAGES) }
            .getOrElse { return ToolOutput("渲染 PDF 失败：${it.message ?: it.javaClass.simpleName}", isError = true) }
        if (pages.isEmpty()) {
            return ToolOutput("PDF $display 没有可渲染的页面。", isError = true)
        }
        return ToolOutput(
            content = "文件 $display 已渲染为 ${pages.size} 页图片（保存在 ${workspace.displayPath(outputDir)}），" +
                "内容已随本次工具结果提供，请直接查看并理解它们。",
            artifacts = pages.map { imageArtifact(it) },
        )
    }

    /** Office 文档：从 zip 容器内抽取文字；抽取失败则退化为二进制元信息。 */
    private fun readOoxml(file: File, display: String): ToolOutput {
        val text = runCatching { DocumentReader.ooxmlText(file) }.getOrNull()
        if (text.isNullOrBlank()) return readBinary(file, display)
        return textResult(display, text)
    }

    /** 文本：直接返回正文，超长截断。 */
    private fun readText(file: File, display: String): ToolOutput {
        val text = runCatching { file.readText() }
            .getOrElse { return ToolOutput("读取失败：${it.message ?: it.javaClass.simpleName}", isError = true) }
        return textResult(display, text)
    }

    /** 二进制：无法作为文本阅读时，给出体积、MIME 与文件头魔数，指引后续操作。 */
    private fun readBinary(file: File, display: String): ToolOutput {
        val head = runCatching { file.inputStream().use { it.readNBytes(BINARY_HEAD_BYTES) } }
            .getOrDefault(ByteArray(0))
        val hex = head.joinToString(" ") { "%02X".format(it) }
        return ToolOutput(
            "文件 $display 是二进制文件（${formatSize(file.length())}，MIME 约 ${mimeTypeOf(file)}），无法作为文本直接阅读。\n" +
                "文件头（前 ${head.size} 字节）：$hex\n" +
                "若它是压缩包或 Office 文档，可先用 extract_archive 解压查看内部结构。",
        )
    }

    /** 深度优先遍历收集命中项，达到数量上限或超过深度上限即停止。 */
    private fun collectMatches(
        dir: File,
        depth: Int,
        needle: String,
        query: String,
        out: MutableList<String>,
    ) {
        if (depth > WorkspaceLimits.MAX_SEARCH_DEPTH || out.size >= WorkspaceLimits.MAX_SEARCH_RESULTS) return
        val children = dir.listFiles().orEmpty()
        for (child in children) {
            if (out.size >= WorkspaceLimits.MAX_SEARCH_RESULTS) return
            if (child.isDirectory) {
                collectMatches(child, depth + 1, needle, query, out)
            } else {
                val nameHit = child.name.lowercase().contains(needle)
                val contentHit = !nameHit && isTextCandidate(child) && contentContains(child, query)
                if (nameHit || contentHit) {
                    out += "${workspace.displayPath(child)}（${if (nameHit) "文件名匹配" else "内容匹配"}）"
                }
            }
        }
    }

    private fun isTextCandidate(file: File): Boolean {
        if (file.length() > MAX_CONTENT_SCAN_BYTES) return false
        return file.extension.lowercase() in TEXT_EXTENSIONS
    }

    private fun contentContains(file: File, query: String): Boolean =
        runCatching { file.readText().contains(query, ignoreCase = true) }.getOrDefault(false)

    private fun textResult(display: String, text: String): ToolOutput =
        if (text.length <= WorkspaceLimits.MAX_READ_CHARS) {
            ToolOutput("文件 $display（${text.length} 字符）：\n$text")
        } else {
            ToolOutput(
                "文件 $display（共 ${text.length} 字符，已截断到 ${WorkspaceLimits.MAX_READ_CHARS} 字符）：\n" +
                    text.take(WorkspaceLimits.MAX_READ_CHARS),
            )
        }

    private companion object {
        const val MAX_PDF_PAGES = 8
        const val BINARY_HEAD_BYTES = 16
        const val MAX_CONTENT_SCAN_BYTES = 512_000L
        val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "csv", "log", "xml", "html", "htm", "yml", "yaml", "kt", "java",
        )
    }
}

/** 图片产物。 */
internal fun imageArtifact(file: File): ToolArtifact = ToolArtifact(
    id = UUID.randomUUID().toString(),
    kind = ToolArtifactKind.IMAGE,
    path = file.absolutePath,
    label = file.name,
)

/** 通用文件产物。 */
internal fun fileArtifact(file: File): ToolArtifact = ToolArtifact(
    id = UUID.randomUUID().toString(),
    kind = ToolArtifactKind.FILE,
    path = file.absolutePath,
    label = file.name,
)
