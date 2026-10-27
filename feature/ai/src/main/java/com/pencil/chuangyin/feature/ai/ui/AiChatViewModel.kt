package com.pencil.chuangyin.feature.ai.ui

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import com.pencil.chuangyin.core.agent.runtime.AgentPermissionMode
import com.pencil.chuangyin.feature.ai.tools.Workspace
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * AI 打印任务页的状态持有者。
 *
 * 自「进程级会话」重构后，全部业务状态与任务协程由 [AiAgentSession] 持有，本类退化为薄代理：
 * 页面销毁只销毁这个代理，会话继续在后台运行，回到页面时重新订阅同一份 [AiAgentSession.state]。
 */
@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val session: AiAgentSession,
    private val workspace: Workspace,
) : ViewModel() {

    val state: StateFlow<AiChatUiState> = session.state

    init {
        session.start()
    }

    fun onInputChange(value: String) = session.onInputChange(value)

    fun send() = session.send()

    fun retry() = session.retry()

    fun stop() = session.stop()

    fun dismissError() = session.dismissError()

    fun startNewConversation() = session.startNewConversation()

    fun openConversation(id: String) = session.openConversation(id)

    fun deleteConversation(id: String) = session.deleteConversation(id)

    fun toggleHistory() = session.toggleHistory()

    fun hideHistory() = session.hideHistory()

    fun importFromUri(uri: Uri, onImported: (String) -> Unit = {}, onFailed: (String) -> Unit = {}) =
        session.importFromUri(uri, onImported, onFailed)

    fun removeAttachment(id: String) = session.removeAttachment(id)

    fun setPermissionMode(mode: AgentPermissionMode) = session.setPermissionMode(mode)

    /** 解码产物缩略图：授权输出目录时产物在 SAF 树里，需要先物化到缓存。 */
    suspend fun resolveImage(artifact: AiUiArtifact): ImageBitmap? = artifact.decodePreview(workspace)
}
