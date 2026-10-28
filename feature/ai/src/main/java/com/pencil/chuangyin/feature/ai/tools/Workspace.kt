package com.pencil.chuangyin.feature.ai.tools

import android.content.Context
import android.net.Uri
import com.pencil.chuangyin.core.ui.data.OutputDomain
import com.pencil.chuangyin.core.ui.data.OutputPathRepository
import com.pencil.chuangyin.core.ui.data.SafOutputDir
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** 工作区内的路径越界/非法访问。 */
class WorkspaceViolationException(message: String) : Exception(message)

/**
 * AI 工具的存储沙箱。
 *
 * - **写入范围**：用户授权输出目录下的 `agent/`（优先，任何文件类型均可写、用户可见），
 *   未授权时回退到应用私有目录。
 * - **读取范围**：[readRoots]，在 agent 目录之外还包含软件私有目录与产物输出根目录，
 *   使 AI 能读取 P 站下载、打印缓存等既有产物，并可用 `copy_file` 搬进 agent 目录。
 *
 * 所有路径都经过 canonical 前缀校验，杜绝 `../` 形式的目录穿越。
 */
@Singleton
class Workspace @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outputPathRepository: OutputPathRepository,
) {

    // 缓存已探测成功的私有 agent 根：以产物输出路径字符串为键，避免每次读写都做一次探测写盘。
    @Volatile private var cachedRoot: File? = null
    @Volatile private var cachedKey: String? = null

    /** 用户授权的产物输出目录树；未授权或已失效时为 null。 */
    private val outputTreeUri: Uri?
        get() = outputPathRepository.outputTreeUri.value
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeIf { SafOutputDir.isAvailable(context, it) }

    /** 是否正把产物写进用户授权的输出目录。 */
    val usesOutputDir: Boolean
        get() = outputTreeUri != null

    /** 私有兜底工作区目录（授权目录不可用时的落点）。 */
    val writeRoot: File
        get() = resolveAgentRoot()

    /** 供系统提示词展示的工作区路径：授权目录优先，其次是私有兜底目录。 */
    fun writeRootPath(): String {
        val tree = outputTreeUri
        if (tree != null) {
            val name = runCatching { SafOutputDir.describe(context, tree) }.getOrDefault("")
            return "/sdcard/$name/$AGENT_DIR_NAME"
        }
        return writeRoot.absolutePath
    }

    /** 供系统提示词展示的读取根绝对路径（多个时以「、」连接）。 */
    fun readRootsPath(): String = readRoots.joinToString(separator = "、") { it.absolutePath }

    /**
     * 把 [source] 流写入 agent 目录下的 [fileName]，返回落盘位置。
     *
     * 授权了输出目录时写入 SAF 目录，返回的 [File] 只是同名占位（其 absolutePath 无意义）；
     * 调用方应只用 [File.name] 展示。
     */
    fun importToWorkspace(source: InputStream, fileName: String): File {
        val safe = sanitizeFileName(fileName)
        val tree = outputTreeUri
        if (tree != null) {
            val unique = uniqueNameInTree(tree, safe)
            SafOutputDir.writeStream(context, tree, "$AGENT_DIR_NAME/$unique", source)
            return File(unique)
        }
        val target = uniqueTarget(safe)
        source.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    /** 重名时在 agent 目录内派生唯一文件名。 */
    fun uniqueTarget(fileName: String): File {
        val safe = sanitizeFileName(fileName)
        val tree = outputTreeUri
        if (tree != null) return File(uniqueNameInTree(tree, safe))
        val dot = safe.lastIndexOf('.')
        val base = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        var candidate = resolveForWrite(safe)
        var index = 1
        while (candidate.exists()) {
            candidate = resolveForWrite("$base ($index)$ext")
            index++
        }
        return candidate
    }

    /**
     * 把 [bytes] 写入 agent 目录下的 [target]。
     *
     * 授权了输出目录时写 SAF，[target] 只用到 [File.name]（相对 agent 目录）；
     * 否则按常规私有路径落盘。
     */
    fun writeBytes(target: File, bytes: ByteArray): Boolean {
        val tree = outputTreeUri
        if (tree != null) {
            return runCatching {
                SafOutputDir.openOutputStream(context, tree, "$AGENT_DIR_NAME/${target.name}")?.use { it.write(bytes) }
            }.getOrNull() != null
        }
        return runCatching {
            target.parentFile?.let { if (!it.exists()) it.mkdirs() }
            target.writeBytes(bytes)
            true
        }.getOrDefault(false)
    }

    /**
     * 把真实存在的文件 [source]（如缓存目录中的中间产物）复制到 agent 内的 [target]。
     *
     * 授权目录下按 [File.name] 写 SAF；私有目录下走常规文件复制。
     */
    fun copyInto(source: File, target: File): Boolean {
        if (outputTreeUri == null) {
            return runCatching {
                target.parentFile?.let { if (!it.exists()) it.mkdirs() }
                source.copyTo(target, overwrite = true)
                true
            }.getOrDefault(false)
        }
        val bytes = runCatching { source.readBytes() }.getOrNull() ?: return false
        return writeBytes(target, bytes)
    }

    /** 删除 agent 内的 [target]（用 [File.name] 表示）；授权目录下走 SAF。 */
    fun deleteInWorkspace(target: File): Boolean {
        val tree = outputTreeUri
        if (tree != null) {
            return runCatching {
                SafOutputDir.delete(context, tree, "$AGENT_DIR_NAME/${target.name}")
            }.getOrDefault(false)
        }
        return runCatching { target.delete() }.getOrDefault(false)
    }

    /** 打开 agent 内 [file] 的读取流（授权目录下走 SAF）；不存在或不可读时返回 null。 */
    fun readAgent(file: File): InputStream? {
        val tree = outputTreeUri
        if (tree != null) {
            return runCatching {
                SafOutputDir.openInputStream(context, tree, "$AGENT_DIR_NAME/${file.name}")
            }.getOrNull()
        }
        return runCatching { file.inputStream() }.getOrNull()
    }

    /**
     * 把 agent 内文件复制到系统临时缓存，返回可交给第三方组件（打印/分享/解码）的普通文件。
     *
     * 授权目录下文档 URI 无法直接当 [File] 用，只能先物化到缓存。
     * 缓存文件名会按实际字节补全扩展名：SAF 里保存的图片可能没有后缀，
     * 而 `BitmapFactory.decodeFile` / `PdfRenderer` 都依赖扩展名判断格式。
     */
    fun materialize(file: File): File? {
        if (outputTreeUri == null) return file.takeIf { it.isFile }
        val dir = File(context.cacheDir, "agent_materialized").apply { mkdirs() }
        val input = readAgent(file) ?: return null
        val staged = File(dir, "stage_${file.name}")
        return runCatching {
            input.use { stream -> staged.outputStream().use { stream.copyTo(it) } }
            val name = sniffedName(file.name, staged)
            val target = File(dir, name)
            if (target != staged) {
                target.delete()
                if (!staged.renameTo(target)) {
                    staged.copyTo(target, overwrite = true)
                    staged.delete()
                }
            }
            target.takeIf { it.isFile }
        }.getOrNull()
    }

    /** 按文件头补全扩展名：原文件名已有可识别后缀时原样返回。 */
    private fun sniffedName(name: String, staged: File): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in KNOWN_EXTENSIONS) return name
        val head = ByteArray(8)
        val read = runCatching { staged.inputStream().use { it.read(head) } }.getOrDefault(0)
        val sniffed = when {
            read >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> "jpg"
            read >= 8 && head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() && head[2] == 'N'.code.toByte() && head[3] == 'G'.code.toByte() -> "png"
            read >= 4 && head[0] == 'R'.code.toByte() && head[1] == 'I'.code.toByte() &&
                head[2] == 'F'.code.toByte() && head[3] == 'F'.code.toByte() -> "webp"
            read >= 4 && head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() &&
                head[2] == 'F'.code.toByte() -> "gif"
            read >= 4 && head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() &&
                head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte() -> "pdf"
            read >= 2 && head[0] == 'B'.code.toByte() && head[1] == 'M'.code.toByte() -> "bmp"
            else -> return name
        }
        return "$name.$sniffed"
    }

    /** 把绝对路径还原为便于模型阅读的展示路径（agent 内相对路径，其余为绝对路径）。 */
    fun displayPath(file: File): String {
        val canonical = canonicalOf(file)
        val root = canonicalOf(writeRoot)
        return if (isInside(canonical, root)) {
            canonical.path.removePrefix(root.path).trimStart(File.separatorChar).ifEmpty { "." }
        } else {
            canonical.path
        }
    }

    /** [file] 是否位于 agent 工作目录内（授权目录下即为「该用 SAF 读写」的文件）。 */
    fun isInsideWorkspace(file: File): Boolean = isInside(canonicalOf(file), canonicalOf(writeRoot))

    /**
     * 解析一个用于**读取**的路径。相对路径以 [writeRoot] 为基准；绝对路径必须落在可读根之内。
     *
     * 仅在未授权输出目录时使用；授权后文件在 SAF 目录里，无法用 [File] 访问。
     */
    fun resolveForRead(path: String): File {
        val file = toFile(path)
        val canonical = canonicalOf(file)
        if (!isInsideAny(canonical, readRoots.map { canonicalOf(it) })) {
            throw WorkspaceViolationException(ERROR_READ_OUTSIDE)
        }
        if (!canonical.exists()) throw WorkspaceViolationException(ERROR_NOT_FOUND)
        return canonical
    }

    /**
     * 解析一个用于**写入**的路径（私有兜底目录）。
     *
     * 相对路径以 [writeRoot] 为基准；绝对路径必须落在 [writeRoot] 之内。
     * 不要求目标已存在，但父目录会被自动创建。
     */
    fun resolveForWrite(path: String): File {
        val file = toFile(path)
        val canonical = canonicalOf(file)
        if (!isInside(canonical, canonicalOf(writeRoot))) {
            throw WorkspaceViolationException(ERROR_WRITE_OUTSIDE)
        }
        canonical.parentFile?.let { if (!it.exists()) it.mkdirs() }
        return canonical
    }

    /** 解析一个用于**删除**的路径（仅限 agent 目录，且不允许删除 agent 根自身）。 */
    fun resolveForDelete(path: String): File {
        val canonical = canonicalOf(toFile(path))
        if (canonical == canonicalOf(writeRoot)) throw WorkspaceViolationException(ERROR_DELETE_ROOT)
        if (!isInside(canonical, canonicalOf(writeRoot))) {
            throw WorkspaceViolationException(ERROR_DELETE_OUTSIDE)
        }
        if (!canonical.exists()) throw WorkspaceViolationException(ERROR_NOT_FOUND)
        return canonical
    }

    /** 在 agent 目录内创建目录（含多级父目录）。 */
    fun makeDirectories(path: String): File {
        val dir = resolveForWrite(path)
        if (dir.exists() && !dir.isDirectory) throw WorkspaceViolationException(ERROR_NOT_DIRECTORY)
        if (!dir.exists() && !dir.mkdirs()) throw WorkspaceViolationException(ERROR_MKDIR_FAILED)
        return dir
    }

    /** 可读根目录集合（含私有兜底 agent 根与各产物域目录）。 */
    private val readRoots: List<File>
        get() = listOfNotNull(
            resolveAgentRoot(),
            context.filesDir,
            context.cacheDir,
            context.getExternalFilesDir(null),
            runCatching { outputPathRepository.legacyPublicDir() }.getOrNull(),
            // 未授权 SAF 时 P 站下载 / 创作导出各自落在 Pictures 下的域子目录，AI 也应能读取。
            runCatching { outputPathRepository.legacyPublicDir(OutputDomain.PIXIV) }.getOrNull(),
            runCatching { outputPathRepository.legacyPublicDir(OutputDomain.CREATIVE) }.getOrNull(),
        ).distinctBy { runCatching { it.absolutePath }.getOrDefault(it.name) }

    /**
     * 逐级探测私有 agent 根目录：应用外部私有目录 → 应用内部目录。
     *
     * 注意：这里**不能**再把 `getExternalFilesDir` 当作「正常输出位置」——
     * `Android/data/<包名>/files` 在 Android 11+ 连系统文件管理器都打不开。
     * 它只是「用户还没在设置里授权输出目录」时的兜底，UI 会提示去授权。
     */
    private fun resolveAgentRoot(): File {
        val key = cachedKey
        cachedRoot?.let { root -> if (key != null && root.exists() && root.isDirectory) return root }

        val candidates = buildList {
            context.getExternalFilesDir(null)?.let { add(File(it, AGENT_DIR_NAME)) }
            add(File(context.filesDir, AGENT_DIR_NAME))
        }
        val resolved = candidates.firstOrNull { isWritable(it) } ?: File(context.filesDir, AGENT_DIR_NAME).apply { mkdirs() }
        cachedRoot = resolved
        cachedKey = "private"
        return resolved
    }

    /** 授权目录下 agent 内的唯一文件名（同名时追加 ` (n)`）。 */
    private fun uniqueNameInTree(tree: Uri, fileName: String): String {
        SafOutputDir.ensureDirectory(context, tree, AGENT_DIR_NAME)
        val existing = SafOutputDir.listNames(context, tree, AGENT_DIR_NAME)
        if (fileName !in existing) return fileName
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = "$base ($index)$ext"
            if (candidate !in existing) return candidate
            index++
        }
    }

    /** 目录是否存在且可写（创建后写探针文件验证）。 */
    private fun isWritable(dir: File): Boolean = runCatching {
        if (!dir.exists() && !dir.mkdirs()) return@runCatching false
        val probe = File(dir, ".probe_${System.nanoTime()}")
        val ok = probe.createNewFile()
        probe.delete()
        ok
    }.getOrDefault(false)

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\').trim()
        return cleaned.ifEmpty { "untitled" }.take(MAX_FILE_NAME_LENGTH)
    }

    private fun toFile(path: String): File {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) throw WorkspaceViolationException(ERROR_EMPTY_PATH)
        return File(trimmed).let { if (it.isAbsolute) it else File(writeRoot, trimmed) }
    }

    private fun canonicalOf(file: File): File =
        runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }

    private fun isInside(file: File, root: File): Boolean =
        file.path == root.path || file.path.startsWith(root.path + File.separator)

    private fun isInsideAny(file: File, roots: List<File>): Boolean = roots.any { isInside(file, it) }

    companion object {
        /** agent 子目录名（位于统一产物输出路径之下，与 P 站 / 创作导出同级）。 */
        val AGENT_DIR_NAME: String = OutputDomain.AGENT.folderName

        private const val MAX_FILE_NAME_LENGTH = 120

        /** materialize 认定为「已带可用后缀」的扩展名，避免重复补后缀。 */
        private val KNOWN_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "pdf", "txt", "md", "csv", "json",
            "zip", "docx", "xlsx", "pptx",
        )

        private const val ERROR_EMPTY_PATH = "路径不能为空"
        private const val ERROR_NOT_FOUND = "文件或目录不存在"
        private const val ERROR_READ_OUTSIDE = "只能读取软件存储目录内的内容"
        private const val ERROR_WRITE_OUTSIDE = "只能在 agent 目录内新建或修改文件（新文件请使用相对路径）"
        private const val ERROR_DELETE_ROOT = "不能删除 agent 根目录"
        private const val ERROR_DELETE_OUTSIDE = "只能删除 agent 目录内的文件"
        private const val ERROR_NOT_DIRECTORY = "同名文件已存在，无法创建目录"
        private const val ERROR_MKDIR_FAILED = "目录创建失败"
    }
}

/** 工具输入/输出的统一限额，集中维护避免散落魔法数字。 */
object WorkspaceLimits {

    /** 单次读取返回的最大字符数，超出部分截断并提示。 */
    const val MAX_READ_CHARS = 64_000

    /** list_files 单目录最多返回条目数。 */
    const val MAX_LIST_ENTRIES = 200

    /** search_files 最多返回命中数。 */
    const val MAX_SEARCH_RESULTS = 80

    /** search_files 递归深度上限，防止深目录树拖慢响应。 */
    const val MAX_SEARCH_DEPTH = 8

    /** 单次写入的最大字符数，防止模型一次性写入超大内容。 */
    const val MAX_WRITE_CHARS = 256_000

    /** 解压/归档允许的最大条目数与解压后总字节，防止 zip bomb 撑爆磁盘。 */
    const val MAX_ARCHIVE_ENTRIES = 2_000
    const val MAX_ARCHIVE_UNPACKED_BYTES = 512L * 1024L * 1024L
}
