package com.pencil.chuangyin.desktop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.glass.glassAwareOutline
import com.pencil.chuangyin.desktop.glass.glassSurface
import com.pencil.chuangyin.desktop.navigation.DesktopNavigator
import com.pencil.chuangyin.desktop.theme.DesktopThemeStore
import com.pencil.chuangyin.desktop.theme.LocalLiquidGlassNavbarEnabled
import com.pencil.chuangyin.desktop.theme.ThemeState
import com.pencil.chuangyin.desktop.theme.cornerSafeInset
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 应用外壳 —— 与安卓端 app/navigation/AppNavHost.kt 的顶级结构 1:1 对齐：
 *   HorizontalPager(beyondViewportPageCount = 1)  三个顶级 tab：首页 / 创作 / P站
 *   └ 悬浮胶囊玻璃底栏（FloatingBottomBar，可点击切换，也可按下并水平拖动驱动 Pager）
 *
 * **启动页是「首页」**（对齐安卓 startDestination = MAIN）：
 * 启动不做任何登录拦截，P 站登录墙只出现在 P 站 tab 内部（PixivBrowseScreen 的 NotLoggedIn 态）。
 * 桌面窗口没有系统导航栏，因此 bottomBarSpace = 底栏高度 + 底部间隙（安卓还要加 navigationBars inset）。
 */
@Composable
fun AppShell(themeState: ThemeState = DesktopThemeStore.state.collectAsState().value) {
    val pagerState = rememberPagerState(
        pageCount = { TopLevelTab.entries.size },
    )
    var settingsOpen by remember { mutableStateOf(false) }
    var textPrintOpen by remember { mutableStateOf(false) }
    var imagePrintOpen by remember { mutableStateOf(false) }
    var aiChatOpen by remember { mutableStateOf(false) }
    var aiConfigOpen by remember { mutableStateOf(false) }
    var connectOpen by remember { mutableStateOf(false) }
    val bottomBarSpace: Dp = FLOATING_NAV_HEIGHT + FLOATING_NAV_BOTTOM_GAP
    val scope = rememberCoroutineScope()

    // AI 工具可以通过 DesktopNavigator 请求切页（对齐安卓端 AppNavigationBus）。
    // 回调可能来自后台线程（工具协程），因此统一切回主线程再改状态。
    DisposableEffect(pagerState) {
        DesktopNavigator.register { page ->
            scope.launch {
                when (page) {
                    DesktopNavigator.Page.Home,
                    DesktopNavigator.Page.Creative,
                    DesktopNavigator.Page.PStation -> {
                        val index = when (page) {
                            DesktopNavigator.Page.Home -> TopLevelTab.Home.ordinal
                            DesktopNavigator.Page.Creative -> TopLevelTab.Creative.ordinal
                            else -> TopLevelTab.PStation.ordinal
                        }
                        settingsOpen = false
                        textPrintOpen = false
                        imagePrintOpen = false
                        aiChatOpen = false
                        connectOpen = false
                        pagerState.animateScrollToPage(index)
                    }

                    DesktopNavigator.Page.Text -> textPrintOpen = true
                    DesktopNavigator.Page.Image -> imagePrintOpen = true
                    DesktopNavigator.Page.Ai -> aiChatOpen = true
                    DesktopNavigator.Page.Connect -> connectOpen = true
                    DesktopNavigator.Page.Settings -> settingsOpen = true
                }
            }
            true
        }
        onDispose { DesktopNavigator.register(null) }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            when (TopLevelTab.entries[page]) {
                TopLevelTab.Home -> HomeScreen(
                    themeState = themeState,
                    bottomBarSpace = bottomBarSpace,
                    onOpenSettings = { settingsOpen = true },
                    onOpenTextPrint = { textPrintOpen = true },
                    onOpenImagePrint = { imagePrintOpen = true },
                    onOpenAiChat = { aiChatOpen = true },
                    onOpenConnect = { connectOpen = true },
                )

                TopLevelTab.Creative -> CreativeScreen(
                    bottomBarSpace = bottomBarSpace,
                    onOpenSettings = { settingsOpen = true },
                )

                TopLevelTab.PStation -> PixivBrowseScreen(
                    bottomBarSpace = bottomBarSpace,
                )
            }
        }

        FloatingBottomBar(
            pagerState = pagerState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 设置以整窗覆盖层呈现（对齐安卓 navController 的 SETTINGS 目的地，返回即退出）。
        if (settingsOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                SettingsScreen(onBack = { settingsOpen = false })
            }
        }

        // 文本打印编辑器同样以整窗覆盖层呈现（对齐安卓 TEXT_EDITOR 目的地）。
        if (textPrintOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TextPrintScreen(onBack = { textPrintOpen = false })
            }
        }

        // 图片打印编辑器同样以整窗覆盖层呈现（对齐安卓 IMAGE_EDITOR 目的地）。
        if (imagePrintOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                ImagePrintScreen(onBack = { imagePrintOpen = false })
            }
        }

        // AI 打印对话同样以整窗覆盖层呈现（对齐安卓 AI_CHAT 目的地）。
        if (aiChatOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AiChatPage(
                    onBack = { aiChatOpen = false },
                    onOpenConfig = { aiConfigOpen = true },
                )
            }
        }

        // AI 模型配置（对齐安卓 SETTINGS → AI 配置页面，可独立覆盖在对话之上）。
        if (aiConfigOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AiConfigPage(onBack = { aiConfigOpen = false })
            }
        }

        // 连接打印机（对齐安卓 CONNECT 目的地）：从首页状态胶囊、文本/图片编辑器底栏叠开，
        // 关闭后仍回到原页面，因此渲染在所有编辑页之后。
        if (connectOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                ConnectScreen(onBack = { connectOpen = false })
            }
        }
    }
}

/** 顶级 tab（对齐安卓端 TopLevelTab：Home / Creative / PStation，文案取 nav_home / nav_creative / nav_pstation）。 */
private enum class TopLevelTab(val icon: AppIcon, val label: String) {
    Home(AppIcon.Home, "主页"),
    Creative(AppIcon.Creative, "创意"),
    PStation(AppIcon.Store, "P站"),
}

// 尺寸对齐安卓端 dimens.xml 的 dimen_floating_nav_* 系列。
// 对同级页面（P 站二级页）可见：它们需要留出同样的底部空间，避免内容被悬浮底栏遮挡。
internal val FLOATING_NAV_HEIGHT = 72.dp
internal val FLOATING_NAV_BOTTOM_GAP = 12.dp
private val FLOATING_NAV_HORIZONTAL = 16.dp
private val FLOATING_NAV_SHADOW_ELEVATION = 8.dp
private val FLOATING_NAV_CONTENT_PADDING_H = 8.dp
private val FLOATING_NAV_ITEM_PADDING_V = 6.dp
private val FLOATING_NAV_INDICATOR_PADDING_H = 18.dp
private val FLOATING_NAV_INDICATOR_PADDING_V = 6.dp
private val FLOATING_NAV_ICON_SIZE = 24.dp

/** 导航项颜色补间时长（毫秒），与安卓端一致。 */
private const val NAV_ITEM_ANIMATION_MS = 250

/** 选中时图标放大幅度，与安卓端一致。 */
private const val NAV_ICON_SELECTED_SCALE = 1.12f

/**
 * 悬浮底部导航栏：形状跟随主题 shapes.large；底板玻璃仅受「液态玻璃导航栏」开关控制
 * （关闭时回退 surfaceContainer 实底，与安卓端语义一致）。
 */
@Composable
private fun FloatingBottomBar(pagerState: PagerState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val barShape = MaterialTheme.shapes.large
    val navbarGlassActive = LocalLiquidGlassNavbarEnabled.current
    val contentPaddingH = FLOATING_NAV_CONTENT_PADDING_H + cornerSafeInset(
        style = com.pencil.chuangyin.desktop.theme.LocalCornerStyle.current,
        strength = com.pencil.chuangyin.desktop.theme.LocalCornerStrength.current,
        shapeScale = 1.5f,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FLOATING_NAV_HORIZONTAL)
            .padding(bottom = FLOATING_NAV_BOTTOM_GAP),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = FLOATING_NAV_SHADOW_ELEVATION,
                    shape = barShape,
                    clip = false,
                )
                .clip(barShape)
                .glassSurface(
                    shape = barShape,
                    enabled = navbarGlassActive,
                    fallbackColor = MaterialTheme.colorScheme.surfaceContainer,
                )
                .glassAwareOutline(barShape, navbarGlassActive)
                // 按下并水平拖动 → 直接驱动 Pager 跟手位移；未越斜率即抬起视为点击。
                .pointerInput(pagerState) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var overSlop = 0f
                        val slopChange = awaitHorizontalTouchSlopOrCancellation(down.id) { _, over ->
                            overSlop += over
                        }
                        if (slopChange == null) return@awaitEachGesture
                        if (overSlop != 0f) pagerState.dispatchRawDelta(-overSlop)
                        while (true) {
                            val change = awaitHorizontalDragOrCancellation(down.id) ?: break
                            if (!change.pressed) break
                            val drag = change.position.x - change.previousPosition.x
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
                    .height(FLOATING_NAV_HEIGHT)
                    .padding(horizontal = contentPaddingH),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelTab.entries.forEachIndexed { index, tab ->
                    FloatingNavItem(
                        icon = tab.icon,
                        label = tab.label,
                        selected = pagerState.targetPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    icon: AppIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(NAV_ITEM_ANIMATION_MS),
        label = "floatingNavContentColor",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(NAV_ITEM_ANIMATION_MS),
        label = "floatingNavIndicatorColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) NAV_ICON_SELECTED_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "floatingNavIconScale",
    )

    // 桌面端鼠标驻留会一直绘制 hover state layer，形成第二个「胶囊选中态」，与安卓观感不符，
    // 因此这里关闭 indication，仅保留选中态指示器。
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = FLOATING_NAV_ITEM_PADDING_V),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(indicatorColor)
                .padding(
                    horizontal = FLOATING_NAV_INDICATOR_PADDING_H,
                    vertical = FLOATING_NAV_INDICATOR_PADDING_V,
                ),
        ) {
            AppIconView(
                icon = icon,
                size = FLOATING_NAV_ICON_SIZE,
                tint = contentColor,
                modifier = Modifier
                    .size(FLOATING_NAV_ICON_SIZE)
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
