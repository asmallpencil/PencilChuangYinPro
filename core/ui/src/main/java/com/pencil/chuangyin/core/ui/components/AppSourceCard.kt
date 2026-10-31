package com.pencil.chuangyin.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.ui.motion.pressScale
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.resources.R as ResR

/** 打印页「来源入口」的单项定义：图标 + 无障碍文案 + 点击。 */
data class AppSourceAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * 来源入口卡片（图片打印 / 文字打印共用）：
 * 白底圆角卡片 + 一排圆形图标按钮。图片打印页用 [primaryAction] 承载大号「+」主入口，
 * 文字打印页不传该参数 —— 即「同款圆形图标、没有大圆+」。
 *
 * 动效（全部走 AppMotion token，随动效预设降级）：
 * - 卡片本体：淡入 + 上浮（空间慢节奏）；
 * - 每个圆形按钮：按 [AppMotion.staggerSpec] 依次延迟入场（缩放 0.55 → 1、上浮 10dp）；
 * - 按下：pressScale 的跟手缩放 + 松手弹性回弹。
 */
@Composable
fun AppSourceCard(
    actions: List<AppSourceAction>,
    modifier: Modifier = Modifier,
    primaryAction: AppSourceAction? = null,
    primarySize: Dp = 76.dp,
    primaryIconSize: Dp = 36.dp,
    actionSize: Dp = 56.dp,
    actionIconSize: Dp = 26.dp,
) {
    val motion = rememberAppMotion()
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val cardReveal by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = motion.spatialSlowSpec(),
        label = "sourceCardReveal",
    )
    Surface(
        shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_m)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardReveal
                translationY = (1f - cardReveal) * 24f
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (primaryAction != null) {
                SourceCircleButton(
                    action = primaryAction,
                    size = primarySize,
                    iconSize = primaryIconSize,
                    index = 0,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
            actions.forEachIndexed { index, action ->
                SourceCircleButton(
                    action = action,
                    size = actionSize,
                    iconSize = actionIconSize,
                    // 有主入口时整体后移一位，让入场节奏从「+」开始依次点亮。
                    index = if (primaryAction != null) index + 1 else index,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SourceCircleButton(
    action: AppSourceAction,
    size: Dp,
    iconSize: Dp,
    index: Int,
    containerColor: Color,
    contentColor: Color,
) {
    val motion = rememberAppMotion()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val reveal by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = motion.staggerSpec(index),
        label = "sourceActionReveal",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                alpha = reveal
                val scale = 0.55f + 0.45f * reveal
                scaleX = scale
                scaleY = scale
                translationY = (1f - reveal) * 12f
            }
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = action.onClick,
            )
            .pressScale(interactionSource)
            .semantics { contentDescription = action.label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}
