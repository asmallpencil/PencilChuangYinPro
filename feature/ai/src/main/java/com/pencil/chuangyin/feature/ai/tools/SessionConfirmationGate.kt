package com.pencil.chuangyin.feature.ai.tools

import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ToolPermission
import com.pencil.chuangyin.core.agent.runtime.ToolApprovalBus
import com.pencil.chuangyin.core.agent.runtime.ToolConfirmationGate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

/**
 * 会话确认门：把引擎的「需要用户确认」转交给 [ToolApprovalBus]，由聊天界面当场弹窗询问。
 *
 * 历史实现用「参数哈希 + 下一轮文本同意词」判定放行，但模型被拦截后极少以完全相同的参数
 * 重新调用，导致终端等工具每次都被判为未获同意。现改为「用户点按钮 → 本会话内该工具免再问」，
 * 既保证危险操作可控，又不会让 AI 终端陷入永远无法执行的死循环。
 */
@Singleton
class SessionConfirmationGate @Inject constructor(
    private val bus: ToolApprovalBus,
) : ToolConfirmationGate {

    override suspend fun requestConfirmation(
        toolName: String,
        arguments: JsonObject,
        transcript: List<ChatMessage>,
        permission: ToolPermission,
    ): Boolean = bus.request(
        toolName = toolName,
        arguments = arguments.toString(),
        permission = permission,
    )
}
