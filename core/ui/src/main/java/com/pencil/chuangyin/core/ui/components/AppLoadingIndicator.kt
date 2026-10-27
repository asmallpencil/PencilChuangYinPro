// FILE: core/ui/src/main/java/com/pencil/chuangyin/core/ui/components/AppLoadingIndicator.kt
package com.pencil.chuangyin.core.ui.components

import android.graphics.Matrix
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Cubic
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.transformed
import com.pencil.chuangyin.core.theme.MotionPreset
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * M3 Expressive 风格加载指示器（morphing loading indicator）。
 *
 * **为什么不转圈**：Material 3 Expressive 明确用「形状持续形变 + 缓慢旋转」替换了 MD2 的
 * [androidx.compose.material3.CircularProgressIndicator]——转圈是一个封闭的匀速循环，
 * 大脑读不到「进度在推进」；而形状每 650ms 完成一次形变、整体 4.6s 转一圈，
 * 每次形变恰好转过 1/7 圈，形成「有节律的生命感」。
 *
 * 实现：用 `androidx.graphics:graphics-shapes` 的 [RoundedPolygon] 定义 7 个**顶点数相同**
 * 的形状（顶点数必须一致，[Morph] 才能逐段插值），每 650ms 在相邻两形状间插值，
 * 同时叠加线性旋转。旧设备 / Reduced 动效预设下退化为「慢速形变 + 旋转减半」，仍然不是死板转圈。
 *
 * @param color 指示器颜色，默认跟随 [LocalContentColor]。
 * @param size 指示器视觉边长（无容器版本）。
 */
@Composable
fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    size: Dp = 44.dp,
) {
    val motion = rememberAppMotion()
    val shapes = remember { LoadingShapes.polygons }
    // 形变进度：0..7 连续推进，取整段决定当前形变的两端。
    val morphTicks = if (motion.preset == MotionPreset.Reduced) 1100 else LoadingShapes.MorphMillis
    val progress by rememberInfiniteTransition(label = "appLoadingMorph").animateFloat(
        initialValue = 0f,
        targetValue = LoadingShapes.StepCount.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = morphTicks * LoadingShapes.StepCount,
                easing = LinearEasing,
            ),
        ),
        label = "appLoadingMorphProgress",
    )
    val spinMillis = if (motion.preset == MotionPreset.Reduced) 9000 else LoadingShapes.SpinMillis
    val spin by rememberInfiniteTransition(label = "appLoadingSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = spinMillis, easing = LinearEasing),
        ),
        label = "appLoadingSpinAngle",
    )
    Canvas(modifier = modifier.size(size)) {
        drawMorphingShape(
            shapes = shapes,
            progress = progress,
            rotationDegrees = spin,
            color = color,
        )
    }
}

/**
 * 带容器的加载指示器：形变图形放在一个主题容器色/形状里，用于需要强视觉锚点的场景
 * （整页空态、弹窗内等待）。容器形状跟随主题拐角（[MaterialTheme.shapes.small]）。
 */
@Composable
fun ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 64.dp,
    indicatorSize: Dp = 30.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        AppLoadingIndicator(color = contentColor, size = indicatorSize)
    }
}

/** 形状集合与时间常量：集中在一处便于整体调速。 */
private object LoadingShapes {

    /** 单次形变时长（与 M3 Expressive 一致：约 650ms）。 */
    const val MorphMillis = 650

    /** 整体旋转周期（4.6s ≈ 7 × 650ms，每次形变恰好转过 1/7 圈）。 */
    const val SpinMillis = 4650

    /**
     * 全部形状统一用 8 个顶点：Morph 要求两端 cubic 数量一致，
     * 顶点数相同即可保证这一点，形状差异只靠半径/圆角/顶点半径起伏表达。
     */
    private const val Vertices = 8

    private fun polygon(radius: Float, rounding: Float, smoothing: Float, rotationDeg: Float): RoundedPolygon {
        val base = RoundedPolygon(
            numVertices = Vertices,
            radius = radius,
            rounding = CornerRounding(rounding, smoothing),
        )
        if (rotationDeg == 0f) return base
        // 库自带的 Matrix 变换（Shapes_androidKt.transformed）比手写 PointTransformer 省事且无类型坑。
        val matrix = Matrix().apply { setRotate(rotationDeg) }
        return base.transformed(matrix)
    }

    /** 顶点半径交替起伏 → 花瓣/星形的观感（仍是同一顶点数，可安全 morph）。 */
    private fun flower(outer: Float, inner: Float, rounding: Float, rotationRad: Float): RoundedPolygon {
        val vertices = FloatArray(Vertices * 2)
        for (i in 0 until Vertices) {
            val angle = (2.0 * PI * i / Vertices) + rotationRad
            val r = if (i % 2 == 0) outer else inner
            vertices[i * 2] = (r * cos(angle)).toFloat()
            vertices[i * 2 + 1] = (r * sin(angle)).toFloat()
        }
        return RoundedPolygon(vertices = vertices, rounding = CornerRounding(rounding, rounding))
    }

    /**
     * 7 个形状：在「圆形 → 圆角多边形 → 花瓣 → 菱形」之间循环，视觉上有明暗/宽窄节奏。
     */
    val polygons: List<RoundedPolygon> by lazy {
        listOf(
            polygon(radius = 1f, rounding = 1.0f, smoothing = 1f, rotationDeg = 0f),
            flower(outer = 1f, inner = 0.72f, rounding = 0.35f, rotationRad = 0.1f),
            polygon(radius = 0.92f, rounding = 0.20f, smoothing = 0.7f, rotationDeg = 22.5f),
            flower(outer = 1f, inner = 0.50f, rounding = 0.30f, rotationRad = 0.35f),
            polygon(radius = 0.85f, rounding = 0.12f, smoothing = 0.6f, rotationDeg = 45f),
            flower(outer = 0.98f, inner = 0.36f, rounding = 0.28f, rotationRad = 0.6f),
            polygon(radius = 0.95f, rounding = 0.45f, smoothing = 0.8f, rotationDeg = 67.5f),
        )
    }

    /** 相邻形状的插值器：每对形状一个 [Morph]，避免每帧重复匹配顶点（那是重活）。 */
    val morphs: List<Morph> by lazy {
        polygons.indices.map { index ->
            Morph(polygons[index], polygons[(index + 1) % polygons.size])
        }
    }

    const val StepCount = 7
}

/**
 * 在当前 [DrawScope] 上绘制一帧形变指示器。
 *
 * @param progress 0..7 的连续进度（7 = 回到第一个形状）。
 * @param rotationDegrees 整体旋转角度。
 */
private fun DrawScope.drawMorphingShape(
    shapes: List<RoundedPolygon>,
    progress: Float,
    rotationDegrees: Float,
    color: Color,
) {
    val stepCount = shapes.size
    val raw = ((progress % stepCount) + stepCount) % stepCount
    val index = raw.toInt() % stepCount
    // 段内线性推进：形状差异与旋转同速，段边界不再有「先停再走」的顿点。
    val local = LinearEasing.transform(raw - index)
    val morph = LoadingShapes.morphs[index]
    val cubics: List<Cubic> = morph.asCubics(local)

    val maxSide = kotlin.math.min(size.width, size.height)
    val path = Path().apply {
        cubics.forEachIndexed { i, cubic ->
            if (i == 0) {
                moveTo(cubic.anchor0X, cubic.anchor0Y)
            }
            cubicTo(
                cubic.control0X, cubic.control0Y,
                cubic.control1X, cubic.control1Y,
                cubic.anchor1X, cubic.anchor1Y,
            )
        }
        close()
    }
    // 形状定义在 [-1, 1] 坐标系内：缩放到画布，居中，再叠旋转。
    val radius = maxSide / 2f * 0.92f
    translate(left = size.width / 2f, top = size.height / 2f) {
        rotate(degrees = rotationDegrees, pivot = Offset.Zero) {
            scale(scaleX = radius, scaleY = radius, pivot = Offset.Zero) {
                drawPath(path = path, color = color)
            }
        }
    }
}

/** 供 DrawScope 复用的 morph 缓存入口（[LoadingShapes.morphs] 已按需 lazy 初始化）。 */

/** 光带宽度占容器宽度的比例：光带与扫过距离都随容器尺寸缩放，因此窄卡片与宽容器观感一致。 */
private const val SkeletonBandFraction = 0.55f

/**
 * 骨架屏占位块（Shimmer）：比任何 spinner 都更能表达「内容正在填充」的结构感，
 * 用于超过 5 秒的等待（列表首屏、详情页加载）。微光扫过用线性渐变 + 无限位移动画。
 *
 * Reduced 动效预设下不启动任何无限动画，直接铺 `surfaceVariant` 静态占位色。
 */
@Composable
fun AppSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val motion = rememberAppMotion()
    if (motion.preset == MotionPreset.Reduced) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "appSkeleton")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
        ),
        label = "appSkeletonSweep",
    )
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                // 梯度几何完全由实测尺寸派生：光带宽度 = 容器宽度 × 比例，
                // 扫过距离 = 容器宽度 + 光带宽度，shimmer 0→1 恰好把光带从左侧外部送到右侧外部。
                val bandWidth = size.width * SkeletonBandFraction
                val travel = size.width + bandWidth
                val startX = shimmer * travel - bandWidth
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(startX, 0f),
                        end = Offset(startX + bandWidth, size.height),
                    ),
                )
            },
    )
}

/** 列表首屏骨架：若干条长短交错的占位（对话列表、收藏列表的通用替代）。 */
@Composable
fun AppSkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 6,
    rowHeight: Dp = 72.dp,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) { index ->
            AppSkeletonBox(
                modifier = Modifier.size(width = if (index % 3 == 0) 220.dp else 300.dp, height = rowHeight),
            )
        }
    }
}

/** 圆形骨架（头像/缩略图）。 */
@Composable
fun AppSkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    AppSkeletonBox(modifier = modifier.size(size), shape = CircleShape)
}
