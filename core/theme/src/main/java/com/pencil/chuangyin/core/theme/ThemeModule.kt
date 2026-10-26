package com.pencil.chuangyin.core.theme

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * core:theme 的依赖绑定：将接口绑定到本层实现。
 * Feature 层只依赖接口（ColorSchemeProvider/ShapeResolver/MotionResolver/ThemeRepository），
 * 实现替换无需改任何调用方。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeModule {

    @Binds
    abstract fun bindThemeRepository(impl: DataStoreThemeRepository): ThemeRepository

    @Binds
    abstract fun bindColorSchemeProvider(impl: MaterialKolorColorSchemeProvider): ColorSchemeProvider

    @Binds
    abstract fun bindShapeResolver(impl: DimenShapeResolver): ShapeResolver
}