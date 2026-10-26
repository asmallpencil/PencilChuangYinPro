package com.pencil.chuangyin.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.glass.glassSurface
import com.pencil.chuangyin.desktop.pixiv.BrowseSections
import com.pencil.chuangyin.desktop.pixiv.PixivAccount
import com.pencil.chuangyin.desktop.pixiv.PixivGraph
import com.pencil.chuangyin.desktop.pixiv.PixivIllust
import com.pencil.chuangyin.desktop.pixiv.PixivMediaType
import com.pencil.chuangyin.desktop.pixiv.PixivNovel
import com.pencil.chuangyin.desktop.theme.LocalLiquidGlassControlsEnabled
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.launch

/** 侧边栏可跳转目标（与安卓端 PixivDrawerDestination 逐项一致）。 */
private enum class PixivDrawerDestination {
    ACCOUNT,
    WALKTHROUGH,
    BROWSER,
    SOURCES,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
    SETTINGS,
    GALLERY,
    BLOCKED_TAGS,
    CACHE,
}

/** P 站 tab 内部路由（层级与安卓端 P 站导航图一致，只是收敛在本 tab 内）。 */
private sealed interface PixivInnerRoute {
    data object Main : PixivInnerRoute
    data class Illust(val id: Long) : PixivInnerRoute
    data class Novel(val id: Long) : PixivInnerRoute
    data object Login : PixivInnerRoute
    data object Account : PixivInnerRoute
    data object Walkthrough : PixivInnerRoute
    data object Sources : PixivInnerRoute
    data object Bookmarks : PixivInnerRoute
    data object History : PixivInnerRoute
    data object Downloads : PixivInnerRoute
    data object Gallery : PixivInnerRoute
    data object BlockedTags : PixivInnerRoute
    data object Cache : PixivInnerRoute
    data object Settings : PixivInnerRoute
}

/** 浏览器：桌面端按既定方案调用系统默认浏览器（对齐安卓端 BROWSER 目的地的语义）。 */
private fun openSystemBrowser(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
    runCatching { PixivGraph.browserStore.recordHistory(url, "Pixiv") }
}

/**
 * P 站 tab —— 与安卓端 `PixivBrowseScreen` 同构：
 * 未登录只显示登录墙（NotLoggedIn），登录后才有 分段/分区/网格/抽屉。
 */
@Composable
fun PixivBrowseScreen(
    bottomBarSpace: Dp,
) {
    val account by PixivGraph.session.account.collectAsState()
    var route by remember { mutableStateOf<PixivInnerRoute>(PixivInnerRoute.Main) }
    var pendingSearch by remember { mutableStateOf<String?>(null) }

    when (val current = route) {
        PixivInnerRoute.Main -> PixivBrowseMain(
            account = account,
            bottomBarSpace = bottomBarSpace,
            initialSearch = pendingSearch,
            onSearchConsumed = { pendingSearch = null },
            onOpenIllust = { route = PixivInnerRoute.Illust(it) },
            onOpenNovel = { route = PixivInnerRoute.Novel(it) },
            onLogin = { route = PixivInnerRoute.Login },
            onOpenDrawerDestination = { destination ->
                route = when (destination) {
                    PixivDrawerDestination.ACCOUNT -> PixivInnerRoute.Account
                    PixivDrawerDestination.WALKTHROUGH -> PixivInnerRoute.Walkthrough
                    PixivDrawerDestination.BROWSER -> {
                        openSystemBrowser("https://www.pixiv.net/")
                        PixivInnerRoute.Main
                    }
                    PixivDrawerDestination.SOURCES -> PixivInnerRoute.Sources
                    PixivDrawerDestination.BOOKMARKS -> PixivInnerRoute.Bookmarks
                    PixivDrawerDestination.HISTORY -> PixivInnerRoute.History
                    PixivDrawerDestination.DOWNLOADS -> PixivInnerRoute.Downloads
                    PixivDrawerDestination.SETTINGS -> PixivInnerRoute.Settings
                    PixivDrawerDestination.GALLERY -> PixivInnerRoute.Gallery
                    PixivDrawerDestination.BLOCKED_TAGS -> PixivInnerRoute.BlockedTags
                    PixivDrawerDestination.CACHE -> PixivInnerRoute.Cache
                }
            },
        )

        is PixivInnerRoute.Illust -> PixivDetailScreen(
            illustId = current.id,
            bottomBarSpace = bottomBarSpace,
            onBack = { route = PixivInnerRoute.Main },
            onRequireLogin = { route = PixivInnerRoute.Login },
            onSearchTag = { tag ->
                pendingSearch = tag
                route = PixivInnerRoute.Main
            },
        )

        is PixivInnerRoute.Novel -> PixivNovelDetailScreen(
            novelId = current.id,
            bottomBarSpace = bottomBarSpace,
            onBack = { route = PixivInnerRoute.Main },
        )

        PixivInnerRoute.Login -> PixivLoginScreen(
            onBack = { route = PixivInnerRoute.Main },
            onLoggedIn = { route = PixivInnerRoute.Main },
        )

        PixivInnerRoute.Account -> PixivAccountScreen(
            onBack = { route = PixivInnerRoute.Main },
            onLogin = { route = PixivInnerRoute.Login },
        )

        PixivInnerRoute.Walkthrough -> PixivWalkthroughScreen(
            onBack = { route = PixivInnerRoute.Main },
            onOpenDetail = { route = PixivInnerRoute.Illust(it) },
        )

        PixivInnerRoute.Sources -> PixivSourcesScreen(
            onBack = { route = PixivInnerRoute.Main },
        )

        PixivInnerRoute.Bookmarks -> PixivBookmarksScreen(
            onBack = { route = PixivInnerRoute.Main },
            onOpenDetail = { route = PixivInnerRoute.Illust(it) },
            onOpenNovel = { route = PixivInnerRoute.Novel(it) },
        )

        PixivInnerRoute.History -> PixivHistoryScreen(
            onBack = { route = PixivInnerRoute.Main },
            onOpenDetail = { route = PixivInnerRoute.Illust(it) },
        )

        PixivInnerRoute.Downloads -> PixivDownloadsScreen(
            onBack = { route = PixivInnerRoute.Main },
        )

        PixivInnerRoute.Gallery -> PixivGalleryScreen(
            onBack = { route = PixivInnerRoute.Main },
        )

        PixivInnerRoute.BlockedTags -> PixivBlockedTagsScreen(
            onBack = { route = PixivInnerRoute.Main },
        )

        PixivInnerRoute.Cache -> PixivCacheScreen(
            onBack = { route = PixivInnerRoute.Main },
        )

        PixivInnerRoute.Settings -> PixivFunctionalSettingsScreen(
            onBack = { route = PixivInnerRoute.Main },
            onOpenBlockedTags = { route = PixivInnerRoute.BlockedTags },
        )
    }
}

@Composable
private fun PixivBrowseMain(
    account: PixivAccount?,
    bottomBarSpace: Dp,
    initialSearch: String?,
    onSearchConsumed: () -> Unit,
    onOpenDrawerDestination: (PixivDrawerDestination) -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onLogin: () -> Unit,
) {
    val client = PixivGraph.client
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var mediaMode by remember { mutableStateOf(PixivMediaType.Illust) }
    var section by remember { mutableStateOf(BrowseSections.RECOMMEND) }
    var refreshTick by remember { mutableIntStateOf(0) }

    var items by remember { mutableStateOf<List<PixivIllust>>(emptyList()) }
    var nextUrl by remember { mutableStateOf<String?>(null) }
    var novels by remember { mutableStateOf<List<PixivNovel>>(emptyList()) }
    var novelNextUrl by remember { mutableStateOf<String?>(null) }
    var initialLoading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var searchOpen by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchPage by remember { mutableIntStateOf(1) }

    var drawerOpen by remember { mutableStateOf(false) }

    val sections = remember(mediaMode) { BrowseSections.sectionsOf(mediaMode) }
    val gridState = rememberLazyGridState()

    // 切换媒体类型时，分区回落到该类型的第一项（安卓端同样行为）。
    LaunchedEffect(mediaMode) {
        val list = BrowseSections.sectionsOf(mediaMode)
        if (list.none { it.first == section }) section = list.first().first
    }

    // 从详情页点标签跳转过来时，直接复用顶栏搜索（与安卓端 onSearchTag 行为一致）。
    LaunchedEffect(initialSearch) {
        val query = initialSearch?.trim().orEmpty()
        if (query.isNotEmpty()) {
            searchOpen = true
            searchInput = query
            searchQuery = query
            onSearchConsumed()
        }
    }

    LaunchedEffect(mediaMode, section, refreshTick, account?.userId, searchQuery) {
        items = emptyList()
        novels = emptyList()
        nextUrl = null
        novelNextUrl = null
        error = null
        if (account == null) return@LaunchedEffect
        initialLoading = true
        runCatching {
            when {
                searchQuery.isNotBlank() -> {
                    searchPage = 1
                    val page = client.search(searchQuery, 1)
                    items = page.items
                    nextUrl = page.nextUrl
                }

                mediaMode == PixivMediaType.Novel -> {
                    val page = client.novels(section)
                    novels = page.items
                    novelNextUrl = page.nextUrl
                }

                else -> {
                    val page = client.illusts(mediaMode, section)
                    items = page.items
                    nextUrl = page.nextUrl
                }
            }
        }.onFailure { throwable ->
            error = throwable.message?.take(160) ?: "请求失败"
        }
        initialLoading = false
    }

    suspend fun loadMore() {
        if (loadingMore) return
        loadingMore = true
        runCatching {
            when {
                searchQuery.isNotBlank() -> {
                    searchPage += 1
                    val page = client.search(searchQuery, searchPage)
                    items = items + page.items
                    nextUrl = page.nextUrl
                }

                mediaMode == PixivMediaType.Novel -> {
                    val url = novelNextUrl ?: return@runCatching
                    val page = client.novels(section, url)
                    novels = novels + page.items
                    novelNextUrl = page.nextUrl
                }

                else -> {
                    val url = nextUrl ?: return@runCatching
                    val page = client.illusts(mediaMode, section, url)
                    items = items + page.items
                    nextUrl = page.nextUrl
                }
            }
        }.onFailure { snackbarHost.showSnackbar("加载更多失败：${it.message?.take(80) ?: ""}") }
        loadingMore = false
    }

    // 触底自动翻页（安卓端 IllustGrid 的 last >= size - 3 规则）。
    LaunchedEffect(gridState, mediaMode, section, searchQuery) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (mediaMode != PixivMediaType.Novel &&
                    nextUrl != null &&
                    !loadingMore &&
                    items.isNotEmpty() &&
                    last >= items.size - 3
                ) {
                    loadMore()
                }
            }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val drawerWidth = (maxWidth * 0.45f).coerceIn(280.dp, 420.dp)

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                PixivTopBar(
                    account = account,
                    searchOpen = searchOpen,
                    onOpenDrawer = { drawerOpen = true },
                    onToggleSearch = {
                        searchOpen = !searchOpen
                        if (!searchOpen) {
                            searchInput = ""
                            searchQuery = ""
                        }
                    },
                    onRefresh = { refreshTick++ },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (account != null) {
                    AppSegmentedRow(
                        options = PixivMediaType.entries.map { it.label },
                        selectedIndex = mediaMode.ordinal,
                        onSelected = { index ->
                            mediaMode = PixivMediaType.entries[index]
                            searchQuery = ""
                            searchInput = ""
                            searchOpen = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    if (searchOpen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppTextField(
                                value = searchInput,
                                onValueChange = { searchInput = it },
                                placeholder = "搜索作品 / 标签",
                                modifier = Modifier.weight(1f),
                                onDone = { searchQuery = searchInput.trim() },
                            )
                            AppPrimaryButton(
                                text = "搜索",
                                enabled = searchInput.isNotBlank(),
                                onClick = { searchQuery = searchInput.trim() },
                            )
                        }
                    }

                    if (searchQuery.isBlank()) {
                        SectionChipRow(
                            sections = sections,
                            selected = section,
                            onSelect = { section = it },
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "搜索「$searchQuery」",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    when {
                        account == null -> NotLoggedIn(
                            bottomBarSpace = bottomBarSpace,
                            onLogin = onLogin,
                        )

                        initialLoading -> LoadingBox()

                        error != null && items.isEmpty() && novels.isEmpty() -> ErrorBox(
                            message = error.orEmpty(),
                            onRetry = { refreshTick++ },
                        )

                        mediaMode == PixivMediaType.Novel -> NovelFeed(
                            novels = novels,
                            novelNextUrl = novelNextUrl,
                            bottomBarSpace = bottomBarSpace,
                            onClick = onOpenNovel,
                            onLoadMore = { scope.launch { loadMore() } },
                        )

                        items.isEmpty() -> AppEmptyState(
                            icon = AppIcon.ImageTool,
                            title = "这里还没有作品",
                            description = "换个分区，或下拉刷新试试",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 32.dp),
                            action = { AppPrimaryButton(text = "刷新", onClick = { refreshTick++ }) },
                        )

                        else -> IllustGrid(
                            items = items,
                            gridState = gridState,
                            bottomBarSpace = bottomBarSpace,
                            loadingMore = loadingMore,
                            onClick = onOpenIllust,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(interactionSource = interaction, indication = null) { drawerOpen = false },
            )
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn() + slideInHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { -it },
            exit = fadeOut() + slideOutHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { -it },
        ) {
            Box(Modifier.fillMaxSize()) {
                PixivDrawer(
                    account = account,
                    sheetWidth = drawerWidth,
                    bottomBarSpace = bottomBarSpace,
                    onDestination = { destination ->
                        drawerOpen = false
                        onOpenDrawerDestination(destination)
                    },
                    onClose = { drawerOpen = false },
                )
            }
        }
    }
}

@Composable
private fun PixivTopBar(
    account: PixivAccount?,
    searchOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onToggleSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    GlassTopAppBar(
        title = "P站",
        navigationIcon = {
            if (account != null) {
                AppIconButton(icon = AppIcon.Menu, contentDescription = "菜单", onClick = onOpenDrawer)
            }
        },
        actions = {
            if (account != null) {
                AppIconButton(
                    icon = AppIcon.Search,
                    contentDescription = if (searchOpen) "关闭搜索" else "搜索",
                    onClick = onToggleSearch,
                    tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppIconButton(icon = AppIcon.Refresh, contentDescription = "刷新", onClick = onRefresh)
            }
        },
    )
}

@Composable
private fun SectionChipRow(
    sections: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sections.size) { index ->
            val (key, label) = sections[index]
            SectionChip(
                label = label,
                selected = key == selected,
                onClick = { onSelect(key) },
            )
        }
    }
}

@Composable
private fun IllustGrid(
    items: List<PixivIllust>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    bottomBarSpace: Dp,
    loadingMore: Boolean,
    onClick: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = bottomBarSpace + 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = items.size, key = { items[it].id }) { index ->
            val item = items[index]
            IllustCard(item = item, onClick = { onClick(item.id) })
        }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelFeed(
    novels: List<PixivNovel>,
    novelNextUrl: String?,
    bottomBarSpace: Dp,
    onClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomBarSpace + 8.dp),
    ) {
        items(count = novels.size, key = { novels[it].id }) { index ->
            val novel = novels[index]
            NovelRow(novel = novel, onClick = { onClick(novel.id) })
        }
        item(key = "novel_loading") {
            LaunchedEffect(novels.size) {
                if (novelNextUrl != null) onLoadMore()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (novelNextUrl != null) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotLoggedIn(bottomBarSpace: Dp, onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .padding(bottom = bottomBarSpace),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 严格对齐安卓 NotLoggedIn：默认 24dp 图标 + titleLarge 标题 + bodyMedium 描述 + 主按钮。
        AppIconView(icon = AppIcon.Login, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "尚未登录",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "登录后可浏览推荐与排行榜、搜索作品，并把原图一键送到打印页",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        AppPrimaryButton(text = "去登录", onClick = onLogin)
    }
}

@Composable
private fun PixivDrawer(
    account: PixivAccount?,
    sheetWidth: Dp,
    bottomBarSpace: Dp,
    onDestination: (PixivDrawerDestination) -> Unit,
    onClose: () -> Unit,
) {
    val glassEnabled = LocalLiquidGlassControlsEnabled.current
    // 安卓端：液态玻璃开启时整块侧边栏底色由原生玻璃层绘制，内容不再套不透明 Surface。
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(sheetWidth)
            .glassSurface(
                shape = MaterialTheme.shapes.large,
                enabled = glassEnabled,
                fallbackColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            DrawerHeader(account = account, onClose = onClose)

            // 主功能 8 项（顺序与安卓端 primaryItems 完全一致）。
            DrawerRow(icon = AppIcon.ManageAccounts, label = "账号管理") {
                onDestination(PixivDrawerDestination.ACCOUNT)
            }
            DrawerRow(icon = AppIcon.Slideshow, label = "速览漫步") {
                onDestination(PixivDrawerDestination.WALKTHROUGH)
            }
            DrawerRow(icon = AppIcon.Language, label = "浏览器") {
                onDestination(PixivDrawerDestination.BROWSER)
            }
            DrawerRow(icon = AppIcon.ImageSearch, label = "以图搜源") {
                onDestination(PixivDrawerDestination.SOURCES)
            }
            DrawerRow(icon = AppIcon.Bookmark, label = "收藏") {
                onDestination(PixivDrawerDestination.BOOKMARKS)
            }
            DrawerRow(icon = AppIcon.History, label = "历史记录") {
                onDestination(PixivDrawerDestination.HISTORY)
            }
            DrawerRow(icon = AppIcon.Download, label = "下载进度") {
                onDestination(PixivDrawerDestination.DOWNLOADS)
            }
            DrawerRow(icon = AppIcon.Settings, label = "功能设置") {
                onDestination(PixivDrawerDestination.SETTINGS)
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // 本地管理 3 项。
            DrawerRow(icon = AppIcon.Collections, label = "已保存内容") {
                onDestination(PixivDrawerDestination.GALLERY)
            }
            DrawerRow(icon = AppIcon.Block, label = "屏蔽标签") {
                onDestination(PixivDrawerDestination.BLOCKED_TAGS)
            }
            DrawerRow(icon = AppIcon.Cached, label = "运行缓存") {
                onDestination(PixivDrawerDestination.CACHE)
            }

            // 与安卓端一致：底部留出悬浮导航栏高度 + 全局间距 s，避免最后一项被遮挡。
            Spacer(Modifier.height(bottomBarSpace + 8.dp))
        }
    }
}

@Composable
private fun DrawerHeader(account: PixivAccount?, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            AppIconButton(
                icon = AppIcon.ChevronLeft,
                contentDescription = "收起侧边栏",
                onClick = onClose,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        PixivAvatar(url = account?.avatarUrl, size = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = account?.name.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = account?.account.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrawerRow(icon: AppIcon, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(icon = icon, size = 24.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
