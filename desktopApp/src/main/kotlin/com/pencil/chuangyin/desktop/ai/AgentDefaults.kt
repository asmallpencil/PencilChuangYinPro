package com.pencil.chuangyin.desktop.ai

/**
 * Agent 运行时默认参数，与安卓端保持一致。
 */
object AgentDefaults {

    /** OpenAI 兼容的对话补全端点后缀。 */
    const val CHAT_COMPLETIONS_SUFFIX = "/chat/completions"

    /** OpenAI 兼容的 API 版本段落。 */
    const val API_VERSION_SEGMENT = "/v1"

    /** 采样温度默认值。 */
    const val TEMPERATURE = 0.6

    /** 单次回复的最大 token 数。 */
    const val MAX_TOKENS = 4096

    /** 单轮任务的工具调用迭代上限。 */
    const val MAX_TOOL_ITERATIONS = 24

    /** 触发上下文折叠的消息总字符数阈值。 */
    const val CONTEXT_FOLD_THRESHOLD_CHARS = 48_000

    /** 折叠后保留的最近消息条数。 */
    const val CONTEXT_KEEP_RECENT = 16

    /** 送入摘要模型的对话文本最大字符数。 */
    const val SUMMARY_INPUT_MAX_CHARS = 32_000

    /** 工具结果回灌模型时的最大字符数。 */
    const val TOOL_RESULT_MAX_CHARS = 8_000

    /** 连通性检查（测试连接）的最大 token 数。 */
    const val PING_MAX_TOKENS = 16

    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 60L
    const val READ_TIMEOUT_STREAM_SECONDS = 0L
    const val READ_TIMEOUT_SYNC_SECONDS = 120L
}
