package com.pencil.chuangyin.feature.camera.result

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import com.pencil.chuangyin.feature.camera.camera.CameraTokens
import com.pencil.chuangyin.resources.R as ResR

/**
 * 裁剪框：四角 + 四边中点共 8 个可拖动手柄。
 *
 * 状态为 4 个归一化角点（TL/TR/BR/BL，0..1），中点由相邻角点推导；
 * 拖边即平移该边两端点。所有拖动带最小边距约束，防止四边形翻转退化。
 *
 * @param contentSize 图片在屏幕上的实际显示尺寸（Fit 后）。
 */
@androidx.compose.runtime.Composable
internal fun CropOverlay(
    points: List<Offset>,
    onPointsChange: (List<Offset>) -> Unit,
    contentSize: Size,
    modifier: Modifier = Modifier,
) {
    val handleTouchR = dimensionResource(ResR.dimen.dimen_camera_crop_touch_radius)
    val cornerR = dimensionResource(ResR.dimen.dimen_camera_crop_handle) / 2
    val strokeW = dimensionResource(ResR.dimen.dimen_camera_crop_stroke)
    var activeHandle by remember { mutableStateOf(-1) }

    // 手势协程在首次组合时启动且不会随 points 更新重启，
    // 必须经 rememberUpdatedState 读取最新角点，否则命中测试与拖动都基于初值。
    val latestPoints by rememberUpdatedState(points)
    val latestOnChange by rememberUpdatedState(onPointsChange)

    Canvas(
        modifier = modifier.pointerInput(contentSize, handleTouchR) {
            val radiusPx = handleTouchR.toPx()

            fun edgeMidsOf(pts: List<Offset>) = listOf(
                (pts[0] + pts[1]) * 0.5f,
                (pts[1] + pts[2]) * 0.5f,
                (pts[3] + pts[2]) * 0.5f,
                (pts[0] + pts[3]) * 0.5f,
            )

            detectDragGestures(
                onDragStart = { start ->
                    val pts = latestPoints
                    // 顺序：角点优先，再边中点；命中最近且在触摸半径内的手柄。
                    val cornerScreen = pts.map {
                        Offset(it.x * contentSize.width, it.y * contentSize.height)
                    }
                    val edgeScreen = edgeMidsOf(pts).map {
                        Offset(it.x * contentSize.width, it.y * contentSize.height)
                    }
                    val cornerHit = cornerScreen.indexOfFirst {
                        (it - start).getDistance() <= radiusPx
                    }
                    activeHandle = if (cornerHit >= 0) {
                        cornerHit
                    } else {
                        val edgeHit = edgeScreen.indexOfFirst {
                            (it - start).getDistance() <= radiusPx
                        }
                        if (edgeHit >= 0) edgeHit + 4 else -1
                    }
                },
                onDragEnd = { activeHandle = -1 },
                onDragCancel = { activeHandle = -1 },
                onDrag = { change, delta ->
                    change.consume()
                    val handle = activeHandle
                    if (handle < 0) return@detectDragGestures
                    val next = latestPoints.toMutableList()
                    val dx = delta.x / contentSize.width
                    val dy = delta.y / contentSize.height
                    when (handle) {
                        0 -> next[0] = clampCorner(next[0], dx, dy, minX = 0f, minY = 0f, maxX = next[2].x, maxY = next[2].y)
                        1 -> next[1] = clampCorner(next[1], dx, dy, minX = next[3].x, minY = 0f, maxX = 1f, maxY = next[2].y)
                        2 -> next[2] = clampCorner(next[2], dx, dy, minX = next[0].x, minY = next[0].y, maxX = 1f, maxY = 1f)
                        3 -> next[3] = clampCorner(next[3], dx, dy, minX = 0f, minY = next[1].y, maxX = next[1].x, maxY = 1f)
                        4 -> { // 上边
                            val y = (next[0].y + dy).coerceIn(0f, next[2].y - MIN_EDGE)
                            next[0] = Offset(next[0].x, y)
                            next[1] = Offset(next[1].x, y)
                        }
                        5 -> { // 右边
                            val x = (next[1].x + dx).coerceIn(next[3].x + MIN_EDGE, 1f)
                            next[1] = Offset(x, next[1].y)
                            next[2] = Offset(x, next[2].y)
                        }
                        6 -> { // 下边
                            val y = (next[3].y + dy).coerceIn(next[0].y + MIN_EDGE, 1f)
                            next[3] = Offset(next[3].x, y)
                            next[2] = Offset(next[2].x, y)
                        }
                        7 -> { // 左边
                            val x = (next[0].x + dx).coerceIn(0f, next[1].x - MIN_EDGE)
                            next[0] = Offset(x, next[0].y)
                            next[3] = Offset(x, next[3].y)
                        }
                    }
                    latestOnChange(next)
                },
            )
        },
    ) {
        val edgeMid = listOf(
            (points[0] + points[1]) * 0.5f,
            (points[1] + points[2]) * 0.5f,
            (points[3] + points[2]) * 0.5f,
            (points[0] + points[3]) * 0.5f,
        )
        val quadPath = Path().apply {
            moveTo(points[0].x * size.width, points[0].y * size.height)
            lineTo(points[1].x * size.width, points[1].y * size.height)
            lineTo(points[2].x * size.width, points[2].y * size.height)
            lineTo(points[3].x * size.width, points[3].y * size.height)
            close()
        }

        // 框外压暗遮罩（evenOdd：全屏矩形与四边形之差）。
        val mask = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addPath(quadPath)
            fillType = PathFillType.EvenOdd
        }
        drawPath(mask, color = CameraTokens.scrim.copy(alpha = 0.55f))

        drawPath(
            path = quadPath,
            color = CameraTokens.content,
            style = Stroke(width = strokeW.toPx()),
        )

        // 四角实心手柄。
        points.forEach { p ->
            drawCircle(
                color = CameraTokens.content,
                radius = cornerR.toPx(),
                center = Offset(p.x * size.width, p.y * size.height),
            )
        }
        // 四边中点小手柄（描边圆）。
        edgeMid.forEach { p ->
            drawCircle(
                color = CameraTokens.content,
                radius = cornerR.toPx() * 0.6f,
                center = Offset(p.x * size.width, p.y * size.height),
                style = Stroke(width = strokeW.toPx()),
            )
        }
    }
}

private fun clampCorner(
    current: Offset,
    dx: Float,
    dy: Float,
    minX: Float,
    minY: Float,
    maxX: Float,
    maxY: Float,
): Offset = Offset(
    (current.x + dx).coerceIn(minX + if (minX > 0f) MIN_EDGE else 0f, maxX - if (maxX < 1f) MIN_EDGE else 0f)
        .coerceIn(0f, 1f),
    (current.y + dy).coerceIn(minY + if (minY > 0f) MIN_EDGE else 0f, maxY - if (maxY < 1f) MIN_EDGE else 0f)
        .coerceIn(0f, 1f),
)

private const val MIN_EDGE = 0.06f
