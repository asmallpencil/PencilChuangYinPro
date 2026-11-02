package com.pencil.chuangyin.feature.ai.tools

import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.agent.runtime.ToolCallScope
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 压缩包工具：解压 zip / gzip / tar，以及把工作区内容打包为 zip。
 *
 * 解压带「zip slip」防护（条目路径一律 canonical 校验）与条目数/总体积上限，
 * 避免恶意压缩包写出工作区或撑爆磁盘。
 *
 * `.tar.gz` 会一次解到最终文件（不再需要手动解第二层），嵌套目录结构完整保留。
 */
@Singleton
class ArchiveTools @Inject constructor(
    private val workspace: Workspace,
    private val scope: ToolCallScope,
) {

    /**
     * 解压压缩包到 agent 工作目录。按文件内容自动识别格式，支持 zip（含 .docx/.xlsx/.pptx/.jar/.apk 等 zip 容器）、
     * gzip（.gz/.tgz，内部为 tar 时继续解 tar）与 tar。destination 省略时按压缩包名派生同名目录。
     */
    @Tool
    suspend fun extract_archive(
        @Param(description = "压缩包路径；相对路径以 agent 工作目录为基准。") path: String,
        @Param(description = "可选解压目标目录（agent 内相对路径）。") destination: String?,
    ): ToolOutput {
        val archive = runCatching { workspace.resolveForRead(path.trim()) }
            .getOrElse { return fileFailure(it.message ?: "压缩包路径无效") }
        if (archive.isDirectory) return fileFailure("${workspace.displayPath(archive)} 是目录，不是压缩包。")

        val rawDestination = destination?.trim()?.takeIf { it.isNotEmpty() }
            ?: archive.name.substringBeforeLast('.').ifEmpty { "extracted" }

        scope.current?.reportProgress("正在解压 ${archive.name}")
        return runCatching {
            // 压缩包必须是真实文件（授权目录下的文档没有可读路径），先物化到缓存。
            val readable = workspace.materialize(archive)
                ?: return@runCatching fileFailure("无法读取压缩包：${workspace.displayPath(archive)}")
            // 按文件头魔数判断真实格式：扩展名常被改错（如 .docx/.jar 本质是 zip），不可信。
            val format = detectFormat(readable)
            // 解压产物先落到缓存目录，再按输出配置写入 agent 目录（授权目录时走 SAF）。
            val staging = workspace.stagingFile("extract_${System.nanoTime()}").apply { mkdirs() }
            try {
                val summary = when (format) {
                    ArchiveFormat.ZIP -> extractZip(readable, staging)
                    ArchiveFormat.GZIP -> extractGzip(readable, staging)
                    ArchiveFormat.TAR -> extractTar(readable, File(staging, "tar_contents"))
                    ArchiveFormat.UNKNOWN -> return@runCatching fileFailure(
                        "无法识别的压缩格式：${archive.name}。当前支持 zip（含 docx/xlsx/pptx/jar/apk 等）、gzip 与 tar。",
                    )
                }
                val target = workspace.resolveForWrite(rawDestination)
                copyTreeIntoWorkspace(staging, staging, target)
                ToolOutput("$summary 已解压到 ${workspace.displayPath(target)}。")
            } finally {
                runCatching { staging.deleteRecursively() }
            }
        }.getOrElse { fileFailure(it.message ?: "解压失败") }
    }

    /** 把缓存目录里的解压结果逐项写入 agent 目录（保留相对嵌套结构）；授权目录下走 SAF。 */
    private fun copyTreeIntoWorkspace(root: File, current: File, target: File) {
        if (current.isDirectory) {
            current.listFiles().orEmpty().forEach { child -> copyTreeIntoWorkspace(root, child, target) }
            return
        }
        val relative = runCatching { current.relativeTo(root).path }.getOrNull()
            ?.takeIf { it.isNotEmpty() && it != "." } ?: current.name
        val bytes = runCatching { current.readBytes() }.getOrNull() ?: return
        workspace.writeBytes(File(target, relative), bytes)
    }

    /** 读取文件头判定压缩格式：`PK` 开头为 zip，`1F 8B` 为 gzip，`ustar` 标记为 tar。 */
    private suspend fun detectFormat(archive: File): ArchiveFormat = withContext(Dispatchers.IO) {
        val head = ByteArray(512)
        val read = runCatching { archive.inputStream().use { it.read(head) } }.getOrDefault(0)
        if (read >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte()) return@withContext ArchiveFormat.GZIP
        if (read >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) return@withContext ArchiveFormat.ZIP
        // ustar 标记位于 257 偏移；老式 tar 没有该标记，但文件名后缀可作兜底。
        val ustar = read >= 263 && String(head, 257, 5, Charsets.US_ASCII) == "ustar"
        if (ustar || archive.name.endsWith(".tar", ignoreCase = true)) return@withContext ArchiveFormat.TAR
        ArchiveFormat.UNKNOWN
    }

    private suspend fun extractZip(archive: File, destination: File): String = withContext(Dispatchers.IO) {
        val root = destination.canonicalFile
        var entries = 0
        var total = 0L
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entries++
                if (entries > WorkspaceLimits.MAX_ARCHIVE_ENTRIES) {
                    throw WorkspaceViolationException("压缩包条目过多（超过 ${WorkspaceLimits.MAX_ARCHIVE_ENTRIES}），已中止。")
                }
                val target = File(root, entry.name)
                val canonical = target.canonicalFile
                if (canonical.path != root.path && !canonical.path.startsWith(root.path + File.separator)) {
                    throw WorkspaceViolationException("压缩包包含越界路径，已中止：${entry.name}")
                }
                if (entry.isDirectory) {
                    canonical.mkdirs()
                } else {
                    canonical.parentFile?.mkdirs()
                    canonical.outputStream().use { output -> input.copyTo(output) }
                    total += canonical.length()
                    if (total > WorkspaceLimits.MAX_ARCHIVE_UNPACKED_BYTES) {
                        throw WorkspaceViolationException("解压体积过大，已中止。")
                    }
                }
                input.closeEntry()
            }
        }
        "已解压 $entries 个条目（${formatFileSize(total)}）。"
    }

    /**
     * gzip 解压：`xxx.tgz` / `xxx.tar.gz` 解出的 tar 会继续解开，
     * 让模型一次调用就拿到最终文件（此前只解到 `.tar` 这一层，还需再解一次）。
     */
    private suspend fun extractGzip(archive: File, destination: File): String = withContext(Dispatchers.IO) {
        val name = archive.name
        val outputName = when {
            name.endsWith(".tgz", ignoreCase = true) -> name.dropLast(4) + ".tar"
            name.endsWith(".gz", ignoreCase = true) -> name.dropLast(3)
            else -> "$name.out"
        }.ifEmpty { archive.name }
        val tar = File(destination, outputName)
        GZIPInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
            BufferedOutputStream(FileOutputStream(tar)).use { output -> input.copyTo(output) }
        }
        val isTar = looksLikeTar(tar) || outputName.endsWith(".tar", ignoreCase = true)
        if (!isTar) {
            return@withContext "已解压为 ${workspace.displayPath(tar)}（${formatFileSize(tar.length())}）。"
        }
        val inner = File(destination, outputName.removeSuffix(".tar").ifEmpty { "archive" })
        val summary = extractTar(tar, inner)
        tar.delete()
        summary
    }

    private fun looksLikeTar(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val head = ByteArray(263)
            val read = input.read(head)
            read >= 263 && String(head, 257, 5, Charsets.US_ASCII) == "ustar"
        }
    }.getOrDefault(false)

    /** 解 USTAR/GNU tar：按 512 字节块顺序读取，支持目录、普通文件与长文件名前缀。 */
    private fun extractTar(tar: File, destination: File): String {
        val root = destination.canonicalFile.also { it.mkdirs() }
        var files = 0
        var total = 0L
        BufferedInputStream(FileInputStream(tar), 8192).use { input ->
            val header = ByteArray(TAR_BLOCK)
            while (true) {
                if (!readFully(input, header)) break
                if (header.all { it == 0.toByte() }) break
                val name = String(header, 0, 100, Charsets.UTF_8).trim('\u0000').trim()
                if (name.isEmpty()) break
                val size = String(header, 124, 12, Charsets.US_ASCII).trim().trim('\u0000').toLongOrNull() ?: 0L
                val type = header[156].toInt().toChar()
                val prefix = String(header, 345, 155, Charsets.UTF_8).trim('\u0000').trim()
                val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name

                val canonical = File(root, fullName).canonicalFile
                if (canonical.path != root.path && !canonical.path.startsWith(root.path + File.separator)) {
                    throw WorkspaceViolationException("压缩包包含越界路径，已中止：$fullName")
                }
                when {
                    type == '5' || fullName.endsWith("/") -> canonical.mkdirs()
                    type == '0' || type == '\u0000' || type == '7' -> {
                        canonical.parentFile?.mkdirs()
                        canonical.outputStream().use { output -> copyExactly(input, output, size) }
                        total += size
                        files++
                        if (total > WorkspaceLimits.MAX_ARCHIVE_UNPACKED_BYTES) {
                            throw WorkspaceViolationException("解压体积过大，已中止。")
                        }
                    }
                    // 链接/设备节点等不落盘，仅跳过其内容。
                    else -> skipExactly(input, size)
                }
                skipExactly(input, (TAR_BLOCK - size % TAR_BLOCK) % TAR_BLOCK)
            }
        }
        return "已解压 $files 个文件（${formatFileSize(total)}）。"
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun copyExactly(input: InputStream, output: OutputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skipExactly(input: InputStream, size: Long) {
        var remaining = size
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) return
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    /** 把 agent 工作目录内的文件或目录打包成 zip 压缩包。sources 省略时打包整个工作区。 */
    @Tool
    suspend fun create_archive(
        @Param(description = "生成的 zip 路径（agent 内相对路径，建议以 .zip 结尾）。") destination: String,
        @Param(description = "要打包的路径数组，如 [\"a.txt\",\"photos\"]；省略则打包整个工作区。") sources: List<String>?,
    ): ToolOutput {
        val destinationArg = destination.trim()
        if (destinationArg.isEmpty()) return fileFailure("请提供压缩包路径。")
        // 目录形式（以 / 结尾）自动补默认文件名；没写 .zip 时补后缀，避免被当成目录。
        val normalized = when {
            destinationArg.endsWith("/") || destinationArg.endsWith("\\") -> destinationArg + DEFAULT_ARCHIVE_NAME
            destinationArg.endsWith(".zip", ignoreCase = true) -> destinationArg
            else -> "$destinationArg.zip"
        }
        val target = runCatching { workspace.resolveForWrite(normalized) }
            .getOrElse { return fileFailure(it.message ?: "压缩包路径无效") }
        val resolved = runCatching {
            sources.orEmpty()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf(".") }
                .map { workspace.resolveForRead(it) }
        }.getOrElse { return fileFailure(it.message ?: "要打包的路径无效") }

        scope.current?.reportProgress("正在打包")
        // 中间包先写缓存：授权输出目录时 agent 内的路径只是占位，不能直接建文件。
        val staging = workspace.stagingFile("pack_${System.nanoTime()}.zip")
        return runCatching {
            var files = 0
            withContext(Dispatchers.IO) {
                staging.parentFile?.mkdirs()
                ZipOutputStream(BufferedOutputStream(FileOutputStream(staging))).use { output ->
                    resolved.forEach { source ->
                        files += zip(output, source, entryNameOf(source))
                    }
                }
            }
            val bytes = runCatching { staging.readBytes() }.getOrNull()
            if (bytes == null || bytes.isEmpty() || !workspace.writeBytes(target, bytes)) {
                return@runCatching fileFailure("打包失败：无法写入 ${workspace.displayPath(target)}")
            }
            ToolOutput(
                content = "已打包 $files 个文件到 ${workspace.displayPath(target)}（${formatFileSize(bytes.size.toLong())}）。",
                artifacts = listOf(
                    ToolArtifact(
                        id = UUID.randomUUID().toString(),
                        kind = ToolArtifactKind.FILE,
                        path = target.absolutePath,
                        label = target.name,
                    ),
                ),
            )
        }.getOrElse { fileFailure(it.message ?: "打包失败") }
            .also { staging.delete() }
    }

    /** 打包条目名：工作区根目录打包时用空前缀（条目直接扁平列出），其余以自身名字为前缀。 */
    private fun entryNameOf(source: File): String {
        val root = runCatching { workspace.writeRoot.canonicalFile }.getOrNull() ?: return source.name
        val canonical = runCatching { source.canonicalFile }.getOrElse { source }
        val relative = runCatching { canonical.relativeTo(root).path }.getOrNull()
        return relative?.takeIf { it.isNotEmpty() && it != "." } ?: source.name
    }

    private fun zip(output: ZipOutputStream, file: File, entryName: String): Int {
        if (file.isDirectory) {
            var count = 0
            output.putNextEntry(ZipEntry(entryName.trimEnd('/') + "/"))
            output.closeEntry()
            file.listFiles()?.sortedBy { it.name }?.forEach { child ->
                count += zip(output, child, "$entryName/${child.name}")
            }
            return count
        }
        output.putNextEntry(ZipEntry(entryName))
        val input = workspace.readAgent(file) ?: runCatching { file.inputStream() }.getOrNull()
        if (input == null) {
            output.closeEntry()
            return 0
        }
        input.use { it.copyTo(output) }
        output.closeEntry()
        return 1
    }

    private companion object {
        const val DEFAULT_ARCHIVE_NAME = "archive.zip"
        const val TAR_BLOCK = 512
    }
}

/** 压缩格式（按文件头魔数识别）。 */
private enum class ArchiveFormat { ZIP, GZIP, TAR, UNKNOWN }
