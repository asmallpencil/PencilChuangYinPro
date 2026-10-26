package com.pencil.chuangyin.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.resources.R as ResR

/**
 * 控件描边运行时描述（由 [ThemeState] 派生，经 [LocalControlOutline] 下发给 core:ui 控件）。
 *
 * 描边颜色**不受深色/浅色主题影响**：
 * - Seed 模式使用 [seedArgb] 对应的固定颜色（而非 ColorScheme.primary，后者随主题色调变化）；
 * - 整体不透明度由 [alpha] 独立控制。
 */
data class ControlOutline(
    val enabled: Boolean,
    val widthDp: Float,
    val colorMode: OutlineColorMode,
    val argb: Long,
    /** 描边整体不透明度 0..1。 */
    val alpha: Float,
    /** 主题种子色（Seed 模式下作为固定描边色，不随深浅主题变化）。 */
    val seedArgb: Long,
) {
    companion object {
        /** 未开启主题描边时的空实现占位（控件读到后不绘制任何描边）。 */
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

/** 主题状态 → 控件描边描述。 */
fun ThemeState.toControlOutline(): ControlOutline = ControlOutline(
    enabled = outlineEnabled,
    widthDp = outlineWidthDp.coerceIn(OUTLINE_WIDTH_MIN_DP, OUTLINE_WIDTH_MAX_DP),
    colorMode = outlineColorMode,
    argb = outlineArgb,
    alpha = outlineAlpha.coerceIn(OUTLINE_ALPHA_MIN, 1f),
    seedArgb = seedArgb,
)

/**
 * 控件描边 CompositionLocal：AppTheme 统一提供，AppButton 等控件按需消费。
 *
 * 必须使用 [compositionLocalOf]：开关/宽度/配色随设置实时变化时，只有实际读取的控件重组；
 * 若误用 staticCompositionLocalOf，值变化不会触发消费方重组（设置页改了导航栏无反应的同类坑）。
 */
val LocalControlOutline = compositionLocalOf { ControlOutline.Disabled }

/** 液态玻璃控件开关 CompositionLocal：AppTheme 统一提供，控件坞按需消费。 */
val LocalLiquidGlassControlsEnabled = compositionLocalOf { false }

/**
 * 液态玻璃导航栏开关 CompositionLocal：AppTheme 统一提供，悬浮导航栏按需消费。
 *
 * 与 [LocalLiquidGlassControlsEnabled] 分开：导航栏玻璃是**独立于控件玻璃**的用户开关
 * （设置页「液态玻璃导航栏」），不能因为用户开了控件玻璃就让导航栏底板自动玻璃化，
 * 也不能因为关了控件玻璃就关掉已开启的导航栏玻璃。
 */
val LocalLiquidGlassNavbarEnabled = compositionLocalOf { false }

/** 当前边角风格 CompositionLocal（供控件计算形状安全内边距，见 [cornerSafeInset]）。 */
val LocalCornerStyle = compositionLocalOf { CornerStyle.Rounded }

/** 当前边角强度 CompositionLocal（0..1）。 */
val LocalCornerStrength = compositionLocalOf { 0.5f }

/**
 * 形状安全内边距：边角强度拉高后，菱形/平行四边形的直切角会侵入控件四角，
 * 仅靠 M3 默认内容内边距（按钮 24dp / 输入框 16dp）在高档位时仍会切掉靠边文字。
 *
 * 本函数按控件使用的形状档位缩放系数（small=0.75 / medium=1 / large=1.5）
 * 与当前风格/强度，返回需要**追加**的水平内边距：
 * - 菱形四角直切，侵占最大；平行四边形只切两角，次之；圆角边缘圆滑，侵占最小；
 * - 返回值封顶 [CORNER_SAFE_INSET_MAX_DP]，避免文字区被过度压缩。
 *
 * 控件应把返回值叠加到自身内容水平内边距上（垂直方向无需处理，文字垂直居中）。
 */
@Composable
fun cornerSafeInset(shapeScale: Float): Dp {
    val style = LocalCornerStyle.current
    val strength = LocalCornerStrength.current.coerceIn(0f, 1f)
    val base = dimensionResource(ResR.dimen.dimen_shape_corner_m)
    // 与 ThemeResolvers 保持同一强度增益（4 倍）。
    val cut = base * shapeScale * strength * SHAPE_STRENGTH_GAIN
    val factor = when (style) {
        CornerStyle.Diamond -> 0.6f
        CornerStyle.Parallelogram -> 0.45f
        CornerStyle.Rounded -> 0.22f
        CornerStyle.Square -> 0f
    }
    return (cut * factor).coerceAtMost(CORNER_SAFE_INSET_MAX_DP)
}

/** 形状安全内边距上限（dp），与 dimens 中全局最大内容边距对齐。 */
private val CORNER_SAFE_INSET_MAX_DP = 20.dp

/** 形状强度增益：必须与 ThemeResolvers.SHAPE_STRENGTH_GAIN 保持一致。 */
private const val SHAPE_STRENGTH_GAIN = 4f

/**
 * 按 [ControlOutline] 配置解析描边画刷（主题色 / 自定义 RGB / 彩虹水平渐变）。
 * 供 [controlOutline] Modifier 与无法使用 Modifier 的组件（如 M3 FilterChip 的 border 参数）共用。
 *
 * Seed 模式使用 [ControlOutline.seedArgb] 对应的固定颜色，不随深色/浅色主题变化；
 * 整体不透明度由 [ControlOutline.alpha] 调制。
 */
@Composable
fun outlineBrush(outline: ControlOutline): Brush {
    val alpha = outline.alpha.coerceIn(OUTLINE_ALPHA_MIN, 1f)
    return when (outline.colorMode) {
        OutlineColorMode.Seed -> {
            val color = Color(outline.seedArgb).copy(alpha = alpha)
            Brush.horizontalGradient(listOf(color, color))
        }
        OutlineColorMode.Rgb -> {
            val color = Color(outline.argb).copy(alpha = alpha)
            Brush.horizontalGradient(listOf(color, color))
        }
        OutlineColorMode.Rainbow -> Brush.horizontalGradient(
            listOf(
                colorResource(ResR.color.rainbow_red).copy(alpha = alpha),
                colorResource(ResR.color.rainbow_orange).copy(alpha = alpha),
                colorResource(ResR.color.rainbow_yellow).copy(alpha = alpha),
                colorResource(ResR.color.rainbow_green).copy(alpha = alpha),
                colorResource(ResR.color.rainbow_cyan).copy(alpha = alpha),
                colorResource(ResR.color.rainbow_blue).copy(alpha = alpha),
                colorResource(ResR.color.rainbow_purple).copy(alpha = alpha),
            ),
        )
    }
}

/**
 * 给控件叠加用户配置的描边；未开启时原样返回（零开销）。
 * 描边沿控件 [shape] 绘制：跟随主题色 / 自定义 RGB / RGB 彩虹水平渐变。
 *
 * 注意：不能用 [androidx.compose.foundation.border]——它把描边**居中**画在形状边缘上，
 * 一半线宽会超出控件可视边缘，造成描边外扩/与控件边缘产生间隙（切角/圆角越粗越明显）。
 * 这里改为：对缩小了一个线宽的尺寸生成形状轮廓，再平移半个线宽绘制，
 * 描边完整落在控件边缘**内侧**，外缘与控件边缘严格重合。
 *
 * 仅适用于"布局尺寸 == 可见尺寸"的控件（如 Button、TextField、SegmentedRow）；
 * 对 M3 FilterChip 这类带 48dp 触摸目标、布局外框大于可见芯片的控件，
 * 本 Modifier 的描边会落在外框上而与芯片边缘脱节——此类组件应改用原生 border 参数
 * （配合 [outlineBrush]）让描边沿真实芯片形状绘制。
 */
@Composable
fun Modifier.controlOutline(shape: Shape): Modifier {
    val outline = LocalControlOutline.current
    if (!outline.enabled || outline.widthDp <= 0f) return this
    val brush = outlineBrush(outline)
    val strokeWidth = outline.widthDp.dp
    return this.drawWithCache {
        val strokePx = strokeWidth.toPx()
        val half = strokePx / 2f
        val insetSize = Size(
            width = (size.width - strokePx).coerceAtLeast(0f),
            height = (size.height - strokePx).coerceAtLeast(0f),
        )
        val shapeOutline = shape.createOutline(insetSize, layoutDirection, this)
        val stroke = Stroke(width = strokePx)
        // 用 onDrawWithContent 让描边画在“内容（含控件半透明表层）之上”，
        // 从而在降低控件不透明度（surfaceAlpha<1）时，描边不会被半透明表层盖住而变暗。
        onDrawWithContent {
            drawContent()
            when (shapeOutline) {
                is Outline.Rectangle -> drawRect(
                    brush = brush,
                    topLeft = Offset(half, half),
                    size = insetSize,
                    style = stroke,
                )
                is Outline.Rounded -> {
                    // 内缩后的圆角半径 = 原半径 - 半线宽，外缘才能与原形状边缘重合。
                    val outerRadius = shapeOutline.roundRect.topLeftCornerRadius.x
                    val innerRadius = (outerRadius - half).coerceAtLeast(0f)
                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(half, half),
                        size = insetSize,
                        cornerRadius = CornerRadius(innerRadius),
                        style = stroke,
                    )
                }
                is Outline.Generic -> translate(half, half) {
                    drawPath(shapeOutline.path, brush = brush, style = stroke)
                }
            }
        }
    }
}

/** RGB 彩虹描边画刷：红→橙→黄→绿→青→蓝→紫 水平渐变（颜色资源集中在 core:resources）。 */
@Composable
fun rainbowOutlineBrush(): Brush = Brush.horizontalGradient(
    listOf(
        colorResource(ResR.color.rainbow_red),
        colorResource(ResR.color.rainbow_orange),
        colorResource(ResR.color.rainbow_yellow),
        colorResource(ResR.color.rainbow_green),
        colorResource(ResR.color.rainbow_cyan),
        colorResource(ResR.color.rainbow_blue),
        colorResource(ResR.color.rainbow_purple),
    ),
)

/**
 * 「玻璃生效时不出 Compose 描边」的描边包装 —— 给同时套了 [nativeGlassSurface] 的控件使用。
 *
 * 原因（重要）：C++ 原生玻璃面板以**窗口快照**（PixelCopy）为背景纹理，Compose 画在控件
 * 边缘的描边会被一并采样进玻璃底面并原样透出，于是玻璃控件上出现一圈「kt 风格边框」，
 * 观感上就是仿玻璃而非真玻璃；真正的玻璃边缘高光由原生层自绘（contourEdge）。
 *
 * 因此：玻璃生效 → 只交给原生层；玻璃不可用（开关关闭 / 宿主缺失）→ 回退到主题描边，
 * 保证回退态仍有边框。
 */
@Composable
fun Modifier.glassAwareOutline(shape: Shape, glassActive: Boolean): Modifier =
    if (glassActive) this else controlOutline(shape)
