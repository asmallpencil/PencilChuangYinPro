package com.pencil.chuangyin.core.proxy.transport

import com.pencil.chuangyin.core.proxy.model.ProxyNode
import com.pencil.chuangyin.core.proxy.model.ProxyProtocol
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * 出站传输层。
 *
 * 路由器只负责「选哪个出口」，真正建立连接由实现类完成。新增协议（如 Shadowsocks）
 * 时只需实现本接口并注册到 [OutboundTransportRegistry]，路由与界面无需改动。
 */
interface OutboundTransport {

    val protocols: Set<ProxyProtocol>

    /** 生成 java.net.Proxy；返回 null 表示该节点无法由本传输层承载。 */
    fun createProxy(node: ProxyNode): Proxy?
}

/** HTTP(S) 代理（Chrome/Clash 的 `type: http`）。 */
object HttpOutboundTransport : OutboundTransport {

    override val protocols: Set<ProxyProtocol> = setOf(ProxyProtocol.Http)

    override fun createProxy(node: ProxyNode): Proxy? {
        if (!node.isSupported) return null
        val address = runCatching { InetSocketAddress(node.server, node.port) }.getOrNull() ?: return null
        if (address.isUnresolved) return null
        return Proxy(Proxy.Type.HTTP, address)
    }
}

/** SOCKS5 代理，支持用户名/密码认证（由 ProxyEngine 提供的 java.net.Authenticator 响应）。 */
object Socks5OutboundTransport : OutboundTransport {

    override val protocols: Set<ProxyProtocol> = setOf(ProxyProtocol.Socks5)

    override fun createProxy(node: ProxyNode): Proxy? {
        if (!node.isSupported) return null
        val address = runCatching { InetSocketAddress(node.server, node.port) }.getOrNull() ?: return null
        if (address.isUnresolved) return null
        return Proxy(Proxy.Type.SOCKS, address)
    }
}

/** 传输层注册表：协议 → 实现。 */
class OutboundTransportRegistry(
    transports: List<OutboundTransport> = listOf(HttpOutboundTransport, Socks5OutboundTransport),
) {

    private val byProtocol: Map<ProxyProtocol, OutboundTransport> =
        transports.flatMap { transport -> transport.protocols.map { it to transport } }.toMap()

    fun transportFor(protocol: ProxyProtocol): OutboundTransport? = byProtocol[protocol]

    fun createProxy(node: ProxyNode): Proxy? = byProtocol[node.protocol]?.createProxy(node)
}
