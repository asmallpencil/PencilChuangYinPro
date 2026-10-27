package com.pencil.chuangyin.core.agent.config

import com.pencil.chuangyin.core.agent.AgentDefaults
import kotlinx.serialization.Serializable

/**
 * 一份模型配置。
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
