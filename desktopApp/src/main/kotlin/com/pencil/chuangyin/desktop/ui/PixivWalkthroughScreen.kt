package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.pixiv.PixivGraph
import com.pencil.chuangyin.desktop.pixiv.PixivIllust
import com.pencil.chuangyin.desktop.support.rememberRemoteImage
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val PREFETCH_DISTANCE = 3
private const val SCRIM_ALPHA = 0.72f
private const val SECONDARY_ALPHA = 0.8f

/**
 * 速览漫步（对齐安卓端 PixivWalkthroughScreen）：
 * 全屏黑底 Pager，默认竖向刷，可切换横向；底部渐变遮罩承载标题/作者/收藏/详情入口。
 */
@Composable
fun PixivWalkthroughScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
) {
    var items by remember { mutableStateOf<List<PixivIllust>>(emptyList()) }
    var nextUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadFirst() {
        loading = true
        error = false
        scope.launch {
            runCatching { PixivGraph.client.walkthrough() }
                .onSuccess { page ->
                    items = page.items
                    nextUrl = page.nextUrl
                    loading = false
                }
                .onFailure {
                    loading = false
                    error = true
                }
        }
    }

    LaunchedEffect(Unit) { loadFirst() }

    val loadMore: () -> Unit = {
        val url = nextUrl
        if (url != null && !loading) {
            loading = true
            scope.launch {
                runCatching { PixivGraph.client.nextPage(url) }
                    .onSuccess { page ->
                        items = items + page.items
                        nextUrl = page.nextUrl
                        loading = false
                    }
                    .onFailure { loading = false }
            }
        }
    }

    val toggleBookmark: (PixivIllust) -> Unit = { illust ->
        val target = !illust.isBookmarked
        scope.launch {
            runCatching { PixivGraph.client.setBookmark(illust.id, target) }.onSuccess { ok ->
                if (ok) {
                    items = items.map { item ->
                        if (item.id == illust.id) {
                            item.copy(
                                isBookmarked = target,
                                totalBookmarks = (
                                    item.totalBookmarks + if (target) 1 else -1
                                    ).coerceAtLeast(0),
                            )
                        } else {
                            item
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            loading && items.isEmpty() -> WalkthroughMessage("正在加载漫步内容…")
            error && items.isEmpty() -> WalkthroughMessage(
                message = "加载失败，请重试",
                actionLabel = "重试",
                onAction = { loadFirst() },
            )
            items.isEmpty() -> WalkthroughMessage("暂无漫步内容，稍后再来")
            else -> WalkthroughPager(
                items = items,
                hasMore = nextUrl != null,
                onBack = onBack,
                onOpenDetail = onOpenDetail,
                onBookmark = toggleBookmark,
                onNearEnd = loadMore,
            )
        }
    }
}

@Composable
private fun WalkthroughMessage(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = SECONDARY_ALPHA),
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel, color = Color.White)
            }
        }
    }
}

@Composable
private fun WalkthroughPager(
    items: List<PixivIllust>,
    hasMore: Boolean,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onBookmark: (PixivIllust) -> Unit,
    onNearEnd: () -> Unit,
) {
    // 默认竖向刷；切换方向后仍保持用户选择。
    var isVertical by rememberSaveable { mutableStateOf(true) }

    // 切换方向时以 key 强制重建 Pager，并重置页码到 0。
    key(isVertical) {
        val pagerState = rememberPagerState(pageCount = { items.size })

        // 接近末尾时续拉下一批漫步内容。
        LaunchedEffect(pagerState, items.size, hasMore) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { page ->
                    if (hasMore && page >= items.size - PREFETCH_DISTANCE) {
                        onNearEnd()
                    }
                }
        }

        if (isVertical) {
            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                WalkthroughPagerPage(
                    illust = items[page],
                    onOpenDetail = onOpenDetail,
                    onBookmark = onBookmark,
                )
            }
        } else {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                WalkthroughPagerPage(
                    illust = items[page],
                    onOpenDetail = onOpenDetail,
                    onBookmark = onBookmark,
                )
            }
        }
    }

    // 悬浮于内容之上的左上返回 + 右上方向切换。
    Box(modifier = Modifier.fillMaxSize()) {
        AppIconButton(
            icon = AppIcon.Back,
            contentDescription = "返回",
            onClick = onBack,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopStart),
            tint = Color.White,
        )
        AppIconButton(
            icon = if (isVertical) AppIcon.SwapVert else AppIcon.SwapHoriz,
            contentDescription = if (isVertical) "切换为横向浏览" else "切换为竖向浏览",
            onClick = { isVertical = !isVertical },
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopEnd),
            tint = Color.White,
        )
    }
}

@Composable
private fun WalkthroughPagerPage(
    modifier: Modifier = Modifier,
    illust: PixivIllust,
    onOpenDetail: (Long) -> Unit,
    onBookmark: (PixivIllust) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val bitmap = rememberRemoteImage(illust.pageUrls.firstOrNull() ?: illust.coverUrl)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = illust.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 底部信息渐变遮罩 + 标题/作者/操作。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM_ALPHA)),
                    ),
                )
                .padding(24.dp),
        ) {
            Column {
                Text(
                    text = illust.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    text = illust.authorName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = SECONDARY_ALPHA),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AppIconButton(
                        icon = AppIcon.Bookmark,
                        contentDescription = "收藏",
                        onClick = { onBookmark(illust) },
                        tint = if (illust.isBookmarked) Color(0xFFFF7BA9) else Color.White,
                    )
                    AppIconButton(
                        icon = AppIcon.Info,
                        contentDescription = "相关作品",
                        onClick = { onOpenDetail(illust.id) },
                        tint = Color.White,
                    )
                    AppIconButton(
                        icon = AppIcon.Language,
                        contentDescription = "在浏览器中打开",
                        onClick = {
                            runCatching {
                                Desktop.getDesktop().browse(
                                    URI("https://www.pixiv.net/artworks/${illust.id}"),
                                )
                            }
                        },
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
