// FILE: core/ui/src/main/java/com/pencil/chuangyin/core/ui/motion/AppMotion.kt
package com.pencil.chuangyin.core.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pencil.chuangyin.core.theme.LocalMotionPreset
import com.pencil.chuangyin.core.theme.MotionPreset

/**
 * 全局动效体系（MD3 Expressive 的 Spatial / Effect 二分）。
 *
 * **为什么需要它**：M3 把动效分两类——
 * - Spatial（空间型）：位置、尺寸、旋转、Scale 变化。允许轻微过冲（overshoot），
 *   用低刚度 spring 制造"物理惯性"，这是"活力感"的主要来源；
 * - Effect（效果型）：颜色、透明度。**禁止过冲**，否则闪烁刺眼，必须 dampingRatio = 1f。
 *
 * 项目里此前大量出现 `tween(250)` 的硬编码颜色过渡与无参 `spring()`，既不符合二分原则，
 * 也让整体观感死板（匀速线性，无物理反馈）。所有新代码一律通过本文件取动画参数，
 * 不再直接写 `tween(...)` / `spring()`。
 *
 * **预设降级**：读取 [LocalMotionPreset]：
 * - [MotionPreset.Expressive]：允许过冲，Spatial 用低刚度 spring（默认）；
 * - [MotionPreset.Full]：保留 spring 的加速/减速手感，但过冲归零（dampingRatio = 1f）；
 * - [MotionPreset.Reduced]：无障碍/低端机，退化为 90~120ms 的短 tween，不做过冲与位移。
 */
@Stable
class AppMotion(val preset: MotionPreset) {

    /** 是否允许空间型动画过冲（仅 Expressive）。 */
    val allowsOvershoot: Boolean get() = preset == MotionPreset.Expressive

    /** 是否已按要求减弱动效（无障碍 / 低端机）。 */
    val isReduced: Boolean get() = preset == MotionPreset.Reduced

    /**
     * 空间型标准节奏：容器尺寸、位置、展开/收起。Expressive 下带轻微过冲，
     * 刚度取 MediumLow（≈0.25s 收敛），既明显有弹性又不拖沓。
     */
    val spatial: FiniteAnimationSpec<Float> get() = spatialSpec()

    /** 空间型快节奏：按压回弹、指示器位移等需要紧跟手指的场景。 */
    val spatialFast: FiniteAnimationSpec<Float> get() = spatialFastSpec()

    /** 空间型慢节奏：页面转场、大容器形态变化。 */
    val spatialSlow: FiniteAnimationSpec<Float> get() = spatialSlowSpec()

    /** 空间型弹跳：选中态、Chip 切换等需要"可爱感"的场合（过冲更明显）。 */
    val bouncy: FiniteAnimationSpec<Float> get() = bouncySpec()

    /** 效果型标准节奏：颜色、透明度，永不过冲。 */
    val effect: FiniteAnimationSpec<Float> get() = effectSpec()

    /** 效果型快节奏。 */
    val effectFast: FiniteAnimationSpec<Float> get() = effectFastSpec()

    /** 列表项增删/重排的 placement（位移）规格。 */
    val itemPlacement: FiniteAnimationSpec<IntOffset> get() = itemPlacementSpec()

    /** 列表项淡入淡出规格（效果型）。 */
    val itemFade: FiniteAnimationSpec<Float> get() = itemFadeSpec()

    /** 按压缩放目标值：Expressive 更明显，Reduced 完全不做缩放。 */
    val pressScale: Float
        get() = when (preset) {
            MotionPreset.Expressive -> 0.94f
            MotionPreset.Full -> 0.96f
            MotionPreset.Reduced -> 1f
        }

    // ---- 泛型版本：供 slide/expand 等需要 IntOffset / IntSize 的场景使用 ----

    fun <T> spatialSpec(): FiniteAnimationSpec<T> = when (preset) {
        MotionPreset.Expressive -> spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        MotionPreset.Full -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
        MotionPreset.Reduced -> tween(durationMillis = 120, easing = LinearOutSlowInEasing)
    }

    fun <T> spatialFastSpec(): FiniteAnimationSpec<T> = when (preset) {
        MotionPreset.Expressive -> spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
        MotionPreset.Full -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
        MotionPreset.Reduced -> tween(durationMillis = 90, easing = LinearOutSlowInEasing)
    }

    fun <T> spatialSlowSpec(): FiniteAnimationSpec<T> = when (preset) {
        MotionPreset.Expressive -> spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
        MotionPreset.Full -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow)
        MotionPreset.Reduced -> tween(durationMillis = 160, easing = LinearOutSlowInEasing)
    }

    fun <T> bouncySpec(): FiniteAnimationSpec<T> = when (preset) {
        MotionPreset.Expressive -> spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        )
        MotionPreset.Full -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
        MotionPreset.Reduced -> tween(durationMillis = 100, easing = LinearOutSlowInEasing)
    }

    fun <T> effectSpec(): FiniteAnimationSpec<T> = when (preset) {
        MotionPreset.Reduced -> tween(durationMillis = 110, easing = LinearOutSlowInEasing)
        // 效果型必须零过冲：颜色/透明度一旦过冲就会闪烁。
        else -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
    }

    fun <T> effectFastSpec(): FiniteAnimationSpec<T> = when (preset) {
        MotionPreset.Reduced -> tween(durationMillis = 80, easing = LinearOutSlowInEasing)
        else -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
    }

    /**
     * 列表项 placement：dampingRatio 0.8 + StiffnessLow 是社区验证过的"贴心"参数，
     * 删除时相邻项平滑合拢、新增时让位、重排时带惯性滑到位。
     */
    fun <T> itemPlacementSpec(): FiniteAnimationSpec<T> = when (preset) {
        MotionPreset.Expressive -> spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
        MotionPreset.Full -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
        MotionPreset.Reduced -> tween(durationMillis = 120, easing = LinearOutSlowInEasing)
    }

    fun <T> itemFadeSpec(): FiniteAnimationSpec<T> = when (preset) {
        MotionPreset.Reduced -> tween(durationMillis = 90, easing = LinearOutSlowInEasing)
        else -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
    }

    // ---- 进入/退出转场 ----

    /** 页面级进入：轻微上浮 + 淡入（Spatial + Effect 组合），取代默认的死板 fade。 */
    fun pageEnter(): EnterTransition = if (isReduced) {
        fadeIn(tween(durationMillis = 120))
    } else {
        slideInVertically(initialOffsetY = { it / 22 }, animationSpec = spatialSlowSpec<IntOffset>()) +
            fadeIn(tween(durationMillis = 180))
    }

    /** 页面级退出：轻微下沉 + 淡出。 */
    fun pageExit(): ExitTransition = if (isReduced) {
        fadeOut(tween(durationMillis = 100))
    } else {
        slideOutVertically(targetOffsetY = { -it / 40 }, animationSpec = spatialSlowSpec<IntOffset>()) +
            fadeOut(tween(durationMillis = 140))
    }

    /** 二级页面（右侧推入）。 */
    fun pushEnter(): EnterTransition = if (isReduced) {
        fadeIn(tween(durationMillis = 120))
    } else {
        slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = spatialSpec<IntOffset>()) +
            fadeIn(tween(durationMillis = 180))
    }

    /** 二级页面退出（向右推出）。 */
    fun popExit(): ExitTransition = if (isReduced) {
        fadeOut(tween(durationMillis = 100))
    } else {
        slideOutHorizontally(targetOffsetX = { it / 6 }, animationSpec = spatialFastSpec<IntOffset>()) +
            fadeOut(tween(durationMillis = 140))
    }

    /** 展开进入：竖向撑开（弹性），用于可展开卡片 / 设置项 / 提示条。 */
    fun expandEnter(): EnterTransition = if (isReduced) {
        fadeIn(tween(durationMillis = 100))
    } else {
        expandVertically(expandFrom = androidx.compose.ui.Alignment.Top, animationSpec = spatialSpec<IntSize>()) +
            fadeIn(tween(durationMillis = 140))
    }

    /** 收起退出。 */
    fun shrinkExit(): ExitTransition = if (isReduced) {
        fadeOut(tween(durationMillis = 90))
    } else {
        shrinkVertically(shrinkTowards = androidx.compose.ui.Alignment.Top, animationSpec = spatialFastSpec<IntSize>()) +
            fadeOut(tween(durationMillis = 110))
    }

    /** 横向展开（如横向弹出的标签行）。 */
    fun expandHorizontalEnter(): EnterTransition = if (isReduced) {
        fadeIn(tween(durationMillis = 100))
    } else {
        expandHorizontally(expandFrom = androidx.compose.ui.Alignment.Start, animationSpec = spatialSpec<IntSize>()) +
            fadeIn(tween(durationMillis = 140))
    }

    fun shrinkHorizontalExit(): ExitTransition = if (isReduced) {
        fadeOut(tween(durationMillis = 90))
    } else {
        shrinkHorizontally(shrinkTowards = androidx.compose.ui.Alignment.Start, animationSpec = spatialFastSpec<IntSize>()) +
            fadeOut(tween(durationMillis = 110))
    }

    /** 弹窗/对话框出现：轻微放大 + 淡入（M3 的弹出观感）。 */
    fun dialogEnter(): EnterTransition = if (isReduced) {
        fadeIn(tween(durationMillis = 120))
    } else {
        scaleIn(initialScale = 0.92f, animationSpec = spatialFastSpec<Float>()) + fadeIn(tween(durationMillis = 140))
    }

    fun dialogExit(): ExitTransition = if (isReduced) {
        fadeOut(tween(durationMillis = 100))
    } else {
        scaleOut(targetScale = 0.96f, animationSpec = spatialFastSpec<Float>()) + fadeOut(tween(durationMillis = 100))
    }
}

/** 读取当前动效偏好并构造 [AppMotion]（同一预设内复用同一实例）。 */
@Composable
fun rememberAppMotion(): AppMotion {
    val preset = LocalMotionPreset.current
    return remember(preset) { AppMotion(preset) }
}

/**
 * 按压反馈：手指按下时缩到 [pressedScale]，松开时用 sprang 回弹。
 *
 * 只在 M3 回退路径使用——C++ 原生玻璃走系统级覆盖层绘制，
 * Compose 侧的 graphicsLayer 变换无法作用于原生纹理，强行加会"点了没反应"。
 *
 * @param interactionSource 由调用方传入（必须与按钮自身的 interactionSource 一致）。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = rememberAppMotion().pressScale,
): Modifier {
    val motion = rememberAppMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed && enabled) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        // 按下要"跟手"（短 tween），松开才交给 spring 回弹，避免按下时出现拖影。
        animationSpec = if (pressed) tween(durationMillis = 90) else motion.spatialFastSpec(),
        label = "appPressScale",
    )
    if (!enabled || motion.isReduced) return this
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 列表项动效：增删/重排全部走 spatial spring。
 *
 * 用法（注意调用时 **不要** 写成 `Modifier.animateItemMotion()`，本函数没有 Modifier 分发接收者）：
 * ```
 * val motion = rememberAppMotion()
 * items(list, key = { it.id }) { item -> Row(animateItemMotion(motion)) }
 * ```
 */
fun LazyItemScope.animateItemMotion(motion: AppMotion): Modifier =
    Modifier.animateItem(
        fadeInSpec = motion.itemFade,
        placementSpec = motion.itemPlacement,
        fadeOutSpec = motion.itemFade,
    )

/**
 * 网格（[androidx.compose.foundation.lazy.grid.LazyVerticalGrid]）版本的列表项动效。
 *
 * `LazyGridItemScope` 与 `LazyItemScope` 是两个独立的接收者，`animateItem` 各有声明，
 * 所以必须单独提供一份——否则 P 站网格这类"主战场"反而没有增删/重排动效。
 */
fun LazyGridItemScope.animateGridItemMotion(motion: AppMotion): Modifier =
    Modifier.animateItem(
        fadeInSpec = motion.itemFade,
        placementSpec = motion.itemPlacement,
        fadeOutSpec = motion.itemFade,
    )
