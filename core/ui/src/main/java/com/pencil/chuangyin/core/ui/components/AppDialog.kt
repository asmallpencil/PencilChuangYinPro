package com.pencil.chuangyin.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.theme.LiquidGlassDialogOrigin
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.theme.LocalLiquidGlassControlsEnabled
import com.pencil.chuangyin.core.theme.LocalLiquidGlassStyle
import com.pencil.chuangyin.core.theme.LocalLiquidGlassWidgetHost

/** 弹窗遮罩不透明度，与 M3 `AlertDialog` 的 scrim 一致。 */
private const val DIALOG_SCRIM_ALPHA = 0.32f

/** 弹窗卡片宽度约束（与 M3 AlertDialog 的 280/560dp 对齐）。 */
private val DIALOG_MIN_WIDTH = 280.dp
private val DIALOG_MAX_WIDTH = 560.dp

/**
 * 全局**窗口内**弹窗宿主状态。
 *
 * 为什么需要它：Compose 的 `Dialog` / M3 `AlertDialog` 会创建**独立窗口**（TYPE_APPLICATION + 自带
 * 全屏 scrim），而 C++ 原生液态玻璃覆盖层（[com.pencil.chuangyin.pro.liquid.LiquidGlassView]）通过
 * `addContentView` 挂在 **Activity 窗口**上：
 *
 * - 独立弹窗窗口在 WindowManager 中高于 Activity 窗口 → 覆盖层（含其上的原生控件文字）被整体盖住；
 * - M3 容器为不透明 `surfaceContainerHigh` → 呈暗色实心矩形，非玻璃质感；
 * - 若弹窗内的按钮走原生文字（`AppTextButton` 的 glass 分支把文字完全交给原生绘制），被盖住后
 *   **必然 100% 不可见** —— 这正是「按钮消失」的成因。
 *
 * 因此弹窗必须**回到 Activity 窗口内**绘制：内容由 Compose 绘制 → 被 PixelCopy 采集成 backdrop →
 * 由 MODAL 层的原生玻璃面板以「镜面」方式透出（面板输出 = 采样到的窗口内容经色调映射，blur 默认 0
 * 保证文字锐利）。这也是顶部导航栏等既有玻璃面所用的同一套机制。
 */
@Stable
class AppDialogHostState {
    internal val entries = mutableStateListOf<AppDialogEntry>()

    /** 是否有弹窗可见：宿主据此设置原生层的触摸拦截，并触发一次 backdrop 立刻重采。 */
    val hasVisibleDialog: Boolean get() = entries.isNotEmpty()
}

/** 单个弹窗条目：内容与关闭回调每帧刷新，保证读到调用点的最新闭包与状态。 */
@Stable
internal class AppDialogEntry {
    var onDismissRequest: () -> Unit by mutableStateOf({})
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
}

/**
 * 弹窗宿主的注入点。由宿主 Activity 在根节点提供；[AppAlertDialog] 读取它以把弹窗提升到根节点绘制。
 * 未提供时（预览 / 非宿主环境）自动回退到 M3 [AlertDialog]。
 */
val LocalAppDialogHost = staticCompositionLocalOf<AppDialogHostState?> { null }

/**
 * 全局弹窗宿主：挂在根节点、位于全部页面内容之上（但在原生玻璃覆盖层之下）。
 * 宿主只需挂载一次，此后任何 [AppAlertDialog] 都会自动提升到这里渲染 —— 新增弹窗无需逐个适配。
 */
@Composable
fun AppDialogHost(state: AppDialogHostState, modifier: Modifier = Modifier) {
    if (state.entries.isEmpty()) return
    Box(modifier = modifier.fillMaxSize()) {
        state.entries.forEach { entry ->
            key(entry) { AppDialogLayer(entry) }
        }
    }
}

/** 单层弹窗：全屏遮罩（点击/返回关闭）+ 居中卡片；卡片区域吞掉点击，避免穿透遮罩。 */
@Composable
private fun AppDialogLayer(entry: AppDialogEntry) {
    BackHandler(enabled = true) { entry.onDismissRequest() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = DIALOG_SCRIM_ALPHA))
            .pointerInput(entry) { detectTapGestures { entry.onDismissRequest() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.pointerInput(entry) {
                // 空手势：仅消费卡片区域的点击，使其不外泄到遮罩（点卡片不关闭）。
                detectTapGestures { }
            },
        ) {
            entry.content?.invoke()
        }
    }
}

/**
 * 全局弹窗：**真 C++ 液态玻璃**卡片 + Compose 绘制的窗口内内容。
 *
 * 与 M3 [AlertDialog] 参数同形，可直接替换调用点。宿主缺失时回退到 M3 [AlertDialog]。
 *
 * @param onDismissRequest 遮罩点击 / 返回键触发。
 * @param confirmButton 确认按钮（右起第二，最右为确认）。
 * @param modifier 卡片附加修饰符。
 * @param dismissButton 取消按钮（可选，位于确认按钮左侧）。
 * @param icon 顶部图标槽（可选）。
 * @param title 标题槽（可选）。
 * @param text 正文槽（可选）。
 * @param shape 卡片形状；圆角会换算为原生玻璃面板的物理像素半径。
 * @param containerColor 玻璃不可用时的回退底色（形状随 [shape]）。
 * @param iconContentColor / titleContentColor / textContentColor 各槽的内容色上下文。
 * @param tonalElevation 仅用于与 M3 签名对齐（玻璃面不依赖色调阴影）。
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = 6.dp,
) {
    val host = LocalAppDialogHost.current
    if (host == null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
        )
        return
    }

    val entry = remember { AppDialogEntry() }
    entry.onDismissRequest = onDismissRequest
    entry.content = {
        AppAlertDialogCard(
            modifier = modifier,
            icon = icon,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
        )
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** 弹窗卡片：MODAL 层原生玻璃底 + Compose 内容（标题/正文/按钮全部在窗口内绘制）。 */
@Composable
private fun AppAlertDialogCard(
    modifier: Modifier,
    icon: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?,
    shape: Shape,
    containerColor: Color,
    iconContentColor: Color,
    titleContentColor: Color,
    textContentColor: Color,
) {
    // 关键门控：只有「用户开启液态玻璃控件 + 渲染器可用」时才注册原生面板。
    // 此前只判断渲染器能力（`rememberLiquidGlassPanelActive`），导致控件开关关闭时弹窗
    // 仍呈现玻璃效果——用户明确要求「未开启液态玻璃控件时，弹窗不得玻璃化」，故此处
    // 改用 `rememberLiquidGlassActive`（含控件开关门控）。
    // - 控件关闭 / 渲染器不可用 / 处于独立弹窗窗口 → 走 Compose 实心卡片（fallbackColor）；
    // - glassOn=false 时原生面板会退化为不透明 fallback 表面并整块盖住 Compose 内容（按钮再次消失），
    //   因此必须在此一并排除。
    val glassUsable = rememberLiquidGlassActive() && LocalLiquidGlassStyle.current.glassOn

    LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
        // 弹窗内容必须由 Compose 在窗口内绘制：原生覆盖层恒在窗口之上，其 ImGui 文字不在 backdrop 中，
        // 被 MODAL 玻璃面板压住即永久不可见。这里关掉控件级玻璃，让 AppTextButton / AppTextField
        // 等全部回退到 Compose 绘制（从而被 backdrop 采集、经镜面透出、可点击）。
        CompositionLocalProvider(LocalLiquidGlassControlsEnabled provides false) {
            Column(
                modifier = modifier
                    .widthIn(min = DIALOG_MIN_WIDTH, max = DIALOG_MAX_WIDTH)
                    .nativeGlassSurface(
                        shape = shape,
                        enabled = glassUsable,
                        fallbackColor = containerColor,
                    )
                    .padding(24.dp),
            ) {
                if (icon != null) {
                    CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) { icon() }
                    }
                }
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            Box(modifier = Modifier.padding(bottom = 16.dp)) { title() }
                        }
                    }
                }
                if (text != null) {
                    CompositionLocalProvider(LocalContentColor provides textContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/**
 * 全局弹窗（**原生优先**）：开启「液态玻璃控件」后，整块弹窗替换为**真 C++ 液态玻璃弹窗**。
 *
 * 门控 = [rememberLiquidGlassActive]（用户开启液态玻璃控件 + C++ 宿主可用 + 位于主窗口）：
 * - **开启**：调用原生 `lgx::Modal`（liblgm.so 内已编译的液态玻璃弹窗）——玻璃卡片、遮罩、
 *   标题/正文/取消/确认按钮全部由 C++ 逐帧自绘并通过 GPU 渲染，**不是** Compose 仿制。
 *   点击遮罩 / 返回键 / 取消键 → 关闭结果 1（取消）；确认键 → 关闭结果 2（确定）。
 * - **未开启**：回退到 [AppAlertDialog]（既有 Compose 弹窗，视觉与行为保持不变）。
 *
 * 弹出动画（库内置两种，均可用）：
 * - 不传 [origin]：从**屏幕中心**长出（库 `Modal::open`）；
 * - 传有效 [origin]（一般取触发按钮的窗口像素矩形）：从**按钮位置**长出（库 `Modal::openFrom`）。
 *
 * 限制：原生弹窗仅承载「纯文本 + 取消/确认按钮」。含图标 / 图片 / 输入框等富内容的弹窗请继续
 * 使用 [AppAlertDialog]（其内容无法由原生弹窗表达）。
 *
 * @param onDismissRequest 点击遮罩 / 返回键触发。
 * @param title 标题（纯文本）。
 * @param message 正文（纯文本，原生层自动换行）。
 * @param confirmLabel 确认按钮文案。
 * @param onConfirm 确认按钮回调。
 * @param dismissLabel 取消按钮文案；**null 表示单按钮弹窗**（仅确认，确认键占满整行）。
 * @param onDismiss 取消按钮回调；为 null 时回退到 [onDismissRequest]。
 * @param origin 弹出动画起点矩形；null/无效时从屏幕中心弹出。
 */
@Composable
fun AppLiquidDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    origin: LiquidGlassDialogOrigin? = null,
) {
    val host = LocalLiquidGlassWidgetHost.current
    if (host != null && rememberLiquidGlassActive()) {
        // 原生路径：不组合任何 Compose 内容（弹窗完全由 C++ 绘制），仅做桥接与回调。
        val confirmState = rememberUpdatedState(onConfirm)
        val dismissState = rememberUpdatedState(onDismiss)
        val dismissRequestState = rememberUpdatedState(onDismissRequest)
        val showCancel = dismissLabel != null
        val originValue = origin?.takeIf { it.isValid }
        DisposableEffect(host, title, message, confirmLabel, dismissLabel, showCancel, originValue) {
            host.onGlassDialogResult = { code ->
                if (code == 2) {
                    confirmState.value()
                } else {
                    dismissState.value?.invoke() ?: dismissRequestState.value()
                }
            }
            host.showGlassDialog(
                title = title,
                message = message,
                confirmLabel = confirmLabel,
                cancelLabel = dismissLabel.orEmpty(),
                showCancel = showCancel,
                originLeft = originValue?.left ?: 0f,
                originTop = originValue?.top ?: 0f,
                originRight = originValue?.right ?: 0f,
                originBottom = originValue?.bottom ?: 0f,
            )
            onDispose {
                host.onGlassDialogResult = null
                // 结果已在原生侧回收时 isGlassDialogVisible() 为 false，此处不会重复关闭。
                if (host.isGlassDialogVisible()) host.closeGlassDialog(1)
            }
        }
        // 返回键 → 关闭原生弹窗（取消）。原生层收不到系统返回键，必须由 Compose 转发。
        BackHandler(enabled = true) { host.closeGlassDialog(1) }
        return
    }

    // 回退路径：既有 Compose 弹窗（含 MODAL 层原生玻璃面）。
    LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
        AppAlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            title = { AppText(text = title, style = MaterialTheme.typography.titleMedium) },
            text = { AppText(text = message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { AppTextButton(text = confirmLabel, onClick = onConfirm) },
            dismissButton = dismissLabel?.let { label ->
                {
                    AppTextButton(
                        text = label,
                        onClick = { onDismiss?.invoke() ?: onDismissRequest() },
                    )
                }
            },
        )
    }
}
