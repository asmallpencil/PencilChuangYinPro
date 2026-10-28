package com.pencil.chuangyin.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


/**
 * 经典蓝牙 SPP (RFCOMM) 连接实现。
 *
 * 连接健壮性（廉价热敏打印机适配）：
 * 1. 建立连接前必须取消扫描（discovery 会显著拖慢/阻断 RFCOMM 连接）；
 * 2. socket 创建按 安全SPP → 非安全SPP → 反射通道1(secure/insecure) 逐级降级；
 * 3. connect() 成功后立即按“先输入流后输出流”的顺序取出两个流（部分手机/打印机
 *    栈在未打开输入流时输出流静默丢弃数据），并再取消一次扫描；
 * 4. 连接状态以 [connectionState] 单例共享，全 App 同一份事实；
 * 5. 所有写失败携带异常原因并输出日志，供 UI 定位“打印机无动作”。
 */
@Singleton
class BluetoothPrinterConnection @Inject constructor() : PrinterConnection {

    private val sppUuid = UUID.fromString(PrinterCommand.SPP_UUID)
    private val statusEventsInternal = MutableSharedFlow<PrinterStatus>(extraBufferCapacity = 16)
    private val connectionStateInternal = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val deviceStateInternal = MutableStateFlow(PrinterDeviceState())

    /** 连接保活轮询（状态刷新 + 电量查询），连接成功启动、断开取消。 */
    private val pollScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    /** 故障状态最近一次上报时间戳（接收线程写、轮询协程读，需并发安全）。 */
    private val statusTimestamps = ConcurrentHashMap<PrinterStatus, Long>()

    /** 电量响应解析窗口：发出 QUERY_BATTERY 后置真，收到响应/超时后复位。 */
    @Volatile private var batteryQueryPending = false
    @Volatile private var batteryQueryAtMs = 0L

    /** 紧急停止专用单线程：用户取消时即使主发送线程阻塞，也能立即发出 STOP 指令。 */
    private val emergencyExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "printer-emergency").apply { isDaemon = true }
    }

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var receiverRunning = false
    @Volatile private var output: OutputStream? = null
    @Volatile private var input: InputStream? = null
    @Volatile override var deviceName: String? = null

    /**
     * 打印任务进行中标志：true 时状态/电量轮询器不再下发查询字节，
     * 避免命令字节被注入 GS v 0 光栅数据流导致像素错位。
     */
    @Volatile private var printing = false

    /**
     * 所有 OutputStream 写入的串行化锁。
     * 打印任务、轮询器、紧急停止都可能并发写同一条流，无锁会导致字节交错，
     * 打印机把命令字节当光栅数据解析（多行偏移→错位/乱码/色块丢失）。
     */
    private val writeLock = Any()

    override val isConnected: Boolean get() = connectionStateInternal.value is ConnectionState.Connected

    override val statusEvents: Flow<PrinterStatus> = statusEventsInternal

    override val deviceState: StateFlow<PrinterDeviceState> = deviceStateInternal.asStateFlow()

    override val connectionState: StateFlow<ConnectionState> = connectionStateInternal.asStateFlow()

    override fun connect(macAddress: String, deviceName: String, callback: (ConnectionResult) -> Unit) {
        if (isConnected) {
            callback(ConnectionResult.Success)
            return
        }
        val device = PrinterDevice(deviceName, macAddress)
        connectionStateInternal.value = ConnectionState.Connecting(device)
        thread(name = "printer-connect", isDaemon = true) {
            val result = doConnect(macAddress)
            if (result is ConnectionResult.Success) {
                this.deviceName = deviceName
                connectionStateInternal.value = ConnectionState.Connected(device)
                startReceiver()
                startStatePoller()
                Log.i(TAG, "连接成功：$deviceName ($macAddress)")
            } else {
                Log.w(TAG, "连接失败：$result")
                connectionStateInternal.value = ConnectionState.Disconnected
            }
            callback(result)
        }
    }

    private fun doConnect(macAddress: String): ConnectionResult {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return ConnectionResult.Failure(ConnectionError.NoBluetooth)
        if (!adapter.isEnabled) return ConnectionResult.Failure(ConnectionError.BluetoothOff)
        val remoteDevice = try {
            adapter.getRemoteDevice(macAddress)
        } catch (e: IllegalArgumentException) {
            return ConnectionResult.Failure(ConnectionError.InvalidAddress, e)
        } catch (e: SecurityException) {
            return ConnectionResult.Failure(ConnectionError.Permission, e)
        }

        // 关键：连接期间必须停掉扫描，否则 RFCOMM 极易超时/被拒绝。
        cancelDiscovery(adapter)

        var lastError: Throwable? = null
        var sawPermissionError = false
        val deadline = System.currentTimeMillis() + PrinterCommand.CONNECT_TIMEOUT_MS

        for (strategy in SocketStrategy.entries) {
            if (System.currentTimeMillis() > deadline) break
            val candidate = try {
                createSocket(remoteDevice, strategy)
            } catch (e: SecurityException) {
                sawPermissionError = true
                lastError = e
                continue
            } catch (e: Exception) {
                lastError = e
                continue
            } ?: continue

            try {
                candidate.connect()
                // 成功后立即、按顺序取出输入/输出流：某些协议栈要求输入流先建立，
                // 否则后续 write() 不报错但数据被静默丢弃（打印机表现为毫无动作）。
                val inStream = candidate.inputStream
                val outStream = candidate.outputStream
                socket = candidate
                input = inStream
                output = outStream
                cancelDiscovery(adapter)
                Log.i(TAG, "socket 策略 $strategy 连接成功")
                return ConnectionResult.Success
            } catch (e: SecurityException) {
                sawPermissionError = true
                lastError = e
                runCatching { candidate.close() }
                // 安全异常通常不会因重试消失，直接尝试下一策略。
                cancelDiscovery(adapter)
            } catch (e: Exception) {
                lastError = e
                Log.i(TAG, "socket 策略 $strategy 失败：${e.javaClass.simpleName}: ${e.message}")
                runCatching { candidate.close() }
                cancelDiscovery(adapter)
            }
        }

        return when {
            sawPermissionError -> ConnectionResult.Failure(ConnectionError.Permission, lastError)
            lastError != null -> ConnectionResult.Failure(ConnectionError.Timeout, lastError)
            else -> ConnectionResult.Failure(ConnectionError.Generic)
        }
    }

    /** 按降级顺序创建 RFCOMM socket。 */
    private fun createSocket(device: BluetoothDevice, strategy: SocketStrategy): BluetoothSocket? = when (strategy) {
        SocketStrategy.SecureSpp ->
            device.createRfcommSocketToServiceRecord(sppUuid)
        SocketStrategy.InsecureSpp ->
            device.createInsecureRfcommSocketToServiceRecord(sppUuid)
        SocketStrategy.ReflectSecureChannel1 ->
            runCatching {
                device.javaClass
                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, CHANNEL_1) as BluetoothSocket
            }.getOrNull()
        SocketStrategy.ReflectInsecureChannel1 ->
            runCatching {
                device.javaClass
                    .getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, CHANNEL_1) as BluetoothSocket
            }.getOrNull()
    }

    private fun cancelDiscovery(adapter: BluetoothAdapter) {
        runCatching {
            @Suppress("DEPRECATION")
            if (adapter.isDiscovering) adapter.cancelDiscovery()
        }
    }

    override fun requestStatus() {
        // 打印任务进行中不下发状态查询：命令字节会污染光栅数据流。
        if (printing) return
        write(PrinterCommand.QUERY_STATUS)
    }

    override fun setPrinting(printing: Boolean) {
        this.printing = printing
        if (!printing) {
            // 任务结束后立即补一次状态查询，刷新 UI 上的故障/电量显示。
            runCatching { write(PrinterCommand.QUERY_STATUS) }
        }
    }

    override fun emergencyStop() {
        Log.w(TAG, "紧急停止：连发 ${PrinterCommand.EMERGENCY_STOP_REPEAT} 次 STOP_PRINT_JOB")
        emergencyExecutor.execute {
            repeat(PrinterCommand.EMERGENCY_STOP_REPEAT) {
                // 走加锁的 write()，确保 STOP 字节不与光栅数据交错；
                // 单次最多等一个 1024 字节块（~1ms），可接受。
                write(PrinterCommand.STOP_PRINT_JOB)
                try {
                    Thread.sleep(PrinterCommand.EMERGENCY_STOP_GAP_MS)
                } catch (_: InterruptedException) {
                    return@execute
                }
            }
        }
    }

    override fun disconnect() {
        receiverRunning = false
        pollJob?.cancel()
        pollJob = null
        statusTimestamps.clear()
        batteryQueryPending = false
        printing = false
        emergencyStop()
        runCatching { socket?.close() }
        socket = null
        output = null
        input = null
        deviceName = null
        connectionStateInternal.value = ConnectionState.Disconnected
        deviceStateInternal.value = PrinterDeviceState()
        Log.i(TAG, "已断开连接")
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int): WriteOutcome {
        val output = output ?: return WriteOutcome.Failure("输出流不存在（未连接）")
        if (length <= 0) return WriteOutcome.Success
        // 串行化所有写入：打印任务、轮询器、紧急停止共享同一条 OutputStream，
        // 无锁会导致字节交错——打印机把命令字节当 GS v 0 像素解析，多行偏移。
        synchronized(writeLock) {
            return try {
                var remaining = length
                while (remaining > 0) {
                    val len = minOf(PrinterCommand.CHUNK_SIZE, remaining)
                    output.write(bytes, offset + (length - remaining), len)
                    output.flush()
                    remaining -= len
                    if (len == PrinterCommand.CHUNK_SIZE) Thread.sleep(PrinterCommand.CHUNK_DELAY_MS)
                }
                WriteOutcome.Success
            } catch (e: IOException) {
                Log.e(TAG, "写入失败($length 字节)：${e.javaClass.simpleName}: ${e.message}", e)
                WriteOutcome.Failure("${e.javaClass.simpleName}: ${e.message}")
            } catch (e: SecurityException) {
                Log.e(TAG, "写入被权限拒绝", e)
                WriteOutcome.Failure("SecurityException: ${e.message}")
            }
        }
    }

    private fun startReceiver() {
        receiverRunning = true
        // 连接成功时已取过输入流，直接复用；取不到则尝试现取。
        val stream = input ?: runCatching { socket?.getInputStream() }.getOrNull()
        if (stream == null) {
            Log.w(TAG, "无法获得输入流，状态接收线程未启动")
            return
        }
        thread(name = "printer-receive", isDaemon = true) {
            val buf = ByteArray(64)
            val pending = ArrayList<Byte>()
            while (receiverRunning) {
                try {
                    val available = stream.available()
                    if (available <= 0) {
                        Thread.sleep(50)
                        continue
                    }
                    val read = stream.read(buf, 0, minOf(buf.size, available))
                    if (read <= 0) continue
                    for (i in 0 until read) pending.add(buf[i])
                    decode(pending)
                } catch (e: Exception) {
                    if (receiverRunning) {
                        Log.w(TAG, "接收线程异常：${e.javaClass.simpleName}: ${e.message}")
                        Thread.sleep(100)
                    }
                }
            }
        }
    }

    /**
     * 解析状态通知（严格复刻原厂 b.smali 接收线程 + QringPinter$2 分发）：
     * - 故障帧：0xFF 前缀 + 状态码（仅 1 缺纸/2 开盖/3 过热/4 低电量），
     *   [0xFF,0x00] 是“无故障”心跳/查询应答，必须丢弃——绝不能当 0% 电量
     *   （旧实现因此永远显示 0%：QUERY_STATUS 的 FF00 应答抢先关掉了电量窗口）；
     * - 电量帧：查询窗口内 2 字节帧，第 2 字节即百分比（原厂直接 valueOf 显示），
     *   实测帧头可能为 0xFF 或命令回显字节；0..4 与心跳/状态码冲突故仅接受 5..100，
     *   低于 5% 的真实电量由“低电量(4)”状态提示覆盖。
     */
    private fun decode(pending: MutableList<Byte>) {
        while (pending.isNotEmpty()) {
            val first = pending.first().toInt() and 0xFF
            if (first == PrinterCommand.STATUS_PREFIX) {
                if (pending.size < 2) return
                val code = pending[1].toInt() and 0xFF
                pending.removeAt(0)
                pending.removeAt(0)
                val status = PrinterStatus.entries.firstOrNull { it.code == code }
                when {
                    status != null -> {
                        recordStatus(status)
                        statusEventsInternal.tryEmit(status)
                    }
                    code == 0 -> {
                        // 无故障心跳，显式丢弃（不吃电量窗口）。
                        Log.d(TAG, "状态心跳：FF 00（无故障）")
                    }
                    batteryQueryPending && code in BATTERY_VALID_RANGE -> {
                        batteryQueryPending = false
                        Log.i(TAG, "电量响应（FF帧头）：$code%")
                        applyBatteryPercent(code)
                    }
                    else -> Log.d(TAG, "未识别帧：FF ${code.toString(16)}")
                }
            } else if (batteryQueryPending && pending.size >= 2) {
                val value = pending[1].toInt() and 0xFF
                if (value in BATTERY_VALID_RANGE) {
                    pending.removeAt(0)
                    pending.removeAt(0)
                    batteryQueryPending = false
                    Log.i(TAG, "电量响应（帧头 ${first.toString(16)}）：$value%")
                    applyBatteryPercent(value)
                } else {
                    pending.removeAt(0)
                }
            } else {
                pending.removeAt(0)
            }
        }
    }

    /** 记录故障上报时间戳并刷新快照（恢复后不再上报、TTL 过期自动消除）。 */
    private fun recordStatus(status: PrinterStatus) {
        statusTimestamps[status] = System.currentTimeMillis()
        deviceStateInternal.update {
            it.copy(activeStatuses = statusTimestamps.keys.toSet())
        }
    }

    /** 电量响应值就是百分比（0..100），直接采用。 */
    private fun applyBatteryPercent(percent: Int) {
        Log.i(TAG, "电量上报：$percent%")
        deviceStateInternal.update { it.copy(batteryPercent = percent) }
    }

    /** 连接保活：周期查询状态刷新故障显示，附带周期电量查询，并清理过期故障。 */
    private fun startStatePoller() {
        pollJob?.cancel()
        pollJob = pollScope.launch {
            var tick = 0
            while (isActive) {
                // 打印任务进行中：禁止下发任何查询字节，否则会污染 GS v 0 光栅数据流。
                // 过热等故障由打印机异步上报，接收线程照常解析，不依赖轮询诱导。
                if (!printing) {
                    val outcome = if (tick % PrinterCommand.BATTERY_POLL_EVERY_N_TICKS == 0) {
                        batteryQueryPending = true
                        batteryQueryAtMs = System.currentTimeMillis()
                        write(PrinterCommand.QUERY_BATTERY)
                    } else {
                        write(PrinterCommand.QUERY_STATUS)
                    }
                    // 保活查询都写不出去：说明链路已失效但状态仍显示「已连接」。
                    // 立即断开刷新全局状态，由 PrinterAutoConnect 重新连接。
                    if (outcome is WriteOutcome.Failure &&
                        connectionStateInternal.value is ConnectionState.Connected
                    ) {
                        Log.w(TAG, "保活查询写入失败，判定设备已断联：${outcome.reason}")
                        disconnect()
                        continue
                    }
                }
                pruneExpiredStatuses()
                tick++
                delay(PrinterCommand.STATE_POLL_INTERVAL_MS)
            }
        }
    }

    private fun requestBattery() {
        if (printing) return // 同上：打印期间不下发电量查询。
        batteryQueryPending = true
        batteryQueryAtMs = System.currentTimeMillis()
        write(PrinterCommand.QUERY_BATTERY)
    }

    private fun pruneExpiredStatuses() {
        if (batteryQueryPending &&
            System.currentTimeMillis() - batteryQueryAtMs > PrinterCommand.BATTERY_REPLY_TIMEOUT_MS
        ) {
            batteryQueryPending = false
        }
        val deadline = System.currentTimeMillis() - PrinterCommand.STATUS_TTL_MS
        statusTimestamps.entries.removeAll { it.value < deadline }
        deviceStateInternal.update {
            val snapshot = statusTimestamps.keys.toSet()
            if (snapshot != it.activeStatuses) it.copy(activeStatuses = snapshot) else it
        }
    }

    private enum class SocketStrategy { SecureSpp, InsecureSpp, ReflectSecureChannel1, ReflectInsecureChannel1 }

    private companion object {
        const val CHANNEL_1 = 1
        const val TAG = "PencilPrinter"

        /**
         * 合法电量百分比区间。0 与“无故障心跳” FF00 冲突、1..4 与状态码冲突，
         * 故仅接受 5..100（极低电量由低电量状态提示）。
         */
        val BATTERY_VALID_RANGE = 5..100
    }
}
