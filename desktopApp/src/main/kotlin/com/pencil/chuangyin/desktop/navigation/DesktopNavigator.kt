package com.pencil.chuangyin.desktop.navigation

import java.util.concurrent.atomic.AtomicReference

/**
 * 桌面端的页面导航总线（对齐安卓端 `AppNavigationBus`）。
 *
 * 安卓端由 `AppNavHost` 订阅总线执行 `navController.navigate`；桌面端是单窗口 Compose，
 * 页面由 [com.pencil.chuangyin.desktop.ui.AppShell] 里的 Pager + 整窗覆盖层表达，
 * 因此这里只保留「AI 请求打开某个页面 → 外壳响应」这一条最小通路：
 * 外壳在组合期注册回调，AI 工具调用 [navigate] 即可替用户切换页面。
 */
object DesktopNavigator {

    /** 桌面上 AI 可打开的页面（与安卓端 AppPage 对齐，去掉桌面不存在的 camera）。 */
    enum class Page(val key: String, val description: String) {
        Home("home", "首页（功能入口、最近使用）"),
        Creative("creative", "创意（模板与创作入口）"),
        PStation("pstation", "P 站（图库浏览、搜索、下载）"),
        Text("text", "文本打印编辑器"),
        Image("image", "图片打印编辑器"),
        Ai("ai", "AI 打印对话"),
        Connect("connect", "连接打印机（蓝牙配对与走纸测试）"),
        Settings("settings", "设置"),
        ;

        companion object {
            fun of(key: String): Page? = entries.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }
        }
    }

    private val handler = AtomicReference<((Page) -> Boolean)?>(null)

    /** 由外壳注册（传 null 表示解除注册）。 */
    fun register(callback: ((Page) -> Boolean)?) {
        handler.set(callback)
    }

    /** 请求切换页面；无外壳注册时返回 false。 */
    fun navigate(page: Page): Boolean = handler.get()?.invoke(page) ?: false

    /** 软件内所有可跳转的页面（供 app_list_destinations 展示）。 */
    fun pages(): List<Page> = Page.entries
}
