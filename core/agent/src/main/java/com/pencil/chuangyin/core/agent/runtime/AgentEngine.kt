package com.pencil.chuangyin.core.agent.runtime

import com.pencil.chuangyin.core.agent.AgentDefaults
import com.pencil.chuangyin.core.agent.AgentJson
import com.pencil.chuangyin.core.agent.config.AiModelConfig
import com.pencil.chuangyin.core.agent.model.AgentError
import com.pencil.chuangyin.core.agent.model.AgentErrorKind
import com.pencil.chuangyin.core.agent.model.AgentEvent
import com.pencil.chuangyin.core.agent.model.AgentRunResult
import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ChatRequest
import com.pencil.chuangyin.core.agent.model.ChatRole
import com.pencil.chuangyin.core.agent.model.ChatStreamEvent
import com.pencil.chuangyin.core.agent.model.ToolCallRequest
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.model.ToolContext
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.agent.model.ToolPermission
import com.pencil.chuangyin.core.agent.openai.OpenAiChatClient
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Agent 主循环：决策 → 工具调用 → 观察 → 迭代。
 *
 * 每轮向模型发起一次流式补全；若模型请求工具，则执行工具并把结果作为
 * tool 消息回灌，继续下一轮，直到模型给出纯文本回复或达到迭代上限。
 */
@Singleton
class AgentEngine @Inject constructor(
    private val client: OpenAiChatClient,
    private val registry: ToolRegistry,
    private val contextFolder: ContextFolder,
    private val confirmationGates: Set<@JvmSuppressWildcards ToolConfirmationGate>,
    private val permissionPolicy: AgentPermissionPolicy,
) {

    /**
     * 运行一轮任务。
     *
     * @param history 已持久化的会话消息（不含系统提示词）。
     * @param systemPrompt 每轮请求前置于消息列表的系统提示词。
     * @param conversationId 当前会话 ID，透传给工具。
     * @param onEvent 事件回调（在调用方协程上下文中同步触发）。
     */
    suspend fun run(
        config: AiModelConfig,
        systemPrompt: String,
        history: List<ChatMessage>,
        conversationId: String,
        onEvent: (AgentEvent) -> Unit,
    ): AgentRunResult {
        if (!config.isUsable) {
            val error = AgentError(AgentErrorKind.MissingConfig, MISSING_CONFIG_MESSAGE)
            onEvent(AgentEvent.Failed(error, history))
            return AgentRunResult(history, error)
        }

        val working = history.toMutableList()
        var iterations = 0
        while (iterations < AgentDefaults.MAX_TOOL_ITERATIONS) {
            iterations++
            val folded = contextFolder.foldIfNeeded(config, working)
            if (folded !== working) {
                working.clear()
                working.addAll(folded)
            }
            onEvent(AgentEvent.IterationStarted(iterations))

            val assistantId = newId(MESSAGE_ID_PREFIX)
            onEvent(AgentEvent.MessageStarted(assistantId))
            val reasoning = StringBuilder()
            val content = StringBuilder()
            var toolCalls: List<ToolCallRequest> = emptyList()
            var failure: AgentError? = null

            val request = ChatRequest(
                model = config.modelName,
                messages = buildWireMessages(systemPrompt, working),
                tools = registry.specs,
                temperature = AgentDefaults.TEMPERATURE,
                maxTokens = AgentDefaults.MAX_TOKENS,
            )
            client.stream(config, request).collect { event ->
                when (event) {
                    is ChatStreamEvent.ReasoningDelta -> {
                        reasoning.append(event.text)
                        onEvent(AgentEvent.ReasoningDelta(assistantId, event.text))
                    }

                    is ChatStreamEvent.ContentDelta -> {
                        content.append(event.text)
                        onEvent(AgentEvent.ContentDelta(assistantId, event.text))
                    }

                    is ChatStreamEvent.ToolCallsReady -> toolCalls = event.calls

                    is ChatStreamEvent.Failed -> failure = event.error

                    ChatStreamEvent.Completed -> Unit
                }
            }

            val assistant = ChatMessage(
                id = assistantId,
                role = ChatRole.Assistant,
                content = content.toString(),
                reasoning = reasoning.toString().ifBlank { null },
                toolCalls = toolCalls,
                createdAt = System.currentTimeMillis(),
                isError = failure != null,
            )
            working += assistant
            onEvent(AgentEvent.MessageCompleted(assistant))

            val streamFailure = failure
            if (streamFailure != null) {
                onEvent(AgentEvent.Failed(streamFailure, working.toList()))
                return AgentRunResult(working.toList(), streamFailure, iterations)
            }
            if (toolCalls.isEmpty()) {
                // 既无正文也无工具调用，视为异常响应，避免界面出现空白气泡。
                val terminal = if (assistant.content.isBlank() && assistant.reasoning.isNullOrBlank()) {
                    AgentError(AgentErrorKind.Protocol, EMPTY_REPLY_MESSAGE)
                } else {
                    null
                }
                if (terminal != null) {
                    onEvent(AgentEvent.Failed(terminal, working.toList()))
                    return AgentRunResult(working.toList(), terminal, iterations)
                }
                onEvent(AgentEvent.Finished(working.toList()))
                return AgentRunResult(working.toList(), null, iterations)
            }

            toolCalls.forEach { call ->
                working += executeTool(call, conversationId, working.toList(), onEvent)
            }
        }

        val error = AgentError(AgentErrorKind.Tool, MAX_ITERATIONS_MESSAGE)
        onEvent(AgentEvent.Failed(error, working.toList()))
        return AgentRunResult(working.toList(), error, iterations)
    }

    /** 把系统提示词前置于会话消息；历史中已有的折叠摘要保持原样。 */
    private fun buildWireMessages(
        systemPrompt: String,
        history: List<ChatMessage>,
    ): List<ChatMessage> {
        if (systemPrompt.isBlank()) return history
        return buildList {
            add(
                ChatMessage(
                    id = SYSTEM_MESSAGE_ID,
                    role = ChatRole.System,
                    content = systemPrompt,
                ),
            )
            addAll(history)
        }
    }

    /**
     * 执行一次工具调用，返回需追加到会话的消息序列。
     *
     * 常规情况下只返回一条 tool 结果消息。若工具产出了图片，则额外返回一条隐藏的
     * 用户消息承载图片：OpenAI 协议的 tool 消息无法携带图片，而图片只有作为
     * `image_url` 内容才能被模型「看到」。该消息不发送 [AgentEvent.MessageCompleted]，
     * 因此不会进入界面，也不会被落库，仅在本次运行内供后续迭代使用。
     */
    private suspend fun executeTool(
        call: ToolCallRequest,
        conversationId: String,
        transcript: List<ChatMessage>,
        onEvent: (AgentEvent) -> Unit,
    ): List<ChatMessage> {
        onEvent(AgentEvent.ToolStarted(call.id, call.name, call.arguments))
        val tool = registry.find(call.name)
        val output = if (tool == null) {
            ToolOutput(UNKNOWN_TOOL_PREFIX + call.name, isError = true)
        } else if (!isApproved(tool.spec.permission, call, transcript)) {
            // 当前权限模式下该操作未被放行：不执行工具函数，把确认指引回灌给模型，由它向用户请求确认。
            ToolOutput(CONFIRMATION_DENIED_MESSAGE, isError = true)
        } else {
            val context = object : ToolContext {
                override val conversationId: String = conversationId

                override suspend fun transcript(): List<ChatMessage> = transcript

                override fun reportProgress(text: String) {
                    onEvent(AgentEvent.ToolProgress(call.id, text))
                }
            }
            try {
                tool.invoke(parseArguments(call.arguments), context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToolOutput(TOOL_FAILED_PREFIX + (e.message ?: e.javaClass.simpleName), isError = true)
            }
        }

        output.artifacts.forEach { onEvent(AgentEvent.ArtifactCreated(it)) }

        val resultMessage = ChatMessage(
            id = newId(MESSAGE_ID_PREFIX),
            role = ChatRole.Tool,
            content = output.content.take(AgentDefaults.TOOL_RESULT_MAX_CHARS),
            toolCallId = call.id,
            toolName = call.name,
            createdAt = System.currentTimeMillis(),
            isError = output.isError,
        )
        onEvent(AgentEvent.MessageCompleted(resultMessage))

        val images = output.artifacts.filter { it.kind == ToolArtifactKind.IMAGE }
        if (output.isError || images.isEmpty()) return listOf(resultMessage)
        val imageMessage = ChatMessage(
            id = newId(MESSAGE_ID_PREFIX),
            role = ChatRole.User,
            content = IMAGE_CONTEXT_PROMPT,
            attachments = images,
            createdAt = System.currentTimeMillis(),
            synthetic = true,
        )
        return listOf(resultMessage, imageMessage)
    }

    /**
     * 按当前权限模式与确认门判定一次工具调用是否放行。
     *
     * 只读工具永不拦截；需要确认时逐一询问确认门，且必须至少存在一个确认门
     * （否则视为「无法完成确认」而拒绝，避免在无门环境下静默放行危险操作）。
     */
    private suspend fun isApproved(
        permission: ToolPermission,
        call: ToolCallRequest,
        transcript: List<ChatMessage>,
    ): Boolean {
        if (!permissionPolicy.requiresConfirmation(permission)) return true
        if (confirmationGates.isEmpty()) return false
        return confirmationGates.all { gate ->
            gate.requestConfirmation(call.name, parseArguments(call.arguments), transcript)
        }
    }

    private fun parseArguments(raw: String): JsonObject {
        if (raw.isBlank()) return buildJsonObject { }
        return try {
            AgentJson.parseToJsonElement(raw) as? JsonObject ?: buildJsonObject { }
        } catch (e: SerializationException) {
            buildJsonObject { }
        }
    }

    private fun newId(prefix: String): String = prefix + UUID.randomUUID()

    private companion object {
        const val MESSAGE_ID_PREFIX = "msg_"
        const val SYSTEM_MESSAGE_ID = "system-prompt"
        const val MISSING_CONFIG_MESSAGE = "尚未配置可用的 AI 模型"
        const val EMPTY_REPLY_MESSAGE = "模型返回了空回复"
        const val MAX_ITERATIONS_MESSAGE = "已达到单轮最大工具调用次数"
        const val UNKNOWN_TOOL_PREFIX = "未知工具："
        const val TOOL_FAILED_PREFIX = "工具执行失败："
        const val IMAGE_CONTEXT_PROMPT = "上方工具已产出图片，请直接查看并理解图片内容（它已作为多模态图片随本消息提供），结合图片回答或继续操作。"
        const val CONFIRMATION_DENIED_MESSAGE =
            "该工具属于不可逆操作，已触发用户确认流程，本次调用被拦截（未执行）。" +
                "请用一句话向用户说明将要执行的具体操作与影响，明确请求用户回复确认；本轮请勿再调用该工具。" +
                "用户回复同意后，下一轮再次以相同参数调用本工具即可放行执行；若用户拒绝或转移话题，不要再调用。"
    }
}
