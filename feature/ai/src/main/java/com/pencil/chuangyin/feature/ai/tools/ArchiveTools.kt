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
 * 压缩包工具：解压 zip / gzip，以及把工作区内容打包为 zip。
 *
 * 解压带「zip slip」防护（条目路径一律 canonical 校验）与条目数/总体积上限，
 * 避免恶意压缩包写出工作区或撑爆磁盘。
 */
@Singleton
class ArchiveTools @Inject constructor(
    private val workspace: Workspace,
    private val scope: ToolCallScope,
) {

    /**
     * 解压压缩包到 agent 工作目录。按文件内容自动识别格式，支持 zip（含 .docx/.xlsx/.pptx/.jar/.apk 等 zip 容器）
     * 与 gzip（.gz/.tgz）。destination 省略时按压缩包名派生同名目录。
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
            // 按文件头魔数判断真实格式：扩展名常被改错（如 .docx/.jar 本质是 zip），不可信。
            val format = detectFormat(archive)
            val target = workspace.resolveForWrite(rawDestination)
            if (!target.exists()) target.mkdirs()
            val summary = when (format) {
                ArchiveFormat.ZIP -> extractZip(archive, target)
                ArchiveFormat.GZIP -> extractGzip(archive, target)
                ArchiveFormat.UNKNOWN -> return@runCatching fileFailure(
                    "无法识别的压缩格式：${archive.name}。当前支持 zip（含 docx/xlsx/pptx/jar/apk 等）与 gzip。",
                )
            }
            ToolOutput("$summary 已解压到 ${workspace.displayPath(target)}。")
        }.getOrElse { fileFailure(it.message ?: "解压失败") }
    }

    /** 读取文件头判定压缩格式：`PK` 开头为 zip，`1F 8B` 为 gzip。 */
    private suspend fun detectFormat(archive: File): ArchiveFormat = withContext(Dispatchers.IO) {
        val head = ByteArray(4)
        val read = runCatching { archive.inputStream().use { it.read(head) } }.getOrDefault(0)
        if (read >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte()) return@withContext ArchiveFormat.GZIP
        if (read >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) return@withContext ArchiveFormat.ZIP
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

    private suspend fun extractGzip(archive: File, destination: File): String = withContext(Dispatchers.IO) {
        val name = archive.name
        val outputName = when {
            name.endsWith(".tgz", ignoreCase = true) -> name.dropLast(4) + ".tar"
            name.endsWith(".gz", ignoreCase = true) -> name.dropLast(3)
            else -> "$name.out"
        }.ifEmpty { archive.name }
        val target = File(destination, outputName)
        GZIPInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
            BufferedOutputStream(FileOutputStream(target)).use { output -> input.copyTo(output) }
        }
        "已解压为 ${workspace.displayPath(target)}（${formatFileSize(target.length())}）。"
    }

    /** 把 agent 工作目录内的文件或目录打包成 zip 压缩包。sources 省略时打包整个工作区。 */
    @Tool
    suspend fun create_archive(
        @Param(description = "生成的 zip 路径（agent 内相对路径，建议以 .zip 结尾）。") destination: String,
        @Param(description = "要打包的路径数组，如 [\"a.txt\",\"photos\"]；省略则打包整个工作区。") sources: List<String>?,
    ): ToolOutput {
        val destinationArg = destination.trim()
        val target = workspace.resolveForWrite(
            if (destinationArg.endsWith(".zip", ignoreCase = true)) destinationArg else "$destinationArg.zip",
        )
        val resolved = sources.orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(".") }
            .map { workspace.resolveForRead(it) }

        scope.current?.reportProgress("正在打包")
        return runCatching {
            if (target.exists()) target.delete()
            var files = 0
            withContext(Dispatchers.IO) {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { output ->
                    resolved.forEach { source ->
                        files += zip(output, source, source.name)
                    }
                }
            }
            ToolOutput(
                content = "已打包 $files 个文件到 ${workspace.displayPath(target)}（${formatFileSize(target.length())}）。",
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
        file.inputStream().use { input -> input.copyTo(output) }
        output.closeEntry()
        return 1
    }
}

/** 压缩格式（按文件头魔数识别）。 */
private enum class ArchiveFormat { ZIP, GZIP, UNKNOWN }
