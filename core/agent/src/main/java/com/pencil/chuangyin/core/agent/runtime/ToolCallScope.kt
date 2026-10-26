package com.pencil.chuangyin.core.agent.runtime

import com.pencil.chuangyin.core.agent.model.ToolContext

/**
 * 当前工具调用的会话上下文持有者。
 *
 * 官方 ADK for Kotlin 的 @Tool 函数由 KSP 生成 [com.google.adk.kt.tools.FunctionTool] 包装器，
 * 其 execute 只能注入官方 ToolContext，无法携带项目侧的会话/进度能力；适配器在调用前后把
 * 项目 [ToolContext] 挂到本持有者上，工具函数体通过注入本接口读取。
 */
interface ToolCallScope {

    /** 当前正在执行的工具调用上下文；调用间隙为 null。 */
    val current: ToolContext?
}

/** [ToolCallScope] 的进程级实现。Agent 引擎串行执行工具调用，直接以可变字段承载。 */
@javax.inject.Singleton
class MutableToolCallScope @javax.inject.Inject constructor() : ToolCallScope {

    @Volatile
    override var current: ToolContext? = null
}
