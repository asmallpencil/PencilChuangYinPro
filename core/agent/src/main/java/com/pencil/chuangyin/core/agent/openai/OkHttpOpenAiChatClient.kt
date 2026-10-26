package com.pencil.chuangyin.core.agent.openai

import com.pencil.chuangyin.core.agent.AgentJson
import com.pencil.chuangyin.core.agent.config.AiModelConfig
import com.pencil.chuangyin.core.agent.di.AgentHttpClient
import com.pencil.chuangyin.core.agent.model.AgentError
import com.pencil.chuangyin.core.agent.model.AgentErrorKind
import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ChatRequest
import com.pencil.chuangyin.core.agent.model.ChatRole
import com.pencil.chuangyin.core.agent.model.ChatStreamEvent
import com.pencil.chuangyin.core.agent.model.ToolCallRequest
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.model.ToolSpec
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 基于 OkHttp 的 OpenAI 兼容客户端。
 *
 * SSE 采用手写逐行解析：只关心 `data:` 行，忽略注释（`:` 开头的心跳）与
 * `event:`/`id:` 等字段，遇到 `[DONE]` 结束。工具调用参数按 `index` 增量合并。
 */
@Singleton
class OkHttpOpenAiChatClient @Inject constructor(
    @AgentHttpClient private val client: OkHttpClient,
) : OpenAiChatClient {

    override fun stream(config: AiModelConfig, request: ChatRequest): Flow<ChatStreamEvent> = flow {
        val httpRequest = try {
            buildHttpRequest(config, request, stream = true)
        } catch (e: IllegalArgumentException) {
            emit(ChatStreamEvent.Failed(invalidUrlError(e)))
            return@flow
        }
        val call = client.newCall(httpRequest)
        // 协程取消时主动断开连接，避免阻塞在 socket 读取上。
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
                val accumulator = ToolCallAccumulator()
                var failure: AgentError? = null
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    val payload = line.toSsePayload() ?: continue
                    if (payload == SSE_DONE_MARKER) break
                    val chunk = decodeChunk(payload)
                    if (chunk == null) {
                        // 部分网关会在 200 的流里直接下发 `{"error": {...}}`。
                        failure = decodeInlineError(payload)
                        if (failure != null) break else continue
                    }
                    val delta = chunk.choices.firstOrNull()?.delta ?: continue
                    delta.reasoningContent?.takeIf { it.isNotEmpty() }
                        ?.let { emit(ChatStreamEvent.ReasoningDelta(it)) }
                    delta.reasoning?.takeIf { it.isNotEmpty() }
                        ?.let { emit(ChatStreamEvent.ReasoningDelta(it)) }
                    delta.content?.takeIf { it.isNotEmpty() }
                        ?.let { emit(ChatStreamEvent.ContentDelta(it)) }
                    delta.toolCalls?.let { accumulator.accept(it) }
                }
                val terminal = failure
                if (terminal != null) {
                    emit(ChatStreamEvent.Failed(terminal))
                    return@use
                }
                val calls = accumulator.build()
                if (calls.isNotEmpty()) emit(ChatStreamEvent.ToolCallsReady(calls))
                emit(ChatStreamEvent.Completed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // 取消导致的 IOException 不再上报，避免向已取消的流发送事件。
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
            client.newCall(httpRequest).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ChatCompletionResult(null, httpError(response.code, raw))
                }
                val parsed = try {
                    AgentJson.decodeFromString(RESPONSE_SERIALIZER, raw)
                } catch (e: SerializationException) {
                    return@withContext ChatCompletionResult(
                        null,
                        AgentError(
                            AgentErrorKind.Protocol,
                            PARSE_FAILED_MESSAGE,
                            raw.take(ERROR_BODY_MAX_CHARS),
                        ),
                    )
                }
                val message = parsed.choices.firstOrNull()?.message
                val text = message?.content ?: message?.reasoningContent ?: message?.reasoning
                ChatCompletionResult(text, null)
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
        val builder = Request.Builder()
            .url(config.chatCompletionsUrl())
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .post(buildRequestBody(config, request, stream))
        // 允许无密钥的本地推理服务（Ollama / LM Studio 等）。
        if (config.apiKey.isNotBlank()) {
            builder.header(HEADER_AUTHORIZATION, AUTH_BEARER_PREFIX + config.apiKey)
        }
        if (stream) {
            builder.header(HEADER_ACCEPT, ACCEPT_EVENT_STREAM)
        }
        return builder.build()
    }

    private fun buildRequestBody(
        config: AiModelConfig,
        request: ChatRequest,
        stream: Boolean,
    ): RequestBody {
        val payload = buildJsonObject {
            put(FIELD_MODEL, request.model.ifBlank { config.modelName })
            put(FIELD_STREAM, stream)
            putJsonArray(FIELD_MESSAGES) {
                request.messages.forEach { message -> add(message.toWireJson()) }
            }
            if (request.tools.isNotEmpty()) {
                putJsonArray(FIELD_TOOLS) {
                    request.tools.forEach { spec -> add(spec.toWireJson()) }
                }
            }
            request.temperature?.let { put(FIELD_TEMPERATURE, it) }
            request.maxTokens?.let { put(FIELD_MAX_TOKENS, it) }
        }
        return payload.toString().toRequestBody(JSON_MEDIA_TYPE)
    }

    /** 消息转线上格式；思考内容不回灌（多数服务端会拒绝该字段）。 */
    private fun ChatMessage.toWireJson(): JsonObject {
        val message = this
        return buildJsonObject {
            put(FIELD_ROLE, role.wireName)
            when (role) {
                ChatRole.Tool -> {
                    put(FIELD_CONTENT, content)
                    put(FIELD_TOOL_CALL_ID, toolCallId ?: id)
                }

                ChatRole.Assistant -> {
                    put(FIELD_CONTENT, content)
                    if (toolCalls.isNotEmpty()) {
                        putJsonArray(FIELD_TOOL_CALLS) {
                            toolCalls.forEach { call ->
                                add(
                                    buildJsonObject {
                                        put(FIELD_ID, call.id)
                                        put(FIELD_TYPE, TOOL_TYPE_FUNCTION)
                                        putJsonObject(FIELD_FUNCTION) {
                                            put(FIELD_NAME, call.name)
                                            put(FIELD_ARGUMENTS, call.arguments)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                ChatRole.User -> putUserContent(message)

                else -> put(FIELD_CONTENT, content)
            }
        }
    }

    /**
     * 用户消息内容：无图片时写纯字符串；含图片附件时写多模态数组
     * （`text` part + 若干 `image_url` part），使模型能直接看到图片。
     *
     * 所有附件（含图片）都会附上一行路径说明，便于模型在后续轮次用工具读取该文件。
     */
    private fun JsonObjectBuilder.putUserContent(message: ChatMessage) {
        val text = message.content + message.attachmentLegend()
        val images = message.attachments.filter { it.kind == ToolArtifactKind.IMAGE }
        val dataUrls = images.mapNotNull { artifact -> InlineImage.toDataUrl(artifact.path) }
        if (dataUrls.isEmpty()) {
            put(FIELD_CONTENT, text)
            return
        }
        putJsonArray(FIELD_CONTENT) {
            if (text.isNotBlank()) {
                add(
                    buildJsonObject {
                        put(FIELD_TYPE, PART_TYPE_TEXT)
                        put(FIELD_TEXT, text)
                    },
                )
            }
            dataUrls.forEach { url ->
                add(
                    buildJsonObject {
                        put(FIELD_TYPE, PART_TYPE_IMAGE_URL)
                        putJsonObject(FIELD_IMAGE_URL) { put(FIELD_URL, url) }
                    },
                )
            }
        }
    }

    /** 附件路径说明；无附件时为空串。 */
    private fun ChatMessage.attachmentLegend(): String {
        if (attachments.isEmpty()) return ""
        val lines = attachments.joinToString(separator = "\n") { artifact ->
            val name = artifact.label?.takeIf { it.isNotBlank() }
                ?: artifact.path.substringAfterLast('/')
            "（附件：$name，路径：${artifact.path}）"
        }
        return if (content.isBlank()) lines else "\n$lines"
    }

    private fun ToolSpec.toWireJson(): JsonObject = buildJsonObject {
        put(FIELD_TYPE, TOOL_TYPE_FUNCTION)
        putJsonObject(FIELD_FUNCTION) {
            put(FIELD_NAME, name)
            put(FIELD_DESCRIPTION, description)
            put(FIELD_PARAMETERS, parameters)
        }
    }

    private fun String.toSsePayload(): String? {
        val line = trim()
        if (!line.startsWith(SSE_DATA_PREFIX)) return null
        return line.removePrefix(SSE_DATA_PREFIX).trim().ifEmpty { null }
    }

    private fun decodeChunk(payload: String): ChatCompletionChunk? = try {
        AgentJson.decodeFromString(CHUNK_SERIALIZER, payload)
    } catch (e: SerializationException) {
        null
    }

    private fun decodeErrorMessage(raw: String): String? = try {
        val envelope = AgentJson.decodeFromString(ERROR_SERIALIZER, raw)
        envelope.error?.message ?: envelope.message
    } catch (e: SerializationException) {
        null
    }

    private fun decodeInlineError(payload: String): AgentError? =
        decodeErrorMessage(payload)
            ?.takeIf { it.isNotBlank() }
            ?.let { AgentError(AgentErrorKind.Protocol, it) }

    private fun httpError(code: Int, raw: String?): AgentError {
        val detail = raw?.let { body -> decodeErrorMessage(body) ?: body.take(ERROR_BODY_MAX_CHARS) }
        return AgentError(AgentErrorKind.Http, "HTTP $code", detail)
    }

    private fun invalidUrlError(cause: IllegalArgumentException): AgentError =
        AgentError(AgentErrorKind.Protocol, INVALID_URL_MESSAGE, cause.message)

    private fun networkError(cause: IOException): AgentError =
        AgentError(AgentErrorKind.Network, cause.message ?: cause.javaClass.simpleName)

    /** 按 `index` 累积流式工具调用分片。 */
    private class ToolCallAccumulator {

        private val slots = sortedMapOf<Int, Slot>()

        fun accept(deltas: List<ChatCompletionChunk.ToolCallDelta>) {
            deltas.forEach { delta ->
                val slot = slots.getOrPut(delta.index) { Slot() }
                delta.id?.takeIf { it.isNotBlank() }?.let { slot.id = it }
                delta.function?.name?.takeIf { it.isNotBlank() }?.let { slot.name.append(it) }
                delta.function?.arguments?.let { slot.arguments.append(it) }
            }
        }

        fun build(): List<ToolCallRequest> = slots.values
            .filter { it.name.isNotEmpty() }
            .map { slot ->
                ToolCallRequest(
                    id = slot.id.ifBlank { GENERATED_CALL_ID_PREFIX + UUID.randomUUID() },
                    name = slot.name.toString(),
                    arguments = slot.arguments.toString()
                        .ifBlank { ToolCallRequest.EMPTY_ARGUMENTS },
                )
            }

        private class Slot {
            var id: String = ""
            val name = StringBuilder()
            val arguments = StringBuilder()
        }
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val AUTH_BEARER_PREFIX = "Bearer "
        const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
        const val ACCEPT_EVENT_STREAM = "text/event-stream"
        const val SSE_DATA_PREFIX = "data:"
        const val SSE_DONE_MARKER = "[DONE]"
        const val FIELD_MODEL = "model"
        const val FIELD_STREAM = "stream"
        const val FIELD_MESSAGES = "messages"
        const val FIELD_TOOLS = "tools"
        const val FIELD_TEMPERATURE = "temperature"
        const val FIELD_MAX_TOKENS = "max_tokens"
        const val FIELD_ROLE = "role"
        const val FIELD_CONTENT = "content"
        const val FIELD_TOOL_CALLS = "tool_calls"
        const val FIELD_TOOL_CALL_ID = "tool_call_id"
        const val FIELD_ID = "id"
        const val FIELD_TYPE = "type"
        const val FIELD_FUNCTION = "function"
        const val FIELD_NAME = "name"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_PARAMETERS = "parameters"
        const val FIELD_ARGUMENTS = "arguments"
        const val FIELD_TEXT = "text"
        const val FIELD_IMAGE_URL = "image_url"
        const val FIELD_URL = "url"
        const val PART_TYPE_TEXT = "text"
        const val PART_TYPE_IMAGE_URL = "image_url"
        const val TOOL_TYPE_FUNCTION = "function"
        const val GENERATED_CALL_ID_PREFIX = "call_"
        const val INVALID_URL_MESSAGE = "服务链接无效"
        const val EMPTY_BODY_MESSAGE = "服务端返回空响应"
        const val PARSE_FAILED_MESSAGE = "响应解析失败"
        const val ERROR_BODY_MAX_CHARS = 500

        val JSON_MEDIA_TYPE = CONTENT_TYPE_JSON.toMediaType()
        val CHUNK_SERIALIZER = ChatCompletionChunk.serializer()
        val RESPONSE_SERIALIZER = ChatCompletionResponse.serializer()
        val ERROR_SERIALIZER = ApiErrorEnvelope.serializer()
    }
}
