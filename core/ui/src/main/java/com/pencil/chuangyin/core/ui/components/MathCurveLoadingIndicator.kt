// FILE: core/ui/src/main/java/com/pencil/chuangyin/core/ui/components/MathCurveLoadingIndicator.kt
package com.pencil.chuangyin.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.theme.LoadingAnimationSpec
import com.pencil.chuangyin.core.theme.LoadingAnimationStyle
import com.pencil.chuangyin.core.theme.LocalLoadingAnimation
import com.pencil.chuangyin.core.theme.LocalMotionPreset
import com.pencil.chuangyin.core.theme.MotionPreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 数学曲线加载动画渲染器：移植自开源项目 `math-curve-loaders`。
 *
 * 每条曲线（[LoadingAnimationStyle]）由若干参数确定几何形状，渲染方式与原项目一致：
 * 1. 一条透明度 0.1 的完整轨迹 path 作为「底稿」；
 * 2. 沿曲线运动的一串粒子构成「尾迹」，越靠尾端越小越淡；
 * 3. 细节缩放 `detail` 随呼吸时长在 0.52~1.0 之间正弦起伏；
 * 4. 部分曲线整体缓慢旋转。
 *
 * 与原项目的差异：原项目用 SVG + requestAnimationFrame，这里用 Compose [Canvas] +
 * `withFrameNanos` 逐帧驱动，坐标仍是 0..100 视口单位，绘制时按实际尺寸等比映射。
 * 动效预设为 [MotionPreset.Reduced] 时停止逐帧动画，退化为一张静态完整轨迹（无障碍要求）。
 */
@Composable
internal fun MathCurveLoadingIndicator(
    modifier: Modifier = Modifier,
    spec: LoadingAnimationSpec = LocalLoadingAnimation.current,
    color: Color = LocalContentColor.current,
    size: Dp = 44.dp,
) {
    val shape = remember(spec.style) { curveShape(spec.style) } ?: return
    val reduced = LocalMotionPreset.current == MotionPreset.Reduced

    var timeMs by remember { mutableLongStateOf(0L) }
    if (!reduced) {
        LaunchedEffect(spec) {
            var start = 0L
            withFrameNanos { start = it }
            while (true) {
                withFrameNanos { now -> timeMs = (now - start) / 1_000_000L }
            }
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / 100f
        val originX = (this.size.width - 100f * unit) / 2f
        val originY = (this.size.height - 100f * unit) / 2f
        val time = if (reduced) 0f else timeMs.toFloat()
        val progress = normalizeProgress(time / spec.durationMs.toFloat())
        val pulseProgress =
            ((time % spec.pulseDurationMs) / spec.pulseDurationMs.toFloat())
        val detail = 0.52f + ((sin(pulseProgress * TWO_PI_F + 0.55f) + 1f) / 2f) * 0.48f
        val rotation = if (!shape.rotate || reduced) {
            0f
        } else {
            -((time % spec.rotationDurationMs) / spec.rotationDurationMs.toFloat()) * 360f
        }

        rotate(degrees = rotation, pivot = center) {
            // 完整轨迹底稿
            val steps = if (unit > 0.4f) 240 else 160
            val trail = Path()
            for (i in 0..steps) {
                val point = shape.point(i / steps.toFloat(), detail)
                val x = originX + point.x * unit
                val y = originY + point.y * unit
                if (i == 0) trail.moveTo(x, y) else trail.lineTo(x, y)
            }
            drawPath(
                path = trail,
                color = color.copy(alpha = color.alpha * 0.1f),
                style = Stroke(
                    width = (spec.strokeWidth * unit).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            // 粒子尾迹
            val particleWidth = (spec.particleCount - 1).coerceAtLeast(1)
            for (index in 0 until spec.particleCount) {
                val tail = index / particleWidth.toFloat()
                val point = shape.point(normalizeProgress(progress - tail * spec.trailSpan), detail)
                val fade = (1f - tail).pow(0.56f)
                val radius = max((0.9f + fade * 2.7f) * unit * 1.35f, 0.55f)
                drawCircle(
                    color = color.copy(alpha = color.alpha * (0.04f + fade * 0.96f)),
                    radius = radius,
                    center = Offset(originX + point.x * unit, originY + point.y * unit),
                )
            }
        }
    }
}

/** 一条曲线的运行时形状：是否需要整体旋转 + 参数化取点。坐标均为 0..100 视口单位。 */
private class CurveShape(
    val rotate: Boolean,
    val point: (progress: Float, detail: Float) -> Offset,
)

private const val TWO_PI = PI * 2
private const val TWO_PI_F = (PI * 2).toFloat()

private fun normalizeProgress(value: Float): Float = ((value % 1f) + 1f) % 1f

private fun of(x: Double, y: Double): Offset = Offset(x.toFloat(), y.toFloat())

/** 曲线索引：与 math-curve-loaders 的 `curves` 数组一一对应。 */
private fun curveShape(style: LoadingAnimationStyle): CurveShape? = when (style) {
    LoadingAnimationStyle.Classic -> null

    // ---- 玫瑰尾迹族：基础圆周 + 倍频余弦细节 ----
    LoadingAnimationStyle.ThinkingRose -> roseTail(petals = 7)
    LoadingAnimationStyle.ThinkingFive -> roseTail(petals = 5)
    LoadingAnimationStyle.ThinkingNine -> roseTail(petals = 9)

    // ---- 玫瑰轨道族：r 随 cos(k t) 起伏 ----
    LoadingAnimationStyle.RoseOrbit -> CurveShape(rotate = true) { progress, detail ->
        val t = progress * TWO_PI
        val k = 7.0
        val r = 7.0 - 2.7 * detail * cos(k * t)
        of(50 + cos(t) * r * 3.9, 50 + sin(t) * r * 3.9)
    }
    LoadingAnimationStyle.RoseTwo -> rosePetal(k = 2)
    LoadingAnimationStyle.RoseThree -> rosePetal(k = 3)
    LoadingAnimationStyle.RoseFour -> rosePetal(k = 4)

    // ---- 李萨如编织 ----
    LoadingAnimationStyle.LissajousDrift -> CurveShape(rotate = false) { progress, detail ->
        val t = progress * TWO_PI
        val amp = 24.0 + detail * 6.0
        of(
            50 + sin(3.0 * t + 1.57) * amp,
            50 + sin(4.0 * t) * (amp * 0.92),
        )
    }

    // ---- 双纽线 ----
    LoadingAnimationStyle.LemniscateBloom -> CurveShape(rotate = false) { progress, detail ->
        val t = progress * TWO_PI
        val scale = 20.0 + detail * 7.0
        val denom = 1.0 + sin(t) * sin(t)
        of(
            50 + scale * cos(t) / denom,
            50 + scale * sin(t) * cos(t) / denom,
        )
    }

    // ---- 内旋轮线 ----
    LoadingAnimationStyle.HypotrochoidLoop -> CurveShape(rotate = false) { progress, detail ->
        val t = progress * TWO_PI
        val bigR = 8.2
        val r = 2.7 + detail * 0.45
        val d = 4.8 + detail * 1.2
        val x = (bigR - r) * cos(t) + d * cos(((bigR - r) / r) * t)
        val y = (bigR - r) * sin(t) - d * sin(((bigR - r) / r) * t)
        of(50 + x * 3.05, 50 + y * 3.05)
    }

    // ---- 花瓣螺旋族：R 决定花瓣数 ----
    LoadingAnimationStyle.SpiralThree -> petalSpiral(bigR = 3.0)
    LoadingAnimationStyle.SpiralFour -> petalSpiral(bigR = 4.0)
    LoadingAnimationStyle.SpiralFive -> petalSpiral(bigR = 5.0)
    LoadingAnimationStyle.SpiralSix -> petalSpiral(bigR = 6.0)

    // ---- 蝴蝶曲线 ----
    LoadingAnimationStyle.ButterflyPhase -> CurveShape(rotate = false) { progress, detail ->
        val t = progress * TWO_PI * 12.0
        val s = exp(cos(t)) - 2.0 * cos(4.0 * t) - sin(t / 12.0).pow(5)
        val scale = 4.6 + detail * 0.45
        of(50 + sin(t) * s * scale, 50 + cos(t) * s * scale)
    }

    // ---- 心形线（尖点在侧 / 正立） ----
    LoadingAnimationStyle.CardioidGlow -> CurveShape(rotate = false) { progress, detail ->
        val t = progress * TWO_PI
        val a = 8.4 + detail * 0.8
        val r = a * (1 - cos(t))
        of(50 + cos(t) * r * 2.15, 50 + sin(t) * r * 2.15)
    }
    LoadingAnimationStyle.CardioidHeart -> CurveShape(rotate = false) { progress, detail ->
        val t = progress * TWO_PI
        val a = 8.8 + detail * 0.8
        val r = a * (1 + cos(t))
        val baseX = cos(t) * r
        val baseY = sin(t) * r
        of(50 - baseY * 2.15, 50 - baseX * 2.15)
    }

    // ---- 心形波纹：x^(2/3) 外轮廓 + sin(bπx) 内部波纹 ----
    LoadingAnimationStyle.HeartWave -> CurveShape(rotate = false) { progress, detail ->
        val xLimit = sqrt(3.3)
        val x = -xLimit + progress * xLimit * 2
        val safeRoot = max(0.0, 3.3 - x * x)
        val wave = 0.9 * sqrt(safeRoot) * sin(6.4 * PI * x)
        val curve = abs(x).pow(2.0 / 3.0)
        val y = curve + wave
        val scaleY = 24.5 + detail * 1.5
        of(50 + x * 23.2, 18 + (1.75 - y) * scaleY)
    }

    // ---- 阿基米德螺线 ----
    LoadingAnimationStyle.SpiralSearch -> CurveShape(rotate = false) { progress, detail ->
        val t = progress * TWO_PI
        val angle = t * 4.0
        val radius = 8.0 + (1 - cos(t)) * (8.5 + detail * 2.4)
        of(50 + cos(angle) * radius, 50 + sin(angle) * radius)
    }

    // ---- 傅里叶波形 ----
    LoadingAnimationStyle.FourierFlow -> CurveShape(rotate = false) { progress, detail ->
        val t = progress * TWO_PI
        val mix = 1.0 + detail * 0.16
        val x = 17.0 * cos(t) + 7.5 * cos(3 * t + 0.6 * mix) + 3.2 * sin(5 * t - 0.4)
        val y = 15.0 * sin(t) + 8.2 * sin(2 * t + 0.25) - 4.2 * cos(4 * t - 0.5 * mix)
        of(50 + x, 50 + y)
    }
}

/** 玫瑰尾迹：基础圆 + petals 倍频余弦细节（Original Thinking / Five / Nine）。 */
private fun roseTail(petals: Int): CurveShape = CurveShape(rotate = true) { progress, detail ->
    val t = progress * TWO_PI
    val k = petals.toDouble()
    val x = 7.0 * cos(t) - 3.0 * detail * cos(k * t)
    val y = 7.0 * sin(t) - 3.0 * detail * sin(k * t)
    of(50 + x * 3.9, 50 + y * 3.9)
}

/** 玫瑰线：r = a·cos(kθ)，a 与呼吸倍率都随 detail 起伏。 */
private fun rosePetal(k: Int): CurveShape = CurveShape(rotate = true) { progress, detail ->
    val t = progress * TWO_PI
    val a = 9.2 + detail * 0.6
    val r = a * (0.72 + detail * 0.28) * cos(k * t)
    of(50 + cos(t) * r * 3.25, 50 + sin(t) * r * 3.25)
}

/** 花瓣螺旋族：R 越大回环越多（Three~Six-Petal Spiral）。 */
private fun petalSpiral(bigR: Double): CurveShape = CurveShape(rotate = true) { progress, detail ->
    val t = progress * TWO_PI
    val smallR = 1.0
    val d = 3.0 + detail * 0.25
    val baseX = (bigR - smallR) * cos(t) + d * cos(((bigR - smallR) / smallR) * t)
    val baseY = (bigR - smallR) * sin(t) - d * sin(((bigR - smallR) / smallR) * t)
    val scale = 2.2 + detail * 0.45
    of(50 + baseX * scale, 50 + baseY * scale)
}
