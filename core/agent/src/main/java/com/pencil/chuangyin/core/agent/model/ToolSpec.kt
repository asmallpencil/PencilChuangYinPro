package com.pencil.chuangyin.core.agent.model

import kotlinx.serialization.json.JsonObject

/**
 * 工具的作用范围与风险等级。
 *
 * 引擎不再按「某个工具是否写死需要确认」判定，而是结合当前权限模式
 * （[com.pencil.chuangyin.core.agent.runtime.AgentPermissionMode]）动态决定是否拦截，
 * 因此每个工具只需声明自己触碰的边界。
 */
enum class ToolPermission {
    /** 只读、无副作用：列目录、读文件、查询状态、联网检索、读取终端状态。 */
    READ,

    /** 仅在 agent 工作区内产生副作用：写文件、建目录、复制/移动、绘图、解压。 */
    WORKSPACE,

    /** 工作区之外的对外副作用或不可逆操作：删除、打印、跳转页面、下载、终端执行。 */
    DANGEROUS,
}

/** OpenAI function calling 的工具声明。 */
data class ToolSpec(
    val name: String,
    val description: String,
    /** JSON Schema 形式的参数声明。 */
    val parameters: JsonObject,
    /** 工具触碰的边界；引擎据当前权限模式决定是否要求用户确认。 */
    val permission: ToolPermission = ToolPermission.READ,
)
