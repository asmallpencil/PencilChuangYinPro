package com.pencil.chuangyin.feature.text.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** feature:text 的字体持久化绑定：把 [FontStorage] 绑定到 [DefaultFontStorage]。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FontStorageModule {

    @Binds
    abstract fun bindFontStorage(impl: DefaultFontStorage): FontStorage
}