package com.pencil.chuangyin.core.agent.di

import javax.inject.Qualifier

/** 标记 core:agent 专用的 OkHttpClient（长连接流式读取，读超时关闭）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AgentHttpClient
