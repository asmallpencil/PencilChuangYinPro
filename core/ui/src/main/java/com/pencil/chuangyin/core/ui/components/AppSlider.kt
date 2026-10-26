package com.pencil.chuangyin.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.pencil.chuangyin.core.theme.LiquidGlassWidgetKind
import com.pencil.chuangyin.core.theme.LocalLiquidGlassWidgetHost
import com.pencil.chuangyin.resources.R as ResR

/**
 * 全局滑动条：默认使用 M3 [Slider]。
 *
 * 液态玻璃：控件开关开启且原生覆盖层可用时，改用 C++ 原生玻璃滑条
 * （透明占位盒 + 原生绘制与拖动），值经 JNI 回传后转成 [onValueChange]。
 *
 * 注意：M3 [Slider] 支持离散 [steps] 刻度；原生滑条为连续值，且当前无调用点使用
 * [steps]，故原生路径忽略该参数。
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    if (enabled && rememberLiquidGlassActive()) {
        val host = LocalLiquidGlassWidgetHost.current
        if (host != null) {
            val key = remember { Any() }
            val state = remember(value, valueRange) {
                listOf(value, valueRange.start, valueRange.endInclusive)
            }
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(dimensionResource(ResR.dimen.dimen_liquid_glass_slider_height))
                    .nativeGlassWidget(
                        kind = LiquidGlassWidgetKind.Slider,
                        key = key,
                        label = emptyList(),
                        state = state,
                    ) { onValueChange(it) },
            )
            return
        }
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
    )
}

@Preview(showBackground = true)
@Composable
private fun AppSliderPreview() {
    AppSlider(value = 0.5f, onValueChange = {})
}
