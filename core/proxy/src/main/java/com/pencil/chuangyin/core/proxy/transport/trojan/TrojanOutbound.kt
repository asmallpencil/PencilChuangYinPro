package com.pencil.chuangyin.core.proxy.transport.trojan

import com.pencil.chuangyin.core.proxy.model.ProxyNode
import com.pencil.chuangyin.core.proxy.transport.TunnelConnection
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Trojan（TCP over TLS）出站连接。
 *
 * 握手协议：
 * 1. TLS 建连（SNI 取 `sni`，缺省用 server）；
 * 2. 发送 `hex(SHA224(password))\r\n`；
 * 3. 发送 `CONNECT host:port\r\n`，之后即为裸字节转发。
 *
 * 仅支持 TCP（Clash 配置中的 `udp: true` 会被忽略），UDP 流量仍由系统的
 * TCP 请求路径处理，不会静默直连。
 */
object TrojanOutbound {

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val HANDSHAKE_TIMEOUT_MS = 8_000

    fun connect(node: ProxyNode, targetHost: String, targetPort: Int): TunnelConnection {
        val password = node.password?.takeIf { it.isNotBlank() }
            ?: throw IOException("Trojan 节点缺少密码")
        val socket = createTlsSocket(node)
        try {
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            socket.outputStream.write(
                buildString {
                    append(hashPassword(password))
                    append("\r\nCONNECT ")
                    append(targetHost)
                    append(':')
                    append(targetPort)
                    append("\r\n")
                }.toByteArray(Charsets.UTF_8),
            )
            socket.outputStream.flush()
            socket.soTimeout = 0
            return TunnelConnection(socket, socket.inputStream, socket.outputStream)
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw if (t is IOException) t else IOException(t.message ?: "Trojan 连接失败", t)
        }
    }

    private fun createTlsSocket(node: ProxyNode): Socket {
        val raw = Socket()
        raw.connect(InetSocketAddress(node.server, node.port), CONNECT_TIMEOUT_MS)
        raw.soTimeout = HANDSHAKE_TIMEOUT_MS
        raw.tcpNoDelay = true

        val context = try {
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, if (node.skipCertVerify) TRUST_ALL_MANAGERS else null, null)
            ctx
        } catch (t: Throwable) {
            raw.closeQuietly()
            throw IOException("初始化 TLS 失败：${t.message.orEmpty()}", t)
        }
        val sni = node.sni?.takeIf { it.isNotBlank() } ?: node.server
        val socket = try {
            context.socketFactory.createSocket(raw, node.server, node.port, true) as SSLSocket
        } catch (t: Throwable) {
            raw.closeQuietly()
            throw IOException("创建 TLS 连接失败：${t.message.orEmpty()}", t)
        }
        runCatching {
            val parameters = socket.sslParameters
            parameters.serverNames = listOf(SNIHostName(sni))
            socket.sslParameters = parameters
        }
        socket.startHandshake()
        return socket
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-224").digest(password.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(digest.size * 2)
        digest.forEach { byte ->
            val value = byte.toInt() and 0xFF
            builder.append(HEX_DIGITS[value ushr 4])
            builder.append(HEX_DIGITS[value and 0x0F])
        }
        return builder.toString()
    }

    private fun Socket.closeQuietly() {
        runCatching { close() }
    }

    private const val HEX_DIGITS = "0123456789abcdef"

    /** 跳过证书校验时使用（对应 Clash 的 skip-cert-verify: true）。 */
    private val TRUST_ALL_MANAGERS: Array<TrustManager> by lazy {
        arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
    }
}
