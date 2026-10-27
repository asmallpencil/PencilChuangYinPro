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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google Gemini `generateContent` 客户端（桌面端，对齐安卓端 `GeminiChatClient`）。
 *
 * 与 OpenAI 协议的主要差异：
 * - 端点是 `/v1beta/models/{model}:streamGenerateContent?alt=sse`，模型名在路径里；
 * - 密钥走 `x-goog-api-key` 请求头；
 * - 消息是 `contents[].parts[]`，角色只有 `user` / `model`，系统提示走 `systemInstruction`；
 * - 工具声明是 `tools[].functionDeclarations[]`（参数用 OpenAPI 子集，不接受
 *   `additionalProperties` 等 JSON Schema 关键字，故下发前会剔除）；
 * - 工具调用是 `functionCall` 部件，工具结果是用户侧的 `functionResponse` 部件。
 */
class GeminiChatClient(
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
                val slots = linkedMapOf<String, ToolSlot>()
                var failure: AgentError? = null
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    val payload = line.toSsePayload() ?: continue
                    val event = payload.toJsonObjectOrNull() ?: continue
                    event.objField(FIELD_ERROR)?.let { error ->
                        failure = AgentError(
                            AgentErrorKind.Protocol,
                            error.stringField(FIELD_MESSAGE) ?: SERVER_ERROR_MESSAGE,
                            error.stringField(FIELD_STATUS),
                        )
                    }
                    if (failure != null) break
                    event.objField(FIELD_PROMPT_FEEDBACK)?.stringField(FIELD_BLOCK_REASON)
                        ?.let { reason ->
                            failure = AgentError(AgentErrorKind.Protocol, BLOCKED_MESSAGE, reason)
                        }
                    if (failure != null) break
                    val parts = event.firstCandidateParts() ?: continue
                    parts.forEachIndexed { index, part ->
                        part.objField(FIELD_FUNCTION_CALL)?.let { functionCall ->
                            val name = functionCall.stringField(FIELD_NAME) ?: return@forEachIndexed
                            val slot = slots.getOrPut("$index|$name") { ToolSlot(name) }
                            functionCall.objField(FIELD_ARGS)?.let { args -> slot.merge(args) }
                        }
                        val text = part.stringField(FIELD_TEXT)?.takeIf { it.isNotEmpty() } ?: return@forEachIndexed
                        if (part.isThought()) {
                            emit(ChatStreamEvent.ReasoningDelta(text))
                        } else {
                            emit(ChatStreamEvent.ContentDelta(text))
                        }
                    }
                }
                val terminal = failure
                if (terminal != null) {
                    emit(ChatStreamEvent.Failed(terminal))
                    return@use
                }
                val calls = slots.values.map { slot ->
                    ToolCallRequest(
                        id = GENERATED_CALL_ID_PREFIX + UUID.randomUUID(),
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
                            error.stringField(FIELD_STATUS),
                        ),
                    )
                }
                val text = body.firstCandidateParts()
                    ?.filterNot { it.isThought() }
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
        val payload = buildPayload(config, request)
        val builder = Request.Builder()
            .url(config.geminiGenerateUrl(stream))
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (config.apiKey.isNotBlank()) {
            builder.header(HEADER_API_KEY, config.apiKey)
        }
        if (stream) {
            builder.header(HEADER_ACCEPT, ACCEPT_EVENT_STREAM)
        }
        return builder.build()
    }

    private fun buildPayload(config: AiModelConfig, request: ChatRequest): JsonObject {
        val system = request.messages
            .filter { it.role == ChatRole.System }
            .map { it.content }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
            .trim()
        val turns = buildTurns(request.messages)
        return buildJsonObject {
            putJsonArray(FIELD_CONTENTS) {
                turns.forEach { turn ->
                    add(
                        buildJsonObject {
                            put(FIELD_ROLE, turn.role)
                            putJsonArray(FIELD_PARTS) { turn.parts.forEach { add(it) } }
                        },
                    )
                }
            }
            if (system.isNotBlank()) {
                putJsonObject(FIELD_SYSTEM_INSTRUCTION) {
                    putJsonArray(FIELD_PARTS) {
                        add(
                            buildJsonObject {
                                put(FIELD_TEXT, system)
                            },
                        )
                    }
                }
            }
            if (request.tools.isNotEmpty()) {
                putJsonArray(FIELD_TOOLS) {
                    add(
                        buildJsonObject {
                            putJsonArray(FIELD_FUNCTION_DECLARATIONS) {
                                request.tools.forEach { spec -> add(spec.toWireJson()) }
                            }
                        },
                    )
                }
            }
            putJsonObject(FIELD_GENERATION_CONFIG) {
                request.temperature?.let { put(FIELD_TEMPERATURE, it) }
                request.maxTokens?.let { put(FIELD_MAX_OUTPUT_TOKENS, it) }
            }
        }
    }

    /** 折叠为「同角色合并」的轮次；Gemini 要求 user / model 交替。 */
    private fun buildTurns(messages: List<ChatMessage>): List<Turn> {
        val turns = mutableListOf<Turn>()
        messages.forEach { message ->
            if (message.role == ChatRole.System) return@forEach
            val parts = message.toParts()
            if (parts.isEmpty()) return@forEach
            val role = if (message.role == ChatRole.Assistant) ROLE_MODEL else ROLE_USER
            val last = turns.lastOrNull()
            if (last != null && last.role == role) {
                last.parts.addAll(parts)
            } else {
                turns.add(Turn(role, parts.toMutableList()))
            }
        }
        return turns
    }

    private fun ChatMessage.toParts(): List<JsonObject> {
        val parts = mutableListOf<JsonObject>()
        when (role) {
            ChatRole.Tool -> parts.add(
                buildJsonObject {
                    putJsonObject(FIELD_FUNCTION_RESPONSE) {
                        put(FIELD_NAME, toolName ?: UNKNOWN_TOOL_NAME)
                        putJsonObject(FIELD_RESPONSE) {
                            put(FIELD_RESULT, content)
                        }
                    }
                },
            )

            ChatRole.Assistant -> {
                if (content.isNotBlank()) {
                    parts.add(buildJsonObject { put(FIELD_TEXT, content) })
                }
                toolCalls.forEach { toolCall ->
                    parts.add(
                        buildJsonObject {
                            putJsonObject(FIELD_FUNCTION_CALL) {
                                put(FIELD_NAME, toolCall.name)
                                put(FIELD_ARGS, toolCall.arguments.toJsonObjectOrNull() ?: JsonObject(emptyMap()))
                            }
                        },
                    )
                }
            }

            else -> {
                val text = content + attachmentLegend()
                if (text.isNotBlank()) parts.add(buildJsonObject { put(FIELD_TEXT, text) })
                attachments
                    .filter { it.kind == ToolArtifactKind.IMAGE }
                    .forEach { artifact ->
                        val (mediaType, data) = InlineImage.toDataUrl(artifact.path)
                            ?.toInlineImageParts()
                            ?: return@forEach
                        parts.add(
                            buildJsonObject {
                                putJsonObject(FIELD_INLINE_DATA) {
                                    put(FIELD_MIME_TYPE, mediaType)
                                    put(FIELD_DATA, data)
                                }
                            },
                        )
                    }
            }
        }
        return parts
    }

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
        put(FIELD_PARAMETERS, parameters.sanitizeSchema())
    }

    /** 首个候选的 `content.parts`。 */
    private fun JsonObject.firstCandidateParts(): List<JsonObject>? {
        val candidate = arrayField(FIELD_CANDIDATES)?.firstOrNull() as? JsonObject ?: return null
        val parts = candidate.objField(FIELD_CONTENT)?.arrayField(FIELD_PARTS) ?: return null
        return parts.mapNotNull { it as? JsonObject }
    }

    private fun JsonObject.isThought(): Boolean =
        (this[FIELD_THOUGHT] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() == true

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
    private class Turn(val role: String, val parts: MutableList<JsonObject>)

    /** 累积一个工具调用的参数（可能分片到达）。 */
    private class ToolSlot(val name: String) {

        private val arguments = linkedMapOf<String, JsonElement>()

        fun merge(chunk: JsonObject) {
            arguments.putAll(chunk)
        }

        fun argumentsJson(): String = JsonObject(arguments).toString()
    }

    private companion object {
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_API_KEY = "x-goog-api-key"
        const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
        const val ACCEPT_EVENT_STREAM = "text/event-stream"

        const val FIELD_CONTENTS = "contents"
        const val FIELD_ROLE = "role"
        const val FIELD_PARTS = "parts"
        const val FIELD_TEXT = "text"
        const val FIELD_THOUGHT = "thought"
        const val FIELD_TOOLS = "tools"
        const val FIELD_FUNCTION_DECLARATIONS = "functionDeclarations"
        const val FIELD_FUNCTION_CALL = "functionCall"
        const val FIELD_FUNCTION_RESPONSE = "functionResponse"
        const val FIELD_NAME = "name"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_PARAMETERS = "parameters"
        const val FIELD_ARGS = "args"
        const val FIELD_RESPONSE = "response"
        const val FIELD_RESULT = "result"
        const val FIELD_INLINE_DATA = "inlineData"
        const val FIELD_MIME_TYPE = "mimeType"
        const val FIELD_DATA = "data"
        const val FIELD_SYSTEM_INSTRUCTION = "systemInstruction"
        const val FIELD_GENERATION_CONFIG = "generationConfig"
        const val FIELD_TEMPERATURE = "temperature"
        const val FIELD_MAX_OUTPUT_TOKENS = "maxOutputTokens"
        const val FIELD_CANDIDATES = "candidates"
        const val FIELD_CONTENT = "content"
        const val FIELD_ERROR = "error"
        const val FIELD_MESSAGE = "message"
        const val FIELD_STATUS = "status"
        const val FIELD_PROMPT_FEEDBACK = "promptFeedback"
        const val FIELD_BLOCK_REASON = "blockReason"

        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"
        const val GENERATED_CALL_ID_PREFIX = "call_"
        const val UNKNOWN_TOOL_NAME = "tool"
        const val INVALID_URL_MESSAGE = "服务链接无效"
        const val EMPTY_BODY_MESSAGE = "服务端返回空响应"
        const val PARSE_FAILED_MESSAGE = "响应解析失败"
        const val SERVER_ERROR_MESSAGE = "服务端返回错误"
        const val BLOCKED_MESSAGE = "内容被服务端安全策略拦截"
        const val ERROR_BODY_MAX_CHARS = 500

        val JSON_MEDIA_TYPE = CONTENT_TYPE_JSON.toMediaType()
    }
}

/** Gemini 不接受这些 JSON Schema 关键字，下发前递归剔除。 */
private val DROPPED_SCHEMA_KEYS = setOf(
    "\$schema",
    "\$id",
    "\$comment",
    "additionalProperties",
    "default",
    "examples",
    "definitions",
)

/** 递归剔除 Gemini 不支持的 JSON Schema 关键字。 */
private fun JsonObject.sanitizeSchema(): JsonObject {
    val source = this
    return buildJsonObject {
        source.forEach { (key, value) ->
            if (key !in DROPPED_SCHEMA_KEYS) {
                put(key, value.sanitizeValue())
            }
        }
    }
}

private fun JsonElement.sanitizeValue(): JsonElement = when (this) {
    is JsonObject -> sanitizeSchema()
    is JsonArray -> JsonArray(map { it.sanitizeValue() })
    else -> this
}
