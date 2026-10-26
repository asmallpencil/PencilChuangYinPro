package com.pencil.chuangyin.desktop.support

import java.util.prefs.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 热敏纸宽度 —— 对齐安卓端 `core/printer/PrinterSettings.kt` 的取值范围：
 * 1–58 mm，默认 58 mm。桌面端无打印通道，仅保存该设置值，
 * 使「尺寸设置」页与安卓端信息架构、文案与取值完全一致。
 */
object PaperWidthStore {

    /** 对齐 PrinterSettings.PAPER_WIDTH_MM_MIN。 */
    const val MIN_MM = 1

    /** 对齐 PrinterSettings.PAPER_WIDTH_MM_MAX。 */
    const val MAX_MM = 58

    /** 对齐 PrinterSettings.PAPER_WIDTH_MM_DEFAULT。 */
    const val DEFAULT_MM = MAX_MM

    private const val KEY_WIDTH_MM = "paper_width_mm"

    private val prefs: Preferences by lazy {
        Preferences.userRoot().node("com/pencil/chuangyin/pro/printer")
    }

    private val _widthMm = MutableStateFlow(
        prefs.getInt(KEY_WIDTH_MM, DEFAULT_MM).coerceIn(MIN_MM, MAX_MM)
    )
    val widthMm: StateFlow<Int> = _widthMm.asStateFlow()

    fun setWidthMm(value: Int) {
        val safe = value.coerceIn(MIN_MM, MAX_MM)
        _widthMm.value = safe
        runCatching {
            prefs.putInt(KEY_WIDTH_MM, safe)
            prefs.flush()
        }
    }
}
