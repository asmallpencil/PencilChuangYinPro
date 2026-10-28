package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.glass.glassSurface
import com.pencil.chuangyin.desktop.theme.LocalLiquidGlassControlsEnabled
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import com.pencil.chuangyin.desktop.ui.motion.DesktopMotion
import com.pencil.chuangyin.desktop.ui.motion.pressScale

/** 顶部应用栏：玻璃材质 + 透明容器（对齐安卓端 HomeScreen / CreativeScreen / PixivBrowseScreen）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glassEnabled = LocalLiquidGlassControlsEnabled.current
    TopAppBar(
        title = {
            if (subtitle == null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
            } else {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        modifier = modifier.glassSurface(
            shape = RectangleShape,
            enabled = glassEnabled,
            fallbackColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** 图标按钮（48dp 触达区，对齐安卓端 touch target；禁用时按安卓端 38% 透明度呈现）。 */
@Composable
fun AppIconButton(
    icon: AppIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    size: Dp = 48.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .pressScale(interaction, enabled = enabled, pressedScale = 0.92f)
            .clip(RoundedCornerShape(size / 2))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AppIconView(
            icon = icon,
            size = 24.dp,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        )
    }
}

/** 主按钮（对齐安卓端 AppPrimaryButton：填充色 + Filled 样式）。 */
@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: AppIcon? = null,
    height: Dp = 44.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        interactionSource = interaction,
        modifier = modifier
            .height(height)
            .pressScale(interaction, enabled = enabled),
    ) {
        if (leadingIcon != null) {
            AppIconView(
                icon = leadingIcon,
                size = 18.dp,
                tint = androidx.compose.material3.LocalContentColor.current,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 筛选胶囊（对齐安卓端 AppFilterChip 的回退分支：M3 FilterChip + small 圆角）。 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.pressScale(interaction, enabled = enabled, pressedScale = 0.94f),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        interactionSource = interaction,
        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
        ),
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
    )
}

/** 次级按钮（描边样式）。 */
@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        interactionSource = interaction,
        modifier = modifier
            .height(44.dp)
            .pressScale(interaction, enabled = enabled),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 描边按钮（可带前置图标，对齐安卓端 AppOutlinedButton）。 */
@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: AppIcon? = null,
    height: Dp = 44.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        interactionSource = interaction,
        modifier = modifier
            .height(height)
            .pressScale(interaction, enabled = enabled),
    ) {
        if (leadingIcon != null) {
            AppIconView(
                icon = leadingIcon,
                size = 18.dp,
                tint = androidx.compose.material3.LocalContentColor.current,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 文字按钮（对齐安卓端 AppTextButton）。 */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.TextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction, enabled = enabled, pressedScale = 0.94f),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 分割线（对齐安卓端 AppDivider：1dp outlineVariant 50%）。 */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/** 滑块（对齐安卓端 AppSlider）。 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    androidx.compose.material3.Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = modifier,
    )
}

/** 开关（对齐安卓端 AppSwitch）。 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}

/** 空状态（对齐安卓端 FullScreenEmpty / 未登录态的统一排版）。 */
@Composable
fun AppEmptyState(
    icon: AppIcon,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIconView(icon = icon, size = 56.dp, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(Modifier.padding(top = 8.dp)) { action() }
        }
    }
}

/** 分段控制器（插画 / 漫画 / 小说），对齐安卓端 AppSegmentedRow 的胶囊分段样式。 */
@Composable
fun AppSegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showCheck: Boolean = false,
) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val itemShape = MaterialTheme.shapes.medium
            val interaction = remember { MutableInteractionSource() }
            // 容器色走 Effect（零过冲），文字/图标缩放走 Spatial（有回弹）：对齐 MD3 的二分原则。
            val itemColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
                animationSpec = DesktopMotion.effect(),
                label = "segmentContainer",
            )
            val contentScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (selected) 1f else 0.95f,
                animationSpec = DesktopMotion.bouncy(),
                label = "segmentContentScale",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressScale(interaction, pressedScale = 0.95f)
                    .clip(itemShape)
                    .background(itemColor)
                    .clickable(interactionSource = interaction, indication = null) { onSelected(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showCheck && selected,
                        enter = androidx.compose.animation.fadeIn(DesktopMotion.effectFast()) +
                            androidx.compose.animation.expandHorizontally(DesktopMotion.spatialFast()),
                        exit = androidx.compose.animation.fadeOut(DesktopMotion.effectFast()) +
                            androidx.compose.animation.shrinkHorizontally(DesktopMotion.spatialFast()),
                    ) {
                        AppIconView(
                            icon = AppIcon.Check,
                            size = 16.dp,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** 输入框（对齐安卓端 AppTextField：描边 + 主题圆角，可带浮动标签，回车可提交）。 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    onDone: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        label = label?.let { text ->
            { Text(text = text, style = MaterialTheme.typography.bodyMedium) }
        },
        placeholder = placeholder?.let { hint ->
            { Text(text = hint, style = MaterialTheme.typography.bodyMedium) }
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            // 输入框统一支持液态玻璃；关闭时回退为 surfaceContainerHighest 纯色底。
            .glassSurface(
                shape = MaterialTheme.shapes.medium,
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            .then(
                if (onDone != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                            onDone()
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
                },
            ),
    )
}

/** 分区胶囊（推荐 / 日榜 / 周榜 …），对齐安卓端 SectionChipRow。 */
@Composable
fun SectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val interaction = remember { MutableInteractionSource() }
    // 容器色走 Effect（不过冲），选中时的轻微放大走 Spatial（带回弹），制造"被按下去选中"的物理感。
    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = DesktopMotion.effect(),
        label = "sectionChipColor",
    )
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = DesktopMotion.effect(),
        label = "sectionChipContentColor",
    )
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = DesktopMotion.bouncy(),
        label = "sectionChipScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pressScale(interaction, pressedScale = 0.94f)
            .clip(shape)
            .background(containerColor)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}
