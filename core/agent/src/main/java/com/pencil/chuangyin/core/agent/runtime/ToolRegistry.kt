package com.pencil.chuangyin.core.agent.runtime

import com.pencil.chuangyin.core.agent.model.AgentTool
import com.pencil.chuangyin.core.agent.model.ToolSpec
import javax.inject.Inject
import javax.inject.Singleton

/** 已注册工具的索引。 */
@Singleton
class ToolRegistry @Inject constructor(
    tools: Set<@JvmSuppressWildcards AgentTool>,
) {

    private val byName: Map<String, AgentTool> = tools.associateBy { it.spec.name }

    /** 按名称升序排列的工具声明，保证请求体稳定可缓存。 */
    val specs: List<ToolSpec> = tools.map { it.spec }.sortedBy { it.name }

    /** 已注册工具数量。 */
    val size: Int get() = byName.size

    /** 按名称查找工具。 */
    fun find(name: String): AgentTool? = byName[name]
}
