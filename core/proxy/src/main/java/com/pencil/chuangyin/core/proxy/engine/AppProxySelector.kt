package com.pencil.chuangyin.core.proxy.engine

import java.io.IOException
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.Proxy

/**
 * 全局 [ProxySelector]：把 URI 的 host 交给 [ProxyEngine] 解析。
 *
 * 安装方式有两种，二者都只影响本进程：
 * 1. `ProxySelector.setDefault(engine.selector)` —— 覆盖所有使用默认选择器的
 *    OkHttp / HttpURLConnection；
 * 2. `ProxyEngine.install(builder)` —— 对关键客户端（P 站、AI）显式安装，
 *    不依赖初始化顺序。
 */
class AppProxySelector(private val engine: ProxyEngine) : ProxySelector() {

    override fun select(uri: URI?): List<Proxy> {
        val host = uri?.host?.takeIf { it.isNotBlank() } ?: return listOf(Proxy.NO_PROXY)
        val port = uri.port.takeIf { it in 1..65535 }
        return engine.proxyFor(host, port)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        engine.onConnectFailed(uri?.host, ioe)
    }
}
