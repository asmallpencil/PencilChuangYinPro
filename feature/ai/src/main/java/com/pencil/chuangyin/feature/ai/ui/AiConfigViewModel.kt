package com.pencil.chuangyin.feature.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.agent.AgentDefaults
import com.pencil.chuangyin.core.agent.config.AiConfigRepository
import com.pencil.chuangyin.core.agent.config.AiModelConfig
import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ChatRequest
import com.pencil.chuangyin.core.agent.model.ChatRole
import com.pencil.chuangyin.core.agent.openai.OpenAiChatClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 表单可编辑字段的快照（与 [AiModelConfig] 同构，便于直接落库）。 */
data class AiConfigForm(
    val id: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    val displayName: String = "",
    val apiKey: String = "",
) {

    /** 必填项是否已填写（服务链接 + 模型名称）。 */
    val isComplete: Boolean get() = baseUrl.isNotBlank() && modelName.isNotBlank()

    /** 转回持久化模型，去掉首尾空白避免误存。 */
    fun toModel(): AiModelConfig = AiModelConfig(
        id = id,
        displayName = displayName.trim(),
        baseUrl = baseUrl.trim(),
        modelName = modelName.trim(),
        apiKey = apiKey.trim(),
    )

    companion object {
        /** 由已有配置构造表单（编辑场景）。 */
        fun of(model: AiModelConfig): AiConfigForm = AiConfigForm(
            id = model.id,
            baseUrl = model.baseUrl,
            modelName = model.modelName,
            displayName = model.displayName,
            apiKey = model.apiKey,
        )
    }
}

/** 正在编辑的模型表单；[isNew] 决定弹窗标题与保存语义。 */
data class AiConfigDialog(
    val isNew: Boolean,
    val form: AiConfigForm,
)

/** 连通性检测状态。 */
enum class AiConfigTestStatus {
    /** 未检测。 */
    Idle,

    /** 检测中。 */
    Testing,

    /** 检测通过。 */
    Success,

    /** 检测失败。 */
    Failure,
}

/** AI 配置页界面状态。 */
data class AiConfigUiState(
    val models: List<AiModelConfig> = emptyList(),
    val activeModelId: String? = null,
    val dialog: AiConfigDialog? = null,
    val testStatus: AiConfigTestStatus = AiConfigTestStatus.Idle,
    /** 检测失败详情；为 null 表示配置不完整（非服务端错误）。 */
    val testDetail: String? = null,
    val pendingDelete: AiModelConfig? = null,
) {
    /** 是否还没有任何配置。 */
    val isEmpty: Boolean get() = models.isEmpty()

    /** 是否正在检测连接。 */
    val testing: Boolean get() = testStatus == AiConfigTestStatus.Testing
}

/**
 * AI 配置页状态持有者：管理多份 OpenAI 兼容模型配置的增删改与当前选中项，
 * 并可在保存前直接发起一次非流式补全验证连通性。
 */
@HiltViewModel
class AiConfigViewModel @Inject constructor(
    private val repository: AiConfigRepository,
    private val client: OpenAiChatClient,
) : ViewModel() {

    private val _state = MutableStateFlow(AiConfigUiState())
    val state: StateFlow<AiConfigUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.models.collect { list -> _state.update { it.copy(models = list) } }
        }
        viewModelScope.launch {
            repository.activeModelId.collect { id -> _state.update { it.copy(activeModelId = id) } }
        }
    }

    /** 打开新增弹窗。 */
    fun openCreate() {
        _state.update {
            it.copy(
                dialog = AiConfigDialog(isNew = true, form = AiConfigForm()),
                testStatus = AiConfigTestStatus.Idle,
                testDetail = null,
            )
        }
    }

    /** 打开编辑弹窗。 */
    fun openEdit(model: AiModelConfig) {
        _state.update {
            it.copy(
                dialog = AiConfigDialog(isNew = false, form = AiConfigForm.of(model)),
                testStatus = AiConfigTestStatus.Idle,
                testDetail = null,
            )
        }
    }

    /** 关闭弹窗并清空检测结果。 */
    fun dismissDialog() {
        _state.update { it.copy(dialog = null, testStatus = AiConfigTestStatus.Idle, testDetail = null) }
    }

    /**
     * 保存表单；必填项缺失时忽略。
     *
     * 表单内容由弹窗本地持有并随调用直接传入（[AiConfigForm]），
     * 不再经 StateFlow 回绕，避免受控输入框被旧值回写导致内容错乱。
     */
    fun save(form: AiConfigForm) {
        if (!form.isComplete) return
        viewModelScope.launch {
            repository.upsert(form.toModel())
            _state.update { it.copy(dialog = null, testStatus = AiConfigTestStatus.Idle, testDetail = null) }
        }
    }

    /** 切换当前使用的模型。 */
    fun setActive(id: String) {
        viewModelScope.launch { repository.setActive(id) }
    }

    /** 请求删除某份配置（需二次确认）。 */
    fun requestDelete(model: AiModelConfig) {
        _state.update { it.copy(pendingDelete = model) }
    }

    /** 取消删除。 */
    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    /** 确认删除。 */
    fun confirmDelete() {
        val model = _state.value.pendingDelete ?: return
        viewModelScope.launch {
            repository.remove(model.id)
            _state.update { it.copy(pendingDelete = null) }
        }
    }

    /** 用弹窗当前表单发起一次连通性检测。 */
    fun testConnection(form: AiConfigForm) {
        val config = form.toModel()
        if (!config.isUsable) {
            _state.update { it.copy(testStatus = AiConfigTestStatus.Failure, testDetail = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testStatus = AiConfigTestStatus.Testing, testDetail = null) }
            val request = ChatRequest(
                model = config.modelName,
                messages = listOf(
                    ChatMessage(id = PING_MESSAGE_ID, role = ChatRole.User, content = PING_PROMPT),
                ),
                maxTokens = AgentDefaults.PING_MAX_TOKENS,
            )
            val result = client.complete(config, request)
            _state.update {
                if (result.isSuccess) {
                    it.copy(testStatus = AiConfigTestStatus.Success, testDetail = null)
                } else {
                    val error = result.error
                    it.copy(
                        testStatus = AiConfigTestStatus.Failure,
                        testDetail = error?.detail ?: error?.message.orEmpty(),
                    )
                }
            }
        }
    }

    private companion object {
        const val PING_MESSAGE_ID = "ping"
        const val PING_PROMPT = "你好"
    }
}
