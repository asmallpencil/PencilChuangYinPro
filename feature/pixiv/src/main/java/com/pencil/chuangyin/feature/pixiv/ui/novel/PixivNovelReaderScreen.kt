package com.pencil.chuangyin.feature.pixiv.ui.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.ui.components.AppFilterChip
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.reader.AppReader
import com.pencil.chuangyin.core.ui.components.reader.ReaderMode
import com.pencil.chuangyin.feature.pixiv.data.PixivRepository
import com.pencil.chuangyin.feature.pixiv.domain.PixivNovel
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenError
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenLoading
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivSubScaffold
import com.pencil.chuangyin.printer.TextTransferBus
import com.pencil.chuangyin.printer.TextTransferPayload
import com.pencil.chuangyin.resources.R as ResR
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NovelReaderState {
    data object Loading : NovelReaderState
    data object Error : NovelReaderState
    data class Content(val novel: PixivNovel, val text: String) : NovelReaderState
}

/** 阅读页一次性 UI 事件。 */
sealed interface NovelReaderEvent {
    /** 正文已送入文本打印总线，导航到文本打印页。 */
    data object PrintRequested : NovelReaderEvent

    /** 小说已保存到本地。 */
    data object DownloadQueued : NovelReaderEvent

    /** 收藏切换或保存失败。 */
    data object ActionFailed : NovelReaderEvent
}

@HiltViewModel
class PixivNovelReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PixivRepository,
    private val textTransferBus: TextTransferBus,
) : ViewModel() {

    private val novelId: Int = savedStateHandle.get<Int>(NAV_ARG_NOVEL_ID) ?: -1

    private val _state = MutableStateFlow<NovelReaderState>(NovelReaderState.Loading)
    val state: StateFlow<NovelReaderState> = _state.asStateFlow()

    private val _bookmarkBusy = MutableStateFlow(false)
    val bookmarkBusy: StateFlow<Boolean> = _bookmarkBusy.asStateFlow()

    private val _events = MutableSharedFlow<NovelReaderEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<NovelReaderEvent> = _events.asSharedFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        if (novelId <= 0) {
            _state.value = NovelReaderState.Error
            return
        }
        _state.value = NovelReaderState.Loading
        viewModelScope.launch {
            // 详情与正文并行拉取；正文主走 webview/v2/novel，仓库层内置旧端点兜底。
            val result = runCatching {
                val novelDeferred = async { repository.novelDetail(novelId) }
                val textDeferred = async { repository.novelText(novelId) }
                novelDeferred.await() to textDeferred.await()
            }
            _state.value = result.fold(
                onSuccess = { (novel, text) -> NovelReaderState.Content(novel, text) },
                onFailure = { NovelReaderState.Error },
            )
        }
    }

    /** 把小说标题+正文送入文本打印总线，复用文本打印全套排版/浓度/连接/过热保护。 */
    fun print() {
        val content = _state.value as? NovelReaderState.Content ?: return
        textTransferBus.offer(
            TextTransferPayload(title = content.novel.title, content = content.text),
        )
        _events.tryEmit(NovelReaderEvent.PrintRequested)
    }

    /** 切换小说收藏：服务端新设/取消爱心，成功后同步本地收藏快照并刷新按钮状态。 */
    fun toggleBookmark() {
        val content = _state.value as? NovelReaderState.Content ?: return
        if (_bookmarkBusy.value) return
        val target = !content.novel.isBookmarked
        val novel = content.novel
        _bookmarkBusy.value = true
        viewModelScope.launch {
            runCatching { repository.setNovelBookmarked(novel, target) }
                .onSuccess {
                    _state.update { current ->
                        if (current is NovelReaderState.Content) {
                            current.copy(novel = current.novel.copy(isBookmarked = target))
                        } else {
                            current
                        }
                    }
                }
                .onFailure { _events.tryEmit(NovelReaderEvent.ActionFailed) }
            _bookmarkBusy.value = false
        }
    }

    /** 把标题+正文保存为本地文本文件（小说下载）。 */
    fun download() {
        val content = _state.value as? NovelReaderState.Content ?: return
        viewModelScope.launch {
            runCatching { repository.downloadNovel(content.novel, content.text) }
                .onSuccess { _events.tryEmit(NovelReaderEvent.DownloadQueued) }
                .onFailure { _events.tryEmit(NovelReaderEvent.ActionFailed) }
        }
    }

    companion object {
        const val NAV_ARG_NOVEL_ID = "novelId"
    }
}

@Composable
fun PixivNovelReaderRoute(
    onBack: () -> Unit,
    onPrint: () -> Unit,
    onSearchTag: (String) -> Unit,
    viewModel: PixivNovelReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bookmarkBusy by viewModel.bookmarkBusy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedText = stringResource(ResR.string.pixiv_novel_saved)
    val actionFailedText = stringResource(ResR.string.pixiv_action_failed)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                NovelReaderEvent.PrintRequested -> onPrint()
                NovelReaderEvent.DownloadQueued -> snackbarHostState.showSnackbar(savedText)
                NovelReaderEvent.ActionFailed -> snackbarHostState.showSnackbar(actionFailedText)
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            NovelReaderState.Loading -> PixivSubScaffold(
                title = stringResource(ResR.string.pixiv_novel_title),
                onBack = onBack,
            ) {
                FullScreenLoading()
            }

            NovelReaderState.Error -> PixivSubScaffold(
                title = stringResource(ResR.string.pixiv_novel_title),
                onBack = onBack,
            ) {
                FullScreenError(
                    message = stringResource(ResR.string.pixiv_novel_text_failed),
                    onRetry = viewModel::retry,
                )
            }

            // 在线阅读一律走小说阅读器（可在右上角切换到文本查看器）。
            // 在线内容没有本地文件，编辑入口置灰禁用。
            is NovelReaderState.Content -> AppReader(
                title = current.novel.title,
                content = current.text,
                onBack = onBack,
                defaultMode = ReaderMode.NOVEL,
                editable = false,
                header = {
                    NovelReaderHeader(
                        novel = current.novel,
                        onSearchTag = onSearchTag,
                    )
                },
                actions = {
                    AppIconButton(
                        onClick = viewModel::toggleBookmark,
                        imageVector = if (current.novel.isBookmarked) {
                            Icons.Filled.Bookmark
                        } else {
                            Icons.Filled.BookmarkBorder
                        },
                        contentDescription = stringResource(
                            if (current.novel.isBookmarked) ResR.string.pixiv_bookmarked
                            else ResR.string.pixiv_bookmark,
                        ),
                        enabled = !bookmarkBusy,
                    )
                    AppIconButton(
                        onClick = viewModel::download,
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(ResR.string.pixiv_novel_download),
                    )
                    AppIconButton(
                        onClick = viewModel::print,
                        imageVector = Icons.Filled.Print,
                        contentDescription = stringResource(ResR.string.pixiv_print),
                    )
                },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 在线小说页眉：作者与标签（点标签继续搜索），占真实高度，分页自动扣除。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelReaderHeader(novel: PixivNovel, onSearchTag: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
    ) {
        AppText(
            text = novel.user?.name.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (novel.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                horizontalArrangement = Arrangement.spacedBy(
                    dimensionResource(ResR.dimen.dimen_pixiv_chip_spacing),
                ),
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(ResR.dimen.dimen_pixiv_chip_spacing),
                ),
            ) {
                novel.tags.forEach { tag ->
                    AppFilterChip(
                        selected = false,
                        onClick = { onSearchTag(tag.name) },
                        label = tag.translatedName ?: tag.name,
                    )
                }
            }
        }
    }
}
