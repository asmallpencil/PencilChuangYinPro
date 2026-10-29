package com.pencil.chuangyin.core.proxy.di

import com.pencil.chuangyin.core.proxy.data.DataStoreProxySettingsRepository
import com.pencil.chuangyin.core.proxy.data.ProxySettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** core:proxy 的 Hilt 绑定。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProxyModule {

    @Binds
    @Singleton
    abstract fun bindProxySettingsRepository(
        impl: DataStoreProxySettingsRepository,
    ): ProxySettingsRepository
}
