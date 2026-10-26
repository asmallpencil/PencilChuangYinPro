package com.pencil.chuangyin.desktop.printer

import com.pencil.chuangyin.desktop.text.PrinterStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 打印机连接与运行时状态的**唯一**数据源。
 *
 * 对齐安卓端 `BluetoothPrinterConnection`：
 *  - 持有传输通道（RFCOMM 或 SPP 串口）、读写串行化、断线检测；
 *  - 后台 3 秒轮询状态、每 5 次轮询查一次电量；
 *  - 把 `FF 01..04` 故障、`0xAA` 回执、电量值解析成 [PrinterStatus] 与 [PrinterDeviceState]。
 */
object PrinterConnectionStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceState = MutableStateFlow(PrinterDeviceState())
    val deviceState: StateFlow<PrinterDeviceState> = _deviceState.asStateFlow()

    private val _statusEvents = MutableSharedFlow<PrinterStatus>(extraBufferCapacity = 16)
    val statusEvents: SharedFlow<PrinterStatus> = _statusEvents.asSharedFlow()

    private var transport: PrinterTransport? = null
    private var readJob: Job? = null
    private var pollJob: Job? = null

    /** 打印期间禁止下发查询字节，避免打断光栅数据流。 */
    @Volatile
    private var printing = false

    private val statusSince = ConcurrentHashMap<PrinterStatus, Long>()

    val isConnected: Boolean
        get() = transport != null && _connectionState.value is ConnectionState.Connected

    val connectedName: String?
        get() = (_connectionState.value as? ConnectionState.Connected)?.device?.displayName

    val connectedDevice: PrinterDevice?
        get() = (_connectionState.value as? ConnectionState.Connected)?.device

    /** 当前连接使用的串口（若走的是 SPP 串口通道）。 */
    val connectedComPort: String?
        get() = (_connectionState.value as? ConnectionState.Connected)?.device?.comPort

    // ---------------------------------------------------------------- 连接管理

    /**
     * 连接指定设备。回调在后台线程触发，调用方自行切换到 UI 线程（桌面端通常写 StateFlow）。
     */
    fun connect(device: PrinterDevice, onResult: (ConnectResult) -> Unit = {}) {
        when (_connectionState.value) {
            is ConnectionState.Connected -> {
                onResult(ConnectResult.Success)
                return
            }
            is ConnectionState.Connecting -> return
            else -> Unit
        }
        _connectionState.value = ConnectionState.Connecting(device)
        scope.launch {
            val result = try {
                val opened = openTransport(device)
                transport = opened
                statusSince.clear()
                _deviceState.value = PrinterDeviceState()
                _connectionState.value = ConnectionState.Connected(device)
                startReadLoop()
                startPoller()
                ConnectResult.Success
            } catch (e: BluetoothChannelException) {
                resetToDisconnected()
                ConnectResult.Failure(e.error, e.message)
            } catch (t: Throwable) {
                resetToDisconnected()
                ConnectResult.Failure(ConnectError.Generic, t.message ?: t.javaClass.simpleName)
            }
            runCatching { onResult(result) }
        }
    }

    fun disconnect() {
        val current = transport
        transport = null
        printing = false
        readJob?.cancel()
        readJob = null
        pollJob?.cancel()
        pollJob = null
        statusSince.clear()
        _connectionState.value = ConnectionState.Disconnected
        _deviceState.value = PrinterDeviceState()
        runCatching { current?.close() }
    }

    /** 连接优先序：已知 SPP 串口 → RFCOMM 直连。 */
    private fun openTransport(device: PrinterDevice): PrinterTransport {
        val errors = ArrayList<String>(2)
        val comPort = device.comPort ?: BluetoothDevices.sppPortFor(device.macAddress)
        if (!comPort.isNullOrBlank()) {
            try {
                val serial = SerialSppTransport(comPort)
                serial.open(PrinterProtocol.CONNECT_TIMEOUT_MS)
                return serial
            } catch (t: Throwable) {
                errors += "串口 $comPort：${t.message ?: "打开失败"}"
            }
        }
        if (device.macAddress.isNotBlank()) {
            try {
                val rfcomm = RfcommTransport(device.macAddress)
                rfcomm.open(PrinterProtocol.CONNECT_TIMEOUT_MS)
                return rfcomm
            } catch (t: Throwable) {
                errors += "蓝牙：${t.message ?: "连接失败"}"
            }
        }
        throw BluetoothChannelException(
            error = ConnectError.Generic,
            message = errors.joinToString("；").ifEmpty { "没有可用的连接通道，请在系统设置中配对该打印机" },
        )
    }

    private fun resetToDisconnected() {
        transport = null
        _connectionState.value = ConnectionState.Disconnected
        _deviceState.value = PrinterDeviceState()
        statusSince.clear()
    }

    // ---------------------------------------------------------------- 写入

    /** 按 1024 字节分块写入（安卓端 SDK 同样如此），块间 1ms。 */
    suspend fun write(bytes: ByteArray) {
        val target = transport
            ?: throw BluetoothChannelException(ConnectError.Generic, "打印机未连接")
        if (bytes.isEmpty()) return
        try {
            writeLock.withLock {
                var offset = 0
                while (offset < bytes.size) {
                    val end = minOf(offset + PrinterProtocol.CHUNK_SIZE, bytes.size)
                    val chunk = if (offset == 0 && end == bytes.size) bytes else bytes.copyOfRange(offset, end)
                    target.write(chunk)
                    offset = end
                    if (offset < bytes.size) delay(PrinterProtocol.CHUNK_DELAY_MS)
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            handleLost()
            throw t
        }
    }

    /** 紧急停止：连发 3 次停止指令（尽力而为，不加写锁以免被卡住的任务拖死）。 */
    suspend fun emergencyStop() {
        val target = transport ?: return
        repeat(3) {
            runCatching { target.write(PrinterProtocol.STOP_PRINT_JOB) }
            delay(60)
        }
    }

    fun setPrinting(active: Boolean) {
        printing = active
    }

    // ---------------------------------------------------------------- 接收与轮询

    private fun startReadLoop() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(512)
            val pending = ArrayList<Int>(600)
            while (isActive) {
                val target = transport ?: break
                val read = try {
                    target.read(buffer)
                } catch (t: Throwable) {
                    -1
                }
                if (read < 0) {
                    handleLost()
                    break
                }
                if (read > 0) {
                    for (index in 0 until read) pending.add(buffer[index].toInt() and 0xFF)
                    decode(pending)
                }
            }
        }
    }

    private fun startPoller() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var tick = 0
            while (isActive) {
                delay(PrinterProtocol.STATE_POLL_INTERVAL_MS)
                tick++
                if (!printing) {
                    try {
                        write(PrinterProtocol.QUERY_STATUS)
                        if (tick % PrinterProtocol.BATTERY_POLL_EVERY_N_TICKS == 0) {
                            write(PrinterProtocol.QUERY_BATTERY)
                        }
                    } catch (c: CancellationException) {
                        throw c
                    } catch (_: Throwable) {
                        handleLost()
                        break
                    }
                }
                pruneExpiredStatuses()
            }
        }
    }

    /**
     * 解析接收缓冲。
     *
     *  - `FF 00`：无故障心跳，必须丢弃（否则会误判成"缺纸"的前缀）；
     *  - `FF 01..04`：缺纸 / 开盖 / 过热 / 低电量；
     *  - `AA`：指令回执；
     *  - `05..64`：电量百分比。
     */
    private fun decode(pending: MutableList<Int>) {
        var index = 0
        while (index < pending.size) {
            val byte = pending[index]
            when {
                byte == 0xFF -> {
                    if (index + 1 >= pending.size) break
                    when (pending[index + 1]) {
                        0x01 -> reportStatus(PrinterStatus.OutOfPaper)
                        0x02 -> reportStatus(PrinterStatus.CoverOpen)
                        0x03 -> reportStatus(PrinterStatus.Overheat)
                        0x04 -> reportStatus(PrinterStatus.LowBattery)
                        else -> Unit
                    }
                    index += 2
                }
                byte == 0xAA -> index += 1
                byte in 5..100 -> {
                    _deviceState.update { it.copy(batteryPercent = byte) }
                    index += 1
                }
                else -> index += 1
            }
        }
        if (index > 0) pending.subList(0, index).clear()
    }

    private fun reportStatus(status: PrinterStatus) {
        statusSince[status] = System.currentTimeMillis()
        _deviceState.update { it.copy(activeStatuses = it.activeStatuses + status) }
        _statusEvents.tryEmit(status)
    }

    /** 故障有 7 秒有效期：超过时间没有再上报即视为恢复。 */
    private fun pruneExpiredStatuses() {
        if (statusSince.isEmpty()) return
        val now = System.currentTimeMillis()
        val expired = statusSince.entries
            .filter { now - it.value > PrinterProtocol.STATUS_TTL_MS }
            .map { it.key }
        if (expired.isEmpty()) return
        expired.forEach { statusSince.remove(it) }
        _deviceState.update { state ->
            state.copy(activeStatuses = state.activeStatuses - expired.toSet())
        }
    }

    private fun handleLost() {
        val current = transport
        transport = null
        printing = false
        readJob?.cancel()
        readJob = null
        pollJob?.cancel()
        pollJob = null
        statusSince.clear()
        if (_connectionState.value !is ConnectionState.Disconnected) {
            _connectionState.value = ConnectionState.Disconnected
        }
        _deviceState.value = PrinterDeviceState()
        runCatching { current?.close() }
    }
}
