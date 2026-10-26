package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.ai.AgentTool
import com.pencil.chuangyin.desktop.ai.ToolOutput
import com.pencil.chuangyin.desktop.navigation.DesktopNavigator
import kotlinx.serialization.json.JsonObject

/**
 * 应用控制工具（桌面版，对齐安卓端 `AppTools`）。
 *
 * 让 AI 直接切换页面：指令经 [DesktopNavigator] 投递给 `AppShell` 执行，
 * 因此 AI 能「替用户点击」进入首页 / 创意 / P 站 / 文本打印 / 图片打印 / AI 对话 / 设置。
 * 桌面端没有安卓端的具名目的地（P 站搜索、插画详情等由 P 站内部自行管理），
 * 因此不提供 `destination_key` 参数，避免向模型承诺不存在的能力。
 */
class AppTools {

    val tools: List<AgentTool> = listOf(
        agentTool(
            name = ToolNames.APP_NAVIGATE,
            description = "跳转/切换到软件内的页面：用 page 指定目标页面。可用页面先用 app_list_destinations 查询。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.PAGE_KEY),
                properties = mapOf(
                    ToolParams.PAGE_KEY to ToolSchema.string(
                        "要切换的页面。",
                        DesktopNavigator.pages().map { it.key },
                    ),
                ),
            ),
        ) { args, _ -> navigate(args) },
        agentTool(
            name = ToolNames.APP_LIST_DESTINATIONS,
            description = "列出软件内所有可跳转的页面，供 app_navigate 使用。",
            parameters = ToolSchema.parameters(required = emptyList(), properties = emptyMap()),
        ) { _, _ -> listDestinations() },
    )

    private fun navigate(args: JsonObject): ToolOutput {
        val raw = args.stringOrNull(ToolParams.PAGE_KEY)?.trim().orEmpty()
        if (raw.isEmpty()) return failure("需要提供 page 参数，可选：${DesktopNavigator.pages().joinToString("、") { it.key }}")
        val page = DesktopNavigator.Page.of(raw)
            ?: return failure("未知页面：$raw。可选：${DesktopNavigator.pages().joinToString("、") { it.key }}")
        return if (DesktopNavigator.navigate(page)) {
            ToolOutput("已切换页面：${page.description}。")
        } else {
            failure("当前无法切换页面（界面尚未就绪），请提示用户手动打开。")
        }
    }

    private fun listDestinations(): ToolOutput {
        val builder = StringBuilder()
        builder.appendLine("【可切换的页面 page 取值】")
        DesktopNavigator.pages().forEach { page -> builder.appendLine("- ${page.key}：${page.description}") }
        return ToolOutput(builder.toString().trim())
    }
}
