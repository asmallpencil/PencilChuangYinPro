package com.pencil.chuangyin.feature.camera.di

import com.pencil.chuangyin.feature.camera.ai.AiImageProcessor
import com.pencil.chuangyin.feature.camera.ai.DefaultAiImageProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 拍照功能的依赖绑定。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {

    /** 拍照 AI 图片处理（模型决策 + 本地工具链）；无状态，单例即可。 */
    @Binds
    @Singleton
    abstract fun bindAiImageProcessor(impl: DefaultAiImageProcessor): AiImageProcessor
}
