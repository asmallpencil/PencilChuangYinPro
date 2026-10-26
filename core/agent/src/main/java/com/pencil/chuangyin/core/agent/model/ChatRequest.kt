package com.pencil.chuangyin.core.agent.model

/** 一次对话补全请求。 */
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolSpec> = emptyList(),
    val temperature: Double? = null,
    val maxTokens: Int? = null,
)
