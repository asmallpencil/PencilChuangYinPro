package com.pencil.chuangyin.feature.pixiv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.pencil.chuangyin.core.ui.components.AppFilterChip
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSecondaryButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.feature.pixiv.domain.PixivComment
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.feature.pixiv.domain.PixivPage
import com.pencil.chuangyin.feature.pixiv.domain.PixivUser
import com.pencil.chuangyin.feature.pixiv.ui.PixivImageProvider
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenError
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenLoading
import com.pencil.chuangyin.feature.pixiv.ui.common.IllustCard
import com.pencil.chuangyin.resources.R as ResR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixivDetailRoute(
    onBack: () -> Unit,
    onPrinted: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onSourceSearch: (String) -> Unit,
    onSearchTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PixivDetailViewModel = hiltViewModel(),
) {
    PixivImageProvider {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }
        val actionFailedText = stringResource(ResR.string.pixiv_action_failed)
        val queuedText = stringResource(ResR.string.pixiv_download_queued)
        val glassActive = rememberLiquidGlassActive()

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    PixivDetailEvent.Printed -> onPrinted()
                    PixivDetailEvent.DownloadQueued ->
                        snackbarHostState.showSnackbar(queuedText)
                    PixivDetailEvent.ActionFailed ->
                        snackbarHostState.showSnackbar(actionFailedText)
                }
            }
        }

        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = {
                        val illust = (state.loadState as? DetailLoadState.Content)?.illust
                        AppText(
                            text = illust?.title.orEmpty(),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        AppIconButton(
                            onClick = onBack,
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(ResR.string.label_back),
                        )
                    },
                    actions = {
                        val illust = (state.loadState as? DetailLoadState.Content)?.illust
                        if (illust != null) {
                            AppIconButton(
                                onClick = {
                                    illust.pages.firstOrNull()?.large?.let(onSourceSearch)
                                },
                                imageVector = Icons.Filled.ImageSearch,
                                contentDescription = stringResource(ResR.string.pixiv_source_search),
                            )
                            AppIconButton(
                                onClick = viewModel::downloadAll,
                                imageVector = Icons.Filled.Download,
                                contentDescription = stringResource(ResR.string.pixiv_download_all),
                            )
                        }
                    },
                    modifier = Modifier.nativeGlassSurface(
                        shape = RoundedCornerShape(0.dp),
                        enabled = glassActive,
                        fallbackColor = MaterialTheme.colorScheme.surface,
                    ),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (val load = state.loadState) {
                    DetailLoadState.Loading -> FullScreenLoading()
                    DetailLoadState.Error -> FullScreenError(
                        message = stringResource(ResR.string.pixiv_load_failed),
                        onRetry = viewModel::retry,
                    )
                    is DetailLoadState.Content -> DetailContent(
                        illust = load.illust,
                        state = state,
                        onToggleBookmark = viewModel::toggleBookmark,
                        onPrint = viewModel::printAll,
                        onOpenDetail = onOpenDetail,
                        onSearchTag = onSearchTag,
                        onRelatedShown = viewModel::loadRelatedMore,
                        onCommentInputChange = viewModel::onCommentInputChange,
                        onPostComment = viewModel::postComment,
                        onCommentsMore = viewModel::loadCommentsMore,
                        onCommentsRetry = viewModel::retryComments,
                    )
                }

                if (state.printing) {
                    PrintingOverlay(done = state.printedDone, total = state.printedTotal)
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    illust: PixivIllust,
    state: PixivDetailUiState,
    onToggleBookmark: () -> Unit,
    onPrint: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onSearchTag: (String) -> Unit,
    onRelatedShown: () -> Unit,
    onCommentInputChange: (String) -> Unit,
    onPostComment: () -> Unit,
    onCommentsMore: () -> Unit,
    onCommentsRetry: () -> Unit,
) {
    val pad = dimensionResource(ResR.dimen.dimen_global_padding_m)
    val gap = dimensionResource(ResR.dimen.dimen_global_padding_s)

    // REQ9：全屏查看页（初始页 = 点击的页码）。
    var fullscreenPage by remember { mutableStateOf<Int?>(null) }
    val currentFullscreen = fullscreenPage
    if (currentFullscreen != null) {
        FullScreenViewer(
            illust = illust,
            initialPage = currentFullscreen,
            onClose = { fullscreenPage = null },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dimensionResource(ResR.dimen.dimen_global_padding_l)),
    ) {
        item { MediaPager(illust = illust, onOpenFullscreen = { fullscreenPage = it }) }

        item {
            Column(modifier = Modifier.padding(pad)) {
                AppText(
                    text = illust.title,
                    style = MaterialTheme.typography.titleLarge,
                )

                AuthorRow(
                    user = illust.user,
                    modifier = Modifier.padding(top = gap),
                )

                StatsRow(
                    illust = illust,
                    modifier = Modifier.padding(top = gap),
                )

                if (illust.tags.isNotEmpty()) {
                    TagFlow(
                        tags = illust.tags,
                        onTagClick = onSearchTag,
                        modifier = Modifier.padding(top = pad),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = pad),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    AppSecondaryButton(
                        text = stringResource(
                            if (illust.isBookmarked) ResR.string.pixiv_bookmarked
                            else ResR.string.pixiv_bookmark,
                        ),
                        onClick = onToggleBookmark,
                        enabled = !state.bookmarkBusy,
                        modifier = Modifier.weight(1f),
                        leadingContent = {
                            Icon(
                                imageVector = if (illust.isBookmarked) {
                                    Icons.Filled.Bookmark
                                } else {
                                    Icons.Filled.BookmarkBorder
                                },
                                contentDescription = null,
                            )
                        },
                    )
                    AppPrimaryButton(
                        text = stringResource(ResR.string.pixiv_print_all, illust.pageCount),
                        onClick = onPrint,
                        enabled = !state.printing,
                        modifier = Modifier.weight(1f),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Print,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }

        if (state.related.isNotEmpty()) {
            item {
                AppText(
                    text = stringResource(ResR.string.pixiv_related),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = pad, vertical = gap),
                )
            }
            item {
                LaunchedEffect(state.related.size, state.relatedNextUrl) {
                    if (state.relatedNextUrl != null) onRelatedShown()
                }
                RelatedRow(items = state.related, onOpenDetail = onOpenDetail)
            }
        }

        item {
            CommentsSection(
                state = state,
                onInputChange = onCommentInputChange,
                onPost = onPostComment,
                onLoadMore = onCommentsMore,
                onRetry = onCommentsRetry,
            )
        }
    }
}

@Composable
private fun MediaPager(
    illust: PixivIllust,
    onOpenFullscreen: (Int) -> Unit,
) {
    val ratio = if (illust.width > 0 && illust.height > 0) {
        illust.width.toFloat() / illust.height.toFloat()
    } else {
        1f
    }.coerceIn(0.5f, 1.8f)

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { illust.pages.size })
    val glassActive = rememberLiquidGlassActive()
    Box(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) { pageIndex ->
            // REQ9：点击图片进入全屏查看。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onOpenFullscreen(pageIndex) },
            ) {
                SubcomposeAsyncImage(
                    model = illust.pages[pageIndex].large,
                    contentDescription = illust.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BrokenImage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }

        if (illust.pageCount > 1) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = Color.Transparent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(dimensionResource(ResR.dimen.dimen_global_padding_s))
                    .nativeGlassSurface(
                        shape = MaterialTheme.shapes.small,
                        enabled = glassActive,
                        fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                AppText(
                    text = "${pagerState.currentPage + 1} / ${illust.pageCount}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(
                        horizontal = dimensionResource(ResR.dimen.dimen_global_padding_s),
                        vertical = 4.dp,
                    ),
                )
            }
        }

        // REQ9：全屏查看快捷入口。
        AppIconButton(
            onClick = { onOpenFullscreen(pagerState.currentPage) },
            imageVector = Icons.Filled.Fullscreen,
            contentDescription = stringResource(ResR.string.pixiv_detail_fullscreen_enter),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_s))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), CircleShape),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** REQ9：全屏查看器（黑底 pager + 双击/双指缩放）。 */
@Composable
private fun FullScreenViewer(
    illust: PixivIllust,
    initialPage: Int,
    onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { illust.pages.size }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                ZoomablePixivImage(
                    model = illust.pages[pageIndex].large,
                    contentDescription = illust.title,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = stringResource(
                        ResR.string.pixiv_browser_find_result,
                        pagerState.currentPage + 1,
                        illust.pageCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                AppIconButton(
                    onClick = onClose,
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(ResR.string.pixiv_detail_fullscreen_exit),
                    tint = Color.White,
                )
            }
            AppText(
                text = stringResource(ResR.string.pixiv_detail_fullscreen_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                align = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_m)),
            )
        }
    }
}

/**
 * REQ9：可缩放图片。
 *
 * 双指捏合**以两指中点为锚点**放大，双击**以点击位置为锚点**放大——
 * 两者都不再固定以图片中心缩放。
 *
 * `graphicsLayer` 的缩放原点是图层中心（transformOrigin 默认 0.5, 0.5），
 * 且平移在缩放之后施加。设图层中心为 C、旧缩放 s0、新缩放 s1、k = s1/s0，
 * 要让锚点 anchor 处的画面在缩放后仍停在原地，必须满足：
 *
 *     offset' = (anchor - C) * (1 - k) + offset * k
 *
 * 旧实现写成了 `anchor - (anchor - offset) * k`，漏掉了 `- C` 这一项，
 * 因此无论手指按在哪里，实际都以图片中心为缩放中心。
 */
@Composable
private fun ZoomablePixivImage(
    model: Any?,
    contentDescription: String?,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    /** 以 [anchor] 为锚点缩放到 [nextScale]，并把平移限制在「画面边缘不出界」的范围内。 */
    fun zoomAround(anchor: Offset, nextScale: Float) {
        val target = nextScale.coerceIn(1f, MAX_ZOOM_SCALE)
        val size = containerSize
        val k = target / scale
        val center = Offset(size.width / 2f, size.height / 2f)
        val moved = (anchor - center) * (1f - k) + offset * k
        val maxX = size.width * (target - 1f) / 2f
        val maxY = size.height * (target - 1f) / 2f
        offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
        scale = target
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val multiTouch = event.changes.count { it.pressed } > 1
                        // 仅在「已放大 / 正在缩放 / 双指按下」时接管手势，
                        // 保证 scale=1 且单指时 HorizontalPager 仍可左右翻页。
                        if (scale > 1f || zoomChange != 1f || multiTouch) {
                            if (zoomChange != 1f && containerSize.width > 0) {
                                // 用上一帧的两指中点：本帧的位移由下面的 pan 补上，
                                // 与 detectTransformGestures 的处理顺序一致。
                                zoomAround(
                                    anchor = event.calculateCentroid(useCurrent = false),
                                    nextScale = scale * zoomChange,
                                )
                            }
                            if (scale > 1f && panChange != Offset.Zero) {
                                val maxX = containerSize.width * (scale - 1f) / 2f
                                val maxY = containerSize.height * (scale - 1f) / 2f
                                offset = Offset(
                                    (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                    (offset.y + panChange.y).coerceIn(-maxY, maxY),
                                )
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        // 以双击位置为锚：放大后该点仍停在手指下方。
                        zoomAround(tap, if (scale > 1f) 1f else DOUBLE_TAP_ZOOM_SCALE)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = Color.White) }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                }
            },
        )
    }
}

@Composable
private fun AuthorRow(user: PixivUser, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
    ) {
        val avatarSize = dimensionResource(ResR.dimen.dimen_pixiv_avatar_size)
        SubcomposeAsyncImage(
            model = user.avatarUrl,
            contentDescription = user.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column {
            AppText(text = user.name, style = MaterialTheme.typography.bodyLarge)
            if (user.account.isNotBlank()) {
                AppText(
                    text = "@${user.account}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsRow(illust: PixivIllust, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
    ) {
        Icon(
            imageVector = Icons.Filled.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        AppText(
            text = stringResource(ResR.string.pixiv_bookmark_count, compactCount(illust.totalBookmarks)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppText(
            text = stringResource(ResR.string.pixiv_view_count, compactCount(illust.totalView)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFlow(
    tags: List<com.pencil.chuangyin.feature.pixiv.domain.PixivTag>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_pixiv_chip_spacing)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_pixiv_chip_spacing)),
    ) {
        tags.forEach { tag ->
            AppFilterChip(
                selected = false,
                onClick = { onTagClick(tag.name) },
                label = tag.translatedName ?: tag.name,
            )
        }
    }
}

@Composable
private fun RelatedRow(items: List<PixivIllust>, onOpenDetail: (Int) -> Unit) {
    val cardWidth = dimensionResource(ResR.dimen.dimen_pixiv_related_card_width)
    val pad = dimensionResource(ResR.dimen.dimen_global_padding_m)
    LazyRow(
        contentPadding = PaddingValues(horizontal = pad),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_pixiv_grid_spacing)),
    ) {
        items(items, key = { it.id }) { illust ->
            Box(modifier = Modifier.width(cardWidth)) {
                IllustCard(illust = illust, onClick = { onOpenDetail(illust.id) })
            }
        }
    }
}

@Composable
private fun CommentsSection(
    state: PixivDetailUiState,
    onInputChange: (String) -> Unit,
    onPost: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val pad = dimensionResource(ResR.dimen.dimen_global_padding_m)
    val gap = dimensionResource(ResR.dimen.dimen_global_padding_s)
    Column(modifier = Modifier.padding(horizontal = pad, vertical = gap)) {
        AppText(
            text = stringResource(ResR.string.pixiv_comments),
            style = MaterialTheme.typography.titleMedium,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = gap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AppTextField(
                    value = state.commentsInput,
                    onValueChange = onInputChange,
                    placeholder = stringResource(ResR.string.pixiv_comment_input_hint),
                    singleLine = false,
                    enabled = !state.postingComment,
                )
            }
            AppIconButton(
                onClick = onPost,
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(ResR.string.pixiv_comment_send),
                enabled = !state.postingComment && state.commentsInput.isNotBlank(),
            )
        }

        when {
            state.loadingComments && state.comments.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = gap),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            // 失败不再伪装成「暂无评论」：明确告知并提供重试。
            state.commentsError && state.comments.isEmpty() -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AppText(
                        text = stringResource(ResR.string.pixiv_load_failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = gap),
                    )
                    AppSecondaryButton(
                        text = stringResource(ResR.string.state_retry),
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            state.comments.isEmpty() -> {
                AppText(
                    text = stringResource(ResR.string.pixiv_comments_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = gap),
                )
            }
            else -> {
                state.comments.forEach { comment ->
                    CommentRow(
                        comment = comment,
                        modifier = Modifier.padding(vertical = gap),
                    )
                }
            }
        }

        if (state.commentsNextUrl != null && state.comments.isNotEmpty() && !state.loadingComments) {
            AppSecondaryButton(
                text = stringResource(ResR.string.pixiv_loading_more),
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CommentRow(comment: PixivComment, modifier: Modifier = Modifier) {
    val avatarSize = dimensionResource(ResR.dimen.dimen_pixiv_avatar_size)
    val gap = dimensionResource(ResR.dimen.dimen_global_padding_s)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        SubcomposeAsyncImage(
            model = comment.user.avatarUrl,
            contentDescription = comment.user.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = comment.user.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(gap))
                AppText(
                    text = formatCommentDate(comment.date),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            // P 站的「スタンプ」贴图评论正文为空串，内容全在贴图里；
            // 只贴图时若按纯文本渲染就是一片空白，这里改为显示贴图。
            if (comment.comment.isNotBlank()) {
                AppText(
                    text = comment.comment,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else if (!comment.stampUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = comment.stampUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .sizeIn(maxWidth = 120.dp, maxHeight = 120.dp),
                )
            }
        }
    }
}

/** 评论时间：Pixiv 返回 ISO 带时区串，这里只取年月日展示。 */
private fun formatCommentDate(raw: String): String =
    raw.substringBefore("T").trim().ifBlank { raw }

@Composable
private fun PrintingOverlay(done: Int, total: Int) {
    val glassActive = rememberLiquidGlassActive()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = Color.Transparent,
            modifier = Modifier.nativeGlassSurface(
                shape = MaterialTheme.shapes.large,
                enabled = glassActive,
                fallbackColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(dimensionResource(ResR.dimen.dimen_global_padding_l)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
            ) {
                CircularProgressIndicator()
                AppText(
                    text = stringResource(ResR.string.pixiv_preparing_print, done, total),
                    align = TextAlign.Center,
                )
            }
        }
    }
}

/** 全屏查看的最大放大倍数。 */
private const val MAX_ZOOM_SCALE = 5f

/** 双击一次放大的倍数。 */
private const val DOUBLE_TAP_ZOOM_SCALE = 3f

/** 中文习惯的数量缩写：1.2 万。 */
private fun compactCount(value: Int): String =
    if (value >= 10_000) String.format("%.1f万", value / 10_000f) else value.toString()
