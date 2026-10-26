package com.pencil.chuangyin.feature.ai.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.agent.runtime.AgentPermissionMode
import com.pencil.chuangyin.core.ui.components.AppAlertDialog
import com.pencil.chuangyin.core.ui.components.AppDivider
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.LiquidGlassLayerScope
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.pseudoGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.feature.ai.R
import com.pencil.chuangyin.feature.ai.data.ConversationSummary
import com.pencil.chuangyin.feature.ai.tools.ToolNames
import com.pencil.chuangyin.resources.R as ResR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「AI 打印」任务页。
 *
 * 页面由四层构成：顶栏（标题 + 当前模型，右侧新建/历史）、消息列表（气泡 / 工具活动 / 产物 / 错误卡）、
 * 底部输入栏（随键盘上移），以及历史会话与删除确认两个覆盖层。
 *
 * 视觉说明：气泡与工具条位于**滚动列表内**，若使用真液态玻璃（背景采样）会因纹理滞后一帧而在滑动中抖动，
 * 因此统一使用 [pseudoGlassSurface]（同帧自绘磨砂）；底部输入栏会随输入法上移、同样改用伪玻璃避免黑屏；
 * 仅顶栏（不随内容滚动、不随窗口 inset 移动）使用 [nativeGlassSurface] 真玻璃，与全局其它页面保持一致。
 */
@Composable
fun AiChatRoute(
    onBack: () -> Unit,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AiChatViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AiChatScreen(
        state = state,
        onBack = onBack,
        onOpenConfig = onOpenConfig,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onRetry = viewModel::retry,
        onNewConversation = viewModel::startNewConversation,
        onHistoryToggle = viewModel::toggleHistory,
        onHistoryDismiss = viewModel::hideHistory,
        onOpenConversation = viewModel::openConversation,
        onDeleteConversation = viewModel::deleteConversation,
        onDismissError = viewModel::dismissError,
        onRemoveAttachment = viewModel::removeAttachment,
        onSetPermissionMode = viewModel::setPermissionMode,
        // 上传：把内容拷贝进 agent 目录，挂到输入框上方的挂载区；失败时才提示。
        onImport = { uri, report ->
            viewModel.importFromUri(
                uri = uri,
                onFailed = { message -> report(context.getString(R.string.ai_attach_failed, message)) },
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    state: AiChatUiState,
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
    onImport: (Uri, (String) -> Unit) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSetPermissionMode: (AgentPermissionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassActive = rememberLiquidGlassActive()
    // 待删除会话：不直接落库，先经确认弹窗，避免误触删除。
    var pendingDelete by remember { mutableStateOf<ConversationSummary?>(null) }
    // 上传入口：底部表单先让用户选择「图片 / 任意文件」，再各自拉起系统文档选择器。
    var attachSheetVisible by remember { mutableStateOf(false) }
    // 权限模式入口：与上传同级，点开后选择审批粒度。
    var permissionSheetVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reportImport: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it, reportImport) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it, reportImport) }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            // 键盘弹出时整体上移，保证输入栏始终可见。
            .imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { AiTopBarTitle(state) },
                navigationIcon = {
                    AppIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(ResR.string.label_back),
                    )
                },
                actions = {
                    AppIconButton(
                        onClick = onHistoryToggle,
                        imageVector = Icons.Filled.History,
                        contentDescription = stringResource(R.string.ai_desc_history),
                    )
                    AppIconButton(
                        onClick = onNewConversation,
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.ai_desc_new),
                    )
                },
                modifier = Modifier.nativeGlassSurface(
                    shape = RoundedCornerShape(0.dp),
                    enabled = glassActive,
                    fallbackColor = MaterialTheme.colorScheme.surface,
                ),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            if (state.hasUsableModel) {
                AiInputBar(
                    value = state.input,
                    attachments = state.attachments,
                    streaming = state.streaming,
                    printerConnected = state.printerConnected,
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
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = onHistoryDismiss, sheetState = sheetState) {
            // 层级提升：底部表单内的原生玻璃控件压住导航栏等低层控件。
            LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
                AiHistorySheet(
                    summaries = state.summaries,
                    currentId = state.conversationId,
                    onNewConversation = onNewConversation,
                    onOpen = onOpenConversation,
                    onDelete = { pendingDelete = it },
                )
            }
        }
    }

    if (attachSheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { attachSheetVisible = false }, sheetState = sheetState) {
            LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
                AiAttachSheet(
                    onPickImage = {
                        attachSheetVisible = false
                        imagePicker.launch(arrayOf("image/*"))
                    },
                    onPickFile = {
                        attachSheetVisible = false
                        filePicker.launch(arrayOf("*/*"))
                    },
                )
            }
        }
    }

    if (permissionSheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { permissionSheetVisible = false }, sheetState = sheetState) {
            LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
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
        val title = summary.title.ifBlank { stringResource(R.string.ai_untitled_conversation) }
        AppAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { AppText(text = stringResource(R.string.ai_delete_conversation_title)) },
            text = { AppText(text = stringResource(R.string.ai_delete_conversation_message, title)) },
            confirmButton = {
                AppTextButton(
                    text = stringResource(ResR.string.action_delete),
                    onClick = {
                        pendingDelete = null
                        onDeleteConversation(summary.id)
                    },
                    contentColor = MaterialTheme.colorScheme.error,
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(ResR.string.action_cancel),
                    onClick = { pendingDelete = null },
                )
            },
        )
    }
}

/** 顶栏标题：主标题固定，副标题展示当前模型或未配置提示。 */
@Composable
private fun AiTopBarTitle(state: AiChatUiState, modifier: Modifier = Modifier) {
    val modelName = state.modelDisplayName
    Column(modifier = modifier) {
        AppText(
            text = stringResource(R.string.ai_title),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AppText(
            text = if (state.hasUsableModel && modelName.isNotBlank()) {
                stringResource(R.string.ai_subtitle_ready, modelName)
            } else {
                stringResource(R.string.ai_subtitle_no_model)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 消息列表：欢迎卡 / 气泡 / 工具活动 / 错误卡。 */
@Composable
private fun AiConversationList(
    state: AiChatUiState,
    bottomInset: androidx.compose.ui.unit.Dp,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val padM = dimensionResource(ResR.dimen.dimen_global_padding_m)
    // 纯工具调用载体的空助手消息不渲染，避免出现空白气泡。
    val messages = remember(state.messages) { state.messages.filter { it.isRenderable() } }
    val showWelcome = messages.isEmpty() && state.toolActivities.isEmpty()

    val listState = rememberLazyListState()
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
            start = padM,
            end = padM,
            top = padM,
            bottom = bottomInset + padM,
        ),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.ai_message_gap)),
    ) {
        if (showWelcome) {
            item(key = KEY_WELCOME) { AiWelcomeCard() }
        }
        items(messages, key = { MESSAGE_KEY_PREFIX + it.id }) { message ->
            AiMessageBubble(message = message)
        }
        items(state.toolActivities, key = { TOOL_KEY_PREFIX + it.toolCallId }) { activity ->
            AiToolActivityRow(activity = activity)
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
            .pseudoGlassSurface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
    ) {
        AppText(
            text = stringResource(R.string.ai_welcome_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        AppText(
            text = stringResource(R.string.ai_welcome_desc),
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
            modifier = Modifier.widthIn(max = dimensionResource(R.dimen.ai_bubble_max_width)),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            if (message.reasoning != null) {
                AiReasoningSection(reasoning = message.reasoning, streaming = message.isStreaming)
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            }
            AiBubbleSurface(message = message)
            if (message.artifacts.isNotEmpty()) {
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s))) {
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
            .pseudoGlassSurface(shape = MaterialTheme.shapes.large, color = container)
            .padding(dimensionResource(R.dimen.ai_bubble_padding)),
    ) {
        if (message.role == AiMessageRole.Tool) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (message.isError) Icons.Filled.Warning else Icons.Filled.Settings,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(dimensionResource(R.dimen.ai_inline_icon_size)),
                )
                Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppText(
                    text = stringResource(
                        if (message.isError) R.string.ai_tool_failed else R.string.ai_tool_done,
                        stringResource(toolLabelRes(message.toolName)),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    modifier = Modifier.weight(1f),
                )
            }
            if (message.content.isNotBlank()) {
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
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
            AppText(
                text = stringResource(R.string.ai_thinking),
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
        AppText(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = if (collapsibleContent && !expanded) TOOL_RESULT_COLLAPSED_LINES else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        if (collapsibleContent) {
            AppTextButton(
                text = stringResource(
                    if (expanded) R.string.ai_reasoning_collapse else R.string.ai_reasoning_expand,
                ),
                onClick = { expanded = !expanded },
                contentColor = color,
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
            .padding(dimensionResource(R.dimen.ai_bubble_padding)),
    ) {
        AppText(
            text = content,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            maxLines = if (collapsible && !expanded) TOOL_RESULT_COLLAPSED_LINES else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        if (collapsible) {
            AppTextButton(
                text = stringResource(if (expanded) R.string.ai_reasoning_collapse else R.string.ai_reasoning_expand),
                onClick = { expanded = !expanded },
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
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
            .padding(dimensionResource(R.dimen.ai_bubble_padding)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimensionResource(R.dimen.ai_inline_icon_size)),
            )
            Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            AppText(
                text = stringResource(R.string.ai_reasoning_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (hasText) {
                AppText(
                    text = stringResource(
                        if (expanded) R.string.ai_reasoning_collapse else R.string.ai_reasoning_expand,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        when {
            expanded && hasText -> {
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                Box(
                    modifier = Modifier
                        .heightIn(max = dimensionResource(R.dimen.ai_reasoning_max_height))
                        .verticalScroll(rememberScrollState()),
                ) {
                    AppText(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            streaming && !hasText -> {
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppText(
                    text = stringResource(R.string.ai_thinking),
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
            .pseudoGlassSurface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            )
            .padding(dimensionResource(R.dimen.ai_bubble_padding)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = stringResource(
                    R.string.ai_tool_running,
                    stringResource(toolLabelRes(activity.toolName)),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            activity.progress?.takeIf { it.isNotBlank() }?.let { progress ->
                AppText(
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
            .pseudoGlassSurface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
            )
            .padding(dimensionResource(R.dimen.ai_bubble_padding)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(dimensionResource(R.dimen.ai_inline_icon_size)),
            )
            Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            AppText(
                text = stringResource(error.messageRes),
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            AppIconButton(
                onClick = onDismiss,
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.ai_desc_close),
                tint = contentColor,
            )
        }
        error.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            AppText(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
        }
        if (canRetry) {
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppTextButton(
                    text = stringResource(R.string.ai_retry),
                    onClick = onRetry,
                    contentColor = contentColor,
                )
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

@Composable
private fun AiImageArtifact(artifact: AiUiArtifact, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    // 图片解码放到 IO 线程，避免大图在主线程阻塞滚动。
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = artifact.id, key2 = artifact.path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(artifact.path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .size(dimensionResource(R.dimen.ai_artifact_size))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.ai_artifact_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = stringResource(R.string.ai_artifact_image),
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
            .pseudoGlassSurface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(dimensionResource(R.dimen.ai_bubble_padding)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimensionResource(R.dimen.ai_inline_icon_size)),
        )
        Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = artifact.label ?: artifact.path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = stringResource(R.string.ai_artifact_file),
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
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_l)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        AppText(
            text = stringResource(R.string.ai_no_model_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        AppText(
            text = stringResource(R.string.ai_no_model_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        AppPrimaryButton(
            text = stringResource(R.string.ai_no_model_action),
            onClick = onOpenConfig,
        )
    }
}

/**
 * 底部输入栏：未连接打印机时给出提示，运行中按钮切换为「停止」。
 *
 * 使用 [pseudoGlassSurface]（同帧自绘磨砂）而非真玻璃：呼起输入法时该栏会随窗口 inset 上移，
 * 真玻璃需要采样背景、布局变化瞬间采样区尚未就绪会呈现黑屏，自绘方案不依赖背景采样。
 */
@Composable
private fun AiInputBar(
    value: String,
    attachments: List<AiUiArtifact>,
    streaming: Boolean,
    printerConnected: Boolean,
    permissionMode: AgentPermissionMode,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onPermissionClick: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.pseudoGlassSurface(
            // 与全局底栏一致：圆角取主题边角尺寸，0 会被原生层按直角处理而产生形状错位。
            shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_l)),
            color = MaterialTheme.colorScheme.surface,
        ),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            // 挂载区：平时不显示，有附件时展开；发送后随状态清空自动折叠。
            AnimatedVisibility(
                visible = attachments.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    AiAttachmentStrip(
                        attachments = attachments,
                        onRemove = onRemoveAttachment,
                    )
                    Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                }
            }
            if (!printerConnected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dimensionResource(R.dimen.ai_inline_icon_size)),
                    )
                    Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                    AppText(
                        text = stringResource(R.string.ai_printer_off_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                AppIconButton(
                    onClick = onPermissionClick,
                    imageVector = Icons.Filled.Security,
                    contentDescription = stringResource(R.string.ai_permission_desc),
                    tint = permissionMode.tintColor(),
                )
                Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppIconButton(
                    onClick = onAttach,
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = stringResource(R.string.ai_attach),
                    enabled = !streaming,
                )
                Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = dimensionResource(R.dimen.ai_input_max_height)),
                    placeholder = stringResource(R.string.ai_input_hint),
                    enabled = !streaming,
                    singleLine = false,
                )
                Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                if (streaming) {
                    AppIconButton(
                        onClick = onStop,
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.ai_desc_stop),
                    )
                } else {
                    AppIconButton(
                        onClick = onSend,
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.ai_desc_send),
                        enabled = value.isNotBlank() || attachments.isNotEmpty(),
                    )
                }
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
        AppText(
            text = stringResource(R.string.ai_attachment_title, attachments.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
        ) {
            attachments.forEach { artifact ->
                AiAttachmentChip(artifact = artifact, onRemove = { onRemove(artifact.id) })
            }
        }
    }
}

/** 单个附件卡片：图片显示缩略图，文件显示类型图标；右上角可移除。 */
@Composable
private fun AiAttachmentChip(
    artifact: AiUiArtifact,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = dimensionResource(R.dimen.ai_bubble_max_width))
            .pseudoGlassSurface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artifact.isImage) {
            AiAttachmentThumbnail(artifact = artifact)
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimensionResource(R.dimen.ai_attach_thumb_size)),
            )
        }
        Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        Column(modifier = Modifier.widthIn(max = dimensionResource(R.dimen.ai_attach_label_width))) {
            AppText(
                text = artifact.label ?: artifact.path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = stringResource(
                    if (artifact.isImage) R.string.ai_attachment_image else R.string.ai_attachment_file,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppIconButton(
            onClick = onRemove,
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.ai_attachment_remove),
        )
    }
}

/** 附件缩略图：IO 线程解码，避免大图阻塞输入栏。 */
@Composable
private fun AiAttachmentThumbnail(artifact: AiUiArtifact, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = artifact.id, key2 = artifact.path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(artifact.path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .size(dimensionResource(R.dimen.ai_attach_thumb_size))
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.ai_artifact_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = stringResource(R.string.ai_artifact_image),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 上传方式选择：图片走 image/* 过滤器，任意文件走 */*。 */
@Composable
private fun AiAttachSheet(
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_m)),
    ) {
        AppText(
            text = stringResource(R.string.ai_attach_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
        )
        AppDivider()
        AiAttachRow(
            icon = Icons.Filled.Image,
            title = stringResource(R.string.ai_attach_image),
            description = stringResource(R.string.ai_attach_image_desc),
            onClick = onPickImage,
        )
        AiAttachRow(
            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
            title = stringResource(R.string.ai_attach_file),
            description = stringResource(R.string.ai_attach_file_desc),
            onClick = onPickFile,
        )
    }
}

/** 上传选项行：图标 + 标题 + 说明。 */
@Composable
private fun AiAttachRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimensionResource(R.dimen.ai_inline_icon_size)),
        )
        Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        Column(modifier = Modifier.weight(1f)) {
            AppText(text = title, style = MaterialTheme.typography.bodyLarge)
            AppText(
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
            .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_m)),
    ) {
        AppText(
            text = stringResource(R.string.ai_permission_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
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
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Security,
            contentDescription = null,
            tint = mode.tintColor(),
            modifier = Modifier.size(dimensionResource(R.dimen.ai_inline_icon_size)),
        )
        Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        Column(modifier = Modifier.weight(1f)) {
            AppText(text = stringResource(mode.titleRes()), style = MaterialTheme.typography.bodyLarge)
            AppText(
                text = stringResource(mode.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(dimensionResource(R.dimen.ai_inline_icon_size)),
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

/** 权限档位标题文案。 */
@StringRes
private fun AgentPermissionMode.titleRes(): Int = when (this) {
    AgentPermissionMode.ALWAYS_ASK -> R.string.ai_permission_always_title
    AgentPermissionMode.OUTSIDE_WORKSPACE_ASK -> R.string.ai_permission_outside_title
    AgentPermissionMode.AUTONOMOUS -> R.string.ai_permission_autonomous_title
}

/** 权限档位说明文案。 */
@StringRes
private fun AgentPermissionMode.descriptionRes(): Int = when (this) {
    AgentPermissionMode.ALWAYS_ASK -> R.string.ai_permission_always_desc
    AgentPermissionMode.OUTSIDE_WORKSPACE_ASK -> R.string.ai_permission_outside_desc
    AgentPermissionMode.AUTONOMOUS -> R.string.ai_permission_autonomous_desc
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
            .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_m)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(R.string.ai_history),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(
                text = stringResource(R.string.ai_new_conversation),
                onClick = onNewConversation,
            )
        }
        AppDivider()
        if (summaries.isEmpty()) {
            AppText(
                text = stringResource(R.string.ai_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = dimensionResource(R.dimen.ai_history_max_height)),
                contentPadding = PaddingValues(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
            ) {
                items(summaries, key = { it.id }) { summary ->
                    AiHistoryRow(
                        summary = summary,
                        selected = summary.id == currentId,
                        onOpen = { onOpen(summary.id) },
                        onDelete = { onDelete(summary) },
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
            .pseudoGlassSurface(
                shape = MaterialTheme.shapes.medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .padding(dimensionResource(R.dimen.ai_bubble_padding)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = summary.title.ifBlank { stringResource(R.string.ai_untitled_conversation) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = stringResource(R.string.ai_message_count, summary.messageCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppIconButton(
            onClick = onDelete,
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(ResR.string.action_delete),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/** 是否需要在列表中渲染该气泡：空白载体（纯工具调用的助手消息）不展示。 */
private fun AiUiMessage.isRenderable(): Boolean =
    role == AiMessageRole.Tool || content.isNotBlank() || !reasoning.isNullOrBlank() || artifacts.isNotEmpty()

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

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun AiChatScreenPreview() {
    AiChatScreen(
        state = AiChatUiState(
            conversationId = "c1",
            title = "打印一张小猫的简笔画",
            hasUsableModel = true,
            modelDisplayName = "gpt-4o-mini",
            printerConnected = true,
            printerName = "Pencil-P1",
            input = "",
            messages = listOf(
                AiUiMessage(
                    id = "m1",
                    role = AiMessageRole.User,
                    content = "帮我画一张小猫简笔画并打印出来",
                ),
                AiUiMessage(
                    id = "m2",
                    role = AiMessageRole.Assistant,
                    reasoning = "用户想要一张小猫简笔画，先调用绘图工具生成图片，再直接打印。",
                    content = "好的，我先画一张小猫简笔画，然后直接发送到打印机。",
                ),
                AiUiMessage(
                    id = "m3",
                    role = AiMessageRole.Tool,
                    toolName = "draw_image",
                    content = "已生成图片：cat.png（400x400）",
                    artifacts = listOf(
                        AiUiArtifact(id = "a1", kind = "file", path = "/data/cat.png", label = "cat.png"),
                    ),
                ),
            ),
        ),
        onBack = {},
        onOpenConfig = {},
        onInputChange = {},
        onSend = {},
        onStop = {},
        onRetry = {},
        onNewConversation = {},
        onHistoryToggle = {},
        onHistoryDismiss = {},
        onOpenConversation = {},
        onDeleteConversation = {},
        onDismissError = {},
        onImport = { _, report -> report("已上传") },
        onRemoveAttachment = {},
        onSetPermissionMode = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun AiChatNoModelPreview() {
    AiNoModelGuide(onOpenConfig = {})
}

@Preview(showBackground = true)
@Composable
private fun AiChatErrorPreview() {
    AiErrorCard(
        error = AiErrorUi(messageRes = R.string.ai_error_network, detail = "Failed to connect to api.example.com"),
        canRetry = true,
        onRetry = {},
        onDismiss = {},
    )
}
