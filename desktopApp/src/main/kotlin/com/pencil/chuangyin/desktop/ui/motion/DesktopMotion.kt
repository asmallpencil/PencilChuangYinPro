package com.pencil.chuangyin.desktop.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay

/**
 * 桌面端的 MD3 Expressive 动效常量，语义与安卓端 `core-ui/AppMotion` 完全一致：
 *
 * - **Spatial**（位移 / 尺寸 / 缩放）：允许轻微过冲，dampingRatio≈0.72–0.8，负责"有物理惯性"的观感；
 * - **Effect**（颜色 / 透明度）：零过冲，dampingRatio = 1f，保证文字与容器底色过渡不抖。
 *
 * 双端一致是本项目的长期约束，因此这里的参数照抄安卓端，不做桌面端特调。
 */
internal object DesktopMotion {

    /** 常规位移 / 尺寸：略带回弹，长距离移动用。 */
    fun <T> spatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

    /** 快速位移：短距离、贴合点击反馈。 */
    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)

    /** 慢速位移：大面积容器展开 / 收拢。 */
    fun <T> spatialSlow(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)

    /** 明显回弹：选中态、图标放大等"有情绪"的动作。 */
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

    /** 颜色 / 透明度：不过冲。 */
    fun <T> effect(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

    /** 快速颜色 / 透明度。 */
    fun <T> effectFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    /** 列表项淡入淡出。 */
    fun itemFade(): FiniteAnimationSpec<Float> = effectFast()

    /** 列表项增删与重排：带物理惯性的位移。 */
    fun itemPlacement(): FiniteAnimationSpec<IntOffset> = spatial()
}

/** LazyColumn / LazyRow 条目动效（增删重排 + 淡入淡出），对应安卓端 `animateItemMotion`。 */
internal fun LazyItemScope.animateItemMotion(): Modifier =
    Modifier.animateItem(
        fadeInSpec = DesktopMotion.itemFade(),
        placementSpec = DesktopMotion.itemPlacement(),
        fadeOutSpec = DesktopMotion.itemFade(),
    )

/** LazyVerticalGrid 条目动效，对应安卓端 `animateGridItemMotion`。 */
internal fun LazyGridItemScope.animateGridItemMotion(): Modifier =
    Modifier.animateItem(
        fadeInSpec = DesktopMotion.itemFade(),
        placementSpec = DesktopMotion.itemPlacement(),
        fadeOutSpec = DesktopMotion.itemFade(),
    )

/**
 * 按压缩放：按下时轻微内收，抬起时弹簧回弹。
 * 桌面端只在指示层/卡片上使用，避免与鼠标 hover 叠加造成二次位移。
 */
@Composable
internal fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = DesktopMotion.bouncy(),
        label = "desktopPressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 确定进度的弹簧化：进度值变化时带惯性推进，而不是逐帧硬跳。
 * 对齐安卓端下载 / 打印进度的 spring 处理。
 */
@Composable
internal fun animatedProgress(
    target: Float,
    label: String = "desktopAnimatedProgress",
): Float = animateFloatAsState(
    targetValue = target,
    animationSpec = DesktopMotion.spatial(),
    label = label,
).value

/**
 * 入场错峰包装：按 [index] 依次延迟后以「淡入 + 轻微上滑（spatial spring）」出现，
 * 让静态页面在没有用户操作时也保持呼吸感（对齐安卓端 appeared 错峰写法）。
 *
 * @param index 出场序号，决定延迟 = index × [step]
 * @param step 每级延迟毫秒数
 * @param offsetDivisor 上滑起始偏移 = 自身高度 / offsetDivisor（越大越轻微）
 */
@Composable
internal fun StaggerIn(
    index: Int,
    step: Long = 70,
    offsetDivisor: Int = 12,
    content: @Composable () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay(index * step)
        appeared = true
    }
    AnimatedVisibility(
        visible = appeared,
        enter = fadeIn(DesktopMotion.effectFast()) +
            slideInVertically(DesktopMotion.spatialSlow()) { it / offsetDivisor },
    ) { content() }
}
