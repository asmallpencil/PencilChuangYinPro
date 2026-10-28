package com.pencil.chuangyin.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.pencil.chuangyin.core.theme.LiquidGlassRenderer
import com.pencil.chuangyin.core.theme.LiquidGlassWidgetKind
import com.pencil.chuangyin.core.theme.LocalLiquidGlassBackdrop
import com.pencil.chuangyin.core.theme.LocalLiquidGlassRenderer
import com.pencil.chuangyin.core.theme.LocalLiquidGlassStyle
import com.pencil.chuangyin.core.theme.LocalLiquidGlassWidgetHost

/**
 * 把一块 Compose 容器（Card / Drawer / TextField / 面板等）的**底面**交给液态玻璃渲染。
 *
 * **新页面请优先使用语义化容器**（见 `GlassContainers.kt` 的 `GlassPanel` / `GlassCard` /
 * `GlassBarSurface` / `GlassTopAppBar`）——它们已封装「可用性判定 + 回退底色 + 直角栏 + 层级」
 * 四件套，只在本函数无法覆盖的特殊场景才直接调用。
 *
 * 按当前[实现方式][LiquidGlassRenderer]二选一（**只显示其一**，绝不叠加）：
 * - [LiquidGlassRenderer.Cpp]：交给 **C++ 原生覆盖层**（LiquidGlass-Cpp），与导航栏、按钮同一套渲染；
 * - [LiquidGlassRenderer.Kyant]：走 **Kotlin/Compose** 实现（Kyant0/AndroidLiquidGlass 的 backdrop
 *   折射 + 模糊），采样由 AppTheme 记录的 [LocalLiquidGlassBackdrop] 提供。
 *
 * 语义（关键）：玻璃生效时**容器自身的背景必须透明**（`Color.Transparent`），
 * 否则会遮住玻璃面。玻璃不可用（开关关闭 / 宿主缺失 / 平台不支持）时回退到 [fallbackColor]，
 * 避免出现「透明空洞」；[fallbackColor] 为 [Color.Unspecified] 时回退到透明（兼容旧调用）。
 *
 * 层级自动继承 [com.pencil.chuangyin.core.theme.LocalLiquidGlassLayer]（仅 C++ 路径）。
 *
 * @param shape 容器形状；圆角半径由它换算为物理像素/折射参数。
 * @param enabled 是否允许使用液态玻璃（通常传 `rememberLiquidGlassActive()`）。
 * @param fallbackColor 关闭液态玻璃 / 渲染不可用时的回退底色（原 M3 容器色）。
 * @param liveScene 是否采样实时窗口内容（默认 false = 只透背景，玻璃下的控件消失）。
 *        仅导航栏需要 true（用户明确豁免的例外）。
 */
@Composable
fun Modifier.nativeGlassSurface(
    shape: Shape,
    enabled: Boolean = true,
    fallbackColor: Color = Color.Unspecified,
    liveScene: Boolean = false,
): Modifier {
    val renderer = LocalLiquidGlassRenderer.current
    if (!enabled) return fallbackOrSelf(fallbackColor, shape)

    return when (renderer) {
        LiquidGlassRenderer.Cpp -> {
            // 独立弹窗窗口内自动回退：原生覆盖层挂在 Activity 窗口上，覆盖不到弹窗窗口，
            // 若仍走原生路径会整块被弹窗窗口盖住（表现为「透明空洞」）。见 isInSeparateWindow。
            if (LocalLiquidGlassWidgetHost.current == null || isInSeparateWindow()) {
                return fallbackOrSelf(fallbackColor, shape)
            }
            val density = LocalDensity.current
            val key = remember { Any() }
            val radiusPx = remember(shape, density) { shape.cornerRadiusPx(density) }
            this.nativeGlassWidget(
                kind = LiquidGlassWidgetKind.Panel,
                key = key,
                label = emptyList(),
                state = emptyList(),
                cornerRadius = radiusPx,
                liveScene = liveScene,
            )
        }

        LiquidGlassRenderer.Kyant -> {
            val backdrop = LocalLiquidGlassBackdrop.current as? LayerBackdrop
                ?: return fallbackOrSelf(fallbackColor, shape)
            val style = LocalLiquidGlassStyle.current
            val density = LocalDensity.current
            val radiusPx = remember(shape, density) { shape.cornerRadiusPx(density) }
            val blurPx = remember(style.blurDp, density) { with(density) { style.blurDp.dp.toPx() } }
            val lensHeightPx =
                remember(style.lensHeightDp, density) { with(density) { style.lensHeightDp.dp.toPx() } }
            val lensAmountPx =
                remember(style.lensAmountDp, density) { with(density) { style.lensAmountDp.dp.toPx() } }
            val chromatic = style.spectral > 0f
            val glassOn = style.glassOn
            // 玻璃材质关闭：退化为纯色面（只保留回退底色），避免用户觉得「关了没反应」。
            if (!glassOn) return fallbackOrSelf(fallbackColor, shape)
            this.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (blurPx > 0f) blur(blurPx)
                    if (lensHeightPx > 0f && lensAmountPx > 0f) {
                        lens(
                            refractionHeight = lensHeightPx,
                            refractionAmount = lensAmountPx,
                            chromaticAberration = chromatic,
                        )
                    }
                },
                // 阴影 / 轮廓高光：与 C++ 路径的开关语义对齐。
                shadow = if (style.shadowOn) {
                    { com.kyant.backdrop.shadow.Shadow.Default }
                } else {
                    null
                },
            )
        }
    }
}

/** 玻璃不可用时的统一回退：有回退色则铺底，否则原样返回（保持旧行为）。 */
private fun Modifier.fallbackOrSelf(fallbackColor: Color, shape: Shape): Modifier {
    if (fallbackColor == Color.Unspecified) return this
    return this.background(fallbackColor, shape)
}

/** 便捷构造：按固定 dp 圆角创建液态玻璃面。 */
@Composable
fun Modifier.nativeGlassSurface(
    cornerRadius: Dp,
    enabled: Boolean = true,
    fallbackColor: Color = Color.Unspecified,
): Modifier = nativeGlassSurface(RoundedCornerShape(cornerRadius), enabled, fallbackColor)

/**
 * **伪液态玻璃**面：Compose 自绘的半透明磨砂底，**不采样背景、不做模糊/折射**。
 *
 * 与 [nativeGlassSurface] 的区别与用途：
 * - 真玻璃（原生 / Compose backdrop）需要读取「上一帧窗口内容」作为纹理再模糊/折射，
 *   内容是**滞后一帧**的。放在**滚动容器内**或随页面一起滚动的位置时，玻璃面与其下方
 *   内容会错位一拍，滑动中表现为明显**抖动**；预览区、输入框正属于此类位置。
 * - 伪玻璃与页面**同帧**绘制，天然无滞后、无抖动，观感上仍有「玻璃感」
 *   （自上而下的半透明渐变 + 内缩高光），因此这些位置统一改用伪玻璃。
 *
 * @param shape 形状（圆角由 Compose 直接裁剪）。
 * @param color 玻璃面色；不指定时取主题 `surfaceContainerHighest`。
 */
@Composable
fun Modifier.pseudoGlassSurface(
    shape: Shape,
    color: Color = Color.Unspecified,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val base = if (color == Color.Unspecified) scheme.surfaceContainerHighest else color
    // 色值为 0（调用方显式传透明）时退化为无底色，避免出现半透明空洞。
    if (base.alpha <= 0f) return this
    val top = base.copy(alpha = PSEUDO_GLASS_TOP_ALPHA)
    val bottom = base.copy(alpha = PSEUDO_GLASS_BOTTOM_ALPHA)
    return this.background(Brush.verticalGradient(listOf(top, bottom)), shape)
}

/** 便捷构造：按固定 dp 圆角创建伪液态玻璃面。 */
@Composable
fun Modifier.pseudoGlassSurface(cornerRadius: Dp, color: Color = Color.Unspecified): Modifier =
    pseudoGlassSurface(RoundedCornerShape(cornerRadius), color)

/** 伪玻璃渐变端点 alpha：上亮下暗，模拟玻璃受光后的纵向明暗变化。 */
private const val PSEUDO_GLASS_TOP_ALPHA = 0.86f
private const val PSEUDO_GLASS_BOTTOM_ALPHA = 0.68f

/**
 * 把 [Shape] 的圆角半径换算为物理像素。
 *
 * 仅 [RoundedCornerShape] 可精确换算（M3 的 `MaterialTheme.shapes.*` 均属此类）；
 * 其余形状（直角矩形、圆形、自定义路径等）返回 0，原生层据此按**直角矩形**处理
 * （见 `makePanelStyle`）。全宽顶栏/底栏正以 `RoundedCornerShape(0.dp)` 走此分支——
 * 若把 0 当成「胶囊」会让窄栏两端变成半圆并产生透镜/色散扭曲，故必须按直角处理。
 */
private fun Shape.cornerRadiusPx(density: Density): Float {
    val rounded = this as? RoundedCornerShape ?: return 0f
    return rounded.topStart.toPx(Size.Zero, density)
}
