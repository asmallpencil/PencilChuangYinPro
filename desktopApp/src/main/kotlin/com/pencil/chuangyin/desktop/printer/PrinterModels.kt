package com.pencil.chuangyin.desktop.printer

import com.pencil.chuangyin.desktop.text.PrinterStatus

/**
 * 桌面端打印机连接模型 —— 与安卓端 `core/printer` 的连接状态机对齐。
 *
 * 安卓端走 `BluetoothSocket`（SPP/RFCOMM）；桌面端 Windows 没有内置蓝牙 API，
 * 因此用两条等价通道承载同一套字节流：
 *  1. Winsock `AF_BTH` + SPP GUID 的 RFCOMM 直连（JNA 调用 ws2_32）；
 *  2. 系统已配对生成的「蓝牙链接上的标准串行 (COMx)」虚拟串口（jSerialComm）。
 */

/** 一台可用于打印的蓝牙设备。 */
data class PrinterDevice(
    val name: String,
    val macAddress: String,
    /** 若系统里已存在该设备的 SPP 虚拟串口，则直接记录，连接时优先使用。 */
    val comPort: String? = null,
) {
    val displayName: String get() = name.ifBlank { macAddress }

    /** 列表去重用的稳定键。 */
    val key: String get() = macAddress.uppercase().ifBlank { "COM:$comPort" }

    /** 是否为「只有串口、拿不到设备名」的退化条目。 */
    val isSerialOnly: Boolean get() = macAddress.isBlank()
}

/** 打印机连接状态（与安卓端 ConnectionState 一致）。 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val device: PrinterDevice) : ConnectionState
    data class Connected(val device: PrinterDevice) : ConnectionState
}

/** 设备运行时状态：电量与故障集合。 */
data class PrinterDeviceState(
    val batteryPercent: Int? = null,
    val activeStatuses: Set<PrinterStatus> = emptySet(),
)

/** 连接失败原因（与安卓端蓝牙连接错误分类对齐）。 */
enum class ConnectError {
    /** 权限被拒绝（Windows 需要「蓝牙」设备访问权限）。 */
    Permission,

    /** 蓝牙适配器被关闭或不存在。 */
    BluetoothOff,

    /** 设备上没有可用的 SPP 打印服务。 */
    NotSupported,

    /** 连接超时。 */
    Timeout,

    /** 其它错误。 */
    Generic,
}

/** 连接结果。 */
sealed interface ConnectResult {
    data object Success : ConnectResult
    data class Failure(val error: ConnectError, val detail: String? = null) : ConnectResult
}

/** 蓝牙适配器可用性。 */
enum class BluetoothAvailability { Available, NoAdapter }

/** 传输层统一异常：把底层错误码翻译成 [ConnectError]。 */
class BluetoothChannelException(
    val error: ConnectError,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
