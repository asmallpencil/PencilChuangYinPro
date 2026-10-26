package com.pencil.chuangyin.core.agent.model

/** Agent 单轮运行结果。 */
data class AgentRunResult(
    /** 运行结束时的完整会话消息（含本次新增）。 */
    val history: List<ChatMessage>,
    /** 失败时的错误；正常结束为 null。 */
    val error: AgentError? = null,
    /** 实际执行的模型调用轮数。 */
    val iterations: Int = 0,
)
