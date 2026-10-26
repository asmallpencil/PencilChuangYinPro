package com.pencil.chuangyin.desktop.ai.chat

import com.pencil.chuangyin.desktop.ai.ChatMessage
import com.pencil.chuangyin.desktop.ai.ChatRole
import kotlinx.serialization.Serializable

/**
 * 一段完整会话 —— 对齐安卓端 `Conversation`：
 * 标题可空，展示时回落到首条用户消息的截断文本。
 */
@Serializable
data class Conversation(
    val id: String,
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {

    /** 由首条用户消息派生的标题；无用户消息时为空串。 */
    fun derivedTitle(): String {
        val first = messages.firstOrNull { it.role == ChatRole.User && it.content.isNotBlank() }
        val raw = first?.content?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val singleLine = raw.replace('\n', ' ').trim()
        return if (singleLine.length > TITLE_MAX_CHARS) singleLine.take(TITLE_MAX_CHARS) else singleLine
    }

    /** 最终展示标题：用户命名优先，其次派生标题。 */
    fun displayTitle(): String = title.ifBlank { derivedTitle() }

    companion object {
        /** 派生标题的最大字符数。 */
        const val TITLE_MAX_CHARS = 24
    }
}

/** 历史列表用的会话摘要（不含消息正文，避免列表页读全量文件）。 */
data class ConversationSummary(
    val id: String,
    val title: String,
    val messageCount: Int,
    val updatedAt: Long,
)
