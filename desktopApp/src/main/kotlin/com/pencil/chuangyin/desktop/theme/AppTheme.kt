package com.pencil.chuangyin.desktop.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.pencil.chuangyin.desktop.support.rememberLocalImageBitmap

/**
 * 桌面端主题入口 —— 与安卓端 core/theme/AppTheme.kt 结构一一对应：
 * 生成 ColorScheme → 按 surfaceAlpha 降透明度 → 下发全部 CompositionLocal →
 * MaterialTheme(background = Transparent) 让根背景图层接管底色。
 */
@Composable
fun AppTheme(
    state: ThemeState = ThemeState.Default,
    content: @Composable () -> Unit,
) {
    val baseScheme = remember(state.seedArgb, state.darkTheme) {
        generateColorScheme(state.seedArgb, state.darkTheme)
    }
    val effectiveScheme = remember(baseScheme, state.surfaceAlpha) {
        baseScheme.withSurfaceAlpha(state.surfaceAlpha)
    }
    val shapes = remember(state.cornerStyle, state.cornerStrength) {
        themeShapes(state.cornerStyle, state.cornerStrength)
    }

    CompositionLocalProvider(
        LocalControlOutline provides state.toControlOutline(),
        LocalLiquidGlassControlsEnabled provides state.liquidGlassControlsEnabled,
        LocalLiquidGlassNavbarEnabled provides state.liquidGlassNavbarEnabled,
        LocalLiquidGlassStyle provides state.toLiquidGlassStyle(),
        LocalCornerStyle provides state.cornerStyle,
        LocalCornerStrength provides state.cornerStrength,
        LocalGlassActive provides (state.liquidGlassControlsEnabled || state.liquidGlassNavbarEnabled),
    ) {
        MaterialTheme(
            colorScheme = effectiveScheme.copy(background = Color.Transparent),
            shapes = shapes,
        ) {
            AppRootBackground(state) {
                content()
            }
        }
    }
}

/**
 * 根背景：纯色底 + 可选本地壁纸（按 backgroundAlpha 与 backgroundBlur 处理），
 * 对齐安卓端 CustomBackgroundImage 的行为。
 */
@Composable
fun AppRootBackground(state: ThemeState, content: @Composable () -> Unit) {
    val baseColor = MaterialTheme.colorScheme.surface
    val wallpaper = rememberLocalImageBitmap(state.backgroundPath)

    Box(Modifier.fillMaxSize().background(baseColor)) {
        if (wallpaper != null && state.backgroundAlpha > 0f) {
            val blurModifier = if (state.backgroundBlur > 0f) {
                Modifier.blur(state.backgroundBlur.coerceIn(0f, BACKGROUND_BLUR_MAX_DP).dp)
            } else {
                Modifier
            }
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(blurModifier),
                alpha = state.backgroundAlpha.coerceIn(0f, 1f),
            )
            // 壁纸上叠一层主题色轻雾，避免内容对比度不足
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                baseColor.copy(alpha = 0.18f),
                                baseColor.copy(alpha = 0.34f),
                            )
                        )
                    )
            )
        }
        content()
    }
}

/** surface 系整体降 alpha（对齐安卓端 AppTheme 的 surfaceAlpha 处理）。 */
private fun ColorScheme.withSurfaceAlpha(alpha: Float): ColorScheme {
    val a = alpha.coerceIn(SURFACE_ALPHA_MIN, 1f)
    if (a >= 1f) return this
    fun Color.fade() = copy(alpha = this.alpha * a)
    return copy(
        surface = surface.fade(),
        surfaceVariant = surfaceVariant.fade(),
        surfaceTint = surfaceTint.fade(),
        surfaceBright = surfaceBright.fade(),
        surfaceDim = surfaceDim.fade(),
        surfaceContainer = surfaceContainer.fade(),
        surfaceContainerLow = surfaceContainerLow.fade(),
        surfaceContainerLowest = surfaceContainerLowest.fade(),
        surfaceContainerHigh = surfaceContainerHigh.fade(),
        surfaceContainerHighest = surfaceContainerHighest.fade(),
        inverseSurface = inverseSurface.fade(),
    )
}

/** 桌面端没有系统栏，深浅色完全由主题状态决定（不跟随系统）。 */
