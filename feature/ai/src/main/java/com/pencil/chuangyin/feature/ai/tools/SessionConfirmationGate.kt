package com.pencil.chuangyin.feature.ai.tools

import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ChatRole
import com.pencil.chuangyin.core.agent.runtime.ToolConfirmationGate
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * 会话级确认门：落实 [com.pencil.chuangyin.core.agent.tools.Tool] 的 requireConfirmation 语义。
 *
 * 由于流式输出期间输入被忽略，确认采用「轮次边界」协议：
 * 1. 模型首次调用不可逆工具 → 本门拦截并登记摘要与拦截时间；
 * 2. 引擎把拦截原因回灌给模型，模型向用户说明并请求确认；
 * 3. 用户在聊天中回复（同意/拒绝）；
 * 4. 模型下一轮以相同参数再次调用 → 本门校验「拦截之后出现过真实用户消息」，
 *    且该消息通过否定词/同意词两层判定后放行；否则继续拦截。
 *
 * 摘要 = 工具名 + 参数的哈希，保证「相同参数的再次调用」才能匹配登记记录。
 */
@Singleton
class SessionConfirmationGate @Inject constructor() : ToolConfirmationGate {

    private val mutex = Mutex()

    /** 摘要 → 拦截时间戳；LRU 淘汰，防止长会话内存增长。 */
    private val pending = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean =
            size > MAX_PENDING
    }

    override suspend fun requestConfirmation(
        toolName: String,
        arguments: JsonObject,
        transcript: List<ChatMessage>,
    ): Boolean = mutex.withLock {
        val digest = digest(toolName, arguments)
        val interceptedAt = pending[digest]

        if (interceptedAt == null) {
            // 首次调用：登记并拦截。
            pending[digest] = System.currentTimeMillis()
            return@withLock false
        }

        val now = System.currentTimeMillis()
        if (now - interceptedAt > PENDING_TTL_MS) {
            // 登记已过期：作废并重新拦截（下次调用重新走确认流程）。
            pending.remove(digest)
            pending[digest] = now
            return@withLock false
        }

        // 必须是拦截之后出现的真实用户消息（忽略摘要与引擎合成消息）。
        val latestUser = transcript.lastOrNull { message ->
            message.role == ChatRole.User && !message.isSummary && !message.synthetic
        }
        if (latestUser == null || latestUser.createdAt <= interceptedAt) return@withLock false

        val reply = latestUser.content.trim()
        if (NEGATIVE_WORDS.any { reply.contains(it, ignoreCase = true) }) {
            // 用户明确拒绝：作废登记，模型不应再调用。
            pending.remove(digest)
            return@withLock false
        }
        if (APPROVAL_WORDS.any { reply.contains(it, ignoreCase = true) }) {
            pending.remove(digest)
            return@withLock true
        }
        // 意图不明：保持登记，等待用户进一步澄清。
        false
    }

    private fun digest(toolName: String, arguments: JsonObject): String {
        val bytes = MessageDigest.getInstance("MD5")
            .digest((toolName + '\u0000' + arguments.toString()).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_PENDING = 64
        const val PENDING_TTL_MS = 10 * 60 * 1000L

        /** 否定词优先判定：先排除拒绝，再看同意，避免「不好」「别打印了」误判。 */
        val NEGATIVE_WORDS = listOf(
            "取消", "算了", "不要", "不行", "不可以", "不对", "别", "停下", "停止", "no", "cancel", "stop",
        )
        val APPROVAL_WORDS = listOf(
            "确认", "同意", "可以", "好的", "好", "行", "嗯", "是的", "对", "ok", "yes", "继续", "执行", "go",
        )
    }
}
