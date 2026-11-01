package com.pencil.chuangyin.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pencil.chuangyin.desktop.theme.AppTheme
import com.pencil.chuangyin.desktop.theme.DesktopThemeStore
import com.pencil.chuangyin.desktop.ui.AppShell
import com.pencil.chuangyin.desktop.ui.DesktopOnboarding
import java.util.prefs.Preferences

/**
 * 桌面版入口 —— 结构与安卓端 MainActivity 一致：
 * 主题状态是唯一真相源（DataStore ↔ DesktopThemeStore），AppTheme 下发后渲染界面。
 *
 * 首次启动先走一遍引导配置（[DesktopOnboarding]），完成标记写入注册表；
 * 之后启动直接进入「首页」tab，不做任何登录拦截（对齐安卓 startDestination = MAIN）。
 */
fun main() = application {
    val state by DesktopThemeStore.state.collectAsState()
    Window(
        onCloseRequest = ::exitApplication,
        title = "铅笔创印Pro",
        state = rememberWindowState(size = DpSize(1180.dp, 820.dp)),
    ) {
        AppTheme(state) {
            DesktopRoot(state)
        }
    }
}

@Composable
private fun DesktopRoot(state: com.pencil.chuangyin.desktop.theme.ThemeState) {
    var onboarded by remember { mutableStateOf(StartupPrefs.onboardingCompleted) }
    if (onboarded) {
        AppShell(state)
    } else {
        DesktopOnboarding(
            onFinished = {
                StartupPrefs.markOnboardingCompleted()
                onboarded = true
            },
        )
    }
}

/** 首启标记：与安卓端 pencil_print_flags 中的 onboarding_completed 等价。 */
private object StartupPrefs {
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    private val prefs: Preferences by lazy {
        Preferences.userRoot().node("com/pencil/chuangyin/pro/flags")
    }

    val onboardingCompleted: Boolean
        get() = runCatching { prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false) }.getOrDefault(false)

    fun markOnboardingCompleted() {
        runCatching {
            prefs.putBoolean(KEY_ONBOARDING_COMPLETED, true)
            prefs.flush()
        }
    }
}
