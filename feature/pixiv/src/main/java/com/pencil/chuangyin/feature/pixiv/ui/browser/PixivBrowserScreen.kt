package com.pencil.chuangyin.feature.pixiv.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.ui.components.AppAlertDialog
import com.pencil.chuangyin.core.ui.components.AppLiquidDialog
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.components.LiquidGlassLayerScope
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.pseudoGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.ui.motion.animateItemMotion
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.pixiv.data.local.BrowserBookmarkEntry
import com.pencil.chuangyin.feature.pixiv.data.local.BrowserHistoryEntry
import com.pencil.chuangyin.resources.R as ResR

/** 浏览器底部弹层种类（同一时刻只展示一个）。 */
private enum class BrowserSheet { MENU, TABS, BOOKMARKS, HISTORY, TOOLBOX }

/** 工具箱内的二级弹窗。 */
private enum class ToolboxDialog { COOKIES, SITE_INFO }

private data class BrowserTab(
    val id: Long,
    val url: String,
    val title: String,
)

/**
 * P 站内置浏览器（Edge 移动版风格，REQ7）：
 * 顶栏（搜索/地址 + 全屏）+ 底栏（后退/前进/主页/标签数/菜单）；
 * 菜单：夜间模式、书签、历史、下载、隐身、添加书签、电脑模式、工具箱、设置、页内查找。
 * WebView 为平台控件，经 AndroidView 承载，不属于 M2/view 体系 UI 组件。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixivBrowserRoute(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PixivBrowserViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val bottomBarHeight = dimensionResource(ResR.dimen.dimen_pixiv_browser_bottom_bar_height)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf(PIXIV_HOME_URL) }
    var pageTitle by remember { mutableStateOf(String()) }
    var urlDraft by remember { mutableStateOf(String()) }

    var incognito by remember { mutableStateOf(false) }
    var nightMode by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(false) }
    var readerMode by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

    var sheet by remember { mutableStateOf<BrowserSheet?>(null) }
    var toolboxDialog by remember { mutableStateOf<ToolboxDialog?>(null) }
    var findActive by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf(String()) }
    var findResult by remember { mutableStateOf(String()) }

    // 轻量标签页：保存各标签 URL/标题，切换标签时在同一 WebView 载入。
    var tabs by remember {
        mutableStateOf(listOf(BrowserTab(id = 1L, url = PIXIV_HOME_URL, title = String())))
    }
    var selectedTabId by remember { mutableStateOf(1L) }
    var nextTabId by remember { mutableStateOf(2L) }
    // WebView 建好后的首次加载只在 update 里做一次：在 factory 里直接 loadUrl 时视图尚未
    // 参与布局，部分机型会把这次请求丢掉，表现为「进来后标签页和地址都在，但页面不动」。
    var initialLoadDone by remember { mutableStateOf(false) }

    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    val snackbarHostState = androidx.compose.material3.SnackbarHostState()
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    val homeLabel = stringResource(ResR.string.pixiv_browser_page_home)
    val incognitoOnMsg = stringResource(ResR.string.pixiv_browser_incognito_on)
    val incognitoOffMsg = stringResource(ResR.string.pixiv_browser_incognito_off)
    val bookmarkAddedMsg = stringResource(ResR.string.pixiv_browser_bookmark_added)
    val siteDataClearedMsg = stringResource(ResR.string.pixiv_browser_site_data_cleared)
    val downloadsFailedMsg = stringResource(ResR.string.pixiv_browser_downloads_open_failed)

    fun syncNavState(view: WebView?) {
        canGoBack = view?.canGoBack() == true
        canGoForward = view?.canGoForward() == true
    }

    fun applyInject(view: WebView) {
        view.evaluateJavascript(buildInjectJs(nightMode, readerMode), null)
    }

    fun loadUrl(url: String) {
        webViewRef?.let { view ->
            currentUrl = url
            view.loadUrl(url)
        }
    }

    fun newTab() {
        val id = nextTabId.toLong()
        nextTabId += 1
        tabs = tabs + BrowserTab(id = id, url = PIXIV_HOME_URL, title = String())
        selectedTabId = id
        loadUrl(PIXIV_HOME_URL)
    }

    fun selectTab(tab: BrowserTab) {
        if (tab.id != selectedTabId) {
            selectedTabId = tab.id
            loadUrl(tab.url)
        }
        sheet = null
    }

    fun closeTab(tabId: Long) {
        val wasSelected = tabId == selectedTabId
        val remaining = tabs.filterNot { it.id == tabId }
        tabs = if (remaining.isEmpty()) {
            val fresh = BrowserTab(id = nextTabId.toLong(), url = PIXIV_HOME_URL, title = String())
            nextTabId += 1
            selectedTabId = fresh.id
            listOf(fresh)
        } else {
            if (wasSelected) selectedTabId = remaining.first().id
            remaining
        }
        if (wasSelected) loadUrl(tabs.first { it.id == selectedTabId }.url)
    }

    fun toggleIncognito() {
        val target = !incognito
        incognito = target
        webViewRef?.let { view ->
            // 进入/退出隐身：清空会话 Cookie、缓存与前进后退历史，再回到主页。
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                removeSessionCookies(null)
                flush()
            }
            view.clearCache(true)
            view.clearHistory()
        }
        val fresh = BrowserTab(id = nextTabId.toLong(), url = PIXIV_HOME_URL, title = String())
        nextTabId += 1
        tabs = listOf(fresh)
        selectedTabId = fresh.id
        loadUrl(PIXIV_HOME_URL)
        snackbarMessage = if (target) incognitoOnMsg else incognitoOffMsg
    }

    BackHandler(enabled = findActive || sheet != null || canGoBack) {
        when {
            findActive -> {
                findActive = false
                findQuery = String()
                webViewRef?.clearMatches()
            }
            sheet != null -> sheet = null
            canGoBack -> webViewRef?.goBack()
        }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val motion = rememberAppMotion()
    // 网页加载进度用弹簧追赶，避免 0→12%→37% 一格一格硬跳。
    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = motion.effectSpec(),
        label = "browserPageProgress",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!fullscreen) {
                BrowserTopBar(
                    urlText = currentUrl,
                    incognito = incognito,
                    urlDraft = urlDraft,
                    onUrlDraftChange = { urlDraft = it },
                    onSubmit = { raw ->
                        val resolved = resolveBrowserInput(raw)
                        urlDraft = resolved
                        focusManager.clearFocus()
                        loadUrl(resolved)
                    },
                    onToggleFullscreen = { fullscreen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(statusBarPadding),
                )
                AnimatedVisibility(
                    visible = progress in 1..99,
                    enter = motion.expandHorizontalEnter(),
                    exit = motion.shrinkHorizontalExit(),
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    syncNavState(view)
                                    url?.let {
                                        currentUrl = it
                                        urlDraft = it
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    syncNavState(view)
                                    progress = 100
                                    val finishedUrl = url.orEmpty()
                                    currentUrl = finishedUrl
                                    val title = view?.title.orEmpty()
                                    pageTitle = title
                                    urlDraft = if (finishedUrl == PIXIV_HOME_URL) homeLabel else title.ifBlank { finishedUrl }
                                    tabs = tabs.map { tab ->
                                        if (tab.id == selectedTabId) {
                                            tab.copy(
                                                url = finishedUrl,
                                                title = title.ifBlank { finishedUrl },
                                            )
                                        } else {
                                            tab
                                        }
                                    }
                                    if (!incognito && finishedUrl.isNotBlank()) {
                                        viewModel.recordHistory(finishedUrl, title)
                                    }
                                    view?.let { applyInject(it) }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                                findResult = if (numberOfMatches > 0) {
                                    "$activeMatchOrdinal/$numberOfMatches"
                                } else {
                                    "0/0"
                                }
                            }
                            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                onDownloadRequested(
                                    context = context,
                                    url = url,
                                    userAgent = userAgent,
                                    contentDisposition = contentDisposition,
                                    mimetype = mimetype,
                                    onStarted = { snackbarMessage = it },
                                )
                            }
                            // 首次加载放在 update 里（见 initialLoadDone），这里只登记引用。
                            webViewRef = this
                        }
                    },
                    update = { view ->
                        if (!initialLoadDone) {
                            initialLoadDone = true
                            currentUrl = PIXIV_HOME_URL
                            urlDraft = PIXIV_HOME_URL
                            progress = 1
                            view.loadUrl(PIXIV_HOME_URL)
                        }
                    },
                )

                if (findActive && !fullscreen) {
                    FindInPageBar(
                        query = findQuery,
                        resultText = findResult,
                        onQueryChange = { q ->
                            findQuery = q
                            if (q.isBlank()) {
                                webViewRef?.clearMatches()
                                findResult = String()
                            } else {
                                webViewRef?.findAllAsync(q)
                            }
                        },
                        onFindPrev = { webViewRef?.findNext(false) },
                        onFindNext = { webViewRef?.findNext(true) },
                        onClose = {
                            findActive = false
                            findQuery = String()
                            webViewRef?.clearMatches()
                        },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }

            if (!fullscreen) {
                BrowserBottomBar(
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    tabCount = tabs.size,
                    onBack = { webViewRef?.goBack() },
                    onForward = { webViewRef?.goForward() },
                    onHome = { loadUrl(PIXIV_HOME_URL) },
                    onOpenTabs = { sheet = BrowserSheet.TABS },
                    onOpenMenu = { sheet = BrowserSheet.MENU },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(navBarPadding),
                )
            }
        }

        if (fullscreen) {
            FilledTonalIconButton(
                onClick = { fullscreen = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(statusBarPadding)
                    .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
            ) {
                Icon(
                    imageVector = Icons.Filled.FullscreenExit,
                    contentDescription = stringResource(ResR.string.pixiv_browser_exit_fullscreen),
                )
            }
        }

        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = if (fullscreen) {
                        navBarPadding.calculateBottomPadding() + dimensionResource(ResR.dimen.dimen_global_padding_m)
                    } else {
                        bottomBarHeight + navBarPadding.calculateBottomPadding()
                    },
                ),
        )
    }

    // ── 底部弹层 ─────────────────────────────────────────────────────────────
    when (sheet) {
        BrowserSheet.MENU -> {
            BrowserMenuSheet(
                nightMode = nightMode,
                desktopMode = desktopMode,
                incognito = incognito,
                onDismiss = { sheet = null },
                onExit = onBack,
                onNightMode = {
                    nightMode = !nightMode
                    webViewRef?.let { applyInject(it) }
                },
                onBookmarks = { sheet = BrowserSheet.BOOKMARKS },
                onHistory = { sheet = BrowserSheet.HISTORY },
                onDownloads = {
                    sheet = null
                    runCatching {
                        context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                    }.onFailure { snackbarMessage = downloadsFailedMsg }
                },
                onIncognito = { sheet = null; toggleIncognito() },
                onAddBookmark = {
                    val title = pageTitle.ifBlank { currentUrl }
                    viewModel.addBookmark(currentUrl, title)
                    snackbarMessage = bookmarkAddedMsg
                },
                onDesktopMode = {
                    desktopMode = !desktopMode
                    webViewRef?.let { view ->
                        view.settings.userAgentString =
                            if (desktopMode) DESKTOP_USER_AGENT else null
                        view.reload()
                    }
                },
                onToolbox = { sheet = BrowserSheet.TOOLBOX },
                onSettings = {
                    sheet = null
                    onOpenSettings()
                },
                onFindInPage = {
                    sheet = null
                    findActive = true
                },
            )
        }

        BrowserSheet.TABS -> {
            BrowserTabsSheet(
                tabs = tabs,
                selectedTabId = selectedTabId,
                onDismiss = { sheet = null },
                onNewTab = {
                    newTab()
                    sheet = null
                },
                onSelect = ::selectTab,
                onClose = ::closeTab,
            )
        }

        BrowserSheet.BOOKMARKS -> {
            BrowserBookmarkListSheet(
                bookmarks = bookmarks,
                onDismiss = { sheet = null },
                onOpen = { entry ->
                    sheet = null
                    loadUrl(entry.url)
                },
                onDelete = { entry -> viewModel.removeBookmark(entry.url) },
                onClear = { viewModel.clearBookmarks() },
            )
        }

        BrowserSheet.HISTORY -> {
            BrowserHistoryListSheet(
                history = history,
                onDismiss = { sheet = null },
                onOpen = { entry ->
                    sheet = null
                    loadUrl(entry.url)
                },
                onClear = { viewModel.clearHistory() },
            )
        }

        BrowserSheet.TOOLBOX -> {
            BrowserToolboxSheet(
                readerMode = readerMode,
                onDismiss = { sheet = null },
                onShowCookies = { toolboxDialog = ToolboxDialog.COOKIES },
                onShowSiteInfo = { toolboxDialog = ToolboxDialog.SITE_INFO },
                onClearSiteData = {
                    clearCurrentSiteData(url = currentUrl, webView = webViewRef)
                    snackbarMessage = siteDataClearedMsg
                },
                onToggleReader = {
                    readerMode = !readerMode
                    webViewRef?.let { applyInject(it) }
                },
            )
        }

        null -> Unit
    }

    when (toolboxDialog) {
        ToolboxDialog.COOKIES -> {
            val cookies = CookieManager.getInstance().getCookie(currentUrl)
            SimpleTextDialog(
                title = stringResource(ResR.string.pixiv_browser_cookies),
                message = cookies?.takeIf { it.isNotBlank() }
                    ?: stringResource(ResR.string.pixiv_browser_no_cookies),
                onDismiss = { toolboxDialog = null },
            )
        }

        ToolboxDialog.SITE_INFO -> {
            val parsed = runCatching { Uri.parse(currentUrl) }.getOrNull()
            val scheme = parsed?.scheme.orEmpty().uppercase()
            val host = parsed?.host.orEmpty()
            SimpleTextDialog(
                title = stringResource(ResR.string.pixiv_browser_site_info),
                message = "$scheme\n$host",
                onDismiss = { toolboxDialog = null },
            )
        }

        null -> Unit
    }
}

// ── 顶栏 / 底栏 / 查找栏 ──────────────────────────────────────────────────────

@Composable
private fun BrowserTopBar(
    urlText: String,
    incognito: Boolean,
    urlDraft: String,
    onUrlDraftChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val barHeight = dimensionResource(ResR.dimen.dimen_pixiv_browser_bar_height)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (incognito) {
            Icon(
                imageVector = Icons.Filled.VisibilityOff,
                contentDescription = stringResource(ResR.string.pixiv_browser_incognito),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = dimensionResource(ResR.dimen.dimen_global_padding_s))
                    .size(dimensionResource(ResR.dimen.dimen_pixiv_drawer_icon_size)),
            )
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                // 地址栏属于输入框，按统一政策启用真液态玻璃；引擎只采样静态背景，
                // 滚动时不会再出现「滞后一帧」的抖动。
                .nativeGlassSurface(
                    shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_pixiv_browser_url_radius)),
                    enabled = rememberLiquidGlassActive(),
                    fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_pixiv_browser_url_radius)),
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_pixiv_drawer_icon_size)),
                )
                Spacer(modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                BasicTextField(
                    value = urlDraft,
                    onValueChange = onUrlDraftChange,
                    singleLine = true,
                    maxLines = 1,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            onSubmit(urlDraft)
                            focusManager.clearFocus()
                        },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            if (state.isFocused) onUrlDraftChange(urlText)
                        },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (urlDraft.isBlank()) {
                                AppText(
                                    text = stringResource(ResR.string.pixiv_browser_search_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
        AppIconButton(
            onClick = onToggleFullscreen,
            imageVector = Icons.Filled.Fullscreen,
            contentDescription = stringResource(ResR.string.pixiv_browser_fullscreen),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BrowserBottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassActive = rememberLiquidGlassActive()
    val barHeight = dimensionResource(ResR.dimen.dimen_pixiv_browser_bottom_bar_height)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .nativeGlassSurface(
                shape = RoundedCornerShape(0.dp),
                enabled = glassActive,
                fallbackColor = MaterialTheme.colorScheme.surface,
            ),
        color = Color.Transparent,
        tonalElevation = dimensionResource(ResR.dimen.dimen_pixiv_browser_fab_elevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrowserBarIcon(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = stringResource(ResR.string.pixiv_browser_back),
                enabled = canGoBack,
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            BrowserBarIcon(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                description = stringResource(ResR.string.pixiv_browser_forward),
                enabled = canGoForward,
                onClick = onForward,
                modifier = Modifier.weight(1f),
            )
            BrowserBarIcon(
                icon = Icons.Filled.Home,
                description = stringResource(ResR.string.pixiv_browser_home),
                enabled = true,
                onClick = onHome,
                modifier = Modifier.weight(1f),
            )
            // 标签计数按钮（方框内数字，对应截图）。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenTabs),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_s)),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        width = dimensionResource(ResR.dimen.dimen_global_border),
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_pixiv_browser_tab_badge_size)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AppText(
                            text = tabCount.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            align = TextAlign.Center,
                        )
                    }
                }
            }
            BrowserBarIcon(
                icon = Icons.Filled.Menu,
                description = stringResource(ResR.string.pixiv_browser_menu),
                enabled = true,
                onClick = onOpenMenu,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BrowserBarIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(dimensionResource(ResR.dimen.dimen_pixiv_browser_bottom_bar_height))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_pixiv_drawer_icon_size)),
        )
    }
}

@Composable
private fun FindInPageBar(
    query: String,
    resultText: String,
    onQueryChange: (String) -> Unit,
    onFindPrev: () -> Unit,
    onFindNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_s))
            // 查找栏内是「输入框」：真玻璃背景纹理滞后一帧，随网页滚动时会抖动 → 固定用伪玻璃。
            .pseudoGlassSurface(
                shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_global_radius_s)),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ),
        shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_global_radius_s)),
        color = Color.Transparent,
        tonalElevation = dimensionResource(ResR.dimen.dimen_pixiv_browser_fab_elevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_s)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.pencil.chuangyin.core.ui.components.AppTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(ResR.string.pixiv_browser_find_hint),
                modifier = Modifier.weight(1f),
            )
            AppText(
                text = resultText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_s)),
            )
            AppIconButton(
                onClick = onFindPrev,
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(ResR.string.pixiv_browser_find_prev),
            )
            AppIconButton(
                onClick = onFindNext,
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(ResR.string.pixiv_browser_find_next),
            )
            AppIconButton(
                onClick = onClose,
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(ResR.string.pixiv_browser_close),
            )
        }
    }
}

// ── 菜单弹层 ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserMenuSheet(
    nightMode: Boolean,
    desktopMode: Boolean,
    incognito: Boolean,
    onDismiss: () -> Unit,
    onExit: () -> Unit,
    onNightMode: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onIncognito: () -> Unit,
    onAddBookmark: () -> Unit,
    onDesktopMode: () -> Unit,
    onToolbox: () -> Unit,
    onSettings: () -> Unit,
    onFindInPage: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // 层级提升：底部表单内的原生玻璃控件压住导航栏/子导航等低层控件。
        LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_s))
                .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                MenuGridItem(
                    icon = Icons.Filled.DarkMode,
                    label = stringResource(ResR.string.pixiv_browser_night_mode),
                    active = nightMode,
                    onClick = onNightMode,
                    modifier = Modifier.weight(1f),
                )
                MenuGridItem(
                    icon = Icons.Filled.BookmarkBorder,
                    label = stringResource(ResR.string.pixiv_browser_bookmarks),
                    active = false,
                    onClick = onBookmarks,
                    modifier = Modifier.weight(1f),
                )
                MenuGridItem(
                    icon = Icons.Filled.History,
                    label = stringResource(ResR.string.pixiv_browser_history),
                    active = false,
                    onClick = onHistory,
                    modifier = Modifier.weight(1f),
                )
                MenuGridItem(
                    icon = Icons.Filled.Download,
                    label = stringResource(ResR.string.pixiv_browser_downloads),
                    active = false,
                    onClick = onDownloads,
                    modifier = Modifier.weight(1f),
                )
                MenuGridItem(
                    icon = Icons.Filled.VisibilityOff,
                    label = stringResource(ResR.string.pixiv_browser_incognito),
                    active = incognito,
                    onClick = onIncognito,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            Row(modifier = Modifier.fillMaxWidth()) {
                MenuGridItem(
                    icon = Icons.Filled.BookmarkAdd,
                    label = stringResource(ResR.string.pixiv_browser_add_bookmark),
                    active = false,
                    onClick = onAddBookmark,
                    modifier = Modifier.weight(1f),
                )
                MenuGridItem(
                    icon = Icons.Filled.DesktopWindows,
                    label = stringResource(ResR.string.pixiv_browser_desktop_mode),
                    active = desktopMode,
                    onClick = onDesktopMode,
                    modifier = Modifier.weight(1f),
                )
                MenuGridItem(
                    icon = Icons.Filled.Handyman,
                    label = stringResource(ResR.string.pixiv_browser_toolbox),
                    active = false,
                    onClick = onToolbox,
                    modifier = Modifier.weight(1f),
                )
                MenuGridItem(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    label = stringResource(ResR.string.pixiv_drawer_functional_settings),
                    active = false,
                    onClick = onSettings,
                    modifier = Modifier.weight(1f),
                )
                MenuGridItem(
                    icon = Icons.Filled.FindInPage,
                    label = stringResource(ResR.string.pixiv_browser_find_in_page),
                    active = false,
                    onClick = onFindInPage,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_padding_m)))
            // 单页指示点（功能一屏容纳，无多页滑动）。
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(dimensionResource(ResR.dimen.dimen_global_padding_s))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
            Spacer(modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                AppIconButton(
                    onClick = onExit,
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = stringResource(ResR.string.pixiv_browser_exit),
                )
                Spacer(modifier = Modifier.weight(1f))
                AppIconButton(
                    onClick = onDismiss,
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(ResR.string.pixiv_browser_close),
                )
            }
        }
        }
    }
}

@Composable
private fun MenuGridItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .heightIn(min = dimensionResource(ResR.dimen.dimen_pixiv_browser_menu_item_min_height))
            .clickable(onClick = onClick)
            .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_pixiv_browser_menu_icon_size)),
        )
        Spacer(modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        AppText(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 1,
        )
    }
}

// ── 标签页 / 书签 / 历史 / 工具箱 弹层 ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTabsSheet(
    tabs: List<BrowserTab>,
    selectedTabId: Long,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onSelect: (BrowserTab) -> Unit,
    onClose: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
        SheetHeader(
            title = stringResource(ResR.string.pixiv_browser_tabs),
            actionIcon = Icons.Filled.Add,
            actionDescription = stringResource(ResR.string.pixiv_browser_new_tab),
            onAction = onNewTab,
        )
        val motion = rememberAppMotion()
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(tabs, key = { it.id }) { tab ->
                Row(
                    modifier = animateItemMotion(motion)
                        .fillMaxWidth()
                        .clickable { onSelect(tab) }
                        .padding(
                            horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                            vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(
                            text = tab.title.ifBlank { tab.url },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (tab.id == selectedTabId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AppText(
                            text = tab.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AppIconButton(
                        onClick = { onClose(tab.id) },
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(ResR.string.pixiv_browser_close_tab),
                    )
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserBookmarkListSheet(
    bookmarks: List<BrowserBookmarkEntry>,
    onDismiss: () -> Unit,
    onOpen: (BrowserBookmarkEntry) -> Unit,
    onDelete: (BrowserBookmarkEntry) -> Unit,
    onClear: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
        SheetHeader(
            title = stringResource(ResR.string.pixiv_browser_bookmarks),
            actionIcon = Icons.Filled.DeleteSweep,
            actionDescription = stringResource(ResR.string.pixiv_browser_clear_bookmarks),
            onAction = onClear,
        )
        if (bookmarks.isEmpty()) {
            SheetEmpty(text = stringResource(ResR.string.pixiv_browser_empty_bookmarks))
        } else {
            val motion = rememberAppMotion()
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(bookmarks, key = { it.url }) { entry ->
                    Row(
                        modifier = animateItemMotion(motion)
                            .fillMaxWidth()
                            .clickable { onOpen(entry) }
                            .padding(
                                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m)),
                        ) {
                            AppText(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            AppText(
                                text = entry.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        AppIconButton(
                            onClick = { onDelete(entry) },
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(ResR.string.pixiv_browser_close),
                        )
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserHistoryListSheet(
    history: List<BrowserHistoryEntry>,
    onDismiss: () -> Unit,
    onOpen: (BrowserHistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
        SheetHeader(
            title = stringResource(ResR.string.pixiv_browser_history),
            actionIcon = Icons.Filled.DeleteSweep,
            actionDescription = stringResource(ResR.string.pixiv_browser_clear_history),
            onAction = onClear,
        )
        if (history.isEmpty()) {
            SheetEmpty(text = stringResource(ResR.string.pixiv_browser_empty_history))
        } else {
            val motion = rememberAppMotion()
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(history, key = { it.url + it.visitedAt }) { entry ->
                    Row(
                        modifier = animateItemMotion(motion)
                            .fillMaxWidth()
                            .clickable { onOpen(entry) }
                            .padding(
                                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m)),
                        ) {
                            AppText(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            AppText(
                                text = entry.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserToolboxSheet(
    readerMode: Boolean,
    onDismiss: () -> Unit,
    onShowCookies: () -> Unit,
    onShowSiteInfo: () -> Unit,
    onClearSiteData: () -> Unit,
    onToggleReader: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
        Column(modifier = Modifier.padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_l))) {
            ToolboxRow(
                icon = Icons.Filled.Cookie,
                label = stringResource(ResR.string.pixiv_browser_cookies),
                onClick = onShowCookies,
            )
            ToolboxRow(
                icon = Icons.Filled.Info,
                label = stringResource(ResR.string.pixiv_browser_site_info),
                onClick = onShowSiteInfo,
            )
            ToolboxRow(
                icon = Icons.Filled.DeleteSweep,
                label = stringResource(ResR.string.pixiv_browser_clear_site_data),
                onClick = onClearSiteData,
            )
            ToolboxRow(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                label = stringResource(ResR.string.pixiv_browser_reader_mode),
                onClick = onToggleReader,
                tint = if (readerMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        }
    }
}

@Composable
private fun ToolboxRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint)
        Spacer(modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        AppText(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun SheetHeader(
    title: String,
    actionIcon: ImageVector,
    actionDescription: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        AppIconButton(
            onClick = onAction,
            imageVector = actionIcon,
            contentDescription = actionDescription,
        )
    }
}

@Composable
private fun SheetEmpty(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_l)),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            align = TextAlign.Center,
        )
    }
}

@Composable
private fun SimpleTextDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AppLiquidDialog(
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        confirmLabel = stringResource(ResR.string.pixiv_browser_close),
        onConfirm = onDismiss,
    )
}

// ── 工具函数 ─────────────────────────────────────────────────────────────────

/** 地址栏输入解析：像域名就直接补 https://，否则走必应搜索。 */
private fun resolveBrowserInput(raw: String): String {
    val query = raw.trim()
    if (query.isBlank()) return PIXIV_HOME_URL
    if (query.startsWith("http://") || query.startsWith("https://")) return query
    val domainRegex = Regex("^[\\w-]+(\\.[\\w-]+)+([/?#].*)?$")
    return if (domainRegex.matches(query)) {
        "https://$query"
    } else {
        "https://cn.bing.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
    }
}

/** 夜间模式（反相滤镜，媒体元素二次反相恢复正常色彩）与阅读模式（限宽/大字）注入。 */
private fun buildInjectJs(night: Boolean, reader: Boolean): String {
    val parts = mutableListOf<String>()
    if (night) {
        parts += "html{background:#000!important;filter:invert(1) hue-rotate(180deg)!important;}"
        parts += "img,video,picture,canvas,svg{filter:invert(1) hue-rotate(180deg)!important;}"
    }
    if (reader) {
        parts += "body{max-width:720px!important;margin:0 auto!important;" +
            "font-size:20px!important;line-height:1.8!important;}"
        parts += "img,video,iframe{max-width:100%!important;height:auto!important;}"
    }
    val css = parts.joinToString(separator = "")
    return "(function(){var e=document.getElementById('__pixiv_inject__');" +
        "if(!e){e=document.createElement('style');e.id='__pixiv_inject__';" +
        "document.documentElement.appendChild(e);}e.textContent='$css';})();"
}

/** 尽力清除当前站点的 Cookie（逐条置过期）与 WebView 缓存。 */
private fun clearCurrentSiteData(url: String, webView: WebView?) {
    runCatching {
        val manager = CookieManager.getInstance()
        manager.getCookie(url)
            ?.split(";")
            ?.forEach { pair ->
                val name = pair.substringBefore("=").trim()
                if (name.isNotEmpty()) {
                    manager.setCookie(
                        url,
                        "$name=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/",
                    )
                }
            }
        manager.flush()
        webView?.clearCache(true)
    }
}

/** 用系统 [DownloadManager] 将 webview 的下载请求写入系统下载目录并弹提示。 */
private fun onDownloadRequested(
    context: android.content.Context,
    url: String,
    userAgent: String,
    contentDisposition: String,
    mimetype: String,
    onStarted: (String) -> Unit,
) {
    runCatching {
        val manager = context.getSystemService(DownloadManager::class.java) ?: return@runCatching
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        val request = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mimetype)
            .addRequestHeader("User-Agent", userAgent)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        manager.enqueue(request)
    }.onSuccess {
        onStarted(context.getString(ResR.string.pixiv_browser_download_started))
    }
}

private const val PIXIV_HOME_URL = "https://www.pixiv.net/"

/** 电脑模式 UA（桌面 Chrome / Windows）。 */
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

@Preview(showBackground = true)
@Composable
private fun BrowserMenuGridPreview() {
    MaterialTheme {
        MenuGridItem(
            icon = Icons.Filled.DarkMode,
            label = "夜间模式",
            active = true,
            onClick = {},
        )
    }
}
