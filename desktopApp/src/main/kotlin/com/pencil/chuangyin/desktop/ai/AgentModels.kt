package com.pencil.chuangyin.desktop.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/* ───────────────────────────── 会话角色与消息 ───────────────────────────── */

/** 会话角色。 */
@Serializable
enum class ChatRole {
    @SerialName("system")
    System,

    @SerialName("user")
    User,

    @SerialName("assistant")
    Assistant,

    @SerialName("tool")
    Tool,
    ;

    /** OpenAI 线上协议使用的角色名。 */
    val wireName: String get() = name.lowercase()
}

/** 模型发起的一次工具调用。 */
@Serializable
data class ToolCallRequest(
    val id: String,
    val name: String,
    val arguments: String = EMPTY_ARGUMENTS,
) {
    companion object {
        const val EMPTY_ARGUMENTS = "{}"
    }
}

/** 工具产物的类型常量。 */
object ToolArtifactKind {
    const val IMAGE = "image"
    const val FILE = "file"
}

/** 工具执行产生的可展示产物。 */
@Serializable
data class ToolArtifact(
    val id: String,
    val kind: String,
    val path: String,
    val label: String? = null,
)

/** 工具执行结果。 */
data class ToolOutput(
    val content: String,
    val isError: Boolean = false,
    val artifacts: List<ToolArtifact> = emptyList(),
)

/**
 * 会话中的一条消息。
 *
 * - [reasoning]：深度思考内容，仅用于界面展示，不回灌模型。
 * - [toolCalls]：助手消息发起的工具调用。
 * - [toolCallId]/[toolName]：角色为 [ChatRole.Tool] 时，对应被执行的工具调用。
 * - [isSummary]：标记该条为上下文折叠产生的历史摘要。
 * - [attachments]：随消息携带的图片/文件，图片会以多模态 `image_url` 形式随请求发送。
 */
@Serializable
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String = "",
    val reasoning: String? = null,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val createdAt: Long = 0L,
    val isError: Boolean = false,
    val isSummary: Boolean = false,
    val attachments: List<ToolArtifact> = emptyList(),
    /** 引擎注入的合成消息（如工具图片的多模态载体），并非真实用户输入。 */
    val synthetic: Boolean = false,
) {
    /** 用于上下文体积估算的字符数。 */
    val estimatedChars: Int
        get() = content.length + (reasoning?.length ?: 0) +
            toolCalls.sumOf { it.name.length + it.arguments.length }
}

/* ───────────────────────────── 工具抽象 ───────────────────────────── */

/**
 * 工具的作用范围与风险等级。
 *
 * 引擎结合当前权限模式动态决定是否拦截，因此每个工具只需声明自己触碰的边界。
 */
enum class ToolPermission {
    /** 只读、无副作用。 */
    READ,

    /** 仅在 agent 工作区内产生副作用。 */
    WORKSPACE,

    /** 工作区之外的对外副作用或不可逆操作。 */
    DANGEROUS,
}

/** OpenAI function calling 的工具声明。 */
data class ToolSpec(
    val name: String,
    val description: String,
    /** JSON Schema 形式的参数声明。 */
    val parameters: JsonObject,
    /** 工具触碰的边界。 */
    val permission: ToolPermission = ToolPermission.READ,
)

/** 工具执行上下文：由 Agent 循环注入，向工具暴露会话与进度上报能力。 */
interface ToolContext {
    /** 当前会话 ID。 */
    val conversationId: String

    /** 当前会话的完整消息（含本次运行中新增的内容）。 */
    suspend fun transcript(): List<ChatMessage>

    /** 向界面汇报阶段性进度。 */
    fun reportProgress(text: String)
}

/** Agent 可调用的工具。 */
interface AgentTool {
    /** 工具声明。 */
    val spec: ToolSpec

    /**
     * 执行工具。
     *
     * @param arguments 模型给出的 JSON 参数，始终为对象。
     * @return 执行结果；失败时返回 [ToolOutput.isError] 为 true 的结果而非抛异常。
     */
    suspend fun invoke(arguments: JsonObject, context: ToolContext): ToolOutput
}

/* ───────────────────────────── 错误与事件 ───────────────────────────── */

/** 错误类别。 */
enum class AgentErrorKind {
    MissingConfig,
    Network,
    Http,
    Protocol,
    Tool,
    Cancelled,
    Unknown,
}

/** Agent 运行错误。 */
data class AgentError(
    val kind: AgentErrorKind,
    val message: String,
    val detail: String? = null,
)

/** 一次对话补全请求。 */
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolSpec> = emptyList(),
    val temperature: Double? = null,
    val maxTokens: Int? = null,
)

/** 流式补全事件。 */
sealed interface ChatStreamEvent {
    data class ReasoningDelta(val text: String) : ChatStreamEvent
    data class ContentDelta(val text: String) : ChatStreamEvent
    data class ToolCallsReady(val calls: List<ToolCallRequest>) : ChatStreamEvent
    data class Failed(val error: AgentError) : ChatStreamEvent
    data object Completed : ChatStreamEvent
}

/** Agent 循环向界面推送的事件。 */
sealed interface AgentEvent {
    data class IterationStarted(val iteration: Int) : AgentEvent
    data class MessageStarted(val messageId: String) : AgentEvent
    data class ReasoningDelta(val messageId: String, val text: String) : AgentEvent
    data class ContentDelta(val messageId: String, val text: String) : AgentEvent
    data class MessageCompleted(val message: ChatMessage) : AgentEvent
    data class ToolStarted(
        val toolCallId: String,
        val toolName: String,
        val arguments: String,
    ) : AgentEvent

    data class ToolProgress(val toolCallId: String, val text: String) : AgentEvent
    data class ArtifactCreated(val artifact: ToolArtifact) : AgentEvent
    data class Finished(val history: List<ChatMessage>) : AgentEvent
    data class Failed(val error: AgentError, val history: List<ChatMessage>) : AgentEvent
}

/** Agent 单轮运行结果。 */
data class AgentRunResult(
    val history: List<ChatMessage>,
    val error: AgentError? = null,
    val iterations: Int = 0,
)
