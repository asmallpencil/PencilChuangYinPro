package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.glass.GlassCard
import com.pencil.chuangyin.desktop.printer.ConnectionState
import com.pencil.chuangyin.desktop.printer.PrinterConnectionStore
import com.pencil.chuangyin.desktop.text.PrinterStatus
import com.pencil.chuangyin.desktop.theme.ThemeState
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import com.pencil.chuangyin.desktop.ui.motion.StaggerIn
import com.pencil.chuangyin.desktop.ui.motion.pressScale

/**
 * 首页 —— 与安卓端 feature/dashboard/HomeScreen.kt 结构 1:1 对齐：
 *   Scaffold(TopAppBar = 首页标题 + 设置齿轮)
 *   └ 滚动内容（16dp 内边距，item 间距 16dp）
 *       ├ PrinterStatusCapsule（打印机状态胶囊卡，点击进连接页）
 *       └ CreativeToolsCard（创作工具列表卡）
 *
 * 状态胶囊的数据直接镜像 [PrinterConnectionStore]（与安卓端同一套连接状态与故障集合）。
 */
@Composable
fun HomeScreen(
    themeState: ThemeState,
    bottomBarSpace: Dp,
    onOpenSettings: () -> Unit,
    onOpenTextPrint: () -> Unit,
    onOpenImagePrint: () -> Unit,
    onOpenAiChat: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    var hint by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = "铅笔创印Pro",
                actions = {
                    AppIconButton(
                        icon = AppIcon.Settings,
                        contentDescription = "设置",
                        onClick = onOpenSettings,
                    )
                },
            )
        },
        containerColor = Color.Transparent,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = bottomBarSpace),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StaggerIn(index = 0) {
                PrinterStatusCapsule(onClick = onOpenConnect)
            }
            StaggerIn(index = 1) {
                CreativeToolsCard(
                    onToolClick = { name ->
                        // 与安卓端导航一致：文本/图片/AI 打印进入各自编辑器；其余入口桌面端暂未接入。
                        when (name) {
                            "文本打印" -> onOpenTextPrint()
                            "图片打印" -> onOpenImagePrint()
                            "AI 打印" -> onOpenAiChat()
                            else -> hint = name
                        }
                    },
                )
            }
        }
    }

    hint?.let { name ->
        AlertDialog(
            onDismissRequest = { hint = null },
            title = { Text(name) },
            text = {
                Text(
                    "桌面端没有摄像头，无法直接拍照。\n" +
                        "请改用「图片打印」选择本地图片，或在手机拍照后用「图片打印」导入。"
                )
            },
            confirmButton = {
                TextButton(onClick = { hint = null }) { Text("知道了") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/** 电量低于该百分比视为低电（与安卓端 BATTERY_LOW_PERCENT 一致）。 */
private const val BATTERY_LOW_PERCENT = 20


/**
 * 打印机状态胶囊：左侧打印机图标 + 标题/连接状态行，右侧 2×2 迷你状态项
 * （电量 / 合盖 / 纸张 / 连接，过热时追加第五项），整卡点击进入连接页。
 */
@Composable
private fun PrinterStatusCapsule(onClick: () -> Unit) {
    val connection by PrinterConnectionStore.connectionState.collectAsState()
    val deviceState by PrinterConnectionStore.deviceState.collectAsState()

    val connectedName = (connection as? ConnectionState.Connected)?.device?.displayName
    val isConnecting = connection is ConnectionState.Connecting
    val connected = connectedName != null
    val interaction = remember { MutableInteractionSource() }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.98f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconView(
                icon = AppIcon.Printer,
                size = 48.dp,
                tint = when {
                    connected -> MaterialTheme.colorScheme.primary
                    isConnecting -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.outline
                },
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "打印机",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = when {
                        connectedName != null -> "已连接：$connectedName"
                        isConnecting -> "连接中…"
                        else -> "未连接"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        connected -> MaterialTheme.colorScheme.primary
                        isConnecting -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            // 与安卓端 PrinterStatusGrid 同构：电量 / 合盖 / 纸张 / 连接，异常项用 error 色。
            val statuses = deviceState.activeStatuses
            val lowBattery = PrinterStatus.LowBattery in statuses
            val coverOpen = PrinterStatus.CoverOpen in statuses
            val outOfPaper = PrinterStatus.OutOfPaper in statuses
            val overheat = PrinterStatus.Overheat in statuses
            val batteryPercent = deviceState.batteryPercent
            val batteryWarn = lowBattery || (batteryPercent != null && batteryPercent <= BATTERY_LOW_PERCENT)
            val cells = listOfNotNull(
                StatusCell(
                    icon = AppIcon.Battery,
                    text = when {
                        batteryPercent != null -> "电量 $batteryPercent%"
                        lowBattery -> "低电"
                        else -> "正常"
                    },
                    description = "电量",
                    warn = batteryWarn,
                ),
                StatusCell(
                    icon = if (coverOpen) AppIcon.Close else AppIcon.Check,
                    text = if (coverOpen) "开盖" else "已合盖",
                    description = "合盖状态",
                    warn = coverOpen,
                ),
                StatusCell(
                    icon = AppIcon.Paper,
                    text = if (outOfPaper) "缺纸" else "有纸",
                    description = "纸张状态",
                    warn = outOfPaper,
                ),
                StatusCell(
                    icon = AppIcon.Bluetooth,
                    text = when {
                        connected -> "已连接"
                        isConnecting -> "连接中…"
                        else -> "未连接"
                    },
                    description = "连接状态",
                    warn = false,
                ),
                if (overheat) {
                    StatusCell(
                        icon = AppIcon.Thermometer,
                        text = "过热",
                        description = "过热",
                        warn = true,
                    )
                } else {
                    null
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { cell ->
                            StatusItem(cell)
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            AppIconView(
                icon = AppIcon.ChevronRight,
                size = 24.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class StatusCell(
    val icon: AppIcon,
    val text: String,
    val description: String,
    val warn: Boolean,
)

@Composable
private fun StatusItem(cell: StatusCell, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIconView(
            icon = cell.icon,
            size = 16.dp,
            tint = if (cell.warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = cell.text,
            style = MaterialTheme.typography.labelSmall,
            color = if (cell.warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 创作工具卡：标题 + 5 个入口（文本/图片/拍照/AI/标签）。 */
@Composable
private fun CreativeToolsCard(onToolClick: (String) -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = "创作工具",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.size(8.dp))
        val tools = listOf(
            ToolEntry(AppIcon.TextTool, "文本打印", "输入文字，排版后打印", null),
            ToolEntry(AppIcon.ImageTool, "图片打印", "选择相册图片打印", null),
            ToolEntry(AppIcon.CameraTool, "拍照打印", "拍摄后即刻打印", null),
            ToolEntry(AppIcon.AiTool, "AI 打印", "让 AI 生成内容再打印", null),
            // 与安卓端一致：标签打印未开放，显示「敬请期待」徽标且不可点击。
            ToolEntry(AppIcon.TagTool, "标签打印", null, "敬请期待"),
        )
        // 与安卓端一致：一排 2 个并排的功能瓦片，奇数项末行补一个空位保持对齐。
        tools.chunked(2).forEachIndexed { rowIndex, rowTools ->
            StaggerIn(index = rowIndex + 2, step = 45, offsetDivisor = 40) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowTools.forEach { tool ->
                        CreativeToolTile(
                            tool = tool,
                            onToolClick = onToolClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowTools.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 单个功能瓦片：图标 + 标题 + 描述，纵向排布（对齐安卓端 CreativeToolEntry）。 */
@Composable
private fun CreativeToolTile(
    tool: ToolEntry,
    onToolClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = tool.badge == null
    val interaction = remember(tool.title) { MutableInteractionSource() }
    Column(
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier
                        .pressScale(interaction, pressedScale = 0.97f)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { onToolClick(tool.title) }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 8.dp),
    ) {
        AppIconView(
            icon = tool.icon,
            size = 24.dp,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = tool.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (tool.badge != null) {
            Text(
                text = tool.badge,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (tool.subtitle != null) {
            Text(
                text = tool.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class ToolEntry(
    val icon: AppIcon,
    val title: String,
    val subtitle: String?,
    val badge: String?,
)
