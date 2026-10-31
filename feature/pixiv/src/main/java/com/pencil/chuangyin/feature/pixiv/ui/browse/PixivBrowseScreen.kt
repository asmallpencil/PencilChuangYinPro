package com.pencil.chuangyin.feature.pixiv.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.ui.components.AppFilterChip
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.LiquidGlassLayerScope
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.ui.motion.animateItemMotion
import com.pencil.chuangyin.core.ui.motion.pressScale
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.pixiv.core.auth.PixivAccount
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.feature.pixiv.domain.PixivPage
import com.pencil.chuangyin.feature.pixiv.domain.PixivUser
import com.pencil.chuangyin.feature.pixiv.ui.PixivImageProvider
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenError
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenLoading
import com.pencil.chuangyin.feature.pixiv.ui.common.IllustGrid
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_ILLUST
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_MANGA
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_NOVEL
import com.pencil.chuangyin.feature.pixiv.ui.common.SECTION_RECOMMEND
import com.pencil.chuangyin.feature.pixiv.ui.common.browseSectionLabel
import com.pencil.chuangyin.feature.pixiv.ui.common.browseSections
import com.pencil.chuangyin.feature.pixiv.ui.common.mangaSections
import com.pencil.chuangyin.feature.pixiv.ui.common.NovelRow
import com.pencil.chuangyin.feature.pixiv.ui.common.novelSections
import com.pencil.chuangyin.feature.pixiv.ui.drawer.PixivDrawerDestination
import com.pencil.chuangyin.feature.pixiv.ui.drawer.PixivDrawerSheet
import com.pencil.chuangyin.resources.R as ResR
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
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
    val glassActive = rememberLiquidGlassActive()

    // 抽屉最多占屏幕 75% 宽度（BoxWithConstraints 取实际可用宽度）。
    BoxWithConstraints(modifier = modifier) {
        val drawerSheetWidth = maxWidth * DRAWER_MAX_WIDTH_FRACTION
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { AppText(stringResource(ResR.string.pstation_title)) },
                    navigationIcon = {
                        if (account != null) {
                            AppIconButton(
                                onClick = { drawerOpen = true },
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(ResR.string.pixiv_menu),
                                glass = false,
                            )
                        }
                    },
                    actions = {
                        if (account != null) {
                            AppIconButton(
                                onClick = onOpenSearch,
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(ResR.string.pixiv_search),
                                glass = false,
                            )
                            AppIconButton(
                                onClick = onRefresh,
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(ResR.string.pixiv_refresh),
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
        ) { innerPadding ->
            // 列表滚动状态提升到页面级：回顶按钮要据此显隐，并可直接驱动滚动。
            val gridState = rememberLazyGridState()
            val novelListState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val gridScrolled by remember {
                derivedStateOf { gridState.firstVisibleItemIndex >= BACK_TO_TOP_THRESHOLD }
            }
            val novelScrolled by remember {
                derivedStateOf { novelListState.firstVisibleItemIndex >= BACK_TO_TOP_THRESHOLD }
            }
            val showBackToTop = account != null &&
                if (state.mediaMode == MEDIA_NOVEL) novelScrolled else gridScrolled

            Column(modifier = Modifier.padding(innerPadding)) {
                if (account != null) {
                    // 插画/漫画/小说彻底分区：模式切换走独立入口，不再混排。
                    AppSegmentedRow(
                        options = listOf(
                            stringResource(ResR.string.pixiv_mode_illust),
                            stringResource(ResR.string.pixiv_mode_manga),
                            stringResource(ResR.string.pixiv_mode_novel),
                        ),
                        selectedIndex = when (state.mediaMode) {
                            MEDIA_MANGA -> 1
                            MEDIA_NOVEL -> 2
                            else -> 0
                        },
                        onSelect = { index ->
                            onSelectMode(
                                when (index) {
                                    2 -> MEDIA_NOVEL
                                    1 -> MEDIA_MANGA
                                    else -> MEDIA_ILLUST
                                },
                            )
                        },
                        modifier = Modifier.padding(
                            horizontal = dimensionResource(ResR.dimen.dimen_pixiv_grid_padding),
                            vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                        ),
                    )
                    SectionChipRow(
                        selected = state.section,
                        mediaMode = state.mediaMode,
                        onSelect = onSelectSection,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 下拉刷新：复用 ViewModel 的 refreshing 状态（刷新期间旧列表继续可见）。
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { if (account != null) onRefresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
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
                                listState = novelListState,
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
                                    // 始终允许续拉：没有 nextUrl 时 ViewModel 换一批新内容接上，
                                    // 保证主页「刷不到底」。
                                    canLoadMore = true,
                                    loadingMore = state.loadingMore,
                                    onLoadMore = onLoadMore,
                                    // 悬浮导航栏不遮挡末行卡片：底部留白经参数注入（页面背景仍延伸到栏下供毛玻璃取样）。
                                    contentPadding = PaddingValues(
                                        start = gridPadding,
                                        end = gridPadding,
                                        top = gridPadding,
                                        bottom = gridPadding + bottomBarSpace,
                                    ),
                                    listState = gridState,
                                )
                            }
                        }
                    }

                    // 右下角回顶：滑过一屏多才浮现（缩放入场 + 弹性退出），点击弹回第一张卡片。
                    BackToTopFab(
                        visible = showBackToTop,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = bottomBarSpace + 16.dp),
                        onClick = {
                            scope.launch {
                                if (state.mediaMode == MEDIA_NOVEL) {
                                    novelListState.animateScrollToItem(0)
                                } else {
                                    gridState.animateScrollToItem(0)
                                }
                            }
                        },
                    )
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
private fun SectionChipRow(
    selected: String,
    mediaMode: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipSpacing = dimensionResource(ResR.dimen.dimen_pixiv_chip_spacing)
    val screenPadding = dimensionResource(ResR.dimen.dimen_pixiv_grid_padding)
    val sections = when (mediaMode) {
        MEDIA_MANGA -> mangaSections
        MEDIA_NOVEL -> novelSections
        else -> browseSections
    }
    LazyRow(
        modifier = modifier
            .padding(vertical = chipSpacing),
        contentPadding = PaddingValues(horizontal = screenPadding),
        horizontalArrangement = Arrangement.spacedBy(chipSpacing),
    ) {
        items(sections, key = { it }) { key ->
            AppFilterChip(
                selected = key == selected,
                onClick = { onSelect(key) },
                label = browseSectionLabel(key),
            )
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
    listState: LazyListState = rememberLazyListState(),
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
            // 与插画网格同一套预取策略：滑到尾部剩余几条时自动续拉，列表不会"到底"。
            LaunchedEffect(state.novels.size, state.loadingMore) {
                if (state.loadingMore || state.refreshing || state.error) return@LaunchedEffect
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                    .distinctUntilChanged()
                    .collect { last ->
                        if (last >= state.novels.size - PREFETCH_DISTANCE) onLoadMore()
                    }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = bottomBarSpace),
            ) {
                items(state.novels, key = { it.id }) { novel ->
                    NovelRow(
                        novel = novel,
                        onClick = { onOpenNovel(novel.id) },
                        modifier = animateItemMotion(motion),
                    )
                }
                // 尾部占位：接口有下一页或正在循环续拉时都保留，让"加载中"的观感连续。
                if (state.nextUrl != null || state.loadingMore) {
                    item(key = "novel_loading") {
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

/**
 * 回顶按钮的显隐宿主：单独成一个无作用域接收者的 Composable，
 * 让 [AnimatedVisibility] 解析到顶层重载（Box/Column 嵌套时隐式接收者会冲突）。
 */
@Composable
private fun BackToTopFab(visible: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val motion = rememberAppMotion()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = motion.dialogEnter(),
        exit = motion.dialogExit(),
    ) {
        BackToTopButton(onClick = onClick)
    }
}

/**
 * 右下角「回到顶部」按钮：圆形浮起的小面积控件（内容层不铺玻璃，与卡片同一容器色）。
 * 入场/出场由 [rememberAppMotion] 的 dialogEnter/dialogExit 提供缩放 + 淡入淡出，
 * 按下有 pressScale 跟手回弹。
 */
@Composable
private fun BackToTopButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .size(46.dp)
            .pressScale(interactionSource),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = stringResource(ResR.string.pixiv_back_to_top),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionChipRowPreview() {
    SectionChipRow(selected = SECTION_RECOMMEND, mediaMode = MEDIA_ILLUST, onSelect = {})
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

/** 列表第一个可见项超过该序号才显示回顶按钮（避免轻微滑动就弹按钮）。 */
private const val BACK_TO_TOP_THRESHOLD = 6

/** 距列表尾部还剩多少条时开始预取下一页（与插画网格保持一致）。 */
private const val PREFETCH_DISTANCE = 6

/** 抽屉遮罩不透明度（取 M3 ModalDrawer scrim 的常规观感）。 */
private const val DRAWER_SCRIM_ALPHA = 0.32f

/** 抽屉开合动画：面板跟手弹簧，无硬切时长。 */
private val DRAWER_SLIDE_SPEC = spring<androidx.compose.ui.unit.IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
private val DRAWER_ENTER = fadeIn() + slideInHorizontally(animationSpec = DRAWER_SLIDE_SPEC) { full -> -full }
private val DRAWER_EXIT = fadeOut() + slideOutHorizontally(animationSpec = DRAWER_SLIDE_SPEC) { full -> -full }
