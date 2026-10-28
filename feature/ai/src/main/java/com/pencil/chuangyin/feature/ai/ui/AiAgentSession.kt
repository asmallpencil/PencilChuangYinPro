package com.pencil.chuangyin.feature.ai.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pencil.chuangyin.core.agent.config.AiConfigRepository
import com.pencil.chuangyin.core.agent.model.AgentEvent
import com.pencil.chuangyin.core.agent.model.AgentRunResult
import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ChatRole
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.runtime.AgentEngine
import com.pencil.chuangyin.core.agent.runtime.AgentPermissionMode
import com.pencil.chuangyin.core.agent.runtime.ToolApprovalBus
import com.pencil.chuangyin.feature.ai.AiPrompts
import com.pencil.chuangyin.feature.ai.R
import com.pencil.chuangyin.feature.ai.data.AiPermissionRepository
import com.pencil.chuangyin.feature.ai.data.Conversation
import com.pencil.chuangyin.feature.ai.data.ConversationStore
import com.pencil.chuangyin.feature.ai.data.newUserMessage
import com.pencil.chuangyin.feature.ai.tools.FileKinds
import com.pencil.chuangyin.feature.ai.tools.Workspace
import com.pencil.chuangyin.printer.ConnectionState
import com.pencil.chuangyin.printer.PrinterController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 进程级 AI 会话协调器。
 *
 * 与 [AiChatViewModel] 分离的原因：ViewModel 随页面销毁而清除，若任务协程挂在 viewModelScope 上，
 * 用户离开 AI 页面任务即被取消。本类作为 `@Singleton` 常驻进程，自持 [scope]（SupervisorJob），
 * 因此「关闭 AI 页面 / 切到其它页面」时任务继续运行，回到页面即可看到最新进度。
 *
 * 状态所有权全部集中于此：界面只读取 [state] 并调用方法，ViewModel 退化为薄代理。
 */
@Singleton
class AiAgentSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: AgentEngine,
    private val configRepository: AiConfigRepository,
    private val store: ConversationStore,
    private val workspace: Workspace,
    private val printer: PrinterController,
    private val permissionRepository: AiPermissionRepository,
    private val approvalBus: ToolApprovalBus,
) {

    // SupervisorJob：单个会话失败不影响后续任务；Main.immediate 保证事件第一时间反映到界面。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(AiChatUiState())
    val state: StateFlow<AiChatUiState> = _state.asStateFlow()

    /** 当前会话的完整消息（不含系统提示词），作为下一轮的输入与落库内容。 */
    private var history: List<ChatMessage> = emptyList()

    /** 会话创建时间；由仓库补齐后回写，保证多次写入同一时间戳。 */
    private var conversationCreatedAt: Long = 0L

    /** 正在运行的任务；取消接口据此中断。 */
    private var runJob: Job? = null

    /** 本次运行产生的全部消息（含本轮追加），作为落库的权威来源。 */
    private var runHistory: MutableList<ChatMessage> = mutableListOf()

    /** 运行期产物暂存：toolCallId → 产物；工具消息落定后挂到对应气泡上。 */
    private val pendingArtifacts = mutableMapOf<String, MutableList<AiUiArtifact>>()

    /** 当前正在执行的工具调用 id，用于把 [AgentEvent.ArtifactCreated] 归属到正确的工具。 */
    private var activeToolCallId: String? = null

    @Volatile
    private var started = false

    /** 首次访问时启动订阅与恢复；幂等，供界面与上传入口共用。 */
    fun start() {
        if (started) return
        started = true
        observeConfig()
        observePrinter()
        observeSummaries()
        observePermission()
        observeApproval()
        restoreConversation()
    }

    // region 对外操作

    /** 更新输入框内容。 */
    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    /**
     * 发送当前输入。空输入且无附件、或任务运行中时忽略。
     *
     * 挂载区中的附件随本条用户消息一起发出，发出后清空并折叠挂载区。
     */
    fun send(text: String? = null) {
        val content = (text ?: _state.value.input).trim()
        val attachments = _state.value.attachments
        if ((content.isEmpty() && attachments.isEmpty()) || _state.value.streaming) return
        val domain = attachments.map { ui ->
            ToolArtifact(id = ui.id, kind = ui.kind, path = ui.path, label = ui.label)
        }
        _state.update { it.copy(input = "", attachments = emptyList()) }
        startRun(base = history, appended = listOf(newUserMessage(content, domain)))
    }

    /** 重新生成本轮回复：丢弃最后一轮的用户消息之后的内容后重跑。 */
    fun retry() {
        if (_state.value.streaming) return
        val index = history.indexOfLast { it.role == ChatRole.User }
        if (index < 0) return
        startRun(base = history.take(index), appended = listOf(history[index]))
    }

    /** 停止当前任务；已生成的内容会保留并落盘。 */
    fun stop() {
        approvalBus.cancelPending()
        runJob?.cancel()
    }

    /**
     * 回应用户对敏感工具的审批请求。
     *
     * 允许后同一工具在本会话内不再重复询问（见 [ToolApprovalBus.approvedTools]），
     * 这样 AI 终端这类需要连续多次调用的工具才能真正跑起来。
     */
    fun decideApproval(id: String, approved: Boolean) {
        approvalBus.decide(id, approved)
    }

    /** 放弃当前错误提示。 */
    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** 新建一段空对话。 */
    fun startNewConversation() {
        if (_state.value.streaming) return
        applyConversation(store.newConversation())
    }

    /** 打开指定历史会话。 */
    fun openConversation(id: String) {
        if (_state.value.streaming) return
        if (id == _state.value.conversationId) {
            _state.update { it.copy(historyVisible = false) }
            return
        }
        // 读取磁盘会话是异步的：先把当前会话的可见内容清空，否则加载期间列表仍渲染旧消息，
        // 切过去时会看到上一个对话的聊天记录残留在背景里。
        _state.update {
            it.copy(
                messages = emptyList(),
                toolActivities = emptyList(),
                attachments = emptyList(),
                error = null,
                pendingApproval = null,
                historyVisible = false,
                canRetry = false,
            )
        }
        scope.launch {
            val conversation = store.load(id)
            if (conversation == null) startNewConversation() else applyConversation(conversation)
        }
    }

    /** 删除指定会话；若删除的是当前会话则自动新建。 */
    fun deleteConversation(id: String) {
        scope.launch {
            store.delete(id)
            if (id == _state.value.conversationId) startNewConversation()
        }
    }

    /** 展开/收起历史会话面板。 */
    fun toggleHistory() {
        _state.update { it.copy(historyVisible = !it.historyVisible) }
    }

    /** 收起历史会话面板。 */
    fun hideHistory() {
        _state.update { it.copy(historyVisible = false) }
    }

    /**
     * 导入用户选择的图片/文件到 agent 工作目录，并挂载到输入框上方的挂载区。
     *
     * 落盘后把产物加入 [AiChatUiState.attachments]，输入框保持干净；用户补充指令后
     * 随消息一并发出。AI 亦可通过 list_files / read_file 等直接操作该文件。
     */
    fun importFromUri(uri: Uri, onImported: (String) -> Unit = {}, onFailed: (String) -> Unit = {}) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = queryDisplayName(uri) ?: "upload_${System.currentTimeMillis()}"
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("无法打开该文件")
                    name to input.use { workspace.importToWorkspace(it, name) }
                }
            }
            result.onSuccess { (name, file) ->
                // 授权输出目录时 file 只是同名占位，展示用 agent 内相对路径。
                val display = if (workspace.usesOutputDir) name else workspace.displayPath(file)
                val artifact = AiUiArtifact(
                    id = "attach_" + java.util.UUID.randomUUID(),
                    kind = kindOf(name),
                    path = if (workspace.usesOutputDir) name else file.absolutePath,
                    label = file.name,
                )
                _state.update { current -> current.copy(attachments = current.attachments + artifact) }
                onImported(display)
            }.onFailure { error ->
                onFailed(error.message ?: "上传失败")
            }
        }
    }

    /** 移除挂载区中的一个附件（尚未发送）。 */
    fun removeAttachment(id: String) {
        _state.update { current ->
            current.copy(attachments = current.attachments.filterNot { it.id == id })
        }
    }

    /** 切换工具审批模式（每次审批 / 工作区外审批 / 自主模式）。 */
    fun setPermissionMode(mode: AgentPermissionMode) {
        permissionRepository.set(mode)
    }

    /** 顶栏快捷切换当前使用的模型。 */
    fun setActiveModel(id: String) {
        scope.launch { configRepository.setActive(id) }
    }

    // endregion

    // region 状态订阅

    private fun observeConfig() {
        scope.launch {
            combine(configRepository.models, configRepository.activeModel) { models, active ->
                models to active
            }.collect { (models, active) ->
                _state.update {
                    it.copy(
                        hasUsableModel = active?.isUsable == true,
                        modelDisplayName = active?.displayName?.takeIf { name -> name.isNotBlank() }
                            ?: active?.modelName.orEmpty(),
                        models = models.map { model ->
                            AiModelOption(
                                id = model.id,
                                label = model.displayName.takeIf { name -> name.isNotBlank() }
                                    ?: model.modelName.ifBlank { model.id },
                            )
                        },
                        activeModelId = active?.id.orEmpty(),
                    )
                }
            }
        }
    }

    private fun observePrinter() {
        scope.launch {
            printer.connectionState.collect { connection ->
                val connected = connection as? ConnectionState.Connected
                _state.update {
                    it.copy(
                        printerConnected = connected != null,
                        printerName = connected?.device?.name.orEmpty(),
                    )
                }
            }
        }
    }

    private fun observeSummaries() {
        scope.launch {
            store.summaries.collect { list -> _state.update { it.copy(summaries = list) } }
        }
    }

    private fun observePermission() {
        scope.launch {
            permissionRepository.mode.collect { mode -> _state.update { it.copy(permissionMode = mode) } }
        }
    }

    /** 订阅引擎的审批请求，推送到界面弹窗。 */
    private fun observeApproval() {
        scope.launch {
            approvalBus.pending.collect { request ->
                _state.update { current ->
                    current.copy(
                        pendingApproval = request?.let { pending ->
                            AiApprovalUi(
                                id = pending.id,
                                toolName = pending.toolName,
                                arguments = pending.arguments,
                                permission = pending.permission,
                            )
                        },
                    )
                }
            }
        }
    }

    /** 启动时恢复最近一次会话，实现「持续任务」；无历史时开一段新对话。 */
    private fun restoreConversation() {
        scope.launch {
            val latest = store.refresh().firstOrNull()
            val conversation = latest?.let { store.load(it.id) }
            if (conversation == null) startNewConversation() else applyConversation(conversation)
        }
    }

    // endregion

    // region 任务执行

    /**
     * 运行一轮任务。
     *
     * @param base 本轮之前的完整历史。
     * @param appended 本轮新增的用户消息。
     */
    private fun startRun(base: List<ChatMessage>, appended: List<ChatMessage>) {
        val config = configRepository.activeModel.value
        if (config == null || !config.isUsable) {
            _state.update {
                it.copy(error = AiErrorUi(R.string.ai_error_missing_config), canRetry = true)
            }
            return
        }
        val prompt = AiPrompts.systemPrompt(
            workspacePath = workspace.writeRootPath(),
            readRootPath = workspace.readRootsPath(),
            printerConnected = _state.value.printerConnected,
            printerName = _state.value.printerName,
        )
        val request = base + appended
        runHistory = request.toMutableList()
        pendingArtifacts.clear()
        activeToolCallId = null
        _state.update { current ->
            current.copy(
                streaming = true,
                error = null,
                canRetry = false,
                toolActivities = emptyList(),
                messages = base.toUiMessages(previous = current.messages) + appended.toUiMessages(),
            )
        }
        val conversationId = _state.value.conversationId
        runJob = scope.launch {
            val result = try {
                engine.run(
                    config = config,
                    systemPrompt = prompt,
                    history = request,
                    conversationId = conversationId,
                    onEvent = ::onAgentEvent,
                )
            } catch (e: CancellationException) {
                withContext(NonCancellable) { onRunStopped() }
                return@launch
            }
            onRunFinished(result)
        }
    }

    /** 引擎事件 → 界面状态的增量映射。 */
    private fun onAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.MessageStarted -> _state.update { current ->
                current.copy(
                    messages = current.messages + AiUiMessage(
                        id = event.messageId,
                        role = AiMessageRole.Assistant,
                        isStreaming = true,
                    ),
                )
            }

            is AgentEvent.ReasoningDelta -> _state.update { current ->
                current.copy(
                    messages = current.messages.updateMessage(event.messageId) { message ->
                        message.copy(reasoning = (message.reasoning ?: "") + event.text)
                    },
                )
            }

            is AgentEvent.ContentDelta -> _state.update { current ->
                current.copy(
                    messages = current.messages.updateMessage(event.messageId) { message ->
                        message.copy(content = message.content + event.text)
                    },
                )
            }

            is AgentEvent.ToolStarted -> {
                activeToolCallId = event.toolCallId
                _state.update { current ->
                    current.copy(
                        toolActivities = current.toolActivities + AiToolActivity(
                            toolCallId = event.toolCallId,
                            toolName = event.toolName,
                            arguments = event.arguments,
                        ),
                    )
                }
            }

            is AgentEvent.ToolProgress -> _state.update { current ->
                current.copy(
                    toolActivities = current.toolActivities.map { activity ->
                        if (activity.toolCallId == event.toolCallId) {
                            activity.copy(progress = event.text)
                        } else {
                            activity
                        }
                    },
                )
            }

            is AgentEvent.ArtifactCreated -> {
                val callId = activeToolCallId ?: return
                pendingArtifacts.getOrPut(callId) { mutableListOf() } += event.artifact.toUi()
            }

            is AgentEvent.MessageCompleted -> onMessageCompleted(event.message)

            is AgentEvent.IterationStarted, is AgentEvent.Finished, is AgentEvent.Failed -> Unit
        }
    }

    private fun onMessageCompleted(message: ChatMessage) {
        runHistory += message
        if (message.role == ChatRole.Tool) {
            val artifacts = message.toolCallId?.let { pendingArtifacts.remove(it) }.orEmpty()
            val ui = message.toUiMessage(artifacts = artifacts)
            _state.update { current ->
                current.copy(
                    messages = current.messages.upsertMessage(ui),
                    toolActivities = current.toolActivities.filterNot { it.toolCallId == message.toolCallId },
                )
            }
        } else {
            val previous = _state.value.messages.firstOrNull { it.id == message.id }
            val ui = message.toUiMessage(previous = previous)
            _state.update { current -> current.copy(messages = current.messages.upsertMessage(ui)) }
        }
    }

    /** 正常结束：以本轮完整消息落库，并用最终内容替换流式占位气泡。 */
    private suspend fun onRunFinished(result: AgentRunResult) {
        history = runHistory.toList()
        val error = result.error
        val messageError = error?.let { AiErrorUi(agentErrorRes(it.kind), it.detail ?: it.message) }
        persist()
        _state.update { current ->
            current.copy(
                streaming = false,
                toolActivities = emptyList(),
                messages = history.toUiMessages(previous = current.messages),
                error = messageError,
                canRetry = error != null && history.any { it.role == ChatRole.User },
            )
        }
        clearRunScratch()
    }

    /** 用户中断：保留已完成内容并落盘，不展示错误。 */
    private suspend fun onRunStopped() {
        // 未收尾的流式气泡可能已产出部分正文，补一条消息避免丢失。
        val unfinished = _state.value.messages.filter { it.isStreaming }.map { it.toChatMessage() }
        history = (runHistory + unfinished).filter { it.role != ChatRole.System }
        persist()
        _state.update { current ->
            current.copy(
                streaming = false,
                toolActivities = emptyList(),
                messages = current.messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it },
                error = null,
                canRetry = history.any { it.role == ChatRole.User },
            )
        }
        clearRunScratch()
    }

    private fun clearRunScratch() {
        runHistory = mutableListOf()
        pendingArtifacts.clear()
        activeToolCallId = null
    }

    // endregion

    // region 持久化

    /** 把当前会话写入磁盘，并回写派生标题。 */
    private suspend fun persist() {
        val id = _state.value.conversationId
        if (id.isBlank()) return
        val stored = store.upsert(
            Conversation(id = id, messages = history, createdAt = conversationCreatedAt),
        )
        conversationCreatedAt = stored.createdAt
        val title = stored.displayTitle()
        _state.update { if (it.title == title) it else it.copy(title = title) }
    }

    /** 把一段已持久化会话装载为当前状态。 */
    private fun applyConversation(conversation: Conversation) {
        history = conversation.messages.filter { it.role != ChatRole.System }
        conversationCreatedAt = conversation.createdAt
        clearRunScratch()
        // 换会话即换上下文：清空会话级工具白名单，危险工具重新征询同意。
        approvalBus.resetSession()
        _state.update {
            it.copy(
                conversationId = conversation.id,
                title = conversation.displayTitle(),
                messages = history.toUiMessages(),
                streaming = false,
                toolActivities = emptyList(),
                error = null,
                input = "",
                attachments = emptyList(),
                historyVisible = false,
                canRetry = false,
                pendingApproval = null,
            )
        }
    }

    // endregion

    /** 从 content Uri 查询原始文件名。 */
    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    /** 按扩展名判断附件类型：可预览图片走多模态，其余按普通文件处理。 */
    private fun kindOf(name: String): String =
        if (FileKinds.isImageExtension(name)) ToolArtifactKind.IMAGE else ToolArtifactKind.FILE
}
