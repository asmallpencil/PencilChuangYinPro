package com.pencil.chuangyin.desktop.ai

import com.pencil.chuangyin.desktop.support.AiModelConfig
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Anthropic Messages API 客户端（桌面端，对齐安卓端 `AnthropicChatClient`）。
 *
 * 与 OpenAI 协议的主要差异：
 * - 系统提示词是顶层 `system` 字段，不混在 messages 里；
 * - 助手发起的工具调用是 `tool_use` 内容块，工具结果是**用户消息**里的
 *   `tool_result` 块，并且必须紧跟在对应 `tool_use` 之后；
 * - 工具声明用 `input_schema` 而非 `parameters`；
 * - 流式事件为 `content_block_start` / `content_block_delta`（`text_delta`、
 *   `thinking_delta`、`input_json_delta`）/ `message_stop`。
 *
 * 为满足「同角色消息必须交替」的约束，相邻同角色消息会被合并成一轮。
 */
class AnthropicChatClient(
    private val client: OkHttpClient = OkHttpOpenAiChatClient.defaultStreamClient(),
) : OpenAiChatClient {

    private val syncClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(AgentDefaults.READ_TIMEOUT_SYNC_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override fun stream(config: AiModelConfig, request: ChatRequest): Flow<ChatStreamEvent> = flow {
        val httpRequest = try {
            buildHttpRequest(config, request, stream = true)
        } catch (e: IllegalArgumentException) {
            emit(ChatStreamEvent.Failed(invalidUrlError(e)))
            return@flow
        }
        val call = client.newCall(httpRequest)
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ChatStreamEvent.Failed(httpError(response.code, response.body?.string())))
                    return@use
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(ChatStreamEvent.Failed(AgentError(AgentErrorKind.Protocol, EMPTY_BODY_MESSAGE)))
                    return@use
                }
                val slots = sortedMapOf<Int, ToolSlot>()
                var failure: AgentError? = null
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    val payload = line.toSsePayload() ?: continue
                    val event = payload.toJsonObjectOrNull() ?: continue
                    when (event.stringField(FIELD_TYPE)) {
                        EVENT_CONTENT_BLOCK_START -> {
                            val index = event.intField(FIELD_INDEX) ?: 0
                            val block = event.objField(FIELD_CONTENT_BLOCK) ?: continue
                            if (block.stringField(FIELD_TYPE) != BLOCK_TOOL_USE) continue
                            val slot = slots.getOrPut(index) { ToolSlot() }
                            block.stringField(FIELD_ID)?.takeIf { it.isNotBlank() }?.let { slot.id = it }
                            block.stringField(FIELD_NAME)?.takeIf { it.isNotBlank() }?.let { slot.name = it }
                            block.objField(FIELD_INPUT)?.let { input ->
                                if (input.isNotEmpty()) slot.input = input
                            }
                        }

                        EVENT_CONTENT_BLOCK_DELTA -> {
                            val index = event.intField(FIELD_INDEX) ?: 0
                            val delta = event.objField(FIELD_DELTA) ?: continue
                            when (delta.stringField(FIELD_TYPE)) {
                                DELTA_TEXT -> delta.stringField(FIELD_TEXT)
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { emit(ChatStreamEvent.ContentDelta(it)) }

                                DELTA_THINKING -> delta.stringField(FIELD_THINKING)
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { emit(ChatStreamEvent.ReasoningDelta(it)) }

                                DELTA_JSON -> {
                                    val partial = delta.stringField(FIELD_PARTIAL_JSON).orEmpty()
                                    if (partial.isEmpty()) continue
                                    slots.getOrPut(index) { ToolSlot() }.raw.append(partial)
                                }
                            }
                        }

                        EVENT_ERROR -> {
                            failure = event.objField(FIELD_ERROR)?.let { error ->
                                AgentError(
                                    AgentErrorKind.Protocol,
                                    error.stringField(FIELD_MESSAGE) ?: SERVER_ERROR_MESSAGE,
                                    error.stringField(FIELD_TYPE),
                                )
                            } ?: AgentError(AgentErrorKind.Protocol, SERVER_ERROR_MESSAGE)
                            break
                        }
                    }
                }
                val terminal = failure
                if (terminal != null) {
                    emit(ChatStreamEvent.Failed(terminal))
                    return@use
                }
                val calls = slots.values
                    .filter { it.name.isNotBlank() }
                    .map { slot ->
                        ToolCallRequest(
                            id = slot.id.ifBlank { GENERATED_CALL_ID_PREFIX + UUID.randomUUID() },
                            name = slot.name,
                            arguments = slot.argumentsJson(),
                        )
                    }
                if (calls.isNotEmpty()) emit(ChatStreamEvent.ToolCallsReady(calls))
                emit(ChatStreamEvent.Completed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (currentCoroutineContext().isActive) {
                emit(ChatStreamEvent.Failed(networkError(e)))
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(
        config: AiModelConfig,
        request: ChatRequest,
    ): ChatCompletionResult = withContext(Dispatchers.IO) {
        val httpRequest = try {
            buildHttpRequest(config, request.copy(tools = emptyList()), stream = false)
        } catch (e: IllegalArgumentException) {
            return@withContext ChatCompletionResult(null, invalidUrlError(e))
        }
        try {
            syncClient.newCall(httpRequest).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ChatCompletionResult(null, httpError(response.code, raw))
                }
                val body = raw.toJsonObjectOrNull()
                    ?: return@withContext ChatCompletionResult(
                        null,
                        AgentError(AgentErrorKind.Protocol, PARSE_FAILED_MESSAGE, raw.take(ERROR_BODY_MAX_CHARS)),
                    )
                body.objField(FIELD_ERROR)?.let { error ->
                    return@withContext ChatCompletionResult(
                        null,
                        AgentError(
                            AgentErrorKind.Protocol,
                            error.stringField(FIELD_MESSAGE) ?: SERVER_ERROR_MESSAGE,
                            error.stringField(FIELD_TYPE),
                        ),
                    )
                }
                val text = body.arrayField(FIELD_CONTENT)
                    ?.mapNotNull { it as? JsonObject }
                    ?.filter { it.stringField(FIELD_TYPE) == BLOCK_TEXT }
                    ?.mapNotNull { it.stringField(FIELD_TEXT) }
                    ?.joinToString(separator = "")
                ChatCompletionResult(text?.takeIf { it.isNotBlank() }, null)
            }
        } catch (e: IOException) {
            ChatCompletionResult(null, networkError(e))
        }
    }

    private fun buildHttpRequest(
        config: AiModelConfig,
        request: ChatRequest,
        stream: Boolean,
    ): Request {
        val payload = buildPayload(config, request, stream)
        val builder = Request.Builder()
            .url(config.anthropicMessagesUrl())
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .header(HEADER_ANTHROPIC_VERSION, AgentDefaults.ANTHROPIC_VERSION)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        // 允许本地代理网关省略密钥。
        if (config.apiKey.isNotBlank()) {
            builder.header(HEADER_API_KEY, config.apiKey)
        }
        if (stream) {
            builder.header(HEADER_ACCEPT, ACCEPT_EVENT_STREAM)
        }
        return builder.build()
    }

    private fun buildPayload(
        config: AiModelConfig,
        request: ChatRequest,
        stream: Boolean,
    ): JsonObject {
        val system = request.messages
            .filter { it.role == ChatRole.System }
            .map { it.content }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
            .trim()
        val turns = buildTurns(request.messages)
        return buildJsonObject {
            put(FIELD_MODEL, request.model.ifBlank { config.modelName })
            put(FIELD_MAX_TOKENS, request.maxTokens ?: AgentDefaults.MAX_TOKENS)
            put(FIELD_STREAM, stream)
            if (system.isNotBlank()) put(FIELD_SYSTEM, system)
            putJsonArray(FIELD_MESSAGES) {
                turns.forEach { turn ->
                    add(
                        buildJsonObject {
                            put(FIELD_ROLE, turn.role)
                            put(FIELD_CONTENT, JsonArray(turn.blocks))
                        },
                    )
                }
            }
            if (request.tools.isNotEmpty()) {
                putJsonArray(FIELD_TOOLS) {
                    request.tools.forEach { spec -> add(spec.toWireJson()) }
                }
            }
            request.temperature?.let { put(FIELD_TEMPERATURE, it) }
        }
    }

    /** 把消息序列折叠成「同角色合并」的轮次；空内容轮次被丢弃。 */
    private fun buildTurns(messages: List<ChatMessage>): List<Turn> {
        val turns = mutableListOf<Turn>()
        messages.forEach { message ->
            if (message.role == ChatRole.System) return@forEach
            val blocks = message.toContentBlocks()
            if (blocks.isEmpty()) return@forEach
            val role = message.wireRole()
            val last = turns.lastOrNull()
            if (last != null && last.role == role) {
                last.blocks.addAll(blocks)
            } else {
                turns.add(Turn(role, blocks.toMutableList()))
            }
        }
        return turns
    }

    private fun ChatMessage.wireRole(): String =
        if (role == ChatRole.Assistant) ROLE_ASSISTANT else ROLE_USER

    /** 单条消息转内容块数组。 */
    private fun ChatMessage.toContentBlocks(): List<JsonObject> {
        val blocks = mutableListOf<JsonObject>()
        when (role) {
            ChatRole.Tool -> {
                blocks.add(
                    buildJsonObject {
                        put(FIELD_TYPE, BLOCK_TOOL_RESULT)
                        put(FIELD_TOOL_USE_ID, toolCallId ?: id)
                        put(FIELD_CONTENT, content.ifBlank { EMPTY_TOOL_RESULT })
                    },
                )
            }

            ChatRole.Assistant -> {
                if (content.isNotBlank()) {
                    blocks.add(textBlock(content))
                }
                toolCalls.forEach { call ->
                    blocks.add(
                        buildJsonObject {
                            put(FIELD_TYPE, BLOCK_TOOL_USE)
                            put(FIELD_ID, call.id)
                            put(FIELD_NAME, call.name)
                            put(FIELD_INPUT, parseArguments(call.arguments))
                        },
                    )
                }
            }

            else -> {
                val text = content + attachmentLegend()
                if (text.isNotBlank()) blocks.add(textBlock(text))
                addImageBlocks(blocks)
            }
        }
        return blocks
    }

    private fun textBlock(text: String): JsonObject = buildJsonObject {
        put(FIELD_TYPE, BLOCK_TEXT)
        put(FIELD_TEXT, text)
    }

    /** 用户消息附带的图片转 `image` 块（base64 源）。 */
    private fun ChatMessage.addImageBlocks(blocks: MutableList<JsonObject>) {
        attachments
            .filter { it.kind == ToolArtifactKind.IMAGE }
            .forEach { artifact ->
                val (mediaType, data) = InlineImage.toDataUrl(artifact.path)
                    ?.toInlineImageParts()
                    ?: return@forEach
                blocks.add(
                    buildJsonObject {
                        put(FIELD_TYPE, BLOCK_IMAGE)
                        putJsonObject(FIELD_SOURCE) {
                            put(FIELD_TYPE, SOURCE_BASE64)
                            put(FIELD_MEDIA_TYPE, mediaType)
                            put(FIELD_DATA, data)
                        }
                    },
                )
            }
    }

    /** 附件路径说明，便于模型后续用工具读取文件。 */
    private fun ChatMessage.attachmentLegend(): String {
        if (attachments.isEmpty()) return ""
        val lines = attachments.joinToString(separator = "\n") { artifact ->
            val name = artifact.label?.takeIf { it.isNotBlank() }
                ?: artifact.path.substringAfterLast('/').substringAfterLast('\\')
            "（附件：$name，路径：${artifact.path}）"
        }
        return if (content.isBlank()) lines else "\n$lines"
    }

    private fun ToolSpec.toWireJson(): JsonObject = buildJsonObject {
        put(FIELD_NAME, name)
        put(FIELD_DESCRIPTION, description)
        put(FIELD_INPUT_SCHEMA, parameters)
    }

    private fun parseArguments(raw: String): JsonObject =
        raw.toJsonObjectOrNull() ?: JsonObject(emptyMap())

    private fun httpError(code: Int, raw: String?): AgentError {
        val detail = raw?.let { body ->
            body.toJsonObjectOrNull()
                ?.objField(FIELD_ERROR)
                ?.stringField(FIELD_MESSAGE)
                ?: body.take(ERROR_BODY_MAX_CHARS)
        }
        return AgentError(AgentErrorKind.Http, "HTTP $code", detail)
    }

    private fun invalidUrlError(cause: IllegalArgumentException): AgentError =
        AgentError(AgentErrorKind.Protocol, INVALID_URL_MESSAGE, cause.message)

    private fun networkError(cause: IOException): AgentError =
        AgentError(AgentErrorKind.Network, cause.message ?: cause.javaClass.simpleName)

    /** 一轮对话（同角色合并后）。 */
    private class Turn(val role: String, val blocks: MutableList<JsonObject>)

    /** 按 `index` 累积流式工具调用。 */
    private class ToolSlot {
        var id: String = ""
        var name: String = ""

        /** `content_block_start` 里可能已带有完整入参。 */
        var input: JsonObject = JsonObject(emptyMap())

        /** `input_json_delta` 的分片。 */
        val raw = StringBuilder()

        fun argumentsJson(): String {
            val streamed = raw.toString()
            if (streamed.isNotBlank()) {
                streamed.toJsonObjectOrNull()?.let { return it.toString() }
            }
            return input.toString()
        }
    }

    private companion object {
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_API_KEY = "x-api-key"
        const val HEADER_ANTHROPIC_VERSION = "anthropic-version"
        const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
        const val ACCEPT_EVENT_STREAM = "text/event-stream"

        const val FIELD_TYPE = "type"
        const val FIELD_INDEX = "index"
        const val FIELD_ID = "id"
        const val FIELD_NAME = "name"
        const val FIELD_INPUT = "input"
        const val FIELD_INPUT_SCHEMA = "input_schema"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_MODEL = "model"
        const val FIELD_MAX_TOKENS = "max_tokens"
        const val FIELD_STREAM = "stream"
        const val FIELD_SYSTEM = "system"
        const val FIELD_MESSAGES = "messages"
        const val FIELD_TOOLS = "tools"
        const val FIELD_TEMPERATURE = "temperature"
        const val FIELD_ROLE = "role"
        const val FIELD_CONTENT = "content"
        const val FIELD_TEXT = "text"
        const val FIELD_THINKING = "thinking"
        const val FIELD_PARTIAL_JSON = "partial_json"
        const val FIELD_CONTENT_BLOCK = "content_block"
        const val FIELD_DELTA = "delta"
        const val FIELD_ERROR = "error"
        const val FIELD_MESSAGE = "message"
        const val FIELD_TOOL_USE_ID = "tool_use_id"
        const val FIELD_SOURCE = "source"
        const val FIELD_MEDIA_TYPE = "media_type"
        const val FIELD_DATA = "data"

        const val EVENT_CONTENT_BLOCK_START = "content_block_start"
        const val EVENT_CONTENT_BLOCK_DELTA = "content_block_delta"
        const val EVENT_ERROR = "error"
        const val DELTA_TEXT = "text_delta"
        const val DELTA_THINKING = "thinking_delta"
        const val DELTA_JSON = "input_json_delta"
        const val BLOCK_TEXT = "text"
        const val BLOCK_TOOL_USE = "tool_use"
        const val BLOCK_TOOL_RESULT = "tool_result"
        const val BLOCK_IMAGE = "image"
        const val SOURCE_BASE64 = "base64"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_USER = "user"

        const val GENERATED_CALL_ID_PREFIX = "toolu_"
        const val EMPTY_TOOL_RESULT = "（无输出）"
        const val INVALID_URL_MESSAGE = "服务链接无效"
        const val EMPTY_BODY_MESSAGE = "服务端返回空响应"
        const val PARSE_FAILED_MESSAGE = "响应解析失败"
        const val SERVER_ERROR_MESSAGE = "服务端返回错误"
        const val ERROR_BODY_MAX_CHARS = 500

        val JSON_MEDIA_TYPE = CONTENT_TYPE_JSON.toMediaType()
    }
}
