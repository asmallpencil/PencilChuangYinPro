package com.pencil.chuangyin.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.theme.LocalLiquidGlassLayer

/**
 * 原生液态玻璃**层级容器**：把 [layer] 下发给子树内所有原生玻璃控件。
 *
 * 这是自动适配接口——原生覆盖层恒在 Compose 之上、其内部按层级排序绘制与命中，因此
 * 抽屉 / 弹窗等覆盖层只需用它包一层，其内部（含后续新增）的原生控件便会自动压住
 * 导航栏、子导航等低层控件，无需逐页、逐控件手改。
 *
 * 用法：
 * ```
 * LiquidGlassLayerScope(LiquidGlassLayer.OVERLAY) {
 *     PixivDrawerSheet(...)
 * }
 * ```
 *
 * @param layer 层级，取值见 [LiquidGlassLayer]（越大越靠上）。
 */
@Composable
fun LiquidGlassLayerScope(
    layer: Int,
    content: @Composable () -> Unit,
) {
    // 用 remember(layer) 让层级变化时重新下发，确保内部控件在 layer 改变后立即重新上报。
    val provided = remember(layer) { layer }
    CompositionLocalProvider(LocalLiquidGlassLayer provides provided, content = content)
}
