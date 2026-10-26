package com.pencil.chuangyin.pro

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.theme.AppTheme
import com.pencil.chuangyin.core.theme.ColorSchemeProvider
import com.pencil.chuangyin.core.theme.LiquidGlassRenderer
import com.pencil.chuangyin.core.theme.ShapeResolver
import com.pencil.chuangyin.core.theme.ThemeRepository
import com.pencil.chuangyin.core.theme.ThemeState
import com.pencil.chuangyin.core.theme.toLiquidGlassStyle
import com.pencil.chuangyin.core.ui.components.AppDialogHost
import com.pencil.chuangyin.core.ui.components.AppDialogHostState
import com.pencil.chuangyin.core.ui.components.LocalAppDialogHost
import com.pencil.chuangyin.core.ui.components.ParticleFallOverlay
import com.pencil.chuangyin.core.ui.navigation.AppNavigationBus
import com.pencil.chuangyin.pro.liquid.LiquidGlassControllerImpl
import com.pencil.chuangyin.pro.liquid.LiquidGlassView
import com.pencil.chuangyin.pro.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 容器玻璃面的模糊半径（dp）：0 = 不模糊，压在玻璃下的 Compose 文字保持锐利。 */
private const val PANEL_BLUR_DP = 0f

/** 容器玻璃面的采样外扩（dp）：尽量取到面板四周的屏幕邻域，取不到时再由兜底色补齐。 */
private const val PANEL_PAD_DP = 24f

/** 容器玻璃面在采样缺失处的兜底不透明度：真实玻璃并非全透明。 */
private const val PANEL_FILL_ALPHA = 0.96f

/**
 * 宿主 Activity：注入主题相关依赖 + 引导标记仓库，订阅 [ThemeRepository.state] 驱动换肤。
 * 主题切换走 StateFlow 重组，禁 recreate()。
 *
 * 液态玻璃覆盖层：原生 [LiquidGlassView]（GLSurfaceView + LiquidGlass-Cpp）以透明图层浮于
 * Compose 之上，承接底部导航栏与已注册的 App 控件（按钮/开关/滑条/分段行）。Compose 侧只放
 * 透明占位盒并上报物理像素位置，绘制与触摸由 C++ 原生层负责，事件经 JNI 回传后分发回调。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeRepository: ThemeRepository
    @Inject lateinit var colorSchemeProvider: ColorSchemeProvider
    @Inject lateinit var shapeResolver: ShapeResolver
    @Inject lateinit var onboardingFlagRepository: OnboardingFlagRepository
    @Inject lateinit var navigationBus: AppNavigationBus

    private var glassView: LiquidGlassView? = null
    private var glassController: LiquidGlassControllerImpl? = null
    private var navTapHandler: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 原生库不可用时（不支持的 ABI）不创建覆盖层，主界面按纯 Compose 运行。
        val glass = if (LiquidGlassView.nativeAvailable) LiquidGlassView(this) else null
        glassView = glass
        val controller = glass?.let { LiquidGlassControllerImpl(it) }
        glassController = controller
        glass?.onNavTap = { index -> navTapHandler?.invoke(index) }

        setContent {
            val themeState by themeRepository.state.collectAsStateWithLifecycle(initialValue = ThemeState.Default)
            // 导航栏仅在顶级页（MAIN）激活，避免在 push 页遮挡底部内容与触摸。
            var navbarActive by remember { mutableStateOf(false) }
            // 全局弹窗宿主状态：在 AppTheme 之外创建，供主题内容（注入）与弹窗可见性效应（下方）共用。
            val dialogHost = remember { AppDialogHostState() }
            AppTheme(
                themeState = themeState,
                colorSchemeProvider = colorSchemeProvider,
                shapeResolver = shapeResolver,
                // 仅 C++ 实现注入原生宿主；Kotlin 实现下控件回退 M3，避免与原生覆盖层叠加绘制。
                liquidGlassWidgetHost = controller
                    .takeIf { themeState.liquidGlassRenderer == LiquidGlassRenderer.Cpp },
                liquidGlassCppAvailable = LiquidGlassView.nativeAvailable,
            ) {
                // 下发 App 真实主题色：accent=primary（控件激活色），surface=玻璃基调（决定亮暗判定），
                // icon=onSurface（图标前景色，亮色模式深色 / 暗色模式浅色，避免亮色模式白图标）。
                val glassColorScheme = MaterialTheme.colorScheme
                LaunchedEffect(
                    glassColorScheme.primary,
                    glassColorScheme.surface,
                    glassColorScheme.onSurface,
                ) {
                    glass?.setThemeColors(
                        glassColorScheme.primary.toArgb(),
                        glassColorScheme.surface.toArgb(),
                        glassColorScheme.onSurface.toArgb(),
                    )
                    // 主题色变化时玻璃面的色调/折射背景随之改变，必须立即重采一次 backdrop，
                    // 否则原生层仍按上一帧的颜色采样，表现为「玻璃卡在旧颜色，需刷新才正常」。
                    glass?.refreshBackdropNow()
                }
                CompositionLocalProvider(LocalAppDialogHost provides dialogHost) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavHost(
                            onboardingFlagRepository = onboardingFlagRepository,
                            navigation = navigationBus,
                            liquidGlassNavbarEnabled = themeState.liquidGlassNavbarEnabled,
                            onNavTapHandlerReady = { handler -> navTapHandler = handler },
                            onNavIndexChanged = { index -> glass?.setNavIndex(index) },
                            onNavbarActiveChanged = { active -> navbarActive = active },
                            // 应用内图标（首页/创作/P站）离屏渲染为位图后下发原生导航栏。
                            onNavIconsReady = { icons ->
                                icons.forEachIndexed { index, icon -> glass?.setNavIconBitmap(index, icon) }
                            },
                        )
                        // 全页面粒子飘落效果：默认关闭，设置开启后覆盖在全部内容之上，类型跟随设置。
                        if (themeState.snowfallEnabled) {
                            ParticleFallOverlay(
                                type = themeState.particleType,
                                speedMultiplier = themeState.particleSpeed,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        // 全局弹窗宿主：挂载一次即让所有 AppAlertDialog 提升到根节点、以真 C++ 玻璃渲染，
                        // 且始终位于页面内容之上（在原生玻璃覆盖层之下，故其内容会被 backdrop 采集）。
                        AppDialogHost(state = dialogHost, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            LaunchedEffect(
                themeState.liquidGlassControlsEnabled,
                themeState.liquidGlassNavbarEnabled,
                themeState.darkTheme,
                themeState.liquidGlassRenderer,
                navbarActive,
            ) {
                val cppActive = themeState.liquidGlassRenderer == LiquidGlassRenderer.Cpp
                val controlsOn = cppActive && themeState.liquidGlassControlsEnabled
                val navbarOn = cppActive && themeState.liquidGlassNavbarEnabled && navbarActive
                controller?.setEnabled(controlsOn, navbarOn)
                glass?.setLightMode(!themeState.darkTheme)
                // 呼吸/高光动画仅在玻璃实际可见时持续出帧，避免无谓的耗电与后台渲染。
                glass?.setAnimate(controlsOn || navbarOn)
            }
            // 详细参数（模糊/透镜/光谱/色调/阴影/玻璃/轮廓）实时下发原生层。
            LaunchedEffect(
                themeState.liquidGlassBlurDp,
                themeState.liquidGlassLensHeightDp,
                themeState.liquidGlassLensAmountDp,
                themeState.liquidGlassSpectral,
                themeState.liquidGlassTintAlpha,
                themeState.liquidGlassShadowOn,
                themeState.liquidGlassGlassOn,
                themeState.liquidGlassContourEdge,
            ) {
                glass?.setStyle(themeState.toLiquidGlassStyle())
            }
            // 容器玻璃面（Panel）参数：容器盖在实时 Compose 内容之上，模糊只会让压在
            // 下面的文字变糊，因此面板默认不模糊；主体保持透明（PANEL_SHEET_ALPHA_SCALE），
            // 玻璃质感由边缘高光 + 透镜 + 投影承担。
            LaunchedEffect(themeState.liquidGlassGlassOn) {
                glass?.setPanelStyle(
                    blurDp = PANEL_BLUR_DP,
                    padDp = PANEL_PAD_DP,
                    fillArgb = if (themeState.liquidGlassGlassOn) PANEL_FILL_ALPHA else 1f,
                )
            }
            // 弹窗开关：打开时让原生覆盖层停止消费触摸（否则点到底部导航栏等原生控件会直接命中，
            // Compose 侧遮罩拦不到原生层），并立即重采 backdrop，使玻璃卡片第一时间映射到弹窗内容，
            // 而不是停留在上一帧的页面（否则卡片内容会短暂空白/错位）。
            // 同时放行「弹窗容器玻璃面」：弹窗常常在控件玻璃**尚未开启**时出现（如开启前的多级警告
            // 弹窗），若原生层此时停绘，弹窗就只剩 Compose 实心底，即「伪液态玻璃弹窗」。
            LaunchedEffect(dialogHost.hasVisibleDialog) {
                glass?.setModalTouchBlocked(dialogHost.hasVisibleDialog)
                glass?.setGlassPanelsAlwaysOn(dialogHost.hasVisibleDialog)
                glass?.refreshBackdropNow()
            }
        }

        if (glass != null) {
            addContentView(
                glass,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        glassView?.onResume()
    }

    override fun onPause() {
        glassView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        glassView?.release()
        glassView = null
        glassController = null
        navTapHandler = null
        super.onDestroy()
    }
}
