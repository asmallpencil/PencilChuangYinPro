package com.pencil.chuangyin.core.ui.tutorial

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.resources.R as ResR
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 新手引导的单步说明：锚点标签 + 标题 + 说明文案。 */
data class CoachMarkStep(
    val anchor: String,
    val title: String,
    val description: String,
)

/**
 * 锚点登记表：页面里的控件通过 [coachMarkAnchor] 把自己的全局坐标写进来，
 * 引导浮层据此挖孔并定位说明卡。
 */
@Stable
class CoachMarkState internal constructor() {
    internal val anchors = mutableStateMapOf<String, Rect>()

    internal fun update(tag: String, rect: Rect) {
        anchors[tag] = rect
    }
}

val LocalCoachMarkState = compositionLocalOf<CoachMarkState?> { null }

/** 把当前控件登记为引导锚点（必须在 [CoachMarkHost] 的内容里调用）。 */
@Composable
fun Modifier.coachMarkAnchor(tag: String): Modifier {
    val state = LocalCoachMarkState.current ?: return this
    return this.onGloballyPositioned { coordinates ->
        state.update(tag, coordinates.boundsInRoot())
    }
}

/**
 * 新手教程悬浮层宿主：内容照常渲染，首次进入该页面时在真实界面上叠一层引导。
 *
 * 每个页面一个 [pageKey]，看过一次后写入 SharedPreferences，不再打扰。
 * 锚点尚未测量到的步骤会自动跳过，避免出现「引导致空白」。
 */
@Composable
fun CoachMarkHost(
    pageKey: String,
    steps: List<CoachMarkStep>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val state = remember(pageKey) { CoachMarkState() }
    var visible by remember(pageKey) { mutableStateOf(false) }
    var index by remember(pageKey) { mutableIntStateOf(0) }

    LaunchedEffect(pageKey, enabled, steps) {
        if (!enabled || steps.isEmpty()) return@LaunchedEffect
        if (!TutorialPrefs.shouldShow(context, pageKey)) return@LaunchedEffect
        // 等页面入场动效与锚点测量稳定后再亮起，避免浮层跟着布局抖动。
        delay(520)
        index = 0
        visible = true
    }

    CompositionLocalProvider(LocalCoachMarkState provides if (visible) state else null) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
            if (visible) {
                CoachMarkOverlay(
                    steps = steps,
                    index = index,
                    anchors = state.anchors,
                    onNext = {
                        if (index >= steps.lastIndex) {
                            visible = false
                            TutorialPrefs.markShown(context, pageKey)
                        } else {
                            index++
                        }
                    },
                    onSkip = {
                        visible = false
                        TutorialPrefs.markShown(context, pageKey)
                    },
                )
            }
        }
    }
}

@Composable
private fun CoachMarkOverlay(
    steps: List<CoachMarkStep>,
    index: Int,
    anchors: Map<String, Rect>,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    // 锚点缺失时停在当前步，等布局上报；不直接跳过，避免引导一闪而过。
    val hole = anchors[step.anchor]
    val density = LocalDensity.current
    val motion = rememberAppMotion()
    val enter by animateFloatAsState(
        targetValue = 1f,
        animationSpec = motion.effectSpec(),
        label = "coachMarkEnter",
    )
    val pulse = rememberInfiniteTransition(label = "coachMarkPulse")
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "coachMarkRingAlpha",
    )
    val holePadPx = with(density) { 7.dp.toPx() }
    val holeCornerPx = with(density) { 20.dp.toPx() }
    val accent = MaterialTheme.colorScheme.primary
    val dim = Color.Black.copy(alpha = 0.62f * enter)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // 拦截底层点击：引导期间只能操作说明卡。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        val screenHeightDp = maxHeight

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            drawRect(dim)
            if (hole != null) {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(hole.left - holePadPx, hole.top - holePadPx),
                    size = Size(hole.width + holePadPx * 2, hole.height + holePadPx * 2),
                    cornerRadius = CornerRadius(holeCornerPx),
                    blendMode = BlendMode.Clear,
                )
                drawRoundRect(
                    color = accent.copy(alpha = ringAlpha),
                    topLeft = Offset(hole.left - holePadPx, hole.top - holePadPx),
                    size = Size(hole.width + holePadPx * 2, hole.height + holePadPx * 2),
                    cornerRadius = CornerRadius(holeCornerPx),
                    style = Stroke(width = with(density) { 2.dp.toPx() }),
                )
            }
        }

        val cardHeightEstimate = 208.dp
        val cardTop = if (hole != null) {
            val belowTop = with(density) { hole.bottom.toDp() } + 18.dp
            if (belowTop + cardHeightEstimate + 24.dp <= screenHeightDp) {
                belowTop
            } else {
                (with(density) { hole.top.toDp() } - cardHeightEstimate - 18.dp)
                    .coerceIn(24.dp, (screenHeightDp - cardHeightEstimate - 24.dp).coerceAtLeast(24.dp))
            }
        } else {
            screenHeightDp / 2 - cardHeightEstimate / 2
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp)
                .offset { IntOffset(0, cardTop.roundToPx()) }
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = enter
                    translationY = (1f - enter) * 28f
                    scaleX = 0.96f + 0.04f * enter
                    scaleY = 0.96f + 0.04f * enter
                },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 14.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(ResR.string.tutorial_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        modifier = Modifier
                            .background(
                                color = accent.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(50),
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${index + 1} / ${steps.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(ResR.string.tutorial_action_skip))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onNext, shape = MaterialTheme.shapes.medium) {
                        Text(
                            stringResource(
                                if (index >= steps.lastIndex) {
                                    ResR.string.tutorial_action_finish
                                } else {
                                    ResR.string.tutorial_action_next
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 教程观看标记：按页面记录，存 SharedPreferences，避免为浮层再引一套 DataStore。 */
private object TutorialPrefs {
    private const val PREFS_NAME = "pencil_tutorial_flags"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldShow(context: Context, pageKey: String): Boolean =
        !prefs(context).getBoolean(pageKey, false)

    fun markShown(context: Context, pageKey: String) {
        prefs(context).edit().putBoolean(pageKey, true).apply()
    }
}
