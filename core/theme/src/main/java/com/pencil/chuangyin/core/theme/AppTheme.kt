package com.pencil.chuangyin.core.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 应用级主题入口：由宿主（MainActivity）注入 [ColorSchemeProvider]/[ShapeResolver] 及当前 [ThemeState]，
 * 将主题状态经 StateFlow 以纯参数方式传入本 Composable，完成“状态驱动换肤”而不触发任何 Activity 重启。
 *
 * 自定义背景全局方案（单点接入，页面零适配）：
 * 1. 最底层 Box 先铺主题背景色，再按 [ThemeState.backgroundAlpha] 绘制用户背景图（居中裁剪）；
 * 2. 下发给内容的 colorScheme 把 `background` 改为透明——M3 Scaffold 默认容器色即 background，
 *    于是所有页面背景自动透出底层壁纸，卡片/顶栏等 surface 系颜色保持不透明不受影响。
 *
 * 说明：基于当前可构建的 material3 stable (1.3.1) 使用标准 [MaterialTheme]（含 lightColorScheme /
 * darkColorScheme / shapes）。上游选择的 material3 1.4.0-alpha15 Expressive MotionScheme
 * 因其需 compileSdk 37 / Compose 1.12 而暂缓，升级工具链后可将此处改回 MaterialExpressiveTheme + MotionScheme。
 */
@Composable
fun AppTheme(
    themeState: ThemeState,
    colorSchemeProvider: ColorSchemeProvider,
    shapeResolver: ShapeResolver,
    liquidGlassWidgetHost: LiquidGlassWidgetHost? = null,
    liquidGlassCppAvailable: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // MaterialKolor 生成整套 M3 色板是纯 CPU 重活（HCT 量化 + 调色板推导，几十毫秒级别）。
    // 这里必须按「种子色 + 明暗」记忆：否则每次 AppTheme 重组（导航切换、弹窗开关等）都会重算一遍，
    // 表现为启动首帧与页面切换时的明显卡顿。
    val baseScheme = remember(themeState.seedArgb, themeState.darkTheme) {
        colorSchemeProvider.colorScheme(themeState.seedArgb, themeState.darkTheme)
    }
    // 控件不透明度：surface 系颜色整体降透明度，让页面透出底层壁纸；
    // 未设背景图时仅透出底色（同色系，无视觉副作用），状态仍持久化。
    val surfaceAlpha = themeState.surfaceAlpha.coerceIn(SURFACE_ALPHA_MIN, 1f)
    val effectiveScheme = remember(baseScheme, surfaceAlpha) {
        if (surfaceAlpha < 1f) {
            baseScheme.copy(
                surface = baseScheme.surface.copy(alpha = surfaceAlpha),
                surfaceVariant = baseScheme.surfaceVariant.copy(alpha = surfaceAlpha),
                surfaceContainerLowest = baseScheme.surfaceContainerLowest.copy(alpha = surfaceAlpha),
                surfaceContainerLow = baseScheme.surfaceContainerLow.copy(alpha = surfaceAlpha),
                surfaceContainer = baseScheme.surfaceContainer.copy(alpha = surfaceAlpha),
                surfaceContainerHigh = baseScheme.surfaceContainerHigh.copy(alpha = surfaceAlpha),
                surfaceContainerHighest = baseScheme.surfaceContainerHighest.copy(alpha = surfaceAlpha),
                surfaceDim = baseScheme.surfaceDim.copy(alpha = surfaceAlpha),
                surfaceBright = baseScheme.surfaceBright.copy(alpha = surfaceAlpha),
            )
        } else {
            baseScheme
        }
    }
    // background 透明：M3 Scaffold 默认容器色即 background，于是所有页面背景自动透出底层壁纸。
    val themeColorScheme = remember(effectiveScheme) {
        effectiveScheme.copy(background = Color.Transparent)
    }
    val themeShapes = remember(shapeResolver, themeState.cornerStyle, themeState.cornerStrength) {
        shapeResolver.shapes(themeState.cornerStyle, themeState.cornerStrength)
    }
    // 壁纸（已按滑杆模糊）：既用于绘制底层背景，也用于合成「仅背景」位图。
    val wallpaper = rememberBackgroundBitmap(themeState.backgroundUri, themeState.backgroundBlur)
    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseScheme.background)
            .onSizeChanged { windowSize = it },
    ) {
        // 液态玻璃（Kotlin 实现）采样源：把底层「底色 + 背景图」记录进 graphicsLayer，
        // 玻璃面（Modifier.drawBackdrop）据此做折射/模糊采样，与上游 Kyant0/AndroidLiquidGlass
        // 的标准用法一致（layerBackdrop 只应用于背景容器，玻璃元素作为同级兄弟节点采样）。
        val layerBackdrop = rememberLayerBackdrop()
        // 主窗口根 View：下发给 core:ui，用于自动识别独立弹窗窗口（见 LocalLiquidGlassWindowView）。
        val windowView = LocalView.current
        val glassBackground = if (themeState.liquidGlassRenderer == LiquidGlassRenderer.Kyant) {
            Modifier.fillMaxSize().layerBackdrop(layerBackdrop)
        } else {
            Modifier.fillMaxSize()
        }
        Box(modifier = glassBackground) {
            wallpaper?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = themeState.backgroundAlpha,
                )
            }
        }
        // C++ 路径：把「底色 + 壁纸」合成为**仅背景**位图下发给原生层。
        // 容器玻璃面据此采样——玻璃面下的控件整体消失、只透背景（导航栏走实时快照，不受影响）。
        if (themeState.liquidGlassRenderer == LiquidGlassRenderer.Cpp && liquidGlassWidgetHost != null) {
            val bgArgb = baseScheme.background.toArgb()
            val bgAlpha = themeState.backgroundAlpha
            LaunchedEffect(liquidGlassWidgetHost, wallpaper, bgArgb, bgAlpha, windowSize) {
                if (windowSize.width <= 0 || windowSize.height <= 0) return@LaunchedEffect
                val composed = withContext(Dispatchers.Default) {
                    composeBackgroundBitmap(windowSize, bgArgb, wallpaper, bgAlpha)
                }
                liquidGlassWidgetHost.setBackgroundBackdrop(composed)
            }
        }
        CompositionLocalProvider(
            LocalControlOutline provides themeState.toControlOutline(),
            LocalLiquidGlassRenderer provides themeState.liquidGlassRenderer,
            LocalLiquidGlassBackdrop provides
                if (themeState.liquidGlassRenderer == LiquidGlassRenderer.Kyant) layerBackdrop else null,
            LocalLiquidGlassControlsEnabled provides themeState.liquidGlassControlsEnabled,
            LocalLiquidGlassNavbarEnabled provides themeState.liquidGlassNavbarEnabled,
            LocalLiquidGlassStyle provides themeState.toLiquidGlassStyle(),
            LocalLiquidGlassWidgetHost provides liquidGlassWidgetHost,
            LocalLiquidGlassCppAvailable provides liquidGlassCppAvailable,
            LocalCornerStyle provides themeState.cornerStyle,
            LocalCornerStrength provides themeState.cornerStrength,
            LocalLiquidGlassWindowView provides windowView,
            LocalMotionPreset provides themeState.motionPreset,
            LocalLoadingAnimation provides themeState.toLoadingAnimationSpec(),
        ) {
            MaterialTheme(
                colorScheme = themeColorScheme,
                shapes = themeShapes,
                content = content,
            )
        }
    }
}

/**
 * 全局自定义背景图：按 content URI 异步解码（IO 线程，随 URI 记忆缓存），
 * 以 [ContentScale.Crop] 铺满全屏并按 alpha 叠加；模糊采用 [blurBitmap] 软件实现，
 * Android 6.0(API23) 起全版本一致生效（Modifier.blur 仅 Android 12+ 可用，旧机会静默失效）。
 * URI 为空或解码失败时返回 null。
 */
@Composable
private fun rememberBackgroundBitmap(uri: String?, blurDp: Float): Bitmap? {
    if (uri == null) return null
    val context = LocalContext.current
    val density = LocalDensity.current.density
    // 第一阶段：仅随 URI 重新解码原图，拖滑杆不会反复读盘解码。
    val source by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }
    // 滑杆值按 0.5dp 取整作为 key，避免连续回调逐像素重算；模糊在 IO 线程执行。
    val blurStep = (blurDp.coerceAtLeast(0f) * 2f).roundToInt()
    val rendered by produceState<Bitmap?>(initialValue = null, source, blurStep) {
        value = withContext(Dispatchers.IO) {
            val src = source
            if (src == null) {
                null
            } else {
                runCatching { blurBitmap(src, blurStep / 2f, density) }.getOrNull() ?: src
            }
        }
    }
    return rendered
}

/**
 * 合成「仅背景」位图：底色铺满 + 壁纸按 Crop 居中缩放并按 [alpha] 叠加。
 *
 * 与页面实际背景的绘制规则（`Image` + `ContentScale.Crop`）保持一致，
 * 因此原生玻璃采样到的就是用户看到的背景。尺寸必须为窗口物理像素。
 */
private fun composeBackgroundBitmap(
    size: IntSize,
    colorArgb: Int,
    wallpaper: Bitmap?,
    alpha: Float,
): Bitmap {
    val width = size.width.coerceAtLeast(1)
    val height = size.height.coerceAtLeast(1)
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(colorArgb)
    if (wallpaper != null && alpha > 0f && wallpaper.width > 0 && wallpaper.height > 0) {
        val scale = max(width / wallpaper.width.toFloat(), height / wallpaper.height.toFloat())
        val drawW = wallpaper.width * scale
        val drawH = wallpaper.height * scale
        val left = (width - drawW) * 0.5f
        val top = (height - drawH) * 0.5f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        }
        canvas.drawBitmap(wallpaper, null, RectF(left, top, left + drawW, top + drawH), paint)
    }
    return out
}