package com.pencil.chuangyin.desktop.ui.motion

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 桌面端 M3 Expressive 加载指示器：语义与观感对齐安卓端 `AppLoadingIndicator`。
 *
 * 桌面端拿不到 `androidx.graphics:graphics-shapes`（Android 专用），因此这里不用
 * `RoundedPolygon / Morph`，而是把 7 个形状都表示为「12 个极坐标半径」，
 * 帧间对半径逐点插值，再用 Catmull-Rom 转三次贝塞尔把 12 个点连成**光滑闭合曲线**。
 * 视觉结果与安卓端一致：形状每 650ms 形变一次，整体 4.65s 转一圈，
 * 每次形变恰好转过 1/7 圈，形成有节律的"呼吸感"，而不是死板转圈。
 *
 * @param color 指示器颜色，默认跟随 [LocalContentColor]。
 * @param size 指示器视觉边长（无容器版本）。
 */
@Composable
fun DesktopLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    size: Dp = 44.dp,
) {
    val progress by rememberInfiniteTransition(label = "desktopLoadingMorph").animateFloat(
        initialValue = 0f,
        targetValue = LoadingShapes.STEP_COUNT.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = LoadingShapes.MORPH_MILLIS * LoadingShapes.STEP_COUNT,
                easing = LinearEasing,
            ),
        ),
        label = "desktopLoadingMorphProgress",
    )
    val spin by rememberInfiniteTransition(label = "desktopLoadingSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LoadingShapes.SPIN_MILLIS, easing = LinearEasing),
        ),
        label = "desktopLoadingSpinAngle",
    )
    Canvas(modifier = modifier.size(size)) {
        drawMorphingShape(progress = progress, rotationDegrees = spin, color = color)
    }
}

/** 带容器的加载指示器（整页空态、弹窗内等待等需要强视觉锚点的场景）。 */
@Composable
fun DesktopContainedLoadingIndicator(
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
        DesktopLoadingIndicator(color = contentColor, size = indicatorSize)
    }
}

/** 形状集合与时间常量：集中在一处便于整体调速。 */
private object LoadingShapes {

    /** 单次形变时长（与 M3 Expressive 一致：约 650ms）。 */
    const val MORPH_MILLIS = 650

    /** 整体旋转周期（4.65s ≈ 7 × 650ms，每次形变恰好转过 1/7 圈）。 */
    const val SPIN_MILLIS = 4650

    /** 形状数量：定长，进度在其间循环。 */
    const val STEP_COUNT = 7

    /** 每个形状统一 12 个采样角，保证逐点插值成立。 */
    private const val VERTICES = 12

    /** 由"圆形 → 花瓣 → 圆角多边形 → 尖角"排列，宽窄与明暗交替，避免视觉疲劳。 */
    val radii: List<FloatArray> = listOf(
        // 1. 正圆
        FloatArray(VERTICES) { 1f },
        // 2. 十二瓣花瓣（轻微）
        FloatArray(VERTICES) { if (it % 2 == 0) 1f else 0.86f },
        // 3. 圆角方形（顶点在 45° 方向）
        squareRadii(rounding = 0.42f),
        // 4. 花瓣（更深）
        FloatArray(VERTICES) { if (it % 2 == 0) 1f else 0.72f },
        // 5. 圆角方块（更硬）
        squareRadii(rounding = 0.18f),
        // 6. 横向椭圆 / 药丸
        FloatArray(VERTICES) {
            val angle = 2.0 * PI * it / VERTICES
            0.86f + 0.14f * cos(2 * angle).toFloat()
        },
        // 7. 菱形与圆之间的过渡
        diamondRadii(rounding = 0.3f),
    )

    /** 方块：θ 方向的到边距离 1/max(|cos|,|sin|)，再按 [rounding] 与圆混合。 */
    private fun squareRadii(rounding: Float): FloatArray {
        val raw = FloatArray(VERTICES) { index ->
            val angle = 2.0 * PI * index / VERTICES
            val c = kotlin.math.abs(cos(angle)).toFloat()
            val s = kotlin.math.abs(sin(angle)).toFloat()
            1f / maxOf(c, s)
        }
        val peak = raw.max()
        return FloatArray(VERTICES) { index ->
            val square = raw[index] / peak
            // 归一化后再把最小值抬回来，否则方块看起来比圆小一圈。
            val blended = (1f - rounding) * square + rounding
            blended.coerceIn(0.72f, 1f)
        }
    }

    /** 菱形：θ 方向的到边距离 1/(|cos|+|sin|)，按 [rounding] 与圆混合。 */
    private fun diamondRadii(rounding: Float): FloatArray {
        val raw = FloatArray(VERTICES) { index ->
            val angle = 2.0 * PI * index / VERTICES
            val c = kotlin.math.abs(cos(angle)).toFloat()
            val s = kotlin.math.abs(sin(angle)).toFloat()
            1f / (c + s)
        }
        val peak = raw.max()
        return FloatArray(VERTICES) { index ->
            val diamond = raw[index] / peak
            ((1f - rounding) * diamond + rounding).coerceIn(0.7f, 1f)
        }
    }

    /** 相邻两形状的插值权重缓存：只在真正绘制时逐点算，12 个点开销可忽略。 */
    fun lerp(from: FloatArray, to: FloatArray, fraction: Float): FloatArray =
        FloatArray(VERTICES) { index -> from[index] + (to[index] - from[index]) * fraction }
}

/** 在当前 [DrawScope] 上绘制一帧形变指示器。 */
private fun DrawScope.drawMorphingShape(
    progress: Float,
    rotationDegrees: Float,
    color: Color,
) {
    val shapes = LoadingShapes.radii
    val stepCount = shapes.size
    val raw = ((progress % stepCount) + stepCount) % stepCount
    val index = raw.toInt() % stepCount
    // 段内进度做一次缓入缓出，形变更"呼吸"（M3 的 morph 也是非线性的）。
    val local = FastOutSlowInEasing.transform(raw - index)
    val radii = LoadingShapes.lerp(shapes[index], shapes[(index + 1) % stepCount], local)

    val maxSide = min(size.width, size.height)
    val radius = maxSide / 2f * 0.92f
    val path = smoothClosedPath(radii = radii, radius = radius)

    translate(left = size.width / 2f, top = size.height / 2f) {
        rotate(degrees = rotationDegrees, pivot = Offset.Zero) {
            drawPath(path = path, color = color)
        }
    }
}

/**
 * 把 12 个极坐标点连成光滑闭合曲线：Catmull-Rom 样条转三次贝塞尔。
 * 圆→花瓣→方块的过渡因此始终是连续曲率，不会出现折线感。
 */
private fun smoothClosedPath(radii: FloatArray, radius: Float): Path {
    val count = radii.size
    val points = Array(count) { index ->
        val angle = 2.0 * PI * index / count
        Offset(
            x = (radius * radii[index] * cos(angle)).toFloat(),
            y = (radius * radii[index] * sin(angle)).toFloat(),
        )
    }
    return Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 0 until count) {
            val p0 = points[(i - 1 + count) % count]
            val p1 = points[i]
            val p2 = points[(i + 1) % count]
            val p3 = points[(i + 2) % count]
            val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
            val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }
        close()
    }
}

/**
 * 骨架屏占位块（Shimmer）：用占位形状 + 微光扫过表达"内容正在填充"，
 * 用于超过 5 秒的等待（列表首屏、详情页加载），比任何 spinner 更有结构感。
 */
@Composable
fun DesktopSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val transition = rememberInfiniteTransition(label = "desktopSkeleton")
    val shimmer by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
        ),
        label = "desktopSkeletonSweep",
    )
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(shimmer * 900f - 600f, 0f),
                    end = Offset(shimmer * 900f, 400f),
                ),
            ),
    )
}

/** 列表首屏骨架：若干条长短交错的占位。 */
@Composable
fun DesktopSkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 6,
    rowHeight: Dp = 72.dp,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) { index ->
            DesktopSkeletonBox(
                modifier = Modifier.size(
                    width = if (index % 3 == 0) 220.dp else 300.dp,
                    height = rowHeight,
                ),
            )
        }
    }
}

/** 圆形骨架（头像 / 缩略图）。 */
@Composable
fun DesktopSkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    DesktopSkeletonBox(modifier = modifier.size(size), shape = CircleShape)
}
