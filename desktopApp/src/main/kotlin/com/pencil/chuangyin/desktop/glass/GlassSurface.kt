package com.pencil.chuangyin.desktop.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.theme.LIQUID_GLASS_LENS_AMOUNT_MAX_DP
import com.pencil.chuangyin.desktop.theme.LIQUID_GLASS_LENS_HEIGHT_MAX_DP
import com.pencil.chuangyin.desktop.theme.LiquidGlassStyle
import com.pencil.chuangyin.desktop.theme.LocalControlOutline
import com.pencil.chuangyin.desktop.theme.LocalLiquidGlassControlsEnabled
import com.pencil.chuangyin.desktop.theme.LocalLiquidGlassStyle
import com.pencil.chuangyin.desktop.theme.OutlineColorMode

/**
 * 液态玻璃面板（桌面实现）——与安卓端 `Modifier.nativeGlassSurface` 的参数语义对齐，
 * 但材质由 Compose/Skia 绘制（桌面无法像 Android 那样用 C++/ImGui 采样窗口背景）。
 *
 * 结构分层（自上而下，全部在 [drawBehind] 内叠加）：
 *  1. 磨砂底色层：surface 按 tintAlpha 半透明铺底
 *  2. 顶部透镜高光：高度 = lensHeightDp，白色纵向渐隐
 *  3. 折射亮带：宽度随 lensAmountDp 扩散，模拟光线在玻璃厚边处的折射
 *  4. 底部内阴影：给出面板厚度与投影关系
 *  5. 轮廓高光：1dp 白色内描边（contourEdge 打开时再加一层内侧亮线）
 *  6. 光谱错位：spectral > 0 时用彩色渐变描边模拟色散
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RectangleShape,
    enabled: Boolean = LocalLiquidGlassControlsEnabled.current,
    fallbackColor: Color = MaterialTheme.colorScheme.surface,
    style: LiquidGlassStyle = LocalLiquidGlassStyle.current,
    elevation: Float = 0f,
): Modifier {
    val active = enabled && style.glassOn
    val surface = fallbackColor
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val shadowColor = MaterialTheme.colorScheme.scrim.copy(alpha = if (isDark) 0.55f else 0.22f)

    val shadowModifier = if (active && style.shadowOn && elevation > 0f) {
        Modifier.shadow(
            elevation = elevation.dp,
            shape = shape,
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowColor,
        )
    } else {
        Modifier
    }

    return this
        .then(shadowModifier)
        .clip(shape)
        .drawBehind {
            if (!active) {
                drawRect(color = surface)
                return@drawBehind
            }

            val w = size.width
            val h = size.height
            val tint = style.tintAlpha.coerceIn(0f, 1f)
            // 亮玻璃 / 暗玻璃的取色差异：深色主题下玻璃偏冷白，浅色主题下更透
            val glassWhite = if (isDark) Color(0xFFDCE6F5) else Color(0xFFFFFFFF)

            // 1. 磨砂底色
            val baseAlpha = (if (isDark) 0.34f else 0.52f) * tint
            drawRect(color = surface.copy(alpha = surface.alpha * baseAlpha))

            // 2. 整体纵向高光（上亮下暗）
            drawRect(
                brush = Brush.verticalGradient(
                    0f to glassWhite.copy(alpha = 0.30f * tint),
                    0.45f to glassWhite.copy(alpha = 0.06f * tint),
                    1f to Color.Black.copy(alpha = 0.04f * tint),
                ),
                size = Size(w, h),
            )

            // 3. 顶部透镜高光带
            val lensH = (style.lensHeightDp.coerceIn(0f, LIQUID_GLASS_LENS_HEIGHT_MAX_DP).dp.toPx())
                .coerceAtMost(h * 0.5f)
            if (lensH > 0.5f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to glassWhite.copy(alpha = 0.34f * tint),
                        0.6f to glassWhite.copy(alpha = 0.08f * tint),
                        1f to Color.Transparent,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(w, lensH),
                )
            }

            // 4. 折射亮带：贴着顶部高光的下沿，横向做一点径向扩散
            val amount = style.lensAmountDp.coerceIn(0f, LIQUID_GLASS_LENS_AMOUNT_MAX_DP).dp.toPx()
            if (amount > 0.5f) {
                val causticY = (lensH * 0.75f).coerceAtLeast(1f)
                val causticH = (amount * 0.10f).coerceIn(1f, 6f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to glassWhite.copy(alpha = 0.24f * tint),
                        1f to Color.Transparent,
                    ),
                    topLeft = Offset(0f, causticY),
                    size = Size(w, causticH),
                )
                // 底部反射光：玻璃下缘的二次反射
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to glassWhite.copy(alpha = 0.14f * tint),
                    ),
                    size = Size(w, h),
                )
            }

            // 5. 轮廓高光
            val outline = shape.createOutline(size, layoutDirection, this)
            val edgeAlpha = 0.40f * tint
            drawOutline(
                outline = outline,
                color = glassWhite.copy(alpha = edgeAlpha),
                style = Stroke(width = 1.dp.toPx()),
            )
            if (style.contourEdge) {
                drawOutline(
                    outline = outline,
                    color = glassWhite.copy(alpha = 0.18f * tint),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }

            // 6. 光谱色散
            if (style.spectral > 0.01f) {
                val s = style.spectral.coerceIn(0f, 2f)
                drawOutline(
                    outline = outline,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF5A7A).copy(alpha = 0.30f * s),
                            Color(0xFFFFF0A0).copy(alpha = 0.22f * s),
                            Color(0xFF66E0FF).copy(alpha = 0.30f * s),
                            Color(0xFF9A7BFF).copy(alpha = 0.26f * s),
                        ),
                        start = Offset.Zero,
                        end = Offset(w, h),
                    ),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
}

/**
 * 玻璃感知描边：玻璃生效时由玻璃材质自己画边，避免与 Compose 描边叠加成双线
 * （对齐安卓端 `Modifier.glassAwareOutline(shape, glassActive)`）。
 */
@Composable
fun Modifier.glassAwareOutline(
    shape: Shape,
    glassActive: Boolean = LocalLiquidGlassControlsEnabled.current,
): Modifier {
    return if (glassActive) this else controlOutline(shape)
}

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

private fun com.pencil.chuangyin.desktop.theme.ControlOutline.toBrush(): Brush? {
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

/** 供设置页预览：返回玻璃面料的代表色（用于色卡）。 */
fun glassPreviewColor(isDark: Boolean): Color =
    if (isDark) Color(0x33405A80) else Color(0x44FFFFFF)
