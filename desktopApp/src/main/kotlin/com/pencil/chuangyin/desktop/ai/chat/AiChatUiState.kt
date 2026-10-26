package com.pencil.chuangyin.desktop.ai.chat

import com.pencil.chuangyin.desktop.ai.AgentErrorKind
import com.pencil.chuangyin.desktop.ai.AgentPermissionMode
import com.pencil.chuangyin.desktop.ai.ChatMessage
import com.pencil.chuangyin.desktop.ai.ChatRole
import com.pencil.chuangyin.desktop.ai.ToolArtifact
import com.pencil.chuangyin.desktop.ai.ToolArtifactKind
import com.pencil.chuangyin.desktop.ai.tools.ToolNames

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

/**
 * 可展示的运行错误。
 *
 * 安卓端用字符串资源 id 承载主提示，桌面端没有资源系统，直接给中文文案。
 */
data class AiErrorUi(
    /** 面向用户的主提示。 */
    val message: String,
    /** 附加详情（服务端原话 / 异常信息等动态文本）。 */
    val detail: String? = null,
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
    /** 打印机是否已连接（桌面版恒为 false，仅保留字段以对齐安卓信息架构）。 */
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
) {
    /** 是否处于「空会话」状态（用于展示欢迎卡片）。 */
    val isEmpty: Boolean get() = messages.isEmpty() && toolActivities.isEmpty() && !streaming
}

/* ───────────────────────────── 映射 ───────────────────────────── */

/** 工具名 → 界面文案。工具是模型可见的实现细节，统一翻译成动词短语。 */
internal fun toolLabel(toolName: String?): String = when (toolName) {
    ToolNames.LIST_FILES -> "浏览文件"
    ToolNames.READ_FILE -> "读取文件"
    ToolNames.WRITE_FILE -> "写入文件"
    ToolNames.DELETE_FILE -> "删除文件"
    ToolNames.SEARCH_FILES -> "搜索文件"
    ToolNames.PRINT_TEXT -> "打印文字"
    ToolNames.PRINT_IMAGE -> "打印图片"
    ToolNames.PRINT_CONVERSATION -> "打印聊天记录"
    ToolNames.PRINTER_STATUS -> "查询打印机"
    ToolNames.TEST_FEED -> "走纸自测"
    ToolNames.DRAW_IMAGE -> "绘制图片"
    ToolNames.MAKE_DIR -> "新建文件夹"
    ToolNames.COPY_FILE -> "复制文件"
    ToolNames.MOVE_FILE -> "移动文件"
    ToolNames.FILE_INFO -> "查看文件信息"
    ToolNames.EXTRACT_ARCHIVE -> "解压文件"
    ToolNames.CREATE_ARCHIVE -> "打包压缩"
    ToolNames.EDIT_IMAGE -> "处理图片"
    ToolNames.PRINT_TABLE -> "打印表格"
    ToolNames.APP_NAVIGATE -> "跳转页面"
    ToolNames.APP_LIST_DESTINATIONS -> "查询可跳转页面"
    ToolNames.PIXIV_HOME_FEED -> "浏览 P 站首页"
    ToolNames.PIXIV_SEARCH_ILLUST -> "搜索 P 站插画"
    ToolNames.PIXIV_ILLUST_DETAIL -> "查看插画详情"
    ToolNames.PIXIV_DOWNLOAD_ILLUST -> "下载 P 站插画"
    ToolNames.PIXIV_SEARCH_NOVEL -> "搜索 P 站小说"
    ToolNames.PIXIV_DOWNLOAD_NOVEL -> "下载 P 站小说"
    ToolNames.WEB_SEARCH -> "联网搜索"
    ToolNames.WEB_FETCH -> "抓取网页"
    ToolNames.RUN_COMMAND -> "执行终端命令"
    ToolNames.SHELL_STATUS -> "查询终端环境"
    else -> "工具调用"
}

/** 错误类别 → 界面文案。 */
internal fun agentErrorMessage(kind: AgentErrorKind): String = when (kind) {
    AgentErrorKind.MissingConfig -> "尚未配置可用的 AI 模型"
    AgentErrorKind.Network -> "网络连接失败"
    AgentErrorKind.Http -> "服务端返回错误"
    AgentErrorKind.Protocol -> "响应格式异常"
    AgentErrorKind.Tool -> "工具执行失败"
    AgentErrorKind.Cancelled -> "任务已取消"
    AgentErrorKind.Unknown -> "发生未知错误"
}

/** 权限模式 → 标题。 */
internal fun AgentPermissionMode.title(): String = when (this) {
    AgentPermissionMode.ALWAYS_ASK -> "每次审批"
    AgentPermissionMode.OUTSIDE_WORKSPACE_ASK -> "工作区外审批"
    AgentPermissionMode.AUTONOMOUS -> "自主模式（危险）"
}

/** 权限模式 → 说明。 */
internal fun AgentPermissionMode.description(): String = when (this) {
    AgentPermissionMode.ALWAYS_ASK -> "任何会产生副作用的操作都先询问你，最安全但最啰嗦。"
    AgentPermissionMode.OUTSIDE_WORKSPACE_ASK -> "工作区内读写自动放行，涉及工作区外的操作先询问你。"
    AgentPermissionMode.AUTONOMOUS -> "所有操作直接执行，不再询问。仅在完全信任任务内容时使用。"
}

/* ───────────────────────────── 领域 ↔ 界面 ───────────────────────────── */

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

/** 把模型消息还原为界面气泡；[previous] 用于按 id 继承运行期产物。 */
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
