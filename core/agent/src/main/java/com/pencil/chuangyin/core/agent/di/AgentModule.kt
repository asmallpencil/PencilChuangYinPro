package com.pencil.chuangyin.core.agent.di

import com.pencil.chuangyin.core.agent.aiimage.AiImageSettingsRepository
import com.pencil.chuangyin.core.agent.aiimage.DataStoreAiImageSettingsRepository
import com.pencil.chuangyin.core.agent.config.AiConfigRepository
import com.pencil.chuangyin.core.agent.config.DataStoreAiConfigRepository
import com.pencil.chuangyin.core.agent.model.AgentTool
import com.pencil.chuangyin.core.agent.openai.OpenAiChatClient
import com.pencil.chuangyin.core.agent.provider.ProtocolChatClient
import com.pencil.chuangyin.core.agent.runtime.AgentPermissionModeProvider
import com.pencil.chuangyin.core.agent.runtime.MutableAgentPermissionModeProvider
import com.pencil.chuangyin.core.agent.runtime.MutableToolCallScope
import com.pencil.chuangyin.core.agent.runtime.ToolCallScope
import com.pencil.chuangyin.core.agent.runtime.ToolConfirmationGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/** core:agent 的依赖绑定。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    @Binds
    @Singleton
    abstract fun bindAiConfigRepository(impl: DataStoreAiConfigRepository): AiConfigRepository

    /** 拍照 AI 处理专属设置（与模型配置分离：模型接口共用，管线参数独立）。 */
    @Binds
    @Singleton
    abstract fun bindAiImageSettingsRepository(
        impl: DataStoreAiImageSettingsRepository,
    ): AiImageSettingsRepository

    /** 按模型配置的协议分派到 OpenAI / Anthropic / Gemini 实现。 */
    @Binds
    @Singleton
    abstract fun bindOpenAiChatClient(impl: ProtocolChatClient): OpenAiChatClient

    /** 官方 ADK FunctionTool 适配层向工具函数体桥接项目 ToolContext。 */
    @Binds
    abstract fun bindToolCallScope(impl: MutableToolCallScope): ToolCallScope

    /** 权限模式来源：宿主（feature:ai）注入具体实现写入，引擎按最新模式实时判定。 */
    @Binds
    @Singleton
    abstract fun bindPermissionModeProvider(impl: MutableAgentPermissionModeProvider): AgentPermissionModeProvider

    /** 声明工具集合可空，避免无工具注册时注入失败。 */
    @Multibinds
    abstract fun bindAgentTools(): Set<AgentTool>

    /** 声明确认门集合可空：无门时 requireConfirmation 工具直接放行。 */
    @Multibinds
    abstract fun bindConfirmationGates(): Set<ToolConfirmationGate>
}
