package com.pencil.chuangyin.printer

/**
 * 打印机状态通知（由连接层从收到字节解码）。UI 层据此提示缺纸/开盖/过热/低电量。
 */
enum class PrinterStatus(val code: Int) {
    OutOfPaper(PrinterCommand.STATUS_OUT_OF_PAPER),
    CoverOpen(PrinterCommand.STATUS_COVER_OPEN),
    Overheat(PrinterCommand.STATUS_OVERHEAT),
    LowBattery(PrinterCommand.STATUS_LOW_BATTERY),
}

/**
 * 打印机实时状态快照（连接层维护，UI 直接订阅）：
 * - [activeStatuses] 为"当前仍视为生效"的故障集合：靠周期轮询刷新时间戳，
 *   恢复后不再上报、超时自动消除（解决"开盖后合盖显示不恢复"问题）；
 * - [batteryPercent] 电量百分比（null = 尚未获得设备上报）。
 */
data class PrinterDeviceState(
    val activeStatuses: Set<PrinterStatus> = emptySet(),
    val batteryPercent: Int? = null,
)

/** 打印错误码：面向稳定契约，具体用户可见文案由 UI 层按码经 stringResource 映射（零硬编码）。 */
enum class PrintError { NotConnected, Permission, Timeout, WriteFailed, RenderFailed, Cancelled, Generic }

/** 连接错误码：同理由 UI 层映射文案。 */
enum class ConnectionError { NoBluetooth, BluetoothOff, Permission, Timeout, InvalidAddress, Generic }

/** 打印结果。 */
sealed interface PrintResult {
    data object Success : PrintResult
    data class Error(val code: PrintError, val detail: String? = null) : PrintResult
}

/** 连接结果。 */
sealed interface ConnectionResult {
    data object Success : ConnectionResult
    data class Failure(val code: ConnectionError, val detail: Throwable? = null) : ConnectionResult
}