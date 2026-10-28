package com.pencil.chuangyin.core.theme

import android.view.View
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 原生液态玻璃控件宿主：由 app 模块实现（桥接 LiquidGlassView / LiquidGlass-Cpp）。
 *
 * core:ui 的 App* 控件在「控件液态玻璃」开启时通过 [LocalLiquidGlassWidgetHost] 取得宿主，
 * 以「透明占位 + 上报坐标」的方式把控件交给 **C++ 原生层**绘制与交互——不是 Kotlin 仿制。
 *
 * 交互链路：
 * 1. Compose 侧只做透明占位（保留精确尺寸/位置/滚动行为），不绘制任何控件视觉；
 * 2. 位置经 [track] 返回的 Modifier 上报窗口像素坐标，原生层 1 库单位 = 1 物理像素；
 * 3. 文案/状态经 [sync] 下发；原生层每帧绘制玻璃控件；
 * 4. 触摸落在控件区域时由原生层消费，状态变化经事件回传 → [bindEvent] 注册的回调。
 */
interface LiquidGlassWidgetHost {

    /**
     * 返回一个仅负责「上报控件窗口像素坐标」的 [Modifier]。
     *
     * @param kind 控件类型（与 native_glass.cpp 的 WidgetType 对齐）。
     * @param key 控件稳定标识（跨重组保持一致，用 remember 生成）。
     */
    fun track(kind: LiquidGlassWidgetKind, key: Any): Modifier

    /** 绑定/更新事件回调。值语义：Toggle=0/1；Slider=真实值；Segmented=选中下标；Button=0。 */
    fun bindEvent(key: Any, onEvent: (Float) -> Unit)

    /**
     * 设置控件层级（值越大越靠上）。
     *
     * 原生覆盖层恒在 Compose 之上、且其内部按 layer 排序绘制与命中，因此覆盖层（抽屉、
     * 弹窗等）只需把自身及其子控件的 layer 抬高，即可自动压住导航栏/子导航等低层控件，
     * 无需针对每个页面逐一适配。
     */
    fun setLayer(key: Any, layer: Int)

    /**
     * 设置控件是否参与交互。
     *
     * false = draw-only：原生层只绘制玻璃外观，命中与事件全部交给 Compose。
     * 用于落在滚动容器（LazyRow 等）内的控件——若原生层消费触摸，容器的滚动手势会失效。
     */
    fun setInteractive(key: Any, interactive: Boolean)

    /**
     * 同步控件文案与状态到原生层。
     *
     * @param label Button 文案 / Segmented 各段文案。
     * @param state Toggle=[on]；Slider=[value,min,max]；Segmented=[activeIndex]；Button=空。
     * @param iconId Button 图标（-1 无；库内置矢量图标索引）。
     * @param segmentedIconIds Segmented 各段图标（可为 null）。
     * @param textSizeSp Button 文字大小（sp，<=0 用库默认）。
     * @param variant Button 外观变体（仅 kind=Button 时有意义）。
     * @param selected Button 选中态（芯片选中时以强调色着色）。
     * @param icon Button 图标位图（白色字形 + alpha，Compose 渲染；优先于 iconId）。
     * @param cornerRadius 容器玻璃面（kind=Panel）的圆角半径（物理像素；<=0 由原生层按胶囊处理）。
     */
    fun sync(
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
    )

    /** 注销控件并解绑回调（控件离开组合时调用）。 */
    fun dispose(key: Any)

    // ---- 原生液态玻璃模态弹窗（lgx::Modal 自绘） ----

    /**
     * 原生弹窗关闭结果回调：1=取消（含点击遮罩 / 返回键），2=确定。
     *
     * 设置后即接管后续由 [showGlassDialog] 弹出的原生弹窗；置 null 表示不再需要。
     */
    var onGlassDialogResult: ((Int) -> Unit)?

    /**
     * 弹出一块**真 C++ 液态玻璃弹窗**（库 `lgx::Modal` 逐帧自绘玻璃面与遮罩，
     * 支持标题 / 正文 + 取消 / 确认两按钮）。
     *
     * @param showCancel 是否显示取消按钮；false 时确认键占满整行（单按钮弹窗）。
     * @param originLeft/Top/Right/Bottom 弹出动画起点矩形（一般取触发按钮的窗口像素坐标）。
     *        有效时走 `openFrom`（从按钮位置长出），否则走 `open`（从屏幕中心长出）——
     *        即开源库内置的两种弹出动画。
     */
    fun showGlassDialog(
        title: String,
        message: String,
        confirmLabel: String,
        cancelLabel: String,
        showCancel: Boolean,
        originLeft: Float = 0f,
        originTop: Float = 0f,
        originRight: Float = 0f,
        originBottom: Float = 0f,
    )

    /** 关闭原生弹窗（1=取消 2=确定），播放关闭动画后经 [onGlassDialogResult] 回传。 */
    fun closeGlassDialog(code: Int = 1)

    /** 原生弹窗是否活跃（含弹出/关闭动画期间）。 */
    fun isGlassDialogVisible(): Boolean
}

/**
 * 原生弹窗**弹出动画起点**（窗口像素坐标，一般取触发按钮的矩形）。
 *
 * 传入有效矩形时，原生层用库的 `Modal::openFrom` —— 弹窗从该矩形位置「长出」（第二种弹出动画）；
 * 不传时用 `Modal::open` —— 弹窗从屏幕中心「长出」（第一种弹出动画）。
 */
data class LiquidGlassDialogOrigin(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** 是否构成有效矩形（宽高均大于 1 像素）。 */
    val isValid: Boolean get() = right - left > 1f && bottom - top > 1f
}

/** 原生液态玻璃控件类型（与 native_glass.cpp 的 WidgetType 对齐）。 */
enum class LiquidGlassWidgetKind {
    /** 按钮（PillButton）。 */
    Button,

    /** 开关（Toggle）。 */
    Toggle,

    /** 滑动条（Slider）。 */
    Slider,

    /** 分段选择（TabStrip）。 */
    Segmented,

    /**
     * 容器玻璃面（Card / Drawer / TextField / 面板等）。
     *
     * 只渲染一块原生液态玻璃底，**不产生任何交互事件**、不参与命中测试；
     * 交互仍由覆盖其上的 Compose 内容自行处理。用于把 Compose 伪玻璃容器整体
     * 替换为与导航栏同一套 C++ 液态玻璃渲染。
     */
    Panel,
}

/**
 * Button 通道的外观变体（与 native_glass.cpp 的 ButtonVariant 对齐）。
 *
 * 四种形态复用同一套原生注册/命中/事件链路，仅外观不同。
 */
enum class LiquidGlassButtonVariant {
    /** 常规玻璃按钮（主/次/描边按钮）。 */
    Plain,

    /** 芯片：选中时以强调色实心着色（文字自动转白）。 */
    Chip,

    /** 纯图标按钮：方形占位框 → 胶囊圆角即圆形。 */
    Icon,

    /** 纯文字按钮：不绘制玻璃面，仅文字与按压反馈。 */
    Text,
}

/**
 * 原生液态玻璃控件宿主 CompositionLocal。
 *
 * 默认 null（纯 Compose 回退：使用 M3 控件）。仅当 app 模块注入实现、且用户开启控件液态玻璃时，
 * core:ui 控件才改走原生绘制路径。
 */
val LocalLiquidGlassWidgetHost = compositionLocalOf<LiquidGlassWidgetHost?> { null }

/**
 * 当前液态玻璃实现方式 CompositionLocal。
 *
 * 由 AppTheme 下发，core:ui 据此选择渲染路径：
 * - [LiquidGlassRenderer.Cpp]：走 [LocalLiquidGlassWidgetHost]（C++ 原生覆盖层）；
 * - [LiquidGlassRenderer.Kyant]：走 [LocalLiquidGlassBackdrop]（Compose backdrop 折射/模糊）。
 */
val LocalLiquidGlassRenderer = compositionLocalOf { LIQUID_GLASS_RENDERER_DEFAULT }

/**
 * Compose 液态玻璃采样源（Kyant0/AndroidLiquidGlass 的 layer backdrop）。
 *
 * 由 AppTheme 在背景容器上创建并记录；玻璃面用 `Modifier.drawBackdrop(backdrop, ...)` 采样其内容。
 * 仅当实现方式为 [LiquidGlassRenderer.Kyant] 时非空，其余情况下为 null（回退 M3 底色）。
 */
val LocalLiquidGlassBackdrop = compositionLocalOf<Any?> { null }

/**
 * C++ 原生实现在当前设备是否可用（原生库是否装载成功）。
 *
 * 由宿主（MainActivity）在启动时探测后下发；设置页据此禁用/提示不可用的实现方式，
 * 避免用户选了却看不到效果。与 [LocalLiquidGlassWidgetHost] 不同——后者仅在实际选用
 * C++ 路径时才注入，因此不能用来判断「设备是否支持 C++」。
 */
val LocalLiquidGlassCppAvailable = compositionLocalOf { false }

/**
 * 原生控件层级 CompositionLocal：值越大越靠上。
 *
 * 这是**自动适配接口**：页面/覆盖层只要用 [LiquidGlassLayer] 包一层，其内部所有原生玻璃控件
 * （含后续新增的控件）会自动继承该层级并参与原生层的绘制/命中排序——无需逐个控件手改，
 * 从而让抽屉、弹窗等覆盖层自动压住导航栏/子导航等低层控件，避免层级错乱与重复适配。
 */
val LocalLiquidGlassLayer = compositionLocalOf { LiquidGlassLayer.BASE }

/** 原生玻璃控件的标准层级取值。 */
object LiquidGlassLayer {
    /** 常规内容层（默认）。 */
    const val BASE = 0

    /** 顶部栏 / 子导航栏等常驻 UI。 */
    const val BAR = 10

    /** 覆盖层：抽屉、下拉、气泡等。 */
    const val OVERLAY = 100

    /** 模态层：对话框、底部表单等（应盖住一切）。 */
    const val MODAL = 1000
}

/**
 * 主窗口（Activity 窗口）根 View CompositionLocal，用于**自动识别独立弹窗窗口**。
 *
 * 背景：C++ 原生覆盖层（LiquidGlassView）以 `addContentView` 挂在 Activity 窗口上，其图层
 * 位于 Activity 窗口内容之上、但在**独立窗口**（Compose `Dialog` / M3 `AlertDialog` /
 * `ModalBottomSheet` / `Popup`）之下。若这些独立窗口内仍走 C++ 原生路径，原生绘制的玻璃面与
 * 控件（部分控件文字只由原生层绘制）会被整体遮盖 → 表现为「按钮消失 / 透明空洞」。
 *
 * 由 [AppTheme] 下发主窗口根 View；`core:ui` 据此把「当前组合的窗口根 View」与主窗口根 View
 * 比对，判定是否处于独立弹窗窗口，从而**自动**回退到 Compose 绘制——新增/未来的弹窗无需逐个
 * 适配，天然获得正确行为。
 */
val LocalLiquidGlassWindowView = compositionLocalOf<View?> { null }
