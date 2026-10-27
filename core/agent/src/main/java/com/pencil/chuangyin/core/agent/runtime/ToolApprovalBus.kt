package com.pencil.chuangyin.core.agent.runtime

import com.pencil.chuangyin.core.agent.model.ToolPermission
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 工具执行审批总线：把「引擎即将执行某个需要确认的工具」直接推给界面。
 *
 * 旧实现（[ToolConfirmationGate] 内部用「参数哈希 + 下一轮文本同意词」判定）有一个无法满足的前提：
 * 模型被拦截后几乎不会以**完全相同的参数**重新调用同一工具，因此终端类工具（`run_command`）
 * 在每个新指令上都被反复拦截，表现为「AI 终端每次都失败」。
 *
 * 本总线的协议改为「当场点按钮」：
 * 1. 引擎执行需要确认的工具前调用 [request]，协程在此**挂起**（不阻塞主线程）；
 * 2. 界面订阅 [pending] 弹出对话框，用户点「允许 / 拒绝」后调用 [decide]；
 * 3. 用户允许的工具名记入 [approvedTools]（会话级白名单），本会话内同一工具不再重复询问；
 * 4. 用户长时间不响应（[TIMEOUT_MS]）按拒绝收尾，绝不永久挂起任务。
 */
@Singleton
class ToolApprovalBus @Inject constructor() {

    /** 一次等待用户决定的审批请求。 */
    data class PendingApproval(
        val id: String,
        val toolName: String,
        /** 模型给出的原始参数 JSON（界面折叠展示，便于用户判断）。 */
        val arguments: String,
        val permission: ToolPermission,
        val createdAt: Long,
    )

    private val _pending = MutableStateFlow<PendingApproval?>(null)

    /** 当前待审批请求；null 表示没有请求在等。 */
    val pending: StateFlow<PendingApproval?> = _pending.asStateFlow()

    private val _approvedTools = MutableStateFlow<Set<String>>(emptySet())

    /** 本会话内已被允许、后续免再询问的工具名。 */
    val approvedTools: StateFlow<Set<String>> = _approvedTools.asStateFlow()

    /** 挂起请求的持有者；用 [lock] 保护，[decide] 从主线程调用、[request] 在引擎协程中调用。 */
    private val lock = Any()
    private var current: PendingApproval? = null
    private var waiter: CompletableDeferred<Boolean>? = null

    /**
     * 请求执行许可；挂起直到用户决定或超时。
     *
     * @return true 表示允许执行。
     */
    suspend fun request(toolName: String, arguments: String, permission: ToolPermission): Boolean {
        if (_approvedTools.value.contains(toolName)) return true

        val approval = PendingApproval(
            id = "approval_" + UUID.randomUUID(),
            toolName = toolName,
            arguments = arguments,
            permission = permission,
            createdAt = System.currentTimeMillis(),
        )
        val deferred = CompletableDeferred<Boolean>()
        synchronized(lock) {
            // 引擎串行执行工具，理论上不会并发；万一发生，旧请求按拒绝收尾，避免永久挂起。
            waiter?.complete(false)
            current = approval
            waiter = deferred
        }
        _pending.value = approval

        val decision = withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }

        synchronized(lock) {
            if (current?.id == approval.id) {
                current = null
                waiter = null
            }
        }
        if (_pending.value?.id == approval.id) _pending.value = null

        val approved = decision == true
        if (approved) _approvedTools.value = _approvedTools.value + toolName
        return approved
    }

    /** 界面点「允许 / 拒绝」；id 不匹配时忽略，防止旧弹窗误判新请求。 */
    fun decide(id: String, approved: Boolean) {
        val deferred = synchronized(lock) {
            if (current?.id != id) return
            val target = waiter
            current = null
            waiter = null
            target
        }
        if (_pending.value?.id == id) _pending.value = null
        deferred?.complete(approved)
    }

    /** 任务被用户中断时撤掉挂起请求（按拒绝收尾）。 */
    fun cancelPending() {
        val id = synchronized(lock) { current?.id } ?: return
        decide(id, false)
    }

    /** 切换 / 新建会话时清空会话级白名单，危险工具重新征询同意。 */
    fun resetSession() {
        val deferred = synchronized(lock) {
            val target = waiter
            current = null
            waiter = null
            target
        }
        _pending.value = null
        _approvedTools.value = emptySet()
        deferred?.complete(false)
    }

    private companion object {
        /** 用户决策等待上限：超过则按拒绝收尾，引擎会把「未获批准」回灌给模型。 */
        const val TIMEOUT_MS = 120_000L
    }
}
