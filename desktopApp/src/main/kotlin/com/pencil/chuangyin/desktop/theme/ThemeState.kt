package com.pencil.chuangyin.desktop.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 桌面端主题状态 —— 与安卓端 core:theme 的 [ThemeState] 字段一一对应，
 * 唯一差异是去掉了 `liquidGlassRenderer`（桌面只有一套 Skia 玻璃实现）。
 *
 * 对齐原则（用户要求「UI 和功能与安卓端同步更新」）：字段名、默认值、取值范围
 * 全部沿用安卓端常量，任何一端新增主题项时，本类与 [StoredTheme] 同步补字段即可。
 */
data class ThemeState(
    val seedArgb: Long,
    val darkTheme: Boolean,
    val cornerStyle: CornerStyle,
    val cornerStrength: Float,
    val motionPreset: MotionPreset,
    val outlineEnabled: Boolean = OUTLINE_ENABLED_DEFAULT,
    val outlineWidthDp: Float = OUTLINE_WIDTH_DEFAULT_DP,
    val outlineColorMode: OutlineColorMode = OutlineColorMode.Seed,
    val outlineArgb: Long = SeedOption.Orange.color,
    val outlineAlpha: Float = OUTLINE_ALPHA_DEFAULT,
    val backgroundPath: String? = null,
    val backgroundAlpha: Float = BACKGROUND_ALPHA_DEFAULT,
    val surfaceAlpha: Float = SURFACE_ALPHA_DEFAULT,
    val backgroundBlur: Float = BACKGROUND_BLUR_DEFAULT,
    val liquidGlassControlsEnabled: Boolean = LIQUID_GLASS_CONTROLS_ENABLED_DEFAULT,
    val liquidGlassNavbarEnabled: Boolean = LIQUID_GLASS_NAVBAR_ENABLED_DEFAULT,
    val liquidGlassBlurDp: Float = LIQUID_GLASS_BLUR_DEFAULT,
    val liquidGlassLensHeightDp: Float = LIQUID_GLASS_LENS_HEIGHT_DEFAULT,
    val liquidGlassLensAmountDp: Float = LIQUID_GLASS_LENS_AMOUNT_DEFAULT,
    val liquidGlassSpectral: Float = LIQUID_GLASS_SPECTRAL_DEFAULT,
    val liquidGlassTintAlpha: Float = LIQUID_GLASS_TINT_ALPHA_DEFAULT,
    val liquidGlassShadowOn: Boolean = LIQUID_GLASS_SHADOW_DEFAULT,
    val liquidGlassGlassOn: Boolean = LIQUID_GLASS_GLASS_DEFAULT,
    val liquidGlassContourEdge: Boolean = LIQUID_GLASS_CONTOUR_DEFAULT,
) {
    companion object {
        val Default = ThemeState(
            seedArgb = SeedOption.Orange.color,
            darkTheme = false,
            cornerStyle = CornerStyle.Rounded,
            cornerStrength = 0.5f,
            motionPreset = MotionPreset.Expressive,
        )
    }
}

// ── 常量（与安卓端 core/theme/ThemeContract.kt、LiquidGlass.kt 完全一致）──────────

const val OUTLINE_ENABLED_DEFAULT = false
const val OUTLINE_WIDTH_MIN_DP = 0.5f
const val OUTLINE_WIDTH_MAX_DP = 4f
const val OUTLINE_WIDTH_DEFAULT_DP = 1.5f
const val OUTLINE_ALPHA_DEFAULT = 1f
const val OUTLINE_ALPHA_MIN = 0.05f

const val BACKGROUND_ALPHA_DEFAULT = 0.3f
const val BACKGROUND_ALPHA_MIN = 0.05f
const val SURFACE_ALPHA_DEFAULT = 1f
const val SURFACE_ALPHA_MIN = 0.1f
const val BACKGROUND_BLUR_DEFAULT = 0f
const val BACKGROUND_BLUR_MAX_DP = 24f

const val LIQUID_GLASS_CONTROLS_ENABLED_DEFAULT = false
const val LIQUID_GLASS_NAVBAR_ENABLED_DEFAULT = false

const val LIQUID_GLASS_BLUR_DEFAULT = 2f
const val LIQUID_GLASS_BLUR_MAX_DP = 10f
const val LIQUID_GLASS_LENS_HEIGHT_DEFAULT = 12f
const val LIQUID_GLASS_LENS_HEIGHT_MAX_DP = 32f
const val LIQUID_GLASS_LENS_AMOUNT_DEFAULT = 24f
const val LIQUID_GLASS_LENS_AMOUNT_MAX_DP = 64f
const val LIQUID_GLASS_SPECTRAL_DEFAULT = 0f
const val LIQUID_GLASS_SPECTRAL_MAX = 2f
const val LIQUID_GLASS_TINT_ALPHA_DEFAULT = 1f
const val LIQUID_GLASS_TINT_ALPHA_MIN = 0.1f
const val LIQUID_GLASS_SHADOW_DEFAULT = true
const val LIQUID_GLASS_GLASS_DEFAULT = true
const val LIQUID_GLASS_CONTOUR_DEFAULT = false

/** 形状强度增益：与安卓端 ThemeResolvers.SHAPE_STRENGTH_GAIN 一致（4 倍灵敏度）。 */
private const val SHAPE_STRENGTH_GAIN = 4f

/** 形状安全内边距上限（dp）。 */
private val CORNER_SAFE_INSET_MAX_DP = 20.dp

enum class CornerStyle { Rounded, Square, Diamond, Parallelogram }

enum class MotionPreset { Full, Expressive, Reduced }

enum class OutlineColorMode { Seed, Rgb, Rainbow }

/** 种子色预设：与安卓端 SeedOption 的六个色值完全一致。 */
enum class SeedOption(val label: String, val color: Long) {
    Orange("琥珀橙", 0xFFF6B626),
    Blue("湖蓝", 0xFF0277BD),
    Green("竹绿", 0xFF2E7D32),
    Purple("藤紫", 0xFF7B1FA2),
    Rose("玫红", 0xFFC2185B),
    Teal("墨青", 0xFF00695C),
}

/** 与安卓端 cornerShape 同构：强度 0..1 映射到基础边距的 0..4 倍。 */
fun cornerShape(style: CornerStyle, strength: Float, base: Dp): Shape {
    val s = strength.coerceIn(0f, 1f) * SHAPE_STRENGTH_GAIN
    return when (style) {
        CornerStyle.Rounded -> RoundedCornerShape(base * s)
        CornerStyle.Square -> RoundedCornerShape(0.dp)
        CornerStyle.Diamond -> CutCornerShape(base * s)
        CornerStyle.Parallelogram -> CutCornerShape(
            topStart = base * s,
            topEnd = 0.dp,
            bottomEnd = base * s,
            bottomStart = 0.dp,
        )
    }
}

/** 与安卓端 DimenShapeResolver 同构：base = dimen_shape_corner_m = 16dp。 */
fun themeShapes(style: CornerStyle, strength: Float): Shapes {
    val base = 16.dp
    return buildShapes(
        extraSmall = cornerShape(style, strength, base * 0.5f),
        small = cornerShape(style, strength, base * 0.75f),
        medium = cornerShape(style, strength, base),
        large = cornerShape(style, strength, base * 1.5f),
        extraLarge = cornerShape(style, strength, base * 2f),
    )
}

/**
 * Material3 1.9 起 [Shapes] 的主构造函数与 `copy()` 在 Kotlin 侧标记为 internal，
 * 但 JVM 字节码仍保留 8 参 public 构造器（extraSmall / small / medium / large /
 * extraLarge / largeIncreased / extraLargeIncreased / extraExtraLarge）。
 * 桌面端只有前五档有语义，后三档沿用 large / extraLarge 占位；
 * 反射失败时退回默认 [Shapes]，保证永不因主题形状崩掉。
 */
private fun buildShapes(
    extraSmall: Shape,
    small: Shape,
    medium: Shape,
    large: Shape,
    extraLarge: Shape,
): Shapes = runCatching {
    val type = CornerBasedShape::class.java
    val ctor = Shapes::class.java.getDeclaredConstructor(
        type, type, type, type, type, type, type, type,
    ).apply { isAccessible = true }
    ctor.newInstance(
        extraSmall as CornerBasedShape,
        small as CornerBasedShape,
        medium as CornerBasedShape,
        large as CornerBasedShape,
        extraLarge as CornerBasedShape,
        large as CornerBasedShape,
        extraLarge as CornerBasedShape,
        extraLarge as CornerBasedShape,
    ) as Shapes
}.getOrElse { Shapes() }

/** 与安卓端 cornerSafeInset 同构：切角/圆角侵入四角时追加的水平内边距。 */
fun cornerSafeInset(style: CornerStyle, strength: Float, shapeScale: Float): Dp {
    val cut = 16.dp * shapeScale * strength.coerceIn(0f, 1f) * SHAPE_STRENGTH_GAIN
    val factor = when (style) {
        CornerStyle.Diamond -> 0.6f
        CornerStyle.Parallelogram -> 0.45f
        CornerStyle.Rounded -> 0.22f
        CornerStyle.Square -> 0f
    }
    return (cut * factor).coerceAtMost(CORNER_SAFE_INSET_MAX_DP)
}

/** 控件描边运行时描述（对齐安卓端 ControlOutline）。 */
data class ControlOutline(
    val enabled: Boolean,
    val widthDp: Float,
    val colorMode: OutlineColorMode,
    val argb: Long,
    val alpha: Float,
    val seedArgb: Long,
) {
    companion object {
        val Disabled = ControlOutline(
            enabled = false,
            widthDp = OUTLINE_WIDTH_DEFAULT_DP,
            colorMode = OutlineColorMode.Seed,
            argb = 0L,
            alpha = OUTLINE_ALPHA_DEFAULT,
            seedArgb = 0L,
        )
    }
}

fun ThemeState.toControlOutline(): ControlOutline = ControlOutline(
    enabled = outlineEnabled,
    widthDp = outlineWidthDp.coerceIn(OUTLINE_WIDTH_MIN_DP, OUTLINE_WIDTH_MAX_DP),
    colorMode = outlineColorMode,
    argb = outlineArgb,
    alpha = outlineAlpha.coerceIn(OUTLINE_ALPHA_MIN, 1f),
    seedArgb = seedArgb,
)

/**
 * 全局液态玻璃样式参数（字段与默认值对齐安卓端 LiquidGlassStyle）。
 *
 * 桌面渲染映射（[com.pencil.chuangyin.desktop.glass.glassSurface]）：
 * - blurDp         → 玻璃面整体的雾化强度（磨砂层不透明度）
 * - lensHeightDp   → 顶部透镜高光带的高度
 * - lensAmountDp   → 透镜位移量，控制折射亮带的扩散宽度
 * - spectral       → 边缘彩色分离（RGB 三色错位描边）
 * - tintAlpha      → 玻璃面整体不透明度
 * - shadowOn       → 是否投射外阴影
 * - glassOn        → 关闭后退化为纯色面板
 * - contourEdge    → 是否绘制轮廓高光
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

// ── CompositionLocal（对齐安卓端 AppTheme 下发的同名 Local）───────────────────────

val LocalControlOutline = compositionLocalOf { ControlOutline.Disabled }
val LocalLiquidGlassControlsEnabled = compositionLocalOf { false }
val LocalLiquidGlassNavbarEnabled = compositionLocalOf { false }
val LocalCornerStyle = compositionLocalOf { CornerStyle.Rounded }
val LocalCornerStrength = compositionLocalOf { 0.5f }
val LocalLiquidGlassStyle = compositionLocalOf { LiquidGlassStyle() }

/** 液态玻璃是否生效（对齐安卓端 rememberLiquidGlassActive 语义）。 */
val LocalGlassActive = compositionLocalOf { false }
