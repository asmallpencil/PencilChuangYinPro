package com.pencil.chuangyin.feature.pixiv.tools

import com.pencil.chuangyin.core.agent.model.AgentTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 把 feature:pixiv 的 6 个 P 站工具注册进 core:agent 的 [AgentTool] 集合。
 *
 * core:agent 以 `@Multibinds` 声明该集合，任一 feature 模块都能追加实现；
 * 因此 P 站能力可被 AI 直接调用，且不引入 feature:pixiv → feature:ai 的反向依赖。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PixivAgentToolModule {

    @Binds
    @IntoSet
    abstract fun bindPixivHomeFeedTool(impl: PixivHomeFeedTool): AgentTool

    @Binds
    @IntoSet
    abstract fun bindPixivSearchIllustTool(impl: PixivSearchIllustTool): AgentTool

    @Binds
    @IntoSet
    abstract fun bindPixivIllustDetailTool(impl: PixivIllustDetailTool): AgentTool

    @Binds
    @IntoSet
    abstract fun bindPixivDownloadIllustTool(impl: PixivDownloadIllustTool): AgentTool

    @Binds
    @IntoSet
    abstract fun bindPixivSearchNovelTool(impl: PixivSearchNovelTool): AgentTool

    @Binds
    @IntoSet
    abstract fun bindPixivDownloadNovelTool(impl: PixivDownloadNovelTool): AgentTool
}
