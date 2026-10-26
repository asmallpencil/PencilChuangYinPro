package com.pencil.chuangyin.core.ui.navigation

import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 应用内可被直接跳转的顶级页面（横向 Pager 的三页 + 覆盖式 push 页）。
 *
 * 顶级页由 Pager 承载，push 页由路由承载；两者对调用方（含 AI 工具）是统一概念。
 */
enum class AppPage(val key: String, val description: String) {
    HOME("home", "首页"),
    CREATIVE("creative", "创作页（文本 / 图片 / 相机入口）"),
    PSTATION("pstation", "P 站主页（推荐 / 搜索 / 排行榜）"),
    TEXT("text", "文本编辑与打印页"),
    IMAGE("image", "图片编辑与打印页"),
    CAMERA("camera", "相机拍照页"),
    SETTINGS("settings", "应用设置页"),
    AI("ai", "AI 助手对话页"),
}

/** 统一的导航指令：任何模块投递，由 [AppNavHost] 消费执行。 */
sealed interface AppNavCommand {

    /** 切换到某个顶级页面（Pager 页面或 push 页）。 */
    data class OpenPage(val page: AppPage) : AppNavCommand

    /** 按具体路由字符串跳转（用于带参数的 P 站详情 / 搜索等）。 */
    data class Navigate(val route: String) : AppNavCommand

    /** 返回上一页。 */
    data object Back : AppNavCommand
}

/**
 * 一个可被具名打开的目的地。
 *
 * [buildRoute] 把调用方提供的参数映射为最终路由字符串，避免 core:ui 依赖 app 层的路由常量。
 */
data class AppDestination(
    val key: String,
    val description: String,
    val buildRoute: (Map<String, String>) -> String = { "" },
)

/**
 * 全局导航总线。
 *
 * 作为进程级单例存在：任何模块（包括运行在后台的 AI 工具）都能投递导航指令；
 * [AppNavHost] 收集 [commands] 并翻译为 NavController / Pager 操作，因此 AI 可以「替用户点击」。
 */
@Singleton
class AppNavigationBus @Inject constructor() {

    // 缓冲 32 条并丢弃最旧：导航指令是「最新意图优先」的语义，迟到指令无意义。
    private val _commands = MutableSharedFlow<AppNavCommand>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 供宿主收集的指令流。 */
    val commands: SharedFlow<AppNavCommand> = _commands.asSharedFlow()

    private val registry = CopyOnWriteArrayList<AppDestination>()

    /** 登记（或覆盖）一个具名目的地，通常在应用启动时批量调用。 */
    fun registerDestination(destination: AppDestination) {
        registry.removeAll { it.key == destination.key }
        registry.add(destination)
    }

    /** 当前已登记的目的地快照，供 AI 查询可用能力。 */
    fun registeredDestinations(): List<AppDestination> = registry.toList()

    /** 按 key 查找目的地。 */
    fun findDestination(key: String): AppDestination? = registry.firstOrNull { it.key == key }

    /** 投递一条指令。 */
    fun send(command: AppNavCommand) {
        _commands.tryEmit(command)
    }

    /** 切换到某个顶级页面。 */
    fun openPage(page: AppPage) = send(AppNavCommand.OpenPage(page))

    /** 按具体路由跳转。 */
    fun navigate(route: String) = send(AppNavCommand.Navigate(route))

    /** 返回上一页。 */
    fun back() = send(AppNavCommand.Back)

    /** 按具名目的地 + 参数跳转；目的地不存在时返回 false。 */
    fun openDestination(key: String, args: Map<String, String> = emptyMap()): Boolean {
        val destination = findDestination(key) ?: return false
        send(AppNavCommand.Navigate(destination.buildRoute(args)))
        return true
    }
}
