package com.pencil.chuangyin.desktop.ai.chat

import com.pencil.chuangyin.desktop.ai.AgentJson
import com.pencil.chuangyin.desktop.ai.ChatMessage
import com.pencil.chuangyin.desktop.ai.ChatRole
import com.pencil.chuangyin.desktop.ai.ToolArtifact
import com.pencil.chuangyin.desktop.support.DesktopPaths
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 会话存档仓库（对齐安卓端 `ConversationStore` 的对外语义）。 */
interface ConversationStore {

    /** 历史列表快照（按更新时间倒序）。 */
    val summaries: StateFlow<List<ConversationSummary>>

    /** 重新扫描磁盘并刷新 [summaries]。 */
    suspend fun refresh(): List<ConversationSummary>

    /** 读取一段会话；不存在或损坏时返回 null。 */
    suspend fun load(id: String): Conversation?

    /** 写入（新增或覆盖）一段会话。 */
    suspend fun upsert(conversation: Conversation): Conversation

    /** 删除一段会话。 */
    suspend fun delete(id: String)

    /** 创建一段尚未落库的空会话。 */
    fun newConversation(): Conversation
}

/**
 * 基于文件的会话存档 —— 对齐安卓端 `FileConversationStore`。
 *
 * 目录为 `%APPDATA%/PencilChuangYinPro/ai_conversations`，一会话一 JSON；
 * 写入先落临时文件再 rename，避免中途退出留下半截 JSON。
 */
class FileConversationStore(
    private val directory: File? = DesktopPaths.subDir(DIR_NAME),
) : ConversationStore {

    private val mutex = Mutex()

    private val _summaries = MutableStateFlow<List<ConversationSummary>>(emptyList())

    override val summaries: StateFlow<List<ConversationSummary>> = _summaries.asStateFlow()

    override suspend fun refresh(): List<ConversationSummary> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = listFiles()
                .mapNotNull { readSummary(it) }
                .sortedByDescending { it.updatedAt }
            _summaries.value = list
            list
        }
    }

    override suspend fun load(id: String): Conversation? = withContext(Dispatchers.IO) {
        val file = fileOf(id) ?: return@withContext null
        if (!file.exists()) return@withContext null
        runCatching { AgentJson.decodeFromString(Conversation.serializer(), file.readText()) }.getOrNull()
    }

    override suspend fun upsert(conversation: Conversation): Conversation = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir = directory ?: return@withLock conversation
            val now = System.currentTimeMillis()
            val stored = conversation.copy(
                createdAt = conversation.createdAt.takeIf { it != 0L } ?: now,
                updatedAt = now,
            )
            runCatching {
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, stored.id + FILE_SUFFIX)
                val temp = File(dir, stored.id + TEMP_SUFFIX)
                temp.writeText(AgentJson.encodeToString(Conversation.serializer(), stored))
                if (target.exists() && !target.delete()) {
                    temp.delete()
                    return@withLock conversation
                }
                if (!temp.renameTo(target)) {
                    temp.delete()
                    return@withLock conversation
                }
            }
            refreshLocked()
            stored
        }
    }

    override suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            fileOf(id)?.delete()
            refreshLocked()
        }
    }

    override fun newConversation(): Conversation = Conversation(id = UUID.randomUUID().toString())

    /** 串行区内的刷新（避免重入 [refresh] 造成死锁）。 */
    private fun refreshLocked(): List<ConversationSummary> {
        val list = listFiles()
            .mapNotNull { readSummary(it) }
            .sortedByDescending { it.updatedAt }
        _summaries.value = list
        return list
    }

    private fun listFiles(): List<File> = directory
        ?.listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
        ?.toList()
        .orEmpty()

    private fun fileOf(id: String): File? {
        val dir = directory ?: return null
        if (id.isBlank()) return null
        return File(dir, id + FILE_SUFFIX)
    }

    /** 只解析摘要字段；损坏文件跳过，不让一条坏数据拖垮整个历史列表。 */
    private fun readSummary(file: File): ConversationSummary? = runCatching {
        val conversation = AgentJson.decodeFromString(Conversation.serializer(), file.readText())
        ConversationSummary(
            id = conversation.id,
            title = conversation.displayTitle(),
            messageCount = conversation.messages.count { it.role != ChatRole.System && !it.isSummary },
            updatedAt = conversation.updatedAt,
        )
    }.getOrNull()

    private companion object {
        const val DIR_NAME = "ai_conversations"
        const val FILE_SUFFIX = ".json"
        const val TEMP_SUFFIX = ".json.tmp"
    }
}

/** 构造一条用户消息（对齐安卓端 `newUserMessage`）。 */
fun newUserMessage(
    content: String,
    attachments: List<ToolArtifact> = emptyList(),
): ChatMessage = ChatMessage(
    id = "user_" + UUID.randomUUID(),
    role = ChatRole.User,
    content = content,
    attachments = attachments,
    createdAt = System.currentTimeMillis(),
)
