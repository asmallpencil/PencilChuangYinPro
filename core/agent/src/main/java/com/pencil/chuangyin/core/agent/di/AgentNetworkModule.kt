package com.pencil.chuangyin.core.agent.di

import com.pencil.chuangyin.core.agent.AgentDefaults
import com.pencil.chuangyin.core.proxy.engine.ProxyEngine
import com.pencil.chuangyin.core.proxy.engine.proxyEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** core:agent 的网络层供给。 */
@Module
@InstallIn(SingletonComponent::class)
object AgentNetworkModule {

    /**
     * Agent 专用 HTTP 客户端。
     *
     * 读超时设为 0（不限时）：SSE 流在模型深度思考期间可能长时间无数据，
     * 一旦设置读超时会被误判为断流。
     *
     * 代理：安装 [ProxyEngine.selector]，是否真正走代理由用户的「开启 / 作用范围」决定。
     */
    @Provides
    @Singleton
    @AgentHttpClient
    fun provideAgentHttpClient(proxyEngine: ProxyEngine): OkHttpClient = OkHttpClient.Builder()
        .proxyEngine(proxyEngine)
        .connectTimeout(AgentDefaults.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(AgentDefaults.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(AgentDefaults.READ_TIMEOUT_STREAM_SECONDS, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()
}
