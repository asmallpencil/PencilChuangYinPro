package com.pencil.chuangyin.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.ai.AgentPermissionMode
import com.pencil.chuangyin.desktop.ai.chat.AiChatStore
import com.pencil.chuangyin.desktop.ai.chat.AiChatUiState
import com.pencil.chuangyin.desktop.ai.chat.AiErrorUi
import com.pencil.chuangyin.desktop.ai.chat.AiMessageRole
import com.pencil.chuangyin.desktop.ai.chat.AiToolActivity
import com.pencil.chuangyin.desktop.ai.chat.AiUiArtifact
import com.pencil.chuangyin.desktop.ai.chat.AiUiMessage
import com.pencil.chuangyin.desktop.ai.chat.ConversationSummary
import com.pencil.chuangyin.desktop.ai.chat.description
import com.pencil.chuangyin.desktop.ai.chat.title
import com.pencil.chuangyin.desktop.ai.chat.toolLabel
import com.pencil.chuangyin.desktop.ai.tools.ToolNames
import com.pencil.chuangyin.desktop.glass.GlassPanel
import com.pencil.chuangyin.desktop.glass.glassSurface
import com.pencil.chuangyin.desktop.support.AiConfigStore
import com.pencil.chuangyin.desktop.support.AiModelConfig
import com.pencil.chuangyin.desktop.support.rememberLocalFileImage
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import com.pencil.chuangyin.desktop.ui.motion.DesktopLoadingIndicator
import com.pencil.chuangyin.desktop.ui.motion.animateItemMotion
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.launch

// 尺寸与安卓端 feature/ai/res/values/dimens.xml 逐项对齐。
private val BubbleMaxWidth = 300.dp
private val BubblePadding = 12.dp
private val MessageGap = 12.dp
private val ReasoningMaxHeight = 220.dp
private val ArtifactSize = 160.dp
private val InputMaxHeight = 140.dp
private val AttachThumbSize = 40.dp
private val AttachLabelWidth = 120.dp
private val InlineIconSize = 18.dp
private val HistoryMaxHeight = 360.dp

/** 底部面板最大宽度：桌面窗口很宽时避免表单被拉成长条（对应安卓端的 ModalBottomSheet 观感）。 */
private val SheetMaxWidth = 760.dp

/** 工具结果超出该字符数时默认折叠，避免整份文件内容刷屏。 */
private const val TOOL_RESULT_COLLAPSE_CHARS = 240

/** 工具结果折叠时展示的最大行数。 */
private const val TOOL_RESULT_COLLAPSED_LINES = 8

/** 工具进度提示最多展示的行数。 */
private const val TOOL_PROGRESS_MAX_LINES = 2

/** 列表 key 前缀：气泡与工具活动共用同一列表，前缀保证 key 不冲突。 */
private const val MESSAGE_KEY_PREFIX = "m:"
private const val TOOL_KEY_PREFIX = "t:"
private const val KEY_WELCOME = "welcome"
private const val KEY_ERROR = "error"

private val IMAGE_EXTENSIONS = arrayOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

/**
 * 「AI 打印」任务页（桌面版）—— 结构 1:1 对齐安卓端 `feature/ai/ui/AiChatScreen.kt`：
 *   顶栏（标题 + 当前模型，右侧历史/新建）
 *   └ 消息列表（欢迎卡 / 气泡 / 工具活动 / 产物 / 错误卡）
 *   └ 底部输入栏（挂载区 + 审批模式 + 上传 + 输入框 + 发送/停止）
 *   以及历史会话、上传方式、审批模式三个底部面板 + 删除确认弹窗。
 *
 * 视觉与安卓端对标：顶栏使用真玻璃 [GlassTopAppBar]，气泡/输入栏/面板使用同一套 [glassSurface] 面料。
 * 桌面端不存在热敏打印机通道，因此不渲染安卓端的「打印机未连接」提示行。
 */
@Composable
fun AiChatPage(onBack: () -> Unit, onOpenConfig: () -> Unit) {
    AiChatStore.start()
    val state by AiChatStore.state.collectAsState()
    val configState by AiConfigStore.state.collectAsState()
    AiChatScreen(
        state = state,
        models = configState.models,
        activeModelId = configState.activeModelId,
        onSelectModel = { id -> AiConfigStore.setActive(id) },
        onBack = onBack,
        onOpenConfig = onOpenConfig,
        onInputChange = AiChatStore::onInputChange,
        onSend = { AiChatStore.send() },
        onStop = { AiChatStore.stop() },
        onRetry = { AiChatStore.retry() },
        onNewConversation = { AiChatStore.startNewConversation() },
        onHistoryToggle = { AiChatStore.toggleHistory() },
        onHistoryDismiss = { AiChatStore.hideHistory() },
        onOpenConversation = { id -> AiChatStore.openConversation(id) },
        onDeleteConversation = { id -> AiChatStore.deleteConversation(id) },
        onDismissError = { AiChatStore.dismissError() },
        onImportFile = { file, report ->
            AiChatStore.importFromFile(
                file = file,
                onImported = { path -> report("已上传到 $path") },
                onFailed = { message -> report("上传失败：$message") },
            )
        },
        onRemoveAttachment = { id -> AiChatStore.removeAttachment(id) },
        onSetPermissionMode = { mode -> AiChatStore.setPermissionMode(mode) },
    )
}

@Composable
fun AiChatScreen(
    state: AiChatUiState,
    models: List<AiModelConfig>,
    activeModelId: String?,
    onSelectModel: (String) -> Unit,
    onBack: () -> Unit,
    onOpenConfig: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onNewConversation: () -> Unit,
    onHistoryToggle: () -> Unit,
    onHistoryDismiss: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDismissError: () -> Unit,
    onImportFile: (File, (String) -> Unit) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSetPermissionMode: (AgentPermissionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 待删除会话：不直接落库，先经确认弹窗，避免误触删除。
    var pendingDelete by remember { mutableStateOf<ConversationSummary?>(null) }
    // 上传入口：先让用户选择「图片 / 任意文件」，再各自拉起系统文件选择器。
    var attachSheetVisible by remember { mutableStateOf(false) }
    // 审批模式入口：与上传同级，点开后选择审批粒度。
    var permissionSheetVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reportImport: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                GlassTopAppBar(
                    title = "AI 打印",
                    subtitle = if (state.hasUsableModel && state.modelDisplayName.isNotBlank()) {
                        "当前模型：${state.modelDisplayName}"
                    } else {
                        "尚未配置模型"
                    },
                    navigationIcon = {
                        AppIconButton(icon = AppIcon.Back, contentDescription = "返回", onClick = onBack)
                    },
                    actions = {
                        // 模型快捷切换：与安卓端一致，会话中途即可换模型，不必退回配置页。
                        if (models.isNotEmpty()) {
                            var modelMenuVisible by remember { mutableStateOf(false) }
                            Box {
                                AppIconButton(
                                    icon = AppIcon.SwapHoriz,
                                    contentDescription = "切换模型",
                                    onClick = { modelMenuVisible = true },
                                )
                                DropdownMenu(
                                    expanded = modelMenuVisible,
                                    onDismissRequest = { modelMenuVisible = false },
                                ) {
                                    models.forEach { model ->
                                        val isActive = model.id == activeModelId ||
                                            (activeModelId == null && model == models.first())
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = model.displayName.ifBlank { model.modelName },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            trailingIcon = if (isActive) {
                                                {
                                                    AppIconView(
                                                        icon = AppIcon.Check,
                                                        size = 18.dp,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                modelMenuVisible = false
                                                if (!isActive) onSelectModel(model.id)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        AppIconButton(
                            icon = AppIcon.History,
                            contentDescription = "历史对话",
                            onClick = onHistoryToggle,
                        )
                        AppIconButton(
                            icon = AppIcon.Add,
                            contentDescription = "新建对话",
                            onClick = onNewConversation,
                        )
                    },
                )
            },
            bottomBar = {
                if (state.hasUsableModel) {
                    AiInputBar(
                        value = state.input,
                        attachments = state.attachments,
                        streaming = state.streaming,
                        permissionMode = state.permissionMode,
                        onValueChange = onInputChange,
                        onSend = onSend,
                        onStop = onStop,
                        onAttach = { attachSheetVisible = true },
                        onPermissionClick = { permissionSheetVisible = true },
                        onRemoveAttachment = onRemoveAttachment,
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    // 只避开顶栏：底部留白转交列表尾部，使内容可以绘制到底栏玻璃之后。
                    .padding(top = innerPadding.calculateTopPadding())
                    .fillMaxSize(),
            ) {
                if (!state.hasUsableModel) {
                    AiNoModelGuide(
                        onOpenConfig = onOpenConfig,
                        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                    )
                } else {
                    AiConversationList(
                        state = state,
                        bottomInset = innerPadding.calculateBottomPadding(),
                        onRetry = onRetry,
                        onDismissError = onDismissError,
                    )
                }
            }
        }

        if (state.historyVisible) {
            AiSheet(onDismissRequest = onHistoryDismiss) {
                AiHistorySheet(
                    summaries = state.summaries,
                    currentId = state.conversationId,
                    onNewConversation = onNewConversation,
                    onOpen = onOpenConversation,
                    onDelete = { pendingDelete = it },
                )
            }
        }

        if (attachSheetVisible) {
            AiSheet(onDismissRequest = { attachSheetVisible = false }) {
                AiAttachSheet(
                    onPickImage = {
                        attachSheetVisible = false
                        chooseFile(imagesOnly = true)?.let { file -> onImportFile(file, reportImport) }
                    },
                    onPickFile = {
                        attachSheetVisible = false
                        chooseFile(imagesOnly = false)?.let { file -> onImportFile(file, reportImport) }
                    },
                )
            }
        }

        if (permissionSheetVisible) {
            AiSheet(onDismissRequest = { permissionSheetVisible = false }) {
                AiPermissionSheet(
                    current = state.permissionMode,
                    onSelect = { mode ->
                        permissionSheetVisible = false
                        onSetPermissionMode(mode)
                    },
                )
            }
        }
    }

    pendingDelete?.let { summary ->
        val title = summary.title.ifBlank { "未命名对话" }
        AppLiquidDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除对话",
            message = "删除后无法恢复，确定要删除「$title」吗？",
            confirmLabel = "删除",
            onConfirm = {
                pendingDelete = null
                onDeleteConversation(summary.id)
            },
            dismissLabel = "取消",
        )
    }
}

/** 系统文件选择器：图片按扩展名过滤，任意文件不过滤。 */
private fun chooseFile(imagesOnly: Boolean): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = if (imagesOnly) "选择图片" else "选择文件"
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = false
        if (imagesOnly) fileFilter = FileNameExtensionFilter("图片", *IMAGE_EXTENSIONS)
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/**
 * 底部面板：桌面端以「遮罩 + 底部玻璃面板」替代安卓端 ModalBottomSheet，
 * 内容与层级与安卓端一致，仅容器形态适配桌面窗口。
 */
@Composable
private fun AiSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = SheetMaxWidth),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            elevation = 8f,
            fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = content,
        )
    }
}

/** 消息列表：欢迎卡 / 气泡 / 工具活动 / 错误卡。 */
@Composable
private fun AiConversationList(
    state: AiChatUiState,
    bottomInset: Dp,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 纯工具调用载体的空助手消息不渲染，避免出现空白气泡。
    val messages = remember(state.messages) { state.messages.filter { it.isRenderable() } }
    val showWelcome = messages.isEmpty() && state.toolActivities.isEmpty()

    // 切换会话时重建列表状态：否则旧会话的滚动位置与退出动画中的旧项会残留在背景里。
    val listState = remember(state.conversationId) { LazyListState() }
    val itemCount = (if (showWelcome) 1 else 0) +
        messages.size +
        state.toolActivities.size +
        (if (state.error != null) 1 else 0)
    // 尾项内容长度参与键，使流式追加 / 内容增长时持续贴底。
    val tailLength = messages.lastOrNull()?.let { it.content.length + (it.reasoning?.length ?: 0) } ?: 0
    LaunchedEffect(itemCount, tailLength, state.streaming) {
        if (itemCount > 0) {
            // LazyColumn 会把越界目标夹到内容末尾，滚到末项即等价于贴底。
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = bottomInset + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(MessageGap),
    ) {
        if (showWelcome) {
            item(key = KEY_WELCOME) { AiWelcomeCard() }
        }
        items(messages, key = { MESSAGE_KEY_PREFIX + it.id }) { message ->
            AiMessageBubble(message = message, modifier = animateItemMotion())
        }
        items(state.toolActivities, key = { TOOL_KEY_PREFIX + it.toolCallId }) { activity ->
            AiToolActivityRow(activity = activity, modifier = animateItemMotion())
        }
        state.error?.let { error ->
            item(key = KEY_ERROR) {
                AiErrorCard(
                    error = error,
                    canRetry = state.canRetry,
                    onRetry = onRetry,
                    onDismiss = onDismissError,
                )
            }
        }
    }
}

/** 欢迎卡：空会话时的首次引导。 */
@Composable
private fun AiWelcomeCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = MaterialTheme.shapes.large,
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(16.dp),
    ) {
        Text(
            text = "你好，我是打印助手",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "直接下达任务，我可以查资料、读写文件、画图，并整理成可打印的文件。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 单条消息气泡：深度思考折叠区 + 工具标签 + 正文 + 产物。 */
@Composable
private fun AiMessageBubble(message: AiUiMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == AiMessageRole.User
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = BubbleMaxWidth),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            if (message.reasoning != null) {
                AiReasoningSection(reasoning = message.reasoning, streaming = message.isStreaming)
                Spacer(Modifier.height(8.dp))
            }
            AiBubbleSurface(message = message)
            if (message.artifacts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.artifacts.forEach { artifact -> AiArtifactItem(artifact = artifact) }
                }
            }
        }
    }
}

/** 气泡本体：按角色取底色，工具消息额外带一行工具名。 */
@Composable
private fun AiBubbleSurface(message: AiUiMessage, modifier: Modifier = Modifier) {
    val container = when (message.role) {
        AiMessageRole.User -> MaterialTheme.colorScheme.primaryContainer
        AiMessageRole.Error -> MaterialTheme.colorScheme.errorContainer
        AiMessageRole.Tool -> MaterialTheme.colorScheme.surfaceContainer
        AiMessageRole.Assistant -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (message.role) {
        AiMessageRole.User -> MaterialTheme.colorScheme.onPrimaryContainer
        AiMessageRole.Error -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .glassSurface(shape = MaterialTheme.shapes.large, fallbackColor = container)
            .padding(BubblePadding),
    ) {
        if (message.role == AiMessageRole.Tool) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconView(
                    icon = if (message.isError) AppIcon.Info else AppIcon.Settings,
                    size = InlineIconSize,
                    tint = if (message.isError) MaterialTheme.colorScheme.error else contentColor,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (message.isError) {
                        "执行失败：${toolLabel(message.toolName)}"
                    } else {
                        "已执行：${toolLabel(message.toolName)}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    modifier = Modifier.weight(1f),
                )
            }
            if (message.content.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
            }
        }
        if (message.content.isNotBlank()) {
            if (message.role == AiMessageRole.Tool && message.toolName == ToolNames.RUN_COMMAND) {
                AiTerminalPanel(content = message.content)
            } else {
                AiToolAwareText(
                    content = message.content,
                    collapsible = message.role == AiMessageRole.Tool,
                    color = contentColor,
                )
            }
        } else if (message.isStreaming && message.reasoning == null) {
            Text(
                text = "正在思考…",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

/**
 * 正文文本。
 *
 * 工具结果可能是一整份文件内容，默认折叠到 [TOOL_RESULT_COLLAPSED_LINES] 行，
 * 超出 [TOOL_RESULT_COLLAPSE_CHARS] 时提供展开/收起。
 */
@Composable
private fun AiToolAwareText(
    content: String,
    collapsible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val collapsibleContent = collapsible && content.length > TOOL_RESULT_COLLAPSE_CHARS
    Column(modifier = modifier) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = if (collapsibleContent && !expanded) TOOL_RESULT_COLLAPSED_LINES else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        if (collapsibleContent) {
            AiTextAction(
                text = if (expanded) "收起" else "展开",
                color = color,
                onClick = { expanded = !expanded },
            )
        }
    }
}

/**
 * 终端结果面板：run_command 的输出以等宽字体 + 反色底呈现，模拟真实终端观感；
 * 长输出与普通工具结果一致，默认折叠、可展开。
 */
@Composable
private fun AiTerminalPanel(content: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val collapsible = content.length > TOOL_RESULT_COLLAPSE_CHARS
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.medium)
            .padding(BubblePadding),
    ) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            maxLines = if (collapsible && !expanded) TOOL_RESULT_COLLAPSED_LINES else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        if (collapsible) {
            AiTextAction(
                text = if (expanded) "收起" else "展开",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                onClick = { expanded = !expanded },
            )
        }
    }
}

/** 文本按钮（带自定义内容色，对齐安卓端 AppTextButton(contentColor = …)）。 */
@Composable
private fun AiTextAction(text: String, color: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 深度思考折叠区：流式输出中自动可见，结束后可折叠回顾。 */
@Composable
private fun AiReasoningSection(
    reasoning: String?,
    streaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val text = reasoning.orEmpty()
    var expanded by remember { mutableStateOf(false) }
    val hasText = text.isNotBlank()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = hasText) { expanded = !expanded }
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(BubblePadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconView(
                icon = AppIcon.Info,
                size = InlineIconSize,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "深度思考",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (hasText) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        when {
            expanded && hasText -> {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .heightIn(max = ReasoningMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            streaming && !hasText -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "正在思考…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 正在执行的工具活动条。 */
@Composable
private fun AiToolActivityRow(activity: AiToolActivity, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = MaterialTheme.shapes.medium,
                fallbackColor = MaterialTheme.colorScheme.surfaceContainer,
            )
            .padding(BubblePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopLoadingIndicator(size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "正在执行：${toolLabel(activity.toolName)}",
                style = MaterialTheme.typography.bodySmall,
            )
            activity.progress?.takeIf { it.isNotBlank() }?.let { progress ->
                Text(
                    text = progress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = TOOL_PROGRESS_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 错误卡：可重试时给出「重新生成」，并可手动关闭。 */
@Composable
private fun AiErrorCard(
    error: AiErrorUi,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onErrorContainer
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = MaterialTheme.shapes.large,
                fallbackColor = MaterialTheme.colorScheme.errorContainer,
            )
            .padding(BubblePadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconView(
                icon = AppIcon.Info,
                size = InlineIconSize,
                tint = contentColor,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = error.message,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            AppIconButton(
                icon = AppIcon.Close,
                contentDescription = "关闭",
                onClick = onDismiss,
                tint = contentColor,
            )
        }
        error.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
        }
        if (canRetry) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AiTextAction(text = "重新生成", color = contentColor, onClick = onRetry)
            }
        }
    }
}

/** 产物条目：图片直接解码预览，其它类型展示文件名。 */
@Composable
private fun AiArtifactItem(artifact: AiUiArtifact, modifier: Modifier = Modifier) {
    if (artifact.isImage) {
        AiImageArtifact(artifact = artifact, modifier = modifier)
    } else {
        AiFileArtifact(artifact = artifact, modifier = modifier)
    }
}

/** 图片产物：桌面端直接读本地文件解码（对齐安卓端 IO 线程解码，避免阻塞滚动）。 */
@Composable
private fun AiImageArtifact(artifact: AiUiArtifact, modifier: Modifier = Modifier) {
    val bitmap = rememberLocalFileImage(File(artifact.path))
    Box(
        modifier = modifier
            .size(ArtifactSize)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "图片产物",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AppIconView(
                icon = AppIcon.Gallery,
                size = 32.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiFileArtifact(artifact: AiUiArtifact, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = MaterialTheme.shapes.medium,
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(BubblePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(
            icon = AppIcon.Description,
            size = InlineIconSize,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artifact.label ?: artifact.path.substringAfterLast('\\').substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "文件产物",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 未配置模型时的引导页。 */
@Composable
private fun AiNoModelGuide(onOpenConfig: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIconView(
            icon = AppIcon.Settings,
            size = 28.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "尚未配置 AI 模型",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "前往「设置 → AI 配置设置」填写服务链接、模型名称与密钥后即可使用。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        AppPrimaryButton(text = "去配置", onClick = onOpenConfig)
    }
}

/**
 * 底部输入栏：挂载区 + 审批模式 + 上传 + 输入框 + 发送/停止。
 *
 * 与安卓端一致使用 [glassSurface]（同帧自绘磨砂），避免内容滚动时背景采样滞后导致抖动。
 */
@Composable
private fun AiInputBar(
    value: String,
    attachments: List<AiUiArtifact>,
    streaming: Boolean,
    permissionMode: AgentPermissionMode,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onPermissionClick: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                // 与全局底栏一致：圆角取主题边角尺寸，0 会被当作直角而产生形状错位。
                shape = MaterialTheme.shapes.large,
                fallbackColor = MaterialTheme.colorScheme.surface,
            )
            .padding(16.dp),
    ) {
        // 挂载区：平时不显示，有附件时展开；发送后随状态清空自动折叠。
        AnimatedVisibility(
            visible = attachments.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                AiAttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment)
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            AppIconButton(
                icon = AppIcon.ManageAccounts,
                contentDescription = "切换工具审批模式",
                onClick = onPermissionClick,
                tint = permissionMode.tintColor(),
            )
            Spacer(Modifier.width(8.dp))
            AppIconButton(
                icon = AppIcon.Add,
                contentDescription = "上传图片或文件",
                onClick = onAttach,
                enabled = !streaming,
            )
            Spacer(Modifier.width(8.dp))
            AppTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = InputMaxHeight),
                placeholder = "描述你想打印或完成的任务…",
                singleLine = false,
                minLines = 1,
                enabled = !streaming,
            )
            Spacer(Modifier.width(8.dp))
            if (streaming) {
                AppIconButton(
                    icon = AppIcon.Close,
                    contentDescription = "停止生成",
                    onClick = onStop,
                )
            } else {
                AppIconButton(
                    icon = AppIcon.Send,
                    contentDescription = "发送",
                    onClick = onSend,
                    enabled = value.isNotBlank() || attachments.isNotEmpty(),
                )
            }
        }
    }
}

/** 挂载区：横向排列待发送附件，逐项可移除。 */
@Composable
private fun AiAttachmentStrip(
    attachments: List<AiUiArtifact>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "待发送附件（${attachments.size}）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            attachments.forEach { artifact ->
                AiAttachmentChip(artifact = artifact, onRemove = { onRemove(artifact.id) })
            }
        }
    }
}

/** 单个附件卡片：图片显示缩略图，文件显示类型图标；右侧可移除。 */
@Composable
private fun AiAttachmentChip(
    artifact: AiUiArtifact,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = BubbleMaxWidth)
            .glassSurface(
                shape = MaterialTheme.shapes.medium,
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artifact.isImage) {
            AiAttachmentThumbnail(artifact = artifact)
        } else {
            AppIconView(
                icon = AppIcon.Description,
                size = AttachThumbSize,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.widthIn(max = AttachLabelWidth)) {
            Text(
                text = artifact.label ?: artifact.path.substringAfterLast('\\').substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (artifact.isImage) "图片" else "文件",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppIconButton(
            icon = AppIcon.Close,
            contentDescription = "移除附件",
            onClick = onRemove,
            size = 36.dp,
        )
    }
}

/** 附件缩略图：读本地文件解码，避免大图阻塞输入栏。 */
@Composable
private fun AiAttachmentThumbnail(artifact: AiUiArtifact, modifier: Modifier = Modifier) {
    val bitmap = rememberLocalFileImage(File(artifact.path))
    Box(
        modifier = modifier
            .size(AttachThumbSize)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AppIconView(
                icon = AppIcon.Gallery,
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 上传方式选择：图片走扩展名过滤，任意文件不过滤。 */
@Composable
private fun AiAttachSheet(
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = "上传到 AI 工作区",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        AppDivider()
        AiAttachRow(
            icon = AppIcon.Gallery,
            title = "上传图片",
            description = "从本机选择图片，AI 可查看、编辑并打印",
            onClick = onPickImage,
        )
        AiAttachRow(
            icon = AppIcon.Description,
            title = "上传文件",
            description = "任意文件（含压缩包），AI 可解析、解压与操作",
            onClick = onPickFile,
        )
    }
}

/** 上传选项行：图标 + 标题 + 说明。 */
@Composable
private fun AiAttachRow(
    icon: AppIcon,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(
            icon = icon,
            size = InlineIconSize,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 权限模式选择：三档审批粒度，当前档位带勾选标记。
 *
 * - 每次审批：所有写工作区/危险工具都需确认，最保守。
 * - 工作区外审批（默认）：工作区内读写自动放行，越界或危险动作需确认。
 * - 自主模式：全部自动放行，AI 连续自主执行，风险自负。
 */
@Composable
private fun AiPermissionSheet(
    current: AgentPermissionMode,
    onSelect: (AgentPermissionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = "工具审批模式",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        AppDivider()
        AgentPermissionMode.entries.forEach { mode ->
            AiPermissionRow(
                mode = mode,
                selected = mode == current,
                onClick = { onSelect(mode) },
            )
        }
    }
}

/** 单个权限档位行：图标 + 标题 + 说明，选中时右侧显示勾选。 */
@Composable
private fun AiPermissionRow(
    mode: AgentPermissionMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(
            icon = AppIcon.ManageAccounts,
            size = InlineIconSize,
            tint = mode.tintColor(),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = mode.title(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = mode.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            AppIconView(
                icon = AppIcon.Check,
                size = InlineIconSize,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 权限档位对应的强调色：自主模式用错误色提示风险，默认档用主色。 */
@Composable
private fun AgentPermissionMode.tintColor(): Color = when (this) {
    AgentPermissionMode.AUTONOMOUS -> MaterialTheme.colorScheme.error
    AgentPermissionMode.OUTSIDE_WORKSPACE_ASK -> MaterialTheme.colorScheme.primary
    AgentPermissionMode.ALWAYS_ASK -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 历史会话面板。 */
@Composable
private fun AiHistorySheet(
    summaries: List<ConversationSummary>,
    currentId: String,
    onNewConversation: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (ConversationSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "历史对话",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(text = "新建对话", onClick = onNewConversation)
        }
        AppDivider()
        if (summaries.isEmpty()) {
            Text(
                text = "暂无历史对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = HistoryMaxHeight),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(summaries, key = { it.id }) { summary ->
                    AiHistoryRow(
                        summary = summary,
                        selected = summary.id == currentId,
                        onOpen = { onOpen(summary.id) },
                        onDelete = { onDelete(summary) },
                        modifier = animateItemMotion(),
                    )
                }
            }
        }
    }
}

/** 历史会话行：标题 + 消息数，右侧删除。 */
@Composable
private fun AiHistoryRow(
    summary: ConversationSummary,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .glassSurface(
                shape = MaterialTheme.shapes.medium,
                fallbackColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .padding(BubblePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.title.ifBlank { "未命名对话" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${summary.messageCount} 条消息",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppIconButton(
            icon = AppIcon.DeleteOutline,
            contentDescription = "删除",
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/** 是否需要在列表中渲染该气泡：空白载体（纯工具调用的助手消息）不展示。 */
private fun AiUiMessage.isRenderable(): Boolean =
    role == AiMessageRole.Tool || content.isNotBlank() || !reasoning.isNullOrBlank() || artifacts.isNotEmpty()
