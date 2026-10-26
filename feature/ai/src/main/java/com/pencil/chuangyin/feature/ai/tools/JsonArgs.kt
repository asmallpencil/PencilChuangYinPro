package com.pencil.chuangyin.feature.ai.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 工具参数读取辅助。
 *
 * 模型给出的 JSON 参数类型并不总是可靠（例如把整数写成字符串），这里统一做宽容解析，
 * 读不到时回退到默认值，避免因单个字段类型不符导致整个工具调用失败。
 */
internal fun JsonObject.stringOrNull(key: String): String? =
    runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()

/** 读取字符串参数并去除首尾空白；缺失或空白时返回 null。 */
internal fun JsonObject.trimmedOrNull(key: String): String? = stringOrNull(key)?.trim()?.ifEmpty { null }

internal fun JsonObject.stringOr(key: String, default: String): String = trimmedOrNull(key) ?: default

internal fun JsonObject.intOrNull(key: String): Int? =
    runCatching { get(key)?.jsonPrimitive?.intOrNull }.getOrNull()
        ?: runCatching { get(key)?.jsonPrimitive?.contentOrNull?.trim()?.toInt() }.getOrNull()
        ?: runCatching { get(key)?.jsonPrimitive?.contentOrNull?.trim()?.toDouble()?.toInt() }.getOrNull()

internal fun JsonObject.intOr(key: String, default: Int): Int = intOrNull(key) ?: default

internal fun JsonObject.booleanOr(key: String, default: Boolean): Boolean =
    runCatching { get(key)?.jsonPrimitive?.booleanOrNull }.getOrNull()
        ?: runCatching {
            get(key)?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.let {
                when (it) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> null
                }
            }
        }.getOrNull()
        ?: default

/** 读取对象数组参数（缺失或类型不符时返回空列表）。 */
internal fun JsonObject.arrayOrNull(key: String): kotlinx.serialization.json.JsonArray? =
    runCatching { get(key) as? kotlinx.serialization.json.JsonArray }.getOrNull()
