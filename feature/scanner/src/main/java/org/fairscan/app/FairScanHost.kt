/*
 * Copyright 2025-2026 The FairScan authors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.fairscan.app

import android.app.Application
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.Screen
import org.fairscan.app.ui.components.rememberCameraPermissionState
import org.fairscan.app.ui.screens.camera.CameraEvent
import org.fairscan.app.ui.screens.camera.CameraScreen
import org.fairscan.app.ui.screens.camera.CameraViewModel
import org.fairscan.app.ui.screens.crop.CropScreen
import org.fairscan.app.ui.screens.document.DocumentScreen
import org.fairscan.app.ui.screens.print.PrintScreen
import org.fairscan.app.ui.screens.print.PrintViewModel
import org.fairscan.app.ui.screens.settings.SettingsScreen
import org.fairscan.app.ui.screens.settings.SettingsViewModel
import org.fairscan.app.ui.theme.FairScanTheme
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * FairScan 原版界面的 Compose 宿主。
 *
 * 与 FairScan 原 MainActivity 的差别仅有三处：
 * 1. 设置页只剩「扫描」分组（导出/保存相关设置已移除）；
 * 2. 导出页替换为打印页，保存按钮改为「打印」；
 * 3. 打印完成把成品 JPEG 交给宿主（[onPrintFiles]），随[宿主]跳回图片打印页。
 * 其余相机、裁剪、文档编辑、纸边检测（LiteRT + OpenCV）流程保持原样。
 */
@Composable
fun FairScanHost(
    onPrintFiles: (List<File>) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val container = remember { AppContainer.get(application) }

    LaunchedEffect(container) {
        container.cleanOrphanSessions()
        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCV", "Initialization failed")
        }
    }

    val sessionViewModel: SessionViewModel = viewModel(
        factory = remember(application, container) {
            SessionViewModelFactory(application, container)
        }
    )
    val imageRepository = sessionViewModel.imageRepository

    val mainViewModel: MainViewModel = viewModel(
        factory = remember(container, imageRepository) {
            container.mainViewModelFactory(imageRepository)
        }
    )
    val cameraViewModel: CameraViewModel = viewModel(factory = container.cameraViewModelFactory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = container.settingsViewModelFactory)
    val printViewModel: PrintViewModel = viewModel(
        factory = remember(container, imageRepository) {
            container.printViewModelFactory(imageRepository)
        }
    )

    val currentScreen by mainViewModel.currentScreen.collectAsStateWithLifecycle()
    val liveAnalysisState by cameraViewModel.liveAnalysisState.collectAsStateWithLifecycle()
    val importState by cameraViewModel.importState.collectAsStateWithLifecycle()
    val document by mainViewModel.documentUiModel.collectAsStateWithLifecycle()
    val documentUiState by mainViewModel.documentUiState.collectAsStateWithLifecycle()
    val cropInitialState by mainViewModel.cropInitState.collectAsStateWithLifecycle()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val isPrinting by printViewModel.isPreparing.collectAsStateWithLifecycle()
    val printError by printViewModel.error.collectAsStateWithLifecycle()
    val cameraPermission = rememberCameraPermissionState()

    // 原 MainActivity 在 Activity 层收集拍照事件：拍照成功后把页面写入当前文档。
    LaunchedEffect(cameraViewModel, mainViewModel) {
        cameraViewModel.events.collect { event ->
            when (event) {
                is CameraEvent.ImageCaptured -> mainViewModel.handleImageCaptured(event.page)
            }
        }
    }

    val navigation = remember(mainViewModel) {
        Navigation(
            toCameraScreen = { mainViewModel.navigateTo(Screen.Main.Camera) },
            toEditImageScreen = { mainViewModel.navigateTo(Screen.Main.EditImage) },
            toDocumentScreen = { mainViewModel.navigateTo(Screen.Main.Document()) },
            toPrintScreen = { mainViewModel.navigateTo(Screen.Main.Print) },
            toSettingsScreen = { mainViewModel.navigateTo(Screen.Overlay.Settings) },
            back = { mainViewModel.navigateBack() },
            shouldDisplayBackButton = { mainViewModel.currentScreen.value !is Screen.Main.Camera },
        )
    }

    // 相机是最底层页面：系统返回直接退出扫描，回到宿主页面。
    BackHandler(enabled = currentScreen == Screen.Main.Camera) { onExit() }

    FairScanTheme {
        when (val screen = currentScreen) {
            null -> Unit
            is Screen.Main.Camera -> {
                val pickMultiple = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetMultipleContents()
                ) { uris -> cameraViewModel.importPhotos(uris) }
                CameraScreen(
                    viewModel = mainViewModel,
                    cameraViewModel = cameraViewModel,
                    navigation = navigation,
                    liveAnalysisState = liveAnalysisState,
                    importState = importState,
                    onImageAnalyzed = { image -> cameraViewModel.liveAnalysis(image) },
                    onFinalizePressed = navigation.toPrintScreen,
                    cameraPermission = cameraPermission,
                    onImportClicked = {
                        cameraViewModel.onImportClicked()
                        pickMultiple.launch("image/*")
                    },
                )
            }
            is Screen.Main.EditImage -> {
                CropScreen(
                    pageId = documentUiState.currentPage?.key?.pageId ?: "",
                    initState = cropInitialState,
                    navigation = navigation,
                    onUpdatePageQuad = { quad -> mainViewModel.setCurrentPageUserQuad(quad) },
                )
            }
            is Screen.Main.Document -> {
                DocumentScreen(
                    uiState = documentUiState,
                    navigation = navigation,
                    onExportClick = navigation.toPrintScreen,
                    onDeleteImage = { mainViewModel.deleteCurrentPage() },
                    onRotateImage = { clockwise -> mainViewModel.rotateCurrentPage(clockwise) },
                    onColorModeSelected = { mainViewModel.setCurrentPageColorMode(it) },
                    onCropClick = { mainViewModel.onClickOnCropButton() },
                    onPageReorder = { id, newIndex -> mainViewModel.movePage(id, newIndex) },
                    onPageSelected = mainViewModel::onPageSelected,
                )
            }
            is Screen.Main.Print -> {
                PrintScreen(
                    document = document,
                    isPreparing = isPrinting,
                    hasError = printError,
                    navigation = navigation,
                    onPrint = { printViewModel.print(onPrintFiles) },
                    onCloseScan = {
                        mainViewModel.startNewDocument()
                        navigation.toCameraScreen()
                    },
                )
            }
            is Screen.Overlay.Settings -> {
                SettingsScreen(
                    uiState = settingsUiState,
                    onDefaultColorModeChanged = settingsViewModel::setDefaultColorMode,
                    navigation = navigation,
                )
            }
        }
    }
}
