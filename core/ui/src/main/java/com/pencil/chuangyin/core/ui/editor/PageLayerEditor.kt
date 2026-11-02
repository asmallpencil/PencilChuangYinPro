package com.pencil.chuangyin.core.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 图层尺寸下限：1%。 */
const val PAGE_LAYER_MIN_SCALE = 0.01f

/** 图层尺寸上限：200%。 */
const val PAGE_LAYER_MAX_SCALE = 2f

/** 预览缩放范围（仅影响屏幕显示，不进入打印）。 */
private val VIEW_SCALE_RANGE = 0.5f..3f

/**
 * 页面图层编辑参数。
 *
 * 这是图片打印 / 条码打印与后续「创意工坊」共用的最小图层模型：
 * - 画布尺寸由打印头宽度推导，高度随图层内容自动延长；
 * - [layerScale] 决定图层相对画布宽度的缩放（1f = 100%）；
 * - [offsetX] / [offsetY] 为相对画布的平移倍数，超出画布宽度的部分打印时会被裁切；
 * - [locked] 只锁图层（禁止拖动与尺寸调整），不锁预览缩放；
 * - [viewScale] 纯粹是查看用的缩放。
 */
data class PageLayerTransform(
    val layerScale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val locked: Boolean = false,
    val viewScale: Float = 1f,
)

/** 图层编辑会话状态：UI 直接读写，打印/保存时取 [transform]。 */
@Stable
class PageLayerEditorState(initial: PageLayerTransform = PageLayerTransform()) {

    var transform by mutableStateOf(initial)
        private set

    fun update(block: (PageLayerTransform) -> PageLayerTransform) {
        transform = block(transform)
    }

    fun setLayerScale(value: Float) =
        update { it.copy(layerScale = value.coerceIn(PAGE_LAYER_MIN_SCALE, PAGE_LAYER_MAX_SCALE)) }

    fun setViewScale(value: Float) =
        update { it.copy(viewScale = value.coerceIn(VIEW_SCALE_RANGE.start, VIEW_SCALE_RANGE.endInclusive)) }

    fun toggleLocked() = update { it.copy(locked = !it.locked) }

    fun reset() = update { PageLayerTransform(locked = it.locked) }
}

/**
 * 图层编辑视口：白纸画布 + 图片图层。
 *
 * - 单指拖动 = 平移图层（锁定后禁用）；
 * - 双指捏合 = 缩放预览视图；
 * - 画布外的区域显示灰色半透明遮罩，代表打印时会被裁切；
 * - 右侧竖条调整图层尺寸（1%~200%），顶部滑杆调整预览缩放。
 */
@Composable
fun LayerEditorViewport(
    image: ImageBitmap?,
    state: PageLayerEditorState,
    modifier: Modifier = Modifier,
) {
    val transform = state.transform
    val pageColor = Color.White
    val outsideColor = Color(0xFF9E9E9E).copy(alpha = 0.55f)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE9E9EE)),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(image) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // 手势回调里必须读 state 的最新值：闭包捕获的 transform 会停留在
                            // 手势开始时的快照，连续缩放会以旧值为基准。
                            if (zoom > 1.02f || zoom < 0.98f) {
                                // 双指缩放只调整预览视图。
                                state.setViewScale(state.transform.viewScale * zoom)
                            } else if (!state.transform.locked && image != null) {
                                // 单指拖动图层：位移按画布比例归一化，跨尺寸设备一致。
                                state.update {
                                    it.copy(
                                        offsetX = (it.offsetX + pan.x / size.width).coerceIn(-1f, 1f),
                                        offsetY = (it.offsetY + pan.y / size.height).coerceIn(-1f, 1f),
                                    )
                                }
                            }
                        }
                    },
            ) {
                val pageInset = size.width * 0.07f
                val pageLeft = pageInset
                val pageWidth = size.width - pageInset * 2

                drawRect(pageColor, topLeft = Offset(pageLeft, 0f), size = Size(pageWidth, size.height))
                if (image != null) {
                    val scale = (pageWidth / image.width) * transform.layerScale * transform.viewScale
                    val w = image.width * scale
                    val h = image.height * scale
                    val left = pageLeft + (pageWidth - w) / 2f + transform.offsetX * pageWidth
                    val top = transform.offsetY * size.height
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                    )
                }
                // 画布左右两端的灰色区域 = 打印时会被裁掉的部分。
                drawRect(outsideColor, topLeft = Offset.Zero, size = Size(pageLeft, size.height))
                drawRect(
                    outsideColor,
                    topLeft = Offset(pageLeft + pageWidth, 0f),
                    size = Size(pageInset, size.height),
                )
                drawPageBorder(pageLeft, pageWidth)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        VerticalScaleSlider(
            value = transform.layerScale,
            enabled = !transform.locked,
            onValueChange = state::setLayerScale,
            modifier = Modifier
                .width(34.dp)
                .height(170.dp),
        )
    }
}

private fun DrawScope.drawPageBorder(pageLeft: Float, pageWidth: Float) {
    val border = Color(0xFFBDBDBD)
    drawRect(
        color = border,
        topLeft = Offset(pageLeft, 0f),
        size = Size(1f, size.height),
    )
    drawRect(
        color = border,
        topLeft = Offset(pageLeft + pageWidth - 1f, 0f),
        size = Size(1f, size.height),
    )
}

/** 编辑工具条：预览缩放滑杆 + 锁定 + 复位。 */
@Composable
fun LayerEditorToolbar(
    state: PageLayerEditorState,
    modifier: Modifier = Modifier,
) {
    val transform = state.transform
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.ZoomIn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Slider(
                value = transform.viewScale,
                onValueChange = state::setViewScale,
                valueRange = VIEW_SCALE_RANGE,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Text(
                text = "${(transform.viewScale * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "图层 ${(transform.layerScale * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = state::toggleLocked) {
                Icon(
                    imageVector = if (transform.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = if (transform.locked) "解锁图层" else "锁定图层",
                    tint = if (transform.locked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = state::reset) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = "复位图层",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 竖向尺寸滑杆：下 1% → 上 200%，100% 处有刻度线。 */
@Composable
fun VerticalScaleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        // 向上拖动放大：归一化到 1%..200%。
                        val delta = -dragAmount / size.height * (PAGE_LAYER_MAX_SCALE - PAGE_LAYER_MIN_SCALE)
                        onValueChange(value + delta)
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackWidth = 6.dp.toPx()
                val left = (size.width - trackWidth) / 2f
                val thumbRadius = 9.dp.toPx()
                drawRoundRect(
                    color = inactive,
                    topLeft = Offset(left, thumbRadius),
                    size = Size(trackWidth, size.height - thumbRadius * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f),
                )
                val fraction = ((value - PAGE_LAYER_MIN_SCALE) /
                    (PAGE_LAYER_MAX_SCALE - PAGE_LAYER_MIN_SCALE)).coerceIn(0f, 1f)
                val usable = size.height - thumbRadius * 2
                val thumbY = size.height - thumbRadius - fraction * usable
                drawRoundRect(
                    color = active.copy(alpha = if (enabled) 1f else 0.4f),
                    topLeft = Offset(left, thumbY),
                    size = Size(trackWidth, size.height - thumbRadius - thumbY),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f),
                )
                drawCircle(
                    color = active.copy(alpha = if (enabled) 1f else 0.4f),
                    radius = thumbRadius,
                    center = Offset(size.width / 2f, thumbY),
                )
                // 100% 刻度。
                val hundred = (1f - PAGE_LAYER_MIN_SCALE) / (PAGE_LAYER_MAX_SCALE - PAGE_LAYER_MIN_SCALE)
                val markY = size.height - thumbRadius - hundred * usable
                drawRect(
                    color = Color.Black.copy(alpha = 0.35f),
                    topLeft = Offset(size.width * 0.18f, markY - 1f),
                    size = Size(size.width * 0.64f, 2f),
                )
            }
        }
        Text(
            text = "${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
