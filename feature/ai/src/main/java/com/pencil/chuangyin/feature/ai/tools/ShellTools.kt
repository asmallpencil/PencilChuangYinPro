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

    /** 查询终端环境：系统终端路径、Termux 是否安装/可执行。 */
    @Tool
    suspend fun shell_status(): ToolOutput {
        val status = runtime.status()
        val builder = StringBuilder()
        builder.appendLine("终端环境状态：")
        builder.appendLine(
            if (status.systemShellPath != null) {
                "- 系统终端：${status.systemShellPath}（可用，随时可执行）"
            } else {
                "- 系统终端：未找到 /system/bin/sh（异常设备）"
            },
        )
        builder.appendLine(
            when {
                !status.termuxInstalled -> "- Termux 环境：未安装（可调用 shell_setup 安装）"
                status.termuxExecutable -> "- Termux 环境：已安装且可执行（${status.termuxRootPath}）"
                else -> "- Termux 环境：已安装但当前系统禁止直接执行（${status.termuxRootPath}），run_command 将使用系统终端"
            },
        )
        builder.appendLine()
        builder.append("用法：run_command 可直接执行命令；需要脚本时先用 write_file 写入 .sh 文件，再执行 `sh 文件名.sh`。")
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
        val result = runtime.run(cmd, dir, timeout) { chunk ->
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
            else -> builder.append("—— 命令退出码 ${result.exitCode}")
        }
        return builder.toString().trimEnd()
    }

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
