package com.pencil.chuangyin.core.agent.runtime

import com.pencil.chuangyin.core.agent.model.ToolPermission
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 权限模式：决定 Agent 在无人干预的情况下能做多少事。
 *
 * 由用户在聊天界面输入框旁切换，引擎每执行一个工具前按最新模式实时判定。
 */
enum class AgentPermissionMode {
    /** 每次审批：任何会产生副作用的操作（写入、删除、打印、终端、下载等）都需用户确认。 */
    ALWAYS_ASK,

    /** 工作区外需审批：工作区内读写自动放行，危险操作（删除、打印、终端、下载等）需确认；联网检索属只读，免确认。 */
    OUTSIDE_WORKSPACE_ASK,

    /** 自主模式（危险）：所有操作无需确认，Agent 全权执行。 */
    AUTONOMOUS,
}

/** 当前权限模式的可观测来源。 */
interface AgentPermissionModeProvider {

    /** 当前权限模式。 */
    val mode: StateFlow<AgentPermissionMode>
}

/**
 * 进程内可变的权限模式持有者。
 *
 * core:agent 只负责「引擎读到的模式」，持久化与界面入口由宿主功能模块驱动：
 * feature:ai 注入本类，把用户选择落库并在启动时回填。
 */
@Singleton
class MutableAgentPermissionModeProvider @Inject constructor() : AgentPermissionModeProvider {

    private val _mode = MutableStateFlow(AgentPermissionMode.OUTSIDE_WORKSPACE_ASK)
    override val mode: StateFlow<AgentPermissionMode> = _mode.asStateFlow()

    /** 更新当前模式（宿主在用户切换或启动回填时调用）。 */
    fun set(value: AgentPermissionMode) {
        _mode.value = value
    }
}

/**
 * 按权限模式判定一次工具调用是否需要用户确认。
 *
 * 规则：
 * - [AgentPermissionMode.ALWAYS_ASK]：除只读工具外一律需确认；
 * - [AgentPermissionMode.OUTSIDE_WORKSPACE_ASK]：仅工作区外（[ToolPermission.DANGEROUS]）需确认；
 * - [AgentPermissionMode.AUTONOMOUS]：一律放行。
 */
@Singleton
class AgentPermissionPolicy @Inject constructor(
    private val provider: AgentPermissionModeProvider,
) {

    /** 该工具在当前模式下是否需要先取得用户确认。 */
    fun requiresConfirmation(permission: ToolPermission): Boolean = when (provider.mode.value) {
        AgentPermissionMode.ALWAYS_ASK -> permission != ToolPermission.READ
        AgentPermissionMode.OUTSIDE_WORKSPACE_ASK -> permission == ToolPermission.DANGEROUS
        AgentPermissionMode.AUTONOMOUS -> false
    }
}
