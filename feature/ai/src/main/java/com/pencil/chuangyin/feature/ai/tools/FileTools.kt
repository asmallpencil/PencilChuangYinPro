package com.pencil.chuangyin.feature.ai.tools

import android.graphics.BitmapFactory
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.model.ToolOutput
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 存储类工具集合：让 Agent 能像本地客户端一样浏览、读写软件存储目录。
 *
 * 所有路径解析与越界校验统一委托给 [Workspace]；失败以 [ToolOutput.isError] 返回，
 * 由 Agent 循环回灌给模型自行纠错，而不是抛异常中断任务。
 */
@Singleton
class FileTools @Inject constructor(
    private val workspace: Workspace,
) {

    /** 列出目录内容。path 省略时列出工作区根目录；相对路径以工作区为基准。 */
    @Tool
    suspend fun list_files(
        @Param(description = "目录的相对或绝对路径，省略则列出工作区根目录。") path: String?,
    ): ToolOutput {
        val target = runCatching { workspace.resolveForRead(path?.trim()?.takeIf { it.isNotEmpty() } ?: ".") }
            .getOrElse { return failure(it) }

        if (target.isFile) {
            return ToolOutput("${workspace.displayPath(target)} 是一个文件（${formatSize(target.length())}）。")
        }
        val children = target.listFiles().orEmpty()
        if (children.isEmpty()) {
            return ToolOutput("目录 ${workspace.displayPath(target)} 为空。")
        }
        val sorted = children.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() },
        )
        val lines = sorted.take(WorkspaceLimits.MAX_LIST_ENTRIES).map { child ->
            if (child.isDirectory) {
                "[目录] ${child.name}/"
            } else {
                "[文件] ${child.name} (${formatSize(child.length())})"
            }
        }
        val builder = StringBuilder()
        builder.appendLine("目录 ${workspace.displayPath(target)} 共 ${sorted.size} 项：")
        lines.forEach { builder.appendLine("- $it") }
        if (sorted.size > lines.size) {
            builder.appendLine("（仅显示前 ${lines.size} 项）")
        }
        return ToolOutput(builder.toString().trim())
    }

    /**
     * 读取并理解文件内容：文本文件直接返回正文；图片与 PDF 会作为图片提供给你查看；
     * Word/Excel/PPT（.docx/.xlsx/.pptx）会提取其中的文字；其余二进制文件返回体积、类型等元信息。
     */
    @Tool
    suspend fun read_file(
        @Param(description = "要读取的文件路径（相对路径以工作区为基准）。") path: String,
    ): ToolOutput {
        val target = runCatching { workspace.resolveForRead(path.trim()) }
            .getOrElse { return failure(it) }
        if (target.isDirectory) {
            return ToolOutput("${workspace.displayPath(target)} 是目录，请改用 list_files 查看。", isError = true)
        }
        val display = workspace.displayPath(target)
        return when (FileKinds.kindOf(target)) {
            FileKind.Image -> readImage(target, display)
            FileKind.Pdf -> readPdf(target, display)
            FileKind.Ooxml -> readOoxml(target, display)
            FileKind.Text -> readText(target, display)
            FileKind.Binary -> readBinary(target, display)
        }
    }

    /** 把文本写入文件（新增或覆盖）。只能写入工作区目录；相对路径以工作区为基准，会自动创建父目录。 */
    @Tool
    suspend fun write_file(
        @Param(description = "目标文件路径（相对路径以工作区为基准）。") path: String,
        @Param(description = "要写入的文本内容。") content: String,
    ): ToolOutput {
        if (content.length > WorkspaceLimits.MAX_WRITE_CHARS) {
            return ToolOutput(
                "内容过长（${content.length} 字符，上限 ${WorkspaceLimits.MAX_WRITE_CHARS}），请分段写入。",
                isError = true,
            )
        }
        val target = runCatching { workspace.resolveForWrite(path.trim()) }
            .getOrElse { return failure(it) }
        if (target.isDirectory) {
            return ToolOutput("${workspace.displayPath(target)} 是目录，无法写入。", isError = true)
        }
        val written = runCatching { workspace.writeBytes(target, content.toByteArray()) }
            .getOrElse { return ToolOutput("写入失败：${it.message ?: it.javaClass.simpleName}", isError = true) }
        if (!written) return ToolOutput("写入失败：${workspace.displayPath(target)}", isError = true)
        return ToolOutput("已写入 ${workspace.displayPath(target)}（${content.length} 字符）。")
    }

    /** 删除工作区内的文件或目录（目录会递归删除）。仅限工作区，无法删除工作区根目录。 */
    @Tool(requireConfirmation = true)
    suspend fun delete_file(
        @Param(description = "要删除的文件或目录路径（相对路径以工作区为基准）。") path: String,
    ): ToolOutput {
        val target = runCatching { workspace.resolveForDelete(path.trim()) }
            .getOrElse { return failure(it) }
        val display = workspace.displayPath(target)
        val deleted = runCatching {
            // agent 内文件授权目录下走 SAF；可读根之外的目录（如 P 站下载）仍用文件系统删除。
            if (workspace.isInsideWorkspace(target)) workspace.deleteInWorkspace(target) else target.deleteRecursively()
        }.getOrElse { return ToolOutput("删除失败：${it.message ?: it.javaClass.simpleName}", isError = true) }
        return if (deleted) {
            ToolOutput("已删除 $display。")
        } else {
            ToolOutput("删除失败：$display", isError = true)
        }
    }

    /** 按关键词搜索文件：同时匹配文件名与小型文本文件的内容（不区分大小写）。 */
    @Tool
    suspend fun search_files(
        @Param(description = "搜索关键词。") query: String,
        @Param(description = "搜索起始目录，省略时从工作区根目录开始。") path: String?,
    ): ToolOutput {
        val needle = query.trim()
        if (needle.isEmpty()) return ToolOutput("缺少参数 query", isError = true)
        val startPath = path?.trim()?.takeIf { it.isNotEmpty() } ?: "."
        val start = runCatching { workspace.resolveForRead(startPath) }
            .getOrElse { return failure(it) }
        val root = if (start.isDirectory) start else start.parentFile ?: workspace.writeRoot

        val matches = ArrayList<String>()
        collectMatches(root, 0, needle.lowercase(), needle, matches)
        if (matches.isEmpty()) {
            return ToolOutput("未找到与「$needle」匹配的文件。")
        }
        val builder = StringBuilder()
        builder.appendLine("找到 ${matches.size} 个匹配「$needle」的文件：")
        matches.forEach { builder.appendLine("- $it") }
        return ToolOutput(builder.toString().trim())
    }

    /** 图片：不解码正文，改为以 IMAGE 产物返回，由引擎作为多模态图片交给模型查看。 */
    private suspend fun readImage(file: File, display: String): ToolOutput = withContext(Dispatchers.IO) {
        // 授权目录下的文件没有可解码的绝对路径，先物化到缓存再读尺寸；产物则直接回传授权目录内的原件。
        val readable = workspace.materialize(file) ?: return@withContext ToolOutput("读取失败：无法打开 $display。", isError = true)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(readable.absolutePath, bounds)
        val size = if (bounds.outWidth > 0) "${bounds.outWidth}×${bounds.outHeight} 像素" else "尺寸未知"
        ToolOutput(
            content = "文件 $display 是一张图片（$size，${formatSize(readable.length())}）。" +
                "图片内容已随本次工具结果提供，请直接查看并理解它。",
            artifacts = listOf(imageArtifact(file, readable)),
        )
    }

    /** PDF：逐页渲染为 PNG 产物，交由多模态理解（纯文本抽取对 PDF 不可靠）。 */
    private suspend fun readPdf(file: File, display: String): ToolOutput = withContext(Dispatchers.IO) {
        // PDF 渲染依赖真实的文件描述符，授权目录下先物化到缓存。
        val readable = workspace.materialize(file)
            ?: return@withContext ToolOutput("读取失败：无法打开 $display。", isError = true)
        val rendered = runCatching {
            DocumentReader.renderPdf(readable, File(readable.parentFile, readable.nameWithoutExtension + "_pages"), MAX_PDF_PAGES)
        }
            .getOrElse { return@withContext ToolOutput("渲染 PDF 失败：${it.message ?: it.javaClass.simpleName}", isError = true) }
        if (rendered.isEmpty()) {
            return@withContext ToolOutput("PDF $display 没有可渲染的页面。", isError = true)
        }
        // 渲染产物先落在缓存目录，再按当前输出配置写到 agent 目录（授权目录时走 SAF）。
        val pages = rendered.map { page ->
            val stored = workspace.uniqueTarget(page.name)
            workspace.copyInto(page, stored)
            stored
        }
        ToolOutput(
            content = "文件 $display 已渲染为 ${pages.size} 页图片（保存在 ${workspace.displayPath(pages.first())} 同级目录），" +
                "内容已随本次工具结果提供，请直接查看并理解它们。",
            artifacts = pages.map { imageArtifact(it) },
        )
    }

    /** Office 文档：从 zip 容器内抽取文字；抽取失败则退化为二进制元信息。 */
    private suspend fun readOoxml(file: File, display: String): ToolOutput = withContext(Dispatchers.IO) {
        val text = runCatching {
            workspace.materialize(file)?.let { DocumentReader.ooxmlText(it) }
        }.getOrNull()
        if (text.isNullOrBlank()) return@withContext readBinary(file, display)
        textResult(display, text)
    }

    /** 文本：直接返回正文，超长截断。 */
    private suspend fun readText(file: File, display: String): ToolOutput = withContext(Dispatchers.IO) {
        val text = runCatching {
            workspace.readAgent(file)?.use { it.readBytes().decodeToString() } ?: file.readText()
        }
            .getOrElse { return@withContext ToolOutput("读取失败：${it.message ?: it.javaClass.simpleName}", isError = true) }
        textResult(display, text)
    }

    /** 二进制：无法作为文本阅读时，给出体积、MIME 与文件头魔数，指引后续操作。 */
    private suspend fun readBinary(file: File, display: String): ToolOutput = withContext(Dispatchers.IO) {
        val head = ByteArray(BINARY_HEAD_BYTES)
        var read = 0
        runCatching {
            val input = workspace.readAgent(file) ?: file.inputStream()
            input.use { stream -> read = stream.read(head) }
        }
        val hex = head.take(read.coerceAtLeast(0)).joinToString(" ") { "%02X".format(it) }
        ToolOutput(
            "文件 $display 是二进制文件（${formatSize(file.length())}，MIME 约 ${mimeTypeOf(file)}），无法作为文本直接阅读。\n" +
                "文件头（前 $read 字节）：$hex\n" +
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
        val ext = file.extension.lowercase()
        return ext in TEXT_EXTENSIONS
    }

    private fun contentContains(file: File, query: String): Boolean =
        runCatching {
            (workspace.readAgent(file)?.use { it.readBytes().decodeToString() } ?: file.readText())
                .contains(query, ignoreCase = true)
        }.getOrDefault(false)

    private fun imageArtifact(file: File, readable: File = file): ToolArtifact = ToolArtifact(
        id = UUID.randomUUID().toString(),
        kind = ToolArtifactKind.IMAGE,
        // 授权输出目录时 file 只是 agent 内的名义路径，多模态编码需要可读的绝对路径，改用物化后的缓存文件。
        path = readable.absolutePath,
        label = file.name,
    )

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

/** 把 [Workspace] 抛出的错误转为工具结果。 */
private fun failure(error: Throwable): ToolOutput =
    ToolOutput(error.message ?: (error.javaClass.simpleName), isError = true)

/** 人类可读的文件体积。 */
private fun formatSize(bytes: Long): String = when {
    bytes < UNIT_KB -> "$bytes B"
    bytes < UNIT_MB -> String.format(java.util.Locale.US, "%.1f KB", bytes.toDouble() / UNIT_KB)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / UNIT_MB)
}

private const val UNIT_KB = 1024L
private const val UNIT_MB = 1024L * 1024L
