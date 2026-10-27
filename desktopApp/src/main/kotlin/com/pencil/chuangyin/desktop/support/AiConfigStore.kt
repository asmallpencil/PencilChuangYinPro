package com.pencil.chuangyin.desktop.support

import com.pencil.chuangyin.desktop.ai.AgentDefaults
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 一份模型配置 —— 逐字段对齐安卓端
 * `core/agent/config/AiModelConfig.kt`。
 *
 * [baseUrl] 允许多种写法，端点由 [protocol] 决定并自动归一化：
 *
 * OpenAI 兼容（[ApiProtocol.OPENAI]，见 [chatCompletionsUrl]）：
 * 1. 完整端点：`https://host/v1/chat/completions`
 * 2. 版本根地址：`https://host/v1`
 * 3. 服务根地址：`https://host`
 *
 * Anthropic（[ApiProtocol.ANTHROPIC]，见 [anthropicMessagesUrl]）：
 * `https://api.anthropic.com`、`https://api.anthropic.com/v1`、
 * `https://api.anthropic.com/v1/messages` 均可。
 *
 * Gemini（[ApiProtocol.GEMINI]，见 [geminiGenerateUrl]）：
 * `https://generativelanguage.googleapis.com`、
 * `https://generativelanguage.googleapis.com/v1beta` 均可。
 */
@Serializable
data class AiModelConfig(
    /** 稳定标识；为空表示尚未落库，由仓库分配。 */
    val id: String = "",
    /** 上游协议；老配置缺省即为 OpenAI 兼容。 */
    val protocol: ApiProtocol = ApiProtocol.OPENAI,
    /** 界面上展示的模型名称。 */
    val displayName: String = "",
    /** 服务链接。 */
    val baseUrl: String = "",
    /** 模型名称（请求体中的 model 字段）。 */
    val modelName: String = "",
    /** 密钥。 */
    val apiKey: String = "",
) {

    /** 去掉首尾空白与尾部斜杠的地址。 */
    private fun normalizedBase(): String = baseUrl.trim().trimEnd(URL_SEPARATOR)

    /** 归一化后的 OpenAI 对话补全端点。 */
    fun chatCompletionsUrl(): String {
        val trimmed = normalizedBase()
        return when {
            trimmed.endsWith(AgentDefaults.CHAT_COMPLETIONS_SUFFIX) -> trimmed
            trimmed.endsWith(AgentDefaults.API_VERSION_SEGMENT) ->
                trimmed + AgentDefaults.CHAT_COMPLETIONS_SUFFIX
            else -> trimmed + AgentDefaults.API_VERSION_SEGMENT + AgentDefaults.CHAT_COMPLETIONS_SUFFIX
        }
    }

    /** 归一化后的 Anthropic Messages 端点。 */
    fun anthropicMessagesUrl(): String {
        val trimmed = normalizedBase()
        return when {
            trimmed.endsWith(AgentDefaults.ANTHROPIC_MESSAGES_SUFFIX) -> trimmed
            trimmed.endsWith(AgentDefaults.API_VERSION_SEGMENT) ->
                trimmed + AgentDefaults.ANTHROPIC_MESSAGES_SUFFIX
            else -> trimmed + AgentDefaults.API_VERSION_SEGMENT + AgentDefaults.ANTHROPIC_MESSAGES_SUFFIX
        }
    }

    /** 归一化后的 Gemini 端点；[stream] 决定流式 / 一次性两种动作。 */
    fun geminiGenerateUrl(stream: Boolean): String {
        val trimmed = normalizedBase()
        val prefix = if (trimmed.endsWith(AgentDefaults.GEMINI_VERSION_SEGMENT)) {
            trimmed
        } else {
            trimmed + AgentDefaults.GEMINI_VERSION_SEGMENT
        }
        val action = if (stream) {
            AgentDefaults.GEMINI_STREAM_ACTION
        } else {
            AgentDefaults.GEMINI_ONCE_ACTION
        }
        return prefix + AgentDefaults.GEMINI_MODELS_SEGMENT + modelName.trim() + action
    }

    /** 配置是否足以发起请求。 */
    val isUsable: Boolean
        get() = baseUrl.isNotBlank() && modelName.isNotBlank()

    companion object {
        private const val URL_SEPARATOR = '/'
    }
}

/** 表单可编辑字段的快照（与 [AiModelConfig] 同构，便于直接落库）。 */
data class AiConfigForm(
    val id: String = "",
    val protocol: ApiProtocol = ApiProtocol.OPENAI,
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
        protocol = protocol,
        displayName = displayName.trim(),
        baseUrl = baseUrl.trim(),
        modelName = modelName.trim(),
        apiKey = apiKey.trim(),
    )

    companion object {
        /** 由已有配置构造表单（编辑场景）。 */
        fun of(model: AiModelConfig): AiConfigForm = AiConfigForm(
            id = model.id,
            protocol = model.protocol,
            baseUrl = model.baseUrl,
            modelName = model.modelName,
            displayName = model.displayName,
            apiKey = model.apiKey,
        )
    }
}

/** AI 配置的持久化快照。 */
@Serializable
private data class StoredAiConfig(
    val models: List<AiModelConfig> = emptyList(),
    val activeModelId: String? = null,
)

/** 对外暴露的 AI 配置状态。 */
data class AiConfigStoreState(
    val models: List<AiModelConfig> = emptyList(),
    val activeModelId: String? = null,
) {
    val isEmpty: Boolean get() = models.isEmpty()

    /** 当前生效的模型（未配置时为 null）。 */
    val activeModel: AiModelConfig?
        get() = models.firstOrNull { it.id == activeModelId } ?: models.firstOrNull()
}

/** 连通性检测结果。 */
sealed interface AiTestResult {
    data object Success : AiTestResult

    /** [detail] 为 null 表示配置不完整（非服务端错误）；否则为服务端错误摘要。 */
    data class Failure(val detail: String?) : AiTestResult
}

/**
 * AI 模型配置仓库 —— 对齐安卓端 `AiConfigRepository` 的对外语义：
 * 多份配置的增删改 + 当前选中项，另有「保存前先测一次连通性」。
 *
 * 桌面端用 Preferences 代替 DataStore，OkHttp 直接打 OpenAI 兼容端点（非流式）。
 */
object AiConfigStore {

    private const val KEY_PAYLOAD = "ai_payload"

    private const val PING_MAX_TOKENS = 16
    private const val PING_PROMPT = "你好"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: Preferences by lazy {
        Preferences.userRoot().node("com/pencil/chuangyin/pro/ai")
    }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AiConfigStoreState> = _state.asStateFlow()

    /** 新增或更新一份配置；[AiModelConfig.id] 为空时自动分配并首次自动置为当前。 */
    fun upsert(input: AiModelConfig) {
        if (!input.isUsable) return
        val current = _state.value
        val id = input.id.ifBlank { UUID.randomUUID().toString() }
        val model = input.copy(
            id = id,
            displayName = input.displayName.trim(),
            baseUrl = input.baseUrl.trim(),
            modelName = input.modelName.trim(),
            apiKey = input.apiKey.trim(),
        )
        val models = if (current.models.any { it.id == id }) {
            current.models.map { if (it.id == id) model else it }
        } else {
            current.models + model
        }
        val active = current.activeModelId?.takeIf { active -> models.any { it.id == active } } ?: id
        save(AiConfigStoreState(models = models, activeModelId = active))
    }

    /** 删除一份配置；若删掉的是当前项，自动切到剩下的第一份。 */
    fun remove(id: String) {
        val current = _state.value
        val models = current.models.filterNot { it.id == id }
        if (models.size == current.models.size) return
        val active = current.activeModelId?.takeIf { active -> models.any { it.id == active } }
            ?: models.firstOrNull()?.id
        save(AiConfigStoreState(models = models, activeModelId = active))
    }

    /** 切换当前使用的模型。 */
    fun setActive(id: String) {
        val current = _state.value
        if (current.models.none { it.id == id }) return
        save(current.copy(activeModelId = id))
    }

    /**
     * 用一份表单配置发起一次连通性检测（非流式）。
     *
     * 按 [ApiModelConfig.protocol] 分派到各自端点与鉴权头：
     * - OpenAI 兼容：`POST {base}/v1/chat/completions` + `Authorization: Bearer`；
     * - Anthropic：`POST {base}/v1/messages` + `x-api-key` / `anthropic-version`；
     * - Gemini：`POST {base}/v1beta/models/{model}:generateContent` + `x-goog-api-key`。
     *
     * 三者都以 `max_tokens = 16` 只换取一次极短的响应，避免浪费额度。
     */
    suspend fun testConnection(config: AiModelConfig): AiTestResult {
        if (!config.isUsable) return AiTestResult.Failure(null)
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = pingRequest(config)
                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        AiTestResult.Success
                    } else {
                        val body = response.body?.string().orEmpty()
                        val detail = extractErrorDetail(body)
                            ?: "${response.code} ${response.message}".trim()
                        AiTestResult.Failure(detail)
                    }
                }
            }.getOrElse { error ->
                AiTestResult.Failure(
                    error.message?.takeIf { it.isNotBlank() }
                        ?: error::class.simpleName.orEmpty()
                )
            }
        }
    }

    /** 按协议构造一次探活请求。 */
    private fun pingRequest(config: AiModelConfig): Request = when (config.protocol) {
        ApiProtocol.OPENAI -> openAiPingRequest(config)
        ApiProtocol.ANTHROPIC -> anthropicPingRequest(config)
        ApiProtocol.GEMINI -> geminiPingRequest(config)
    }

    private fun openAiPingRequest(config: AiModelConfig): Request {
        val payload = buildJsonObject {
            put("model", config.modelName)
            put("max_tokens", PING_MAX_TOKENS)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", PING_PROMPT)
                }
            }
        }.toString()
        return Request.Builder()
            .url(config.chatCompletionsUrl())
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                if (config.apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${config.apiKey}")
                }
            }
            .build()
    }

    private fun anthropicPingRequest(config: AiModelConfig): Request {
        val payload = buildJsonObject {
            put("model", config.modelName)
            put("max_tokens", PING_MAX_TOKENS)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", PING_PROMPT)
                }
            }
        }.toString()
        return Request.Builder()
            .url(config.anthropicMessagesUrl())
            .header("anthropic-version", AgentDefaults.ANTHROPIC_VERSION)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                if (config.apiKey.isNotBlank()) {
                    header("x-api-key", config.apiKey)
                }
            }
            .build()
    }

    private fun geminiPingRequest(config: AiModelConfig): Request {
        val payload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", PING_PROMPT) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", PING_MAX_TOKENS)
            }
        }.toString()
        return Request.Builder()
            .url(config.geminiGenerateUrl(stream = false))
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                if (config.apiKey.isNotBlank()) {
                    header("x-goog-api-key", config.apiKey)
                }
            }
            .build()
    }

    private fun load(): AiConfigStoreState {
        val raw = runCatching { prefs.get(KEY_PAYLOAD, null) }.getOrNull()
        if (raw.isNullOrBlank()) return AiConfigStoreState()
        return runCatching {
            val stored = json.decodeFromString(StoredAiConfig.serializer(), raw)
            val models = stored.models
            val active = stored.activeModelId?.takeIf { id -> models.any { it.id == id } }
                ?: models.firstOrNull()?.id
            AiConfigStoreState(models = models, activeModelId = active)
        }.getOrDefault(AiConfigStoreState())
    }

    private fun save(next: AiConfigStoreState) {
        _state.value = next
        runCatching {
            val stored = StoredAiConfig(models = next.models, activeModelId = next.activeModelId)
            prefs.put(KEY_PAYLOAD, json.encodeToString(StoredAiConfig.serializer(), stored))
            prefs.flush()
        }
    }

    /** 从 OpenAI 兼容错误响应体里取 `error.message`；取不到时回退到原始文本。 */
    private fun extractErrorDetail(body: String): String? {
        if (body.isBlank()) return null
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull()
        val obj = element as? JsonObject
        val error = obj?.get("error")
        if (error is JsonObject) {
            (error["message"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return body.take(ERROR_BODY_MAX_CHARS).trim().takeIf { it.isNotBlank() }
    }

    private const val ERROR_BODY_MAX_CHARS = 200

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
}
