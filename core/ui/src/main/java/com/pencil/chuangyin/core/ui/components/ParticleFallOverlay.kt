package com.pencil.chuangyin.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.theme.ParticleType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 全局粒子飘落覆盖层：单 [Canvas] 逐帧绘制，无逐粒子 Composable。
 *
 * - 帧驱动：[LaunchedEffect] 内 [withFrameNanos] 每帧刷新时间戳，Canvas 读取该状态触发重绘；
 * - 运动基于真实时间差 dt（px/ms），不同刷新率下速度一致；
 * - 颜色取 [MaterialTheme.colorScheme]（onSurface/primary/tertiary），
 *   浅色/深色模式与换肤均自动可见，零硬编码颜色；
 * - 类型由 [ParticleType] 决定（雪花/雨滴/落叶/花瓣），新增类型只需扩展 [Particle.draw]。
 */
@Composable
fun ParticleFallOverlay(
    type: ParticleType,
    modifier: Modifier = Modifier,
    /** 飘落速度倍率（1=默认），同步缩放下落、摆动、自转速度。 */
    speedMultiplier: Float = 1f,
    particleCount: Int = when (type) {
        ParticleType.Rain -> RAIN_COUNT
        else -> DEFAULT_COUNT
    },
) {
    val density = LocalDensity.current
    val baseRadius = with(density) { 3f.dp.toPx() }
    // 主题感知配色：深色模式浅色粒子、浅色模式深色粒子，任何主题下均可见。
    val mainColor = MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.primary
    val particles = remember(particleCount, type) { Array(particleCount) { Particle(type) } }

    // 帧时钟：每帧刷新时间戳，驱动 Canvas 重绘。
    // 环境粒子封顶 ~30fps：高刷屏（90/120Hz）下减少 2/3 以上主线程绘制，
    // 运动基于真实 dt，限速不影响下落速度观感。
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var lastDispatchedNanos = 0L
        while (true) {
            val vsyncNanos = withFrameNanos { it }
            if (vsyncNanos - lastDispatchedNanos >= FRAME_INTERVAL_CAP_NS) {
                lastDispatchedNanos = vsyncNanos
                frameNanos = vsyncNanos
            }
        }
    }

    // graphicsLayer 形成独立 RenderNode：本层逐帧失效不会带着下层（P 站网格/Coil 图片）
    // 整棵树重新记录绘制指令，仅做一次图层合成，滚动网格时显著降低主线程压力。
    Canvas(modifier = modifier.graphicsLayer()) {
        val nowNanos = frameNanos
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        particles.forEach { p ->
            p.step(nowNanos, w, h, speedMultiplier)
            p.draw(this, baseRadius, mainColor, accentColor)
        }
    }
}

private const val DEFAULT_COUNT = 60
private const val RAIN_COUNT = 90

/** 帧间隔上限（纳秒）：约 30fps，环境动画无需跟随屏幕 90/120Hz 全速重绘。 */
private const val FRAME_INTERVAL_CAP_NS = 33_000_000L

/**
 * 单个粒子运动模型（单位均为像素/毫秒，帧率无关）。
 * 公共骨架：垂直下落 + 水平正弦摆动 + 自转；不同 [ParticleType] 仅参数与绘制不同。
 */
private class Particle(private val type: ParticleType) {
    private var x = 0f
    private var y = 0f
    private var vyPxPerMs = 0.05f
    private var swingAmplitudePx = 12f
    private var phase = 0f
    private var phaseSpeed = 0.002f
    private var rot = 0f
    private var vrotPerMs = 0.001f
    private var sizeRatio = 1f
    private var alpha = 0.8f
    private var lastNanos = 0L
    private var initialized = false

    /** 推进一帧：[nowNanos] 为系统纳秒时间，[w]/[h] 为画布尺寸，[speed] 为速度倍率。 */
    fun step(nowNanos: Long, w: Float, h: Float, speed: Float) {
        if (!initialized) {
            reset(w, randomY = true)
            lastNanos = nowNanos
            initialized = true
            return
        }
        val dtMs = ((nowNanos - lastNanos).coerceAtLeast(0L) / 1_000_000f)
            .coerceAtMost(MAX_FRAME_DT_MS)
        lastNanos = nowNanos

        val s = speed.coerceAtLeast(0f)
        y += vyPxPerMs * dtMs * s
        phase += phaseSpeed * dtMs * s
        rot += vrotPerMs * dtMs * s

        // 飘出边界后从顶部重新下落。
        if (y > h + 16f || x < -24f || x > w + 24f) reset(w, randomY = false)
    }

    /** 复位到屏幕顶部随机位置；[randomY]=true 时（首次）纵向均匀散布，避免开场全挤在一条线上。 */
    private fun reset(w: Float, randomY: Boolean) {
        x = Random.nextFloat() * w
        y = if (randomY) Random.nextFloat() * -SCREEN_HEIGHT_MULT else -16f
        phase = Random.nextFloat() * (2f * PI.toFloat())
        rot = Random.nextFloat() * (2f * PI.toFloat())
        when (type) {
            ParticleType.Snow -> {
                vyPxPerMs = 0.03f + Random.nextFloat() * 0.06f
                swingAmplitudePx = 6f + Random.nextFloat() * 14f
                phaseSpeed = 0.001f + Random.nextFloat() * 0.002f
                vrotPerMs = (Random.nextFloat() - 0.5f) * 0.002f
                sizeRatio = 0.6f + Random.nextFloat() * 1.0f
                alpha = 0.45f + Random.nextFloat() * 0.4f
            }
            ParticleType.Rain -> {
                // 雨：快、直、几乎不摆。
                vyPxPerMs = 0.35f + Random.nextFloat() * 0.35f
                swingAmplitudePx = 0f
                phaseSpeed = 0f
                vrotPerMs = 0f
                sizeRatio = 0.7f + Random.nextFloat() * 0.8f
                alpha = 0.3f + Random.nextFloat() * 0.35f
            }
            ParticleType.Leaf -> {
                // 落叶：慢、大幅摇摆、明显自转。
                vyPxPerMs = 0.04f + Random.nextFloat() * 0.05f
                swingAmplitudePx = 18f + Random.nextFloat() * 26f
                phaseSpeed = 0.0012f + Random.nextFloat() * 0.002f
                vrotPerMs = (Random.nextFloat() - 0.5f) * 0.004f
                sizeRatio = 1.2f + Random.nextFloat() * 1.2f
                alpha = 0.5f + Random.nextFloat() * 0.35f
            }
            ParticleType.Petal -> {
                vyPxPerMs = 0.05f + Random.nextFloat() * 0.05f
                swingAmplitudePx = 10f + Random.nextFloat() * 18f
                phaseSpeed = 0.0015f + Random.nextFloat() * 0.002f
                vrotPerMs = (Random.nextFloat() - 0.5f) * 0.003f
                sizeRatio = 0.9f + Random.nextFloat() * 0.9f
                alpha = 0.45f + Random.nextFloat() * 0.4f
            }
        }
    }

    fun draw(scope: DrawScope, baseRadius: Float, mainColor: Color, accentColor: Color) {
        val radius = baseRadius * sizeRatio
        if (radius <= 0f) return
        val centerX = x + sin(phase) * swingAmplitudePx
        when (type) {
            ParticleType.Snow -> drawSnowflake(scope, centerX, radius, mainColor)
            ParticleType.Rain -> drawRaindrop(scope, centerX, radius, accentColor)
            ParticleType.Leaf -> drawLeaf(scope, centerX, radius, accentColor)
            ParticleType.Petal -> drawPetal(scope, centerX, radius, accentColor)
        }
    }

    /** 雪花：六臂 + 臂端小须。 */
    private fun drawSnowflake(scope: DrawScope, centerX: Float, radius: Float, color: Color) {
        val c = color.copy(alpha = alpha)
        val stroke = (radius * 0.25f).coerceAtLeast(0.5f)
        val arms = 6
        for (i in 0 until arms) {
            val angle = rot + i.toFloat() * (2f * PI.toFloat()) / arms
            val dx = cos(angle)
            val dy = sin(angle)
            val endX = centerX + dx * radius
            val endY = y + dy * radius
            scope.drawLine(c, Offset(centerX, y), Offset(endX, endY), strokeWidth = stroke)
            val branchLen = 0.45f * radius
            val sx = -dy
            val sy = dx
            scope.drawLine(
                c,
                Offset(endX, endY),
                Offset(endX + (dx + sx) * branchLen, endY + (dy + sy) * branchLen),
                strokeWidth = stroke * 0.6f,
            )
            scope.drawLine(
                c,
                Offset(endX, endY),
                Offset(endX + (dx - sx) * branchLen, endY + (dy - sy) * branchLen),
                strokeWidth = stroke * 0.6f,
            )
        }
    }

    /** 雨滴：略带倾斜的短线段（倾斜方向固定，营造风感）。 */
    private fun drawRaindrop(scope: DrawScope, centerX: Float, radius: Float, color: Color) {
        val length = radius * 6f
        val slant = length * 0.15f
        scope.drawLine(
            color.copy(alpha = alpha),
            Offset(centerX + slant, y - length / 2f),
            Offset(centerX - slant, y + length / 2f),
            strokeWidth = (radius * 0.3f).coerceAtLeast(0.5f),
        )
    }

    /** 落叶：绕中心自转的椭圆叶片 + 叶脉。 */
    private fun drawLeaf(scope: DrawScope, centerX: Float, radius: Float, color: Color) {
        val c = color.copy(alpha = alpha)
        val leafW = radius * 2.4f
        val leafH = radius * 1.4f
        scope.withTransform({
            rotate(degrees = rot * 180f / PI.toFloat(), pivot = Offset(centerX, y))
        }) {
            drawOval(
                c,
                topLeft = Offset(centerX - leafW / 2f, y - leafH / 2f),
                size = Size(leafW, leafH),
            )
            drawLine(
                c.copy(alpha = alpha * 0.6f),
                Offset(centerX - leafW / 2f, y),
                Offset(centerX + leafW / 2f, y),
                strokeWidth = (radius * 0.15f).coerceAtLeast(0.4f),
            )
        }
    }

    /** 花瓣：绕中心自转的圆胖椭圆 + 浅色内瓣。 */
    private fun drawPetal(scope: DrawScope, centerX: Float, radius: Float, color: Color) {
        val c = color.copy(alpha = alpha)
        val petalW = radius * 2f
        val petalH = radius * 1.5f
        scope.withTransform({
            rotate(degrees = rot * 180f / PI.toFloat(), pivot = Offset(centerX, y))
        }) {
            drawOval(
                c,
                topLeft = Offset(centerX - petalW / 2f, y - petalH / 2f),
                size = Size(petalW, petalH),
            )
            drawOval(
                c.copy(alpha = alpha * 0.5f),
                topLeft = Offset(centerX - petalW / 4f, y - petalH / 4f),
                size = Size(petalW / 2f, petalH / 2f),
            )
        }
    }

    private companion object {
        /** 单帧最大步长（毫秒）：后台切回/卡顿后避免粒子瞬移。 */
        const val MAX_FRAME_DT_MS = 50f

        /** 首次生成时纵向散布的屏幕高度倍数（负值区间）。 */
        const val SCREEN_HEIGHT_MULT = 800f
    }
}
