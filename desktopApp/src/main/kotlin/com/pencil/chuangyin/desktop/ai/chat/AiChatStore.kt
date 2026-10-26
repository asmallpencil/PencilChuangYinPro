package com.pencil.chuangyin.desktop.ai.chat

import com.pencil.chuangyin.desktop.ai.AgentEngine
import com.pencil.chuangyin.desktop.ai.AgentEvent
import com.pencil.chuangyin.desktop.ai.AgentPermissionMode
import com.pencil.chuangyin.desktop.ai.AgentPermissionPolicy
import com.pencil.chuangyin.desktop.ai.AgentPrompts
import com.pencil.chuangyin.desktop.ai.AgentRunResult
import com.pencil.chuangyin.desktop.ai.ChatMessage
import com.pencil.chuangyin.desktop.ai.ChatRole
import com.pencil.chuangyin.desktop.ai.ContextFolder
import com.pencil.chuangyin.desktop.ai.MutableAgentPermissionModeProvider
import com.pencil.chuangyin.desktop.ai.OkHttpOpenAiChatClient
import com.pencil.chuangyin.desktop.ai.ToolArtifact
import com.pencil.chuangyin.desktop.ai.ToolArtifactKind
import com.pencil.chuangyin.desktop.ai.ToolRegistry
import com.pencil.chuangyin.desktop.ai.tools.AgentTools
import com.pencil.chuangyin.desktop.ai.tools.FileKinds
import com.pencil.chuangyin.desktop.ai.tools.SessionConfirmationGate
import com.pencil.chuangyin.desktop.ai.tools.Workspace
import com.pencil.chuangyin.desktop.support.AiConfigStore
import com.pencil.chuangyin.desktop.support.AiModelConfig
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 进程级 AI 会话协调器（桌面版，对齐安卓端 `AiAgentSession`）。
 *
 * 与安卓端一样把「任务协程」挂在常驻作用域上而非 Composable 上，
 * 因此切走 AI 页面后任务继续跑，回来即可看到最新进度。
 *
 * 状态所有权全部集中于此：界面只读取 [state] 并调用方法。
 */
object AiChatStore {

    // SupervisorJob：单个会话失败不影响后续任务。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(AiChatUiState())
    val state: StateFlow<AiChatUiState> = _state.asStateFlow()

    /** 工具共享的存储沙箱。 */
    private val workspace = Workspace()

    /** 权限模式的进程内持有者（安卓端持久化在 DataStore，桌面端按会话保持）。 */
    private val permissionProvider = MutableAgentPermissionModeProvider()

    private val store: ConversationStore = FileConversationStore()

    /** 引擎按需构建：工具注册表依赖工作区，初始化一次即可复用。 */
    private val engine: AgentEngine by lazy {
        val client = OkHttpOpenAiChatClient()
        AgentEngine(
            client = client,
            registry = ToolRegistry(AgentTools.build(workspace)),
            contextFolder = ContextFolder(client),
            confirmationGates = listOf(SessionConfirmationGate()),
            permissionPolicy = AgentPermissionPolicy(permissionProvider),
        )
    }

    /** 当前会话的完整消息（不含系统提示词），作为下一轮的输入与落库内容。 */
    @Volatile
    private var history: List<ChatMessage> = emptyList()

    /** 会话创建时间；由仓库补齐后回写，保证多次写入同一时间戳。 */
    @Volatile
    private var conversationCreatedAt: Long = 0L

    /** 正在运行的任务；取消接口据此中断。 */
    @Volatile
    private var runJob: Job? = null

    /** 本次运行产生的全部消息（含本轮追加），作为落库的权威来源。 */
    private var runHistory: MutableList<ChatMessage> = mutableListOf()

    /** 运行期产物暂存：toolCallId → 产物；工具消息落定后挂到对应气泡上。 */
    private val pendingArtifacts = mutableMapOf<String, MutableList<AiUiArtifact>>()

    /** 当前正在执行的工具调用 id，用于把 [AgentEvent.ArtifactCreated] 归属到正确的工具。 */
    private var activeToolCallId: String? = null

    @Volatile
    private var started = false

    /** 首次访问时启动订阅与恢复；幂等。 */
    fun start() {
        if (started) return
        started = true
        observeConfig()
        observeSummaries()
        observePermission()
        restoreConversation()
    }

    /* ───────────────────────── 对外操作 ───────────────────────── */

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
        val snapshot = history
        val index = snapshot.indexOfLast { it.role == ChatRole.User }
        if (index < 0) return
        startRun(base = snapshot.take(index), appended = listOf(snapshot[index]))
    }

    /** 停止当前任务；已生成的内容会保留并落盘。 */
    fun stop() {
        runJob?.cancel()
    }

    /** 放弃当前错误提示。 */
    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** 新建一段空对话。 */
    fun startNewConversation() {
        if (_state.value.streaming) return
        scope.launch { applyConversation(store.newConversation()) }
    }

    /** 打开指定历史会话。 */
    fun openConversation(id: String) {
        if (_state.value.streaming) return
        scope.launch {
            val conversation = store.load(id)
            if (conversation == null) applyConversation(store.newConversation()) else applyConversation(conversation)
        }
    }

    /** 删除指定会话；若删除的是当前会话则自动新建。 */
    fun deleteConversation(id: String) {
        scope.launch {
            store.delete(id)
            if (id == _state.value.conversationId) applyConversation(store.newConversation())
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
     * 安卓端从 content Uri 读流，桌面端直接拿本地文件复制。
     */
    fun importFromFile(file: File, onImported: (String) -> Unit = {}, onFailed: (String) -> Unit = {}) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { workspace.importFile(file) }
            }
            result.onSuccess { imported ->
                val artifact = AiUiArtifact(
                    id = "attach_" + UUID.randomUUID(),
                    kind = kindOf(imported.name),
                    path = imported.absolutePath,
                    label = imported.name,
                )
                _state.update { current -> current.copy(attachments = current.attachments + artifact) }
                onImported(workspace.displayPath(imported))
            }.onFailure { error ->
                onFailed(error.message ?: "导入失败")
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
        permissionProvider.set(mode)
    }

    /* ───────────────────────── 状态订阅 ───────────────────────── */

    private fun observeConfig() {
        scope.launch {
            AiConfigStore.state.collect { config ->
                val model = config.activeModel
                _state.update {
                    it.copy(
                        hasUsableModel = model?.isUsable == true,
                        modelDisplayName = model?.displayName?.takeIf { name -> name.isNotBlank() }
                            ?: model?.modelName.orEmpty(),
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
            permissionProvider.mode.collect { mode -> _state.update { it.copy(permissionMode = mode) } }
        }
    }

    /** 启动时恢复最近一次会话，实现「持续任务」；无历史时开一段新对话。 */
    private fun restoreConversation() {
        scope.launch {
            val latest = store.refresh().firstOrNull()
            val conversation = latest?.let { store.load(it.id) }
            if (conversation == null) applyConversation(store.newConversation()) else applyConversation(conversation)
        }
    }

    /* ───────────────────────── 任务执行 ───────────────────────── */

    /**
     * 运行一轮任务。
     *
     * @param base 本轮之前的完整历史。
     * @param appended 本轮新增的用户消息。
     */
    private fun startRun(base: List<ChatMessage>, appended: List<ChatMessage>) {
        val config = AiConfigStore.state.value.activeModel
        if (config == null || !config.isUsable) {
            _state.update { it.copy(error = AiErrorUi(MISSING_CONFIG_MESSAGE), canRetry = true) }
            return
        }
        val prompt = AgentPrompts.systemPrompt(
            workspacePath = workspace.writeRootPath(),
            readRootPath = workspace.readRootsPath(),
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
                runEngine(
                    config = config,
                    prompt = prompt,
                    request = request,
                    conversationId = conversationId,
                )
            } catch (e: CancellationException) {
                withContext(NonCancellable) { onRunStopped() }
                return@launch
            }
            onRunFinished(result)
        }
    }

    private suspend fun runEngine(
        config: AiModelConfig,
        prompt: String,
        request: List<ChatMessage>,
        conversationId: String,
    ): AgentRunResult = engine.run(
        config = config,
        systemPrompt = prompt,
        history = request,
        conversationId = conversationId,
        onEvent = ::onAgentEvent,
    )

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
        val messageError = error?.let { AiErrorUi(agentErrorMessage(it.kind), it.detail ?: it.message) }
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

    /* ───────────────────────── 持久化 ───────────────────────── */

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
            )
        }
    }

    /** 按扩展名判断附件类型：可预览图片走多模态，其余按普通文件处理。 */
    private fun kindOf(name: String): String =
        if (FileKinds.isImageExtension(name)) ToolArtifactKind.IMAGE else ToolArtifactKind.FILE

    private const val MISSING_CONFIG_MESSAGE = "尚未配置可用的 AI 模型"
}
