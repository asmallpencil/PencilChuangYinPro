package com.pencil.chuangyin.desktop.printer

import com.fazecast.jSerialComm.SerialPort

/**
 * SPP 虚拟串口通道（备选/首选兜底）。
 *
 * Windows 在设备配对后会为串口服务生成「蓝牙链接上的标准串行 (COMx)」，
 * 该端口已经把 RFCOMM 全封装好，读写等价于安卓端的 SPP Socket，且无需额外权限。
 */
class SerialSppTransport(
    private val portName: String,
    private val baudRate: Int = 9600,
) : PrinterTransport {

    private var port: SerialPort? = null

    override val description: String get() = "串口 $portName"

    override val isOpen: Boolean get() = port?.isOpen == true

    override fun open(timeoutMs: Int) {
        val target = SerialPort.getCommPort(portName)
            ?: throw BluetoothChannelException(ConnectError.NotSupported, "系统里没有 $portName")
        target.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
        target.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
        target.setComPortTimeouts(
            SerialPort.TIMEOUT_READ_SEMI_BLOCKING or SerialPort.TIMEOUT_WRITE_BLOCKING,
            200,
            2_000,
        )
        if (!target.openPort()) {
            throw BluetoothChannelException(
                ConnectError.NotSupported,
                "无法打开 $portName（可能被其它程序占用）",
            )
        }
        port = target
    }

    override fun write(bytes: ByteArray) {
        val target = port ?: throw BluetoothChannelException(ConnectError.Generic, "串口尚未打开")
        if (bytes.isEmpty()) return
        val written = target.writeBytes(bytes, bytes.size)
        if (written != bytes.size) {
            throw BluetoothChannelException(
                ConnectError.Generic,
                "串口写入不完整（$written/${bytes.size}）",
            )
        }
    }

    override fun read(buffer: ByteArray): Int {
        val target = port ?: return -1
        return when (val read = target.readBytes(buffer, buffer.size)) {
            in 1..Int.MAX_VALUE -> read
            0 -> 0
            else -> -1
        }
    }

    override fun close() {
        val target = port ?: return
        port = null
        runCatching { target.closePort() }
    }
}
