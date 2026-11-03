package com.pencil.chuangyin.feature.barcode.scan

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pencil.chuangyin.core.barcode.BarcodeFormatOption
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.feature.barcode.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 通用扫描器：CameraX 实时取景 → 解码 → 内容查看页。 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScanRoute(
    allowFillBarcode: Boolean,
    onClose: () -> Unit,
    onOpenTextPrint: () -> Unit,
    onFillBarcode: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BarcodeScanViewModel = hiltViewModel(),
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 识别结果的轻震动：只在首次命中时触发，避免连续帧反复震动。
    var lastHapticAt by remember { mutableStateOf(0L) }

    BackHandler {
        if (viewModel.result != null) viewModel.rescan() else onClose()
    }

    // 全黑取景页需要浅色系统栏图标，退出时恢复，避免浅色主题下状态栏看不见。
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = previousStatus ?: true
            controller?.isAppearanceLightNavigationBars = previousNav ?: true
        }
    }

    LaunchedEffect(viewModel.navigateToTextPrint) {
        if (viewModel.navigateToTextPrint) {
            viewModel.consumeTextPrintNavigation()
            onOpenTextPrint()
        }
    }

    LaunchedEffect(viewModel.notice) {
        val notice = viewModel.notice ?: return@LaunchedEffect
        val text = when (notice) {
            ScanNotice.Copied -> context.getString(R.string.scan_copied)
            ScanNotice.CopyPartFailed -> context.getString(R.string.scan_copy_part_failed)
            ScanNotice.Saved -> context.getString(R.string.scan_saved_ok)
            ScanNotice.SaveFailed -> context.getString(R.string.scan_save_failed)
            ScanNotice.NoUrl -> context.getString(R.string.scan_no_url)
            ScanNotice.OpenFailed -> context.getString(R.string.scan_open_failed)
            ScanNotice.AlbumMissed -> context.getString(R.string.scan_album_failed)
            ScanNotice.CameraUnavailable -> context.getString(R.string.scan_camera_unavailable)
        }
        viewModel.consumeNotice()
        snackbarHostState.showSnackbar(text)
    }

    val albumLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.decodeAlbum(uri)
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (cameraPermission.status.isGranted) {
            CameraStage(
                torchOn = viewModel.torchOn,
                paused = viewModel.result != null,
                mode = viewModel.mode,
                onDecoded = { text, format ->
                    val accepted = viewModel.onDecoded(text, format)
                    if (accepted) {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastHapticAt > 400) {
                            lastHapticAt = now
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                },
                onCameraError = { viewModel.markCameraUnavailable(true) },
            )
            ScanOverlay(
                mode = viewModel.mode,
                cameraUnavailable = viewModel.cameraUnavailable,
                torchOn = viewModel.torchOn,
                onClose = onClose,
                onToggleTorch = viewModel::toggleTorch,
                onPickAlbum = {
                    albumLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onOpenSaved = viewModel::openSavedSheet,
                onModeChange = viewModel::selectMode,
            )
        } else {
            PermissionPanel(
                onRequest = {
                    if (Build.VERSION.SDK_INT >= 23) cameraPermission.launchPermissionRequest()
                },
                onPickAlbum = {
                    albumLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onClose = onClose,
            )
        }

        AnimatedVisibility(
            visible = viewModel.result != null,
            enter = slideInVertically(animationSpec = tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(animationSpec = tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(160)),
        ) {
            val result = viewModel.result
            if (result != null) {
                ScanResultPage(
                    result = result,
                    alreadySaved = viewModel.currentSaved,
                    allowFillBarcode = allowFillBarcode,
                    onRescan = viewModel::rescan,
                    onCopyAll = viewModel::copyAll,
                    onSelectCopy = viewModel::toggleSelectCopy,
                    onSave = viewModel::saveCurrent,
                    onTextPrint = viewModel::requestTextPrint,
                    onFillBarcode = {
                        viewModel.fillBarcode()
                        onFillBarcode()
                    },
                    onOpenUrl = { url -> viewModel.openUrl(url) },
                    onNoUrl = viewModel::reportNoUrl,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 108.dp, start = 20.dp, end = 20.dp),
        )

        if (viewModel.savedSheetOpen) {
            SavedSheet(
                items = viewModel.saved,
                onDismiss = viewModel::closeSavedSheet,
                onCopy = { viewModel.copyPart(it) },
                onDelete = viewModel::deleteSaved,
            )
        }

        if (viewModel.selectCopyOpen) {
            SelectCopySheet(
                result = viewModel.result,
                onDismiss = viewModel::closeSelectCopy,
                onCopyPart = viewModel::copyPart,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                                 相机与取景                                  */
/* -------------------------------------------------------------------------- */

/**
 * 相机取景：预览 + ImageAnalysis。识别分析在单线程执行器上，结果页打开时暂停分析
 * （仍保留预览，返回继续扫描可立即恢复，无需重新打开相机）。
 */
@Composable
private fun CameraStage(
    torchOn: Boolean,
    paused: Boolean,
    mode: ScanMode,
    onDecoded: (String, String) -> Unit,
    onCameraError: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }

    val currentMode by rememberUpdatedState(mode)
    val currentPaused by rememberUpdatedState(paused)
    val currentOnDecoded by rememberUpdatedState(onDecoded)
    val currentOnError by rememberUpdatedState(onCameraError)

    val analyzer = remember {
        BarcodeLiveAnalyzer(
            modeProvider = { currentMode },
            enabledProvider = { !currentPaused },
            onDecoded = { text, format -> currentOnDecoded(text, format) },
        )
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        val listener = Runnable {
            if (disposed) return@Runnable
            val cameraProvider = runCatching { future.get() }.getOrNull()
            if (cameraProvider == null) {
                currentOnError()
                return@Runnable
            }
            provider = cameraProvider
            runCatching {
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }.onFailure {
                currentOnError()
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            runCatching { provider?.unbindAll() }
            camera = null
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(torchOn, camera) {
        runCatching { camera?.cameraControl?.enableTorch(torchOn) }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * 取景叠层：暗场蒙版 + 扫码框 + 扫描线动画 + 顶部工具 + 底部模式导航。
 * 中间无快门按钮，命中即自动进入结果页。
 */
@Composable
private fun ScanOverlay(
    mode: ScanMode,
    cameraUnavailable: Boolean,
    torchOn: Boolean,
    onClose: () -> Unit,
    onToggleTorch: () -> Unit,
    onPickAlbum: () -> Unit,
    onOpenSaved: () -> Unit,
    onModeChange: (ScanMode) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val barHeight by animateFloatAsState(
        targetValue = if (mode == ScanMode.Barcode) 0.44f else 1f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "scanFrameHeight",
    )
    val transition = rememberInfiniteTransition(label = "scanLine")
    val lineProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanLineProgress",
    )

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val frameWidth = minOf(size.width * 0.74f, 320.dp.toPx())
            val frameHeight = frameWidth * barHeight
            val left = (size.width - frameWidth) / 2f
            val top = (size.height - frameHeight) / 2f - 18.dp.toPx()
            val corner = 22.dp.toPx()

            // 暗场：整屏减去取景框（EvenOdd 填充形成镂空）。
            val mask = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(
                    RoundRect(
                        rect = Rect(
                            left = left,
                            top = top,
                            right = left + frameWidth,
                            bottom = top + frameHeight,
                        ),
                        cornerRadius = CornerRadius(corner),
                    ),
                )
            }
            drawPath(mask, Color.Black.copy(alpha = 0.46f))

            // 取景框细边。
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(left, top),
                size = Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(corner),
                style = Stroke(width = 1.dp.toPx()),
            )

            // 四角高亮。
            val stroke = 3.dp.toPx()
            val leg = 26.dp.toPx()
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(primary, Offset(x, y + dy * leg), Offset(x, y), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(primary, Offset(x, y), Offset(x + dx * leg, y), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            val rr = left + frameWidth
            val bb = top + frameHeight
            corner(left, top, 1f, 1f)
            corner(rr, top, -1f, 1f)
            corner(left, bb, 1f, -1f)
            corner(rr, bb, -1f, -1f)

            // 扫描线：横贯取景框，两端渐隐。
            val y = top + frameHeight * lineProgress
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        primary.copy(alpha = 0f),
                        primary.copy(alpha = 0.85f),
                        primary.copy(alpha = 0f),
                    ),
                    startX = left,
                    endX = left + frameWidth,
                ),
                start = Offset(left + 6.dp.toPx(), y),
                end = Offset(left + frameWidth - 6.dp.toPx(), y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // 取景框内极淡的网格呼吸层，提示"正在识别"。
            drawRoundRect(
                color = primary.copy(alpha = 0.05f),
                topLeft = Offset(left, top),
                size = Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(corner),
            )
        }

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleToolButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.scan_close),
                    onClick = onClose,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.scan_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                CircleToolButton(
                    icon = if (torchOn) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                    contentDescription = stringResource(R.string.scan_torch),
                    onClick = onToggleTorch,
                    active = torchOn,
                )
                Spacer(Modifier.width(10.dp))
                CircleToolButton(
                    icon = Icons.Filled.Bookmarks,
                    contentDescription = stringResource(R.string.scan_saved_page),
                    onClick = onOpenSaved,
                )
                Spacer(Modifier.width(10.dp))
                CircleToolButton(
                    icon = Icons.Filled.PhotoLibrary,
                    contentDescription = stringResource(R.string.scan_album),
                    onClick = onPickAlbum,
                )
            }

            Spacer(Modifier.weight(1f))

            if (cameraUnavailable) {
                Text(
                    text = stringResource(R.string.scan_camera_unavailable),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ModeBar(mode = mode, onModeChange = onModeChange)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(
                        if (mode == ScanMode.Barcode) R.string.scan_hint_bar else R.string.scan_hint_qr,
                    ),
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** 圆形半透明工具按钮：扫描页与结果页共用的轻量图标入口。 */
@Composable
private fun CircleToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val background = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.38f)
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 底部识别模式导航：胶囊底 + 滑动指示块，切换时指示块做缓动位移动画。 */
@Composable
private fun ModeBar(
    mode: ScanMode,
    onModeChange: (ScanMode) -> Unit,
) {
    val options = listOf(ScanMode.Qr, ScanMode.Barcode)
    val selected = options.indexOf(mode).coerceAtLeast(0)
    val indicator by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "scanModeIndicator",
    )
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .width(232.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black.copy(alpha = 0.46f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp)),
    ) {
        Box(
            modifier = Modifier
                .padding(5.dp)
                .fillMaxWidth()
                .height(42.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / 2f
                drawRoundRect(
                    color = primary,
                    topLeft = Offset(cell * indicator + 0.dp.toPx(), 0.dp.toPx()),
                    size = Size(cell, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .clickable { onModeChange(option) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            tint = if (option == mode) Color.White else Color.White.copy(alpha = 0.62f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (option == ScanMode.Qr) R.string.scan_mode_qr else R.string.scan_mode_bar,
                            ),
                            color = if (option == mode) Color.White else Color.White.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (option == mode) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPanel(
    onRequest: () -> Unit,
    onPickAlbum: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.scan_permission_title),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.scan_permission_desc),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(20.dp))
            AppPrimaryButton(
                text = stringResource(R.string.scan_permission_grant),
                onClick = onRequest,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            Spacer(Modifier.height(10.dp))
            AppOutlinedButton(
                text = stringResource(R.string.scan_album),
                onClick = onPickAlbum,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.scan_perm_back),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                                 结果查看页                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun ScanResultPage(
    result: ScanResult,
    alreadySaved: Boolean,
    allowFillBarcode: Boolean,
    onRescan: () -> Unit,
    onCopyAll: () -> Unit,
    onSelectCopy: () -> Unit,
    onSave: () -> Unit,
    onTextPrint: () -> Unit,
    onFillBarcode: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onNoUrl: () -> Unit,
) {
    val urls = remember(result.text) { findUrls(result.text) }
    val scope = rememberCoroutineScope()
    val tapState = rememberTapState()

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.IconButton(onClick = onRescan) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.scan_rescan),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.scan_result_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.scan_result_format,
                            formatDisplayName(result.formatName),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatTime(result.scannedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .observeTaps(
                            state = tapState,
                            scope = scope,
                            onDoubleTap = onCopyAll,
                            onTripleTap = {
                                val url = urls.firstOrNull()
                                if (url == null) onNoUrl() else onOpenUrl(url)
                            },
                        ),
                ) {
                    SelectionContainerCompat {
                        Text(
                            text = highlightUrls(result.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }

                if (urls.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.scan_url_detected, urls.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    urls.take(3).forEach { url ->
                        Surface(
                            onClick = { onOpenUrl(url) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.scan_gesture_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppOutlinedButton(
                        text = stringResource(R.string.scan_copy_all),
                        onClick = onCopyAll,
                        modifier = Modifier.weight(1f),
                        leadingIcon = Icons.Filled.ContentCopy,
                    )
                    AppOutlinedButton(
                        text = stringResource(R.string.scan_copy_select),
                        onClick = onSelectCopy,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppOutlinedButton(
                        text = stringResource(if (alreadySaved) R.string.scan_saved_short else R.string.scan_save_action),
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = !alreadySaved,
                        leadingIcon = if (alreadySaved) Icons.Filled.Check else Icons.Filled.Save,
                    )
                    AppOutlinedButton(
                        text = stringResource(R.string.scan_text_print),
                        onClick = onTextPrint,
                        modifier = Modifier.weight(1f),
                        leadingIcon = Icons.Filled.Print,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (allowFillBarcode) {
                    AppPrimaryButton(
                        text = stringResource(R.string.scan_fill_barcode),
                        onClick = onFillBarcode,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                AppPrimaryButton(
                    text = stringResource(R.string.scan_rescan),
                    onClick = onRescan,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                              保存页 / 选择复制                                */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedSheet(
    items: List<SavedScan>,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(R.string.scan_saved_page),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.scan_saved_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Column(
                    Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    items.forEach { item ->
                        SavedRow(
                            item = item,
                            onCopy = { onCopy(item.text) },
                            onDelete = { onDelete(item.id) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SavedRow(
    item: SavedScan,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatDisplayName(item.formatName)} · ${formatTime(item.savedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.scan_copy_all),
                modifier = Modifier.size(18.dp),
            )
        }
        androidx.compose.material3.IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.scan_saved_delete),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectCopySheet(
    result: ScanResult?,
    onDismiss: () -> Unit,
    onCopyPart: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val text = result?.text.orEmpty()
    val urls = remember(text) { findUrls(text) }
    val lines = remember(text) {
        text.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(16)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(R.string.scan_select_copy_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            if (urls.isNotEmpty()) {
                SelectCopyRow(
                    label = stringResource(R.string.scan_copy_urls, urls.size),
                    preview = urls.joinToString("  "),
                    onClick = { onCopyPart(urls.joinToString("\n")) },
                )
            }
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                lines.forEach { line ->
                    SelectCopyRow(
                        label = line.take(48),
                        preview = if (line.length > 48) "…" else "",
                        onClick = { onCopyPart(line) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SelectCopyRow(
    label: String,
    preview: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 72.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                              文本识别与手势辅助                               */
/* -------------------------------------------------------------------------- */

/** 网址识别：http(s) 与 www 开头，排除常见中文标点结尾。 */
private val URL_REGEX = Regex(
    """(?:https?://|www\.)[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%\-]+""",
    RegexOption.IGNORE_CASE,
)

private data class UrlMatch(
    val raw: String,
    val url: String,
    val start: Int,
    val end: Int,
)

private fun findUrlMatches(text: String): List<UrlMatch> =
    URL_REGEX.findAll(text).mapNotNull { match ->
        val raw = match.value.trimEnd('.', ',', ';', ')', '）', '】', '。', '，', '；')
        if (raw.isBlank()) return@mapNotNull null
        UrlMatch(
            raw = raw,
            url = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw,
            start = match.range.first,
            end = match.range.first + raw.length,
        )
    }.toList()

internal fun findUrls(text: String): List<String> =
    findUrlMatches(text).map { it.url }.distinct()

/** 把内容中的网址加底色 + 下划线，直接体现"智能识别"。 */
internal fun highlightUrls(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    findUrlMatches(text).forEach { match ->
        addStyle(
            SpanStyle(
                color = Color(0xFF1A73E8),
                background = Color(0x1A1A73E8),
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium,
            ),
            start = match.start,
            end = match.end,
        )
    }
}

private fun formatDisplayName(name: String): String =
    BarcodeFormatOption.fromName(name)?.displayName ?: name

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

/** 双击 / 三击的判定窗口。 */
private const val MULTI_TAP_WINDOW_MS = 420L

private class TapState {
    var count: Int = 0
    var job: Job? = null
}

@Composable
private fun rememberTapState(): TapState = remember { TapState() }

/**
 * 观察式多击手势：不消费事件，所以内部 `SelectionContainer` 的长按选择、
 * 拖拽把手仍然可用；两次点击 → 复制，三次及以上 → 打开首个链接。
 */
private fun Modifier.observeTaps(
    state: TapState,
    scope: CoroutineScope,
    onDoubleTap: () -> Unit,
    onTripleTap: () -> Unit,
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        var moved = false
        var released = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                moved = true
            }
            if (!change.pressed) {
                released = true
                break
            }
        }
        if (!released || moved) return@awaitEachGesture

        state.count += 1
        state.job?.cancel()
        val currentCount = state.count
        state.job = scope.launch {
            delay(MULTI_TAP_WINDOW_MS)
            when {
                currentCount >= 3 -> onTripleTap()
                currentCount == 2 -> onDoubleTap()
            }
            state.count = 0
        }
    }
}

/** 极简 SelectionContainer 包装，避免在文件顶部额外导入整个文本包。 */
@Composable
private fun SelectionContainerCompat(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        content()
    }
}

/** Compose 的 View.context 常被主题包装，需要逐层解包才能拿到 Activity。 */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return current as? android.app.Activity
}
