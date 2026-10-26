package com.pencil.chuangyin.core.ui.di

import com.pencil.chuangyin.core.ui.data.DataStoreOutputPathRepository
import com.pencil.chuangyin.core.ui.data.OutputPathRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * core:ui 依赖绑定。
 * [OutputPathRepository] 为全局「产物输出路径」接口，绑定到 DataStore 实现，
 * 上层（P 站下载、文字/图片打印导出）只依赖接口。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UiModule {

    @Binds
    abstract fun bindOutputPathRepository(impl: DataStoreOutputPathRepository): OutputPathRepository
}