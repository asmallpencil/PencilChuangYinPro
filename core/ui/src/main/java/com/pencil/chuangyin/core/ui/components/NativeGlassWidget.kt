package com.pencil.chuangyin.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.pencil.chuangyin.core.theme.LiquidGlassButtonVariant
import com.pencil.chuangyin.core.theme.LiquidGlassWidgetKind
import com.pencil.chuangyin.core.theme.LocalLiquidGlassLayer
import com.pencil.chuangyin.core.theme.LocalLiquidGlassWidgetHost

/**
 * 把一个 Compose 控件挂接到 **C++ 原生液态玻璃层**（LiquidGlass-Cpp）：
 *
 * - 宿主存在时返回叠加了「坐标上报」的 [Modifier]，控件本体只作透明占位、不绘制视觉；
 * - 文案/状态每次重组后经 [LiquidGlassWidgetHost.sync] 下发原生层；
 * - 控件离开组合时自动注销；原生交互事件经 [onEvent] 回传。
 * - 层级自动继承 [LocalLiquidGlassLayer]（由 `LiquidGlassLayer` 容器设置），
 *   使抽屉/弹窗等覆盖层自动压住导航栏等低层控件，无需逐个适配。
 *
 * 宿主为 null（原生库不可用 / 未注入）时原样返回 [this]，调用方应回退到 M3 控件。
 *
 * @param key 控件稳定标识（用 `remember` 生成，跨重组保持一致）。
 * @param interactive 是否参与原生交互。false = 原生只绘制玻璃外观，命中与事件交给 Compose
 *                    （用于滚动容器内的控件，避免原生吞掉滚动手势）。
 */
@Composable
internal fun Modifier.nativeGlassWidget(
    kind: LiquidGlassWidgetKind,
    key: Any,
    label: List<String>,
    state: List<Float>,
    iconId: Int = -1,
    segmentedIconIds: IntArray? = null,
    textSizeSp: Float = 0f,
    variant: LiquidGlassButtonVariant = LiquidGlassButtonVariant.Plain,
    selected: Boolean = false,
    icon: ImageBitmap? = null,
    cornerRadius: Float = 0f,
    interactive: Boolean = true,
    liveScene: Boolean = false,
    onEvent: (Float) -> Unit = {},
): Modifier {
    val host = LocalLiquidGlassWidgetHost.current ?: return this
    val layer = LocalLiquidGlassLayer.current
    DisposableEffect(host, key) {
        onDispose { host.dispose(key) }
    }
    SideEffect {
        host.setLayer(key, layer)
        host.setInteractive(key, interactive)
        if (kind != LiquidGlassWidgetKind.Panel) host.bindEvent(key, onEvent)
        host.sync(
            kind, key, label, state, iconId, segmentedIconIds, textSizeSp, variant, selected, icon,
            cornerRadius, liveScene,
        )
    }
    // track 必须置于链尾：Modifier 链中除 tail 之外不会调用 onGloballyPositioned 所在的
    // LayoutModifier 之外的节点测量回调，而调用方常写 `modifier.nativeGlassWidget(...)`
    // 且传入的 modifier 自带 size/height 等布局修饰。若把 track 放在传入 modifier 之前
    // （`host.track(...).then(this)`），则本 Modifier 处于链尾时会成为「最外层」，
    // 其 onGloballyPositioned 上报的将是**调用方 modifier 之前**的尺寸（0×0），
    // 原生层因坐标无效而跳过绘制 → 表现为「开关/拖拉条无头」「按钮隐形」。
    // 故必须保持 `调用方 modifier → ... → track` 的顺序，使上报坐标始终等于控件最终布局。
    return this.then(host.track(kind, key))
}
