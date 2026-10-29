package com.pencil.chuangyin.core.proxy.engine

import java.net.InetSocketAddress
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * HTTP 代理认证：响应 407 时用当前配置里对应节点的账号密码重试一次。
 *
 * 只处理 407（代理认证），不处理 401（站点认证），也不会无限重试：
 * 请求头里已经有 `Proxy-Authorization` 时直接放弃。
 */
class ProxyHttpAuthenticator(private val engine: ProxyEngine) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != PROXY_AUTH_REQUIRED) return null
        if (response.request.header("Proxy-Authorization") != null) return null
        val address = route?.proxy?.address() as? InetSocketAddress ?: return null
        val credential = engine.credentialsFor(address.hostString, address.port) ?: return null
        val header = Credentials.basic(credential.username, credential.password)
        return response.request.newBuilder()
            .header("Proxy-Authorization", header)
            .build()
    }

    private companion object {
        const val PROXY_AUTH_REQUIRED = 407
    }
}
