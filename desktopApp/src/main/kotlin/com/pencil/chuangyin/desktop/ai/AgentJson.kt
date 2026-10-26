package com.pencil.chuangyin.desktop.ai

import kotlinx.serialization.json.Json

/**
 * Agent 统一使用的 JSON 配置：各厂商在 OpenAI 协议上普遍有扩展字段，故一律宽松解析。
 */
val AgentJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}
