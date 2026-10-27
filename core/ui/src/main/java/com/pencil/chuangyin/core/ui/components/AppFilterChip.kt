package com.pencil.chuangyin.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.theme.LiquidGlassButtonVariant
import com.pencil.chuangyin.core.theme.LiquidGlassWidgetKind
import com.pencil.chuangyin.core.theme.LocalControlOutline
import com.pencil.chuangyin.core.theme.LocalLiquidGlassWidgetHost
import com.pencil.chuangyin.core.theme.outlineBrush
import com.pencil.chuangyin.core.ui.motion.pressScale
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion

/**
 * 全局筛选芯片：M3 [FilterChip] + 主题拐角形状 + 主题描边。
 * 所有页面一律使用本组件，禁止直接使用 M3 FilterChip（固定圆角、描边需逐处适配）。
 *
 * 描边走 [FilterChip.border] 参数（而非 [com.pencil.chuangyin.core.theme.controlOutline] Modifier）：
 * M3 FilterChip 带 48dp 触摸目标，布局外框大于可见芯片，Modifier 描边会落在外框上而
 * 与芯片真实边缘脱节；border 参数则严格沿芯片形状绘制，保证描边与边缘对齐。
 *
 * 液态玻璃：控件开关开启且原生覆盖层可用时，芯片整体交给 **C++ 原生玻璃**绘制
 * （Button 通道的 Chip 变体，选中时以强调色实心着色），Compose 侧仅保留透明占位盒
 * 以撑出与 M3 一致的尺寸/位置与点击语义；否则回退 M3 FilterChip。
 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val nativeHost = LocalLiquidGlassWidgetHost.current
    if (enabled && rememberLiquidGlassActive() && nativeHost != null) {
        // 芯片常置于 LazyRow 等横向滚动容器内。若交给原生层消费触摸，横向滑动手势会被吞掉，
        // 因此这里采用「原生 draw-only + Compose clickable」：
        //  - nativeGlassWidget(interactive = false)：原生只绘制玻璃外观，不参与命中/事件；
        //  - Compose clickable：负责点击语义（indication=null，视觉完全由原生玻璃呈现）。
        // 这样既可横向滑动，又能正常点选。
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .nativeGlassWidget(
                    kind = LiquidGlassWidgetKind.Button,
                    key = remember { Any() },
                    label = listOf(label),
                    state = emptyList(),
                    textSizeSp = MaterialTheme.typography.labelLarge.fontSize.value,
                    variant = LiquidGlassButtonVariant.Chip,
                    selected = selected,
                    interactive = false,
                ),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .alpha(0f)
                    .padding(horizontal = chipHorizontalPadding(), vertical = chipVerticalPadding()),
            )
        }
        return
    }

    val shape = MaterialTheme.shapes.small
    val outline = LocalControlOutline.current
    val motion = rememberAppMotion()
    val interactionSource = remember { MutableInteractionSource() }
    // 选中态容器色走 effect spring（零过冲，避免闪），选中瞬间的 scale 走 bouncy spring
    // （DampingRatioMediumBouncy + StiffnessLow）——比单纯的颜色渐变多一层物理反馈。
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motion.effectSpec(),
        label = "chipContainer",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motion.effectSpec(),
        label = "chipLabel",
    )
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = motion.bouncySpec(),
        label = "chipScale",
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = selectionScale
                scaleY = selectionScale
            }
            .pressScale(interactionSource, enabled),
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = container,
            labelColor = labelColor,
            selectedContainerColor = container,
            selectedLabelColor = labelColor,
        ),
        border = when {
            outline.enabled -> BorderStroke(outline.widthDp.dp, outlineBrush(outline))
            else -> FilterChipDefaults.filterChipBorder(
                enabled = enabled,
                selected = selected,
            )
        },
        label = { Text(label) },
    )
}

/** 芯片水平内边距：M3 FilterChip 默认为 8dp（起始含 8dp 前导间距），此处取 16dp 保证文字不贴边。 */
@Composable
private fun chipHorizontalPadding() = androidx.compose.ui.unit.Dp(16f)

/** 芯片垂直内边距：M3 FilterChip 默认 8dp。 */
@Composable
private fun chipVerticalPadding() = androidx.compose.ui.unit.Dp(8f)

@Preview(showBackground = true)
@Composable
private fun AppFilterChipPreview() {
    androidx.compose.foundation.layout.Row {
        AppFilterChip(selected = true, onClick = {}, label = "已选中")
        AppFilterChip(selected = false, onClick = {}, label = "未选中")
    }
}
