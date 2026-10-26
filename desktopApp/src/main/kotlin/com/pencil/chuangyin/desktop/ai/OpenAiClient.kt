package com.pencil.chuangyin.desktop.ai

import com.pencil.chuangyin.desktop.support.AiModelConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 非流式补全结果。 */
data class ChatCompletionResult(
    /** 正文；失败时为 null。 */
    val content: String?,
    /** 失败原因；成功时为 null。 */
    val error: AgentError? = null,
) {
    /** 是否成功。 */
    val isSuccess: Boolean get() = error == null
}

/** OpenAI 兼容的对话补全客户端。 */
interface OpenAiChatClient {

    /** 以 SSE 流式发起对话补全。 */
    fun stream(config: AiModelConfig, request: ChatRequest): Flow<ChatStreamEvent>

    /**
     * 以非流式方式发起对话补全。
     *
     * 用于上下文摘要与连通性检查；[request] 中的工具声明会被忽略。
     */
    suspend fun complete(config: AiModelConfig, request: ChatRequest): ChatCompletionResult
}

/* ───────────────────────────── 线上协议 DTO ───────────────────────────── */

/** 流式分片（`chat.completion.chunk`）。 */
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
