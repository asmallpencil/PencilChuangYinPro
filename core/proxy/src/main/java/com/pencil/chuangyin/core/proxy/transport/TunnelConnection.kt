package com.pencil.chuangyin.core.proxy.transport

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * 一条已经建立、可双向传输的隧道。
 *
 * 与 [java.net.Proxy] 不同，这类连接无法交给系统 Socket 工厂处理，
 * 只能由本地 SOCKS5 桥接在握手完成后把字节流对接给调用方。
 */
class TunnelConnection(
    private val socket: Socket,
    val input: InputStream,
    val output: OutputStream,
) : Closeable {

    override fun close() {
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { socket.close() }
    }
}
