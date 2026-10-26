package com.pencil.chuangyin.core.agent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 会话角色。 */
@Serializable
enum class ChatRole {
    @SerialName("system")
    System,

    @SerialName("user")
    User,

    @SerialName("assistant")
    Assistant,

    @SerialName("tool")
    Tool,
    ;

    /** OpenAI 线上协议使用的角色名。 */
    val wireName: String get() = name.lowercase()
}
