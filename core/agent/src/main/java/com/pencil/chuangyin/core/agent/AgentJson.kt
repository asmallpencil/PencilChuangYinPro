package com.pencil.chuangyin.core.agent

import kotlinx.serialization.json.Json

/**
 * core:agent 统一使用的 JSON 配置。
 *
 * 各厂商在 OpenAI 协议上普遍有扩展字段，故一律宽松解析；
 * 以顶层属性暴露，便于 feature 层复用而不必再建一份配置。
 */
val AgentJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}
