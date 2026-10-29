package com.pencil.chuangyin.pro.navigation

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pencil.chuangyin.core.theme.LocalLiquidGlassNavbarEnabled
import com.pencil.chuangyin.core.theme.cornerSafeInset
import com.pencil.chuangyin.core.theme.glassAwareOutline
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassPanelActive
import com.pencil.chuangyin.core.ui.components.rememberNativeGlassIcon
import com.pencil.chuangyin.core.ui.motion.pressScale
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.core.ui.navigation.AppDestination
import com.pencil.chuangyin.core.ui.navigation.AppNavCommand
import com.pencil.chuangyin.core.ui.navigation.AppNavigationBus
import com.pencil.chuangyin.core.ui.navigation.AppPage
import com.pencil.chuangyin.feature.ai.ui.AiChatRoute
import com.pencil.chuangyin.feature.ai.ui.AiConfigRoute
import com.pencil.chuangyin.feature.dashboard.ConnectRoute
import com.pencil.chuangyin.feature.dashboard.HomeRoute
import com.pencil.chuangyin.feature.camera.CameraRoute
import com.pencil.chuangyin.feature.image.ImageEditorRoute
import com.pencil.chuangyin.feature.onboarding.OnboardingRoute
import com.pencil.chuangyin.feature.settings.AiImageSettingsRoute
import com.pencil.chuangyin.feature.settings.LicensesRoute
import com.pencil.chuangyin.feature.settings.LiquidGlassSettingsRoute
import com.pencil.chuangyin.feature.settings.FunctionSettingsRoute
import com.pencil.chuangyin.feature.settings.ProxySettingsRoute
import com.pencil.chuangyin.feature.settings.SettingsRoute
import com.pencil.chuangyin.feature.settings.VisualSettingsRoute
import com.pencil.chuangyin.feature.text.TextEditorRoute
import com.pencil.chuangyin.pro.OnboardingFlagRepository
import com.pencil.chuangyin.pro.ui.CreativeScreen
import com.pencil.chuangyin.feature.pixiv.ui.browse.PixivBrowseRoute
import com.pencil.chuangyin.feature.pixiv.ui.browser.PixivBrowserRoute
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_NOVEL
import com.pencil.chuangyin.feature.pixiv.ui.detail.PixivDetailRoute
import com.pencil.chuangyin.feature.pixiv.ui.drawer.PixivDrawerDestination
import com.pencil.chuangyin.feature.pixiv.ui.login.PixivLoginRoute
import com.pencil.chuangyin.feature.pixiv.ui.novel.PixivNovelListRoute
import com.pencil.chuangyin.feature.pixiv.ui.novel.PixivNovelReaderRoute
import com.pencil.chuangyin.feature.pixiv.ui.novel.PixivNovelReaderViewModel
import com.pencil.chuangyin.feature.pixiv.ui.search.PixivSearchRoute
import com.pencil.chuangyin.feature.pixiv.ui.settings.PixivSettingsRoute
import com.pencil.chuangyin.feature.pixiv.ui.tools.PixivAccountRoute
import com.pencil.chuangyin.feature.pixiv.ui.tools.PixivBlockedTagsRoute
import com.pencil.chuangyin.feature.pixiv.ui.tools.PixivBookmarksRoute
import com.pencil.chuangyin.feature.pixiv.ui.tools.PixivCacheRoute
import com.pencil.chuangyin.feature.pixiv.ui.tools.PixivDownloadsRoute
import com.pencil.chuangyin.feature.pixiv.ui.tools.PixivGalleryRoute
import com.pencil.chuangyin.feature.pixiv.ui.tools.PixivHistoryRoute
import com.pencil.chuangyin.feature.pixiv.ui.tools.PixivSourcesRoute
import com.pencil.chuangyin.feature.pixiv.ui.walkthrough.PixivWalkthroughRoute
import com.pencil.chuangyin.resources.R as ResR
import kotlinx.coroutines.launch
import java.net.URLEncoder
import kotlin.math.roundToInt

/** 路由常量（app 枢纽层唯一事实来源）。 */
object AppRoutes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val CONNECT = "connect"
    const val TEXT = "text"
    const val CREATIVE = "creative"
    const val PSTATION = "pstation"
    const val IMAGE = "image"
    const val CAMERA = "camera"
    const val SETTINGS = "settings"
    const val SETTINGS_VISUAL = "settings_visual"
    const val SETTINGS_LIQUID_GLASS = "settings_liquid_glass"
    const val SETTINGS_SIZE = "settings_function"
    const val SETTINGS_LICENSES = "settings_licenses"
    const val SETTINGS_AI_CONFIG = "settings_ai_config"
    const val SETTINGS_AI_IMAGE = "settings_ai_image"
    const val SETTINGS_PROXY = "settings_proxy"

    // AI 打印（Agent 客户端）
    const val AI = "ai"

    // P 站自研客户端
    const val PIXIV_LOGIN = "pixiv_login"
    const val PIXIV_SEARCH = "pixiv_search?word={word}&mode={mode}"
    fun pixivSearch(word: String? = null, mode: String? = null): String {
        val params = buildList {
            if (!word.isNullOrBlank()) add("word=" + URLEncoder.encode(word, "UTF-8"))
            if (!mode.isNullOrBlank()) add("mode=" + URLEncoder.encode(mode, "UTF-8"))
        }
        return if (params.isEmpty()) "pixiv_search" else "pixiv_search?" + params.joinToString("&")
    }
    const val PIXIV_DETAIL = "pixiv_detail/{illustId}"
    fun pixivDetail(illustId: Int) = "pixiv_detail/$illustId"

    // P 站二期：侧边栏 13 项对应页面
    const val PIXIV_ACCOUNT = "pixiv_account"
    const val PIXIV_WALKTHROUGH = "pixiv_walkthrough"
    const val PIXIV_BROWSER = "pixiv_browser"
    const val PIXIV_NOVELS = "pixiv_novels"
    const val PIXIV_NOVEL_READER = "pixiv_novel/{novelId}"
    fun pixivNovelReader(novelId: Int) = "pixiv_novel/$novelId"
    const val PIXIV_SETTINGS = "pixiv_settings"
    const val PIXIV_SOURCES = "pixiv_sources?imageUrl={imageUrl}"
    fun pixivSources(imageUrl: String? = null): String =
        if (imageUrl.isNullOrBlank()) "pixiv_sources" else "pixiv_sources?imageUrl=$imageUrl"
    const val PIXIV_HISTORY = "pixiv_history"
    const val PIXIV_BOOKMARKS = "pixiv_bookmarks"
    const val PIXIV_DOWNLOADS = "pixiv_downloads"
    const val PIXIV_GALLERY = "pixiv_gallery"
    const val PIXIV_BLOCKED_TAGS = "pixiv_blocked_tags"
    const val PIXIV_CACHE = "pixiv_cache"
}

private enum class TopLevelTab(
    val icon: ImageVector,
    val labelRes: Int,
) {
    Home(Icons.Filled.Home, ResR.string.nav_home),
    Creative(Icons.Filled.AutoAwesome, ResR.string.nav_creative),
    PStation(Icons.Filled.Store, ResR.string.nav_pstation),
}

/**
 * 应用级导航宿主：集中实例化各 feature 的入口 Composable，并把导航行为以普通参数传下去
 * （feature 内部不持有 NavController）。主页/创意/P站三个顶级页承载于同一个
 * [HorizontalPager]：支持左右滑动流畅切页，并与悬浮底部导航栏双向联动；
 * 文本编辑、图片编辑、连接、设置为覆盖式 push 页面。
 *
 * 液态玻璃集成：当 [liquidGlassNavbarEnabled] 为真时隐藏 Compose 悬浮导航栏，改由原生
 * 覆盖层绘制；原生导航栏点击经 [onNavTapHandlerReady] 注册的回调驱动 Pager 切页，
 * Pager 当前页变化经 [onNavIndexChanged] 回传原生层同步高亮；
 * [onNavbarActiveChanged] 上报导航栏是否应激活（仅顶级页）；导航栏图标由本文件在
 * Compose 侧离屏渲染为位图，经 [onNavIconsReady] 下发原生层着色绘制（应用内图标库，
 * 而非库内置图标）。
 */
@Composable
fun AppNavHost(
    onboardingFlagRepository: OnboardingFlagRepository,
    navigation: AppNavigationBus,
    liquidGlassNavbarEnabled: Boolean = false,
    onNavTapHandlerReady: ((Int) -> Unit) -> Unit = {},
    onNavIndexChanged: (Int) -> Unit = {},
    onNavbarActiveChanged: (Boolean) -> Unit = {},
    onNavIconsReady: (List<Bitmap>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val navController: NavHostController = rememberNavController()
    val startDestination = remember(onboardingFlagRepository.hasCompletedOnboarding()) {
        if (onboardingFlagRepository.hasCompletedOnboarding()) {
            AppRoutes.MAIN
        } else {
            AppRoutes.ONBOARDING
        }
    }
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = currentRoute == AppRoutes.MAIN

    // 三个顶级页共享同一个 PagerState：内容区滑动、导航栏点击、导航栏拖动都经由它动画到目标页。
    val pagerState = rememberPagerState(initialPage = 0) { TopLevelTab.entries.size }
    val scope = rememberCoroutineScope()

    // 原生液态玻璃导航栏使用**应用内图标库**（Material Icons），而非库内置的 11 个矢量图标。
    // 这里把三个顶级页图标离屏渲染为「白字 + alpha」位图，下发原生层按主题前景色着色绘制；
    // 位图按 vector/density 记忆，主题换色无需重传。
    if (liquidGlassNavbarEnabled) {
        val navIcons = TopLevelTab.entries.map { rememberNativeGlassIcon(it.icon).asAndroidBitmap() }
        LaunchedEffect(navIcons) { onNavIconsReady(navIcons) }
    }

    // 原生液态玻璃导航栏 → Pager：注册切页回调（点击原生导航项时动画到对应页）。
    LaunchedEffect(pagerState, scope) {
        onNavTapHandlerReady { index ->
            val target = index.coerceIn(0, TopLevelTab.entries.size - 1)
            scope.launch { pagerState.animateScrollToPage(target) }
        }
    }
    // Pager → 原生导航栏：当前页变化回传原生层同步高亮。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page -> onNavIndexChanged(page) }
    }
    // 导航栏仅在顶级页激活。
    LaunchedEffect(showBottomBar) { onNavbarActiveChanged(showBottomBar) }

    // 返回防抖：双击/手抖只处理一次，且只在存在可返回的上一页时 pop，
    // 杜绝“快速连点返回 → 把 MAIN 也弹掉 → 只剩导航栏的空白页”的老 Bug。
    var lastPopMs by remember { mutableLongStateOf(0L) }
    fun safeBack() {
        val now = System.currentTimeMillis()
        if (now - lastPopMs < 400) return
        lastPopMs = now
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    // AI 导航桥：把软件内所有可跳转页面/具名功能登记为具名目的地，供 Agent 的
    // app_navigate / app_list_destinations 工具查询与调用，使 AI 能「替用户点击」进入任意功能。
    LaunchedEffect(Unit) {
        val destinations = listOf(
            AppDestination("pixiv_search", "P 站搜索页（参数 word 关键词、mode=illust|novel）") { args ->
                AppRoutes.pixivSearch(args["word"], args["mode"])
            },
            AppDestination("pixiv_detail", "P 站插画详情页（参数 illust_id）") { args ->
                AppRoutes.pixivDetail(args["illust_id"]?.toIntOrNull() ?: 0)
            },
            AppDestination("pixiv_novel_reader", "P 站小说阅读页（参数 novel_id）") { args ->
                AppRoutes.pixivNovelReader(args["novel_id"]?.toIntOrNull() ?: 0)
            },
            AppDestination("pixiv_novels", "P 站小说列表页") { AppRoutes.PIXIV_NOVELS },
            AppDestination("pixiv_downloads", "P 站下载管理页") { AppRoutes.PIXIV_DOWNLOADS },
            AppDestination("pixiv_bookmarks", "P 站收藏页") { AppRoutes.PIXIV_BOOKMARKS },
            AppDestination("pixiv_history", "P 站浏览历史页") { AppRoutes.PIXIV_HISTORY },
            AppDestination("pixiv_gallery", "P 站本地图库页") { AppRoutes.PIXIV_GALLERY },
            AppDestination("pixiv_login", "P 站登录页") { AppRoutes.PIXIV_LOGIN },
            AppDestination("pixiv_account", "P 站账号页") { AppRoutes.PIXIV_ACCOUNT },
            AppDestination("connect", "打印机连接页") { AppRoutes.CONNECT },
            AppDestination("text_editor", "文本编辑与打印页") { AppRoutes.TEXT },
            AppDestination("image_editor", "图片编辑与打印页") { AppRoutes.IMAGE },
            AppDestination("camera", "相机拍照页") { AppRoutes.CAMERA },
            AppDestination("settings", "应用设置页") { AppRoutes.SETTINGS },
            AppDestination("ai_config", "AI 模型配置页") { AppRoutes.SETTINGS_AI_CONFIG },
            AppDestination("ai_image_settings", "AI 图片处理设置页") { AppRoutes.SETTINGS_AI_IMAGE },
            AppDestination("proxy_settings", "应用内代理设置页") { AppRoutes.SETTINGS_PROXY },
            AppDestination("ai_chat", "AI 助手对话页") { AppRoutes.AI },
        )
        destinations.forEach { navigation.registerDestination(it) }
    }

    // 接收 AI（或任意调用方）经导航桥下发的指令并执行。切到顶级页时，若当前不在 MAIN，
    // 先回到 MAIN 再驱动 Pager 动画到目标页，确保 Pager 已挂载。
    LaunchedEffect(Unit) {
        navigation.commands.collect { command ->
            when (command) {
                is AppNavCommand.OpenPage -> {
                    when (command.page) {
                        AppPage.HOME -> {
                            if (currentRoute != AppRoutes.MAIN) navController.navigate(AppRoutes.MAIN) { launchSingleTop = true }
                            scope.launch { pagerState.animateScrollToPage(0) }
                        }
                        AppPage.CREATIVE -> {
                            if (currentRoute != AppRoutes.MAIN) navController.navigate(AppRoutes.MAIN) { launchSingleTop = true }
                            scope.launch { pagerState.animateScrollToPage(1) }
                        }
                        AppPage.PSTATION -> {
                            if (currentRoute != AppRoutes.MAIN) navController.navigate(AppRoutes.MAIN) { launchSingleTop = true }
                            scope.launch { pagerState.animateScrollToPage(2) }
                        }
                        AppPage.TEXT -> navController.navigate(AppRoutes.TEXT)
                        AppPage.IMAGE -> navController.navigate(AppRoutes.IMAGE)
                        AppPage.CAMERA -> navController.navigate(AppRoutes.CAMERA)
                        AppPage.SETTINGS -> navController.navigate(AppRoutes.SETTINGS)
                        AppPage.AI -> navController.navigate(AppRoutes.AI)
                    }
                }
                is AppNavCommand.Navigate -> navController.navigate(command.route)
                AppNavCommand.Back -> safeBack()
            }
        }
    }

    // 液态玻璃导航栏开启时，底部导航栏改由原生覆盖层（LiquidGlassView）绘制，
    // 此处隐藏 Compose 悬浮导航栏避免重复；内容区底部留白仍由 MainTabPager 保留。
    // 页面转场统一走全局动效体系：二级页从右侧推入、返回时向右推出，进入与返回互为反向；
    // 顶级页（MAIN/ONBOARDING）单独覆写为轻微上浮/下沉 + 淡入淡出。
    // 四个方向全部引用 AppMotion 既有 token，取代 NavHost 默认的「硬切/统一 fade」。
    val motion = rememberAppMotion()
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar && !liquidGlassNavbarEnabled) {
                FloatingBottomBar(pagerState = pagerState)
            }
        },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { motion.pushEnter() },
                exitTransition = { motion.pageExit() },
                popEnterTransition = { motion.pageEnter() },
                popExitTransition = { motion.popExit() },
            ) {
            composable(
                route = AppRoutes.ONBOARDING,
                enterTransition = { motion.pageEnter() },
                exitTransition = { motion.pageExit() },
                popEnterTransition = { motion.pageEnter() },
                popExitTransition = { motion.pageExit() },
            ) {
                OnboardingRoute(
                    onFinished = {
                        onboardingFlagRepository.markCompleted()
                        navController.navigate(AppRoutes.MAIN) {
                            popUpTo(AppRoutes.ONBOARDING) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutes.MAIN,
                enterTransition = { motion.pageEnter() },
                exitTransition = { motion.pageExit() },
                popEnterTransition = { motion.pageEnter() },
                popExitTransition = { motion.pageExit() },
            ) {
                MainTabPager(
                    pagerState = pagerState,
                    onOpenConnect = { navController.navigate(AppRoutes.CONNECT) },
                    onOpenText = { navController.navigate(AppRoutes.TEXT) },
                    onOpenImage = { navController.navigate(AppRoutes.IMAGE) },
                    onOpenCamera = { navController.navigate(AppRoutes.CAMERA) },
                    onOpenAi = { navController.navigate(AppRoutes.AI) },
                    onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) },
                    onPixivLogin = { navController.navigate(AppRoutes.PIXIV_LOGIN) },
                    onPixivSearch = { navController.navigate(AppRoutes.pixivSearch()) },
                    onPixivDetail = { id -> navController.navigate(AppRoutes.pixivDetail(id)) },
                    onPixivNovel = { id -> navController.navigate(AppRoutes.pixivNovelReader(id)) },
                    onPixivDrawer = { destination ->
                        val route = when (destination) {
                            PixivDrawerDestination.ACCOUNT -> AppRoutes.PIXIV_ACCOUNT
                            PixivDrawerDestination.WALKTHROUGH -> AppRoutes.PIXIV_WALKTHROUGH
                            PixivDrawerDestination.BROWSER -> AppRoutes.PIXIV_BROWSER
                            PixivDrawerDestination.SOURCES -> AppRoutes.pixivSources()
                            PixivDrawerDestination.BOOKMARKS -> AppRoutes.PIXIV_BOOKMARKS
                            PixivDrawerDestination.HISTORY -> AppRoutes.PIXIV_HISTORY
                            PixivDrawerDestination.DOWNLOADS -> AppRoutes.PIXIV_DOWNLOADS
                            // 主题样式/应用设置已从侧边栏移除；SETTINGS 为 P 站自己的功能设置页。
                            PixivDrawerDestination.SETTINGS -> AppRoutes.PIXIV_SETTINGS
                            PixivDrawerDestination.GALLERY -> AppRoutes.PIXIV_GALLERY
                            PixivDrawerDestination.BLOCKED_TAGS -> AppRoutes.PIXIV_BLOCKED_TAGS
                            PixivDrawerDestination.CACHE -> AppRoutes.PIXIV_CACHE
                        }
                        navController.navigate(route)
                    },
                )
            }
            composable(AppRoutes.CONNECT) {
                ConnectRoute(onBack = { safeBack() })
            }
            composable(AppRoutes.TEXT) {
                TextEditorRoute(
                    onBack = { safeBack() },
                    onGoConnect = { navController.navigate(AppRoutes.CONNECT) },
                    onOpenPixivGallery = { navController.navigate(AppRoutes.PIXIV_GALLERY) },
                )
            }
            composable(AppRoutes.IMAGE) {
                ImageEditorRoute(
                    onBack = { safeBack() },
                    onGoConnect = { navController.navigate(AppRoutes.CONNECT) },
                    onOpenCamera = { navController.navigate(AppRoutes.CAMERA) },
                    onOpenPixivGallery = { navController.navigate(AppRoutes.PIXIV_GALLERY) },
                )
            }
            composable(AppRoutes.CAMERA) {
                CameraRoute(
                    onClose = { safeBack() },
                    onConfirmed = {
                        // 无论从主页还是图片页进入，都移除相机页后落到图片打印页；
                        // 图片页已在栈中则 launchSingleTop 复用（保留之前已编辑页）。
                        navController.navigate(AppRoutes.IMAGE) {
                            popUpTo(AppRoutes.CAMERA) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(AppRoutes.SETTINGS) {
                SettingsRoute(
                    onBack = { safeBack() },
                    onOpenVisual = { navController.navigate(AppRoutes.SETTINGS_VISUAL) },
                    onOpenLiquidGlass = { navController.navigate(AppRoutes.SETTINGS_LIQUID_GLASS) },
                    onOpenSize = { navController.navigate(AppRoutes.SETTINGS_SIZE) },
                    onOpenLicenses = { navController.navigate(AppRoutes.SETTINGS_LICENSES) },
                    onOpenAiConfig = { navController.navigate(AppRoutes.SETTINGS_AI_CONFIG) },
                    onOpenAiImage = { navController.navigate(AppRoutes.SETTINGS_AI_IMAGE) },
                    onOpenProxy = { navController.navigate(AppRoutes.SETTINGS_PROXY) },
                )
            }
            composable(AppRoutes.SETTINGS_PROXY) {
                ProxySettingsRoute(onBack = { safeBack() })
            }
            composable(AppRoutes.SETTINGS_AI_CONFIG) {
                AiConfigRoute(onBack = { safeBack() })
            }
            composable(AppRoutes.SETTINGS_AI_IMAGE) {
                AiImageSettingsRoute(
                    onBack = { safeBack() },
                    onOpenAiConfig = { navController.navigate(AppRoutes.SETTINGS_AI_CONFIG) },
                )
            }
            composable(AppRoutes.AI) {
                AiChatRoute(
                    onBack = { safeBack() },
                    onOpenConfig = { navController.navigate(AppRoutes.SETTINGS_AI_CONFIG) },
                )
            }
            composable(AppRoutes.SETTINGS_VISUAL) {
                VisualSettingsRoute(onBack = { safeBack() })
            }
            composable(AppRoutes.SETTINGS_LIQUID_GLASS) {
                LiquidGlassSettingsRoute(onBack = { safeBack() })
            }
            composable(AppRoutes.SETTINGS_SIZE) {
                FunctionSettingsRoute(onBack = { safeBack() })
            }
            composable(AppRoutes.SETTINGS_LICENSES) {
                LicensesRoute(onBack = { safeBack() })
            }

            // P 站自研客户端：登录 / 搜索 / 详情。详情打印经 ShotTransferBus 落到图片打印页，
            // 完整复用现有纸宽/浓度/二值化/份数/连接/过热保护，不另起打印管线。
            composable(AppRoutes.PIXIV_LOGIN) {
                PixivLoginRoute(
                    onBack = { safeBack() },
                    onSuccess = { safeBack() },
                )
            }
            composable(
                route = AppRoutes.PIXIV_SEARCH,
                arguments = listOf(
                    navArgument("word") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("mode") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                PixivSearchRoute(
                    onBack = { safeBack() },
                    onOpenDetail = { id -> navController.navigate(AppRoutes.pixivDetail(id)) },
                    onOpenNovel = { id -> navController.navigate(AppRoutes.pixivNovelReader(id)) },
                )
            }
            composable(
                route = AppRoutes.PIXIV_DETAIL,
                arguments = listOf(
                    navArgument(PixivDetailViewModelArg.ILLUST_ID) { type = NavType.IntType },
                ),
            ) {
                PixivDetailRoute(
                    onBack = { safeBack() },
                    onPrinted = {
                        navController.navigate(AppRoutes.IMAGE) { launchSingleTop = true }
                    },
                    onOpenDetail = { id -> navController.navigate(AppRoutes.pixivDetail(id)) },
                    onSourceSearch = { imageUrl ->
                        val encoded = URLEncoder.encode(imageUrl, "UTF-8")
                        navController.navigate(AppRoutes.pixivSources(encoded))
                    },
                    onSearchTag = { tag -> navController.navigate(AppRoutes.pixivSearch(tag)) },
                )
            }

            // ── P 站二期：侧边栏功能页 ──────────────────────────────────────────
            composable(AppRoutes.PIXIV_ACCOUNT) {
                PixivAccountRoute(
                    onBack = { safeBack() },
                    onLogin = { navController.navigate(AppRoutes.PIXIV_LOGIN) },
                )
            }
            composable(AppRoutes.PIXIV_WALKTHROUGH) {
                PixivWalkthroughRoute(
                    onBack = { safeBack() },
                    onOpenDetail = { id -> navController.navigate(AppRoutes.pixivDetail(id)) },
                )
            }
            composable(AppRoutes.PIXIV_BROWSER) {
                PixivBrowserRoute(
                    onBack = { safeBack() },
                    onOpenSettings = { navController.navigate(AppRoutes.PIXIV_SETTINGS) },
                )
            }
            composable(AppRoutes.PIXIV_NOVELS) {
                PixivNovelListRoute(
                    onBack = { safeBack() },
                    onOpenNovel = { id -> navController.navigate(AppRoutes.pixivNovelReader(id)) },
                )
            }
            // P 站功能设置（原侧边栏「应用设置/主题设置」已移除，改由本站独立设置承载）。
            composable(AppRoutes.PIXIV_SETTINGS) {
                PixivSettingsRoute(
                    onBack = { safeBack() },
                    onOpenBlockedTags = { navController.navigate(AppRoutes.PIXIV_BLOCKED_TAGS) },
                )
            }
            composable(
                route = AppRoutes.PIXIV_NOVEL_READER,
                arguments = listOf(
                    navArgument(PixivNovelReaderViewModel.NAV_ARG_NOVEL_ID) {
                        type = NavType.IntType
                    },
                ),
            ) {
                PixivNovelReaderRoute(
                    onBack = { safeBack() },
                    onPrint = {
                        // 小说标题+正文经 TextTransferBus 传给文本打印页，复用完整文本打印管线。
                        navController.navigate(AppRoutes.TEXT) { launchSingleTop = true }
                    },
                    onSearchTag = { tag -> navController.navigate(AppRoutes.pixivSearch(tag, MEDIA_NOVEL)) },
                )
            }
            composable(
                route = AppRoutes.PIXIV_SOURCES,
                arguments = listOf(
                    navArgument("imageUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                PixivSourcesRoute(
                    onBack = { safeBack() },
                    imageUrl = backStackEntry.arguments?.getString("imageUrl"),
                )
            }
            composable(AppRoutes.PIXIV_HISTORY) {
                PixivHistoryRoute(
                    onBack = { safeBack() },
                    onOpenDetail = { id -> navController.navigate(AppRoutes.pixivDetail(id)) },
                )
            }
            composable(AppRoutes.PIXIV_BOOKMARKS) {
                PixivBookmarksRoute(
                    onBack = { safeBack() },
                    onOpenDetail = { id -> navController.navigate(AppRoutes.pixivDetail(id)) },
                    onOpenNovel = { id -> navController.navigate(AppRoutes.pixivNovelReader(id)) },
                )
            }
            composable(AppRoutes.PIXIV_DOWNLOADS) {
                PixivDownloadsRoute(onBack = { safeBack() })
            }
            composable(AppRoutes.PIXIV_GALLERY) {
                PixivGalleryRoute(
                    onBack = { safeBack() },
                    // 已保存内容多选打印：直接落到既有打印页，复用同一套打印设置与流程。
                    onOpenPrintImages = {
                        navController.navigate(AppRoutes.IMAGE) { launchSingleTop = true }
                    },
                    onOpenPrintText = {
                        navController.navigate(AppRoutes.TEXT) { launchSingleTop = true }
                    },
                )
            }
            composable(AppRoutes.PIXIV_BLOCKED_TAGS) {
                PixivBlockedTagsRoute(onBack = { safeBack() })
            }
            composable(AppRoutes.PIXIV_CACHE) {
                PixivCacheRoute(onBack = { safeBack() })
            }
            }
        }
    }
}

/** 与 PixivDetailViewModel 的 SavedStateHandle 键保持一致（feature 模块不依赖 app 路由常量）。 */
private object PixivDetailViewModelArg {
    const val ILLUST_ID = "illustId"
}

/**
 * 顶级页滑动容器：HorizontalPager 自带跟手滑动 + settle 动画（页面水平跟手位移、
 * 松手回弹/吸附），[beyondViewportPageCount] = 1 让三个页面全部保活，切回不丢状态。
 */
@Composable
private fun MainTabPager(
    pagerState: PagerState,
    onOpenConnect: () -> Unit,
    onOpenText: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenSettings: () -> Unit,
    onPixivLogin: () -> Unit,
    onPixivSearch: () -> Unit,
    onPixivDetail: (Int) -> Unit,
    onPixivNovel: (Int) -> Unit,
    onPixivDrawer: (PixivDrawerDestination) -> Unit,
) {
    // 导航栏占据的底部总高度（系统导航栏 inset + 悬浮栏高度 + 底部间隙），
    // 传给各页滚动内容做底部留白；页面背景仍延伸到导航栏下方供毛玻璃取样。
    val density = LocalDensity.current
    val navigationBarsBottom = with(density) {
        WindowInsets.navigationBars.getBottom(density).toDp()
    }
    val bottomBarSpace = navigationBarsBottom +
        dimensionResource(ResR.dimen.dimen_floating_nav_height) +
        dimensionResource(ResR.dimen.dimen_floating_nav_bottom_gap)
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { it },
    ) { page ->
        when (TopLevelTab.entries[page]) {
            TopLevelTab.Home -> HomeRoute(
                onOpenConnect = onOpenConnect,
                onOpenText = onOpenText,
                onOpenImage = onOpenImage,
                onOpenCamera = onOpenCamera,
                onOpenAi = onOpenAi,
                onOpenSettings = onOpenSettings,
                bottomBarSpace = bottomBarSpace,
            )
            TopLevelTab.Creative -> CreativeScreen(
                onOpenSettings = onOpenSettings,
                bottomBarSpace = bottomBarSpace,
            )
            TopLevelTab.PStation -> PixivBrowseRoute(
                onLogin = onPixivLogin,
                onOpenSearch = onPixivSearch,
                onOpenDetail = onPixivDetail,
                onOpenNovel = onPixivNovel,
                onOpenDrawerDestination = onPixivDrawer,
                bottomBarSpace = bottomBarSpace,
            )
        }
    }
}

/**
 * 悬浮底部导航栏：跟随主题 shapes.large 的悬浮圆角栏（外边距 + 阴影浮于内容之上），
 * 选中项胶囊背景/图标放大/颜色变化均带补间动画，与 Pager 滑动联动。
 *
 * 交互：点击切换（吸附动画）+ **在导航栏上按下并水平拖动**直接驱动 Pager 跟手位移，
 * 松手按当前偏移吸附到最近页（与在内容区滑动手感一致）。
 */
@Composable
private fun FloatingBottomBar(
    pagerState: PagerState,
) {
    val scope = rememberCoroutineScope()
    // 形状跟随主题 Shapes 的 large 档（跟随设置页的边角风格/强度）：圆角半径按短边一半
    // 自动夹取，72dp 高的导航栏呈现稳定的大圆角「悬浮条」观感——这是既定设计，不能改成
    // 方形/小方角。（此前「背景显示不完整」的真正根因是关闭玻璃时原生覆盖层残留最后一帧，
    // 与 Compose 导航栏双层叠加，已在 LiquidGlassView.setGlassEnabled 修复，与形状无关。）
    val barShape = MaterialTheme.shapes.large
    // 导航栏底板玻璃**仅受「液态玻璃导航栏」开关控制**，与「液态玻璃控件」开关独立：
    // - 开启导航栏开关：底板走原生玻璃面（折射其下页面内容），图标/选中胶囊仍为 Compose 绘制；
    // - 关闭导航栏开关：底板回退 surfaceContainer 实底（无论控件开关是否开启）。
    // 这避免了「开控件没开导航栏，导航栏却玻璃化」的越界行为。
    val navbarGlassActive = LocalLiquidGlassNavbarEnabled.current && rememberLiquidGlassPanelActive()
    val contentPaddingH = dimensionResource(ResR.dimen.dimen_floating_nav_content_padding_h) +
        cornerSafeInset(shapeScale = 1.5f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_floating_nav_horizontal),
            )
            .navigationBarsPadding()
            .padding(bottom = dimensionResource(ResR.dimen.dimen_floating_nav_bottom_gap)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 悬浮阴影自绘（M3 Surface 的 tonalElevation 会派生不透明实色，使主题透明度
                // 失效）。轮廓只由一次 clip(barShape) 决定，玻璃底板/回退实底均与轮廓重合。
                .shadow(
                    elevation = dimensionResource(ResR.dimen.dimen_floating_nav_shadow_elevation),
                    shape = barShape,
                    clip = false,
                )
                .clip(barShape)
                // 玻璃开启：原生玻璃面承载底板（此处 Compose 透明，不铺实底）；
                // 玻璃关闭/不可用：回退 surfaceContainer，保证仍有可见底板。
                .nativeGlassSurface(
                    shape = barShape,
                    enabled = navbarGlassActive,
                    fallbackColor = MaterialTheme.colorScheme.surfaceContainer,
                )
                // 玻璃生效时描边交给原生边缘高光，Compose 描边不进快照（避免假边框/重影）。
                .glassAwareOutline(barShape, navbarGlassActive)
                // 按下并水平拖动（自控手势）：越过触摸斜率才进入拖动态——拖动中逐事件消费并
                // dispatchRawDelta 跟手平移页面；未越斜率即抬起视为点击（交由子级 clickable）；
                // 松手按当前偏移 animateScrollToPage 吸附最近页。
                .pointerInput(pagerState) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var overSlop = 0f
                        val slopChange = awaitHorizontalTouchSlopOrCancellation(down.id) { _, over ->
                            overSlop += over
                        }
                        // null = 未越斜率即抬起（点击）或手势被打断，不处理。
                        if (slopChange == null) return@awaitEachGesture
                        if (overSlop != 0f) pagerState.dispatchRawDelta(-overSlop)
                        while (true) {
                            val change = awaitHorizontalDragOrCancellation(down.id) ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            val drag = change.positionChange().x
                            if (drag != 0f) {
                                change.consume()
                                pagerState.dispatchRawDelta(-drag)
                            }
                        }
                        scope.launch {
                            val target = (
                                pagerState.currentPage +
                                    pagerState.currentPageOffsetFraction.roundToInt()
                                ).coerceIn(0, pagerState.pageCount - 1)
                            pagerState.animateScrollToPage(target)
                        }
                    }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(ResR.dimen.dimen_floating_nav_height))
                    .padding(horizontal = contentPaddingH),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelTab.entries.forEachIndexed { index, tab ->
                    FloatingNavItem(
                        icon = tab.icon,
                        label = stringResource(tab.labelRes),
                        // 拖动过程中 targetPage 随偏移实时变化，指示胶囊随之滑动切换。
                        selected = pagerState.targetPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = rememberAppMotion()
    val interactionSource = remember { MutableInteractionSource() }
    // MD3 的 Spatial / Effect 二分：
    // - 图标/标签颜色、胶囊底色属于 Effect，必须零过冲（过冲会让颜色闪烁）；
    // - 胶囊与图标的缩放属于 Spatial，用带过冲的 spring 制造"物理回应"。
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motion.effectSpec(),
        label = "floatingNavContentColor",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = motion.effectSpec(),
        label = "floatingNavIndicatorColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) NAV_ICON_SELECTED_SCALE else 1f,
        animationSpec = motion.bouncySpec(),
        label = "floatingNavIconScale",
    )
    // 选中胶囊「弹」出来（0.72 → 1）而不是原地换色，切换 Tab 时多一层形状层面的反馈。
    val indicatorScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.72f,
        animationSpec = motion.bouncySpec(),
        label = "floatingNavIndicatorScale",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            // 按压反馈：按下微缩、松开弹性回弹（Effect 主导的触感补强）。
            .pressScale(interactionSource, enabled = true)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(
                vertical = dimensionResource(ResR.dimen.dimen_floating_nav_item_padding_v),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = indicatorScale
                    scaleY = indicatorScale
                }
                .clip(CircleShape)
                .background(indicatorColor)
                .padding(
                    horizontal = dimensionResource(ResR.dimen.dimen_floating_nav_indicator_padding_h),
                    vertical = dimensionResource(ResR.dimen.dimen_floating_nav_indicator_padding_v),
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier
                    .size(dimensionResource(ResR.dimen.dimen_floating_nav_icon_size))
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** 选中时图标放大幅度（配合 Spatial spring，过冲后自然回落）。 */
private const val NAV_ICON_SELECTED_SCALE = 1.12f
