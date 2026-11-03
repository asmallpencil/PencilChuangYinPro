package com.pencil.chuangyin.core.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 图层尺寸下限：1%。 */
const val PAGE_LAYER_MIN_SCALE = 0.01f

/** 图层尺寸上限：200%。 */
const val PAGE_LAYER_MAX_SCALE = 2f

/** 预览缩放范围（仅影响屏幕显示，不进入打印）。 */
private val VIEW_SCALE_RANGE = 0.5f..5f

/** 纸张在视口中四周留白：保证 100% 时四边都可见。 */
private val PAGE_VIEWPORT_PADDING = 14.dp

/**
 * 页面图层编辑参数。
 *
 * 这是图片打印 / 条码打印与后续「创意工坊」共用的最小图层模型：
 * - 画布尺寸由打印头宽度推导，高度随图层内容自动延长；
 * - [layerScale] 决定图层相对画布宽度的缩放（1f = 100%），**会进入打印结果**；
 * - [offsetX] / [offsetY] 为相对画布的平移倍数，超出画布宽度的部分打印时会被裁切；
 * - [locked] 只锁图层（禁止拖动与尺寸调整），不锁预览缩放。
 *
 * 预览缩放/平移只存在于 [PageLayerEditorState] 的视图字段里，不放进本模型，
 * 从根上避免「把看图的缩放当成图层缩放保存进打印」。
 */
data class PageLayerTransform(
    val layerScale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val locked: Boolean = false,
)

/** 图层编辑会话状态：UI 直接读写，打印/保存时取 [transform]。 */
@Stable
class PageLayerEditorState(initial: PageLayerTransform = PageLayerTransform()) {

    var transform by mutableStateOf(initial)
        private set

    /** 视图平移（屏幕像素）：只用于查看，不写入 [transform]，因此不会影响打印。 */
    var viewOffset by mutableStateOf(Offset.Zero)

    /** 视图缩放（仅显示）：与文印参数完全分离。 */
    var viewScale by mutableStateOf(1f)
        private set

    fun update(block: (PageLayerTransform) -> PageLayerTransform) {
        transform = block(transform)
    }

    fun setLayerScale(value: Float) =
        update { it.copy(layerScale = value.coerceIn(PAGE_LAYER_MIN_SCALE, PAGE_LAYER_MAX_SCALE)) }

    fun scaleViewTo(value: Float) {
        viewScale = value.coerceIn(VIEW_SCALE_RANGE.start, VIEW_SCALE_RANGE.endInclusive)
    }

    /** 视图回到「整页可见」：预览缩放 100% + 平移归零。 */
    fun resetView() {
        viewOffset = Offset.Zero
        scaleViewTo(1f)
    }

    fun toggleLocked() = update { it.copy(locked = !it.locked) }

    fun reset() {
        resetView()
        update { PageLayerTransform(locked = it.locked) }
    }
}

/**
 * 图层编辑视口：整页白纸 + 图片/条码图层。
 *
 * 手势分工（避免「视图缩放」与「图层缩放」混淆）：
 * - 单指拖动 = 平移图层（锁定后禁用）；
 * - 双指捏合 = 缩放**视图**（只影响查看）；双指平移 = 平移视图；
 * - 右侧竖条 = 缩放**图层**（1%~200%，会进入打印结果）。
 *
 * 纸张默认按「整页可见」缩放，四边与投影都可见；图层超出纸张的部分按打印规则裁切。
 */
@Composable
fun LayerEditorViewport(
    image: ImageBitmap?,
    state: PageLayerEditorState,
    modifier: Modifier = Modifier,
) {
    val transform = state.transform
    val pageColor = Color.White
    val borderColor = Color(0xFFB4B4BE)
    val shadowColor = Color.Black.copy(alpha = 0.12f)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE4E4EA)),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(image) {
                        // 按手指数分流，而不是靠 zoom 阈值猜：
                        // 1 指 = 图层平移；2 指及以上 = 视图缩放/平移（永不改打印参数）。
                        val padPx = PAGE_VIEWPORT_PADDING.toPx()
                        fun viewPan(pan: Offset) {
                            val img = image
                            val geometry = editorGeometry(
                                viewportWidth = size.width.toFloat(),
                                viewportHeight = size.height.toFloat(),
                                imageWidth = (img?.width ?: 384).toFloat().coerceAtLeast(1f),
                                imageHeight = (img?.height ?: 384).toFloat().coerceAtLeast(1f),
                                transform = state.transform,
                                viewScale = state.viewScale,
                                paddingPx = padPx,
                            )
                            val maxX = max(0f, (geometry.pageScreenWidth - (size.width - padPx * 2f)) / 2f)
                            val maxY = max(0f, (geometry.pageScreenHeight - (size.height - padPx * 2f)) / 2f)
                            state.viewOffset = Offset(
                                (state.viewOffset.x + pan.x).coerceIn(-maxX, maxX),
                                (state.viewOffset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                        }

                        fun layerPan(pan: Offset) {
                            val img = image ?: return
                            if (state.transform.locked) return
                            // 位移按「纸张在屏幕上的实际宽度/图层高度」归一化：
                            // 手指移动多少像素，图层就移动多少像素（不受视口留白影响）。
                            val geometry = editorGeometry(
                                viewportWidth = size.width.toFloat(),
                                viewportHeight = size.height.toFloat(),
                                imageWidth = img.width.toFloat(),
                                imageHeight = img.height.toFloat(),
                                transform = state.transform,
                                viewScale = state.viewScale,
                                paddingPx = padPx,
                            )
                            val layerScreenHeight = geometry.layerScreenHeight.coerceAtLeast(1f)
                            state.update {
                                it.copy(
                                    offsetX = (it.offsetX + pan.x / geometry.pageScreenWidth)
                                        .coerceIn(-1f, 1f),
                                    offsetY = (it.offsetY + pan.y / layerScreenHeight)
                                        .coerceIn(-1f, 1f),
                                )
                            }
                        }

                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var layerDragStarted = false
                            var pendingLayerPan = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed == 0) break
                                if (pressed >= 2) {
                                    // 双指：视图缩放 + 视图平移；离开双指后重置单指拖动状态。
                                    layerDragStarted = false
                                    pendingLayerPan = Offset.Zero
                                    val zoom = event.calculateZoom()
                                    if (zoom != 1f && zoom > 0f) {
                                        state.scaleViewTo(state.viewScale * zoom)
                                    }
                                    viewPan(event.calculatePan())
                                } else if (!state.transform.locked) {
                                    val pan = event.calculatePan()
                                    if (!layerDragStarted) {
                                        pendingLayerPan += pan
                                        if (pendingLayerPan.getDistance() > viewConfiguration.touchSlop) {
                                            layerDragStarted = true
                                            layerPan(pendingLayerPan)
                                        }
                                    } else {
                                        layerPan(pan)
                                    }
                                }
                                // 无论哪种模式都消费事件：父级滚动/分页器不会抢走手势。
                                event.changes.forEach { change -> change.consume() }
                            }
                        }
                    },
            ) {
                val pad = PAGE_VIEWPORT_PADDING.toPx()
                val geometry = editorGeometry(
                    viewportWidth = size.width,
                    viewportHeight = size.height,
                    imageWidth = (image?.width ?: 384).toFloat().coerceAtLeast(1f),
                    imageHeight = (image?.height ?: 384).toFloat().coerceAtLeast(1f),
                    transform = transform,
                    viewScale = state.viewScale,
                    paddingPx = pad,
                )
                // 视图平移跟随缩放范围：放大后才能拖出纸张，缩放回 100% 时自动收回到居中。
                val maxOffsetX = max(0f, (geometry.pageScreenWidth - (size.width - pad * 2f)) / 2f)
                val maxOffsetY = max(0f, (geometry.pageScreenHeight - (size.height - pad * 2f)) / 2f)
                val offset = Offset(
                    state.viewOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                    state.viewOffset.y.coerceIn(-maxOffsetY, maxOffsetY),
                )
                val pageLeft = (size.width - geometry.pageScreenWidth) / 2f + offset.x
                val pageTop = (size.height - geometry.pageScreenHeight) / 2f + offset.y

                // 纸张投影：让纸张从灰色背景上「浮起来」，边缘一眼可见。
                drawRect(
                    color = shadowColor,
                    topLeft = Offset(pageLeft, pageTop + 3.dp.toPx()),
                    size = Size(geometry.pageScreenWidth, geometry.pageScreenHeight),
                )
                drawRect(
                    color = pageColor,
                    topLeft = Offset(pageLeft, pageTop),
                    size = Size(geometry.pageScreenWidth, geometry.pageScreenHeight),
                )
                if (image != null) {
                    val layerLeft = pageLeft +
                        (geometry.pageScreenWidth - geometry.layerScreenWidth) / 2f +
                        transform.offsetX * geometry.pageScreenWidth
                    val layerTop = pageTop + transform.offsetY * geometry.layerScreenHeight
                    // 超出纸张的部分直接裁掉：与打印时的裁切规则完全一致。
                    clipRect(
                        left = pageLeft,
                        top = pageTop,
                        right = pageLeft + geometry.pageScreenWidth,
                        bottom = pageTop + geometry.pageScreenHeight,
                    ) {
                        drawImage(
                            image = image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(image.width, image.height),
                            dstOffset = IntOffset(layerLeft.roundToInt(), layerTop.roundToInt()),
                            dstSize = IntSize(
                                geometry.layerScreenWidth.roundToInt().coerceAtLeast(1),
                                geometry.layerScreenHeight.roundToInt().coerceAtLeast(1),
                            ),
                            // 缩到「整页可见」时是几倍下采样，Low 双线性会明显走样。
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                }
                drawRect(
                    color = borderColor,
                    topLeft = Offset(pageLeft, pageTop),
                    size = Size(geometry.pageScreenWidth, geometry.pageScreenHeight),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        VerticalScaleSlider(
            value = transform.layerScale,
            enabled = !transform.locked,
            onValueChange = state::setLayerScale,
            modifier = Modifier
                .width(46.dp)
                .height(190.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(vertical = 6.dp),
        )
    }
}

/** 视口几何：纸张/图层在屏幕上的尺寸，供绘制与手势共用同一套算法。 */
private data class EditorGeometry(
    val pageScreenWidth: Float,
    val pageScreenHeight: Float,
    val layerScreenWidth: Float,
    val layerScreenHeight: Float,
)

/**
 * 计算纸张与图层的屏幕几何。
 *
 * 页面坐标系与 ImagePageComposer / BarcodePageComposer 保持一致：
 * 页宽 = 图层原始宽度（打印管线已统一到打印头宽度），图层按 [PageLayerTransform.layerScale]
 * 缩放并按 [PageLayerTransform.offsetX]/offsetY 平移；向下平移会延长纸张，向上平移裁掉页首。
 * 纸张再按「整页可见」比例缩放到视口，并叠加视图缩放（仅显示）。
 */
private fun editorGeometry(
    viewportWidth: Float,
    viewportHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    transform: PageLayerTransform,
    viewScale: Float,
    paddingPx: Float,
): EditorGeometry {
    val pageWidth = imageWidth.coerceAtLeast(1f)
    val layerHeight = imageHeight * transform.layerScale
    val extraHeight = (layerHeight * transform.offsetY).coerceAtLeast(0f)
    val pageHeight = max(layerHeight + extraHeight, 1f)
    val availableWidth = (viewportWidth - paddingPx * 2f).coerceAtLeast(1f)
    val availableHeight = (viewportHeight - paddingPx * 2f).coerceAtLeast(1f)
    val fit = min(availableWidth / pageWidth, availableHeight / pageHeight).coerceAtLeast(0.001f)
    val screenScale = fit * viewScale
    val layerWidth = pageWidth * transform.layerScale
    return EditorGeometry(
        pageScreenWidth = pageWidth * screenScale,
        pageScreenHeight = pageHeight * screenScale,
        layerScreenWidth = layerWidth * screenScale,
        layerScreenHeight = layerHeight * screenScale,
    )
}

/** 编辑工具条：上行「视图缩放」（只影响查看），下行「图层尺寸 / 锁定 / 复位」。 */
@Composable
fun LayerEditorToolbar(
    state: PageLayerEditorState,
    modifier: Modifier = Modifier,
) {
    val transform = state.transform
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "视图",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Filled.ZoomOut,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(18.dp),
            )
            Slider(
                value = state.viewScale,
                onValueChange = state::scaleViewTo,
                valueRange = VIEW_SCALE_RANGE,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
            Icon(
                imageVector = Icons.Filled.ZoomIn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            TextButton(onClick = state::resetView) {
                Text(text = "适应", style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "图层 ${(transform.layerScale * 100).roundToInt()}%（影响打印）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
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

/**
 * 竖向图层尺寸滑杆：下 1% → 上 200%，100% 处有刻度线。
 *
 * 触摸处理刻意绕开 Compose 的拖动手势识别：外层页面是 `verticalScroll`，常规
 * `detectVerticalDragGestures` 会在触摸坡度判定期间与父级滚动竞争，表现为「滑条拖不动」。
 * 这里在 [PointerEventPass.Initial] 阶段直接消费按下与移动，父级滚动收不到未消费事件；
 * 数值按按下后的相对位移换算（不用绝对落点，避免一按就跳），并读取按下瞬间的最新值。
 */
@Composable
fun VerticalScaleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val thumbRadius = 9.dp
    // 指针手势不随数值重组而重启，必须用 latestValue 读取「按下那一刻」的真实值。
    val latestValue by rememberUpdatedState(value)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "图层",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val radius = thumbRadius.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        down.consume()
                        val startY = down.position.y
                        val startValue = latestValue
                        val range = PAGE_LAYER_MAX_SCALE - PAGE_LAYER_MIN_SCALE
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            val dy = change.position.y - startY
                            if (!dragging && abs(dy) > viewConfiguration.touchSlop) {
                                dragging = true
                            }
                            if (dragging) {
                                val usable = (size.height - radius * 2f).coerceAtLeast(1f)
                                // 相对拖动：不跳到落点，也不会因为读到旧快照而抖动。
                                onValueChange(
                                    (startValue - dy / usable * range)
                                        .coerceIn(PAGE_LAYER_MIN_SCALE, PAGE_LAYER_MAX_SCALE),
                                )
                            }
                        }
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackWidth = 6.dp.toPx()
                val left = (size.width - trackWidth) / 2f
                val radius = thumbRadius.toPx()
                drawRoundRect(
                    color = inactive,
                    topLeft = Offset(left, radius),
                    size = Size(trackWidth, size.height - radius * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f),
                )
                val fraction = ((value - PAGE_LAYER_MIN_SCALE) /
                    (PAGE_LAYER_MAX_SCALE - PAGE_LAYER_MIN_SCALE)).coerceIn(0f, 1f)
                val usable = size.height - radius * 2
                val thumbY = size.height - radius - fraction * usable
                drawRoundRect(
                    color = active.copy(alpha = if (enabled) 1f else 0.4f),
                    topLeft = Offset(left, thumbY),
                    size = Size(trackWidth, size.height - radius - thumbY),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f),
                )
                drawCircle(
                    color = active.copy(alpha = if (enabled) 1f else 0.4f),
                    radius = radius,
                    center = Offset(size.width / 2f, thumbY),
                )
                // 100% 刻度。
                val hundred = (1f - PAGE_LAYER_MIN_SCALE) /
                    (PAGE_LAYER_MAX_SCALE - PAGE_LAYER_MIN_SCALE)
                val markY = size.height - radius - hundred * usable
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
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}
