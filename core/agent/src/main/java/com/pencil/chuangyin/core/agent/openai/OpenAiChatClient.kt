package com.pencil.chuangyin.core.agent.openai

import com.pencil.chuangyin.core.agent.config.AiModelConfig
import com.pencil.chuangyin.core.agent.model.AgentError
import com.pencil.chuangyin.core.agent.model.ChatRequest
import com.pencil.chuangyin.core.agent.model.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

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
