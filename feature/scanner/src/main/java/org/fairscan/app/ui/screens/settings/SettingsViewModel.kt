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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.fairscan.app.AppContainer

data class SettingsUiState(
    val defaultColorMode: DefaultColorMode = DefaultColorMode.AUTO,
)

class SettingsViewModel(container: AppContainer) : ViewModel() {

    private val repo = container.settingsRepository

    val uiState: StateFlow<SettingsUiState> = repo.defaultColorMode
        .map { SettingsUiState(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SettingsUiState()
        )

    fun setDefaultColorMode(pref: DefaultColorMode) {
        viewModelScope.launch {
            repo.setDefaultColorMode(pref)
        }
    }
}
