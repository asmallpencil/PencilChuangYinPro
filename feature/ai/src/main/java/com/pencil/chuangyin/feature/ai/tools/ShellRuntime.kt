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

    fun status(): ShellStatus {
        val installed = installedMarker.exists() && termuxRoot.isDirectory
        return ShellStatus(
            systemShellPath = systemShell?.absolutePath,
            termuxInstalled = installed,
            termuxRootPath = termuxRoot.absolutePath,
            termuxExecutable = installed && canExecuteTermuxShell(),
        )
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
        onOutput: (String) -> Unit = {},
    ): ShellRunResult {
        if (canExecuteTermuxShell()) {
            val loader = termuxLoader()
            val bash = File(termuxRoot, "bin/bash")
            if (loader != null && bash.exists()) {
                val args = listOf(
                    loader.absolutePath,
                    "--library-path", File(termuxRoot, "lib").absolutePath,
                    bash.absolutePath, "-c", command,
                )
                val result = execute(args, cwd, timeoutMs, "Termux bash", termuxMode = true, onOutput = onOutput)
                if (result.exitCode != EXIT_START_FAILED) return result
            }
        }
        val shell = systemShell
            ?: return ShellRunResult("未找到可用的系统终端（/system/bin/sh），无法执行命令。", -1, false, "none")
        return execute(
            args = listOf(shell.absolutePath, "-c", command),
            cwd = cwd,
            timeoutMs = timeoutMs,
            backend = "系统终端 ${shell.absolutePath}",
            onOutput = onOutput,
        )
    }

    // ---- 子进程执行 ----

    private suspend fun execute(
        args: List<String>,
        cwd: File,
        timeoutMs: Long,
        backend: String,
        termuxMode: Boolean = false,
        onOutput: (String) -> Unit = {},
    ): ShellRunResult = withContext(Dispatchers.IO) {
        val process = runCatching {
            ProcessBuilder(args).apply {
                directory(cwd)
                redirectErrorStream(true)
                environment().apply {
                    put("HOME", workspace.writeRootPath())
                    put("TMPDIR", context.cacheDir.absolutePath)
                    put("PATH", if (termuxMode) "$termuxRootPath/bin:/system/bin:/system/xbin"
                    else "/system/bin:/system/xbin:/bin:/usr/bin")
                    put("TERM", "dumb")
                    put("LANG", "en_US.UTF-8")
                }
            }.start()
        }.getOrElse {
            return@withContext ShellRunResult("命令启动失败：${it.message ?: it.javaClass.simpleName}", EXIT_START_FAILED, false, backend)
        }

        runCatching { process.outputStream.close() }
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
        const val BOOTSTRAP_CACHE_NAME = "termux-bootstrap.zip"
        const val BOOTSTRAP_BASE_URL = "https://github.com/termux/termux-packages/releases/latest/download"
        const val EXEC_PROBE_SECONDS = 8L
        const val MAX_OUTPUT_CHARS = 40_000

        /** 流式读取的字符块大小。 */
        const val READ_CHUNK_CHARS = 2_048

        /** 约定：进程无法启动时返回该退出码，供上层回退其它后端（与真实 shell 退出码区分）。 */
        const val EXIT_START_FAILED = Int.MIN_VALUE

        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) PencilChuangYin/1.0"
    }
}
