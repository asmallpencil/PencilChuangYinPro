/*
 * Copyright 2025-2026 The FairScan authors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.fairscan.app.ui.screens.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.fairscan.app.R
import org.fairscan.imageprocessing.ColorMode

/**
 * 设置仅保留「默认滤镜」。
 * 原来 FairScan 的导出目录 / 格式 / 质量 / 文件名设置已移除：成品直接进入图片打印。
 */
class SettingsRepository(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val dataStore: DataStore<Preferences>,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
) {

    private val DEFAULT_COLOR_MODE = stringPreferencesKey("default_color_mode")

    val defaultColorMode: Flow<DefaultColorMode> =
        dataStore.data.map { prefs ->
            when (prefs[DEFAULT_COLOR_MODE]) {
                "AUTO" -> DefaultColorMode.AUTO
                "COLOR" -> DefaultColorMode.COLOR
                "GRAYSCALE" -> DefaultColorMode.GRAYSCALE
                "BLACK_AND_WHITE" -> DefaultColorMode.BLACK_AND_WHITE
                else -> DefaultColorMode.AUTO
            }
        }

    suspend fun setDefaultColorMode(mode: DefaultColorMode) {
        dataStore.edit { prefs ->
            prefs[DEFAULT_COLOR_MODE] = mode.name
        }
    }
}

enum class DefaultColorMode(val colorMode: ColorMode?, val labelResource: Int) {
    AUTO(null, R.string.color_mode_auto),
    COLOR(ColorMode.COLOR, R.string.color_mode_color),
    GRAYSCALE(ColorMode.GRAYSCALE, R.string.color_mode_grayscale),
    BLACK_AND_WHITE(ColorMode.BLACK_AND_WHITE, R.string.color_mode_black_and_white),
}
