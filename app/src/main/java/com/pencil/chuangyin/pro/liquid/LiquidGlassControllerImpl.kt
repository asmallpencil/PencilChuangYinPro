package com.pencil.chuangyin.pro.liquid

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.theme.LiquidGlassButtonVariant
import com.pencil.chuangyin.core.theme.LiquidGlassWidgetHost
import com.pencil.chuangyin.core.theme.LiquidGlassWidgetKind

/**
 * [LiquidGlassWidgetHost] 的 app 侧实现：把 Compose 控件桥接到 **C++ 原生液态玻璃层**
 * （[LiquidGlassView] / LiquidGlass-Cpp）。
 *
 * 职责：
 * 1. 为每个控件分配稳定 id（key → id），在布局期读取控件的**窗口物理像素**坐标，减去覆盖层
 *    视图自身的窗口偏移后上报原生层（原生层 1 库单位 = 1 物理像素，故无需 density 换算）；
 * 2. 缓存文案/状态，仅在内容变化时调用原生 update 接口，几何/结构变化才重新注册（避免打断动画）；
 * 3. 接管 [LiquidGlassView.onWidgetEvent]，把原生事件解码回控件语义
 *    （Toggle=0/1、Slider=真实值、Segmented=选中下标、Button=点击）后分发给 Compose 回调。
 *
 * 全部状态只在主线程读写（组合 / 布局 / [LiquidGlassView.onWidgetEvent] 均为主线程回调）。
 */
class LiquidGlassControllerImpl(private val view: LiquidGlassView) : LiquidGlassWidgetHost {

    /** 单个控件的桥接状态。 */
    private class Entry(val id: Int) {
        var kind: LiquidGlassWidgetKind? = null
        var box: LiquidGlassView.Rect? = null
        var label: List<String> = emptyList()
        var state: List<Float> = emptyList()
        var iconId: Int = -1
        var segmentedIconIds: IntArray? = null
        var textSizeSp: Float = 0f
        var variant: LiquidGlassButtonVariant = LiquidGlassButtonVariant.Plain
        var selected: Boolean = false
        var icon: ImageBitmap? = null
        var iconUploaded: ImageBitmap? = null
        var cornerRadius: Float = 0f
        /** 已下发给原生层的圆角；null = 尚未注册。用于检测圆角变化需重注册。 */
        var radiusApplied: Float? = null
        var layer: Int = 0
        var layerApplied: Int? = null
        var interactive: Boolean = true
        var interactiveApplied: Boolean? = null
        var callback: ((Float) -> Unit)? = null
        var registered: Boolean = false
    }

    private val entries = HashMap<Any, Entry>()
    private val byId = HashMap<Int, Entry>()
    private val freeIds = ArrayDeque<Int>()
    private var nextId = 1
    private val viewLocation = IntArray(2)

    init {
        view.onWidgetEvent = { id, kind, value -> dispatch(id, kind, value) }
    }

    /**
     * 下发开关（控件层 / 导航栏）。
     *
     * 原生层在控件关闭时会清空控件表，故本地注册标记须一并失效；重新开启时按缓存的
     * 位置与文案重注册，避免「关闭再开启后控件不显示」。
     */
    fun setEnabled(controls: Boolean, navbar: Boolean) {
        if (!controls) {
            entries.values.forEach { invalidateNativeWidget(it) }
        }
        view.setGlassEnabled(controls, navbar)
        if (controls) {
            entries.values.forEach { entry ->
                if (entry.box?.let(::isVisibleOnScreen) == true) register(entry)
            }
        }
    }

    override fun track(kind: LiquidGlassWidgetKind, key: Any): Modifier =
        Modifier
            // 兜底保证占位节点拥有有效尺寸：调用方以 `modifier.nativeGlassWidget(...)` 使用时，
            // 若传入的 modifier 未给出尺寸（如 `Box { AppSwitch(...) }` 里的默认 Modifier），
            // 空的 Box 会测量为 0×0，上报的坐标无效 → 原生层跳过绘制，呈现「控件无头/隐形」。
            // 这里给一个最小尺寸，仅在调用方未指定尺寸时生效（fill 类约束优先，不受影响）。
            .sizeIn(minWidth = GlassPlaceholderMinSize, minHeight = GlassPlaceholderMinSize)
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                view.getLocationInWindow(viewLocation)
                val box = LiquidGlassView.Rect(
                    bounds.left - viewLocation[0],
                    bounds.top - viewLocation[1],
                    bounds.right - viewLocation[0],
                    bounds.bottom - viewLocation[1],
                )
                val entry = entry(key)
                if (entry.kind != kind) {
                    // 类型变化会由原生 upsert 重建控件（旧图标纹理一并释放），本地缓存同步失效。
                    entry.kind = kind
                    invalidateNativeWidget(entry)
                }
                // 完全滚出屏幕的控件注销，避免原生层在覆盖层上留下「幽灵」（原生层恒在 Compose 之上）。
                if (!isVisibleOnScreen(box)) {
                    entry.box = box
                    if (entry.registered) {
                        view.unregisterWidget(entry.id)
                        // 注销会连带释放原生图标纹理与层级，本地缓存必须一并失效，
                        // 否则回屏时 register 会跳过重传，图标永久丢失。
                        invalidateNativeWidget(entry)
                    }
                    return@onGloballyPositioned
                }
                val moved = entry.box != box
                val radiusChanged = entry.radiusApplied != entry.cornerRadius
                entry.box = box
                when {
                    // 未注册，或圆角变化（Panel / Button 的圆角随几何一起下发）→ 重新注册。
                    !entry.registered || radiusChanged -> register(entry)
                    // 仅位置变化 → 增量更新几何，避免打断进行中的动画。
                    moved -> view.updateBox(entry.id, box, entry.cornerRadius)
                }
            }

    override fun bindEvent(key: Any, onEvent: (Float) -> Unit) {
        entry(key).callback = onEvent
    }

    override fun setLayer(key: Any, layer: Int) {
        val entry = entry(key)
        if (entry.layer == layer) return
        entry.layer = layer
        // 已注册的控件立即改层；未注册的待注册时一并下发。
        if (entry.registered) {
            view.setWidgetLayer(entry.id, layer)
            entry.layerApplied = layer
        }
    }

    override fun setInteractive(key: Any, interactive: Boolean) {
        val entry = entry(key)
        if (entry.interactive == interactive) return
        entry.interactive = interactive
        // 已注册的控件立即改交互性；未注册的待注册时一并下发。
        if (entry.registered) {
            view.setWidgetInteractive(entry.id, interactive)
            entry.interactiveApplied = interactive
        }
    }

    override fun sync(
        kind: LiquidGlassWidgetKind,
        key: Any,
        label: List<String>,
        state: List<Float>,
        iconId: Int,
        segmentedIconIds: IntArray?,
        textSizeSp: Float,
        variant: LiquidGlassButtonVariant,
        selected: Boolean,
        icon: ImageBitmap?,
        cornerRadius: Float,
    ) {
        val entry = entry(key)
        if (entry.kind != kind) {
            // 同上：类型变化由原生侧重建控件，本地「已下发」标记须失效。
            entry.kind = kind
            invalidateNativeWidget(entry)
        }
        // 圆角属于「结构」参数（Panel / Button 注册时随几何一起下发），
        // 由 [track] 通过 entry.radiusApplied 比较决定是否重注册，这里仅记录期望值。
        entry.cornerRadius = cornerRadius

        val labelChanged = entry.label != label
        val stateChanged = entry.state != state
        val variantChanged = entry.variant != variant
        val selectedChanged = entry.selected != selected
        val iconIdChanged = entry.iconId != iconId
        val iconChanged = entry.icon !== icon
        // 需要重新注册（而非增量更新）的「结构」变化：图标、字号、滑条量程、分段数量。
        val structureChanged = !sameIcons(entry.segmentedIconIds, segmentedIconIds) ||
            iconIdChanged ||
            entry.textSizeSp != textSizeSp ||
            (kind == LiquidGlassWidgetKind.Slider && sliderRangeChanged(entry.state, state)) ||
            (kind == LiquidGlassWidgetKind.Segmented && entry.label.size != label.size)

        entry.label = label
        entry.state = state
        entry.iconId = iconId
        entry.segmentedIconIds = segmentedIconIds
        entry.textSizeSp = textSizeSp
        entry.variant = variant
        entry.selected = selected
        entry.icon = icon

        if (!entry.registered) {
            if (entry.box?.let(::isVisibleOnScreen) == true) register(entry)
            return
        }
        if (entry.iconId < 0 && iconIdChanged) view.clearButtonIcon(entry.id)
        if (iconChanged) uploadIconIfNeeded(entry)
        if (structureChanged) {
            register(entry)
            return
        }
        when (kind) {
            LiquidGlassWidgetKind.Button -> {
                if (variantChanged) view.setButtonVariant(entry.id, variant.ordinal)
                if (selectedChanged) view.setButtonSelected(entry.id, selected)
                if (labelChanged) view.updateButton(entry.id, label.firstOrNull().orEmpty())
            }

            LiquidGlassWidgetKind.Toggle -> if (stateChanged) {
                view.updateToggle(entry.id, (state.firstOrNull() ?: 0f) >= 0.5f)
            }

            LiquidGlassWidgetKind.Slider -> if (stateChanged) {
                view.updateSlider(entry.id, state.firstOrNull() ?: 0f)
            }

            LiquidGlassWidgetKind.Segmented -> if (stateChanged) {
                view.updateSegmented(entry.id, (state.firstOrNull() ?: 0f).toInt())
            }

            // Panel 无状态，几何（含圆角）变化已走 register/updateBox。
            LiquidGlassWidgetKind.Panel -> Unit
        }
    }

    override fun dispose(key: Any) {
        val entry = entries.remove(key) ?: return
        byId.remove(entry.id)
        if (entry.registered) view.unregisterWidget(entry.id)
        freeIds.addLast(entry.id)
    }

    // ---- 原生液态玻璃模态弹窗：直接委托 [LiquidGlassView]（lgx::Modal 自绘） ----

    override var onGlassDialogResult: ((Int) -> Unit)?
        get() = view.onDialogResult
        set(value) {
            view.onDialogResult = value
        }

    override fun showGlassDialog(
        title: String,
        message: String,
        confirmLabel: String,
        cancelLabel: String,
        showCancel: Boolean,
        originLeft: Float,
        originTop: Float,
        originRight: Float,
        originBottom: Float,
    ) {
        val valid = originRight - originLeft > 1f && originBottom - originTop > 1f
        view.showDialog(
            title,
            message,
            cancelLabel,
            confirmLabel,
            showCancel,
            if (valid) LiquidGlassView.Rect(originLeft, originTop, originRight, originBottom) else null,
        )
    }

    override fun closeGlassDialog(code: Int) {
        view.closeDialog(code)
    }

    override fun isGlassDialogVisible(): Boolean = view.isDialogVisible()

    private fun entry(key: Any): Entry = entries.getOrPut(key) {
        val id = if (freeIds.isEmpty()) nextId++ else freeIds.removeLast()
        Entry(id).also { byId[id] = it }
    }

    private fun register(entry: Entry) {
        val box = entry.box ?: return
        val kind = entry.kind ?: return
        val state = entry.state
        when (kind) {
            LiquidGlassWidgetKind.Button -> {
                view.registerButton(
                    entry.id,
                    box,
                    entry.label.firstOrNull().orEmpty(),
                    entry.iconId,
                    entry.textSizeSp,
                    entry.variant.ordinal,
                    entry.selected,
                    entry.cornerRadius,
                )
                uploadIconIfNeeded(entry)
            }

            LiquidGlassWidgetKind.Toggle -> view.registerToggle(
                entry.id,
                box,
                (state.firstOrNull() ?: 0f) >= 0.5f,
            )

            LiquidGlassWidgetKind.Slider -> view.registerSlider(
                entry.id,
                box,
                state.getOrElse(0) { 0f },
                state.getOrElse(1) { 0f },
                state.getOrElse(2) { 1f },
            )

            LiquidGlassWidgetKind.Segmented -> view.registerSegmented(
                entry.id,
                box,
                entry.label.toTypedArray(),
                entry.segmentedIconIds ?: IntArray(entry.label.size) { -1 },
                (state.firstOrNull() ?: 0f).toInt(),
            )

            // 容器玻璃面：只绘制一块原生液态玻璃底，不参与命中、不产生事件。
            LiquidGlassWidgetKind.Panel -> view.registerPanel(entry.id, box, entry.cornerRadius)
        }
        entry.registered = true
        // 记录本次已下发的圆角，供 [track] 判定「圆角是否变化需重注册」。
        entry.radiusApplied = entry.cornerRadius
        // 注册后立刻补发层级与交互性（原生注册默认为 layer=0 / interactive=true）。
        if (entry.layerApplied != entry.layer) {
            view.setWidgetLayer(entry.id, entry.layer)
            entry.layerApplied = entry.layer
        }
        if (entry.interactiveApplied != entry.interactive) {
            view.setWidgetInteractive(entry.id, entry.interactive)
            entry.interactiveApplied = entry.interactive
        }
    }

    /**
     * 标记「该控件在原生侧已不存在」（注销 / 控件表被清空），使本地缓存失效。
     *
     * 原生 `unregisterWidget` 会 `glDeleteTextures` 释放控件图标纹理并删除该控件，
     * 重新注册时是一个全新的原生控件（图标纹理为空、layer=0、interactive=true）。
     * 因此 [registered] 与一切「已下发」标记（图标位图、layer、interactive、圆角）都必须
     * 归零，否则下一次 [register] 会因缓存命中而跳过下发 —— 表现为「控件看得见、点得动，
     * 但图标消失」（顶部导航栏在分页滑动出屏再回屏时尤其明显）。
     */
    private fun invalidateNativeWidget(entry: Entry) {
        entry.registered = false
        entry.iconUploaded = null
        entry.layerApplied = null
        entry.interactiveApplied = null
        entry.radiusApplied = null
    }

    /**
     * 上传控件图标位图到原生层（仅在位图对象变化时上传一次）。
     *
     * 位图由 Compose 渲染为「白色字形 + alpha」，原生层按控件文字色着色绘制，
     * 因此主题换色无需重新上传。
     */
    private fun uploadIconIfNeeded(entry: Entry) {
        val icon = entry.icon ?: return
        if (entry.iconUploaded === icon) return
        entry.iconUploaded = icon
        view.setButtonIconBitmap(entry.id, icon.asAndroidBitmap())
    }

    /**
     * 解码原生事件并分发。
     *
     * 同时把解码结果回写 [Entry.state]，使随后的重组 [sync] 不会把同一值再推回原生层
     * （避免与正在拖动的原生控件互相「打架」）。
     */
    private fun dispatch(id: Int, kind: Int, value: Int) {
        val entry = byId[id] ?: return
        val callback = entry.callback ?: return
        val decoded = when (kind) {
            1 -> if (value != 0) 1f else 0f
            2 -> {
                val range = view.sliderRange(id)
                val min = range?.getOrNull(0) ?: 0f
                val max = range?.getOrNull(1) ?: 1f
                min + (value / SLIDER_VALUE_MAX) * (max - min)
            }

            3 -> value.toFloat()
            else -> 0f
        }
        when (kind) {
            1 -> entry.state = listOf(decoded)
            2 -> entry.state = listOf(
                decoded,
                view.sliderRange(id)?.getOrNull(0) ?: 0f,
                view.sliderRange(id)?.getOrNull(1) ?: 1f,
            )

            3 -> entry.state = listOf(decoded)
        }
        callback(decoded)
    }

    /** 控件是否与覆盖层可见区域相交（覆盖层尺寸 = 窗口内容尺寸，坐标同为物理像素）。 */
    private fun isVisibleOnScreen(box: LiquidGlassView.Rect): Boolean {
        if (box.right <= box.left || box.bottom <= box.top) return false
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return true
        return box.right > 0f && box.bottom > 0f && box.left < width && box.top < height
    }

    private fun sameIcons(a: IntArray?, b: IntArray?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        return a.contentEquals(b)
    }

    private fun sliderRangeChanged(old: List<Float>, new: List<Float>): Boolean {
        if (old.size < 3 || new.size < 3) return false
        return old[1] != new[1] || old[2] != new[2]
    }

    private companion object {
        /** 原生 Slider 事件值域（24 位量化）。 */
        const val SLIDER_VALUE_MAX = 16777215f

        /**
         * 原生玻璃控件占位节点的最小尺寸（dp）。
         *
         * 仅作「兜底」：调用方已给出尺寸/填充约束时不起作用。用于避免空的占位 Box
         * 被测量为 0×0，导致上报给原生层的矩形无效、控件整体不绘制。
         */
        val GlassPlaceholderMinSize = 1.dp
    }
}
