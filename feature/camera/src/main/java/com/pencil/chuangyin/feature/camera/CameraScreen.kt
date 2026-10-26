package com.pencil.chuangyin.feature.camera

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.pencil.chuangyin.core.scanner.ScanMode
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.feature.camera.camera.CameraBottomBar
import com.pencil.chuangyin.feature.camera.camera.CameraPreview
import com.pencil.chuangyin.feature.camera.camera.CameraTokens
import com.pencil.chuangyin.feature.camera.camera.CameraTopBar
import com.pencil.chuangyin.feature.camera.camera.ProcessingOverlay
import com.pencil.chuangyin.feature.camera.camera.ViewfinderOverlay
import com.pencil.chuangyin.feature.camera.result.AdjustScreen
import com.pencil.chuangyin.feature.camera.result.ResultScreen
import com.pencil.chuangyin.resources.R as ResR

/**
 * 拍照打印入口：
 * 完成后（结果页大勾）成品文件经 ShotTransferBus 传给图片打印页并回调 [onConfirmed]；
 * 关闭（顶栏 X）回调 [onClose] 返回来源页。
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraRoute(
    onClose: () -> Unit,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CameraViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.completed.collect { onConfirmed() }
    }

    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)
    // 记录"曾经被拒过"：勾"不再询问"后 shouldShowRationale 也为 false，
    // 需要据此区分"尚未申请"与"永久拒绝"，后者直接引导去系统设置。
    var deniedEver by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(permissionState.status) {
        if (!permissionState.status.isGranted &&
            permissionState.status.shouldShowRationale
        ) {
            deniedEver = true
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
    ) {
        if (permissionState.status.isGranted) {
            CameraContent(
                viewModel = viewModel,
                onClose = onClose,
                snackbarHostState = snackbarHostState,
            )
        } else {
            PermissionView(
                showSettings = deniedEver && !permissionState.status.shouldShowRationale,
                onRequestPermission = {
                    deniedEver = true
                    permissionState.launchPermissionRequest()
                },
                onClose = onClose,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionView(
    showSettings: Boolean,
    onRequestPermission: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var autoRequested by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoRequested && !showSettings) {
            autoRequested = true
            onRequestPermission()
        }
    }

    Box(Modifier.fillMaxSize().background(CameraTokens.scrim)) {
        CameraTopBar(onClose = onClose, modifier = Modifier.align(Alignment.TopCenter))
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_l)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = CameraTokens.content,
                modifier = Modifier.size(
                    dimensionResource(ResR.dimen.dimen_camera_permission_icon),
                ),
            )
            AppText(
                text = stringResource(R.string.camera_permission_title),
                style = MaterialTheme.typography.titleLarge,
                color = CameraTokens.content,
            )
            AppText(
                text = stringResource(R.string.camera_permission_message),
                style = MaterialTheme.typography.bodyMedium,
                color = CameraTokens.contentSecondary,
                align = androidx.compose.ui.text.style.TextAlign.Center,
            )
            AppPrimaryButton(
                text = stringResource(
                    if (showSettings) R.string.camera_permission_open_settings
                    else R.string.camera_permission_grant,
                ),
                onClick = {
                    if (showSettings) {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    } else {
                        onRequestPermission()
                    }
                },
                modifier = Modifier.padding(
                    top = dimensionResource(ResR.dimen.dimen_global_padding_s),
                ),
            )
        }
    }
}

@Composable
private fun CameraContent(
    viewModel: CameraViewModel,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val analyzer = remember { viewModel.createAnalyzer() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraUnavailable by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.importFromUri(uri)
    }

    val phase = viewModel.phase
    Box(Modifier.fillMaxSize().background(CameraTokens.scrim)) {
        when (phase) {
            is CameraPhase.Adjust -> {
                // 角点调整：全屏原图 + 可拖四角，确认后透视拉直。
                AdjustScreen(viewModel = viewModel, onRetake = {})
            }
            is CameraPhase.Result -> {
                ResultScreen(viewModel = viewModel, onRetake = {})
            }
            else -> {
                CameraPreview(
                    analyzer = analyzer,
                    onImageCaptureReady = { imageCapture = it },
                    onCameraUnavailable = { cameraUnavailable = true },
                    modifier = Modifier.fillMaxSize(),
                )

                ViewfinderOverlay(
                    quad = if (viewModel.mode.showLiveQuad) viewModel.liveQuad else null,
                    modifier = Modifier.fillMaxSize(),
                )

                CameraTopBar(
                    onClose = onClose,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                CameraBottomBar(
                    mode = viewModel.mode,
                    flashAuto = viewModel.flashAuto,
                    onSelectMode = viewModel::selectMode,
                    onShutter = { imageCapture?.let(viewModel::capture) },
                    onPickGallery = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onToggleFlash = { viewModel.toggleFlash(imageCapture) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                if (phase is CameraPhase.Processing) {
                    ProcessingOverlay(stage = phase.stage)
                }

                if (cameraUnavailable) {
                    AppText(
                        text = stringResource(R.string.camera_unavailable),
                        color = CameraTokens.content,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}
