package com.pencil.chuangyin.core.agent.config

import com.pencil.chuangyin.core.agent.AgentDefaults
import kotlinx.serialization.Serializable

/**
 * 一个 OpenAI 兼容的模型配置。
 *
 * [baseUrl] 允许三种写法，[chatCompletionsUrl] 会自动归一化：
 * 1. 完整端点：`https://host/v1/chat/completions`
 * 2. 版本根地址：`https://host/v1`
 * 3. 服务根地址：`https://host`
 */
@Serializable
data class AiModelConfig(
    /** 稳定标识；为空表示尚未落库，由仓库分配。 */
    val id: String = "",
    /** 界面上展示的模型名称。 */
    val displayName: String = "",
    /** 服务链接。 */
    val baseUrl: String = "",
    /** 模型名称（请求体中的 model 字段）。 */
    val modelName: String = "",
    /** 密钥。 */
    val apiKey: String = "",
) {

    /** 归一化后的对话补全端点。 */
    fun chatCompletionsUrl(): String {
        val trimmed = baseUrl.trim().trimEnd(URL_SEPARATOR)
        return when {
            trimmed.endsWith(AgentDefaults.CHAT_COMPLETIONS_SUFFIX) -> trimmed
            trimmed.endsWith(AgentDefaults.API_VERSION_SEGMENT) ->
                trimmed + AgentDefaults.CHAT_COMPLETIONS_SUFFIX
            else -> trimmed + AgentDefaults.API_VERSION_SEGMENT + AgentDefaults.CHAT_COMPLETIONS_SUFFIX
        }
    }

    /** 配置是否足以发起请求。 */
    val isUsable: Boolean
        get() = baseUrl.isNotBlank() && modelName.isNotBlank()

    companion object {
        private const val URL_SEPARATOR = '/'
    }
}
