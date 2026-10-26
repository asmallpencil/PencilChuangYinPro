package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.printer.BluetoothAvailability
import com.pencil.chuangyin.desktop.printer.ConnectError
import com.pencil.chuangyin.desktop.printer.PrinterConnectStore
import com.pencil.chuangyin.desktop.printer.PrinterConnectUiState
import com.pencil.chuangyin.desktop.printer.PrinterDevice
import com.pencil.chuangyin.desktop.printer.TestFeedMessage
import com.pencil.chuangyin.desktop.text.PrinterStatus
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView

/**
 * 「连接打印机」页 —— 与安卓端 feature/dashboard/ConnectScreen.kt 结构 1:1 对齐：
 *   TopAppBar(返回)
 *   ├ ConnectionHeader（蓝牙图标 + 标题 + 已连接 / 连接中 / 未连接 + 断开 + 故障文案）
 *   ├ 已连接时：走纸测试 + 说明
 *   ├ 配对提示 + 「前往系统蓝牙设置配对」
 *   ├ 已配对设备列表（DeviceRow：名称 / 地址 / 已连接徽标 / 连接按钮）
 *   └ 刷新设备列表
 *
 * 唯一的平台差异：Windows 扫描周边蓝牙设备需要 WinRT 异步互操作，桌面端改为「先配对、
 * 再刷新已配对列表」，因此按钮文案是「刷新设备列表」而非「搜索附近设备」。
 */
@Composable
fun ConnectScreen(onBack: () -> Unit) {
    val state by PrinterConnectStore.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 进入页面刷新一次（对齐安卓端 ON_RESUME 刷新已配对列表）。
    LaunchedEffect(Unit) { PrinterConnectStore.refresh() }

    val errorText = state.error?.let { connectErrorText(it) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            PrinterConnectStore.clearError()
        }
    }
    val testText = state.testMessage?.let { testFeedText(it) }
    LaunchedEffect(testText) {
        if (testText != null) {
            snackbarHostState.showSnackbar(testText)
            PrinterConnectStore.clearTestMessage()
        }
    }

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = "连接打印机",
                navigationIcon = {
                    AppIconButton(
                        icon = AppIcon.Back,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionHeader(state = state)

            if (state.isConnected) {
                AppPrimaryButton(
                    text = if (state.isTesting) "测试中…" else "走纸测试",
                    onClick = { PrinterConnectStore.testFeed() },
                    enabled = !state.isTesting,
                )
                Text(
                    text = "点击后若打印机吐出一小段空白，说明连接与协议正常",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.availability == BluetoothAvailability.NoAdapter) {
                Text(
                    text = "未检测到蓝牙适配器，请确认电脑蓝牙已开启后再刷新",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = "连接方式：先在系统「设置 → 蓝牙和其他设备」中与打印机完成配对" +
                    "（被归类到「其他设备」里也算已配对），再回到本页点击对应设备的「连接」。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppSecondaryButton(
                text = "前往系统蓝牙设置配对",
                onClick = { PrinterConnectStore.openSystemBluetoothSettings() },
            )

            if (state.serialPorts.isNotEmpty()) {
                Text(
                    text = "已识别蓝牙串口：${state.serialPorts.joinToString("、")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DeviceSection(
                sectionTitle = "已配对设备",
                devices = state.devices,
                emptyText = "暂无已配对设备，请先到系统蓝牙设置中配对",
                state = state,
            )

            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                AppSecondaryButton(
                    text = "刷新中…",
                    onClick = {},
                    enabled = false,
                )
            } else {
                AppSecondaryButton(
                    text = "刷新设备列表",
                    onClick = { PrinterConnectStore.refresh() },
                )
            }
        }
    }
}

/** 连接状态头部：图标 + 标题 + 状态文案 + 断开按钮 + 故障文案（error 色）。 */
@Composable
private fun ConnectionHeader(state: PrinterConnectUiState) {
    val connectedName = state.connectedDevice?.displayName
    val isConnecting = state.connectingDevice != null
    val statusText = state.printerStatus?.let { printerStatusText(it) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconView(
                icon = AppIcon.Bluetooth,
                size = 40.dp,
                tint = if (connectedName != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(text = "蓝牙打印机", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        connectedName != null -> "已连接：$connectedName"
                        isConnecting -> "连接中…"
                        else -> "未连接"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (connectedName != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (connectedName != null) {
                AppTextButton(text = "断开", onClick = { PrinterConnectStore.disconnect() })
            }
        }
        if (statusText != null) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DeviceSection(
    sectionTitle: String,
    devices: List<PrinterDevice>,
    emptyText: String?,
    state: PrinterConnectUiState,
) {
    Text(
        text = sectionTitle,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (devices.isEmpty()) {
        if (emptyText != null) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val connectedKey = state.connectedDevice?.key
    val connectingKey = state.connectingDevice?.key
    devices.forEach { device ->
        DeviceRow(
            device = device,
            connected = connectedKey != null && connectedKey == device.key,
            connecting = connectingKey != null && connectingKey == device.key,
        )
    }
}

@Composable
private fun DeviceRow(device: PrinterDevice, connected: Boolean, connecting: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !connected && !connecting) {
                PrinterConnectStore.connect(device)
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(
            icon = AppIcon.Bluetooth,
            size = 24.dp,
            tint = if (connected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(text = device.displayName, style = MaterialTheme.typography.bodyLarge)
            val subtitle = listOfNotNull(
                device.macAddress.takeIf { it.isNotBlank() },
                // 没有系统串口的设备（含「其他设备」分类的打印机）走蓝牙直连，这里明确标出来。
                device.comPort ?: "蓝牙直连",
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            connected -> Text(
                text = "已连接",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            connecting -> Text(
                text = "连接中…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> AppTextButton(text = "连接", onClick = { PrinterConnectStore.connect(device) })
        }
    }
}

private fun connectErrorText(error: ConnectError): String = when (error) {
    ConnectError.Permission -> "未获得蓝牙访问权限，请先授权"
    ConnectError.BluetoothOff -> "电脑蓝牙未开启，请先打开蓝牙"
    ConnectError.NotSupported -> "该设备不支持蓝牙打印"
    ConnectError.Timeout -> "连接超时，请确认打印机已开机、在电脑附近且已配对"
    ConnectError.Generic -> "连接失败，请重试"
}

private fun printerStatusText(status: PrinterStatus): String = when (status) {
    PrinterStatus.OutOfPaper -> "打印机缺纸"
    PrinterStatus.CoverOpen -> "打印机开盖"
    PrinterStatus.Overheat -> "打印机过热"
    PrinterStatus.LowBattery -> "打印机电量低"
}

private fun testFeedText(message: TestFeedMessage): String = when (message) {
    TestFeedMessage.Success -> "指令已发送，请观察打印机是否走纸"
    TestFeedMessage.NotConnected -> "打印机未连接"
    TestFeedMessage.Failed -> "测试指令发送失败，请重新连接后再试"
}
