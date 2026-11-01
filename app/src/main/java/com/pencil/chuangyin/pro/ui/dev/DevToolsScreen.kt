package com.pencil.chuangyin.pro.ui.dev

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.printer.ConnectionState
import com.pencil.chuangyin.printer.PrinterDevice
import com.pencil.chuangyin.printer.PrinterDeviceState
import com.pencil.chuangyin.printer.PrinterStatus
import com.pencil.chuangyin.core.ui.components.AppAlertDialog
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppSwitch
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.GlassPanel
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.ui.components.viewer.AppImageViewerDialog
import com.pencil.chuangyin.resources.R as ResR

/**
 * 开发者调试：高权限诊断面板。
 *
 * 覆盖打印机链路（扫描/连接/裸指令/走纸/测试打印）、网络探测、文件读写、AI 配置与
 * 内置提示词覆盖、主题与引导标记重置，以及全程操作日志。用户平时用不到，但便于现场排障。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DevToolsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val device by viewModel.deviceState.collectAsStateWithLifecycle()
    val theme by viewModel.themeState.collectAsStateWithLifecycle(initialValue = null)

    DevToolsScreen(
        viewModel = viewModel,
        state = state,
        connection = connection,
        device = device,
        darkTheme = theme?.darkTheme ?: false,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevToolsScreen(
    viewModel: DevToolsViewModel,
    state: DevToolsUiState,
    connection: ConnectionState,
    device: PrinterDeviceState,
    darkTheme: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassActive = rememberLiquidGlassActive()
    val topBarFallback = MaterialTheme.colorScheme.surface

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    var hexInput by remember { mutableStateOf("10 FF 40") }
    var thicknessInput by remember { mutableStateOf("2") }
    var copiesInput by remember { mutableStateOf("1") }
    var testText by remember { mutableStateOf("开发者调试打印\nPencil ChuangYin Pro 2.1.0.0\n${viewModel.versionName()}") }
    var urlInput by remember { mutableStateOf("https://www.pixiv.net") }
    var downloadUrl by remember { mutableStateOf("https://www.pixiv.net/robots.txt") }
    var showImageViewer by remember { mutableStateOf(false) }
    var showTextPreview by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importPickedFile)
    }
    val mountLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::mountOutputTree)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = "开发者调试") },
                navigationIcon = {
                    AppIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResourceBack(),
                        glass = false,
                    )
                },
                modifier = Modifier.nativeGlassSurface(
                    shape = RoundedCornerShape(0.dp),
                    enabled = glassActive,
                    fallbackColor = topBarFallback,
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
                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m))
                .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_xl)),
        ) {
            // ================= 运行环境 =================
            DevSection("运行环境", appeared, 0) {
                AppText(
                    text = viewModel.environmentSummary(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // ================= 打印机调试 =================
            DevSection("打印机调试", appeared, 60) {
                PrinterStatusPanel(connection = connection, device = device)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(
                        text = if (state.scanning) "停止扫描" else "扫描蓝牙设备",
                        onClick = { if (state.scanning) viewModel.stopScan() else viewModel.startScan() },
                        modifier = Modifier.weight(1f),
                    )
                    AppOutlinedButton(
                        text = "查询状态",
                        onClick = viewModel::requestStatus,
                        modifier = Modifier.weight(1f),
                        enabled = connection is ConnectionState.Connected,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(
                        text = "断开",
                        onClick = viewModel::disconnect,
                        modifier = Modifier.weight(1f),
                        enabled = connection is ConnectionState.Connected,
                    )
                    AppOutlinedButton(
                        text = "紧急停止",
                        onClick = viewModel::emergencyStop,
                        modifier = Modifier.weight(1f),
                        enabled = connection is ConnectionState.Connected,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(
                        text = "走纸自测",
                        onClick = viewModel::testFeed,
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy && connection is ConnectionState.Connected,
                    )
                    AppOutlinedButton(
                        text = "已配对设备",
                        onClick = {
                            viewModel.bondedPrinters().forEach { viewModel.connect(it) }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = false,
                    )
                }
                Spacer(Modifier.height(8.dp))
                AppText("已配对设备（点击连接）", style = MaterialTheme.typography.labelLarge)
                val bonded = viewModel.bondedPrinters()
                if (bonded.isEmpty()) {
                    AppText(
                        "无已配对设备，请先在系统蓝牙中配对",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    bonded.forEach { deviceItem ->
                        DeviceRow(device = deviceItem) { viewModel.connect(deviceItem) }
                    }
                }
                if (state.scanResults.isNotEmpty()) {
                    AppText(
                        "扫描结果（点击连接）",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    state.scanResults.forEach { deviceItem ->
                        DeviceRow(device = deviceItem) { viewModel.connect(deviceItem) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it },
                    label = "十六进制裸指令",
                    placeholder = "10 FF 40",
                )
                Spacer(Modifier.height(8.dp))
                AppOutlinedButton(
                    text = "发送裸指令",
                    onClick = { viewModel.sendHex(hexInput) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = connection is ConnectionState.Connected,
                )
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    label = "测试打印文本",
                    singleLine = false,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = thicknessInput,
                        onValueChange = { thicknessInput = it },
                        label = "浓度",
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = copiesInput,
                        onValueChange = { copiesInput = it },
                        label = "份数",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                AppOutlinedButton(
                    text = "打印测试文本",
                    onClick = {
                        viewModel.printTestText(
                            text = testText,
                            thickness = thicknessInput.toIntOrNull() ?: 2,
                            copies = copiesInput.toIntOrNull() ?: 1,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy && connection is ConnectionState.Connected,
                )
            }

            // ================= 网络自检 =================
            DevSection("网络自检", appeared, 120) {
                AppTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = "探测地址",
                    placeholder = "https://…",
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(
                        text = if (state.probing) "探测中…" else "探测",
                        onClick = { viewModel.probeNetwork(urlInput) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.probing,
                    )
                    AppOutlinedButton(
                        text = "AI 接口",
                        onClick = viewModel::probeAiEndpoint,
                        modifier = Modifier.weight(1f),
                        enabled = !state.probing,
                    )
                    AppOutlinedButton(
                        text = "P 站",
                        onClick = { viewModel.probeNetwork("https://www.pixiv.net") },
                        modifier = Modifier.weight(1f),
                        enabled = !state.probing,
                    )
                }
                Spacer(Modifier.height(8.dp))
                AppOutlinedButton(
                    text = if (state.probing) "请求中…" else "AI 对话请求测试（真实调用一次）",
                    onClick = viewModel::probeAiChat,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.probing,
                )
                state.networkResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    AppText(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ================= 下载 · 保存 · 查看器 =================
            DevSection("下载 · 保存 · 查看器", appeared, 160) {
                AppTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    label = "下载地址",
                    placeholder = "https://…",
                )
                Spacer(Modifier.height(8.dp))
                AppOutlinedButton(
                    text = "下载到工作区",
                    onClick = { viewModel.downloadTest(downloadUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                )
                state.downloadResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    AppText(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(
                        text = "保存到相册",
                        onClick = viewModel::saveSampleToGallery,
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    )
                    AppOutlinedButton(
                        text = "生成查看器样例",
                        onClick = viewModel::generateViewerSamples,
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(
                        text = "打开图片查看器",
                        onClick = { showImageViewer = true },
                        modifier = Modifier.weight(1f),
                        enabled = !state.sampleImagePath.isNullOrBlank(),
                    )
                    AppOutlinedButton(
                        text = "打开文本查看器",
                        onClick = { showTextPreview = true },
                        modifier = Modifier.weight(1f),
                        enabled = !state.sampleText.isNullOrBlank(),
                    )
                }
                if (state.sampleImagePath.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    AppText(
                        "先「生成查看器样例」，再打开图片 / 文本查看器验证显示与编辑。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ================= 文件系统 =================
            DevSection("文件与存储", appeared, 180) {
                AppText(
                    "工作区：${viewModel.workspacePath()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(
                        text = "刷新目录",
                        onClick = viewModel::refreshFiles,
                        modifier = Modifier.weight(1f),
                    )
                    AppOutlinedButton(
                        text = "读写删自检",
                        onClick = viewModel::runFileRoundTrip,
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (state.files.isEmpty()) {
                    AppText(
                        "工作区为空",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.files.take(20).forEach { file ->
                        AppText(
                            text = file,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }

            // ================= 文件导入与挂载 =================
            DevSection("文件导入与挂载", appeared, 210) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(
                        text = "导入文件",
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    )
                    AppOutlinedButton(
                        text = "挂载输出目录",
                        onClick = { mountLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    )
                }
                Spacer(Modifier.height(8.dp))
                AppOutlinedButton(
                    text = "撤销输出目录授权",
                    onClick = viewModel::unmountOutputTree,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                AppText(
                    text = viewModel.mountSummary(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ================= AI 与提示词 =================
            DevSection("AI 与提示词", appeared, 240) {
                AppText(
                    text = viewModel.activeModelSummary(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    value = state.promptDraft,
                    onValueChange = viewModel::updatePromptDraft,
                    label = "自定义系统提示词（留空使用内置）",
                    singleLine = false,
                    placeholder = "粘贴自定义提示词，将整体替换内置提示词",
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(
                        text = "保存覆盖",
                        onClick = viewModel::savePromptDraft,
                        modifier = Modifier.weight(1f),
                    )
                    AppOutlinedButton(
                        text = "恢复内置",
                        onClick = viewModel::clearPromptOverride,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                BuiltInPromptPanel(promptProvider = viewModel::builtInPrompt)
            }

            // ================= UI 快速调整 =================
            DevSection("UI 快速调整", appeared, 300) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        AppText("深色模式", style = MaterialTheme.typography.titleSmall)
                        AppText(
                            "切换后全局立即生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppSwitch(
                        checked = darkTheme,
                        onCheckedChange = { viewModel.toggleDarkTheme() },
                    )
                }
                Spacer(Modifier.height(10.dp))
                AppText("动效预设", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("expressive" to "表现", "full" to "完整", "reduced" to "精简").forEach { (id, label) ->
                        AppOutlinedButton(
                            text = label,
                            onClick = { viewModel.setMotionPreset(id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ================= 工具与接口自检 =================
            DevSection("工具与接口自检", appeared, 330) {
                AppOutlinedButton(
                    text = "列出全部已注册工具并实调用 list_files",
                    onClick = viewModel::runToolSelfCheck,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                )
                state.toolReport?.let { report ->
                    Spacer(Modifier.height(8.dp))
                    AppText(
                        text = report,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }

            // ================= 重置与维护 =================
            DevSection("重置与维护", appeared, 360) {
                AppOutlinedButton(
                    text = "重置主题外观",
                    onClick = viewModel::resetTheme,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                AppOutlinedButton(
                    text = "清除引导与教程标记（冷启动后重新展示）",
                    onClick = viewModel::resetOnboarding,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ================= 操作日志 =================
            DevSection("操作日志", appeared, 420) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        "最近 ${state.logs.size} 条",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    AppIconButton(
                        onClick = viewModel::clearLogs,
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "清空日志",
                        glass = false,
                    )
                }
                Spacer(Modifier.height(6.dp))
                AppText(
                    text = if (state.logs.isEmpty()) "暂无日志" else state.logs.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                )
            }
        }
    }

    if (showImageViewer) {
        AppImageViewerDialog(
            models = listOf(state.sampleImagePath),
            title = "调试样例图",
            onDismiss = { showImageViewer = false },
        )
    }

    if (showTextPreview) {
        AppAlertDialog(
            onDismissRequest = { showTextPreview = false },
            confirmButton = {
                AppOutlinedButton(text = "关闭", onClick = { showTextPreview = false })
            },
            title = { AppText("调试样例文本", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    AppText(
                        text = state.sampleText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
        )
    }
}

/** 带错峰入场动画的调试分组卡片。 */
@Composable
private fun DevSection(
    title: String,
    visible: Boolean,
    delayMillis: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 240, delayMillis = delayMillis)) +
            slideInVertically(tween(durationMillis = 320, delayMillis = delayMillis)) { it / 12 },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AppText(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = dimensionResource(ResR.dimen.dimen_global_padding_m),
                    top = dimensionResource(ResR.dimen.dimen_global_padding_l),
                    bottom = dimensionResource(ResR.dimen.dimen_global_padding_s),
                ),
            )
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_l)),
                enabled = rememberLiquidGlassActive(),
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    content()
                }
            }
        }
    }
}

/** 打印机实时状态：连接态 + 电量 + 当前故障集合。 */
@Composable
private fun PrinterStatusPanel(
    connection: ConnectionState,
    device: PrinterDeviceState,
) {
    val statusColor by animateColorAsState(
        targetValue = when (connection) {
            is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
            is ConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
            is ConnectionState.Disconnected -> MaterialTheme.colorScheme.error
        },
    )
    val statusText = when (connection) {
        is ConnectionState.Connected -> "已连接：${connection.device.name}"
        is ConnectionState.Connecting -> "连接中：${connection.device.name}"
        is ConnectionState.Disconnected -> "未连接"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(statusColor, RoundedCornerShape(50)),
        )
        AppText(
            text = statusText,
            style = MaterialTheme.typography.titleSmall,
            color = statusColor,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    AppText(
        text = buildString {
            append("电量：")
            append(device.batteryPercent?.let { "$it%" } ?: "未知")
            append("    故障：")
            append(device.activeStatuses.takeIf { it.isNotEmpty() }?.joinToString { it.statusLabel() } ?: "无")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun PrinterStatus.statusLabel(): String = when (this) {
    PrinterStatus.OutOfPaper -> "缺纸"
    PrinterStatus.CoverOpen -> "开盖"
    PrinterStatus.Overheat -> "过热"
    PrinterStatus.LowBattery -> "低电量"
}

/** 单个设备行：名称 + 地址，点击触发连接。 */
@Composable
private fun DeviceRow(device: PrinterDevice, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(device.name, style = MaterialTheme.typography.bodyMedium)
            AppText(
                device.address,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppText(
            "连接",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 内置提示词预览：默认折叠，展开后完整展示（便于对照自定义覆盖）。 */
@Composable
private fun BuiltInPromptPanel(promptProvider: () -> String) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppText(
            text = "内置系统提示词",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        AppOutlinedButton(
            text = if (expanded) "收起" else "展开",
            onClick = { expanded = !expanded },
        )
    }
    if (expanded) {
        AppText(
            text = promptProvider(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        )
    } else {
        AppText(
            text = promptProvider().take(160) + "…",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
        )
    }
}

@Composable
private fun stringResourceBack(): String = androidx.compose.ui.res.stringResource(ResR.string.label_back)
