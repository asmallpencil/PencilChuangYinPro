package com.pencil.chuangyin.feature.ai.data

import android.content.Context
import com.pencil.chuangyin.core.agent.AgentJson
import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ChatRole
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/**
 * 会话存储。
 *
 * 每个会话一个 JSON 文件，落在应用私有目录 `filesDir/ai_conversations/` 下，
 * 从而支持「持续任务」：退出页面、杀进程后再次进入仍可继续同一段对话。
 */
interface ConversationStore {

    /** 会话摘要列表（按更新时间倒序）。 */
    val summaries: StateFlow<List<ConversationSummary>>

    /** 重新扫描磁盘并刷新 [summaries]。 */
    suspend fun refresh(): List<ConversationSummary>

    /** 读取指定会话；不存在或损坏时返回 null。 */
    suspend fun load(id: String): Conversation?

    /** 写入（新增或覆盖）会话，返回补齐时间戳后的结果。 */
    suspend fun upsert(conversation: Conversation): Conversation

    /** 删除指定会话。 */
    suspend fun delete(id: String)

    /** 构造一个尚未落库的空会话。 */
    fun newConversation(): Conversation
}

/**
 * 基于文件的 [ConversationStore] 实现。
 *
 * 写入采用「临时文件 → 重命名」的原子替换，避免断电/崩溃产生半截 JSON；
 * 所有磁盘操作串行化在 [mutex] 内，防止并发写同一文件。
 */
@Singleton
class FileConversationStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConversationStore {

    private val mutex = Mutex()

    private val _summaries = MutableStateFlow<List<ConversationSummary>>(emptyList())
    override val summaries: StateFlow<List<ConversationSummary>> = _summaries.asStateFlow()

    private val directory: File
        get() = File(context.filesDir, DIRECTORY_NAME).apply { if (!exists()) mkdirs() }

    override fun newConversation(): Conversation {
        val now = System.currentTimeMillis()
        return Conversation(
            id = UUID.randomUUID().toString(),
            messages = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
    }

    override suspend fun refresh(): List<ConversationSummary> = withContext(Dispatchers.IO) {
        val list = directory.listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
            .orEmpty()
            .mapNotNull { readSummary(it) }
            .sortedByDescending { it.updatedAt }
        _summaries.value = list
        list
    }

    override suspend fun load(id: String): Conversation? = withContext(Dispatchers.IO) {
        val file = File(directory, id + FILE_SUFFIX)
        if (!file.exists()) return@withContext null
        runCatching { AgentJson.decodeFromString<Conversation>(file.readText()) }.getOrNull()
    }

    override suspend fun upsert(conversation: Conversation): Conversation {
        val now = System.currentTimeMillis()
        val stamped = conversation.copy(
            createdAt = if (conversation.createdAt == 0L) now else conversation.createdAt,
            updatedAt = now,
        )
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val target = File(directory, stamped.id + FILE_SUFFIX)
                val temp = File(directory, stamped.id + TEMP_SUFFIX)
                temp.writeText(AgentJson.encodeToString(stamped))
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
        }
        refresh()
        return stamped
    }

    override suspend fun delete(id: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                File(directory, id + FILE_SUFFIX).delete()
            }
        }
        refresh()
    }

    /** 只解析出摘要所需字段；损坏文件直接跳过，不影响其余会话。 */
    private fun readSummary(file: File): ConversationSummary? = runCatching {
        val conversation = AgentJson.decodeFromString<Conversation>(file.readText())
        ConversationSummary(
            id = conversation.id,
            title = conversation.displayTitle(),
            messageCount = conversation.messages.count { it.role != ChatRole.System && !it.isSummary },
            updatedAt = conversation.updatedAt,
        )
    }.getOrNull()

    private companion object {
        const val DIRECTORY_NAME = "ai_conversations"
        const val FILE_SUFFIX = ".json"
        const val TEMP_SUFFIX = ".json.tmp"
    }
}

/** 便于外部构造一条用户消息；可携带用户挂载的图片/文件附件。 */
internal fun newUserMessage(content: String, attachments: List<ToolArtifact> = emptyList()): ChatMessage = ChatMessage(
    id = "user_" + UUID.randomUUID(),
    role = ChatRole.User,
    content = content,
    attachments = attachments,
    createdAt = System.currentTimeMillis(),
)
