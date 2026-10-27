package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.glass.glassSurface
import com.pencil.chuangyin.desktop.image.ImageEditorStore
import com.pencil.chuangyin.desktop.navigation.DesktopNavigator
import com.pencil.chuangyin.desktop.pixiv.PixivComment
import com.pencil.chuangyin.desktop.pixiv.PixivGraph
import com.pencil.chuangyin.desktop.pixiv.PixivIllust
import com.pencil.chuangyin.desktop.support.rememberRemoteImage
import com.pencil.chuangyin.desktop.theme.LocalLiquidGlassControlsEnabled
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import com.pencil.chuangyin.desktop.ui.motion.DesktopLoadingIndicator
import com.pencil.chuangyin.desktop.ui.motion.DesktopMotion
import com.pencil.chuangyin.desktop.ui.motion.animateItemMotion
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 作品详情 —— 与安卓端 `PixivDetailRoute` / `DetailContent` 逐项对齐：
 * 顶栏（返回 / 收藏 / 刷新）+ 多页头图 pager（页码玻璃胶囊、右上全屏）
 * + 标题 / 作者行 / 统计行 / 标签流 + 收藏 / 打印按钮 + 相关作品 + 评论四态。
 *
 * 「打印全部」与安卓端同流程：先把全部原图下到打印缓存（`pixiv_print`），
 * 再交给图片打印页复用同一套打印设置（安卓经 ShotTransferBus，桌面直接导入
 * [com.pencil.chuangyin.desktop.image.ImageEditorStore] 并跳转图片打印页）。
 */
@Composable
fun PixivDetailScreen(
    illustId: Long,
    bottomBarSpace: Dp,
    onBack: () -> Unit,
    onRequireLogin: () -> Unit,
    onSearchTag: (String) -> Unit = {},
) {
    val client = PixivGraph.client
    val session = PixivGraph.session
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var illust by remember { mutableStateOf<PixivIllust?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }

    var related by remember { mutableStateOf<List<PixivIllust>>(emptyList()) }
    var relatedNextUrl by remember { mutableStateOf<String?>(null) }

    var comments by remember { mutableStateOf<List<PixivComment>>(emptyList()) }
    var commentsOffset by remember { mutableIntStateOf(0) }
    var commentsNextUrl by remember { mutableStateOf<String?>(null) }
    var commentsLoading by remember { mutableStateOf(false) }
    var commentsError by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var bookmarkBusy by remember { mutableStateOf(false) }
    var printing by remember { mutableStateOf(false) }
    var printedDone by remember { mutableIntStateOf(0) }
    var printedTotal by remember { mutableIntStateOf(0) }

    var fullscreenPage by remember { mutableStateOf<Int?>(null) }

    suspend fun loadComments(reset: Boolean) {
        commentsLoading = true
        commentsError = false
        runCatching { client.comments(illustId, if (reset) 0 else commentsOffset) }
            .onSuccess { (list, next) ->
                comments = if (reset) list else comments + list
                commentsNextUrl = next
                commentsOffset = if (reset) list.size else commentsOffset + list.size
            }
            .onFailure { commentsError = true }
        commentsLoading = false
    }

    LaunchedEffect(illustId, reloadTick) {
        loading = true
        error = null
        comments = emptyList()
        commentsOffset = 0
        commentsNextUrl = null
        related = emptyList()
        relatedNextUrl = null
        if (session.accessToken == null) {
            loading = false
            onRequireLogin()
            return@LaunchedEffect
        }
        runCatching { client.illustDetail(illustId) }
            .onSuccess { detail ->
                if (detail == null) error = "作品不存在或已被删除" else illust = detail
            }
            .onFailure { error = it.message?.take(160) ?: "请求失败" }
        loading = false
        if (illust != null) {
            runCatching { client.related(illustId) }
                .onSuccess { related = it.items; relatedNextUrl = it.nextUrl }
            loadComments(reset = true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                GlassTopAppBar(
                    title = illust?.title.orEmpty(),
                    navigationIcon = {
                        AppIconButton(
                            icon = AppIcon.Back,
                            contentDescription = "返回",
                            onClick = onBack,
                        )
                    },
                    actions = {
                        val current = illust
                        if (current != null) {
                            AppIconButton(
                                icon = AppIcon.Bookmark,
                                contentDescription = if (current.isBookmarked) "取消收藏" else "收藏",
                                tint = if (current.isBookmarked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                onClick = {
                                    if (bookmarkBusy) return@AppIconButton
                                    bookmarkBusy = true
                                    scope.launch {
                                        val target = !current.isBookmarked
                                        val ok = client.setBookmark(current.id, target)
                                        if (ok) {
                                            illust = illust?.copy(isBookmarked = target)
                                        } else {
                                            snackbarHost.showSnackbar("操作失败，请重试")
                                        }
                                        bookmarkBusy = false
                                    }
                                },
                            )
                            AppIconButton(
                                icon = AppIcon.Refresh,
                                contentDescription = "刷新",
                                onClick = { reloadTick++ },
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val current = illust
                when {
                    loading && current == null -> LoadingBox()
                    error != null && current == null -> ErrorBox(
                        message = error.orEmpty(),
                        onRetry = { reloadTick++ },
                    )

                    current == null -> ErrorBox(
                        message = "作品不存在或已被删除",
                        onRetry = { reloadTick++ },
                    )

                    else -> DetailContent(
                        illust = current,
                        related = related,
                        comments = comments,
                        commentsLoading = commentsLoading,
                        commentsError = commentsError,
                        commentsHasMore = commentsNextUrl != null,
                        commentInput = commentInput,
                        posting = posting,
                        bookmarkBusy = bookmarkBusy,
                        printing = printing,
                        bottomBarSpace = bottomBarSpace,
                        onOpenFullscreen = { fullscreenPage = it },
                        onToggleBookmark = {
                            if (!bookmarkBusy) {
                                bookmarkBusy = true
                                scope.launch {
                                    val target = !current.isBookmarked
                                    val ok = client.setBookmark(current.id, target)
                                    if (ok) {
                                        illust = illust?.copy(isBookmarked = target)
                                    } else {
                                        snackbarHost.showSnackbar("操作失败，请重试")
                                    }
                                    bookmarkBusy = false
                                }
                            }
                        },
                        onPrint = {
                            if (printing) return@DetailContent
                            printing = true
                            printedDone = 0
                            printedTotal = current.pageCount
                            scope.launch {
                                var failure: Throwable? = null
                                var files: List<File> = emptyList()
                                runCatching {
                                    files = PixivGraph.downloadManager.downloadForPrint(current) { done, total ->
                                        printedDone = done
                                        printedTotal = total
                                    }
                                }.onFailure { failure = it }
                                printing = false
                                if (failure != null || files.isEmpty()) {
                                    snackbarHost.showSnackbar("打印失败，请稍后重试")
                                } else {
                                    // 与安卓端一致：文件交接给图片打印页，由它复用整套打印设置。
                                    ImageEditorStore.importGallery(files)
                                    DesktopNavigator.navigate(DesktopNavigator.Page.Image)
                                }
                            }
                        },
                        onTagClick = onSearchTag,
                        onCommentInputChange = { commentInput = it },
                        onPostComment = {
                            val text = commentInput.trim()
                            if (text.isEmpty() || posting) return@DetailContent
                            if (session.accessToken == null) {
                                onRequireLogin()
                                return@DetailContent
                            }
                            posting = true
                            scope.launch {
                                val created = client.addComment(current.id, text)
                                if (created != null) {
                                    comments = listOf(created) + comments
                                    commentInput = ""
                                } else {
                                    snackbarHost.showSnackbar("评论发送失败，请稍后重试")
                                }
                                posting = false
                            }
                        },
                        onCommentsMore = {
                            if (!commentsLoading) {
                                scope.launch { loadComments(reset = false) }
                            }
                        },
                        onCommentsRetry = {
                            scope.launch { loadComments(reset = true) }
                        },
                    )
                }
            }
        }

        val overlayPage = fullscreenPage
        if (overlayPage != null && illust != null) {
            FullScreenViewer(
                illust = illust!!,
                initialPage = overlayPage,
                onClose = { fullscreenPage = null },
            )
        }

        if (printing) {
            PrintingOverlay(done = printedDone, total = printedTotal)
        }
    }
}

@Composable
private fun DetailContent(
    illust: PixivIllust,
    related: List<PixivIllust>,
    comments: List<PixivComment>,
    commentsLoading: Boolean,
    commentsError: Boolean,
    commentsHasMore: Boolean,
    commentInput: String,
    posting: Boolean,
    bookmarkBusy: Boolean,
    printing: Boolean,
    bottomBarSpace: Dp,
    onOpenFullscreen: (Int) -> Unit,
    onToggleBookmark: () -> Unit,
    onPrint: () -> Unit,
    onTagClick: (String) -> Unit,
    onCommentInputChange: (String) -> Unit,
    onPostComment: () -> Unit,
    onCommentsMore: () -> Unit,
    onCommentsRetry: () -> Unit,
) {
    val pad = 16.dp
    val gap = 8.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomBarSpace + 16.dp),
    ) {
        item { MediaPager(illust = illust, onOpenFullscreen = onOpenFullscreen) }

        item {
            Column(Modifier.padding(pad)) {
                Text(
                    text = illust.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                AuthorRow(illust = illust, modifier = Modifier.padding(top = gap))

                StatsRow(illust = illust, modifier = Modifier.padding(top = gap))

                if (illust.tags.isNotEmpty()) {
                    TagFlow(
                        tags = illust.tags,
                        onTagClick = onTagClick,
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
                        text = if (illust.isBookmarked) "已收藏" else "收藏",
                        onClick = onToggleBookmark,
                        enabled = !bookmarkBusy,
                        modifier = Modifier.weight(1f),
                    )
                    AppPrimaryButton(
                        text = "打印全部 ${illust.pageCount} 张",
                        onClick = onPrint,
                        enabled = !printing,
                        leadingIcon = AppIcon.Printer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (related.isNotEmpty()) {
            item {
                Text(
                    text = "相关作品",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = pad, vertical = gap),
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = pad),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    items(count = related.size, key = { related[it].id }) { index ->
                        IllustCard(
                            item = related[index],
                            onClick = {},
                            modifier = animateItemMotion().width(150.dp),
                        )
                    }
                }
            }
        }

        item {
            CommentsSection(
                comments = comments,
                loading = commentsLoading,
                error = commentsError,
                hasMore = commentsHasMore,
                input = commentInput,
                posting = posting,
                onInputChange = onCommentInputChange,
                onPost = onPostComment,
                onLoadMore = onCommentsMore,
                onRetry = onCommentsRetry,
            )
        }
    }
}

@Composable
private fun MediaPager(illust: PixivIllust, onOpenFullscreen: (Int) -> Unit) {
    val pages = illust.pageUrls
    val ratio = if (illust.width > 0 && illust.height > 0) {
        illust.width.toFloat() / illust.height.toFloat()
    } else {
        1f
    }.coerceIn(0.5f, 1.8f)

    if (pages.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AppIconView(AppIcon.ImageTool, size = 40.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val glassEnabled = LocalLiquidGlassControlsEnabled.current

    Box(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) { pageIndex ->
            val bitmap = rememberRemoteImage(pages[pageIndex])
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onOpenFullscreen(pageIndex) },
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = illust.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    DesktopLoadingIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        size = 28.dp,
                    )
                }
            }
        }

        if (illust.pageCount > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .glassSurface(
                        shape = MaterialTheme.shapes.small,
                        enabled = glassEnabled,
                        fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${illust.pageCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        AppIconButton(
            icon = AppIcon.Fullscreen,
            contentDescription = "全屏查看",
            onClick = { onOpenFullscreen(pagerState.currentPage) },
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), CircleShape),
        )
    }
}

/** 全屏查看（黑底 pager + 双击/双指缩放），桌面用同窗口覆盖层而非独立弹窗。 */
@Composable
private fun FullScreenViewer(illust: PixivIllust, initialPage: Int, onClose: () -> Unit) {
    val pages = illust.pageUrls
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            ZoomableImage(url = pages.getOrNull(pageIndex))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "第 ${pagerState.currentPage + 1} / ${illust.pageCount} 张",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            AppIconButton(
                icon = AppIcon.Close,
                contentDescription = "退出全屏",
                onClick = onClose,
                tint = Color.White,
            )
        }
        Text(
            text = "双击放大，双指捏合缩放",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}

/** 可缩放图片：双击以点击点为锚 1x/3x，双指以两指中点为锚捏合 1..5x，放大后可平移；scale == 1 时放行 pager。 */
@Composable
private fun ZoomableImage(url: String?) {
    val bitmap = rememberRemoteImage(url)
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // graphicsLayer 的缩放原点是图层中心 C，平移在缩放之后施加。
    // 设 k = 新缩放 / 旧缩放，要让锚点 anchor 处的画面缩放后仍停在原地，必须满足
    //   offset' = (anchor - C) * (1 - k) + offset * k
    // 旧实现漏掉了 `- C`，导致无论手指按在哪里都以图片中心缩放。
    fun zoomAround(anchor: Offset, nextScale: Float) {
        val target = nextScale.coerceIn(1f, 5f)
        val k = target / scale
        val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
        val moved = (anchor - center) * (1f - k) + offset * k
        val maxX = containerSize.width * (target - 1f) / 2f
        val maxY = containerSize.height * (target - 1f) / 2f
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
                        // 保证 scale=1 且单指时 pager 仍可左右翻页。
                        if (scale > 1f || zoomChange != 1f || multiTouch) {
                            if (zoomChange != 1f && containerSize.width > 0) {
                                // 用上一帧的两指中点：本帧位移由下面的 pan 补上。
                                zoomAround(event.calculateCentroid(useCurrent = false), scale * zoomChange)
                            }
                            if (scale > 1f && panChange != Offset.Zero) {
                                val maxX = containerSize.width * (scale - 1f) / 2f
                                val maxY = containerSize.height * (scale - 1f) / 2f
                                offset = Offset(
                                    (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                    (offset.y + panChange.y).coerceIn(-maxY, maxY),
                                )
                            }
                            if (event.changes.any { it.positionChanged() }) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        // 以双击位置为锚：放大后该点仍停在手指下方。
                        zoomAround(tap, if (scale > 1f) 1f else 3f)
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        } else {
            DesktopLoadingIndicator(color = Color.White, size = 36.dp)
        }
    }
}

@Composable
private fun AuthorRow(illust: PixivIllust, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PixivAvatar(url = illust.authorAvatar, size = 40.dp)
        Column {
            Text(
                text = illust.authorName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StatsRow(illust: PixivIllust, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(
            icon = AppIcon.Bookmark,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${compactCount(illust.totalBookmarks)} 收藏",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${compactCount(illust.totalView)} 浏览",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFlow(tags: List<String>, onTagClick: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            SectionChip(label = tag, selected = false, onClick = { onTagClick(tag) })
        }
    }
}

@Composable
private fun CommentsSection(
    comments: List<PixivComment>,
    loading: Boolean,
    error: Boolean,
    hasMore: Boolean,
    input: String,
    posting: Boolean,
    onInputChange: (String) -> Unit,
    onPost: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val pad = 16.dp
    val gap = 8.dp
    Column(Modifier.padding(horizontal = pad, vertical = gap)) {
        Text(
            text = "评论",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = gap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            Box(Modifier.weight(1f)) {
                AppTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = "友善评论，理性发言…",
                    singleLine = false,
                    minLines = 1,
                )
            }
            AppIconButton(
                icon = AppIcon.Send,
                contentDescription = "发送",
                onClick = onPost,
                tint = if (!posting && input.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            )
        }

        when {
            loading && comments.isEmpty() -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = gap),
                contentAlignment = Alignment.Center,
            ) {
                DesktopLoadingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    size = 26.dp,
                )
            }

            error && comments.isEmpty() -> Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "加载失败，请重试",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = gap),
                )
                AppSecondaryButton(
                    text = "重试",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            comments.isEmpty() -> Text(
                text = "还没有评论，来抢沙发吧",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = gap),
            )

            else -> comments.forEachIndexed { index, comment ->
                AnimatedComment(index = index) {
                    CommentRow(comment = comment, modifier = Modifier.padding(vertical = gap))
                }
            }
        }

        if (hasMore && comments.isNotEmpty() && !loading) {
            AppSecondaryButton(
                text = "正在加载更多…",
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 评论逐条入场：错峰展开 + 淡入，对齐安卓端 `AppearingComment`。
 * 评论容器不是 Lazy 列表，拿不到 `animateItem`，用 expandVertically 的 spatial spring 顶上。
 */
@Composable
private fun AnimatedComment(index: Int, content: @Composable () -> Unit) {
    var visible by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay((index.coerceAtMost(8) * 45).toLong())
        visible = true
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(DesktopMotion.effectFast()) +
            androidx.compose.animation.expandVertically(DesktopMotion.spatial()),
    ) {
        content()
    }
}

@Composable
private fun CommentRow(comment: PixivComment, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PixivAvatar(url = comment.avatarUrl, size = 32.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.userName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatCommentDate(comment.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (comment.text.isNotBlank()) {
                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else if (!comment.stampUrl.isNullOrBlank()) {
                // 贴图评论：Pixiv 的 stamp 图片，纯贴图评论正文为空串。
                val stamp = rememberRemoteImage(comment.stampUrl)
                if (stamp != null) {
                    Image(
                        bitmap = stamp,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(top = 2.dp).sizeIn(maxWidth = 120.dp, maxHeight = 120.dp),
                    )
                }
            }
        }
    }
}

/** 打印准备遮罩（对齐安卓端 `PrintingOverlay`）：下载原图期间盖住详情页，给出逐页进度。 */
@Composable
private fun PrintingOverlay(done: Int, total: Int) {
    val glassEnabled = LocalLiquidGlassControlsEnabled.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .glassSurface(
                    shape = MaterialTheme.shapes.large,
                    enabled = glassEnabled,
                    fallbackColor = MaterialTheme.colorScheme.surface,
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DesktopLoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
                size = 36.dp,
            )
            Text(
                text = "正在下载原图并准备打印…（$done/$total）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 与安卓端一致：只取日期部分。 */
private fun formatCommentDate(raw: String): String =
    raw.substringBefore("T").trim().ifBlank { raw }

/** 与安卓端一致：>=1 万按「x.x万」压缩。 */
private fun compactCount(value: Int): String =
    if (value >= 10_000) String.format("%.1f万", value / 10_000f) else value.toString()
