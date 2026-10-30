package com.pencil.chuangyin.core.proxy.bridge

import com.pencil.chuangyin.core.proxy.model.ProxyNode
import com.pencil.chuangyin.core.proxy.model.ProxyProtocol
import com.pencil.chuangyin.core.proxy.route.Outbound
import com.pencil.chuangyin.core.proxy.route.RouteDecision
import com.pencil.chuangyin.core.proxy.transport.TunnelConnection
import com.pencil.chuangyin.core.proxy.transport.ss.ShadowsocksOutbound
import com.pencil.chuangyin.core.proxy.transport.trojan.TrojanOutbound
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 进程内 SOCKS5 桥接。
 *
 * Java 的 [java.net.Proxy] 只能表达 HTTP / SOCKS5 两种「已存在」的代理服务器，
 * 无法承载 Shadowsocks / Trojan 这类需要客户端协议栈的节点。桥接的做法：
 *
 * 1. 监听 `127.0.0.1` 上一个临时端口（无鉴权，仅本进程可达）；
 * 2. 收到 SOCKS5 请求后把目标 `host:port` 交给路由层，得到最终出口；
 * 3. 直连或按协议建立隧道，把两侧字节流对接。
 *
 * 它只在本应用内使用，不注册 VpnService、不修改系统设置，因此不影响设备其他流量。
 */
class LocalSocks5Bridge(
    private val resolver: (host: String, port: Int) -> RouteDecision,
) {

    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "proxy-bridge").apply { isDaemon = true }
    }

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var localPort: Int = 0

    /** 启动（或复用）监听端口；失败返回 null，调用方应回落到失败关闭。 */
    @Synchronized
    fun ensureStarted(): Int? {
        val existing = serverSocket
        if (existing != null && !existing.isClosed && localPort in 1..65535) return localPort
        return try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName(HOST), 0), BACKLOG)
            serverSocket = socket
            localPort = socket.localPort
            startAcceptLoop(socket)
            localPort
        } catch (t: Throwable) {
            runCatching { serverSocket?.close() }
            serverSocket = null
            localPort = 0
            null
        }
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        localPort = 0
    }

    private fun startAcceptLoop(socket: ServerSocket) {
        val thread = Thread({
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching { executor.execute { handle(client) } }
                    .onFailure { runCatching { client.close() } }
            }
        }, "proxy-bridge-accept")
        thread.isDaemon = true
        thread.start()
    }

    // ---------------- 单个连接 ----------------

    private fun handle(client: Socket) {
        try {
            client.soTimeout = HANDSHAKE_TIMEOUT_MS
            val input = client.getInputStream()
            val output = client.getOutputStream()
            if (!negotiate(input, output)) return
            val request = readRequest(input) ?: return

            val decision = resolver(request.host, request.port)
            val tunnel = when (val outbound = decision.outbound) {
                Outbound.Direct -> dialDirect(request.host, request.port)
                is Outbound.Node -> dialNode(outbound.node, request.host, request.port)
                else -> {
                    reply(output, REPLY_REFUSED)
                    return
                }
            }
            reply(output, REPLY_SUCCEEDED)
            client.soTimeout = 0
            relay(client, tunnel)
        } catch (t: Throwable) {
            runCatching { client.close() }
            return
        }
    }

    private fun relay(client: Socket, tunnel: TunnelConnection) {
        // 远端 → 客户端在后台线程，客户端 → 远端占用当前线程，任一方向结束即整体关闭。
        executor.execute {
            copyQuietly(tunnel.input, client.getOutputStream())
            closeQuietly(client)
            tunnel.close()
        }
        copyQuietly(client.getInputStream(), tunnel.output)
        closeQuietly(client)
        tunnel.close()
    }

    private fun copyQuietly(input: InputStream, output: OutputStream) {
        try {
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: Throwable) {
            // 连接被对端关闭 / 超时：静默结束，由调用方关闭两端。
        }
    }

    private fun closeQuietly(socket: Socket) {
        runCatching { socket.close() }
    }

    // ---------------- SOCKS5 协议 ----------------

    private fun negotiate(input: InputStream, output: OutputStream): Boolean {
        val greeting = readFully(input, 2) ?: return false
        if (greeting[0].toInt() != SOCKS_VERSION) return false
        val methods = greeting[1].toInt() and 0xFF
        if (methods == 0) return false
        readFully(input, methods) ?: return false
        output.write(byteArrayOf(SOCKS_VERSION.toByte(), METHOD_NO_AUTH))
        output.flush()
        return true
    }

    private data class Target(val host: String, val port: Int)

    private fun readRequest(input: InputStream): Target? {
        val head = readFully(input, 4) ?: return null
        if (head[0].toInt() != SOCKS_VERSION) return null
        if (head[1].toInt() != COMMAND_CONNECT) return null
        val host = when (val addressType = head[3].toInt() and 0xFF) {
            ATYP_IPV4 -> readFully(input, 4)
                ?.let { InetAddress.getByAddress(it).hostAddress }
            ATYP_IPV6 -> readFully(input, 16)
                ?.let { InetAddress.getByAddress(it).hostAddress }
            ATYP_DOMAIN -> {
                val length = readFully(input, 1)?.get(0)?.toInt()?.and(0xFF) ?: return null
                if (length <= 0) return null
                readFully(input, length)?.toString(Charsets.UTF_8)
            }
            else -> null
        } ?: return null
        val portBytes = readFully(input, 2) ?: return null
        val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
        if (port !in 1..65535) return null
        return Target(host, port)
    }

    private fun reply(output: OutputStream, code: Byte) {
        output.write(
            byteArrayOf(
                SOCKS_VERSION.toByte(), code, 0x00,
                ATYP_IPV4.toByte(), 0, 0, 0, 0,
                0, 0,
            ),
        )
        output.flush()
    }

    // ---------------- 拨号 ----------------

    private fun dialDirect(host: String, port: Int): TunnelConnection {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        socket.tcpNoDelay = true
        return TunnelConnection(socket, socket.getInputStream(), socket.getOutputStream())
    }

    private fun dialNode(node: ProxyNode, host: String, port: Int): TunnelConnection =
        when (node.protocol) {
            ProxyProtocol.Shadowsocks -> ShadowsocksOutbound.connect(node, host, port)
            ProxyProtocol.Trojan -> TrojanOutbound.connect(node, host, port)
            else -> throw IOException("本地桥接不承载 ${node.protocol.token} 协议")
        }

    private fun readFully(input: InputStream, size: Int): ByteArray? {
        val buffer = ByteArray(size)
        var read = 0
        while (read < size) {
            val n = input.read(buffer, read, size - read)
            if (n < 0) return null
            read += n
        }
        return buffer
    }

    private companion object {
        const val HOST = "127.0.0.1"
        const val BACKLOG = 64
        const val BUFFER_SIZE = 16 * 1024
        const val HANDSHAKE_TIMEOUT_MS = 10_000
        const val CONNECT_TIMEOUT_MS = 10_000

        const val SOCKS_VERSION = 5
        const val METHOD_NO_AUTH: Byte = 0x00
        const val COMMAND_CONNECT = 1
        const val ATYP_IPV4 = 1
        const val ATYP_DOMAIN = 3
        const val ATYP_IPV6 = 4
        const val REPLY_SUCCEEDED: Byte = 0x00
        const val REPLY_REFUSED: Byte = 0x05
    }
}
