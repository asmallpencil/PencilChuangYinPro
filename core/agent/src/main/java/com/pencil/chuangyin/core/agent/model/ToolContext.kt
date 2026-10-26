package com.pencil.chuangyin.core.agent.model

/** 工具执行上下文：由 Agent 循环注入，向工具暴露会话与进度上报能力。 */
interface ToolContext {

    /** 当前会话 ID。 */
    val conversationId: String

    /** 当前会话的完整消息（含本次运行中新增的内容）。 */
    suspend fun transcript(): List<ChatMessage>

    /** 向界面汇报阶段性进度。 */
    fun reportProgress(text: String)
}
