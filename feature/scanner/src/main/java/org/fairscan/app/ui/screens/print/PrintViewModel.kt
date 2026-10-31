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
package org.fairscan.app.ui.screens.print

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.AppContainer
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.domain.ExportQuality
import org.fairscan.app.domain.jpegsToExport
import java.io.File

/**
 * 打印页：与 FairScan 导出页相同的「成品页」，但产物不是保存/分享，
 * 而是把每一页的 JPEG 交给宿主的图片打印页（ShotTransferBus）。
 */
class PrintViewModel(
    container: AppContainer,
    private val imageRepository: ImageRepository,
) : ViewModel() {

    private val logger = container.logger
    private val preparationDir = File(container.cacheDir, "print")

    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    fun print(onReady: (List<File>) -> Unit) {
        if (_isPreparing.value) return
        _isPreparing.value = true
        _error.value = false
        viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    val jpegPages = jpegsToExport(imageRepository, ExportQuality.BALANCED)
                    val timestamp = System.currentTimeMillis()
                    preparationDir.mkdirs()
                    jpegPages.mapIndexed { index, page ->
                        val file = File(preparationDir, "$timestamp-${index + 1}.jpg")
                        file.writeBytes(page.image.get().bytes)
                        file
                    }
                }
                _isPreparing.value = false
                if (files.isEmpty()) {
                    _error.value = true
                } else {
                    onReady(files)
                }
            } catch (e: Exception) {
                logger.e("FairScan", "Failed to prepare print", e)
                _isPreparing.value = false
                _error.value = true
            }
        }
    }
}
