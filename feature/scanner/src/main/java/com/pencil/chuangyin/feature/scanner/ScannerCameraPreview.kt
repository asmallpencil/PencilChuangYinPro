package com.pencil.chuangyin.feature.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.os.SystemClock
import android.util.Size
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 取景器实时纸张检测：降采样帧转正 → FairScan 检测 → 归一化四角。
 * 500ms 限频，预览流畅且不积压帧。
 */
internal class LiveDocumentAnalyzer(
    private val engine: FairScanEngine,
    private val onDetection: (List<PointF>?, Float) -> Unit,
) : ImageAnalysis.Analyzer {

    @Volatile
    private var lastRunMs = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRunMs < INTERVAL_MS) return
            lastRunMs = now

            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val oriented = if (rotation != 0) rotate(raw, rotation) else raw
            if (oriented !== raw) runCatching { raw.recycle() }

            val detection = engine.detect(oriented)
            val aspect = oriented.height.toFloat() / oriented.width.toFloat()
            val normalized = detection?.normalized()
            runCatching { oriented.recycle() }
            onDetection(normalized, aspect)
        } catch (_: Throwable) {
            // 单帧失败不影响后续帧。
        } finally {
            runCatching { image.close() }
        }
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private companion object {
        const val INTERVAL_MS = 500L
    }
}

/**
 * CameraX 全屏取景（FairScan 扫描页专用）：
 * Preview / ImageCapture / ImageAnalysis 共用同一 ViewPort，所见即所得；
 * 点按对焦 3 秒后自动回落连续对焦。
 */
@Composable
internal fun ScannerCameraPreview(
    analyzer: ImageAnalysis.Analyzer,
    flashEnabled: Boolean,
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
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            runCatching { analysisExecutor.shutdown() }
        }
    }

    LaunchedEffect(analyzer) {
        runCatching {
            val provider = context.awaitCameraProvider()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            imageCapture = capture
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(Size(1280, 720))
                .build()
                .also { it.setAnalyzer(analysisExecutor, analyzer) }

            awaitLayout(previewView)

            val group = UseCaseGroup.Builder()
                .setViewPort(
                    ViewPort.Builder(
                        android.util.Rational(previewView.width, previewView.height.coerceAtLeast(1)),
                        previewView.display?.rotation ?: capture.targetRotation,
                    ).build(),
                )
                .addUseCase(preview)
                .addUseCase(capture)
                .addUseCase(analysis)
                .build()

            runCatching { provider.unbindAll() }
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                group,
            )
            boundCamera = camera
            startCenterFocus(previewView, camera)
            onImageCaptureReady(capture)
        }.onFailure { onCameraUnavailable() }
    }

    LaunchedEffect(imageCapture, flashEnabled) {
        imageCapture?.flashMode =
            if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val camera = boundCamera ?: return@detectTapGestures
                runCatching {
                    val point = previewView.meteringPointFactory.createPoint(tap.x, tap.y)
                    val action = FocusMeteringAction.Builder(point)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                    camera.cameraControl.startFocusAndMetering(action)
                }
            }
        },
    )
}

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

private suspend fun android.content.Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            { runCatching { cont.resume(future.get()) } },
            ContextCompat.getMainExecutor(this),
        )
    }
