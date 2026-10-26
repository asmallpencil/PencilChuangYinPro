package com.pencil.chuangyin.core.agent.model

import kotlinx.serialization.Serializable

/**
 * 模型发起的一次工具调用。
 *
 * [arguments] 保留服务端返回的原始 JSON 字符串：流式响应下参数是分片下发的，
 * 以字符串累积最为稳妥，回灌模型时也无需再做序列化。
 */
@Serializable
data class ToolCallRequest(
    val id: String,
    val name: String,
    val arguments: String = EMPTY_ARGUMENTS,
) {
    companion object {
        /** 空参数占位。 */
        const val EMPTY_ARGUMENTS = "{}"
    }
}
