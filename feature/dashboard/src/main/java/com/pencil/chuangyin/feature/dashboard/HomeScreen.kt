package com.pencil.chuangyin.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
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
import com.pencil.chuangyin.printer.PrinterStatus
import com.pencil.chuangyin.resources.R as ResR

@Composable
fun HomeRoute(
    onOpenConnect: () -> Unit,
    onOpenText: () -> Unit,
    onOpenScanner: () -> Unit,
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
        onOpenScanner = onOpenScanner,
        onOpenCamera = onOpenCamera,
        onOpenAi = onOpenAi,
        onOpenSettings = onOpenSettings,
        bottomBarSpace = bottomBarSpace,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    capsule: HomePrinterCapsule,
    onOpenConnect: () -> Unit,
    onOpenText: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenAi: () -> Unit = {},
    onOpenSettings: () -> Unit,
    bottomBarSpace: Dp = Dp(0f),
    modifier: Modifier = Modifier,
) {
    val glassActive = rememberLiquidGlassActive()
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
                        // 顶栏设置按钮不使用液态玻璃（避免导航区玻璃滥用）。
                        glass = false,
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
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m))
                // 底部留白放在滚动内容内部：列表末项能停在悬浮导航栏上方，
                // 滚动过程中卡片仍可穿过导航栏下方（液态玻璃毛玻璃取样）。
                .padding(bottom = bottomBarSpace),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            PrinterStatusCapsule(capsule = capsule, onClick = onOpenConnect)
            CreativeToolsCard(
                onOpenText = onOpenText,
                onOpenScanner = onOpenScanner,
                onOpenCamera = onOpenCamera,
                onOpenAi = onOpenAi,
            )
        }
    }
}

/**
 * 打印机状态胶囊：左侧打印图标 + 标题/连接状态行，右侧 2×2 迷你状态项
 * （电量/合盖/缺纸/连接），过热时追加一项；整卡点击进连接管理页。
 */
@Composable
private fun PrinterStatusCapsule(
    capsule: HomePrinterCapsule,
    onClick: () -> Unit,
) {
    val connectedName = capsule.connectedDevice?.let { it.name.ifBlank { it.address } }
    val connected = connectedName != null
    val glassActive = rememberLiquidGlassActive()
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .nativeGlassSurface(
                shape = CardDefaults.shape,
                enabled = glassActive,
                fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            .glassAwareOutline(MaterialTheme.shapes.medium, glassActive)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                imageVector = Icons.Filled.Print,
                contentDescription = stringResource(R.string.home_capsule_printer_title),
                tint = when {
                    connected -> MaterialTheme.colorScheme.primary
                    capsule.isConnecting -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_touch_target)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = dimensionResource(ResR.dimen.dimen_global_padding_m)),
            ) {
                AppText(
                    text = stringResource(R.string.home_capsule_printer_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                AppText(
                    text = when {
                        connectedName != null -> stringResource(R.string.home_connected, connectedName)
                        capsule.isConnecting -> stringResource(R.string.home_connecting)
                        else -> stringResource(R.string.home_disconnected)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        connected -> MaterialTheme.colorScheme.primary
                        capsule.isConnecting -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            PrinterStatusGrid(capsule = capsule)
            AppIcon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.home_capsule_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.dimen_dashboard_status_gap))) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s))) {
                rowItems.forEach { item -> CapsuleStatusChip(item) }
            }
        }
    }
}

@Composable
private fun CapsuleStatusChip(item: CapsuleStatusItem) {
    val color = if (item.isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
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
            modifier = Modifier.padding(start = dimensionResource(R.dimen.dimen_dashboard_status_gap)),
        )
    }
}

/** 创作工具区：文本/文稿扫描/拍照/AI 打印入口，每条带一句功能描述。 */
@Composable
private fun CreativeToolsCard(
    onOpenText: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenAi: () -> Unit,
) {
    val glassActive = rememberLiquidGlassActive()
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .nativeGlassSurface(
                shape = CardDefaults.shape,
                enabled = glassActive,
                fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            .glassAwareOutline(MaterialTheme.shapes.medium, glassActive),
    ) {
        Column(
            modifier = Modifier.padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
        ) {
            AppText(
                text = stringResource(R.string.home_creative_tools),
                style = MaterialTheme.typography.titleMedium,
            )
            // 一排 2 个功能栏：按入口列表分块，每块一行、等宽并排。
            val tools = listOf(
                CreativeTool(Icons.Filled.Description, stringResource(ResR.string.creative_text_print), stringResource(ResR.string.creative_text_print_desc), onOpenText),
                CreativeTool(Icons.Filled.DocumentScanner, stringResource(ResR.string.creative_scan_print), stringResource(ResR.string.creative_scan_print_desc), onOpenScanner),
                CreativeTool(Icons.Filled.PhotoCamera, stringResource(ResR.string.creative_photo_print), stringResource(ResR.string.creative_photo_print_desc), onOpenCamera),
                CreativeTool(Icons.Filled.AutoAwesome, stringResource(ResR.string.creative_ai_print), stringResource(ResR.string.creative_ai_print_desc), onOpenAi),
            )
            tools.chunked(2).forEach { rowTools ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
                ) {
                    rowTools.forEach { tool ->
                        CreativeToolEntry(
                            icon = tool.icon,
                            title = tool.title,
                            subtitle = tool.subtitle,
                            onClick = tool.onClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowTools.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 功能栏入口数据（标题 + 一句描述 + 点击行为）。 */
private data class CreativeTool(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
private fun CreativeToolEntry(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
    ) {
        AppIcon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
        )
        AppText(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            AppText(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        onOpenScanner = {},
        onOpenCamera = {},
        onOpenAi = {},
        onOpenSettings = {},
    )
}

/** 电量百分比低于该值视为低电（红色警示）。 */
private const val BATTERY_LOW_PERCENT = 20
