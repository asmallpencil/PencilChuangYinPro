package com.pencil.chuangyin.core.ui.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 用户通过 SAF 授权的产物输出目录（`ACTION_OPEN_DOCUMENT_TREE` 返回的树 URI）。
 *
 * 为什么必须有它：targetSdk=35 且未申请存储权限时，[File] 只能写应用私有目录，
 * 而 `Android/data/<包名>/files` 在 Android 11+ 连系统文件管理器都无法访问；
 * 只有 SAF 授权目录能真正「写到用户能看到、任何文件类型都行」的位置。
 *
 * 所有子路径都按段逐级创建，拒绝 `..` 与绝对路径，避免逃逸出授权目录。
 */
object SafOutputDir {

    /** 授权目录的展示名；未授权或已失效时返回 ""。 */
    fun describe(context: Context, treeUri: Uri): String = runCatching {
        val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching ""
        val name = doc.name?.takeIf { it.isNotBlank() } ?: return@runCatching ""
        // 系统根目录（如「下载」）的父级不可读，此时只展示自身名字。
        val parent = runCatching { DocumentsContract.getTreeDocumentId(treeUri).substringBeforeLast(':') }
            .getOrDefault("")
        if (parent.isBlank()) name else "$parent/$name"
    }.getOrDefault("")

    /** 授权是否仍然可用（用户可能已在系统设置里撤销，或目录被删除）。 */
    fun isAvailable(context: Context, treeUri: Uri): Boolean = runCatching {
        DocumentFile.fromTreeUri(context, treeUri)?.let { it.exists() && it.isDirectory && it.canWrite() } ?: false
    }.getOrDefault(false)

    /**
     * 在授权目录下按 [relativePath] 逐级创建文件，返回可写的输出流；失败返回 null。
     *
     * [relativePath] 形如 `agent/draw_123.png` 或 `pixiv/pixiv_1_p0.jpg`。
     */
    fun openOutputStream(context: Context, treeUri: Uri, relativePath: String): OutputStream? = runCatching {
        val segments = segmentsOf(relativePath) ?: return@runCatching null
        if (segments.isEmpty()) return@runCatching null
        val root = DocumentFile.fromTreeUri(context, treeUri)?.takeIf { it.isDirectory } ?: return@runCatching null
        val fileName = segments.last()
        val dir = ensureDir(root, segments.dropLast(1)) ?: return@runCatching null
        val target = findChild(dir, fileName) ?: dir.createFile(mimeOf(fileName), fileName)
            ?: return@runCatching null
        context.contentResolver.openOutputStream(target.uri, "wt")
    }.getOrNull()

    /** 在授权目录下按 [relativePath] 逐级创建目录，返回是否成功。 */
    fun ensureDirectory(context: Context, treeUri: Uri, relativePath: String): Boolean = runCatching {
        val segments = segmentsOf(relativePath) ?: return@runCatching false
        val root = DocumentFile.fromTreeUri(context, treeUri)?.takeIf { it.isDirectory } ?: return@runCatching false
        ensureDir(root, segments) != null
    }.getOrDefault(false)

    /** 列出授权目录下 [relativePath] 子目录里的文件名；目录不存在时返回空集。 */
    fun listNames(context: Context, treeUri: Uri, relativePath: String): Set<String> = runCatching {
        val segments = segmentsOf(relativePath) ?: return@runCatching emptySet()
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching emptySet()
        val dir = ensureDir(root, segments) ?: return@runCatching emptySet()
        dir.listFiles().mapNotNull { it.name }.toSet()
    }.getOrDefault(emptySet())

    /** 把本地文件复制到授权目录下的 [relativePath]；成功返回 true。 */
    fun copyFile(context: Context, treeUri: Uri, relativePath: String, source: File): Boolean = runCatching {
        if (!source.isFile) return@runCatching false
        val out = openOutputStream(context, treeUri, relativePath) ?: return@runCatching false
        source.inputStream().use { input -> out.use { input.copyTo(it) } }
        true
    }.getOrDefault(false)

    /** 把输入流写入授权目录下的 [relativePath]；成功返回 true。 */
    fun writeStream(context: Context, treeUri: Uri, relativePath: String, source: InputStream): Boolean = runCatching {
        val out = openOutputStream(context, treeUri, relativePath) ?: return@runCatching false
        source.use { input -> out.use { input.copyTo(it) } }
        true
    }.getOrDefault(false)

    /** 打开授权目录下 [relativePath] 的读取流；文件不存在时返回 null。 */
    fun openInputStream(context: Context, treeUri: Uri, relativePath: String): InputStream? = runCatching {
        val segments = segmentsOf(relativePath) ?: return@runCatching null
        if (segments.isEmpty()) return@runCatching null
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching null
        val fileName = segments.last()
        var dir: DocumentFile = root
        segments.dropLast(1).forEach { segment ->
            dir = findChild(dir, segment)?.takeIf { it.isDirectory } ?: return@runCatching null
        }
        val target = findChild(dir, fileName)?.takeIf { it.isFile } ?: return@runCatching null
        context.contentResolver.openInputStream(target.uri)
    }.getOrNull()

    /** 删除授权目录下 [relativePath] 的文件；成功返回 true。 */
    fun delete(context: Context, treeUri: Uri, relativePath: String): Boolean = runCatching {
        val segments = segmentsOf(relativePath) ?: return@runCatching false
        if (segments.isEmpty()) return@runCatching false
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching false
        var dir: DocumentFile = root
        segments.dropLast(1).forEach { segment ->
            dir = findChild(dir, segment)?.takeIf { it.isDirectory } ?: return@runCatching false
        }
        findChild(dir, segments.last())?.delete() ?: false
    }.getOrDefault(false)

    private fun ensureDir(root: DocumentFile, segments: List<String>): DocumentFile? {
        var current = root
        segments.forEach { segment ->
            val next = findChild(current, segment)
                ?: current.createDirectory(segment)
                ?: return null
            current = next
        }
        return current
    }

    private fun findChild(dir: DocumentFile, name: String): DocumentFile? =
        dir.listFiles().firstOrNull { it.name == name }

    /** 拆路径段并拒绝越界；非法路径返回 null。 */
    private fun segmentsOf(relativePath: String): List<String>? {
        val normalized = relativePath.replace('\\', '/').trim('/')
        if (normalized.isBlank()) return emptyList()
        val segments = normalized.split('/').filter { it.isNotBlank() }
        if (segments.any { it == "." || it == ".." || it.contains(':') }) return null
        return segments
    }

    private fun mimeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "txt" -> "text/plain"
        "json" -> "application/json"
        "csv" -> "text/csv"
        "md" -> "text/markdown"
        "html", "htm" -> "text/html"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> "application/octet-stream"
    }
}
