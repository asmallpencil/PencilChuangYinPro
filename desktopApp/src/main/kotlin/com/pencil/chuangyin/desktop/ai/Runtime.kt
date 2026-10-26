package com.pencil.chuangyin.desktop.ai

import com.pencil.chuangyin.desktop.support.AiModelConfig
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/* ───────────────────────────── 权限模式 ───────────────────────────── */

/**
 * 权限模式：决定 Agent 在无人干预的情况下能做多少事。
 *
 * 由用户在聊天界面输入框旁切换，引擎每执行一个工具前按最新模式实时判定。
 */
enum class AgentPermissionMode {
    /** 每次审批：任何会产生副作用的操作都需用户确认。 */
    ALWAYS_ASK,

    /** 工作区外需审批：工作区内读写自动放行，危险操作需确认。 */
    OUTSIDE_WORKSPACE_ASK,

    /** 自主模式（危险）：所有操作无需确认。 */
    AUTONOMOUS,
}

/** 当前权限模式的可观测来源。 */
interface AgentPermissionModeProvider {
    val mode: StateFlow<AgentPermissionMode>
}

/** 进程内可变的权限模式持有者；持久化与界面入口由聊天层驱动。 */
class MutableAgentPermissionModeProvider : AgentPermissionModeProvider {

    private val _mode = MutableStateFlow(AgentPermissionMode.OUTSIDE_WORKSPACE_ASK)
    override val mode: StateFlow<AgentPermissionMode> = _mode.asStateFlow()

    /** 更新当前模式（用户切换或启动回填时调用）。 */
    fun set(value: AgentPermissionMode) {
        _mode.value = value
    }
}

/**
 * 按权限模式判定一次工具调用是否需要用户确认。
 *
 * - [AgentPermissionMode.ALWAYS_ASK]：除只读工具外一律需确认；
 * - [AgentPermissionMode.OUTSIDE_WORKSPACE_ASK]：仅工作区外（[ToolPermission.DANGEROUS]）需确认；
 * - [AgentPermissionMode.AUTONOMOUS]：一律放行。
 */
class AgentPermissionPolicy(
    private val provider: AgentPermissionModeProvider,
) {

    /** 该工具在当前模式下是否需要先取得用户确认。 */
    fun requiresConfirmation(permission: ToolPermission): Boolean = when (provider.mode.value) {
        AgentPermissionMode.ALWAYS_ASK -> permission != ToolPermission.READ
        AgentPermissionMode.OUTSIDE_WORKSPACE_ASK -> permission == ToolPermission.DANGEROUS
        AgentPermissionMode.AUTONOMOUS -> false
    }
}

/* ───────────────────────────── 确认门与注册表 ───────────────────────────── */

/**
 * 不可逆工具执行前的用户确认门。
 *
 * [AgentEngine] 在调用需要确认的工具前逐一询问，任一门拒绝即拦截本次调用
 * （不会执行工具函数），并把拦截原因作为工具结果回灌给模型。
 */
interface ToolConfirmationGate {

    /**
     * 询问是否放行一次工具调用。
     *
     * @return true 表示放行执行；false 表示拦截。
     */
    suspend fun requestConfirmation(toolName: String, arguments: JsonObject, transcript: List<ChatMessage>): Boolean
}

/** 已注册工具的索引。 */
class ToolRegistry(tools: Set<AgentTool>) {

    private val byName: Map<String, AgentTool> = tools.associateBy { it.spec.name }

    /** 按名称升序排列的工具声明，保证请求体稳定可缓存。 */
    val specs: List<ToolSpec> = tools.map { it.spec }.sortedBy { it.name }

    /** 已注册工具数量。 */
    val size: Int get() = byName.size

    /** 按名称查找工具。 */
    fun find(name: String): AgentTool? = byName[name]
}

/* ───────────────────────────── 上下文折叠 ───────────────────────────── */

/**
 * 自动上下文折叠：会话体积超过阈值时，把较早的消息交给模型压缩成一条摘要系统消息。
 */
class ContextFolder(
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

/* ───────────────────────────── 主循环 ───────────────────────────── */

/**
 * Agent 主循环：决策 → 工具调用 → 观察 → 迭代。
 *
 * 每轮向模型发起一次流式补全；若模型请求工具，则执行工具并把结果作为
 * tool 消息回灌，继续下一轮，直到模型给出纯文本回复或达到迭代上限。
 */
class AgentEngine(
    private val client: OpenAiChatClient,
    private val registry: ToolRegistry,
    private val contextFolder: ContextFolder,
    private val confirmationGates: List<ToolConfirmationGate>,
    private val permissionPolicy: AgentPermissionPolicy,
) {

    /**
     * 运行一轮任务。
     *
     * @param history 已持久化的会话消息（不含系统提示词）。
     * @param systemPrompt 每轮请求前置于消息列表的系统提示词。
     * @param conversationId 当前会话 ID，透传给工具。
     * @param onEvent 事件回调。
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
     * 若工具产出了图片，则额外返回一条隐藏的用户消息承载图片：OpenAI 协议的
     * tool 消息无法携带图片，而图片只有作为 `image_url` 内容才能被模型「看到」。
     * 该消息不发送 [AgentEvent.MessageCompleted]，因此不会进入界面，也不会被落库。
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
            // 当前权限模式下该操作未被放行：不执行工具函数，把确认指引回灌给模型。
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
