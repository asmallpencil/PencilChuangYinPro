package com.pencil.chuangyin.feature.pixiv.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.pencil.chuangyin.core.ui.components.AppFilterChip
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSecondaryButton
import com.pencil.chuangyin.core.ui.components.AppSkeletonBox
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.ui.motion.animateItemMotion
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.pixiv.domain.PixivComment
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.feature.pixiv.domain.PixivPage
import com.pencil.chuangyin.feature.pixiv.domain.PixivUser
import com.pencil.chuangyin.feature.pixiv.ui.PixivImageProvider
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenError
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenLoading
import com.pencil.chuangyin.feature.pixiv.ui.common.IllustCard
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivZoomableImage
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
                            glass = false,
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
                                glass = false,
                            )
                            AppIconButton(
                                onClick = viewModel::downloadAll,
                                imageVector = Icons.Filled.Download,
                                contentDescription = stringResource(ResR.string.pixiv_download_all),
                                glass = false,
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

    val motion = rememberAppMotion()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dimensionResource(ResR.dimen.dimen_global_padding_l)),
    ) {
        item(key = "media") {
            // 相关作品 / 评论加载完成后，上方内容会带惯性让位，而不是硬跳。
            Box(modifier = animateItemMotion(motion)) {
                MediaPager(illust = illust, onOpenFullscreen = { fullscreenPage = it })
            }
        }

        item(key = "info") {
            Column(modifier = animateItemMotion(motion).padding(pad)) {
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
            item(key = "related_title") {
                AppText(
                    text = stringResource(ResR.string.pixiv_related),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = animateItemMotion(motion).padding(horizontal = pad, vertical = gap),
                )
            }
            item(key = "related_row") {
                LaunchedEffect(state.related.size, state.relatedNextUrl) {
                    if (state.relatedNextUrl != null) onRelatedShown()
                }
                RelatedRow(
                    items = state.related,
                    onOpenDetail = onOpenDetail,
                    modifier = animateItemMotion(motion),
                )
            }
        }

        item(key = "comments") {
            CommentsSection(
                state = state,
                onInputChange = onCommentInputChange,
                onPost = onPostComment,
                onLoadMore = onCommentsMore,
                onRetry = onCommentsRetry,
                modifier = animateItemMotion(motion),
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
                        // 大图占位用 shimmer 骨架而不是转圈：形状与最终图片一致，切换时无跳动。
                        AppSkeletonBox(
                            modifier = Modifier.fillMaxSize(),
                            shape = RectangleShape,
                        )
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
                // 手势缩放 / 双击放大 / 超大图子采样交给 ZoomImage（panpf/zoomimage）
                PixivZoomableImage(
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
private fun RelatedRow(
    items: List<PixivIllust>,
    onOpenDetail: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardWidth = dimensionResource(ResR.dimen.dimen_pixiv_related_card_width)
    val pad = dimensionResource(ResR.dimen.dimen_global_padding_m)
    val motion = rememberAppMotion()
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = pad),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_pixiv_grid_spacing)),
    ) {
        items(items, key = { it.id }) { illust ->
            Box(modifier = animateItemMotion(motion).width(cardWidth)) {
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
    modifier: Modifier = Modifier,
) {
    val pad = dimensionResource(ResR.dimen.dimen_global_padding_m)
    val gap = dimensionResource(ResR.dimen.dimen_global_padding_s)
    Column(modifier = modifier.padding(horizontal = pad, vertical = gap)) {
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
                ) { AppLoadingIndicator() }
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
                    key(comment.id) {
                        AppearingComment(
                            comment = comment,
                            modifier = Modifier.padding(vertical = gap),
                        )
                    }
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

/**
 * 评论出现动效：新发的 / 新加载的评论从顶部弹性撑开，
 * 而不是在列表里"啪"地插入一行。
 */
@Composable
private fun AppearingComment(comment: PixivComment, modifier: Modifier = Modifier) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val motion = rememberAppMotion()
    AnimatedVisibility(visible = appeared, enter = motion.expandEnter()) {
        CommentRow(comment = comment, modifier = modifier)
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
                AppLoadingIndicator()
                AppText(
                    text = stringResource(ResR.string.pixiv_preparing_print, done, total),
                    align = TextAlign.Center,
                )
            }
        }
    }
}

/** 中文习惯的数量缩写：1.2 万。 */
private fun compactCount(value: Int): String =
    if (value >= 10_000) String.format("%.1f万", value / 10_000f) else value.toString()
