package com.pencil.chuangyin.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.pencil.chuangyin.core.theme.LiquidGlassWidgetKind
import com.pencil.chuangyin.core.theme.LocalControlOutline
import com.pencil.chuangyin.core.theme.LocalLiquidGlassWidgetHost
import com.pencil.chuangyin.core.theme.controlOutline
import com.pencil.chuangyin.core.theme.cornerSafeInset
import com.pencil.chuangyin.resources.R as ResR

/**
 * 全局单选分段行：整组外框**一条描边**，形状取 [MaterialTheme.shapes.small]
 * （已由 core:theme 按用户拐角风格/强度全局配置，平行四边/菱形/切角自动跟随）。
 *
 * 所有页面一律使用本组件，禁止直接使用 M3 SegmentedButton——
 * 后者使用固定圆角，不跟随主题拐角，且描边需逐处单独适配。
 *
 * @param options 选项文案。
 * @param selectedIndex 当前选中下标。
 * @param onSelect 选中回调（下标）。
 * @param showCheck 选中项是否显示对勾图标。
 *
 * 液态玻璃：控件开关开启且原生覆盖层可用时，改用 C++ 原生玻璃分段条
 * （透明占位盒 + 原生绘制与按住拖动选择，[showCheck] 由原生路径忽略）。
 */
@Composable
fun AppSegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCheck: Boolean = false,
) {
    if (enabled && options.isNotEmpty() && rememberLiquidGlassActive()) {
        val host = LocalLiquidGlassWidgetHost.current
        if (host != null) {
            val key = remember { Any() }
            val state = remember(selectedIndex) { listOf(selectedIndex.toFloat()) }
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(dimensionResource(ResR.dimen.dimen_global_segment_height))
                    .nativeGlassWidget(
                        kind = LiquidGlassWidgetKind.Segmented,
                        key = key,
                        label = options,
                        state = state,
                    ) { value -> onSelect(value.toInt().coerceIn(0, options.lastIndex)) },
            )
            return
        }
    }
    val shape = MaterialTheme.shapes.small
    // 未开启主题描边时补回 M3 原生细边（开启时由 controlOutline 接管，避免双重描边）。
    val outline = LocalControlOutline.current
    // 形状安全内边距：高档菱形/切角下首尾选项文字会被斜切边裁掉，向内收缩内容区（外框尺寸不变）。
    val safeInset = cornerSafeInset(shapeScale = 0.75f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(ResR.dimen.dimen_global_segment_height))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (!outline.enabled) {
                    Modifier.border(
                        width = dimensionResource(ResR.dimen.dimen_global_border),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .controlOutline(shape)
            .padding(horizontal = safeInset),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_segment_gap)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showCheck && selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_touch_target) * 0.45f),
                        )
                    }
                    AppText(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            selected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppSegmentedRowPreview() {
    AppSegmentedRow(
        options = listOf("左对齐", "居中", "右对齐"),
        selectedIndex = 0,
        onSelect = {},
        showCheck = true,
    )
}
