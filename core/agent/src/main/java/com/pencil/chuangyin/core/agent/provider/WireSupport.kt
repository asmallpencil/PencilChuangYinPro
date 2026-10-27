package com.pencil.chuangyin.core.agent.provider

import com.pencil.chuangyin.core.agent.AgentJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** SSE 单行转 `data:` 负载；非数据行（注释、event、id）返回 null。 */
internal fun String.toSsePayload(): String? {
    val line = trim()
    if (!line.startsWith(SSE_DATA_PREFIX)) return null
    return line.removePrefix(SSE_DATA_PREFIX).trim().ifEmpty { null }
}

/** 宽松解析为 JSON 对象；失败返回 null（心跳等非 JSON 行直接跳过）。 */
internal fun String.toJsonObjectOrNull(): JsonObject? = try {
    AgentJson.parseToJsonElement(this) as? JsonObject
} catch (e: SerializationException) {
    null
}

internal fun JsonObject.stringField(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.intField(key: String): Int? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()

internal fun JsonObject.objField(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arrayField(key: String): JsonArray? = this[key] as? JsonArray

/**
 * 把 `data:image/jpeg;base64,XXXX` 拆成（媒体类型, 纯 base64）。
 *
 * Anthropic / Gemini 都要求 base64 与 mimeType 分开传，不能直接复用 data URL。
 */
internal fun String.toInlineImageParts(): Pair<String, String>? {
    if (!startsWith(DATA_URL_PREFIX)) return null
    val rest = removePrefix(DATA_URL_PREFIX)
    val separator = rest.indexOf(BASE64_MARKER)
    if (separator <= 0) return null
    val mediaType = rest.substring(0, separator)
    val data = rest.substring(separator + BASE64_MARKER.length)
    if (mediaType.isBlank() || data.isBlank()) return null
    return mediaType to data
}

private const val SSE_DATA_PREFIX = "data:"
private const val DATA_URL_PREFIX = "data:"
private const val BASE64_MARKER = ";base64,"
