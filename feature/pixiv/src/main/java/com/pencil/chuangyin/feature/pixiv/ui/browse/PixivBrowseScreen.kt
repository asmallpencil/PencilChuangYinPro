package com.pencil.chuangyin.feature.pixiv.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.LiquidGlassLayerScope
import com.pencil.chuangyin.core.ui.motion.animateItemMotion
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.pixiv.core.auth.PixivAccount
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.feature.pixiv.domain.PixivPage
import com.pencil.chuangyin.feature.pixiv.domain.PixivUser
import com.pencil.chuangyin.feature.pixiv.ui.PixivImageProvider
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenError
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenLoading
import com.pencil.chuangyin.feature.pixiv.ui.common.IllustGrid
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_MANGA
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_NOVEL
import com.pencil.chuangyin.feature.pixiv.ui.common.NovelRow
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivBrowseTopBar
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivFilterChipRow
import com.pencil.chuangyin.feature.pixiv.ui.common.pixivMediaChipGroup
import com.pencil.chuangyin.feature.pixiv.ui.common.pixivSectionChipGroup
import com.pencil.chuangyin.feature.pixiv.ui.drawer.PixivDrawerDestination
import com.pencil.chuangyin.feature.pixiv.ui.drawer.PixivDrawerSheet
import com.pencil.chuangyin.resources.R as ResR
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity

@Composable
fun PixivBrowseRoute(
    onLogin: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onOpenNovel: (Int) -> Unit,
    onOpenDrawerDestination: (PixivDrawerDestination) -> Unit,
    modifier: Modifier = Modifier,
    bottomBarSpace: Dp = 0.dp,
    viewModel: PixivBrowseViewModel = hiltViewModel(),
) {
    PixivImageProvider {
        val account by viewModel.account.collectAsStateWithLifecycle()
        val state by viewModel.state.collectAsStateWithLifecycle()

        PixivBrowseContent(
            modifier = modifier,
            account = account,
            state = state,
            bottomBarSpace = bottomBarSpace,
            onLogin = onLogin,
            onOpenSearch = onOpenSearch,
            onOpenDetail = onOpenDetail,
            onOpenNovel = onOpenNovel,
            onSelectMode = viewModel::selectMediaMode,
            onSelectSection = viewModel::selectSection,
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onOpenDrawerDestination = onOpenDrawerDestination,
        )
    }
}

@Composable
private fun PixivBrowseContent(
    account: PixivAccount?,
    state: PixivFeedState,
    bottomBarSpace: Dp,
    onLogin: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onOpenNovel: (Int) -> Unit,
    onSelectMode: (String) -> Unit,
    onSelectSection: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDrawerDestination: (PixivDrawerDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drawerOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) {
        dimensionResource(ResR.dimen.dimen_pixiv_drawer_dismiss_threshold).toPx()
    }

    // 抽屉最多占屏幕 75% 宽度（BoxWithConstraints 取实际可用宽度）。
    BoxWithConstraints(modifier = modifier) {
        val drawerSheetWidth = maxWidth * DRAWER_MAX_WIDTH_FRACTION
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PixivBrowseTopBar(
                        title = stringResource(ResR.string.pstation_title),
                        showActions = account != null,
                        onMenu = { drawerOpen = true },
                        onSearch = onOpenSearch,
                        onRefresh = onRefresh,
                        menuContentDescription = stringResource(ResR.string.pixiv_menu),
                        searchContentDescription = stringResource(ResR.string.pixiv_search),
                        refreshContentDescription = stringResource(ResR.string.pixiv_refresh),
                    )
                    if (account != null) {
                        // 内容模式与榜单合并为一条分组芯片行（组间竖线分隔，组内单选）。
                        PixivFilterChipRow(
                            groups = listOf(
                                pixivMediaChipGroup(state.mediaMode),
                                pixivSectionChipGroup(state.mediaMode, state.section),
                            ),
                            onSelect = { groupIndex, key ->
                                if (groupIndex == 0) onSelectMode(key) else onSelectSection(key)
                            },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        account == null -> NotLoggedIn(
                            onLogin = onLogin,
                            bottomBarSpace = bottomBarSpace,
                        )
                        state.mediaMode == MEDIA_NOVEL -> NovelFeed(
                            state = state,
                            bottomBarSpace = bottomBarSpace,
                            onOpenNovel = onOpenNovel,
                            onLoadMore = onLoadMore,
                            onRetry = onRefresh,
                        )
                        state.initialLoading -> FullScreenLoading()
                        state.error && state.items.isEmpty() -> FullScreenError(
                            message = stringResource(ResR.string.pixiv_load_failed),
                            onRetry = onRefresh,
                        )
                        state.items.isEmpty() && !state.initialLoading -> FullScreenLoading()
                        else -> {
                            val gridPadding = dimensionResource(ResR.dimen.dimen_pixiv_grid_padding)
                            IllustGrid(
                                items = state.items,
                                onOpen = onOpenDetail,
                                canLoadMore = state.nextUrl != null,
                                loadingMore = state.loadingMore,
                                onLoadMore = onLoadMore,
                                // 悬浮导航栏不遮挡末行卡片：底部留白经参数注入（页面背景仍延伸到栏下供毛玻璃取样）。
                                contentPadding = PaddingValues(
                                    start = gridPadding,
                                    end = gridPadding,
                                    top = gridPadding,
                                    bottom = gridPadding + bottomBarSpace,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // ── 自绘抽屉覆盖层（彻底替代 ModalNavigationDrawer）────────────────────
        // 关键：覆盖层在抽屉可见的整个期间（含开合动画）都挂在内容树中，
        // 根节点的水平拖拽检测器是叶子优先命中路径上最深的消费者，
        // 无论手指落在面板还是遮罩，水平手势在此全部被吞，
        // 外层 HorizontalPager 在抽屉开合的任何瞬间都收不到水平手势序列，
        // 从根上杜绝「折叠抽屉后页面滑到创意工坊」。垂直滚动/点击不受影响。
        AnimatedVisibility(
            visible = drawerOpen,
            enter = DRAWER_ENTER,
            exit = DRAWER_EXIT,
        ) {
            // 层级提升：抽屉内（含后续新增）的原生玻璃控件自动压住导航栏/子导航等低层控件，
            // 无需逐控件手改——这是原生覆盖层的自动适配接口。
            LiquidGlassLayerScope(LiquidGlassLayer.OVERLAY) {
                var dragOffsetXPx by remember { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragOffsetXPx = 0f },
                                onDragEnd = {
                                    // 左拖越过阈值则收起；否则弹性回位（spring 动画由状态变化驱动）。
                                    if (dragOffsetXPx <= -dismissThresholdPx) {
                                        drawerOpen = false
                                    } else {
                                        dragOffsetXPx = 0f
                                    }
                                },
                                onDragCancel = { dragOffsetXPx = 0f },
                            ) { change, amount ->
                                change.consume()
                                dragOffsetXPx = (dragOffsetXPx + amount)
                                    .coerceIn(-size.width.toFloat(), 0f)
                            }
                        },
                ) {
                    // 遮罩：点击关闭（无涟漪）；自身不消费水平拖拽，统一交由父层吞掉。
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = DRAWER_SCRIM_ALPHA))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { drawerOpen = false },
                            ),
                    )
                    // 面板随手指左拖平移；遮罩保持静止。
                    PixivDrawerSheet(
                        account = account,
                        bottomBarSpace = bottomBarSpace,
                        sheetWidth = drawerSheetWidth,
                        onClose = { drawerOpen = false },
                        onDestination = { destination ->
                            drawerOpen = false
                            onOpenDrawerDestination(destination)
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .graphicsLayer { translationX = dragOffsetXPx },
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelFeed(
    state: PixivFeedState,
    bottomBarSpace: Dp,
    onOpenNovel: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.initialLoading -> FullScreenLoading()
        state.error && state.novels.isEmpty() -> FullScreenError(
            message = stringResource(ResR.string.pixiv_load_failed),
            onRetry = onRetry,
        )
        state.novels.isEmpty() -> FullScreenLoading()
        else -> {
            val motion = rememberAppMotion()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomBarSpace),
            ) {
                items(state.novels, key = { it.id }) { novel ->
                    NovelRow(
                        novel = novel,
                        onClick = { onOpenNovel(novel.id) },
                        modifier = animateItemMotion(motion),
                    )
                }
                if (state.nextUrl != null) {
                    item(key = "novel_loading") {
                        // 接近列表尾部自动续拉下一批小说。
                        LaunchedEffect(state.novels.size) {
                            if (state.nextUrl != null) onLoadMore()
                        }
                        if (state.loadingMore) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppLoadingIndicator(size = 28.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 小说条目统一走共享组件 ui.common.NovelRow（榜单/浏览/搜索/收藏复用）。

@Composable
private fun NotLoggedIn(onLogin: () -> Unit, bottomBarSpace: Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_l))
            .padding(bottom = bottomBarSpace),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Login,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        AppText(
            text = stringResource(ResR.string.pixiv_not_login_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_m)),
        )
        AppText(
            text = stringResource(ResR.string.pixiv_not_login_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            align = TextAlign.Center,
            modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        )
        AppPrimaryButton(
            text = stringResource(ResR.string.pixiv_go_login),
            onClick = onLogin,
            modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_l)),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun PixivBrowseGridPreview() {
    val user = PixivUser(id = 1, name = "作者", account = "author", avatarUrl = null, isFollowed = false)
    val samples = (1..6).map { i ->
        PixivIllust(
            id = i,
            title = "示例作品标题 $i",
            caption = "",
            user = user,
            tags = emptyList(),
            createDate = "",
            width = 600,
            height = 800,
            pageCount = if (i % 2 == 0) 2 else 1,
            type = "illust",
            xRestrict = 0,
            totalView = 100,
            totalBookmarks = 20,
            isBookmarked = i == 2,
            pages = listOf(PixivPage(thumb = "", medium = "", large = "", original = "")),
        )
    }
    MaterialTheme {
        Box {
            IllustGrid(items = samples, onOpen = {})
        }
    }
}

/** P 站侧边栏相对屏幕的最大宽度占比（75%）。 */
private const val DRAWER_MAX_WIDTH_FRACTION = 0.75f

/** 抽屉遮罩不透明度（取 M3 ModalDrawer scrim 的常规观感）。 */
private const val DRAWER_SCRIM_ALPHA = 0.32f

/** 抽屉开合动画：面板跟手弹簧，无硬切时长。 */
private val DRAWER_SLIDE_SPEC = spring<androidx.compose.ui.unit.IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
private val DRAWER_ENTER = fadeIn() + slideInHorizontally(animationSpec = DRAWER_SLIDE_SPEC) { full -> -full }
private val DRAWER_EXIT = fadeOut() + slideOutHorizontally(animationSpec = DRAWER_SLIDE_SPEC) { full -> -full }
