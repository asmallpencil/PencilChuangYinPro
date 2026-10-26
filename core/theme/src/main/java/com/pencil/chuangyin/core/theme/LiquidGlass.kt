package com.pencil.chuangyin.core.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * 全局液态玻璃样式参数（设置页可调，实时作用于所有自研液态玻璃控件）。
 *
 * 说明：这些参数是「用户可调的表现层开关」，由 core:ui 的自研渲染层
 * （Modifier.liquidGlassSurface / LiquidGlassControls）消费并映射为具体的绘制层次。
 * 所有默认值与上限集中在下方常量，避免散落硬编码。
 *
 * @param blurDp 背景模糊半径（dp），越大越浑浊（当前以磨砂白纱模拟）。
 * @param lensHeightDp 透镜高度（dp），越大高光 sheen 越高。
 * @param lensAmountDp 透镜位移量（dp），控制高光/折射渐变的扩散范围。
 * @param spectral 光谱色散强度（0~2），边缘彩色分离。
 * @param tintAlpha 控件整体不透明度（0~1）。
 * @param shadowOn 是否投射阴影。
 * @param glassOn 是否启用玻璃材质（关闭则退化为纯色面）。
 * @param contourEdge 是否绘制轮廓高光边缘。
 */
data class LiquidGlassStyle(
    val blurDp: Float = LIQUID_GLASS_BLUR_DEFAULT,
    val lensHeightDp: Float = LIQUID_GLASS_LENS_HEIGHT_DEFAULT,
    val lensAmountDp: Float = LIQUID_GLASS_LENS_AMOUNT_DEFAULT,
    val spectral: Float = LIQUID_GLASS_SPECTRAL_DEFAULT,
    val tintAlpha: Float = LIQUID_GLASS_TINT_ALPHA_DEFAULT,
    val shadowOn: Boolean = LIQUID_GLASS_SHADOW_DEFAULT,
    val glassOn: Boolean = LIQUID_GLASS_GLASS_DEFAULT,
    val contourEdge: Boolean = LIQUID_GLASS_CONTOUR_DEFAULT,
)

/** 背景模糊默认值（dp）。 */
const val LIQUID_GLASS_BLUR_DEFAULT = 2f

/** 背景模糊上限（dp），与库 Render::BLUR_RADIUS_MAX_DP 对齐。 */
const val LIQUID_GLASS_BLUR_MAX_DP = 10f

/** 透镜高度默认值（dp）。 */
const val LIQUID_GLASS_LENS_HEIGHT_DEFAULT = 12f

/** 透镜高度上限（dp）。 */
const val LIQUID_GLASS_LENS_HEIGHT_MAX_DP = 32f

/** 透镜位移量默认值（dp）。 */
const val LIQUID_GLASS_LENS_AMOUNT_DEFAULT = 24f

/** 透镜位移量上限（dp）。 */
const val LIQUID_GLASS_LENS_AMOUNT_MAX_DP = 64f

/** 光谱色散强度默认值。 */
const val LIQUID_GLASS_SPECTRAL_DEFAULT = 0f

/** 光谱色散强度上限，与库 Render::SPECTRAL_MAX 对齐。 */
const val LIQUID_GLASS_SPECTRAL_MAX = 2f

/** 控件整体不透明度默认值。 */
const val LIQUID_GLASS_TINT_ALPHA_DEFAULT = 1f

/** 控件整体不透明度下限（保留可见性）。 */
const val LIQUID_GLASS_TINT_ALPHA_MIN = 0.1f

/** 阴影默认开启。 */
const val LIQUID_GLASS_SHADOW_DEFAULT = true

/** 玻璃材质默认开启。 */
const val LIQUID_GLASS_GLASS_DEFAULT = true

/** 轮廓高光边缘默认关闭。 */
const val LIQUID_GLASS_CONTOUR_DEFAULT = false

/** 主题状态 → 液态玻璃样式。 */
fun ThemeState.toLiquidGlassStyle(): LiquidGlassStyle = LiquidGlassStyle(
    blurDp = liquidGlassBlurDp.coerceIn(0f, LIQUID_GLASS_BLUR_MAX_DP),
    lensHeightDp = liquidGlassLensHeightDp.coerceIn(0f, LIQUID_GLASS_LENS_HEIGHT_MAX_DP),
    lensAmountDp = liquidGlassLensAmountDp.coerceIn(0f, LIQUID_GLASS_LENS_AMOUNT_MAX_DP),
    spectral = liquidGlassSpectral.coerceIn(0f, LIQUID_GLASS_SPECTRAL_MAX),
    tintAlpha = liquidGlassTintAlpha.coerceIn(LIQUID_GLASS_TINT_ALPHA_MIN, 1f),
    shadowOn = liquidGlassShadowOn,
    glassOn = liquidGlassGlassOn,
    contourEdge = liquidGlassContourEdge,
)

/**
 * 液态玻璃样式 CompositionLocal：由 AppTheme 统一提供当前主题的 [LiquidGlassStyle]。
 *
 * core:ui 的自研控件与 [Modifier.liquidGlassSurface] 默认从此处读取样式，
 * 也可在局部（如设置页预览区）用 [androidx.compose.runtime.CompositionLocalProvider]
 * 覆盖，实现「局部参数预览」而不影响全局。
 */
val LocalLiquidGlassStyle = compositionLocalOf { LiquidGlassStyle() }
