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
package org.fairscan.app

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.fairscan.app.data.FileLogger
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.LogRepository
import org.fairscan.app.domain.ImageSegmentationService
import org.fairscan.app.platform.AndroidImageLoader
import org.fairscan.app.ui.screens.camera.CameraViewModel
import org.fairscan.app.ui.screens.print.PrintViewModel
import org.fairscan.app.ui.screens.settings.SettingsRepository
import org.fairscan.app.ui.screens.settings.SettingsViewModel
import java.io.File

/** 扫描会话容器：由 [FairScanRoute] 在进入扫描页时创建，退出时随组合销毁。 */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val cacheDir: File = context.cacheDir
    private val dataStore = context.dataStore
    val logRepository = LogRepository(File(context.filesDir, "logs.txt"))
    val logger = FileLogger(logRepository)
    val imageSegmentationService = ImageSegmentationService(context, logger)
    val imageLoader = AndroidImageLoader(context.contentResolver)
    val settingsRepository = SettingsRepository(context, dataStore, scope)

    init {
        // LiteRT 在 Android 8.0 以下不可用（FairScan 亦要求 API 26+）：
        // 低版本机型降级为「不检测纸边」，拍照/裁剪/增强流程仍可用。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            scope.launch {
                runCatching { imageSegmentationService.initialize() }
                    .onFailure { logger.e("FairScan", "Failed to load LiteRT model", it) }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified VM : ViewModel> viewModelFactory(
        crossinline create: (AppContainer) -> VM
    ) = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return create(this@AppContainer) as T
        }
    }

    val cameraViewModelFactory = viewModelFactory { CameraViewModel(it) }
    val settingsViewModelFactory = viewModelFactory { SettingsViewModel(it) }
    fun mainViewModelFactory(imageRepository: ImageRepository) =
        viewModelFactory { MainViewModel(imageRepository, it.logger) }
    fun printViewModelFactory(imageRepository: ImageRepository) =
        viewModelFactory { PrintViewModel(it, imageRepository) }

    fun cleanOrphanSessions() {
        val sessionsRoot = sessionsRoot()
        if (!sessionsRoot.exists()) return

        val now = System.currentTimeMillis()

        sessionsRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { dir ->
                if (isOldSession(dir, now)) {
                    dir.deleteRecursively()
                }
            }
    }

    fun sessionsRoot(): File = File(cacheDir, "sessions")

    private fun isOldSession(dir: File, now: Long): Boolean {
        val lastModified = dir.lastModified()
        return now - lastModified > 24 * 60 * 60 * 1000 // 24h
    }

    companion object {
        // 分割模型加载成本高（约 5MB），整个进程复用一份容器，
        // 避免每次进出扫描页都重新初始化 LiteRT / DataStore。
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}

const val THUMBNAIL_SIZE_DP = 120

private val Context.dataStore by preferencesDataStore(name = "fairscan_settings")
