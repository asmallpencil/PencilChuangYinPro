package com.pencil.chuangyin.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.printer.ConnectionError
import com.pencil.chuangyin.printer.ConnectionResult
import com.pencil.chuangyin.printer.ConnectionState
import com.pencil.chuangyin.printer.PrintError
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterAutoConnect
import com.pencil.chuangyin.printer.PrinterController
import com.pencil.chuangyin.printer.PrinterDevice
import com.pencil.chuangyin.printer.PrinterScanner
import com.pencil.chuangyin.printer.PrinterStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 连接页错误事件（UI 层映射为本地化文案）。 */
enum class ConnectError { Permission, BluetoothOff, NotSupported, Timeout, Generic }

/** 走纸自测结果（UI 层映射为本地化文案）。 */
enum class TestFeedMessage { Success, NotConnected, Failed }

data class ConnectUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val bondedDevices: List<PrinterDevice> = emptyList(),
    val foundDevices: List<PrinterDevice> = emptyList(),
    val isScanning: Boolean = false,
    val error: ConnectError? = null,
    val printerStatus: PrinterStatus? = null,
    val isTesting: Boolean = false,
    val testMessage: TestFeedMessage? = null,
)

/**
 * 连接页 ViewModel：打印机连接是全局单例行为，本页是唯一连接管理入口。
 * 标准流程：系统蓝牙设置里配对 → 本页“已配对设备”列表直接连接；
 * 也支持现场扫描发现周边设备（部分设备可直接通过 SPP 连接）。
 */
@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val printerController: PrinterController,
    private val printerScanner: PrinterScanner,
    private val autoConnect: PrinterAutoConnect,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    init {
        // 进入设备配置页即停止后台自动连接；离开后仍保持停止，
        // 直到用户在本页手动连接成功一次（见 connect()）。
        autoConnect.pauseForDeviceConfig()
        viewModelScope.launch {
            printerController.connectionState.collect { connection ->
                _state.update { it.copy(connection = connection) }
            }
        }
        viewModelScope.launch {
            printerController.statusEvents.collect { status ->
                _state.update { it.copy(printerStatus = status) }
            }
        }
    }

    /** 权限就绪/页面返回前台时刷新已配对列表（用户可能刚去系统设置完成配对）。 */
    fun onPermissionReady(granted: Boolean) {
        if (granted) refreshBonded() else _state.update { it.copy(error = ConnectError.Permission) }
    }

    fun refreshBonded() {
        val devices = runCatching { printerScanner.bondedPrinters() }
            .getOrElse {
                _state.update { it.copy(error = ConnectError.Permission) }
                return
            }
        _state.update { it.copy(bondedDevices = devices) }
    }

    fun startScan() {
        if (_state.value.isScanning) return
        _state.update { it.copy(isScanning = true, foundDevices = emptyList()) }
        printerScanner.startScan(
            onDeviceFound = { device ->
                _state.update { up ->
                    val known = up.bondedDevices.any { it.address == device.address } ||
                        up.foundDevices.any { it.address == device.address }
                    if (known) up else up.copy(foundDevices = up.foundDevices + device)
                }
            },
            onScanFinished = { _state.update { it.copy(isScanning = false) } },
            onError = { message ->
                val mapped = if (message.contains("权限")) ConnectError.Permission else ConnectError.Generic
                _state.update { it.copy(isScanning = false, error = mapped) }
            },
        )
    }

    fun stopScan() {
        printerScanner.stopScan()
        _state.update { it.copy(isScanning = false) }
    }

    fun connect(device: PrinterDevice) {
        if (_state.value.connection is ConnectionState.Connecting ||
            _state.value.connection is ConnectionState.Connected
        ) {
            return
        }
        printerController.connect(device.address, device.name) { result ->
            when (result) {
                // 手动连接成功：记录该设备并恢复自动连接。
                ConnectionResult.Success ->
                    viewModelScope.launch { autoConnect.onManualConnected(device) }
                is ConnectionResult.Failure ->
                    _state.update { it.copy(error = mapConnectionError(result.code)) }
            }
        }
    }

    fun disconnect() {
        printerScanner.stopScan()
        printerController.disconnect()
    }

    /** 走纸自测：仅验证“连接通道 + 私有握手 + ESC J”是否被打印机执行。 */
    fun testFeed() {
        if (_state.value.isTesting) return
        _state.update { it.copy(isTesting = true) }
        viewModelScope.launch {
            val message = when (val result = printerController.testFeed()) {
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

    fun consumeTestMessage() {
        _state.update { it.copy(testMessage = null) }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    private fun mapConnectionError(code: ConnectionError): ConnectError = when (code) {
        ConnectionError.Permission -> ConnectError.Permission
        ConnectionError.BluetoothOff -> ConnectError.BluetoothOff
        ConnectionError.NoBluetooth -> ConnectError.NotSupported
        ConnectionError.Timeout -> ConnectError.Timeout
        ConnectionError.InvalidAddress,
        ConnectionError.Generic,
        -> ConnectError.Generic
    }
}
