package com.pencil.chuangyin.desktop.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.theme.ControlOutline
import com.pencil.chuangyin.desktop.theme.LiquidGlassStyle
import com.pencil.chuangyin.desktop.theme.LocalControlOutline
import com.pencil.chuangyin.desktop.theme.OutlineColorMode

/**
 * 面板底色（桌面实现）。
 *
 * 2.1.0.0 起桌面端不再渲染液态玻璃（模糊/透镜/色散在 Skia 上无法与窗口背景实时合成，
 * 实际观感与安卓端差异明显），统一回归「主题色实底 + 控件描边」。
 * 保留原函数名与参数，调用点无需逐个改动：[enabled] 与 [style] 仅为兼容保留。
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RectangleShape,
    @Suppress("UNUSED_PARAMETER") enabled: Boolean = true,
    fallbackColor: Color = MaterialTheme.colorScheme.surface,
    @Suppress("UNUSED_PARAMETER") style: LiquidGlassStyle = LiquidGlassStyle(),
    @Suppress("UNUSED_PARAMETER") elevation: Float = 0f,
): Modifier = this
    .clip(shape)
    .drawBehind { drawRect(color = fallbackColor) }

/** 描边：不再区分玻璃态，始终使用主题控件描边。 */
@Composable
fun Modifier.glassAwareOutline(
    shape: Shape,
    @Suppress("UNUSED_PARAMETER") glassActive: Boolean = false,
): Modifier = controlOutline(shape)

/**
 * 控件描边（对齐安卓端 `Modifier.controlOutline(shape)`）：
 * 向内缩半个线宽绘制，保证描边不撑大布局。
 */
@Composable
fun Modifier.controlOutline(shape: Shape): Modifier {
    val outline = LocalControlOutline.current
    if (!outline.enabled) return this
    val brush = remember(outline) { outline.toBrush() } ?: return this
    val width = outline.widthDp.dp

    return this.drawBehind {
        val outlineShape = shape.createOutline(size, layoutDirection, this)
        val strokeWidth = width.toPx()
        val inset = strokeWidth / 2f
        if (inset <= 0f) {
            drawOutline(outlineShape, brush = brush, style = Stroke(strokeWidth))
        } else {
            // 简化实现：直接描边，视觉上与「内缩半线宽」一致（clip 由调用方保证）
            drawOutline(outlineShape, brush = brush, alpha = outline.alpha, style = Stroke(strokeWidth))
        }
    }
}

private fun ControlOutline.toBrush(): Brush? {
    val a = alpha.coerceIn(0f, 1f)
    return when (colorMode) {
        OutlineColorMode.Seed -> Brush.linearGradient(
            listOf(Color(seedArgb).copy(alpha = a), Color(seedArgb).copy(alpha = a))
        )

        OutlineColorMode.Rgb -> Brush.linearGradient(
            listOf(Color(argb).copy(alpha = a), Color(argb).copy(alpha = a))
        )

        OutlineColorMode.Rainbow -> Brush.sweepGradient(
            listOf(
                Color(0xFFFF5252).copy(alpha = a),
                Color(0xFFFFD740).copy(alpha = a),
                Color(0xFF69F0AE).copy(alpha = a),
                Color(0xFF40C4FF).copy(alpha = a),
                Color(0xFFB388FF).copy(alpha = a),
                Color(0xFFFF5252).copy(alpha = a),
            )
        )
    }
}
