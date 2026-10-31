package com.pencil.chuangyin.feature.camera

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.feature.camera.camera.CameraBottomBar
import com.pencil.chuangyin.feature.camera.camera.CameraPreview
import com.pencil.chuangyin.feature.camera.camera.CameraTokens
import com.pencil.chuangyin.feature.camera.camera.CameraTopBar
import com.pencil.chuangyin.resources.R as ResR

/**
 * 极简拍照页：取景 → 快门 → 返回图片打印页。
 *
 * 不做任何 AI 处理、边缘检测、透视校正或滤镜——拍到的照片原样交给图片打印页，
 * 由用户在打印页里调整；需要文档校正请使用「文稿扫描」（FairScan）。
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
        if (!permissionState.status.isGranted && permissionState.status.shouldShowRationale) {
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
) {
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraUnavailable by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(CameraTokens.scrim)) {
        CameraPreview(
            onImageCaptureReady = { imageCapture = it },
            onCameraUnavailable = { cameraUnavailable = true },
            modifier = Modifier.fillMaxSize(),
        )

        CameraTopBar(
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        CameraBottomBar(
            flashAuto = viewModel.flashAuto,
            capturing = viewModel.isCapturing,
            onShutter = { imageCapture?.let(viewModel::capture) },
            onToggleFlash = { viewModel.toggleFlash(imageCapture) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (cameraUnavailable) {
            AppText(
                text = stringResource(R.string.camera_unavailable),
                color = CameraTokens.content,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
