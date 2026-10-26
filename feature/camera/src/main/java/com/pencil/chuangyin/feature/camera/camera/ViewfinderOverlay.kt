package com.pencil.chuangyin.feature.camera.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.dimensionResource
import com.pencil.chuangyin.feature.camera.LiveQuad
import com.pencil.chuangyin.resources.R as ResR
import kotlin.math.max

/**
 * 取景叠加层：九宫格构图线 + 文档模式实时纸张四边形。
 *
 * 角点为分析帧归一化坐标，按 PreviewView FILL_CENTER 的等比裁剪规则映射：
 * 内容按帧高宽比等比放大到至少铺满视图，居中后两侧/上下溢出裁掉。
 */
@Composable
internal fun ViewfinderOverlay(
    quad: LiveQuad?,
    modifier: Modifier = Modifier,
) {
    val gridStroke = dimensionResource(ResR.dimen.dimen_camera_grid_stroke)
    val quadStroke = dimensionResource(ResR.dimen.dimen_camera_quad_stroke)
    val cornerR = dimensionResource(ResR.dimen.dimen_camera_quad_corner) / 2
    val quadColor = CameraTokens.pageAccent

    Canvas(modifier = modifier.fillMaxSize()) {
        // 九宫格（两条竖线 + 两条横线）。
        val thirdW = size.width / 3f
        val thirdH = size.height / 3f
        for (i in 1..2) {
            drawLine(
                color = CameraTokens.gridLine,
                start = Offset(thirdW * i, 0f),
                end = Offset(thirdW * i, size.height),
                strokeWidth = gridStroke.toPx(),
            )
            drawLine(
                color = CameraTokens.gridLine,
                start = Offset(0f, thirdH * i),
                end = Offset(size.width, thirdH * i),
                strokeWidth = gridStroke.toPx(),
            )
        }

        if (quad == null || quad.points.size != 4) return@Canvas

        // FILL_CENTER：以归一化帧坐标铺满视图的内容矩形。
        val contentW = max(size.width, size.height / quad.frameAspect)
        val contentH = contentW * quad.frameAspect
        val offX = (size.width - contentW) / 2f
        val offY = (size.height - contentH) / 2f
        fun map(p: Offset) = Offset(offX + p.x * contentW, offY + p.y * contentH)

        val mapped = quad.points.map(::map)
        val path = Path().apply {
            moveTo(mapped[0].x, mapped[0].y)
            for (i in 1..3) lineTo(mapped[i].x, mapped[i].y)
            close()
        }
        drawPath(
            path = path,
            color = quadColor,
            style = Stroke(width = quadStroke.toPx()),
        )
        // 四角小圆点，强化"已锁定"反馈。
        mapped.forEach { p ->
            drawCircle(
                color = quadColor,
                radius = cornerR.toPx(),
                center = p,
            )
        }
    }
}
