package com.pencil.chuangyin.desktop.printer

import com.pencil.chuangyin.desktop.text.PrintError
import com.pencil.chuangyin.desktop.text.PrintResult
import com.pencil.chuangyin.desktop.text.PrinterStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 「测试走纸」的结果提示（对齐安卓端 TestFeedMessage）。 */
enum class TestFeedMessage { Success, NotConnected, Failed }

/**
 * 「连接打印机」页的界面状态 —— 对齐安卓端 `ConnectUiState`。
 *
 * 数据来源：设备列表来自 [BluetoothDevices]（已配对设备 + 蓝牙串口），
 * 连接状态与运行时状态直接镜像 [PrinterConnectionStore]（唯一数据源）。
 */
data class PrinterConnectUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val deviceState: PrinterDeviceState = PrinterDeviceState(),
    val devices: List<PrinterDevice> = emptyList(),
    val serialPorts: List<String> = emptyList(),
    val availability: BluetoothAvailability = BluetoothAvailability.Available,
    val isRefreshing: Boolean = false,
    val isTesting: Boolean = false,
    val error: ConnectError? = null,
    val errorDetail: String? = null,
    val testMessage: TestFeedMessage? = null,
    /** 最近一次上报的故障（缺纸 / 开盖 / 过热 / 低电量），对齐安卓端 printerStatus 字段。 */
    val printerStatus: PrinterStatus? = null,
) {
    val connectedDevice: PrinterDevice? get() = (connection as? ConnectionState.Connected)?.device
    val connectingDevice: PrinterDevice? get() = (connection as? ConnectionState.Connecting)?.device
    val isConnected: Boolean get() = connectedDevice != null
}

/**
 * 连接页状态仓库：设备刷新、连接 / 断开、测试走纸。
 *
 * 桌面端没有「扫描附近设备」（需要 WinRT 异步互操作），改为「刷新设备列表」：
 * 用户在 Windows 设置里完成配对后回到本页刷新即可看到设备。
 */
object PrinterConnectStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PrinterConnectUiState())
    val state: StateFlow<PrinterConnectUiState> = _state.asStateFlow()

    init {
        scope.launch {
            PrinterConnectionStore.connectionState.collect { connection ->
                _state.update { it.copy(connection = connection) }
            }
        }
        scope.launch {
            PrinterConnectionStore.deviceState.collect { deviceState ->
                _state.update { it.copy(deviceState = deviceState) }
            }
        }
        scope.launch {
            PrinterConnectionStore.statusEvents.collect { status ->
                _state.update { it.copy(printerStatus = status) }
            }
        }
        refresh()
    }

    /** 重新枚举已配对设备与蓝牙串口。 */
    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        scope.launch {
            val availability = runCatching { BluetoothDevices.availability() }
                .getOrDefault(BluetoothAvailability.NoAdapter)
            val devices = if (availability == BluetoothAvailability.Available) {
                runCatching { BluetoothDevices.pairedDevices() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val ports = if (availability == BluetoothAvailability.Available) {
                runCatching { BluetoothDevices.bluetoothSerialPorts() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _state.update {
                it.copy(
                    availability = availability,
                    devices = devices,
                    serialPorts = ports,
                    isRefreshing = false,
                )
            }
        }
    }

    fun connect(device: PrinterDevice) {
        _state.update { it.copy(error = null, errorDetail = null) }
        PrinterConnectionStore.connect(device) { result ->
            if (result is ConnectResult.Failure) {
                _state.update { it.copy(error = result.error, errorDetail = result.detail) }
            }
        }
    }

    fun disconnect() {
        PrinterConnectionStore.disconnect()
    }

    fun testFeed() {
        if (_state.value.isTesting) return
        _state.update { it.copy(isTesting = true, testMessage = null) }
        scope.launch {
            val message = when (val result = PrinterController.testFeed()) {
                is PrintResult.Success -> TestFeedMessage.Success
                is PrintResult.Error -> if (result.code == PrintError.NotConnected) {
                    TestFeedMessage.NotConnected
                } else {
                    TestFeedMessage.Failed
                }
            }
            _state.update { it.copy(isTesting = false, testMessage = message) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null, errorDetail = null) }

    fun clearTestMessage() = _state.update { it.copy(testMessage = null) }

    fun clearStatus() = _state.update { it.copy(printerStatus = null) }

    /** 打开 Windows 的「蓝牙和其他设备」设置页，方便用户完成配对。 */
    fun openSystemBluetoothSettings() {
        scope.launch {
            runCatching { Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", "ms-settings:bluetooth")) }
        }
    }
}
