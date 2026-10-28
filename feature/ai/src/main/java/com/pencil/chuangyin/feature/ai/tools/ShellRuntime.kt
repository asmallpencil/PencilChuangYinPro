package com.pencil.chuangyin.feature.ai.tools

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 一次 shell 命令的执行结果。 */
data class ShellRunResult(
    val output: String,
    val exitCode: Int,
    val timedOut: Boolean,
    val backend: String,
)

/** 终端环境的可用性快照，供 shell_status 如实上报。 */
data class ShellStatus(
    val systemShellPath: String?,
    val termuxInstalled: Boolean,
    val termuxRootPath: String,
    val termuxExecutable: Boolean,
    /** 系统终端自检是否真的能跑起一条命令（不是「文件存在」就算数）。 */
    val systemShellProbe: Boolean,
    /** 自检输出（成功时是 `ok`，失败时是错误信息），用于让模型/用户看到真实原因。 */
    val systemShellProbeDetail: String,
    /** 工作区 bin 目录：里面提供 bash→sh 兼容 shim，并已加入 PATH。 */
    val workspaceBinPath: String,
    /** 是否存在可用的 bash（Termux 自带 或 兼容 shim）。 */
    val bashAvailable: Boolean,
)

/**
 * AI 终端运行时。
 *
 * 提供两个后端：
 * - **系统终端**：Android 自带的 `/system/bin/sh`（toybox），无需安装、始终可执行，
 *   内置 sh/sed/awk/grep/find/echo 等，足以让模型编写并运行脚本，是可靠主力后端。
 * - **Termux 环境**（增强）：按 ABI 下载官方 `bootstrap-*.zip` 解压到应用私有目录，得到完整
 *   Linux 用户land（bash、coreutils、apt 等）。注意：`targetSdk>=29` 时 Android 默认禁止从
 *   应用可写目录直接 execve，故此处**实测**该用户land能否启动，能则优先使用，不能则诚实上报、
 *   自动回退系统终端，绝不伪造成功。
 */
@Singleton
class ShellRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workspace: Workspace,
) {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** Android 自带的系统 shell（toybox），无需安装、始终可执行。 */
    val systemShell: File? by lazy {
        SYSTEM_SHELL_CANDIDATES.map(::File).firstOrNull { it.exists() && it.canExecute() }
    }

    private val termuxDir: File get() = File(context.filesDir, TERMUX_DIR_NAME)

    /** Termux bootstrap 的 `usr` 根目录（解压后 `usr/...` 落在这里）。 */
    val termuxRoot: File get() = File(termuxDir, "usr")

    private val installedMarker: File get() = File(termuxDir, ".installed")

    /** Termux 可执行性探测结果缓存（探测需起子进程，缓存避免重复）。 */
    @Volatile private var termuxExecCache: Boolean? = null

    /**
     * 兼容 bin 目录：提供 `bash → sh` shim（以及 `python3` 之类的缺命令提示）。
     *
     * 模型生成脚本时最常踩的坑就是写 `bash script.sh`，而 Android 系统终端只有 toybox `sh`，
     * 于是报 `bash: not found`。这里在 PATH 最前面放一个同名 shim，等于把「bash 语法子集」
     * 直接接到 sh 上，无需用户装任何东西。
     */
    private val binDir: File by lazy {
        val dir = File(context.filesDir, BIN_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        ensureBashShim(dir)
        dir
    }

    private fun ensureBashShim(dir: File) {
        val bash = File(dir, "bash")
        if (bash.exists()) return
        val target = systemShell?.absolutePath ?: return
        // 优先真实符号链接；部分文件系统不支持软链时退化为 tiny wrapper 脚本。
        val linked = runCatching {
            android.system.Os.symlink(target, bash.absolutePath)
            true
        }.getOrDefault(false)
        if (!linked) {
            runCatching { bash.writeText("#!/system/bin/sh\nexec $target \"$@\"\n") }
            bash.setExecutable(true, false)
        }
    }

    fun status(): ShellStatus {
        val installed = installedMarker.exists() && termuxRoot.isDirectory
        val probe = probeSystemShell()
        val bash = File(binDir, "bash")
        return ShellStatus(
            systemShellPath = systemShell?.absolutePath,
            termuxInstalled = installed,
            termuxRootPath = termuxRoot.absolutePath,
            termuxExecutable = installed && canExecuteTermuxShell(),
            systemShellProbe = probe.first,
            systemShellProbeDetail = probe.second,
            workspaceBinPath = binDir.absolutePath,
            bashAvailable = bash.exists(),
        )
    }

    /** 真的起一个进程跑 `echo pencil-ok`，验证系统终端可用性并保留失败原因。 */
    private fun probeSystemShell(): Pair<Boolean, String> {
        val shell = systemShell ?: return false to "未找到 /system/bin/sh"
        return runCatching {
            val process = ProcessBuilder(shell.absolutePath, "-c", "echo pencil-ok")
                .redirectErrorStream(true)
                .start()
            runCatching { process.outputStream.close() }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val done = process.waitFor(PROBE_SECONDS, TimeUnit.SECONDS)
            if (!done) process.destroyForcibly()
            val ok = done && output.contains("pencil-ok")
            ok to (output.ifBlank { if (done) "无输出" else "超时" })
        }.getOrElse { false to (it.message ?: it.javaClass.simpleName) }
    }

    /**
     * 下载并解压 Termux bootstrap。
     *
     * @param force 已安装时是否强制重装
     * @param timeoutSeconds 下载超时
     * @param onProgress 进度回调（下载中/解压中）
     * @return 面向模型的安装结果说明
     */
    suspend fun setupTermux(
        force: Boolean,
        timeoutSeconds: Long,
        onProgress: (String) -> Unit,
    ): String {
        if (installedMarker.exists() && !force) {
            return "Termux 环境已安装于 ${termuxRoot.absolutePath}；如需重装请设置 force=true。"
        }
        val asset = bootstrapAsset()
        val url = "$BOOTSTRAP_BASE_URL/$asset"
        onProgress("正在下载 $asset …")
        val zip = withContext(Dispatchers.IO) { download(url, timeoutSeconds) }
            .getOrElse { return "下载失败：${it.message ?: it.javaClass.simpleName}（来源 $url）" }

        onProgress("下载完成，正在解压 …")
        val count = withContext(Dispatchers.IO) {
            runCatching { extract(zip, termuxDir) }.getOrElse { -1 }
        }
        zip.delete()
        if (count < 0) {
            return "解压失败：安装包可能损坏，请重试。"
        }
        installedMarker.writeText("$asset\n${System.currentTimeMillis()}\n")
        termuxExecCache = null

        val sb = StringBuilder()
        sb.appendLine("Termux bootstrap 已安装（$asset，$count 个文件）。")
        sb.appendLine("安装位置：$termuxRootPath")
        if (canExecuteTermuxShell()) {
            sb.append("该用户land可直接执行，run_command 将优先使用其中完整的 Linux 工具链。")
        } else {
            sb.appendLine("注意：当前 Android 版本禁止从应用目录直接执行未知二进制（W^X 限制），")
            sb.append("该用户land暂不能直接作为命令解释器；run_command 将回退到系统终端（toybox），仍可编写并运行脚本。")
        }
        return sb.toString()
    }

    /**
     * 执行一条 shell 命令。
     *
     * @param cwd 工作目录（必须位于工作区内）
     * @param timeoutMs 超时毫秒
     * @param onOutput 流式输出回调：命令每产出一段文本即触发一次（跨块正确解码 UTF-8），
     *   供上层把输出实时回显到聊天进度行。
     */
    suspend fun run(
        command: String,
        cwd: File,
        timeoutMs: Long,
        stdin: String? = null,
        onOutput: (String) -> Unit = {},
    ): ShellRunResult {
        val cmd = sanitizeCommand(command)
        if (cmd.isEmpty()) return ShellRunResult("命令不能为空。", -1, false, "none")

        // 装包类命令在「Termux 用户land不可执行」的设备上必然失败，
        // 直接给出可执行的替代方案，而不是让模型看到一堆 `command not found`。
        if (!canExecuteTermuxShell() && isPackageManagerCommand(cmd)) {
            return ShellRunResult(PACKAGE_UNAVAILABLE_MESSAGE, 127, false, "none")
        }

        // 让脚本可以直接 `./x.sh`：把工作目录里的 .sh 补上执行位（Android 上常被忽略）。
        prepareScripts(cwd)

        if (canExecuteTermuxShell()) {
            val loader = termuxLoader()
            val bash = File(termuxRoot, "bin/bash")
            if (loader != null && bash.exists()) {
                val args = listOf(
                    loader.absolutePath,
                    "--library-path", File(termuxRoot, "lib").absolutePath,
                    bash.absolutePath, "-c", cmd,
                )
                val result = execute(
                    args = args,
                    cwd = cwd,
                    timeoutMs = timeoutMs,
                    backend = "Termux bash",
                    termuxMode = true,
                    stdin = stdin,
                    onOutput = onOutput,
                )
                if (result.exitCode != EXIT_START_FAILED) return result
            }
        }
        val shell = systemShell
            ?: return ShellRunResult("未找到可用的系统终端（/system/bin/sh），无法执行命令。", -1, false, "none")
        return execute(
            args = listOf(shell.absolutePath, "-c", cmd),
            cwd = cwd,
            timeoutMs = timeoutMs,
            backend = "系统终端 ${shell.absolutePath}",
            stdin = stdin,
            onOutput = onOutput,
        )
    }

    /** 清洗模型给的命令：去掉 CRLF / Markdown 代码围栏 / 首尾空白。 */
    private fun sanitizeCommand(command: String): String {
        var cmd = command.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (cmd.startsWith("```")) {
            cmd = cmd.substringAfter('\n', "").substringBeforeLast("```").trim()
        }
        return cmd.trim()
    }

    private fun isPackageManagerCommand(command: String): Boolean {
        val head = command.trimStart().substringBefore(' ').substringBefore('\n')
        return head in PACKAGE_MANAGERS
    }

    /** 工作目录顶层的 `.sh` 补执行位，避免 `./script.sh: permission denied`。 */
    private fun prepareScripts(cwd: File) {
        runCatching {
            cwd.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".sh", ignoreCase = true) && !file.canExecute()) {
                    file.setExecutable(true, false)
                }
            }
        }
    }

    // ---- 子进程执行 ----

    private suspend fun execute(
        args: List<String>,
        cwd: File,
        timeoutMs: Long,
        backend: String,
        termuxMode: Boolean = false,
        stdin: String? = null,
        onOutput: (String) -> Unit = {},
    ): ShellRunResult = withContext(Dispatchers.IO) {
        val process = runCatching {
            ProcessBuilder(args).apply {
                directory(cwd)
                redirectErrorStream(true)
                environment().apply {
                    // HOME / TMPDIR 指向工作区与缓存目录，脚本里的相对写入落在这里。
                    put("HOME", workspace.writeRootPath())
                    put("TMPDIR", context.cacheDir.absolutePath)
                    put("PWD", cwd.absolutePath)
                    put("WORKSPACE", workspace.writeRootPath())
                    val path = buildString {
                        // 工作区 bin 优先：那里有 bash→sh shim。
                        append(binDir.absolutePath)
                        if (termuxMode) {
                            append(':').append(termuxRootPath).append("/bin")
                            append(':').append(termuxRootPath).append("/bin/applets")
                        }
                        append(":/system/bin:/system/xbin:/bin:/usr/bin:/system/sbin")
                    }
                    put("PATH", path)
                    if (termuxMode) {
                        put("PREFIX", termuxRootPath)
                        put("LD_LIBRARY_PATH", "${termuxRootPath}/lib")
                    }
                    put("SHELL", systemShell?.absolutePath ?: "/system/bin/sh")
                    put("TERM", "dumb")
                    put("LANG", "en_US.UTF-8")
                }
            }.start()
        }.getOrElse {
            return@withContext ShellRunResult("命令启动失败：${it.message ?: it.javaClass.simpleName}", EXIT_START_FAILED, false, backend)
        }

        // 写入 stdin 后关闭：既支持 `cat > file` 这类读输入的命令，
        // 又保证不写 stdin 时立即 EOF，不会让命令卡在等待输入上。
        runCatching {
            process.outputStream.use { out ->
                if (!stdin.isNullOrEmpty()) out.write(stdin.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }
        // 按字符流式读取：InputStreamReader 内部处理跨块的多字节 UTF-8 序列，
        // 每读一段即回调 onOutput，同时积累完整输出用于最终结果。
        val text = StringBuilder()
        val reader = launch {
            runCatching {
                process.inputStream.reader(Charsets.UTF_8).use { input ->
                    val chunk = CharArray(READ_CHUNK_CHARS)
                    while (true) {
                        val count = input.read(chunk)
                        if (count < 0) break
                        text.append(chunk, 0, count)
                        onOutput(String(chunk, 0, count))
                    }
                }
            }
        }
        val finished = runCatching { process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!finished) runCatching { process.destroyForcibly() }
        reader.join()
        val exit = runCatching { process.exitValue() }.getOrDefault(-1)
        ShellRunResult(truncateOutput(text.toString()), exit, !finished, backend)
    }

    // ---- Termux 探测 ----

    private fun canExecuteTermuxShell(): Boolean {
        termuxExecCache?.let { return it }
        val bash = File(termuxRoot, "bin/bash")
        val loader = termuxLoader()
        val ok = if (bash.exists() && loader != null) {
            runCatching {
                val process = ProcessBuilder(
                    loader.absolutePath,
                    "--library-path", File(termuxRoot, "lib").absolutePath,
                    bash.absolutePath, "-c", "echo trace-ok",
                ).redirectErrorStream(true).start()
                runCatching { process.outputStream.close() }
                val out = process.inputStream.readBytes().decodeToString()
                val done = process.waitFor(EXEC_PROBE_SECONDS, TimeUnit.SECONDS)
                if (!done) process.destroyForcibly()
                done && out.contains("trace-ok")
            }.getOrDefault(false)
        } else {
            false
        }
        termuxExecCache = ok
        return ok
    }

    /** 定位 bootstrap 内的动态链接器（aarch64/arm/x86_64/i686 各异）。 */
    private fun termuxLoader(): File? {
        val lib = File(termuxRoot, "lib")
        return LOADER_NAMES.map { File(lib, it) }.firstOrNull { it.exists() }
    }

    // ---- 下载与解压 ----

    private fun download(url: String, timeoutSeconds: Long): Result<File> = runCatching {
        val scoped = http.newBuilder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build()
        val target = File(context.cacheDir, BOOTSTRAP_CACHE_NAME)
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        scoped.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} ${response.message}")
            val body = response.body ?: error("响应为空")
            target.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        target
    }

    private fun extract(zip: File, destDir: File): Int {
        if (!destDir.exists() && !destDir.mkdirs()) error("无法创建安装目录")
        var count = 0
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val name = entry.name
                if (isSafeEntry(name)) {
                    val target = File(destDir, name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> input.copyTo(out) }
                        if (shouldBeExecutable(name)) target.setExecutable(true, false)
                        count++
                    }
                }
                entry = input.nextEntry
            }
        }
        return count
    }

    /** 拒绝绝对路径与 `..` 穿越，防止安装包写到目录之外。 */
    private fun isSafeEntry(name: String): Boolean =
        name.isNotBlank() &&
            !name.startsWith("/") &&
            !name.startsWith("\\") &&
            name.split('/', '\\').none { it == ".." }

    private fun shouldBeExecutable(name: String): Boolean =
        name.startsWith("usr/bin/") || name.startsWith("usr/libexec/") || name.startsWith("usr/lib/")

    private fun bootstrapAsset(): String {
        val abis = Build.SUPPORTED_ABIS
        return when {
            abis.contains("arm64-v8a") -> "bootstrap-aarch64.zip"
            abis.contains("armeabi-v7a") -> "bootstrap-arm.zip"
            abis.contains("x86_64") -> "bootstrap-x86_64.zip"
            abis.contains("x86") -> "bootstrap-i686.zip"
            else -> "bootstrap-aarch64.zip"
        }
    }

    private fun truncateOutput(text: String): String =
        if (text.length <= MAX_OUTPUT_CHARS) text
        else text.take(MAX_OUTPUT_CHARS) + "\n…（输出过长已截断，共 ${text.length} 字）"

    private val termuxRootPath: String get() = termuxRoot.absolutePath

    private companion object {
        val SYSTEM_SHELL_CANDIDATES = listOf("/system/bin/sh", "/system/xbin/sh", "/bin/sh")

        val LOADER_NAMES = listOf(
            "ld-linux-aarch64.so.1",
            "ld-linux-armhf.so.3",
            "ld-linux-x86-64.so.2",
            "ld-linux.so.2",
        )

        const val TERMUX_DIR_NAME = "termux"
        const val BIN_DIR_NAME = "bin"
        const val BOOTSTRAP_CACHE_NAME = "termux-bootstrap.zip"
        const val BOOTSTRAP_BASE_URL = "https://github.com/termux/termux-packages/releases/latest/download"

        /** Termux 可执行性探测上限：只在已安装时才发起，超时即判不可用。 */
        const val EXEC_PROBE_SECONDS = 3L

        /** 系统终端自检上限。 */
        const val PROBE_SECONDS = 5L

        const val MAX_OUTPUT_CHARS = 40_000

        /** 这些命令依赖 apt/dpkg 数据库，只有真正可执行的 Termux 用户land才支持。 */
        val PACKAGE_MANAGERS = setOf("apt", "apt-get", "pkg", "dpkg")

        val PACKAGE_UNAVAILABLE_MESSAGE = """
            无法安装额外的 Linux 包：当前 Android 版本禁止应用从私有目录执行下载的二进制（W^X），
            因此 Termux 用户land即使解压也无法运行，apt / pkg / dpkg 不可用。
            可用的替代方案：
            - 直接用系统终端内置的 toybox 工具编写脚本：sh、echo、cat、ls、cp、mv、rm、mkdir、
              find、grep、sed、awk、sort、head、tail、wc、xxd、tar、unzip、base64、md5sum 等；
            - 需要新能力时，把「纯脚本」（不依赖额外二进制）写进工作区再执行；
            - 图像 / 文件 / 压缩 / 打印需求直接用项目内置工具（draw_image、edit_image、
              extract_archive、create_archive、print_* 等）。
        """.trimIndent()

        /** 流式读取的字符块大小。 */
        const val READ_CHUNK_CHARS = 2_048

        /** 约定：进程无法启动时返回该退出码，供上层回退其它后端（与真实 shell 退出码区分）。 */
        const val EXIT_START_FAILED = Int.MIN_VALUE

        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) PencilChuangYin/1.0"
    }
}
