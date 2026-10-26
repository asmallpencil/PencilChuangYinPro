package com.pencil.chuangyin.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.pencil.chuangyin.core.theme.LiquidGlassWidgetKind
import com.pencil.chuangyin.core.theme.LocalControlOutline
import com.pencil.chuangyin.core.theme.LocalLiquidGlassWidgetHost
import com.pencil.chuangyin.resources.R as ResR

/**
 * 全局开关：默认使用 M3 [Switch]。
 * 开关本身依靠轨道/拇指颜色表达状态，不再叠加主题描边（描边沿胶囊外框绘制时
 * 与 M3 Switch 的视觉轨道易出现错位，且开关态辨识度已足够）。
 *
 * 液态玻璃：控件开关开启且原生覆盖层可用时，改用 C++ 原生玻璃开关
 * （透明占位盒 + 原生绘制与触摸），事件经 JNI 回传后转成 [onCheckedChange]。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (enabled && onCheckedChange != null && rememberLiquidGlassActive()) {
        val host = LocalLiquidGlassWidgetHost.current
        if (host != null) {
            val key = remember { Any() }
            val state = remember(checked) { listOf(if (checked) 1f else 0f) }
            Box(
                modifier = modifier
                    .size(
                        width = dimensionResource(ResR.dimen.dimen_liquid_glass_toggle_w),
                        height = dimensionResource(ResR.dimen.dimen_liquid_glass_toggle_h),
                    )
                    .nativeGlassWidget(
                        kind = LiquidGlassWidgetKind.Toggle,
                        key = key,
                        label = emptyList(),
                        state = state,
                        // 回传值为与原生层一致的 0/1，故阈值取二者的中点。
                    ) { value -> onCheckedChange(value >= LiquidGlassToggleMetrics.CheckedThreshold) },
            )
            return
        }
    }
    val outline = LocalControlOutline.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        // 开启主题描边时隐藏 M3 未选中轨道的原生描边，避免多余边框。
        colors = if (outline.enabled) {
            SwitchDefaults.colors(uncheckedBorderColor = Color.Transparent)
        } else {
            SwitchDefaults.colors()
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AppSwitchPreview() {
    AppSwitch(checked = true, onCheckedChange = {})
}

/**
 * 原生液态玻璃开关的回传阈值等**行为常量**。
 *
 * 尺寸（占位框宽高）走 `dimens.xml` 资源（见 `dimen_liquid_glass_toggle_w/h`），
 * 此处只保留无法用资源表达的数值型阈值。
 */
private object LiquidGlassToggleMetrics {
    /** 判定「已开启」的回传阈值：原生层回传 0/1，取中点。 */
    const val CheckedThreshold = 0.5f
}
