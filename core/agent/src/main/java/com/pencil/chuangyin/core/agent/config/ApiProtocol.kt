package com.pencil.chuangyin.core.agent.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 上游服务商协议。
 *
 * 应用内部统一使用一套会话模型（[com.pencil.chuangyin.core.agent.model.ChatMessage]），
 * 由各协议客户端负责与各自线格式互转，因此上层无需关心上游差异。
 */
@Serializable
enum class ApiProtocol {

    /** OpenAI Chat Completions；各类兼容网关（DeepSeek、通义、Kimi、Ollama 等）同属此类。 */
    @SerialName("openai")
    OPENAI,

    /** Anthropic Messages API。 */
    @SerialName("anthropic")
    ANTHROPIC,

    /** Google Gemini generateContent API。 */
    @SerialName("gemini")
    GEMINI,
    ;

    /** 新增配置时的默认服务地址。 */
    val defaultBaseUrl: String
        get() = when (this) {
            OPENAI -> "https://api.openai.com/v1"
            ANTHROPIC -> "https://api.anthropic.com"
            GEMINI -> "https://generativelanguage.googleapis.com"
        }

    /** 界面展示名。 */
    val displayName: String
        get() = when (this) {
            OPENAI -> "OpenAI 兼容"
            ANTHROPIC -> "Anthropic Claude"
            GEMINI -> "Google Gemini"
        }

    /** 分段控件等窄空间使用的短名。 */
    val shortName: String
        get() = when (this) {
            OPENAI -> "OpenAI"
            ANTHROPIC -> "Claude"
            GEMINI -> "Gemini"
        }
}
