package com.pencil.chuangyin.core.proxy.engine

import okhttp3.OkHttpClient

/**
 * 给指定 OkHttp 客户端安装代理路由。
 *
 * 用于不能依赖全局默认选择器初始化顺序的模块（P 站、AI）：
 * ```kotlin
 * OkHttpClient.Builder().proxyEngine(engine).build()
 * ```
 */
fun OkHttpClient.Builder.proxyEngine(engine: ProxyEngine): OkHttpClient.Builder = apply {
    proxySelector(engine.selector)
    proxyAuthenticator(ProxyHttpAuthenticator(engine))
}
