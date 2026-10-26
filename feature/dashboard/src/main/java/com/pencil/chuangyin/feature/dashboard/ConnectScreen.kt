package com.pencil.chuangyin.feature.dashboard

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.pencil.chuangyin.core.ui.components.AppIcon
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSecondaryButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.printer.ConnectionState
import com.pencil.chuangyin.printer.PrinterDevice
import com.pencil.chuangyin.printer.PrinterStatus
import com.pencil.chuangyin.resources.R as ResR

/**
 * 独立的打印机连接页（从主页状态胶囊进入）：权限授权、已配对设备直连、
 * 附近设备扫描，以及跳转系统蓝牙设置完成配对。长设备列表只存在于本页，
 * 不占用主页空间。
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ConnectRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ConnectViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)
    val granted = permissionState.allPermissionsGranted

    LaunchedEffect(granted) {
        viewModel.onPermissionReady(granted)
    }

    // 从系统蓝牙设置配对后返回页面，自动刷新已配对列表。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, granted) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && granted) viewModel.refreshBonded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ConnectScreen(
        state = state,
        hasPermission = granted,
        onBack = onBack,
        onRequestPermission = { permissionState.launchMultiplePermissionRequest() },
        onRefreshBonded = viewModel::refreshBonded,
        onStartScan = viewModel::startScan,
        onStopScan = viewModel::stopScan,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onConsumeError = viewModel::consumeError,
        onTestFeed = viewModel::testFeed,
        onConsumeTestMessage = viewModel::consumeTestMessage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    state: ConnectUiState,
    hasPermission: Boolean,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefreshBonded: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (PrinterDevice) -> Unit,
    onDisconnect: () -> Unit,
    onConsumeError: () -> Unit,
    onTestFeed: () -> Unit,
    onConsumeTestMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = state.error?.let { stringResource(connectErrorRes(it)) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onConsumeError()
        }
    }
    val testText = state.testMessage?.let { stringResource(testFeedRes(it)) }
    LaunchedEffect(testText) {
        if (testText != null) {
            snackbarHostState.showSnackbar(testText)
            onConsumeTestMessage()
        }
    }
    val statusText = state.printerStatus?.let { stringResource(connectStatusRes(it)) }
    val connectedDevice = (state.connection as? ConnectionState.Connected)?.device
    val isConnecting = state.connection is ConnectionState.Connecting
    val connectingAddress = (state.connection as? ConnectionState.Connecting)?.device?.address
    val glassActive = rememberLiquidGlassActive()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.connect_title)) },
                navigationIcon = {
                    AppIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(ResR.string.label_back),
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            ConnectionHeader(
                connectedName = connectedDevice?.name ?: connectedDevice?.address,
                isConnecting = isConnecting,
                statusText = statusText,
                onDisconnect = onDisconnect,
            )

            if (connectedDevice != null) {
                AppPrimaryButton(
                    text = stringResource(
                        if (state.isTesting) R.string.connect_testing else R.string.connect_test_feed
                    ),
                    onClick = onTestFeed,
                    enabled = !state.isTesting,
                )
                AppText(
                    text = stringResource(R.string.connect_test_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AppText(
                text = stringResource(R.string.home_pair_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppSecondaryButton(
                text = stringResource(R.string.connect_open_system_bt),
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                },
            )

            if (!hasPermission) {
                AppText(
                    text = stringResource(R.string.home_permission_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppPrimaryButton(
                    text = stringResource(R.string.home_grant_permission),
                    onClick = onRequestPermission,
                )
            } else {
                DeviceSection(
                    sectionTitle = stringResource(R.string.home_bonded_section),
                    devices = state.bondedDevices,
                    emptyText = stringResource(R.string.home_bonded_empty),
                    connectedAddress = connectedDevice?.address,
                    isConnecting = isConnecting,
                    connectingAddress = connectingAddress,
                    onConnect = onConnect,
                )

                if (state.isScanning || state.foundDevices.isNotEmpty()) {
                    DeviceSection(
                        sectionTitle = stringResource(R.string.home_nearby_section),
                        devices = state.foundDevices,
                        emptyText = null,
                        connectedAddress = connectedDevice?.address,
                        isConnecting = isConnecting,
                        connectingAddress = connectingAddress,
                        onConnect = onConnect,
                    )
                }

                if (state.isScanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    AppSecondaryButton(
                        text = stringResource(R.string.home_stop_scan),
                        onClick = onStopScan,
                    )
                } else {
                    AppSecondaryButton(
                        text = stringResource(R.string.home_scan),
                        onClick = onStartScan,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionHeader(
    connectedName: String?,
    isConnecting: Boolean,
    statusText: String?,
    onDisconnect: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = stringResource(R.string.home_printer_title),
            tint = if (connectedName != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_touch_target)),
        )
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = dimensionResource(ResR.dimen.dimen_global_padding_s))) {
            AppText(
                text = stringResource(R.string.home_printer_title),
                style = MaterialTheme.typography.titleMedium,
            )
            AppText(
                text = when {
                    connectedName != null -> stringResource(R.string.home_connected, connectedName)
                    isConnecting -> stringResource(R.string.home_connecting)
                    else -> stringResource(R.string.home_disconnected)
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
            AppTextButton(text = stringResource(R.string.home_disconnect), onClick = onDisconnect)
        }
    }
    if (statusText != null) {
        AppText(
            text = statusText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        )
    }
}

@Composable
private fun DeviceSection(
    sectionTitle: String,
    devices: List<PrinterDevice>,
    emptyText: String?,
    connectedAddress: String?,
    isConnecting: Boolean,
    connectingAddress: String?,
    onConnect: (PrinterDevice) -> Unit,
) {
    AppText(
        text = sectionTitle,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(
            top = dimensionResource(ResR.dimen.dimen_global_padding_s),
            bottom = dimensionResource(ResR.dimen.dimen_global_padding_s),
        ),
    )
    if (devices.isEmpty()) {
        if (emptyText != null) {
            AppText(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        devices.forEach { device ->
            DeviceRow(
                device = device,
                connected = connectedAddress == device.address,
                connecting = isConnecting && connectingAddress == device.address,
                onConnect = onConnect,
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: PrinterDevice,
    connected: Boolean,
    connecting: Boolean,
    onConnect: (PrinterDevice) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !connected && !connecting) { onConnect(device) }
            .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
            contentDescription = device.displayName(),
            tint = if (connected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = dimensionResource(ResR.dimen.dimen_global_padding_s))) {
            AppText(text = device.displayName(), style = MaterialTheme.typography.bodyLarge)
            AppText(
                text = device.address,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            connected -> AppText(
                text = stringResource(R.string.home_connected_badge),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            connecting -> AppText(
                text = stringResource(R.string.home_connecting),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> AppTextButton(text = stringResource(R.string.home_connect), onClick = { onConnect(device) })
        }
    }
}

private fun PrinterDevice.displayName(): String = name.ifBlank { address }

private fun connectErrorRes(error: ConnectError): Int = when (error) {
    ConnectError.Permission -> R.string.home_err_permission
    ConnectError.BluetoothOff -> R.string.home_err_bt_off
    ConnectError.NotSupported -> R.string.home_err_unsupported
    ConnectError.Timeout -> R.string.home_err_timeout
    ConnectError.Generic -> R.string.home_err_generic
}

private fun connectStatusRes(status: PrinterStatus): Int = when (status) {
    PrinterStatus.OutOfPaper -> R.string.home_status_out_of_paper
    PrinterStatus.CoverOpen -> R.string.home_status_cover_open
    PrinterStatus.Overheat -> R.string.home_status_overheat
    PrinterStatus.LowBattery -> R.string.home_status_low_battery
}

private fun testFeedRes(message: TestFeedMessage): Int = when (message) {
    TestFeedMessage.Success -> R.string.connect_test_ok
    TestFeedMessage.NotConnected -> R.string.home_err_not_connected
    TestFeedMessage.Failed -> R.string.connect_test_failed
}

@Preview(showBackground = true)
@Composable
private fun ConnectScreenPreview() {
    ConnectScreen(
        state = ConnectUiState(),
        hasPermission = true,
        onBack = {},
        onRequestPermission = {},
        onRefreshBonded = {},
        onStartScan = {},
        onStopScan = {},
        onConnect = {},
        onDisconnect = {},
        onConsumeError = {},
        onTestFeed = {},
        onConsumeTestMessage = {},
    )
}
