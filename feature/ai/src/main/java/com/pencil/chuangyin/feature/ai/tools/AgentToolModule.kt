package com.pencil.chuangyin.feature.ai.tools

import com.google.adk.kt.tools.FunctionTool
import com.pencil.chuangyin.core.agent.adk.AdkAgentTool
import com.pencil.chuangyin.core.agent.model.AgentTool
import com.pencil.chuangyin.core.agent.runtime.MutableToolCallScope
import com.pencil.chuangyin.core.agent.runtime.ToolConfirmationGate
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet

/**
 * 把 feature:ai 的工具注册进 core:agent 的 [AgentTool] 集合。
 *
 * 工具集类中的函数用官方 Google ADK for Kotlin 的 @Tool/@Param 注解声明，由官方
 * google-adk-kotlin-processor 在编译期生成 `generatedTools()` 扩展（返回官方 [FunctionTool]），
 * 这里统一经 [AdkAgentTool] 包装为项目工具后整体并入多绑定；
 * 新增工具时在工具集类里加一个注解函数即可，无需改本文件。
 */
@Module
@InstallIn(SingletonComponent::class)
object GeneratedAgentToolsModule {

    /**
     * 工具权限由 [ToolPermissions] 按工具名映射：READ（只读，免确认）/ WORKSPACE（写工作区）
     * / DANGEROUS（终端、打印、删除等），引擎结合用户当前的权限模式决定是否拦截首次调用。
     */
    private fun Collection<FunctionTool>.asAgentTools(scope: MutableToolCallScope): Set<AgentTool> =
        map { AdkAgentTool(it, scope, permission = ToolPermissions.of(it.name)) }.toSet()

    @Provides
    @ElementsIntoSet
    fun fileTools(impl: FileTools, scope: MutableToolCallScope): Set<AgentTool> = impl.generatedTools().asAgentTools(scope)

    @Provides
    @ElementsIntoSet
    fun advancedFileTools(impl: AdvancedFileTools, scope: MutableToolCallScope): Set<AgentTool> =
        impl.generatedTools().asAgentTools(scope)

    @Provides
    @ElementsIntoSet
    fun archiveTools(impl: ArchiveTools, scope: MutableToolCallScope): Set<AgentTool> = impl.generatedTools().asAgentTools(scope)

    @Provides
    @ElementsIntoSet
    fun imageTools(impl: ImageTools, scope: MutableToolCallScope): Set<AgentTool> = impl.generatedTools().asAgentTools(scope)

    @Provides
    @ElementsIntoSet
    fun videoTools(impl: VideoTools, scope: MutableToolCallScope): Set<AgentTool> = impl.generatedTools().asAgentTools(scope)

    @Provides
    @ElementsIntoSet
    fun tableTools(impl: TableTools, scope: MutableToolCallScope): Set<AgentTool> = impl.generatedTools().asAgentTools(scope)

    @Provides
    @ElementsIntoSet
    fun printTools(impl: PrintTools, scope: MutableToolCallScope): Set<AgentTool> = impl.generatedTools().asAgentTools(scope)

    @Provides
    @ElementsIntoSet
    fun drawTools(impl: DrawTools, scope: MutableToolCallScope): Set<AgentTool> = impl.generatedTools().asAgentTools(scope)

    @Provides
    @ElementsIntoSet
    fun appTools(impl: AppTools, scope: MutableToolCallScope): Set<AgentTool> = impl.generatedTools().asAgentTools(scope)

    @Provides
    @ElementsIntoSet
    fun webTools(impl: WebTools, scope: MutableToolCallScope): Set<AgentTool> = impl.generatedTools().asAgentTools(scope)

    @Provides
    @ElementsIntoSet
    fun shellTools(impl: ShellTools, scope: MutableToolCallScope): Set<AgentTool> = impl.generatedTools().asAgentTools(scope)
}

/** 会话级确认门：拦截 requireConfirmation 工具的首次调用，直至用户回复同意。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentConfirmationModule {

    @Binds
    @IntoSet
    abstract fun bindSessionConfirmationGate(impl: SessionConfirmationGate): ToolConfirmationGate
}
