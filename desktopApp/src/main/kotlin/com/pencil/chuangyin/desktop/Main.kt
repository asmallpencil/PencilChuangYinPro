package com.pencil.chuangyin.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pencil.chuangyin.desktop.theme.AppTheme
import com.pencil.chuangyin.desktop.theme.DesktopThemeStore
import com.pencil.chuangyin.desktop.ui.AppShell

/**
 * 桌面版入口 —— 结构与安卓端 MainActivity 一致：
 * 主题状态是唯一真相源（DataStore ↔ DesktopThemeStore），AppTheme 下发后渲染 AppShell。
 *
 * 启动即进入「首页」tab，不做任何登录拦截（对齐安卓 startDestination = MAIN）。
 */
fun main() = application {
    val state by DesktopThemeStore.state.collectAsState()
    Window(
        onCloseRequest = ::exitApplication,
        title = "铅笔创印Pro",
        state = rememberWindowState(size = DpSize(1180.dp, 820.dp)),
    ) {
        AppTheme(state) {
            AppShell(state)
        }
    }
}
