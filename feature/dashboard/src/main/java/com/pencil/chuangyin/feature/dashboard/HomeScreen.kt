package com.pencil.chuangyin.feature.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.theme.glassAwareOutline
import com.pencil.chuangyin.core.ui.components.AppIcon
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.ui.motion.pressScale
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.printer.PrinterStatus
import com.pencil.chuangyin.resources.R as ResR

@Composable
fun HomeRoute(
    onOpenConnect: () -> Unit,
    onOpenText: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomBarSpace: Dp = Dp(0f),
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val capsule by viewModel.printerCapsule.collectAsStateWithLifecycle()
    HomeScreen(
        capsule = capsule,
        onOpenConnect = onOpenConnect,
        onOpenText = onOpenText,
        onOpenImage = onOpenImage,
        onOpenCamera = onOpenCamera,
        onOpenAi = onOpenAi,
        onOpenSettings = onOpenSettings,
        bottomBarSpace = bottomBarSpace,
        modifier = modifier,
    )
}

/**
 * 首页（M3 Expressive 版）。
 *
 * 结构上把原来的"一整张卡片里竖排 5 条入口"拆成两层：
 * 1. 打印机英雄卡：状态文案、电量条、状态胶囊全部带弹簧过渡；
 * 2. 创作工具瀑布：2 列瓦片，逐块错峰入场，按压有物理回弹。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    capsule: HomePrinterCapsule,
    onOpenConnect: () -> Unit,
    onOpenText: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenAi: () -> Unit = {},
    onOpenSettings: () -> Unit,
    bottomBarSpace: Dp = Dp(0f),
    modifier: Modifier = Modifier,
) {
    val glassActive = rememberLiquidGlassActive()
    val paddingM = dimensionResource(ResR.dimen.dimen_global_padding_m)

    // 首帧之后再打开入场动画，避免和 NavHost 转场同时位移造成"抖动"。
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.dashboard_title)) },
                actions = {
                    AppIconButton(
                        onClick = onOpenSettings,
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.home_settings),
                    )
                },
                modifier = Modifier.nativeGlassSurface(
                    shape = RoundedCornerShape(0.dp),
                    enabled = glassActive,
                    fallbackColor = MaterialTheme.colorScheme.surface,
                ),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = paddingM)
                .padding(top = paddingM)
                // 底部留白放在滚动内容内部：列表末项能停在悬浮导航栏上方，
                // 滚动过程中卡片仍可穿过导航栏下方（液态玻璃毛玻璃取样）。
                .padding(bottom = bottomBarSpace + paddingM),
            verticalArrangement = Arrangement.spacedBy(paddingM),
        ) {
            AnimatedVisibility(
                visible = appeared,
                enter = staggerEnter(delayMillis = 0),
            ) {
                PrinterHeroCard(capsule = capsule, onClick = onOpenConnect)
            }

            AnimatedVisibility(
                visible = appeared,
                enter = staggerEnter(delayMillis = 70),
            ) {
                AppText(
                    text = stringResource(R.string.home_creative_tools),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            ToolGrid(
                appeared = appeared,
                firstDelayMillis = 120,
                tiles = listOf(
                    ToolTile(
                        icon = Icons.Filled.Description,
                        title = stringResource(ResR.string.creative_text_print),
                        subtitle = stringResource(ResR.string.creative_text_print_desc),
                        onClick = onOpenText,
                    ),
                    ToolTile(
                        icon = Icons.Filled.Image,
                        title = stringResource(ResR.string.creative_image_print),
                        subtitle = stringResource(ResR.string.creative_image_print_desc),
                        onClick = onOpenImage,
                    ),
                    ToolTile(
                        icon = Icons.Filled.PhotoCamera,
                        title = stringResource(ResR.string.creative_photo_print),
                        subtitle = stringResource(ResR.string.creative_photo_print_desc),
                        onClick = onOpenCamera,
                    ),
                    ToolTile(
                        icon = Icons.Filled.AutoAwesome,
                        title = stringResource(ResR.string.creative_ai_print),
                        subtitle = stringResource(ResR.string.creative_ai_print_desc),
                        onClick = onOpenAi,
                    ),
                    ToolTile(
                        icon = Icons.AutoMirrored.Filled.Label,
                        title = stringResource(ResR.string.creative_label_print),
                        subtitle = null,
                        badge = stringResource(ResR.string.common_coming_soon),
                        onClick = null,
                    ),
                ),
            )
        }
    }
}

/** 错峰入场：淡入 + 轻微上移，时间曲线与全局动效语言保持一致。 */
private fun staggerEnter(delayMillis: Int): EnterTransition =
    fadeIn(
        animationSpec = tween(
            durationMillis = 260,
            delayMillis = delayMillis,
            easing = LinearOutSlowInEasing,
        ),
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = 320,
            delayMillis = delayMillis,
            easing = LinearOutSlowInEasing,
        ),
        initialOffsetY = { fullHeight -> fullHeight / 14 },
    )

/**
 * 打印机英雄卡：左侧状态图标（连接/连接中/未连接三态色）+ 标题与状态行，
 * 下方是电量条与 2×2 状态胶囊；整卡点击进连接管理页。
 */
@Composable
private fun PrinterHeroCard(
    capsule: HomePrinterCapsule,
    onClick: () -> Unit,
) {
    val motion = rememberAppMotion()
    val glassActive = rememberLiquidGlassActive()
    val interaction = remember { MutableInteractionSource() }
    val paddingM = dimensionResource(ResR.dimen.dimen_global_padding_m)
    val paddingS = dimensionResource(ResR.dimen.dimen_global_padding_s)

    val connectedName = capsule.connectedDevice?.let { it.name.ifBlank { it.address } }
    val connected = connectedName != null
    val accentTarget = when {
        connected -> MaterialTheme.colorScheme.primary
        capsule.isConnecting -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    val accent by androidx.compose.animation.animateColorAsState(
        targetValue = accentTarget,
        animationSpec = motion.effectSpec(),
        label = "printerAccent",
    )
    val statusText = when {
        connectedName != null -> stringResource(R.string.home_connected, connectedName)
        capsule.isConnecting -> stringResource(R.string.home_connecting)
        else -> stringResource(R.string.home_disconnected)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            // 过热异常项出现/消失时，整卡高度带弹簧节奏扩展，而不是瞬间跳变。
            .animateContentSize(animationSpec = motion.spatialSpec())
            .pressScale(interaction, enabled = true)
            .nativeGlassSurface(
                shape = MaterialTheme.shapes.large,
                enabled = glassActive,
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .glassAwareOutline(MaterialTheme.shapes.large, glassActive)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingM),
            verticalArrangement = Arrangement.spacedBy(paddingS),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(dimensionResource(ResR.dimen.dimen_global_touch_target))
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(
                        imageVector = Icons.Filled.Print,
                        contentDescription = stringResource(R.string.home_capsule_printer_title),
                        tint = accent,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = paddingM),
                ) {
                    AppText(
                        text = stringResource(R.string.home_capsule_printer_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            targetState = statusText,
                            transitionSpec = {
                                (fadeIn(motion.effectFastSpec()) +
                                    slideInVertically(motion.effectFastSpec()) { it / 3 })
                                    .togetherWith(
                                        fadeOut(motion.effectFastSpec()) +
                                            slideOutVertically(motion.effectFastSpec()) { -it / 3 },
                                    )
                            },
                            label = "printerStatus",
                        ) { text ->
                            AppText(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = accent,
                            )
                        }
                        if (capsule.isConnecting) {
                            ConnectingPulseDot(color = accent)
                        }
                    }
                }
                AppIcon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.home_capsule_open),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            capsule.batteryPercent?.let { percent ->
                BatteryBar(percent = percent)
            }

            PrinterStatusGrid(capsule = capsule)
        }
    }
}

/** 连接中呼吸点：缩放与透明度反相呼吸，让"正在连接"有生命感。 */
@Composable
private fun ConnectingPulseDot(color: Color) {
    val motion = rememberAppMotion()
    val paddingS = dimensionResource(ResR.dimen.dimen_global_padding_s)
    val dotModifier = Modifier
        .padding(start = paddingS)
        .size(8.dp)
        .clip(CircleShape)
        .background(color)

    if (motion.isReduced) {
        Box(modifier = dotModifier)
        return
    }

    val transition = rememberInfiniteTransition(label = "connectingPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "connectingPulseScale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "connectingPulseAlpha",
    )
    Box(
        modifier = dotModifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        },
    )
}

/** 电量条：数值变化时按弹簧节奏推进，低电自动转 error 色。 */
@Composable
private fun BatteryBar(percent: Int) {
    val motion = rememberAppMotion()
    val paddingS = dimensionResource(ResR.dimen.dimen_global_padding_s)
    val low = percent <= BATTERY_LOW_PERCENT
    val target = (percent.coerceIn(0, 100) / 100f)
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = motion.spatialSpec(),
        label = "batteryFraction",
    )
    val color by androidx.compose.animation.animateColorAsState(
        targetValue = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        animationSpec = motion.effectSpec(),
        label = "batteryColor",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = paddingS)
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** 迷你状态项展示数据（文案已在组合内解析）。 */
private data class CapsuleStatusItem(
    val icon: ImageVector,
    val text: String,
    val contentDescription: String,
    val isError: Boolean,
)

/** 2×2 迷你状态区：异常项用 error 色，正常项用 onSurfaceVariant；过热时追加第五项。 */
@Composable
private fun PrinterStatusGrid(capsule: HomePrinterCapsule) {
    val statuses = capsule.activeStatuses
    val lowBattery = PrinterStatus.LowBattery in statuses
    val coverOpen = PrinterStatus.CoverOpen in statuses
    val outOfPaper = PrinterStatus.OutOfPaper in statuses
    val overheat = PrinterStatus.Overheat in statuses
    val connected = capsule.connectedDevice != null
    val batteryPercent = capsule.batteryPercent
    val items = listOfNotNull(
        CapsuleStatusItem(
            icon = if (lowBattery || (batteryPercent != null && batteryPercent <= BATTERY_LOW_PERCENT)) {
                Icons.Filled.BatteryAlert
            } else {
                Icons.Filled.BatteryFull
            },
            text = if (batteryPercent != null) {
                stringResource(R.string.home_capsule_battery_percent, batteryPercent)
            } else {
                stringResource(
                    if (lowBattery) R.string.home_capsule_battery_low else R.string.home_capsule_battery_normal
                )
            },
            contentDescription = stringResource(R.string.home_capsule_battery),
            isError = lowBattery || (batteryPercent != null && batteryPercent <= BATTERY_LOW_PERCENT),
        ),
        CapsuleStatusItem(
            icon = if (coverOpen) Icons.Filled.Cancel else Icons.Filled.CheckCircle,
            text = stringResource(
                if (coverOpen) R.string.home_capsule_cover_open else R.string.home_capsule_cover_closed
            ),
            contentDescription = stringResource(R.string.home_capsule_cover),
            isError = coverOpen,
        ),
        CapsuleStatusItem(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            text = stringResource(
                if (outOfPaper) R.string.home_capsule_paper_out else R.string.home_capsule_paper_ok
            ),
            contentDescription = stringResource(R.string.home_capsule_paper),
            isError = outOfPaper,
        ),
        CapsuleStatusItem(
            icon = when {
                connected -> Icons.Filled.Bluetooth
                capsule.isConnecting -> Icons.Filled.Link
                else -> Icons.Filled.BluetoothDisabled
            },
            text = stringResource(
                when {
                    connected -> R.string.home_connected_badge
                    capsule.isConnecting -> R.string.home_connecting
                    else -> R.string.home_disconnected
                }
            ),
            contentDescription = stringResource(R.string.home_capsule_connection),
            isError = false,
        ),
        if (overheat) {
            CapsuleStatusItem(
                icon = Icons.Filled.Thermostat,
                text = stringResource(R.string.home_capsule_overheat),
                contentDescription = stringResource(R.string.home_capsule_overheat),
                isError = true,
            )
        } else {
            null
        },
    )
    val gap = dimensionResource(R.dimen.dimen_dashboard_status_gap)
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                rowItems.forEach { item -> CapsuleStatusChip(item) }
                if (rowItems.size == 1) {
                    // 落单项保持半宽节奏，避免第五项被拉伸成全宽。
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CapsuleStatusChip(item: CapsuleStatusItem) {
    val color by androidx.compose.animation.animateColorAsState(
        targetValue = if (item.isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = rememberAppMotion().effectSpec(),
        label = "capsuleStatusColor",
    )
    val gap = dimensionResource(R.dimen.dimen_dashboard_status_gap)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .padding(horizontal = gap, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            imageVector = item.icon,
            contentDescription = item.contentDescription,
            tint = color,
            modifier = Modifier.size(dimensionResource(R.dimen.dimen_dashboard_status_icon)),
        )
        AppText(
            text = item.text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(start = gap),
        )
    }
}

/** 创作工具瓦片数据。 */
private data class ToolTile(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val badge: String? = null,
    val onClick: (() -> Unit)?,
)

/** 2 列工具瀑布：逐块错峰入场（每块 +45ms），未开放项降到低饱和。 */
@Composable
private fun ToolGrid(
    tiles: List<ToolTile>,
    appeared: Boolean,
    firstDelayMillis: Int,
) {
    val gap = dimensionResource(ResR.dimen.dimen_global_padding_s)
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        tiles.chunked(2).forEachIndexed { rowIndex, rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                rowTiles.forEachIndexed { columnIndex, tile ->
                    val index = rowIndex * 2 + columnIndex
                    AnimatedVisibility(
                        visible = appeared,
                        enter = staggerEnter(firstDelayMillis + index * 45),
                        modifier = Modifier.weight(1f),
                    ) {
                        ToolTileCard(tile = tile)
                    }
                }
                if (rowTiles.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolTileCard(tile: ToolTile) {
    val motion = rememberAppMotion()
    val glassActive = rememberLiquidGlassActive()
    val interaction = remember { MutableInteractionSource() }
    val enabled = tile.onClick != null
    val paddingM = dimensionResource(ResR.dimen.dimen_global_padding_m)
    val paddingS = dimensionResource(ResR.dimen.dimen_global_padding_s)

    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val iconContainer = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val iconTint = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.outline
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = motion.spatialSpec())
            .pressScale(interaction, enabled)
            .nativeGlassSurface(
                shape = MaterialTheme.shapes.large,
                enabled = glassActive,
                fallbackColor = containerColor,
            )
            .glassAwareOutline(MaterialTheme.shapes.large, glassActive)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = { tile.onClick?.invoke() },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingM),
            verticalArrangement = Arrangement.spacedBy(paddingS),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconContainer),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    imageVector = tile.icon,
                    contentDescription = tile.title,
                    tint = iconTint,
                )
            }
            AppText(
                text = tile.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                tile.subtitle != null -> AppText(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                tile.badge != null -> AppText(
                    text = tile.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        capsule = HomePrinterCapsule(
            activeStatuses = setOf(PrinterStatus.LowBattery, PrinterStatus.Overheat),
            batteryPercent = 18,
        ),
        onOpenConnect = {},
        onOpenText = {},
        onOpenImage = {},
        onOpenCamera = {},
        onOpenAi = {},
        onOpenSettings = {},
    )
}

/** 电量百分比低于该值视为低电（红色警示）。 */
private const val BATTERY_LOW_PERCENT = 20
