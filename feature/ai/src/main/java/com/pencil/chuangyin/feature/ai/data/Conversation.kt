package com.pencil.chuangyin.feature.ai.data

import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ChatRole
import kotlinx.serialization.Serializable

/**
 * 一段可持久化的 AI 会话。
 *
 * [messages] 保存的是「不含系统提示词」的会话消息：系统提示词每轮由
 * [com.pencil.chuangyin.feature.ai.AiPrompts] 依据当前工作区与打印状态动态生成，
 * 因此不落库，避免路径/状态变化后仍使用陈旧提示。
 */
@Serializable
data class Conversation(
    /** 稳定标识，同时作为落盘文件名。 */
    val id: String,
    /** 用户可见标题；为空时由首条用户消息派生。 */
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {

    /** 由首条用户消息派生的标题；无法派生时返回空串，由界面回退到默认文案。 */
    fun derivedTitle(): String {
        val first = messages.firstOrNull { it.role == ChatRole.User && it.content.isNotBlank() }
        val raw = first?.content?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val singleLine = raw.replace('\n', ' ').trim()
        return if (singleLine.length > TITLE_MAX_CHARS) singleLine.take(TITLE_MAX_CHARS) else singleLine
    }

    /** 列表展示标题：显式标题优先，其次派生，最后为空串。 */
    fun displayTitle(): String = title.ifBlank { derivedTitle() }

    companion object {
        /** 列表标题的最大字符数。 */
        const val TITLE_MAX_CHARS = 24
    }
}

/** 会话列表项（轻量，不含完整消息体）。 */
data class ConversationSummary(
    val id: String,
    /** 派生后的展示标题；可能为空串。 */
    val title: String,
    val messageCount: Int,
    val updatedAt: Long,
)
