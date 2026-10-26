package com.pencil.chuangyin.core.agent.model

import kotlinx.serialization.Serializable

/**
 * 会话中的一条消息。
 *
 * - [reasoning]：深度思考内容，仅用于界面展示，不回灌模型（多数服务端会拒绝该字段）。
 * - [toolCalls]：助手消息发起的工具调用。
 * - [toolCallId]/[toolName]：角色为 [ChatRole.Tool] 时，对应被执行的工具调用。
 * - [isSummary]：标记该条为上下文折叠产生的历史摘要。
 * - [attachments]：随消息携带的图片/文件（用户上传或工具产出）；图片会以多模态
 *   `image_url` 形式随请求发送，使模型能直接「看到」图片内容。
 */
@Serializable
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String = "",
    val reasoning: String? = null,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val createdAt: Long = 0L,
    val isError: Boolean = false,
    val isSummary: Boolean = false,
    val attachments: List<ToolArtifact> = emptyList(),
    /** 引擎注入的合成消息（如工具图片的多模态载体），并非真实用户输入；确认门等逻辑应忽略。 */
    val synthetic: Boolean = false,
) {
    /** 用于上下文体积估算的字符数。 */
    val estimatedChars: Int
        get() = content.length + (reasoning?.length ?: 0) +
            toolCalls.sumOf { it.name.length + it.arguments.length }
}
