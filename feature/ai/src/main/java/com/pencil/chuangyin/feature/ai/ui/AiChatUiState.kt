package com.pencil.chuangyin.feature.ai.ui

import androidx.annotation.StringRes
import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ChatRole
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.model.ToolPermission
import com.pencil.chuangyin.core.agent.runtime.AgentPermissionMode
import com.pencil.chuangyin.feature.ai.data.ConversationSummary

/** 界面消息角色：在 [ChatRole] 基础上拆出错误气泡，便于分别渲染。 */
enum class AiMessageRole {
    /** 用户输入。 */
    User,

    /** 助手正文（可含深度思考）。 */
    Assistant,

    /** 工具执行结果。 */
    Tool,

    /** 失败的助手回复。 */
    Error,
}

/** 可展示产物（图片 / 文件）。 */
data class AiUiArtifact(
    val id: String,
    val kind: String,
    /** 本地绝对路径。 */
    val path: String,
    val label: String?,
) {
    /** 是否为可直接预览的图片。 */
    val isImage: Boolean get() = kind == ToolArtifactKind.IMAGE
}

/** 一条界面气泡。 */
data class AiUiMessage(
    val id: String,
    val role: AiMessageRole,
    val content: String = "",
    /** 深度思考内容；为空表示该条没有思考过程。 */
    val reasoning: String? = null,
    /** 是否仍在流式输出。 */
    val isStreaming: Boolean = false,
    /** 工具类消息对应的工具名。 */
    val toolName: String? = null,
    val toolCallId: String? = null,
    val artifacts: List<AiUiArtifact> = emptyList(),
    val isError: Boolean = false,
    val createdAt: Long = 0L,
)

/** 正在执行的工具活动（执行期间展示，完成后并入消息列表）。 */
data class AiToolActivity(
    val toolCallId: String,
    val toolName: String,
    val arguments: String = "",
    /** 工具上报的阶段性进度。 */
    val progress: String? = null,
)

/** 可展示的运行错误。 */
data class AiErrorUi(
    /** 面向用户的主提示（资源文案 id，由界面解析）。 */
    @StringRes val messageRes: Int,
    /** 附加详情（服务端原话 / 异常信息等动态文本）。 */
    val detail: String? = null,
)

/** 顶栏模型快捷切换菜单中的一项。 */
data class AiModelOption(
    val id: String,
    val label: String,
)

/** 等待用户点「允许 / 拒绝」的危险工具调用。 */
data class AiApprovalUi(
    val id: String,
    val toolName: String,
    /** 模型给出的原始参数 JSON。 */
    val arguments: String,
    val permission: ToolPermission,
)

/** AI 聊天任务页的完整界面状态。 */
data class AiChatUiState(
    val conversationId: String = "",
    /** 会话标题（首条用户消息派生）。 */
    val title: String = "",
    val messages: List<AiUiMessage> = emptyList(),
    /** 是否有任务正在运行。 */
    val streaming: Boolean = false,
    /** 运行中的工具活动。 */
    val toolActivities: List<AiToolActivity> = emptyList(),
    val error: AiErrorUi? = null,
    /** 输入框内容。 */
    val input: String = "",
    /** 输入框上方挂载区中待发送的附件（随下一条消息一起发出）。 */
    val attachments: List<AiUiArtifact> = emptyList(),
    /** 是否存在可用模型配置。 */
    val hasUsableModel: Boolean = false,
    /** 当前模型显示名（用于副标题）。 */
    val modelDisplayName: String = "",
    /** 已配置的全部模型（用于顶栏快捷切换）。 */
    val models: List<AiModelOption> = emptyList(),
    /** 当前选中模型 id。 */
    val activeModelId: String = "",
    /** 打印机是否已连接。 */
    val printerConnected: Boolean = false,
    /** 已连接打印机名称。 */
    val printerName: String = "",
    /** 历史会话抽屉是否展开。 */
    val historyVisible: Boolean = false,
    val summaries: List<ConversationSummary> = emptyList(),
    /** 是否有可重试的上一轮任务。 */
    val canRetry: Boolean = false,
    /** 当前工具审批模式。 */
    val permissionMode: AgentPermissionMode = AgentPermissionMode.OUTSIDE_WORKSPACE_ASK,
    /** 等待用户批准的敏感工具调用；非空时聊天页弹出审批对话框。 */
    val pendingApproval: AiApprovalUi? = null,
) {
    /** 是否处于「空会话」状态（用于展示欢迎卡片）。 */
    val isEmpty: Boolean get() = messages.isEmpty() && toolActivities.isEmpty() && !streaming
}

/** 将领域产物转为界面产物。 */
internal fun ToolArtifact.toUi(): AiUiArtifact = AiUiArtifact(
    id = id,
    kind = kind,
    path = path,
    label = label,
)

/** 角色映射：领域角色 → 界面角色（错误回复单独成类，便于渲染红色气泡）。 */
private fun ChatRole.toUiRole(isError: Boolean): AiMessageRole = when {
    this == ChatRole.User -> AiMessageRole.User
    this == ChatRole.Tool -> AiMessageRole.Tool
    isError -> AiMessageRole.Error
    else -> AiMessageRole.Assistant
}

/**
 * 把单条领域消息还原为界面气泡。
 *
 * @param previous 同 id 的既有气泡；用于继承运行期产物（工具产物不落库，重新载入会话时会丢失）。
 * @param isStreaming 是否仍处于流式输出中。
 * @param artifacts 显式指定的工具产物；默认沿用 [previous] 的产物。
 *
 * 用户消息的附件持久化在消息内，始终以 [ChatMessage.attachments] 为准渲染，
 * 避免「既有气泡产物 + 持久化附件」重复展示。
 */
internal fun ChatMessage.toUiMessage(
    previous: AiUiMessage? = null,
    isStreaming: Boolean = false,
    artifacts: List<AiUiArtifact> = previous?.artifacts.orEmpty(),
): AiUiMessage = AiUiMessage(
    id = id,
    role = role.toUiRole(isError),
    content = content,
    reasoning = reasoning,
    isStreaming = isStreaming,
    toolName = toolName,
    toolCallId = toolCallId,
    artifacts = if (role == ChatRole.User) attachments.map { it.toUi() } else artifacts,
    isError = isError,
    createdAt = createdAt,
)

/**
 * 把模型消息还原为界面气泡。
 *
 * [previous] 用于按 id 继承运行期产物（产物不落库，重新载入会话时不再保留）。
 */
internal fun List<ChatMessage>.toUiMessages(previous: List<AiUiMessage> = emptyList()): List<AiUiMessage> =
    map { message -> message.toUiMessage(previous = previous.firstOrNull { it.id == message.id }) }

/** 按 id 就地替换气泡；id 不存在时追加到末尾，保证事件乱序时也不会丢消息。 */
internal fun List<AiUiMessage>.upsertMessage(message: AiUiMessage): List<AiUiMessage> =
    if (any { it.id == message.id }) map { if (it.id == message.id) message else it } else this + message

/** 按 id 变换气泡内容；id 不存在时保持原列表不变。 */
internal fun List<AiUiMessage>.updateMessage(
    id: String,
    transform: (AiUiMessage) -> AiUiMessage,
): List<AiUiMessage> = map { if (it.id == id) transform(it) else it }

/** 由界面气泡还原领域消息（用于取消/中断时保留已完成内容）。 */
internal fun AiUiMessage.toChatMessage(): ChatMessage = ChatMessage(
    id = id,
    role = when (role) {
        AiMessageRole.User -> ChatRole.User
        AiMessageRole.Tool -> ChatRole.Tool
        AiMessageRole.Assistant, AiMessageRole.Error -> ChatRole.Assistant
    },
    content = content,
    reasoning = reasoning,
    toolCallId = toolCallId,
    toolName = toolName,
    createdAt = createdAt,
    isError = isError,
)
