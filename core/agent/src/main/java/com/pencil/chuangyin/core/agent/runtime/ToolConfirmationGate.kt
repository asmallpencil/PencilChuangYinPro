package com.pencil.chuangyin.core.agent.runtime

import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ToolPermission
import kotlinx.serialization.json.JsonObject

/**
 * 不可逆工具执行前的用户确认门。
 *
 * 由宿主功能模块通过 Hilt `@IntoSet` 注入；[AgentEngine] 在调用
 * `requireConfirmation = true` 的工具前逐一询问，任一门拒绝即拦截本次调用
 * （不会执行工具函数），并把拦截原因作为工具结果回灌给模型。
 */
interface ToolConfirmationGate {

    /**
     * 询问是否放行一次工具调用。
     *
     * @param toolName 工具名。
     * @param arguments 模型给出的调用参数。
     * @param transcript 当前会话消息（含本次运行新增）。
     * @param permission 该工具的权限等级，用于界面提示风险程度。
     * @return true 表示放行执行；false 表示拦截。
     */
    suspend fun requestConfirmation(
        toolName: String,
        arguments: JsonObject,
        transcript: List<ChatMessage>,
        permission: ToolPermission,
    ): Boolean
}
