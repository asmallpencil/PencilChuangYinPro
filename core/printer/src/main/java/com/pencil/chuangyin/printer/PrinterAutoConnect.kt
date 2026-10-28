package com.pencil.chuangyin.printer

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 自动连接（后台静默重连）管理器。
 *
 * 规则：
 * 1. 应用运行期间常驻后台轮询：只要「开关开启 + 存在上次成功设备 + 当前未连接」，
 *    就按固定间隔持续尝试连接那台设备，直到成功；失败静默重试，前台不展示任何提示。
 * 2. 进入设备配置页（连接页）立即暂停；离开后依然保持暂停，直到用户手动连接成功
 *    某台设备，才恢复自动连接（并把该设备写为「上次成功设备」）。
 * 3. 打印过程中若设备断联或数据异常（状态显示已连接但下发无效/报错），由
 *    [DefaultPrinterController] 主动断开并刷新状态，本管理器随即重新开始自动重连。
 */
@Singleton
class PrinterAutoConnect @Inject constructor(
    private val controller: PrinterController,
    private val settings: PrinterSettings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 进入设备配置页后置为 true；仅「手动连接成功」会清除。 */
    @Volatile
    private var pausedByDeviceConfig = false

    private var loopJob: Job? = null

    /** 应用启动时调用一次，开始后台重连循环。 */
    fun start() {
        if (loopJob != null) return
        loopJob = scope.launch { retryLoop() }
    }

    /** 进入设备配置界面时调用：立即停止自动连接，并在离开后持续保持停止。 */
    fun pauseForDeviceConfig() {
        pausedByDeviceConfig = true
    }

    /** 用户手动连接成功：记录设备并恢复自动连接。 */
    suspend fun onManualConnected(device: PrinterDevice) {
        settings.saveLastDevice(device)
        pausedByDeviceConfig = false
    }

    private suspend fun retryLoop() {
        while (currentCoroutineContext().isActive) {
            // 首次循环的延迟同时给 DataStore 留出冷启动读取时间。
            delay(RETRY_INTERVAL_MS)
            if (!settings.autoConnectEnabled.value) continue
            if (pausedByDeviceConfig) continue
            val target = settings.currentLastDevice() ?: continue
            // 已连接/正在连接时不打扰（含打印任务进行中）。
            if (controller.connectionState.value !is ConnectionState.Disconnected) continue
            attempt(target)
        }
    }

    private suspend fun attempt(device: PrinterDevice) {
        suspendCancellableCoroutine { continuation ->
            controller.connect(device.address, device.name) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }

    private companion object {
        /** 静默重试间隔：固定频率持续尝试，失败后继续。 */
        const val RETRY_INTERVAL_MS = 5_000L
    }
}
