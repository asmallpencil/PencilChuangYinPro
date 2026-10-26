package com.pencil.chuangyin.core.agent.model

/** Agent 循环向界面推送的事件。 */
sealed interface AgentEvent {

    /** 开始一次模型调用（第 [iteration] 轮）。 */
    data class IterationStarted(val iteration: Int) : AgentEvent

    /** 新助手消息开始流式输出。 */
    data class MessageStarted(val messageId: String) : AgentEvent

    /** 深度思考增量。 */
    data class ReasoningDelta(val messageId: String, val text: String) : AgentEvent

    /** 正文增量。 */
    data class ContentDelta(val messageId: String, val text: String) : AgentEvent

    /** 一条消息定稿（助手消息或工具结果消息）。 */
    data class MessageCompleted(val message: ChatMessage) : AgentEvent

    /** 开始执行某个工具调用。 */
    data class ToolStarted(
        val toolCallId: String,
        val toolName: String,
        val arguments: String,
    ) : AgentEvent

    /** 工具执行进度。 */
    data class ToolProgress(val toolCallId: String, val text: String) : AgentEvent

    /** 产生可展示产物。 */
    data class ArtifactCreated(val artifact: ToolArtifact) : AgentEvent

    /** 任务正常结束。 */
    data class Finished(val history: List<ChatMessage>) : AgentEvent

    /** 任务失败结束。 */
    data class Failed(val error: AgentError, val history: List<ChatMessage>) : AgentEvent
}
