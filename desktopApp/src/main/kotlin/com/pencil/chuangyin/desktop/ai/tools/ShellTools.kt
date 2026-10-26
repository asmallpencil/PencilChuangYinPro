package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.ai.AgentTool
import com.pencil.chuangyin.desktop.ai.ToolOutput
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * 终端类工具集合（桌面版，对齐安卓端 `ShellTools`）。
 *
 * - `shell_status`：如实报告 PowerShell 的可用性；
 * - `run_command`：在工作区内执行任意 PowerShell 命令，可先 write_file 写脚本再执行。
 *
 * 执行能力本身是通用且开放的：模型由此可以「自造工具」——把一段脚本写进工作区再运行，
 * 从而扩展自身能力，无需改代码或重新编译。桌面端没有 Termux 概念，因此不提供 `shell_setup`。
 */
class ShellTools(
    private val runtime: ShellRuntime,
    private val workspace: Workspace,
) {

    val tools: List<AgentTool> = listOf(
        agentTool(
            name = ToolNames.SHELL_STATUS,
            description = "查询终端环境状态：PowerShell 的路径与可用性、工作目录、输出编码等。",
            parameters = ToolSchema.parameters(required = emptyList(), properties = emptyMap()),
        ) { _, _ -> shellStatus() },
        agentTool(
            name = ToolNames.RUN_COMMAND,
            description = "在工作区内执行一条 PowerShell 命令。支持管道、重定向、多行脚本；" +
                "工作目录默认为工作区根目录，越界路径会被拒绝；需要脚本时先用 write_file 写 .ps1 文件再执行。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.COMMAND),
                properties = mapOf(
                    ToolParams.COMMAND to ToolSchema.string("要执行的 PowerShell 命令，可含管道/重定向/多行脚本。"),
                    ToolParams.CWD to ToolSchema.string("工作目录，省略则使用工作区根目录；须位于工作区内。"),
                    ToolParams.TIMEOUT_MS to ToolSchema.integer("超时毫秒数，默认 60000，最大 300000。"),
                ),
            ),
        ) { args, context -> runCommand(args, context) },
    )

    private fun shellStatus(): ToolOutput {
        val status = runtime.status()
        val builder = StringBuilder()
        builder.appendLine("终端环境状态：")
        builder.appendLine(
            if (status.powershellPath != null) {
                "- PowerShell：${status.powershellPath}（可用，随时可执行）"
            } else {
                "- PowerShell：未找到 powershell.exe（系统 PATH 异常）"
            },
        )
        builder.appendLine("- 平台：Windows 桌面版（命令以 PowerShell 语法执行）")
        builder.appendLine("- 工作区：${workspace.writeRootPath()}")
        builder.appendLine()
        builder.append("用法：run_command 可直接执行命令；需要脚本时先用 write_file 写入 .ps1 文件，再用 `& .\\文件名.ps1` 执行。")
        return ToolOutput(builder.toString().trim())
    }

    private suspend fun runCommand(args: JsonObject, context: com.pencil.chuangyin.desktop.ai.ToolContext): ToolOutput {
        val cmd = args.stringOrNull(ToolParams.COMMAND).orEmpty().trim()
        if (cmd.isEmpty()) return ToolOutput("命令不能为空。", isError = true)

        val rawCwd = args.stringOrNull(ToolParams.CWD)
        val dir = resolveCwd(rawCwd) ?: return ToolOutput(
            "工作目录不在工作区范围内：${rawCwd?.trim()}（只能在工作区内执行）",
            isError = true,
        )
        val timeout = (args.intOrNull(ToolParams.TIMEOUT_MS)?.toLong() ?: DEFAULT_TIMEOUT_MS)
            .coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)

        context.reportProgress("正在执行命令")
        // 流式回显：把命令输出的尾部实时打到聊天进度行；
        // 节流上报（两次间隔不小于 STREAM_REPORT_INTERVAL_MS），避免每个输出块都触发重组。
        val tail = StringBuilder()
        var lastReportAt = 0L
        val result = withContext(Dispatchers.IO) {
            runtime.run(cmd, dir, timeout) { chunk ->
                tail.append(chunk)
                if (tail.length > STREAM_TAIL_CHARS) tail.delete(0, tail.length - STREAM_TAIL_CHARS)
                val now = System.currentTimeMillis()
                if (now - lastReportAt >= STREAM_REPORT_INTERVAL_MS) {
                    lastReportAt = now
                    context.reportProgress(tail.toString().trimEnd())
                }
            }
        }
        return ToolOutput(
            format(command = cmd, dir = dir, result = result),
            isError = result.exitCode != 0 && !result.timedOut,
        )
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
        builder.appendLine("PS> $command")
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
        const val DEFAULT_TIMEOUT_MS = 60_000L
        const val MIN_TIMEOUT_MS = 1_000L
        const val MAX_TIMEOUT_MS = 300_000L

        /** 流式回显保留的输出尾部最大字符数。 */
        const val STREAM_TAIL_CHARS = 400

        /** 两次流式进度上报的最小间隔毫秒。 */
        const val STREAM_REPORT_INTERVAL_MS = 300L
    }
}
