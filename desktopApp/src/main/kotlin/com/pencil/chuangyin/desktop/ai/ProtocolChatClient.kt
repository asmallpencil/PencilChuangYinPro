package com.pencil.chuangyin.desktop.ai

import com.pencil.chuangyin.desktop.support.AiModelConfig
import com.pencil.chuangyin.desktop.support.ApiProtocol
import kotlinx.coroutines.flow.Flow

/**
 * 协议分派客户端：按 [AiModelConfig.protocol] 把请求交给对应协议实现。
 *
 * 上层（引擎、摘要、连通性检测）只依赖 [OpenAiChatClient] 这一套会话模型，
 * 因此新增上游协议时不必改动引擎。
 */
class ProtocolChatClient(
    private val openAi: OkHttpOpenAiChatClient,
    private val anthropic: AnthropicChatClient,
    private val gemini: GeminiChatClient,
) : OpenAiChatClient {

    override fun stream(config: AiModelConfig, request: ChatRequest): Flow<ChatStreamEvent> =
        delegate(config).stream(config, request)

    override suspend fun complete(
        config: AiModelConfig,
        request: ChatRequest,
    ): ChatCompletionResult = delegate(config).complete(config, request)

    private fun delegate(config: AiModelConfig): OpenAiChatClient = when (config.protocol) {
        ApiProtocol.OPENAI -> openAi
        ApiProtocol.ANTHROPIC -> anthropic
        ApiProtocol.GEMINI -> gemini
    }
}
