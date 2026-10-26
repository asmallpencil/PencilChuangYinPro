package com.pencil.chuangyin.desktop.printer

/**
 * 打印机字节流通道抽象。
 *
 * 安卓端是 `BluetoothSocket.getOutputStream()/getInputStream()`；
 * 桌面端有两个等价实现：[RfcommTransport]（Winsock AF_BTH）与 [SerialSppTransport]（SPP 虚拟串口）。
 */
interface PrinterTransport {

    /** 面向用户的通道描述，用于错误提示。 */
    val description: String

    /** 通道是否可用。 */
    val isOpen: Boolean

    /** 建立连接；失败必须抛 [BluetoothChannelException]。 */
    fun open(timeoutMs: Int)

    /** 写入全部字节；失败抛 [BluetoothChannelException]。 */
    fun write(bytes: ByteArray)

    /**
     * 读取最多 `buffer.size` 字节。
     *
     * @return 大于 0 为实际字节数；0 表示读超时（连接仍然有效）；-1 表示连接已断开。
     */
    fun read(buffer: ByteArray): Int

    /** 关闭通道（幂等）。 */
    fun close()
}
