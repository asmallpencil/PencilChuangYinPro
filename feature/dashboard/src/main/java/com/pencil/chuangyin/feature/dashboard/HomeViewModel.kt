package com.pencil.chuangyin.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.printer.ConnectionState
import com.pencil.chuangyin.printer.PrinterController
import com.pencil.chuangyin.printer.PrinterDevice
import com.pencil.chuangyin.printer.PrinterStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomePrinterCapsule(
    val connectedDevice: PrinterDevice? = null,
    val isConnecting: Boolean = false,
    /** 硬件上报后仍未恢复的异常状态（缺纸/开盖/过热/低电），断开连接或超时自动消除。 */
    val activeStatuses: Set<PrinterStatus> = emptySet(),
    /** 打机电量百分比（null = 设备尚未上报）。 */
    val batteryPercent: Int? = null,
)

/**
 * 主页 ViewModel：只承载打印机状态胶囊。连接/扫描/配对等长列表管理
 * 收敛在独立的 [ConnectViewModel]（连接页）中，主页保持轻量。
 * 故障集合与电量来自连接层的 [PrinterController.deviceState] 快照：
 * 周期轮询刷新，恢复后自动消除，断开连接时清零。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val printerController: PrinterController,
) : ViewModel() {

    val printerCapsule: StateFlow<HomePrinterCapsule> =
        combine(printerController.connectionState, printerController.deviceState) { connection, device ->
            HomePrinterCapsule(
                connectedDevice = (connection as? ConnectionState.Connected)?.device,
                isConnecting = connection is ConnectionState.Connecting,
                activeStatuses = device.activeStatuses,
                batteryPercent = device.batteryPercent,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_STOP_TIMEOUT_MS),
            initialValue = HomePrinterCapsule(
                connectedDevice = (printerController.connectionState.value
                    as? ConnectionState.Connected)?.device,
                isConnecting = printerController.connectionState.value is ConnectionState.Connecting,
                activeStatuses = printerController.deviceState.value.activeStatuses,
                batteryPercent = printerController.deviceState.value.batteryPercent,
            ),
        )

    private companion object {
        const val STATE_STOP_TIMEOUT_MS = 5_000L
    }
}
