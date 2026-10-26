package com.pencil.chuangyin.feature.pixiv.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.feature.pixiv.data.PixivRepository
import com.pencil.chuangyin.feature.pixiv.domain.PixivComment
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.printer.ShotTransferBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** 详情页一次性事件：已转入打印页 / 已加入下载队列 / 操作失败。 */
sealed interface PixivDetailEvent {
    data object Printed : PixivDetailEvent
    data object DownloadQueued : PixivDetailEvent
    data object ActionFailed : PixivDetailEvent
}

sealed interface DetailLoadState {
    data object Loading : DetailLoadState
    data object Error : DetailLoadState
    data class Content(val illust: PixivIllust) : DetailLoadState
}

data class PixivDetailUiState(
    val loadState: DetailLoadState = DetailLoadState.Loading,
    val related: List<PixivIllust> = emptyList(),
    val relatedNextUrl: String? = null,
    val loadingRelatedMore: Boolean = false,
    val bookmarkBusy: Boolean = false,
    val printing: Boolean = false,
    val printedDone: Int = 0,
    val printedTotal: Int = 0,
    val comments: List<PixivComment> = emptyList(),
    val commentsNextUrl: String? = null,
    val loadingComments: Boolean = false,
    /** 评论拉取失败（用于展示「加载失败 + 重试」，而不是伪装的「暂无评论」）。 */
    val commentsError: Boolean = false,
    val commentsInput: String = "",
    val postingComment: Boolean = false,
)

@HiltViewModel
class PixivDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PixivRepository,
    private val shotTransferBus: ShotTransferBus,
) : ViewModel() {

    val illustId: Int = savedStateHandle.get<Int>(NAV_ARG_ILLUST_ID) ?: -1

    private val _state = MutableStateFlow(PixivDetailUiState())
    val state: StateFlow<PixivDetailUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PixivDetailEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PixivDetailEvent> = _events.asSharedFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        if (illustId <= 0) {
            _state.update { it.copy(loadState = DetailLoadState.Error) }
            return
        }
        _state.update { it.copy(loadState = DetailLoadState.Loading) }
        viewModelScope.launch {
            val detailResult = runCatching { repository.detail(illustId) }
            detailResult.fold(
                onSuccess = { illust ->
                    _state.update { it.copy(loadState = DetailLoadState.Content(illust)) }
                    // 进入详情即写入本地浏览历史（侧边栏「历史记录」数据源）。
                    runCatching { repository.recordHistory(illust) }
                    loadRelated()
                    loadComments()
                },
                onFailure = {
                    _state.update { it.copy(loadState = DetailLoadState.Error) }
                },
            )
        }
    }

    private fun loadRelated() {
        viewModelScope.launch {
            val result = runCatching { repository.related(illustId) }
            result.onSuccess { page ->
                _state.update { it.copy(related = page.items, relatedNextUrl = page.nextUrl) }
            }
        }
    }

    fun loadRelatedMore() {
        val url = _state.value.relatedNextUrl ?: return
        if (_state.value.loadingRelatedMore) return
        _state.update { it.copy(loadingRelatedMore = true) }
        viewModelScope.launch {
            val result = runCatching { repository.nextPage(url) }
            _state.update {
                result.fold(
                    onSuccess = { page ->
                        it.copy(
                            related = it.related + page.items,
                            relatedNextUrl = page.nextUrl,
                            loadingRelatedMore = false,
                        )
                    },
                    onFailure = { _ -> it.copy(loadingRelatedMore = false) },
                )
            }
        }
    }

    // ── 评论区 ─────────────────────────────────────────────────────────────────

    private fun loadComments() {
        if (_state.value.loadingComments) return
        _state.update { it.copy(loadingComments = true, commentsError = false) }
        viewModelScope.launch {
            val result = runCatching { repository.comments(illustId) }
            _state.update {
                result.fold(
                    onSuccess = { page ->
                        it.copy(
                            comments = page.items,
                            commentsNextUrl = page.nextUrl,
                            loadingComments = false,
                            commentsError = false,
                        )
                    },
                    // 失败必须显式暴露：否则界面会退化成「暂无评论」，看起来像接口没有评论。
                    onFailure = { _ -> it.copy(loadingComments = false, commentsError = true) },
                )
            }
        }
    }

    /** 评论区重试（UI 的「加载失败 / 重试」入口）。 */
    fun retryComments() = loadComments()

    fun loadCommentsMore() {
        val url = _state.value.commentsNextUrl ?: return
        if (_state.value.loadingComments) return
        _state.update { it.copy(loadingComments = true) }
        viewModelScope.launch {
            val result = runCatching { repository.commentNextPage(url) }
            _state.update {
                result.fold(
                    onSuccess = { page ->
                        it.copy(
                            comments = it.comments + page.items,
                            commentsNextUrl = page.nextUrl,
                            loadingComments = false,
                        )
                    },
                    onFailure = { _ -> it.copy(loadingComments = false) },
                )
            }
        }
    }

    fun onCommentInputChange(value: String) =
        _state.update { it.copy(commentsInput = value) }

    fun postComment() {
        val text = _state.value.commentsInput.trim()
        if (text.isBlank() || _state.value.postingComment) return
        _state.update { it.copy(postingComment = true) }
        viewModelScope.launch {
            val result = runCatching { repository.addComment(illustId, text) }
            val posted = result.getOrNull()
            _state.update {
                if (result.isFailure) {
                    // 失败保留输入内容，方便用户直接重发。
                    it.copy(postingComment = false)
                } else {
                    val list = if (posted != null && it.comments.none { c -> c.id == posted.id }) {
                        it.comments + posted
                    } else {
                        it.comments
                    }
                    it.copy(postingComment = false, commentsInput = "", comments = list, commentsError = false)
                }
            }
            if (result.isFailure) {
                _events.tryEmit(PixivDetailEvent.ActionFailed)
            } else if (posted == null) {
                // 接口未回执评论体：重新拉取第一页兜底。
                loadComments()
            }
        }
    }

    fun toggleBookmark() {
        val content = _state.value.loadState as? DetailLoadState.Content ?: return
        if (_state.value.bookmarkBusy) return
        val target = !content.illust.isBookmarked
        _state.update { it.copy(bookmarkBusy = true) }
        viewModelScope.launch {
            val result = runCatching {
                repository.setBookmarked(illustId, target)
                // 与本地收藏页同步：成功后写入/移除快照。
                repository.syncBookmark(content.illust, target)
            }
            _state.update { current ->
                result.fold(
                    onSuccess = {
                        val illust = (current.loadState as DetailLoadState.Content).illust
                        val delta = if (target) 1 else -1
                        current.copy(
                            bookmarkBusy = false,
                            loadState = DetailLoadState.Content(
                                illust.copy(
                                    isBookmarked = target,
                                    totalBookmarks = (illust.totalBookmarks + delta).coerceAtLeast(0),
                                ),
                            ),
                        )
                    },
                    onFailure = { current.copy(bookmarkBusy = false) },
                )
            }
            if (result.isFailure) _events.tryEmit(PixivDetailEvent.ActionFailed)
        }
    }

    /** 下载全部原图 → ShotTransferBus → 通知 UI 跳到图片打印页（零偏差复用打印设置）。 */
    fun printAll() {
        val illust = (_state.value.loadState as? DetailLoadState.Content)?.illust ?: return
        if (_state.value.printing) return
        _state.update { it.copy(printing = true, printedDone = 0, printedTotal = illust.pageCount) }
        viewModelScope.launch {
            var files: List<File>? = null
            var failure: Throwable? = null
            runCatching {
                files = repository.downloadForPrint(illust) { done, total ->
                    _state.update { it.copy(printedDone = done, printedTotal = total) }
                }
            }.onFailure { failure = it }

            val delivered = files?.takeIf { it.isNotEmpty() }?.let {
                shotTransferBus.offer(it)
                true
            } ?: false

            _state.update { it.copy(printing = false) }
            if (delivered && failure == null) {
                _events.tryEmit(PixivDetailEvent.Printed)
            } else {
                _events.tryEmit(PixivDetailEvent.ActionFailed)
            }
        }
    }

    /** 长期保存：把全部原图加入下载队列（侧边栏「下载进度/图片管理」可见）。 */
    fun downloadAll() {
        val illust = (_state.value.loadState as? DetailLoadState.Content)?.illust ?: return
        repository.enqueueDownload(illust)
        _events.tryEmit(PixivDetailEvent.DownloadQueued)
    }

    companion object {
        const val NAV_ARG_ILLUST_ID = "illustId"
    }
}
