package com.pencil.chuangyin.printer

/** 扫描到的打印机设备元数据。 */
data class PrinterDevice(val name: String, val address: String)

/**
 * 打印机设备扫描契约：发现周边符合条件的蓝牙打印机。
 * 实现层负责权限/广播/过滤，UI 层仅收集设备列表即可。
 */
interface PrinterScanner {
    val isScanning: Boolean

    /** 开始扫描，收到设备与结束事件。 */
    fun startScan(
        onDeviceFound: (PrinterDevice) -> Unit,
        onScanFinished: () -> Unit,
        onError: (String) -> Unit,
    )

    fun stopScan()

    /** 已配对的打印机列表。 */
    fun bondedPrinters(): List<PrinterDevice>
}