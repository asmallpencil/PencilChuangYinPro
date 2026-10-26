package com.pencil.chuangyin.feature.ai.tools

import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.ui.navigation.AppNavigationBus
import com.pencil.chuangyin.core.ui.navigation.AppPage
import javax.inject.Inject
import javax.inject.Singleton

/** app_navigate 的顶级页面取值（官方生成器会把枚举值列表写进 Schema 的 enum）。 */
@Suppress("EnumEntryName")
enum class AppPageKey { home, creative, pstation, text, image, camera, settings, ai }

/**
 * 应用控制工具：让 AI 直接切换页面、打开具名功能（含带参数的 P 站搜索/详情/小说页）。
 *
 * 指令经 [AppNavigationBus] 投递给导航宿主执行，因此 AI 能「替用户点击」进入任意页面。
 */
@Singleton
class AppTools @Inject constructor(
    private val navigation: AppNavigationBus,
) {

    /** 跳转/切换到软件内的页面或功能：可用 page 切换顶级页面，或用 destination_key + args 打开具名功能（如 P 站搜索、插画详情、小说阅读）。可用目的地先用 app_list_destinations 查询。 */
    @Tool
    suspend fun app_navigate(
        @Param(description = "要切换的顶级页面。") page: AppPageKey?,
        @Param(description = "具名目的地 key，如 pixiv_search、pixiv_detail、pixiv_novel_reader。") destination_key: String?,
        @Param(description = "目的地参数对象，如 {\"word\":\"猫\",\"mode\":\"date_desc\"}。") args: Map<String, String>?,
    ): ToolOutput {
        val pageKey = page?.name
        if (pageKey != null) {
            val target = AppPage.entries.firstOrNull { it.key.equals(pageKey, ignoreCase = true) }
                ?: return fileFailure("未知页面：$pageKey。可选：${AppPage.entries.joinToString("、") { it.key }}")
            navigation.openPage(target)
            return ToolOutput("已切换页面：${target.description}。")
        }

        val destinationKeyValue = destination_key?.trim()?.takeIf { it.isNotEmpty() }
        if (destinationKeyValue != null) {
            val argsMap = args ?: emptyMap()
            return if (navigation.openDestination(destinationKeyValue, argsMap)) {
                ToolOutput("已跳转到 $destinationKeyValue。")
            } else {
                val available = navigation.registeredDestinations().joinToString("、") { it.key }.ifEmpty { "（暂无）" }
                fileFailure("未知目的地：$destinationKeyValue。可用：$available")
            }
        }

        return fileFailure("需要提供 page 或 destination_key 参数。")
    }

    /** 列出软件内所有可跳转的页面与具名功能目的地，供 app_navigate 使用。 */
    @Tool
    suspend fun app_list_destinations(): ToolOutput {
        val builder = StringBuilder()
        builder.appendLine("【顶级页面 page 取值】")
        AppPage.entries.forEach { page -> builder.appendLine("- ${page.key}：${page.description}") }
        builder.appendLine()
        builder.appendLine("【具名功能 destination_key】")
        val destinations = navigation.registeredDestinations()
        if (destinations.isEmpty()) {
            builder.appendLine("（暂无）")
        } else {
            destinations.forEach { destination ->
                builder.appendLine("- ${destination.key}：${destination.description}")
            }
        }
        return ToolOutput(builder.toString().trim())
    }
}
