/*
 * 文稿扫描（FairScan 开源文稿扫描器）。
 *
 * 交互：取景实时检测纸边 → 快门/导入 → 自动透视校正与增强 →
 * 编辑台切换颜色模式/旋转 → 页面列表 → 完成交给图片打印页。
 */
package com.pencil.chuangyin.feature.scanner

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fairscan.imageprocessing.ColorMode
import java.io.File
import kotlin.math.max

/** 文稿扫描入口：完成后成品经 ShotTransferBus 交给图片打印页，并回调 [onConfirmed]。 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerRoute(
    onClose: () -> Unit,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ScannerViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.completed.collect { onConfirmed() }
    }

    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var deniedEver by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(permissionState.status) {
        if (!permissionState.status.isGranted && permissionState.status.shouldShowRationale) {
            deniedEver = true
        }
    }

    Box(modifier = modifier.fillMaxSize().background(ScannerTokens.scrim)) {
        val editor = viewModel.editor
        if (permissionState.status.isGranted) {
            if (editor != null) {
                ScanEditorScreen(
                    editor = editor,
                    processing = viewModel.isProcessing,
                    onClose = viewModel::discardEditor,
                    onColorMode = viewModel::setEditorColorMode,
                    onRotate = viewModel::rotateEditor,
                    onDelete = { viewModel.deletePage(editor.page) },
                    onConfirm = viewModel::confirmEditor,
                )
            } else {
                CameraStage(viewModel = viewModel, onClose = onClose)
            }
        } else {
            PermissionView(
                showSettings = deniedEver && !permissionState.status.shouldShowRationale,
                onRequest = {
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
                .padding(16.dp),
        )
    }
}

@Composable
private fun PermissionView(
    showSettings: Boolean,
    onRequest: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var autoRequested by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoRequested && !showSettings) {
            autoRequested = true
            onRequest()
        }
    }
    Box(Modifier.fillMaxSize()) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.statusBarsPadding().padding(8.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.scanner_close), tint = ScannerTokens.content)
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = ScannerTokens.content,
                modifier = Modifier.size(48.dp),
            )
            AppText(
                text = stringResource(R.string.scanner_permission_title),
                style = MaterialTheme.typography.titleLarge,
                color = ScannerTokens.content,
            )
            AppText(
                text = stringResource(R.string.scanner_permission_message),
                style = MaterialTheme.typography.bodyMedium,
                color = ScannerTokens.contentSecondary,
                align = TextAlign.Center,
            )
            AppPrimaryButton(
                text = stringResource(
                    if (showSettings) R.string.scanner_permission_open_settings
                    else R.string.scanner_permission_grant,
                ),
                onClick = {
                    if (showSettings) {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    } else {
                        onRequest()
                    }
                },
            )
        }
    }
}

@Composable
private fun CameraStage(
    viewModel: ScannerViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val analyzer = remember { viewModel.createAnalyzer() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var flashEnabled by remember { mutableStateOf(false) }
    var cameraUnavailable by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.importFromUri(uri)
    }

    val onShutter: () -> Unit = {
        val capture = imageCapture
        if (capture != null && !viewModel.isProcessing) {
            val dir = File(context.cacheDir, ScannerViewModel.SCAN_DIR).apply { runCatching { mkdirs() } }
            val file = File(dir, "src_${System.nanoTime()}.jpg")
            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                androidx.core.content.ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                        viewModel.onCaptured(file)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        runCatching { file.delete() }
                        Toast.makeText(
                            context,
                            context.getString(R.string.scanner_capture_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        ScannerCameraPreview(
            analyzer = analyzer,
            flashEnabled = flashEnabled,
            onImageCaptureReady = { imageCapture = it },
            onCameraUnavailable = { cameraUnavailable = true },
            modifier = Modifier.fillMaxSize(),
        )

        ScannerOverlay(
            quad = viewModel.liveQuad,
            frameAspect = viewModel.liveFrameAspect,
            modifier = Modifier.fillMaxSize(),
        )

        // 顶部：关闭 + 标题（FairScan 开源文稿扫描器）+ 闪光灯。
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.scanner_close),
                    tint = ScannerTokens.content,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppText(
                    text = stringResource(R.string.scanner_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = ScannerTokens.content,
                )
                AppText(
                    text = stringResource(R.string.scanner_source_tag),
                    style = MaterialTheme.typography.labelSmall,
                    color = ScannerTokens.contentSecondary,
                )
            }
            IconButton(onClick = { flashEnabled = !flashEnabled }) {
                Icon(
                    imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = stringResource(
                        if (flashEnabled) R.string.scanner_flash_on else R.string.scanner_flash_off,
                    ),
                    tint = if (flashEnabled) ScannerTokens.accent else ScannerTokens.content,
                )
            }
        }

        if (viewModel.liveQuad == null && !viewModel.isProcessing) {
            AppText(
                text = stringResource(R.string.scanner_detect_hint),
                style = MaterialTheme.typography.bodySmall,
                color = ScannerTokens.contentSecondary,
                align = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp, start = 32.dp, end = 32.dp),
            )
        }

        // 底部：已扫描缩略图条 + 导入 / 快门 / 完成。
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (pages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pages, key = { it.id }) { page ->
                        PageThumbnail(page = page, onClick = { viewModel.openPage(page) })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !viewModel.isProcessing,
                ) {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        contentDescription = stringResource(R.string.scanner_import),
                        tint = ScannerTokens.content,
                    )
                }
                Spacer(Modifier.weight(1f))
                ShutterButton(enabled = !viewModel.isProcessing && !cameraUnavailable, onClick = onShutter)
                Spacer(Modifier.weight(1f))
                if (pages.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.scanner_done),
                        color = ScannerTokens.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !viewModel.isProcessing, onClick = viewModel::finish)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }

            if (pages.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                AppText(
                    text = stringResource(R.string.scanner_pages_count, pages.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = ScannerTokens.contentSecondary,
                )
            }
        }

        if (cameraUnavailable) {
            AppText(
                text = stringResource(R.string.scanner_camera_unavailable),
                color = ScannerTokens.content,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (viewModel.isProcessing) {
            ProcessingOverlay(text = stringResource(R.string.scanner_processing))
        }
    }
}

/** 取景叠加层：按 PreviewView FILL_CENTER 的裁剪规则映射归一化四角。 */
@Composable
private fun ScannerOverlay(
    quad: List<PointF>?,
    frameAspect: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (quad == null || quad.size != 4 || frameAspect <= 0f) return@Canvas
        val contentW = max(size.width, size.height / frameAspect)
        val contentH = contentW * frameAspect
        val offsetX = (size.width - contentW) / 2f
        val offsetY = (size.height - contentH) / 2f
        val mapped = quad.map { Offset(offsetX + it.x * contentW, offsetY + it.y * contentH) }

        val path = Path().apply {
            moveTo(mapped[0].x, mapped[0].y)
            for (index in 1..3) lineTo(mapped[index].x, mapped[index].y)
            close()
        }
        drawPath(path, ScannerTokens.accent, style = Stroke(width = 3.dp.toPx()))
        mapped.forEach { point ->
            drawCircle(ScannerTokens.accent, radius = 5.dp.toPx(), center = point)
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val ring = if (enabled) ScannerTokens.content else ScannerTokens.content.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(76.dp)
            .border(4.dp, ring, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(58.dp).background(ring, CircleShape))
    }
}

@Composable
private fun PageThumbnail(page: ScanPage, onClick: () -> Unit) {
    val bitmap by produceState<Bitmap?>(initialValue = null, page.outputFile) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(page.outputFile) }
    }
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ScannerTokens.panel)
            .border(1.dp, ScannerTokens.content.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val thumb = bitmap
        if (thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = ScannerTokens.content,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun ProcessingOverlay(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = ScannerTokens.accent)
            Spacer(Modifier.height(14.dp))
            AppText(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = ScannerTokens.content,
            )
        }
    }
}

/** 编辑台：成品预览 + 颜色模式 / 旋转 / 删除 + 确认加入打印。 */
@Composable
private fun ScanEditorScreen(
    editor: ScanEditor,
    processing: Boolean,
    onClose: () -> Unit,
    onColorMode: (ColorMode) -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colorOptions = listOf(
        stringResource(R.string.scanner_color_auto),
        stringResource(R.string.scanner_color_color),
        stringResource(R.string.scanner_color_gray),
        stringResource(R.string.scanner_color_bw),
    )
    val selectedIndex = when (editor.page.colorMode) {
        ColorMode.COLOR -> if (editor.page.autoColorMode == ColorMode.COLOR) 0 else 1
        ColorMode.GRAYSCALE -> if (editor.page.autoColorMode == ColorMode.GRAYSCALE) 0 else 2
        ColorMode.BLACK_AND_WHITE -> if (editor.page.autoColorMode == ColorMode.BLACK_AND_WHITE) 0 else 3
    }

    Box(Modifier.fillMaxSize().background(ScannerTokens.scrim)) {
        Image(
            bitmap = editor.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 64.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.scanner_retake),
                    tint = ScannerTokens.content,
                )
            }
            Spacer(Modifier.weight(1f))
            AppText(
                text = stringResource(
                    if (editor.isNew) R.string.scanner_confirm else R.string.scanner_title,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = ScannerTokens.content,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.62f),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppSegmentedRow(
                    options = colorOptions,
                    selectedIndex = selectedIndex,
                    onSelect = { index ->
                        when (index) {
                            0 -> onColorMode(editor.page.autoColorMode)
                            1 -> onColorMode(ColorMode.COLOR)
                            2 -> onColorMode(ColorMode.GRAYSCALE)
                            else -> onColorMode(ColorMode.BLACK_AND_WHITE)
                        }
                    },
                    enabled = !processing,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.scanner_rotate),
                        color = ScannerTokens.content,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !processing, onClick = onRotate)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Icon(
                        Icons.Filled.RotateRight,
                        contentDescription = null,
                        tint = ScannerTokens.content,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    if (!editor.isNew) {
                        Text(
                            text = stringResource(R.string.scanner_delete),
                            color = ScannerTokens.contentSecondary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !processing, onClick = onDelete)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = ScannerTokens.contentSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                AppPrimaryButton(
                    text = stringResource(R.string.scanner_confirm),
                    onClick = onConfirm,
                    enabled = !processing,
                )
            }
        }

        if (processing) {
            ProcessingOverlay(text = stringResource(R.string.scanner_processing))
        }
    }
}

private fun decodeThumbnail(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 72 || bounds.outHeight / (sample * 2) >= 72) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

/** 取景器固定深色场景配色（与相机页一致：预览背景不受主题控制）。 */
private object ScannerTokens {
    val scrim: Color = Color.Black
    val content: Color = Color.White
    val contentSecondary: Color = Color.White.copy(alpha = 0.72f)
    val panel: Color = Color.Black.copy(alpha = 0.42f)

    /** 纸张识别强调色（取景画面专用）。 */
    val accent: Color = Color(0xFF34C759)
}
