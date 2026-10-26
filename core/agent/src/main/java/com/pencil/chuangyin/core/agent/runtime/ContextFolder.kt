package com.pencil.chuangyin.core.agent.runtime

import com.pencil.chuangyin.core.agent.AgentDefaults
import com.pencil.chuangyin.core.agent.config.AiModelConfig
import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ChatRequest
import com.pencil.chuangyin.core.agent.model.ChatRole
import com.pencil.chuangyin.core.agent.openai.OpenAiChatClient
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动上下文折叠。
 *
 * 会话体积超过阈值时，把较早的消息交给模型压缩成一条摘要系统消息，
 * 与保留的近期消息拼接后继续对话；摘要可累加，不会丢失更早的信息。
 */
@Singleton
class ContextFolder @Inject constructor(
    private val client: OpenAiChatClient,
) {

    /**
     * 视需要折叠 [history]。
     *
     * @return 折叠后的消息列表；无需折叠时原样返回同一实例。
     */
    suspend fun foldIfNeeded(
        config: AiModelConfig,
        history: List<ChatMessage>,
    ): List<ChatMessage> {
        if (history.sumOf { it.estimatedChars } <= AgentDefaults.CONTEXT_FOLD_THRESHOLD_CHARS) {
            return history
        }
        if (history.size <= AgentDefaults.CONTEXT_KEEP_RECENT + 1) return history

        var cut = history.size - AgentDefaults.CONTEXT_KEEP_RECENT
        // 不能切开 assistant.tool_calls 与其后的 tool 结果，否则服务端会拒绝请求。
        while (cut < history.size && history[cut].role == ChatRole.Tool) cut++
        if (cut <= 0 || cut >= history.size) return history

        val older = history.subList(0, cut)
        val recent = history.subList(cut, history.size)
        val summary = summarize(config, older).ifBlank { FALLBACK_SUMMARY }
        val head = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.System,
            content = summary,
            createdAt = System.currentTimeMillis(),
            isSummary = true,
        )
        return buildList {
            add(head)
            addAll(recent)
        }
    }

    /** 请求模型把早期消息压缩为结构化摘要；失败时返回空串由调用方降级。 */
    private suspend fun summarize(config: AiModelConfig, older: List<ChatMessage>): String {
        val digest = renderTranscript(older).take(AgentDefaults.SUMMARY_INPUT_MAX_CHARS)
        if (digest.isBlank()) return ""
        val request = ChatRequest(
            model = config.modelName,
            messages = listOf(
                ChatMessage(
                    id = SUMMARY_MESSAGE_ID,
                    role = ChatRole.System,
                    content = SUMMARY_SYSTEM_PROMPT,
                ),
                ChatMessage(
                    id = SUMMARY_MESSAGE_ID,
                    role = ChatRole.User,
                    content = digest,
                ),
            ),
            temperature = SUMMARY_TEMPERATURE,
            maxTokens = AgentDefaults.MAX_TOKENS,
        )
        return client.complete(config, request).content?.trim().orEmpty()
    }

    /** 渲染为纯文本；思考内容体积大且无长期价值，不计入摘要输入。 */
    private fun renderTranscript(messages: List<ChatMessage>): String = buildString {
        messages.forEach { message ->
            when (message.role) {
                ChatRole.User -> appendLine(ROLE_USER_TAG + message.content)

                ChatRole.Assistant -> {
                    if (message.content.isNotBlank()) {
                        appendLine(ROLE_ASSISTANT_TAG + message.content)
                    }
                    message.toolCalls.forEach { call ->
                        appendLine(TOOL_CALL_TAG + call.name + ARGUMENTS_SEPARATOR + call.arguments)
                    }
                }

                ChatRole.Tool -> appendLine(
                    TOOL_RESULT_PREFIX + (message.toolName ?: "") + TOOL_RESULT_SUFFIX + message.content,
                )

                ChatRole.System -> {
                    // 折叠产生的旧摘要继续参与累加，避免多轮折叠丢失更早的信息。
                    appendLine(ROLE_CONTEXT_TAG + message.content)
                }
            }
        }
    }

    private companion object {
        const val SUMMARY_MESSAGE_ID = "context-folder"
        const val SUMMARY_TEMPERATURE = 0.3
        const val ROLE_USER_TAG = "用户："
        const val ROLE_ASSISTANT_TAG = "助手："
        const val ROLE_CONTEXT_TAG = "既有背景："
        const val TOOL_CALL_TAG = "助手调用工具："
        const val ARGUMENTS_SEPARATOR = " "
        const val TOOL_RESULT_PREFIX = "工具 "
        const val TOOL_RESULT_SUFFIX = " 返回："
        const val FALLBACK_SUMMARY = "（更早的对话内容因长度限制已省略）"

        val SUMMARY_SYSTEM_PROMPT = buildString {
            appendLine("你是一个会话压缩器。请把给定的历史对话压缩成一段简洁的中文摘要，供后续对话直接使用。")
            appendLine("摘要必须保留：")
            appendLine("1. 用户的原始目标与当前进度；")
            appendLine("2. 已经确认的关键结论、数值与文件路径；")
            appendLine("3. 已经执行过的工具调用及其结果要点；")
            appendLine("4. 尚未完成的事项与下一步计划。")
            append("直接输出摘要正文，不要任何前后缀说明。")
        }
    }
}
