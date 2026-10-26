package com.pencil.chuangyin.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 经典蓝牙扫描实现：列出系统已配对设备 + 通过广播发现周边设备。
 *
 * 设计决策（对应用户标准流程：系统蓝牙里“配对”打印机，再到 App 连接页连接）：
 * - 已配对设备**不做名称关键词过滤**——不同厂商/固件命名差异极大，
 *   过滤会导致用户已配对的打印机在列表中“消失”；
 * - 附近设备同样全部返回，由用户自行识别选择。
 */
@Singleton
class BluetoothPrinterScanner @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : PrinterScanner {

    @Volatile override var isScanning = false
    private val adapter = BluetoothAdapter.getDefaultAdapter()

    private var onDeviceFound: ((PrinterDevice) -> Unit)? = null
    private var onScanFinished: (() -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.toPrinterDeviceOrNull()?.let { onDeviceFound?.invoke(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isScanning = false
                    runCatching { context.unregisterReceiver(this) }
                    onScanFinished?.invoke()
                }
            }
        }
    }

    override fun startScan(
        onDeviceFound: (PrinterDevice) -> Unit,
        onScanFinished: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (adapter == null) {
            onError("设备不支持蓝牙")
            return
        }
        if (isScanning) {
            onError("正在扫描")
            return
        }
        this.onDeviceFound = onDeviceFound
        this.onScanFinished = onScanFinished

        @Suppress("DEPRECATION")
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        runCatching { context.registerReceiver(receiver, filter) }.onFailure { onError("缺少权限") }
        @Suppress("DEPRECATION")
        runCatching { if (adapter.isDiscovering) adapter.cancelDiscovery() }
        isScanning = true
        @Suppress("DEPRECATION")
        runCatching { adapter.startDiscovery() }.onFailure { isScanning = false; onError("扫描失败") }

        // 兜底：即便没有扫描完成广播也主动结束
        scheduleScanFallback(onScanFinished)
    }

    override fun stopScan() {
        @Suppress("DEPRECATION")
        runCatching { adapter?.cancelDiscovery() }
        isScanning = false
        runCatching { context.unregisterReceiver(receiver) }
    }

    /** 已配对设备全量返回（按名称排序），不做关键词过滤。 */
    override fun bondedPrinters(): List<PrinterDevice> {
        @Suppress("DEPRECATION")
        val bonded = runCatching { adapter?.bondedDevices }.getOrNull() ?: return emptyList()
        return bonded.mapNotNull { it.toPrinterDeviceOrNull() }
            .sortedBy { it.name.ifBlank { it.address } }
    }

    private fun BluetoothDevice.toPrinterDeviceOrNull(): PrinterDevice? =
        runCatching { PrinterDevice(name = name ?: "", address = address) }.getOrNull()

    /** 兜底停止扫描：120s 后确保置为扫描结束状态，避免 UI 停在扫描动画。 */
    private fun scheduleScanFallback(finished: () -> Unit) {
        android.os.Handler(context.mainLooper).postDelayed({
            if (isScanning) {
                isScanning = false
                runCatching { context.unregisterReceiver(receiver) }
                finished()
            }
        }, SCAN_FALLBACK_MS)
    }

    private companion object {
        const val SCAN_FALLBACK_MS = 120_000L
    }
}
