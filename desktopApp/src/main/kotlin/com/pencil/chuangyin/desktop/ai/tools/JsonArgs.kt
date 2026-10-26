package com.pencil.chuangyin.desktop.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 工具参数的宽容读取：模型给出的类型经常与 Schema 不符（数字写成字符串、
 * 布尔写成 "true"），这里统一按「类型不符则回退默认值」处理，避免工具因入参格式失败。
 */

internal fun JsonObject.stringOrNull(key: String): String? =
    runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()?.trim()?.ifEmpty { null }

internal fun JsonObject.stringOr(key: String, default: String): String = stringOrNull(key) ?: default

internal fun JsonObject.intOrNull(key: String): Int? =
    runCatching { get(key)?.jsonPrimitive?.intOrNull }.getOrNull()
        ?: runCatching { get(key)?.jsonPrimitive?.contentOrNull?.trim()?.toDouble()?.toInt() }.getOrNull()

internal fun JsonObject.intOr(key: String, default: Int): Int = intOrNull(key) ?: default

internal fun JsonObject.doubleOrNull(key: String): Double? =
    runCatching { get(key)?.jsonPrimitive?.doubleOrNull }.getOrNull()
        ?: runCatching { get(key)?.jsonPrimitive?.contentOrNull?.trim()?.toDouble() }.getOrNull()

internal fun JsonObject.doubleOr(key: String, default: Double): Double = doubleOrNull(key) ?: default

internal fun JsonObject.booleanOr(key: String, default: Boolean): Boolean =
    runCatching { get(key)?.jsonPrimitive?.booleanOrNull }.getOrNull()
        ?: runCatching { get(key)?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.let { it == "true" || it == "1" || it == "yes" } }
            .getOrNull()
        ?: default

internal fun JsonObject.arrayOrNull(key: String): JsonArray? =
    runCatching { get(key) as? JsonArray }.getOrNull()

/** 读取字符串数组；元素若为对象则由 [pick] 提取文本。 */
internal fun JsonObject.stringList(key: String): List<String> =
    arrayOrNull(key)?.mapNotNull { element ->
        runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }
    }.orEmpty()

/** 数字或数字字符串。 */
internal fun JsonPrimitive.numberOrNull(): Double? =
    runCatching { doubleOrNull }.getOrNull()
        ?: runCatching { contentOrNull?.trim()?.toDouble() }.getOrNull()

internal fun JsonObject.numberOrNull(key: String): Double? =
    runCatching { get(key)?.jsonPrimitive?.numberOrNull() }.getOrNull()

internal fun JsonObject.numberOr(key: String, default: Double): Double = numberOrNull(key) ?: default
