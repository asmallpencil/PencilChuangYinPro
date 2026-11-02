package com.pencil.chuangyin.feature.ai.tools

import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.agent.runtime.ToolCallScope
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 终端类工具集合：让 Agent 拥有一个可调用、可写脚本并执行的真实终端。
 *
 * - `shell_status`：如实报告系统终端 / Termux 环境的可用性；
 * - `shell_setup`：下载并解压 Termux bootstrap（增强用户land）；
 * - `run_command`：在工作区内执行任意 shell 命令，可先 write_file 写脚本再执行。
 *
 * 执行能力本身是通用且开放的：模型由此可以「自造工具」——把一段脚本写进工作区再运行，
 * 从而扩展自身能力，无需改代码或重新编译。
 */
@Singleton
class ShellTools @Inject constructor(
    private val runtime: ShellRuntime,
    private val workspace: Workspace,
    private val scope: ToolCallScope,
) {

    /** 查询终端环境：系统终端路径、Termux 是否安装/可执行、以及一次真实自检结果。 */
    @Tool
    suspend fun shell_status(): ToolOutput {
        val status = runtime.status()
        val builder = StringBuilder()
        builder.appendLine("终端环境状态：")
        builder.appendLine(
            if (status.systemShellPath != null) {
                "- 系统终端：${status.systemShellPath}（随时可执行）"
            } else {
                "- 系统终端：未找到 /system/bin/sh（异常设备）"
            },
        )
        builder.appendLine(
            if (status.systemShellProbe) {
                "- 自检：通过（`echo` 往返正常）"
            } else {
                "- 自检：失败（${status.systemShellProbeDetail}）"
            },
        )
        builder.appendLine("- 兼容 bin 目录：${status.workspaceBinPath}（已加入 PATH）")
        builder.appendLine(
            if (status.bashAvailable) {
                "- bash：可用（Termux 自带或 bash→sh 兼容 shim，可直接写 `bash 脚本.sh`）"
            } else {
                "- bash：不可用，请统一用 `sh 脚本.sh`"
            },
        )
        builder.appendLine(
            when {
                !status.termuxInstalled -> "- Termux 环境：未安装（apt/pkg 不可用）"
                status.termuxExecutable -> "- Termux 环境：已安装且可执行（${status.termuxRootPath}）"
                else -> "- Termux 环境：已安装但系统禁止直接执行（${status.termuxRootPath}），apt/pkg 不可用，run_command 使用系统终端"
            },
        )
        builder.appendLine()
        builder.appendLine("用法：")
        builder.appendLine("- 直接执行命令：run_command(command = \"ls -la\")，支持管道 / 重定向 / 多行脚本；")
        builder.appendLine("- 需要脚本：先用 write_file 写入 `xxx.sh`，再 run_command(command = \"sh xxx.sh\")；")
        builder.appendLine("- 需要喂输入：把内容放进 stdin 参数，例如 command=\"cat > a.txt\" + stdin=\"hello\"。")
        return ToolOutput(builder.toString().trim())
    }

    /**
     * 安装/修复 Termux 环境：下载对应 ABI 的官方 bootstrap 并解压到应用私有目录。
     * 属于重量级操作，仅在需要完整 Linux 工具链（bash、coreutils、apt 等）时调用。
     */
    @Tool
    suspend fun shell_setup(
        @Param(description = "已安装时是否强制重新下载安装，默认 false。") force: Boolean?,
        @Param(description = "下载超时秒数，默认 300。") timeout_seconds: Int?,
    ): ToolOutput {
        scope.current?.reportProgress("正在准备终端环境")
        val timeout = (timeout_seconds ?: DEFAULT_SETUP_SECONDS).toLong().coerceIn(MIN_SETUP_SECONDS, MAX_SETUP_SECONDS)
        val message = withContext(Dispatchers.IO) {
            runtime.setupTermux(force = force ?: false, timeoutSeconds = timeout) { text ->
                scope.current?.reportProgress(text)
            }
        }
        val failed = message.contains("失败")
        return ToolOutput(message, isError = failed)
    }

    /**
     * 在工作区内执行一条 shell 命令。
     *
     * 支持管道、重定向、多行脚本；工作目录默认为工作区根目录，越界路径会被拒绝。
     */
    @Tool
    suspend fun run_command(
        @Param(description = "要执行的 shell 命令，可含管道/重定向/多行脚本。") command: String,
        @Param(description = "工作目录，省略则使用工作区根目录；须位于工作区内。") cwd: String?,
        @Param(description = "超时毫秒数，默认 60000，最大 300000。") timeout_ms: Int?,
        @Param(description = "可选：写入命令标准输入的内容，用于需要读取输入的命令。") stdin: String?,
    ): ToolOutput {
        val cmd = command.trim()
        if (cmd.isEmpty()) return ToolOutput("命令不能为空。", isError = true)

        val dir = resolveCwd(cwd) ?: return ToolOutput(
            "工作目录不在工作区范围内：${cwd?.trim()}（只能在工作区内执行）",
            isError = true,
        )
        val timeout = (timeout_ms ?: DEFAULT_TIMEOUT_MS).toLong().coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)

        scope.current?.reportProgress("正在执行命令")
        // 流式回显：把命令输出的尾部实时打到聊天进度行；
        // 节流上报（两次间隔不小于 STREAM_REPORT_INTERVAL_MS），避免每个输出块都触发重组。
        val tail = StringBuilder()
        var lastReportAt = 0L
        val result = runtime.run(cmd, dir, timeout, stdin) { chunk ->
            tail.append(chunk)
            if (tail.length > STREAM_TAIL_CHARS) tail.delete(0, tail.length - STREAM_TAIL_CHARS)
            val now = System.currentTimeMillis()
            if (now - lastReportAt >= STREAM_REPORT_INTERVAL_MS) {
                lastReportAt = now
                scope.current?.reportProgress(tail.toString().trimEnd())
            }
        }
        return ToolOutput(format(command = cmd, dir = dir, result = result), isError = result.exitCode != 0 && !result.timedOut)
    }

    /**
     * 把一段脚本写入工作区并直接执行。
     *
     * 相比 run_command 手工拼多行命令，这个工具让模型只专注脚本内容本身：
     * 路径、执行位、解释器都由这里处理，避免引号/换行/权限导致的失败。
     */
    @Tool
    suspend fun run_script(
        @Param(description = "脚本内容（sh 语法）。支持多行、变量、管道与重定向。") script: String,
        @Param(description = "可选脚本文件名（不含路径），默认自动命名，扩展名固定为 .sh。") file_name: String?,
        @Param(description = "可选命令行参数，会以 $1 $2 … 传给脚本。") args: List<String>?,
        @Param(description = "工作目录，省略则使用工作区根目录；须位于工作区内。") cwd: String?,
        @Param(description = "超时毫秒数，默认 60000，最大 300000。") timeout_ms: Int?,
        @Param(description = "可选：写入脚本标准输入的内容。") stdin: String?,
    ): ToolOutput {
        val body = script.trim()
        if (body.isEmpty()) return ToolOutput("脚本内容不能为空。", isError = true)

        val dir = resolveCwd(cwd) ?: return ToolOutput(
            "工作目录不在工作区范围内：${cwd?.trim()}（只能在工作区内执行）",
            isError = true,
        )
        val timeout = (timeout_ms ?: DEFAULT_TIMEOUT_MS).toLong().coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        // 注意：CharSequence.map 返回 List<Char>，旧实现直接拼进文件名会得到 "[s, e, l, ...]"
        // 这种「把字母拆成列表」的怪文件名；这里用字符串替换生成合法文件名。
        val baseName = file_name?.trim()?.removeSuffix(".sh")?.takeIf { it.isNotEmpty() }
            ?.replace(Regex("[^A-Za-z0-9_-]"), "_")
            ?.takeIf { it.isNotEmpty() }
            ?: ("script_" + System.currentTimeMillis())
        // 脚本必须落在真实可执行的私有工作区（授权输出目录走 SAF，无法被 sh 直接读取）。
        val scriptsDir = File(workspace.writeRoot, "scripts").apply { mkdirs() }
        var target = File(scriptsDir, "$baseName.sh")
        var suffix = 1
        while (target.exists()) {
            target = File(scriptsDir, "$baseName ($suffix).sh")
            suffix++
        }
        val saved = runCatching {
            target.parentFile?.mkdirs()
            target.writeBytes(body.toByteArray(Charsets.UTF_8))
            true
        }.getOrDefault(false)
        if (!saved) return ToolOutput("脚本写入失败：${target.absolutePath}", isError = true)
        runCatching { target.setExecutable(true, false) }

        val relative = "scripts/${target.name}"
        val quotedArgs = args.orEmpty().joinToString(" ") { shQuote(it) }
        // 用系统终端的绝对路径当解释器：PATH 里的 shim 位于应用私有目录，Android 10+
        // 禁止从可写目录 execve，`sh 脚本.sh` 会报 Permission denied。
        val interpreter = runtime.systemShell?.absolutePath ?: "sh"
        val command = buildString {
            append(shQuote(interpreter)).append(' ').append(shQuote(target.absolutePath))
            if (quotedArgs.isNotEmpty()) append(' ').append(quotedArgs)
        }

        scope.current?.reportProgress("正在运行脚本 ${target.name}")
        val result = runtime.run(command, dir, timeout, stdin)
        val output = buildString {
            appendLine("脚本已保存为 $relative")
            appendLine()
            append(format(command = command, dir = dir, result = result))
            if (result.exitCode == 127) appendLine().append(COMMAND_NOT_FOUND_HINT)
        }
        return ToolOutput(output.trimEnd(), isError = result.exitCode != 0 && !result.timedOut)
    }

    private fun resolveCwd(cwd: String?): File? {
        val raw = cwd?.trim()?.takeIf { it.isNotEmpty() } ?: return ensureDir(workspace.writeRoot)
        val target = runCatching { workspace.resolveForWrite(raw) }.getOrNull() ?: return null
        return ensureDir(target)
    }

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun format(command: String, dir: File, result: ShellRunResult): String {
        val builder = StringBuilder()
        builder.appendLine("$ $command")
        builder.appendLine("（工作目录：${workspace.displayPath(dir)}，后端：${result.backend}）")
        builder.appendLine()
        if (result.output.isNotBlank()) {
            builder.append(result.output.trimEnd())
            if (!builder.endsWith("\n")) builder.appendLine()
        }
        when {
            result.timedOut -> builder.append("—— 命令超时被终止（退出码 ${result.exitCode}）")
            result.exitCode == 0 -> builder.append("—— 命令执行成功")
            result.exitCode == 127 -> builder.append("—— 命令不存在或无法执行（退出码 127）").appendLine().append(COMMAND_NOT_FOUND_HINT)
            else -> builder.append("—— 命令退出码 ${result.exitCode}")
        }
        return builder.toString().trimEnd()
    }

    /** 命令不存在时的固定指引，避免模型反复换命令试探。 */
    private val COMMAND_NOT_FOUND_HINT: String = """
        可用能力提示：
        - 文本/文件处理：sh、cat、ls、cp、mv、rm、mkdir、find、grep、sed、awk、sort、wc、head、tail、base64、md5sum、tar、unzip；
        - 制表/制图/排版：直接用 create_table / create_chart / create_note；
        - 图片处理：edit_image（缩放/裁剪/旋转/灰度/格式转换）；
        - 当前终端没有 python / node / git / apt，不要继续尝试安装或调用它们。
    """.trimIndent()

    private fun shQuote(text: String): String = "'" + text.replace("'", "'\\''") + "'"

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 60_000
        const val MIN_TIMEOUT_MS = 1_000L
        const val MAX_TIMEOUT_MS = 300_000L

        const val DEFAULT_SETUP_SECONDS = 300
        const val MIN_SETUP_SECONDS = 30L
        const val MAX_SETUP_SECONDS = 1_800L

        /** 流式回显保留的输出尾部最大字符数。 */
        const val STREAM_TAIL_CHARS = 400

        /** 两次流式进度上报的最小间隔毫秒。 */
        const val STREAM_REPORT_INTERVAL_MS = 300L
    }
}
