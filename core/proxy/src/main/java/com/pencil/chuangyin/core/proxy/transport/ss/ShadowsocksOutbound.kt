package com.pencil.chuangyin.core.proxy.transport.ss

import com.pencil.chuangyin.core.proxy.model.ProxyNode
import com.pencil.chuangyin.core.proxy.transport.TunnelConnection
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 加密族：决定 JCE 的 transformation 与 nonce 形态。 */
internal enum class SsCipherFamily { Aes, ChaCha20 }

/**
 * 支持的 Shadowsocks AEAD 加密方式（SIP004）。
 *
 * 只覆盖 TCP 单端口 AEAD；2022 系列与 stream cipher（rc4-md5 等）不在范围内，
 * 遇到时抛出可读的 [IOException]，由界面提示而不是静默直连。
 */
internal enum class SsMethod(
    val method: String,
    val keyLength: Int,
    val saltLength: Int,
    val transformation: String,
    val jceKeyAlgorithm: String,
    val family: SsCipherFamily,
) {
    Aes128Gcm("aes-128-gcm", 16, 16, "AES/GCM/NoPadding", "AES", SsCipherFamily.Aes),
    Aes192Gcm("aes-192-gcm", 24, 24, "AES/GCM/NoPadding", "AES", SsCipherFamily.Aes),
    Aes256Gcm("aes-256-gcm", 32, 32, "AES/GCM/NoPadding", "AES", SsCipherFamily.Aes),
    ChaCha20IetfPoly1305(
        "chacha20-ietf-poly1305", 32, 32, "ChaCha20-Poly1305", "ChaCha20", SsCipherFamily.ChaCha20,
    ),
    ;

    companion object {
        fun fromName(raw: String?): SsMethod? {
            val name = raw?.trim()?.lowercase().orEmpty()
            if (name.isEmpty()) return null
            return entries.firstOrNull { it.method == name }
                ?: when (name) {
                    // 部分订阅把 SIP004 的 chacha 写成不带 ietf 的旧名。
                    "chacha20-poly1305" -> ChaCha20IetfPoly1305
                    "aead_aes_128_gcm" -> Aes128Gcm
                    "aead_aes_256_gcm" -> Aes256Gcm
                    else -> null
                }
        }
    }
}

/** Shadowsocks AEAD 的密钥派生与字节工具。 */
internal object SsCrypto {

    private const val TAG_LENGTH = 16
    private val random = SecureRandom()

    /** 读取固定长度；对端在分片边界前关闭时返回 null。 */
    fun readFully(input: InputStream, size: Int): ByteArray? {
        val buffer = ByteArray(size)
        var read = 0
        while (read < size) {
            val n = input.read(buffer, read, size - read)
            if (n < 0) {
                if (read == 0) return null
                throw IOException("Shadowsocks 连接被提前关闭")
            }
            read += n
        }
        return buffer
    }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    /** SS 的 master key：EVP_BytesToKey（MD5 迭代）。 */
    fun evpBytesToKey(password: String, keyLength: Int): ByteArray {
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("MD5")
        val out = ByteArray(keyLength)
        var previous = ByteArray(0)
        var offset = 0
        while (offset < keyLength) {
            digest.reset()
            if (previous.isNotEmpty()) digest.update(previous)
            digest.update(passwordBytes)
            previous = digest.digest()
            val n = minOf(previous.size, keyLength - offset)
            System.arraycopy(previous, 0, out, offset, n)
            offset += n
        }
        return out
    }

    /** HKDF-SHA1：SS 用 salt 从 master key 派生子密钥。 */
    fun hkdfSha1(ikm: ByteArray, salt: ByteArray, info: ByteArray, outLength: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(salt, "HmacSHA1"))
        val prk = mac.doFinal(ikm)
        val out = ByteArray(outLength)
        var produced = 0
        var previous = ByteArray(0)
        var counter = 1
        while (produced < outLength) {
            mac.init(SecretKeySpec(prk, "HmacSHA1"))
            mac.update(info)
            if (previous.isNotEmpty()) mac.update(previous)
            mac.update(byteArrayOf(counter.toByte()))
            previous = mac.doFinal()
            val n = minOf(previous.size, outLength - produced)
            System.arraycopy(previous, 0, out, produced, n)
            produced += n
            counter++
        }
        return out
    }

    fun box(method: SsMethod, key: ByteArray): SsAeadBox = SsAeadBox(method, key)

    const val TAG_LEN: Int = TAG_LENGTH
}

/** 单方向的 AEAD 封包器：每个分片独立加解密，nonce 从 0 递增。 */
internal class SsAeadBox(private val method: SsMethod, private val key: ByteArray) {

    fun encrypt(nonce: ByteArray, plaintext: ByteArray): ByteArray =
        transform(Cipher.ENCRYPT_MODE, nonce, plaintext)

    fun decrypt(nonce: ByteArray, cipherText: ByteArray): ByteArray =
        transform(Cipher.DECRYPT_MODE, nonce, cipherText)

    private fun transform(mode: Int, nonce: ByteArray, data: ByteArray): ByteArray {
        val cipher = try {
            Cipher.getInstance(method.transformation)
        } catch (t: Throwable) {
            throw IOException("当前系统不支持 ${method.method} 加密方式：${t.message.orEmpty()}", t)
        }
        try {
            val keySpec = SecretKeySpec(key, method.jceKeyAlgorithm)
            if (method.family == SsCipherFamily.Aes) {
                cipher.init(mode, keySpec, GCMParameterSpec(SsCrypto.TAG_LEN * 8, nonce))
            } else {
                cipher.init(mode, keySpec, IvParameterSpec(nonce))
            }
            return cipher.doFinal(data)
        } catch (t: Throwable) {
            throw IOException("Shadowsocks 分片加解密失败：${t.message.orEmpty()}", t)
        }
    }
}

/** 写入方向：salt 之后每个分片 = 加密的 2 字节长度 + 加密的负载。 */
internal class SsAeadOutputStream(
    private val out: OutputStream,
    private val box: SsAeadBox,
) : OutputStream() {

    private val nonce = ByteArray(NONCE_LENGTH)

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var offset = off
        var remaining = len
        while (remaining > 0) {
            val size = minOf(remaining, MAX_PAYLOAD)
            writeChunk(b, offset, size)
            offset += size
            remaining -= size
        }
        if (len > 0) out.flush()
    }

    private fun writeChunk(source: ByteArray, off: Int, size: Int) {
        val lengthBytes = byteArrayOf(
            ((size ushr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte(),
        )
        out.write(box.encrypt(nonce, lengthBytes))
        nonce.increment()
        out.write(box.encrypt(nonce, source.copyOfRange(off, off + size)))
        nonce.increment()
    }

    override fun flush() {
        out.flush()
    }

    override fun close() {
        runCatching { out.flush() }
        runCatching { out.close() }
    }

    private companion object {
        const val NONCE_LENGTH = 12
        /** 单个分片负载上限（SIP004）。 */
        const val MAX_PAYLOAD = 0x3FFF
    }
}

/** 读取方向：salt 之后逐个还原分片，对上层表现为普通 [InputStream]。 */
internal class SsAeadInputStream(
    private val input: InputStream,
    private val box: SsAeadBox,
) : InputStream() {

    private val nonce = ByteArray(NONCE_LENGTH)
    private var buffer: ByteArray = ByteArray(0)
    private var position = 0
    private var finished = false

    override fun read(): Int {
        if (!ensureAvailable()) return -1
        return buffer[position++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (!ensureAvailable()) return -1
        val size = minOf(len, buffer.size - position)
        System.arraycopy(buffer, position, b, off, size)
        position += size
        return size
    }

    override fun available(): Int = buffer.size - position

    private fun ensureAvailable(): Boolean {
        while (position >= buffer.size) {
            if (finished) return false
            if (!readChunk()) return false
        }
        return true
    }

    private fun readChunk(): Boolean {
        val lengthCipher = SsCrypto.readFully(input, lengthSize + SsCrypto.TAG_LEN) ?: run {
            finished = true
            return false
        }
        val lengthPlain = box.decrypt(nonce, lengthCipher)
        nonce.increment()
        val size = ((lengthPlain[0].toInt() and 0xFF) shl 8) or (lengthPlain[1].toInt() and 0xFF)
        if (size <= 0 || size > MAX_PAYLOAD) throw IOException("Shadowsocks 分片长度异常：$size")
        val bodyCipher = SsCrypto.readFully(input, size + SsCrypto.TAG_LEN)
            ?: throw IOException("Shadowsocks 连接被提前关闭")
        buffer = box.decrypt(nonce, bodyCipher)
        position = 0
        nonce.increment()
        return true
    }

    override fun close() {
        runCatching { input.close() }
    }

    private companion object {
        const val NONCE_LENGTH = 12
        const val lengthSize = 2
        const val MAX_PAYLOAD = 0x3FFF
    }
}

/** 目标地址编码，与 SOCKS5 请求头一致（远端解析域名）。 */
internal object SsAddress {

    fun encode(host: String, port: Int): ByteArray {
        val address = when {
            host.isIpv4Literal() -> byteArrayOf(ATYP_IPV4) + host.split('.').map { it.toInt().toByte() }.toByteArray()
            host.contains(':') -> ipv6Bytes(host)?.let { byteArrayOf(ATYP_IPV6) + it }
            else -> null
        }
        val body = address ?: run {
            val bytes = host.toByteArray(Charsets.UTF_8)
            byteArrayOf(ATYP_DOMAIN, bytes.size.toByte()) + bytes
        }
        return body + byteArrayOf(
            ((port ushr 8) and 0xFF).toByte(),
            (port and 0xFF).toByte(),
        )
    }

    private fun String.isIpv4Literal(): Boolean {
        val parts = split('.')
        if (parts.size != 4) return false
        return parts.all { part -> part.isNotEmpty() && part.length <= 3 && part.toIntOrNull() in 0..255 }
    }

    private fun ipv6Bytes(host: String): ByteArray? =
        runCatching { InetAddress.getByName(host).address }.getOrNull()
            ?.takeIf { it.size == 16 }

    private const val ATYP_IPV4 = 0x01.toByte()
    private const val ATYP_DOMAIN = 0x03.toByte()
    private const val ATYP_IPV6 = 0x04.toByte()
}

/** 12 字节小端 nonce 递增。 */
private fun ByteArray.increment() {
    for (index in indices) {
        this[index] = (this[index].toInt() + 1).toByte()
        if (this[index].toInt() != 0) return
    }
}

/**
 * Shadowsocks（AEAD）出站连接。
 *
 * 建立到 SS 服务端的 TCP 连接并完成握手，返回一条可双向读写的隧道。
 */
object ShadowsocksOutbound {

    private const val CONNECT_TIMEOUT_MS = 8_000
    private val SUBKEY_INFO = "ss-subkey".toByteArray(Charsets.US_ASCII)

    fun connect(node: ProxyNode, targetHost: String, targetPort: Int): TunnelConnection {
        val method = SsMethod.fromName(node.cipher)
            ?: throw IOException("不支持的 Shadowsocks 加密方式：${node.cipher ?: "（未填写）"}")
        val password = node.password?.takeIf { it.isNotBlank() }
            ?: throw IOException("Shadowsocks 节点缺少密码")
        val masterKey = SsCrypto.evpBytesToKey(password, method.keyLength)

        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(node.server, node.port), CONNECT_TIMEOUT_MS)
            socket.tcpNoDelay = true
            val rawOut = socket.getOutputStream()
            val salt = SsCrypto.randomBytes(method.saltLength)
            rawOut.write(salt)
            rawOut.flush()
            val writer = SsAeadOutputStream(
                rawOut,
                SsCrypto.box(method, SsCrypto.hkdfSha1(masterKey, salt, SUBKEY_INFO, method.keyLength)),
            )
            // 首个分片是目标地址（服务端据此转发）。
            writer.write(SsAddress.encode(targetHost, targetPort))

            val rawIn = socket.getInputStream()
            val incomingSalt = SsCrypto.readFully(rawIn, method.saltLength)
                ?: throw IOException("Shadowsocks 服务端未返回握手盐")
            val reader = SsAeadInputStream(
                rawIn,
                SsCrypto.box(
                    method,
                    SsCrypto.hkdfSha1(masterKey, incomingSalt, SUBKEY_INFO, method.keyLength),
                ),
            )
            return TunnelConnection(socket, reader, writer)
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw if (t is IOException) t else IOException(t.message ?: "Shadowsocks 连接失败", t)
        }
    }
}
