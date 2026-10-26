package com.pencil.chuangyin.core.agent.model

import kotlinx.serialization.json.JsonObject

/**
 * Agent 可调用的工具。
 *
 * 实现类通过 Hilt `@IntoSet` 注册，由 [com.pencil.chuangyin.core.agent.runtime.ToolRegistry] 汇总。
 */
interface AgentTool {

    /** 工具声明。 */
    val spec: ToolSpec

    /**
     * 执行工具。
     *
     * @param arguments 模型给出的 JSON 参数，始终为对象。
     * @return 执行结果；失败时返回 [ToolOutput.isError] 为 true 的结果而非抛异常。
     */
    suspend fun invoke(arguments: JsonObject, context: ToolContext): ToolOutput
}
