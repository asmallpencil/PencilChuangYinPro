package com.pencil.chuangyin.core.agent.model

/** 错误类别。 */
enum class AgentErrorKind {
    /** 尚未完成模型配置。 */
    MissingConfig,

    /** 网络或 IO 失败。 */
    Network,

    /** 服务端返回非 2xx。 */
    Http,

    /** 响应不符合 OpenAI 协议。 */
    Protocol,

    /** 工具执行失败。 */
    Tool,

    /** 用户取消。 */
    Cancelled,

    /** 其他未归类错误。 */
    Unknown,
}

/** Agent 运行错误。 */
data class AgentError(
    val kind: AgentErrorKind,
    val message: String,
    val detail: String? = null,
)
