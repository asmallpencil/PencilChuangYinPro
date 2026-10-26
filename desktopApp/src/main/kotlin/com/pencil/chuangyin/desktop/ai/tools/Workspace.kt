package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.support.DesktopPaths
import com.pencil.chuangyin.desktop.support.OutputPathStore
import java.io.File
import java.io.InputStream

/** 工作区内的路径越界/非法访问。 */
class WorkspaceViolationException(message: String) : Exception(message)

/**
 * AI 工具的存储沙箱（桌面版，对齐安卓端 `Workspace`）。
 *
 * - **写入范围**：[writeRoot]，即「产物输出路径」下的 `agent` 目录：
 *   用户上传的图片/文件、AI 生成的图片、AI 新建的文件都落在这里，AI 可对其增、删、改。
 * - **读取范围**：[readRoots]：agent 目录、产物输出根目录、软件数据目录、用户主目录，
 *   使 AI 能读取用户磁盘上的既有资料（P 站下载、打印产物、桌面上的 PDF 等）。
 *
 * agent 根目录按「产物输出路径/agent → %APPDATA%/PencilChuangYinPro/agent」两级探测，
 * 探测方式为写入探针文件，保证该目录真的可写。
 *
 * 所有路径都经过 canonical 前缀校验，杜绝 `../` 形式的目录穿越。
 */
class Workspace(
    private val outputDirProvider: () -> File = { OutputPathStore.activeDir() },
) {

    @Volatile private var cachedRoot: File? = null
    @Volatile private var cachedKey: String? = null

    /** 工作区目录：AI 新建/上传文件的默认位置，也是唯一可写目录。 */
    val writeRoot: File get() = resolveAgentRoot()

    /** 可读根目录集合（agent 根包含在其中）。 */
    private val readRoots: List<File>
        get() = listOfNotNull(
            resolveAgentRoot(),
            runCatching { outputDirProvider() }.getOrNull(),
            DesktopPaths.appDataRoot(),
            DesktopPaths.userHome(),
        ).distinctBy { runCatching { it.absolutePath }.getOrDefault(it.name) }

    /** 供系统提示词展示的工作区绝对路径。 */
    fun writeRootPath(): String = writeRoot.absolutePath

    /** 供系统提示词展示的读取根绝对路径（多个时以「、」连接）。 */
    fun readRootsPath(): String = readRoots.joinToString(separator = "、") { it.absolutePath }

    /** 解析一个用于**读取**的路径。相对路径以 [writeRoot] 为基准；绝对路径必须落在可读根之内。 */
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
     * 解析一个用于**写入**的路径。
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

    /**
     * 把 [source] 流写入 agent 目录下的 [fileName]。
     *
     * 用于「用户上传图片/文件」：文件名自动去路径分隔符，重名时追加 ` (n)` 序号，返回落盘文件。
     */
    fun importToWorkspace(source: InputStream, fileName: String): File {
        val target = uniqueTarget(fileName)
        source.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    /** 把本地文件复制进 agent 目录（重名自动改名）。 */
    fun importFile(source: File, fileName: String = source.name): File = importToWorkspace(source.inputStream(), fileName)

    /** 重名时在 agent 目录内派生唯一文件名。 */
    fun uniqueTarget(fileName: String): File {
        val safe = sanitizeFileName(fileName)
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

    /**
     * 逐级探测 agent 根目录：产物输出路径 → 软件数据目录。
     *
     * 探测方式为在候选目录写入一个探针文件再删除，确保该目录真的可写。
     */
    private fun resolveAgentRoot(): File {
        val key = runCatching { outputDirProvider().absolutePath }.getOrDefault("")
        cachedRoot?.let { root -> if (cachedKey == key && root.exists() && root.isDirectory) return root }

        val candidates = buildList {
            runCatching { add(File(outputDirProvider(), AGENT_DIR_NAME)) }
            add(File(DesktopPaths.appDataRoot(), AGENT_DIR_NAME))
        }
        val resolved = candidates.firstOrNull { isWritable(it) }
            ?: File(DesktopPaths.appDataRoot(), AGENT_DIR_NAME).apply { mkdirs() }
        cachedRoot = resolved
        cachedKey = key
        return resolved
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
        /** agent 子目录名（位于产物输出路径之下）。 */
        const val AGENT_DIR_NAME = "agent"

        private const val MAX_FILE_NAME_LENGTH = 120

        private const val ERROR_EMPTY_PATH = "路径不能为空"
        private const val ERROR_NOT_FOUND = "文件或目录不存在"
        private const val ERROR_READ_OUTSIDE = "只能读取软件存储目录或用户目录内的内容"
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
