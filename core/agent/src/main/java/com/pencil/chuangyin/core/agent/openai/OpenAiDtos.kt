package com.pencil.chuangyin.core.agent.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 流式分片（`chat.completion.chunk`）。
 *
 * 各厂商在 `delta` 上还有扩展字段，统一由宽松 JSON 配置忽略。
 */
@Serializable
internal data class ChatCompletionChunk(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
) {
    @Serializable
    internal data class Choice(
        val index: Int = 0,
        val delta: Delta? = null,
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    internal data class Delta(
        val role: String? = null,
        val content: String? = null,
        /** DeepSeek / 智谱 等厂商的思考字段。 */
        @SerialName("reasoning_content") val reasoningContent: String? = null,
        /** OpenRouter / 部分厂商的思考字段。 */
        val reasoning: String? = null,
        @SerialName("tool_calls") val toolCalls: List<ToolCallDelta>? = null,
    )

    @Serializable
    internal data class ToolCallDelta(
        val index: Int = 0,
        val id: String? = null,
        val type: String? = null,
        val function: FunctionDelta? = null,
    )

    @Serializable
    internal data class FunctionDelta(
        val name: String? = null,
        val arguments: String? = null,
    )
}

/** 非流式补全响应。 */
@Serializable
internal data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
) {
    @Serializable
    internal data class Choice(
        val index: Int = 0,
        val message: Message? = null,
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    internal data class Message(
        val role: String? = null,
        val content: String? = null,
        @SerialName("reasoning_content") val reasoningContent: String? = null,
        val reasoning: String? = null,
    )
}

/** 服务端错误体（`{"error": {...}}` 或 `{"message": "..."}`）。 */
@Serializable
internal data class ApiErrorEnvelope(
    val error: ApiError? = null,
    val message: String? = null,
)

@Serializable
internal data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)
