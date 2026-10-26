package com.pencil.chuangyin.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 真 RGB（HSV）调色盘：上方为「饱和度 × 明度」二维色板，下方为色相滑杆，
 * 左侧实时预览当前颜色。点击或拖动任意位置选色，适用于主题种子色等自定义取色。
 *
 * 所有尺寸均由外部以 dimen 令牌注入（零硬编码）；选色行为通过 [onColorChange] 回调暴露。
 *
 * 内部 HSV 与外部 ARGB 的双向同步遵循三条不变量：
 * 1. 拖拽期间只以内部 HSV 为准，绝不被外部异步回传覆盖（否则选择点会被往回拽）；
 * 2. 组件自己发出过的颜色被异步回传时（DataStore 落盘有延迟，可能是若干帧前的旧值），
 *    识别为「自身回声」直接忽略，避免松手后被旧值拽回；松手瞬间还会进入「等回传」窗口，
 *    窗口内一律不受理外部值（否则此时拿到的仍是旧种子色，会把选择点拽回），直到自身值回传或超时；
 * 3. 真正的外部颜色（预设 / 十六进制输入 / 首次挂载）按 [android.graphics.Color.colorToHSV]
 *    反解；纯灰（白 / 灰 / 黑）没有可靠色相（会被解析成 0 = 红色），此时保留用户当前色相，
 *    保证「拖到纯白再拖回来」不会串色到红色。
 *
 * @param color 当前颜色（外部事实来源；内部拖拽产生变化后经回调同步回外部）。
 * @param barHeight 色相滑杆高度。
 * @param selectorRadius 色板/滑杆选择器圆点半径。
 * @param selectorStrokeWidth 选择器描边宽度。
 * @param previewSize 当前色预览圆尺寸。
 */
@Composable
fun RgbColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    barHeight: Dp,
    selectorRadius: Dp,
    selectorStrokeWidth: Dp,
    previewSize: Dp,
    modifier: Modifier = Modifier,
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var brightness by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    // 本组件发出过的颜色（ARGB），用于识别异步回传的「自身回声」。
    val ownEmissions = remember { ArrayDeque<Int>() }
    // 最近一次发出、尚未在外部落地确认的颜色：等待期间外部值可能还是旧值，绝不能反向解析。
    var pendingEchoArgb by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(color, dragging, pendingEchoArgb) {
        if (dragging) return@LaunchedEffect
        val incoming = color.toArgb()
        val pending = pendingEchoArgb
        if (pending != null) {
            // 自身值已回传落地 → 结束等待；否则继续忽略（此刻的外部值多半是旧的）。
            if (incoming == pending) pendingEchoArgb = null
            return@LaunchedEffect
        }
        // 迟到的自身回声：不能拿它反向覆盖内部状态，否则松手后选择点会回弹。
        if (ownEmissions.remove(incoming)) return@LaunchedEffect
        // 与内部 HSV 折算结果一致（含首次挂载 / 重复下发），无需同步。
        if (incoming == Color.hsv(hue, saturation, brightness).toArgb()) return@LaunchedEffect

        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(incoming, hsv)
        // 纯灰没有可靠色相（会被解析成 0 = 红色），此时保留用户当前色相。
        if (hsv[1] > ACHROMATIC_SATURATION) hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
    }

    // 回传超时兜底：外部写入失败（或等待期间用户从外部改色）时不能永久屏蔽外部颜色。
    LaunchedEffect(pendingEchoArgb) {
        val pending = pendingEchoArgb ?: return@LaunchedEffect
        delay(ECHO_TIMEOUT_MS)
        if (pendingEchoArgb == pending) pendingEchoArgb = null
    }

    fun emit() {
        val picked = Color(
            Color.hsv(
                hue = hue,
                saturation = saturation.coerceIn(0f, 1f),
                value = brightness.coerceIn(0f, 1f),
            ).toArgb(),
        )
        val argb = picked.toArgb()
        ownEmissions.addLast(argb)
        if (ownEmissions.size > MAX_TRACKED_EMISSIONS) ownEmissions.removeFirst()
        pendingEchoArgb = argb
        onColorChange(picked)
    }

    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(selectorStrokeWidth * 2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(selectorStrokeWidth * 2),
        ) {
            SaturationValuePanel(
                hue = hue,
                saturation = saturation,
                brightness = brightness,
                selectorRadius = selectorRadius,
                selectorStrokeWidth = selectorStrokeWidth,
                onPress = { dragging = true },
                onRelease = { dragging = false },
                onChange = { s, v ->
                    saturation = s
                    brightness = v
                    emit()
                },
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
            )
            // 预览圆带描边：纯白 / 近白也能看清色块边界与当前选中值。
            Canvas(modifier = Modifier.size(previewSize)) {
                val strokePx = selectorStrokeWidth.toPx()
                drawCircle(Color.hsv(hue, saturation, brightness))
                drawCircle(
                    color = outlineColor,
                    radius = size.minDimension / 2f - strokePx / 2f,
                    style = Stroke(strokePx),
                )
            }
        }

        HueBar(
            hue = hue,
            barHeight = barHeight,
            selectorRadius = selectorRadius,
            selectorStrokeWidth = selectorStrokeWidth,
            onPress = { dragging = true },
            onRelease = { dragging = false },
            onChange = { h ->
                hue = h
                emit()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    brightness: Float,
    selectorRadius: Dp,
    selectorStrokeWidth: Dp,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onChange: (saturation: Float, brightness: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pureHue = Color.hsv(hue, 1f, 1f)
    Canvas(
        modifier = modifier.pickGestures(
            onPress = onPress,
            onRelease = onRelease,
            onPosition = { fractionX, fractionY ->
                onChange(
                    fractionX.coerceIn(0f, 1f),
                    (1f - fractionY).coerceIn(0f, 1f),
                )
            },
        ),
    ) {
        // 底层：白 → 纯色相（横向饱和度）。
        drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue)))
        // 上层：透明 → 黑（纵向明度）。
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        val selectorRadiusPx = selectorRadius.toPx()
        val strokePx = selectorStrokeWidth.toPx()
        val center = Offset(
            saturation * size.width,
            (1f - brightness) * size.height,
        )
        drawCircle(Color.Black, selectorRadiusPx, center, style = Stroke(strokePx * 1.6f))
        drawCircle(Color.White, selectorRadiusPx, center, style = Stroke(strokePx))
    }
}

@Composable
private fun HueBar(
    hue: Float,
    barHeight: Dp,
    selectorRadius: Dp,
    selectorStrokeWidth: Dp,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onChange: (hue: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spectrum = remember {
        List(HUE_SEGMENTS + 1) { i -> Color.hsv(i * 360f / HUE_SEGMENTS, 1f, 1f) }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .pickGestures(
                onPress = onPress,
                onRelease = onRelease,
                onPosition = { fractionX, _ ->
                    onChange(fractionX.coerceIn(0f, 1f) * 360f)
                },
            ),
    ) {
        drawRect(Brush.horizontalGradient(spectrum))
        val selectorRadiusPx = selectorRadius.toPx()
        val strokePx = selectorStrokeWidth.toPx()
        val center = Offset(hue / 360f * size.width, size.height / 2f)
        drawCircle(Color.Black, selectorRadiusPx, center, style = Stroke(strokePx * 1.6f))
        drawCircle(Color.White, selectorRadiusPx, center, style = Stroke(strokePx))
    }
}

/**
 * 统一的「按下即选 + 拖动跟手」手势：单指针循环同时覆盖点击与拖拽，
 * 避免 tap / drag 两套 detector 互相抢事件导致拖动时好时坏。
 * 回调收到的是相对控件宽高的归一化坐标（可能超出 0..1，代表手指拖出边界，由调用方裁剪）。
 */
private fun Modifier.pickGestures(
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onPosition: (fractionX: Float, fractionY: Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPress()
        onPosition(down.position.x / size.width, down.position.y / size.height)
        down.consume()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                change.consume()
                break
            }
            change.consume()
            onPosition(change.position.x / size.width, change.position.y / size.height)
        }
        onRelease()
    }
}

private const val HUE_SEGMENTS = 6

/** 饱和度低于此值视为「无彩色」（白 / 灰 / 黑），色相不可信。 */
private const val ACHROMATIC_SATURATION = 0.01f

/** 自身回声缓存的容量上限：长时间连续拖拽时只保留最近若干次，防止无界增长。 */
private const val MAX_TRACKED_EMISSIONS = 64

/** 等待自身颜色回传落地的超时（毫秒）；超时后恢复接受外部颜色，避免外部改色失效。 */
private const val ECHO_TIMEOUT_MS = 500L

@Preview(showBackground = true)
@Composable
private fun RgbColorPickerPreview() {
    RgbColorPicker(
        color = Color(0xFF6750A4),
        onColorChange = {},
        barHeight = 24.dp,
        selectorRadius = 10.dp,
        selectorStrokeWidth = 3.dp,
        previewSize = 48.dp,
    )
}
