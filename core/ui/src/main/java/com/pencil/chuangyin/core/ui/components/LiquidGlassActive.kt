package com.pencil.chuangyin.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import com.pencil.chuangyin.core.theme.LiquidGlassRenderer
import com.pencil.chuangyin.core.theme.LocalLiquidGlassBackdrop
import com.pencil.chuangyin.core.theme.LocalLiquidGlassControlsEnabled
import com.pencil.chuangyin.core.theme.LocalLiquidGlassRenderer
import com.pencil.chuangyin.core.theme.LocalLiquidGlassWidgetHost
import com.pencil.chuangyin.core.theme.LocalLiquidGlassWindowView

/**
 * 液态玻璃是否生效：由 AppTheme 下发的「用户开关」决定，并按当前实现方式校验渲染资源是否就绪。
 *
 * - [LiquidGlassRenderer.Cpp]：需要 C++ 原生覆盖层宿主（[LocalLiquidGlassWidgetHost]），
 *   **且当前组合必须位于主窗口内**——原生覆盖层挂在 Activity 窗口上，无法覆盖独立弹窗窗口，
 *   若在其中仍走原生路径，玻璃面/控件会被弹窗窗口整层遮盖 → 表现为「按钮消失 / 透明空洞」。
 *   因此处于独立弹窗窗口（`Dialog` / `AlertDialog` / `ModalBottomSheet` / `Popup`）时自动回退；
 * - [LiquidGlassRenderer.Kyant]：需要 Compose 采样源（[LocalLiquidGlassBackdrop]）。该实现为
 *   Compose 层采样，在独立弹窗窗口内也能正常工作，故不加窗口门控。
 *
 * 该判定用于**控件级**玻璃（按钮/开关/滑条/分段行/输入框）——它们是否交给原生绘制，取决于用户
 * 是否开启控件玻璃，故包含 [LocalLiquidGlassControlsEnabled]。容器玻璃面（Panel）另有
 * [rememberLiquidGlassPanelActive]：其可用性只取决于渲染器能力，不受控件开关影响。
 *
 * 任一资源缺失即返回 false，各控件自动回退 M3 实现，避免出现「透明空洞」。此处只做判定，不参与绘制。
 */
@Composable
fun rememberLiquidGlassActive(): Boolean =
    LocalLiquidGlassControlsEnabled.current && rememberLiquidGlassPanelActive()

/**
 * **容器玻璃面（Panel）**是否可用：只取决于当前渲染器能否绘制，与「控件玻璃」用户开关无关。
 *
 * 与 [rememberLiquidGlassActive] 分开的原因（全局弹窗问题的根因）：
 * 弹窗必须由 Compose 在**窗口内**绘制、再注册为 MODAL 层 Panel 借原生玻璃面「镜面」透出，
 * 才是真 C++ 液态玻璃弹窗。而弹窗常在控件玻璃**尚未开启**时出现（如「开启液态玻璃」的警告弹窗），
 * 若此处沿用控件开关门控，弹窗便退化为 Compose 实心底——即「伪液态玻璃弹窗」。
 *
 * 原生层侧由 `setGlassPanelsAlwaysOn` 配合：控件玻璃关闭时仍放行 Panel 绘制。
 *
 * - [LiquidGlassRenderer.Cpp]：原生库可用且当前处于主窗口（覆盖层只挂在 Activity 窗口上）；
 * - [LiquidGlassRenderer.Kyant]：有 Compose 采样源即可，独立弹窗窗口内同样有效。
 */
@Composable
fun rememberLiquidGlassPanelActive(): Boolean = when (LocalLiquidGlassRenderer.current) {
    LiquidGlassRenderer.Cpp -> !isInSeparateWindow() && LocalLiquidGlassWidgetHost.current != null
    LiquidGlassRenderer.Kyant -> LocalLiquidGlassBackdrop.current != null
}

/**
 * 当前组合是否位于「独立弹窗窗口」（Compose `Dialog`、M3 `AlertDialog` / `ModalBottomSheet`、
 * `Popup` 等）。
 *
 * 判定方式（**全局自动适配**，无需弹窗逐个声明）：把当前组合的窗口根 View 与 [AppTheme] 下发的
 * 主窗口根 View 比对——主窗口根 View 由 [LocalLiquidGlassWindowView] 提供。同一窗口内两者
 * 相同，返回 false；一旦进入独立窗口，其根 View 与主窗口根 View 不同，返回 true。
 *
 * 未提供主窗口根 View（如 Compose Preview、单测、或 AppTheme 之外的组合）时返回 false，
 * 保持既有行为不变，避免误判导致玻璃整体失效。
 */
@Composable
internal fun isInSeparateWindow(): Boolean {
    val host = LocalLiquidGlassWindowView.current ?: return false
    return LocalView.current.rootView !== host.rootView
}
