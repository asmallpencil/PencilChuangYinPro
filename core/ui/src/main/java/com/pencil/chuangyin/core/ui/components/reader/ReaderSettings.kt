package com.pencil.chuangyin.core.ui.components.reader

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阅读器排版偏好（全局共享，本地持久化）。
 *
 * 归属 core:ui：P 站本地小说、P 站在线小说、AI 打印的文本预览共用同一套阅读器，
 * 用户在任何入口调过的字号 / 配色，下次在其它入口打开时保持一致。
 */
data class ReaderSettings(
    /** 正文字号（sp）；决定每页可容纳的字数。 */
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    /** 正文颜色（ARGB）。 */
    val textColorArgb: Int = 0xFF23201C.toInt(),
    /** 页面底色（ARGB）。 */
    val backgroundArgb: Int = 0xFFF7F2E8.toInt(),
    /** 整体亮度（0.35~1.0）：以暗色叠层实现，不改动系统亮度。 */
    val brightness: Float = 1f,
) {
    companion object {
        const val MIN_FONT_SIZE_SP = 12f
        const val MAX_FONT_SIZE_SP = 30f
        const val DEFAULT_FONT_SIZE_SP = 17f
        const val MIN_BRIGHTNESS = 0.35f
        const val MAX_BRIGHTNESS = 1f
    }
}

/** 阅读模式：一条式文本查看器 / 分页小说阅读器。 */
enum class ReaderMode { TEXT, NOVEL }

private val Context.readerDataStore by preferencesDataStore(name = "reader_settings")

/**
 * 阅读偏好的持久化仓库。
 *
 * 读取失败（首次启动、文件损坏）一律回退到 [ReaderSettings] 默认值，
 * 不向调用方抛异常：阅读器属于高频入口，偏好损坏不应阻断阅读。
 */
@Singleton
class ReaderSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyFontSize = floatPreferencesKey("font_size_sp")
    private val keyTextColor = intPreferencesKey("text_color_argb")
    private val keyBackground = intPreferencesKey("background_argb")
    private val keyBrightness = floatPreferencesKey("brightness")

    val settings: Flow<ReaderSettings> = context.readerDataStore.data
        .map { prefs ->
            ReaderSettings(
                fontSizeSp = (prefs[keyFontSize] ?: ReaderSettings.DEFAULT_FONT_SIZE_SP)
                    .coerceIn(ReaderSettings.MIN_FONT_SIZE_SP, ReaderSettings.MAX_FONT_SIZE_SP),
                textColorArgb = prefs[keyTextColor] ?: ReaderSettings().textColorArgb,
                backgroundArgb = prefs[keyBackground] ?: ReaderSettings().backgroundArgb,
                brightness = (prefs[keyBrightness] ?: 1f)
                    .coerceIn(ReaderSettings.MIN_BRIGHTNESS, ReaderSettings.MAX_BRIGHTNESS),
            )
        }

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        context.readerDataStore.edit { prefs ->
            val current = ReaderSettings(
                fontSizeSp = prefs[keyFontSize] ?: ReaderSettings.DEFAULT_FONT_SIZE_SP,
                textColorArgb = prefs[keyTextColor] ?: ReaderSettings().textColorArgb,
                backgroundArgb = prefs[keyBackground] ?: ReaderSettings().backgroundArgb,
                brightness = prefs[keyBrightness] ?: 1f,
            )
            val next = transform(current)
            prefs[keyFontSize] = next.fontSizeSp.coerceIn(
                ReaderSettings.MIN_FONT_SIZE_SP,
                ReaderSettings.MAX_FONT_SIZE_SP,
            )
            prefs[keyTextColor] = next.textColorArgb
            prefs[keyBackground] = next.backgroundArgb
            prefs[keyBrightness] = next.brightness.coerceIn(
                ReaderSettings.MIN_BRIGHTNESS,
                ReaderSettings.MAX_BRIGHTNESS,
            )
        }
    }
}

/** 阅读器在 core:ui 内部自取偏好仓库，调用方无需了解 DI 细节。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ReaderSettingsEntryPoint {
    fun store(): ReaderSettingsStore
}

internal fun readerSettingsStore(context: Context): ReaderSettingsStore =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        ReaderSettingsEntryPoint::class.java,
    ).store()
