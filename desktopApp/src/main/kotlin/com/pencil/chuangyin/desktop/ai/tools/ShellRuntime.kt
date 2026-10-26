package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.support.DesktopPaths
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一次 shell 命令的执行结果。 */
data class ShellRunResult(
    val output: String,
    val exitCode: Int,
    val timedOut: Boolean,
    val backend: String,
)

/** 终端环境的可用性快照，供 shell_status 如实上报。 */
data class ShellStatus(
    val powershellPath: String?,
) {
    val available: Boolean get() = powershellPath != null
}

/**
 * AI 终端运行时（桌面版，对齐安卓端 `ShellRuntime`）。
 *
 * 安卓端有「系统终端（toybox）」与「Termux 用户land」两个后端，桌面端不需要下载任何运行时：
 * Windows 自带的 PowerShell 就是完整可用的脚本解释器，因此这里只保留一个后端，
 * 命令一律通过 `powershell.exe -NoProfile -Command` 执行。
 *
 * 与安卓端一致的约定：
 * - 工作目录必须落在 agent 工作区内（越界由 [Workspace] 拒绝）；
 * - 输出按 UTF-8 字符流读取并逐段回调，供上层把输出实时回显到聊天进度行；
 * - 超时强制终止进程，输出总量超限截断并提示；
 * - 进程无法启动时返回 [EXIT_START_FAILED]，与真实退出码区分。
 */
class ShellRuntime(private val workspace: Workspace) {

    /** PowerShell 可执行文件路径（找不到时为 null）。 */
    val powershellPath: String? by lazy { resolvePowerShell() }

    fun status(): ShellStatus = ShellStatus(powershellPath)

    /**
     * 执行一条 shell 命令。
     *
     * @param cwd 工作目录（必须位于工作区内）
     * @param timeoutMs 超时毫秒
     * @param onOutput 流式输出回调：命令每产出一段文本即触发一次（跨块正确解码 UTF-8）。
     */
    suspend fun run(
        command: String,
        cwd: File,
        timeoutMs: Long,
        onOutput: (String) -> Unit = {},
    ): ShellRunResult {
        val shell = powershellPath
            ?: return ShellRunResult(
                "未找到可用的 PowerShell（powershell.exe），无法执行命令。",
                EXIT_START_FAILED,
                false,
                "none",
            )
        return execute(
            args = listOf(shell, "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", wrap(command)),
            cwd = cwd,
            timeoutMs = timeoutMs,
            backend = "PowerShell $shell",
            onOutput = onOutput,
        )
    }

    /**
     * 在用户命令前后拼接编码设置与退出码透传语句。
     *
     * PowerShell 默认按控制台代码页输出，先统一为 UTF-8 才不会把中文读成乱码；
     * 尾部把 `$LASTEXITCODE`（原生程序的退出码）原样透传，否则以 `$?` 判定成败。
     */
    private fun wrap(command: String): String = buildString {
        appendLine("[Console]::OutputEncoding=[System.Text.Encoding]::UTF8")
        appendLine("\$OutputEncoding=[System.Text.Encoding]::UTF8")
        appendLine("\$ProgressPreference='SilentlyContinue'")
        appendLine(command)
        appendLine("if (\$null -ne \$LASTEXITCODE) { exit \$LASTEXITCODE } elseif (\$?) { exit 0 } else { exit 1 }")
    }

    // ---- 子进程执行 ----

    private suspend fun execute(
        args: List<String>,
        cwd: File,
        timeoutMs: Long,
        backend: String,
        onOutput: (String) -> Unit = {},
    ): ShellRunResult = withContext(Dispatchers.IO) {
        val process = runCatching {
            ProcessBuilder(args).apply {
                directory(cwd)
                redirectErrorStream(true)
                environment().apply {
                    put("HOME", workspace.writeRootPath())
                    put("TEMP", tempDir().absolutePath)
                    put("TMP", tempDir().absolutePath)
                }
            }.start()
        }.getOrElse {
            return@withContext ShellRunResult(
                "命令启动失败：${it.message ?: it.javaClass.simpleName}",
                EXIT_START_FAILED,
                false,
                backend,
            )
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

    // ---- 环境探测 ----

    /** 依次探测 `%SystemRoot%` 下的 PowerShell、再回退到 `PATH` 中的 `powershell.exe`。 */
    private fun resolvePowerShell(): String? {
        val root = System.getenv("SystemRoot") ?: System.getenv("windir") ?: "C:\\Windows"
        val candidates = listOf(
            File(root, "System32\\WindowsPowerShell\\v1.0\\powershell.exe"),
            File(root, "Sysnative\\WindowsPowerShell\\v1.0\\powershell.exe"),
        )
        candidates.firstOrNull { it.isFile }?.let { return it.absolutePath }

        val path = System.getenv("PATH").orEmpty()
        return path.split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }
            .map { File(it.trim(), POWERSHELL_EXE) }
            .firstOrNull { it.isFile }
            ?.absolutePath
    }

    private fun tempDir(): File = DesktopPaths.subDir(TEMP_DIR_NAME) ?: DesktopPaths.appDataRoot()

    private fun truncateOutput(text: String): String =
        if (text.length <= MAX_OUTPUT_CHARS) text
        else text.take(MAX_OUTPUT_CHARS) + "\n…（输出过长已截断，共 ${text.length} 字）"

    private companion object {
        const val POWERSHELL_EXE = "powershell.exe"
        const val TEMP_DIR_NAME = "tmp"

        const val MAX_OUTPUT_CHARS = 40_000

        /** 流式读取的字符块大小。 */
        const val READ_CHUNK_CHARS = 2_048

        /** 约定：进程无法启动时返回该退出码，与真实退出码区分。 */
        const val EXIT_START_FAILED = Int.MIN_VALUE
    }
}
