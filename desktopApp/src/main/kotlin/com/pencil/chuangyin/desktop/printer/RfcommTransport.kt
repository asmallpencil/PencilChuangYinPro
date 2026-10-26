package com.pencil.chuangyin.desktop.printer

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Winsock 中本模块用到的最小函数集。
 *
 * JVM 没有内置蓝牙 API，也无法注册 Windows 的 C++ 蓝牙栈，因此直接通过 JNA
 * 调用 `ws2_32.dll`：`socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM)` + SPP 服务 GUID，
 * 等价于安卓端的 `BluetoothSocket(SPP UUID)`。
 */
internal interface Ws2_32 : Library {
    fun WSAStartup(wVersionRequested: Int, lpWSAData: Pointer): Int
    fun WSACleanup(): Int
    fun socket(af: Int, type: Int, protocol: Int): Long
    fun connect(s: Long, name: Pointer, namelen: Int): Int
    fun send(s: Long, buf: Pointer, len: Int, flags: Int): Int
    fun recv(s: Long, buf: Pointer, len: Int, flags: Int): Int
    fun closesocket(s: Long): Int
    fun WSAGetLastError(): Int
    fun setsockopt(s: Long, level: Int, optname: Int, optval: Pointer, optlen: Int): Int
    fun getsockopt(s: Long, level: Int, optname: Int, optval: Pointer, optlen: IntByReference): Int
    fun ioctlsocket(s: Long, cmd: Int, argp: Pointer): Int
    fun select(nfds: Int, readfds: Pointer, writefds: Pointer, exceptfds: Pointer, timeout: Pointer): Int
}

/** Winsock 常量。 */
private object Wsa {
    const val AF_BTH = 32
    const val BTHPROTO_RFCOMM = 3
    const val SOCK_STREAM = 1

    const val SOL_SOCKET = 0xFFFF
    const val SO_ERROR = 0x1007
    const val SO_RCVTIMEO = 0x1006
    const val FIONBIO = 0x8004667E.toInt()

    const val INVALID_SOCKET = -1L
    const val SOCKET_ERROR = -1

    const val WSAEWOULDBLOCK = 10035
    const val WSAEINPROGRESS = 10036
    const val WSAETIMEDOUT = 10060
}

/**
 * 串口服务（SPP）UUID 的 GUID 内存布局：
 * `00001101-0000-1000-8000-00805F9B34FB`
 *  - Data1 = 0x00001101 → `01 11 00 00`
 *  - Data2 = 0x0000     → `00 00`
 *  - Data3 = 0x1000     → `00 10`
 *  - Data4              → `80 00 00 80 5F 9B 34 FB`
 */
private val SPP_GUID: ByteArray = byteArrayOf(
    0x01, 0x11, 0x00, 0x00,
    0x00, 0x00,
    0x00, 0x10,
    0x80.toByte(), 0x00, 0x00, 0x80.toByte(),
    0x5F, 0x9B.toByte(), 0x34, 0xFB.toByte(),
)

/**
 * RFCOMM 直连通道（主通道）。
 *
 * `SOCKADDR_BTH` 在不同编译单元下有两种真实布局（是否被 `#pragma pack(1)` 压缩），
 * 无法在运行时探测，因此这里把两种布局都作为候选尝试，并对 `port=0`（触发 SDP 解析）
 * 与常见 RFCOMM 通道号 1..3 做兜底。
 */
class RfcommTransport(
    private val macAddress: String,
    private val readTimeoutMs: Int = 2_000,
) : PrinterTransport {

    private var socket: Long = Wsa.INVALID_SOCKET

    override val description: String get() = "RFCOMM $macAddress"

    override val isOpen: Boolean get() = socket != Wsa.INVALID_SOCKET

    override fun open(timeoutMs: Int) {
        ensureWinsock()
        val address = parseBtAddress(macAddress)
            ?: throw BluetoothChannelException(ConnectError.Generic, "无法解析蓝牙地址：$macAddress")

        var lastError: BluetoothChannelException? = null
        val perAttempt = timeoutMs.coerceIn(1_000, 6_000)
        for (candidate in candidates()) {
            try {
                socket = connectOnce(address, candidate, perAttempt)
                return
            } catch (e: BluetoothChannelException) {
                lastError = e
                closeSocket()
            }
        }
        throw lastError ?: BluetoothChannelException(ConnectError.Generic, "无法连接到 $macAddress")
    }

    override fun write(bytes: ByteArray) {
        if (!isOpen) throw BluetoothChannelException(ConnectError.Generic, "蓝牙连接已关闭")
        if (bytes.isEmpty()) return
        val memory = Memory(bytes.size.toLong())
        memory.write(0L, bytes, 0, bytes.size)
        var sent = 0
        while (sent < bytes.size) {
            val rc = lib.send(socket, memory.share(sent.toLong()), bytes.size - sent, 0)
            if (rc <= 0) throw describe(lib.WSAGetLastError())
            sent += rc
        }
    }

    override fun read(buffer: ByteArray): Int {
        if (!isOpen) return -1
        val memory = Memory(buffer.size.toLong())
        val rc = lib.recv(socket, memory, buffer.size, 0)
        if (rc > 0) {
            memory.read(0L, buffer, 0, rc)
            return rc
        }
        if (rc == 0) return -1
        val error = lib.WSAGetLastError()
        return if (error == Wsa.WSAETIMEDOUT || error == Wsa.WSAEWOULDBLOCK) 0 else -1
    }

    override fun close() {
        closeSocket()
    }

    // ---------------------------------------------------------------- 内部实现

    private fun closeSocket() {
        if (socket != Wsa.INVALID_SOCKET) {
            runCatching { lib.closesocket(socket) }
            socket = Wsa.INVALID_SOCKET
        }
    }

    private data class Candidate(val padded: Boolean, val port: Int, val reversedAddress: Boolean)

    private fun candidates(): List<Candidate> = buildList {
        // 先试 SDP 自动解析（port = 0），地址按 Windows 的 BTH_ADDR 小端约定（字节逆序）
        add(Candidate(padded = false, port = 0, reversedAddress = true))
        add(Candidate(padded = true, port = 0, reversedAddress = true))
        add(Candidate(padded = false, port = 0, reversedAddress = false))
        add(Candidate(padded = true, port = 0, reversedAddress = false))
        // SDP 失败时按常见 RFCOMM 通道号兜底
        for (port in 1..3) {
            add(Candidate(padded = false, port = port, reversedAddress = true))
            add(Candidate(padded = true, port = port, reversedAddress = true))
        }
    }

    private fun connectOnce(address: Long, candidate: Candidate, timeoutMs: Int): Long {
        val handle = lib.socket(Wsa.AF_BTH, Wsa.SOCK_STREAM, Wsa.BTHPROTO_RFCOMM)
        if (handle == Wsa.INVALID_SOCKET) throw describe(lib.WSAGetLastError())

        val target = if (candidate.reversedAddress) reverseBytes(address) else address
        val sockAddr = buildSockAddr(target, candidate.port, candidate.padded)
        try {
            setNonBlocking(handle, true)
            val rc = lib.connect(handle, sockAddr, if (candidate.padded) 40 else 30)
            if (rc == Wsa.SOCKET_ERROR) {
                val error = lib.WSAGetLastError()
                if (error != Wsa.WSAEWOULDBLOCK && error != Wsa.WSAEINPROGRESS) throw describe(error)
                if (!waitWritable(handle, timeoutMs)) {
                    throw BluetoothChannelException(ConnectError.Timeout, "蓝牙连接超时")
                }
                val soError = readSoError(handle)
                if (soError != 0) throw describe(soError)
            }
            setNonBlocking(handle, false)
            setReadTimeout(handle, readTimeoutMs)
            return handle
        } catch (t: Throwable) {
            runCatching { lib.closesocket(handle) }
            throw t
        }
    }

    private fun buildSockAddr(btAddr: Long, port: Int, padded: Boolean): Memory {
        val size = if (padded) 40 else 30
        val memory = Memory(size.toLong())
        memory.clear()
        memory.setShort(0L, Wsa.AF_BTH.toShort())
        val addrOffset = if (padded) 8L else 2L
        memory.setLong(addrOffset, btAddr)
        val guidOffset = if (padded) 16L else 10L
        memory.write(guidOffset, SPP_GUID, 0, SPP_GUID.size)
        val portOffset = if (padded) 32L else 26L
        memory.setInt(portOffset, port)
        return memory
    }

    private fun setNonBlocking(handle: Long, enabled: Boolean) {
        val buffer = Memory(4)
        buffer.setInt(0L, if (enabled) 1 else 0)
        lib.ioctlsocket(handle, Wsa.FIONBIO, buffer)
    }

    private fun setReadTimeout(handle: Long, millis: Int) {
        val buffer = Memory(4)
        buffer.setInt(0L, millis)
        lib.setsockopt(handle, Wsa.SOL_SOCKET, Wsa.SO_RCVTIMEO, buffer, 4)
    }

    private fun waitWritable(handle: Long, timeoutMs: Int): Boolean {
        val pointerSize = Native.POINTER_SIZE
        val fdSet = Memory((pointerSize + pointerSize).toLong())
        fdSet.clear()
        fdSet.setInt(0L, 1)
        val fdOffset = if (pointerSize == 8) 8L else 4L
        if (pointerSize == 8) fdSet.setLong(fdOffset, handle) else fdSet.setInt(fdOffset, handle.toInt())

        val timeval = Memory(8)
        timeval.setInt(0L, timeoutMs / 1000)
        timeval.setInt(4L, (timeoutMs % 1000) * 1000)

        val rc = lib.select(0, Pointer.NULL, fdSet, Pointer.NULL, timeval)
        return rc > 0
    }

    private fun readSoError(handle: Long): Int {
        val buffer = Memory(4)
        val length = IntByReference(4)
        val rc = lib.getsockopt(handle, Wsa.SOL_SOCKET, Wsa.SO_ERROR, buffer, length)
        return if (rc == 0) buffer.getInt(0L) else 0
    }

    private fun describe(code: Int): BluetoothChannelException = when (code) {
        10013 -> BluetoothChannelException(ConnectError.Permission, "蓝牙访问被系统拒绝（$code）")
        10060 -> BluetoothChannelException(ConnectError.Timeout, "蓝牙连接超时（$code）")
        10049 -> BluetoothChannelException(ConnectError.NotSupported, "蓝牙地址不可用，请先在系统里配对（$code）")
        10050 -> BluetoothChannelException(ConnectError.BluetoothOff, "网络/蓝牙适配器已关闭（$code）")
        10051, 10065 -> BluetoothChannelException(ConnectError.NotSupported, "打印机不可达，请确认已开机且在范围内（$code）")
        10061 -> BluetoothChannelException(ConnectError.NotSupported, "打印机没有可用的打印服务（$code）")
        11001 -> BluetoothChannelException(ConnectError.NotSupported, "服务发现失败，设备可能不支持串口打印（$code）")
        10093 -> BluetoothChannelException(ConnectError.Generic, "Winsock 未初始化（$code）")
        else -> BluetoothChannelException(ConnectError.Generic, "蓝牙连接失败（$code）")
    }

    companion object {

        private val startupDone = AtomicBoolean(false)

        internal val lib: Ws2_32 by lazy { Native.load("ws2_32", Ws2_32::class.java) }

        /** 进程内只需初始化一次 Winsock。 */
        fun ensureWinsock() {
            if (startupDone.get()) return
            synchronized(this) {
                if (startupDone.get()) return
                val data = Memory(512)
                val rc = lib.WSAStartup(0x0202, data)
                if (rc != 0) {
                    throw BluetoothChannelException(ConnectError.Generic, "Winsock 初始化失败（$rc）")
                }
                startupDone.set(true)
            }
        }

        /** `AA:BB:CC:DD:EE:FF` → `0xAABBCCDDEEFF`。 */
        internal fun parseBtAddress(mac: String): Long? {
            val hex = mac.filter { it.isLetterOrDigit() }
            if (hex.length != 12) return null
            return try {
                java.lang.Long.parseLong(hex, 16)
            } catch (_: NumberFormatException) {
                null
            }
        }

        /**
         * 把数值按字节逆序排列：`0x001122334455` → `0x554433221100`。
         *
         * Windows 的 `BTH_ADDR` 是一个 ULONGLONG，其最低字节对应 MAC 的第一个字节，
         * 因此写进 `SOCKADDR_BTH.btAddr` 的值需要把人类可读顺序反过来。
         */
        internal fun reverseBytes(value: Long): Long {
            var result = 0L
            for (index in 0 until 6) {
                val byte = (value ushr (8 * index)) and 0xFF
                result = result or (byte shl (8 * (5 - index)))
            }
            return result
        }
    }
}
