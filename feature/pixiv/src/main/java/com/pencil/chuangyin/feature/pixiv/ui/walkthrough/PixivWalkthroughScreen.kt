package com.pencil.chuangyin.feature.pixiv.ui.walkthrough

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.SubcomposeAsyncImage
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.feature.pixiv.data.PixivRepository
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.feature.pixiv.domain.PixivPage
import com.pencil.chuangyin.feature.pixiv.domain.PixivUser
import com.pencil.chuangyin.feature.pixiv.ui.PixivImageProvider
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenEmpty
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenError
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenLoading
import com.pencil.chuangyin.resources.R as ResR
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PixivWalkthroughState(
    val items: List<PixivIllust> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val nextUrl: String? = null,
)

@HiltViewModel
class PixivWalkthroughViewModel @Inject constructor(
    private val repository: PixivRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PixivWalkthroughState(loading = true))
    val state: StateFlow<PixivWalkthroughState> = _state.asStateFlow()

    init {
        loadFirst()
    }

    fun retry() = loadFirst()

    private fun loadFirst() {
        _state.value = PixivWalkthroughState(loading = true)
        viewModelScope.launch {
            runCatching { repository.walkthrough() }
                .onSuccess { page ->
                    _state.value = PixivWalkthroughState(
                        items = page.items,
                        nextUrl = page.nextUrl,
                    )
                }
                .onFailure {
                    _state.update { it.copy(loading = false, error = true) }
                }
        }
    }

    fun loadMore() {
        val url = _state.value.nextUrl ?: return
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching { repository.nextPage(url) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = it.items + page.items,
                            nextUrl = page.nextUrl,
                            loading = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loading = false) } }
        }
    }

    fun toggleBookmark(illust: PixivIllust) {
        val target = !illust.isBookmarked
        viewModelScope.launch {
            runCatching { repository.setBookmarked(illust.id, target) }.onSuccess {
                _state.update { st ->
                    st.copy(
                        items = st.items.map {
                            if (it.id == illust.id) {
                                it.copy(
                                    isBookmarked = target,
                                    totalBookmarks = (
                                        it.totalBookmarks + if (target) 1 else -1
                                        ).coerceAtLeast(0),
                                )
                            } else {
                                it
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun PixivWalkthroughRoute(
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    viewModel: PixivWalkthroughViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PixivImageProvider {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            when {
                state.loading && state.items.isEmpty() -> FullScreenLoading()
                state.error && state.items.isEmpty() -> FullScreenError(
                    message = stringResource(ResR.string.pixiv_load_failed),
                    onRetry = viewModel::retry,
                )
                state.items.isEmpty() -> FullScreenEmpty(
                    message = stringResource(ResR.string.pixiv_walkthrough_empty),
                )
                else -> WalkthroughPager(
                    state = state,
                    onBack = onBack,
                    onOpenDetail = onOpenDetail,
                    onBookmark = viewModel::toggleBookmark,
                    onNearEnd = viewModel::loadMore,
                )
            }
        }
    }
}

@Composable
private fun WalkthroughPager(
    state: PixivWalkthroughState,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onBookmark: (PixivIllust) -> Unit,
    onNearEnd: () -> Unit,
) {
    // 默认竖向刷；用 rememberSaveable 在横/竖切换后仍保持用户选择。
    var isVertical by rememberSaveable { mutableStateOf(true) }

    // 切换方向时以 key 强制重组 Pager，并重置页码到 0
    // （各方向独立 rememberPagerState，续拉 LaunchedEffect 也随之重建并触发）。
    key(isVertical) {
        val pagerState = rememberPagerState(pageCount = { state.items.size })

        // 接近末尾时续拉下一批漫步内容。
        LaunchedEffect(pagerState, state.items.size, state.nextUrl) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { page ->
                    if (state.nextUrl != null && page >= state.items.size - PREFETCH_DISTANCE) {
                        onNearEnd()
                    }
                }
        }

        if (isVertical) {
            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                WalkthroughPagerPage(
                    illust = state.items[page],
                    onOpenDetail = onOpenDetail,
                    onBookmark = onBookmark,
                )
            }
        } else {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                WalkthroughPagerPage(
                    illust = state.items[page],
                    onOpenDetail = onOpenDetail,
                    onBookmark = onBookmark,
                )
            }
        }
    }

    // 悬浮于内容之上的左上返回 + 右上方向切换。
    val switchContentDescription = if (isVertical) {
        ResR.string.pixiv_walkthrough_switch_to_horizontal
    } else {
        ResR.string.pixiv_walkthrough_switch_to_vertical
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AppIconButton(
            onClick = onBack,
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(ResR.string.label_back),
            modifier = Modifier
                .statusBarsPadding()
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_s))
                .align(Alignment.TopStart),
            tint = Color.White,
        )
        AppIconButton(
            onClick = { isVertical = !isVertical },
            imageVector = if (isVertical) {
                Icons.Filled.SwapVert
            } else {
                Icons.Filled.SwapHoriz
            },
            contentDescription = stringResource(switchContentDescription),
            modifier = Modifier
                .statusBarsPadding()
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_s))
                .align(Alignment.TopEnd),
            tint = Color.White,
        )
    }
}

@Composable
private fun WalkthroughPagerPage(
    modifier: Modifier = Modifier,
    illust: PixivIllust,
    onOpenDetail: (Int) -> Unit,
    onBookmark: (PixivIllust) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        SubcomposeAsyncImage(
            model = illust.pages.firstOrNull()?.large ?: illust.cover,
            contentDescription = illust.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // 底部信息渐变遮罩 + 标题/作者/操作（横竖方向均置于底部）。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM_ALPHA)),
                    ),
                )
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_l)),
        ) {
            Column {
                AppText(
                    text = illust.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                AppText(
                    text = illust.user.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = SECONDARY_ALPHA),
                    modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                )
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_m)),
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                ) {
                    AppIconButton(
                        onClick = { onBookmark(illust) },
                        imageVector = if (illust.isBookmarked) {
                            Icons.Filled.Bookmark
                        } else {
                            Icons.Filled.BookmarkBorder
                        },
                        contentDescription = stringResource(ResR.string.pixiv_bookmark),
                        tint = Color.White,
                    )
                    AppIconButton(
                        onClick = { onOpenDetail(illust.id) },
                        imageVector = Icons.Filled.Info,
                        contentDescription = stringResource(ResR.string.pixiv_related),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun WalkthroughPagerPagePreview() {
    WalkthroughPagerPage(
        illust = PixivIllust(
            id = 123,
            title = "示例作品",
            caption = "",
            user = PixivUser(
                id = 1,
                name = "创作者",
                account = "creator",
                avatarUrl = null,
                isFollowed = false,
            ),
            tags = emptyList(),
            createDate = "2026-01-01T00:00:00+09:00",
            width = 1000,
            height = 1414,
            pageCount = 1,
            type = "illust",
            xRestrict = 0,
            totalView = 0,
            totalBookmarks = 0,
            isBookmarked = false,
            pages = listOf(
                PixivPage(
                    thumb = "",
                    medium = "",
                    large = "",
                    original = "",
                ),
            ),
        ),
        onOpenDetail = {},
        onBookmark = {},
    )
}

private const val PREFETCH_DISTANCE = 3
private const val SCRIM_ALPHA = 0.72f
private const val SECONDARY_ALPHA = 0.8f
