package com.pencil.chuangyin.core.agent.model

/** 流式补全事件。 */
sealed interface ChatStreamEvent {

    /** 深度思考增量。 */
    data class ReasoningDelta(val text: String) : ChatStreamEvent

    /** 正文增量。 */
    data class ContentDelta(val text: String) : ChatStreamEvent

    /** 服务端下发完毕的完整工具调用列表。 */
    data class ToolCallsReady(val calls: List<ToolCallRequest>) : ChatStreamEvent

    /** 请求失败（终止事件，不会再出现 [Completed]）。 */
    data class Failed(val error: AgentError) : ChatStreamEvent

    /** 流正常结束。 */
    data object Completed : ChatStreamEvent
}
