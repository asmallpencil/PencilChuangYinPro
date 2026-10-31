package com.pencil.chuangyin.feature.camera.camera

import android.content.Context
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * CameraX 全屏取景预览：
 * - [PreviewView] 以 FILL_CENTER 铺满；
 * - **ViewPort 把 Preview / ImageCapture 裁到同一视场**——成片所见即所得；
 * - 进入页面即以中心为对焦点启动连续自动对焦；点按对焦 3 秒后自动解除锁定，
 *   恢复中心连续对焦——否则一次点按会把 AF 锁死在旧区域，之后全程泛焦模糊；
 * - 点按对焦区域扩大（_sizeRatio 0.35）提高抓焦成功率。
 *
 * 相机生命周期跟随当前 [LifecycleOwner]。
 */
@Composable
internal fun CameraPreview(
    onImageCaptureReady: (ImageCapture) -> Unit,
    onCameraUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose { runCatching { cameraProvider?.unbindAll() } }
    }

    LaunchedEffect(Unit) {
        runCatching {
            val provider = context.awaitCameraProvider()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                .build()

            // 等 PreviewView 完成布局：ViewPort 尺寸必须等于用户实际看到的画幅。
            awaitLayout(previewView)

            val useCaseGroup = UseCaseGroup.Builder()
                .setViewPort(
                    ViewPort.Builder(
                        android.util.Rational(previewView.width, previewView.height.coerceAtLeast(1)),
                        previewView.display?.rotation ?: imageCapture.targetRotation,
                    ).build(),
                )
                .addUseCase(preview)
                .addUseCase(imageCapture)
                .build()

            runCatching { provider.unbindAll() }
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                useCaseGroup,
            )
            boundCamera = camera

            // 初始对焦：中心大区域 AF+AE，靠 auto-cancel 回落连续对焦。
            startCenterFocus(previewView, camera)
            onImageCaptureReady(imageCapture)
        }.onFailure { onCameraUnavailable() }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val camera = boundCamera ?: return@detectTapGestures
                runCatching {
                    val point = previewView.meteringPointFactory.createPoint(tap.x, tap.y)
                    val action = FocusMeteringAction.Builder(point)
                        // 3 秒后自动解除锁定 → 恢复连续对焦，避免锁死导致泛焦。
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                    camera.cameraControl.startFocusAndMetering(action)
                }
            }
        },
    )
}

/** 中心区域连续对焦兜底：点按对焦超时/切场景后仍能合焦。 */
private fun startCenterFocus(previewView: PreviewView, camera: Camera) {
    runCatching {
        val width = previewView.width.coerceAtLeast(1).toFloat()
        val height = previewView.height.coerceAtLeast(1).toFloat()
        val point = previewView.meteringPointFactory.createPoint(width / 2f, height / 2f)
        val action = FocusMeteringAction.Builder(point)
            .addPoint(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()
        camera.cameraControl.startFocusAndMetering(action)
    }
}

private suspend fun awaitLayout(view: View) {
    if (view.width > 0 && view.height > 0) return
    suspendCoroutine { cont ->
        var resumed = false
        view.doOnNextLayout {
            if (!resumed) {
                resumed = true
                cont.resume(Unit)
            }
        }
    }
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener(
        { runCatching { cont.resume(future.get()) } },
        ContextCompat.getMainExecutor(this),
    )
}
