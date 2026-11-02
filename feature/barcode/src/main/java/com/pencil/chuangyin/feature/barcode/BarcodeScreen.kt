package com.pencil.chuangyin.feature.barcode

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Filter1
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pencil.chuangyin.core.barcode.BarcodeFormatOption
import com.pencil.chuangyin.core.barcode.BarcodeGroup
import com.pencil.chuangyin.core.ui.components.AppAlertDialog
import com.pencil.chuangyin.core.ui.components.AppFilterChip
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppLiquidDialog
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.pseudoGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.ui.editor.FullscreenImageDialog
import com.pencil.chuangyin.core.ui.editor.LayerEditorToolbar
import com.pencil.chuangyin.core.ui.editor.LayerEditorViewport
import com.pencil.chuangyin.core.ui.editor.PageLayerEditorState
import com.pencil.chuangyin.core.ui.editor.PageLayerTransform
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.core.theme.glassAwareOutline
import com.pencil.chuangyin.printer.PrintError
import com.pencil.chuangyin.printer.PrintPhase
import com.pencil.chuangyin.printer.PrintProgress
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterStatus
import com.pencil.chuangyin.resources.R as ResR
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun BarcodeRoute(
    onBack: () -> Unit,
    onGoConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BarcodeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    BarcodeScreen(
        state = state,
        onBack = onBack,
        onGoConnect = onGoConnect,
        onContentChange = viewModel::setContent,
        onFormatChange = viewModel::setFormat,
        onGenerate = viewModel::generate,
        onOpenEditor = viewModel::openEditor,
        onCloseEditor = viewModel::closeEditor,
        onEditTransformChange = viewModel::updateEditTransform,
        onSave = viewModel::save,
        onPrint = viewModel::print,
        onPrintSingle = viewModel::printSingle,
        onCancelPrint = viewModel::cancelPrint,
        onScan = viewModel::scanFromUri,
        onApplyScanResult = viewModel::applyScanResultToContent,
        onClearScanResult = viewModel::clearScanResult,
        onClearResult = viewModel::clearResult,
        onClearNotice = viewModel::clearNotice,
        modifier = modifier,
    )
}

/**
 * 条码打印页：内容输入 → 格式选择（常用 6 项 + 更多格式分一维/二维）→ 生成 →
 * 白纸预览（复用图层编辑器，可拖动/缩放）→ 底部保存/打印，另附相库「扫描解码」入口。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScreen(
    state: BarcodeUiState,
    onBack: () -> Unit,
    onGoConnect: () -> Unit,
    onContentChange: (String) -> Unit,
    onFormatChange: (BarcodeFormatOption) -> Unit,
    onGenerate: () -> Unit,
    onOpenEditor: () -> Unit,
    onCloseEditor: () -> Unit,
    onEditTransformChange: (PageLayerTransform) -> Unit,
    onSave: () -> Unit,
    onPrint: () -> Unit,
    onPrintSingle: () -> Unit,
    onCancelPrint: () -> Unit,
    onScan: (Uri) -> Unit,
    onApplyScanResult: () -> Unit,
    onClearScanResult: () -> Unit,
    onClearResult: () -> Unit,
    onClearNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 控制编辑模式优先于返回：先退出图层编辑，再退出页面。
    BackHandler(enabled = state.editorOpen) { onCloseEditor() }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onScan(uri) }

    // Android 9 及以下保存公共相册需要写存储权限。
    val writeStoragePermission = rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    var savePending by remember { mutableStateOf(false) }
    LaunchedEffect(writeStoragePermission.status) {
        if (savePending && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                writeStoragePermission.status.isGranted)
        ) {
            savePending = false
            onSave()
        }
    }
    val onSaveRequested: () -> Unit = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !writeStoragePermission.status.isGranted) {
            savePending = true
            writeStoragePermission.launchPermissionRequest()
        } else {
            onSave()
        }
    }

    val cooling = state.isPrinting && state.overheatActive
    var overheatDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(cooling) { if (cooling) overheatDismissed = false }
    val canPrint = state.connectedDevice != null && (!state.isPrinting || cooling) &&
        state.generatedBitmap != null
    val onPrintGuarded: () -> Unit = { if (cooling) overheatDismissed = false else onPrint() }
    val onPrintSingleGuarded: () -> Unit = { if (cooling) overheatDismissed = false else onPrintSingle() }

    val noticeMessage = when (val notice = state.notice) {
        BarcodeNotice.ContentEmpty -> stringResource(R.string.barcode_content_empty)
        BarcodeNotice.GenerateFailed -> stringResource(R.string.barcode_generate_failed)
        BarcodeNotice.Saved -> stringResource(R.string.barcode_saved)
        BarcodeNotice.SaveFailed -> stringResource(R.string.barcode_save_failed)
        BarcodeNotice.ScanFailed -> stringResource(R.string.barcode_scan_failed)
        null -> null
    }
    val statusMessage = state.printerStatus?.let { stringResource(printerStatusRes(it)) }
    val errorResult = state.lastResult as? PrintResult.Error
    val glassActive = rememberLiquidGlassActive()

    LaunchedEffect(state.lastResult) {
        if (state.lastResult is PrintResult.Success) {
            snackbarHostState.showSnackbar(context.getString(R.string.barcode_print_success))
            onClearResult()
        }
    }
    LaunchedEffect(statusMessage) {
        statusMessage?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(noticeMessage) {
        noticeMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearNotice()
        }
    }

    if (errorResult != null) {
        val errText = stringResource(printErrorRes(errorResult.code))
        val detail = errorResult.detail
        val detailText = if (!detail.isNullOrBlank()) {
            stringResource(R.string.barcode_print_failed_detail, detail)
        } else {
            null
        }
        AppLiquidDialog(
            onDismissRequest = onClearResult,
            title = stringResource(R.string.barcode_print_failed_title),
            message = if (detailText != null) errText + "\n\n" + detailText else errText,
            confirmLabel = stringResource(ResR.string.label_ok),
            onConfirm = onClearResult,
        )
    }

    if (cooling && !overheatDismissed) {
        OverheatDialog(
            remainingMs = state.progress?.coolingRemainingMs ?: 0L,
            onDismiss = { overheatDismissed = true },
        )
    }

    state.scanResult?.let { decoded ->
        AppAlertDialog(
            onDismissRequest = onClearScanResult,
            icon = {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { AppText(text = stringResource(R.string.barcode_scan_title)) },
            text = {
                Column {
                    AppText(
                        text = stringResource(R.string.barcode_scan_format, decoded.formatName),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                    AppText(text = decoded.text, style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {
                AppTextButton(
                    text = stringResource(R.string.barcode_scan_fill),
                    onClick = onApplyScanResult,
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(R.string.barcode_scan_close),
                    onClick = onClearScanResult,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.barcode_title)) },
                navigationIcon = {
                    AppIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(ResR.string.label_back),
                    )
                },
                modifier = Modifier.nativeGlassSurface(
                    shape = RoundedCornerShape(0.dp),
                    enabled = glassActive,
                    fallbackColor = MaterialTheme.colorScheme.surface,
                ),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            BarcodeBottomBar(
                connectedName = state.connectedDevice?.name,
                isConnecting = state.isConnecting,
                isPrinting = state.isPrinting,
                isSaving = state.isSaving,
                hasBarcode = state.generatedBitmap != null,
                progress = state.progress,
                canPrint = canPrint,
                onGoConnect = onGoConnect,
                onSave = onSaveRequested,
                onPrint = onPrintGuarded,
                onPrintSingle = onPrintSingleGuarded,
                onCancelPrint = onCancelPrint,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m))
                .padding(
                    top = dimensionResource(ResR.dimen.dimen_global_padding_m),
                    bottom = innerPadding.calculateBottomPadding() +
                        dimensionResource(ResR.dimen.dimen_global_padding_m),
                ),
        ) {
            ContentAndFormatCard(
                content = state.content,
                format = state.format,
                isGenerating = state.isGenerating,
                isScanning = state.isScanning,
                onContentChange = onContentChange,
                onFormatChange = onFormatChange,
                onGenerate = onGenerate,
                onScan = {
                    runCatching {
                        scanLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }.onFailure {
                        Toast.makeText(context, R.string.barcode_scan_failed, Toast.LENGTH_SHORT).show()
                    }
                },
            )

            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))

            BarcodePreview(
                previewBitmap = state.previewBitmap,
                generatedBitmap = state.generatedBitmap,
                isGenerating = state.isGenerating,
                editorOpen = state.editorOpen,
                editTransform = state.editTransform,
                onOpenEditor = onOpenEditor,
                onCloseEditor = onCloseEditor,
                onEditTransformChange = onEditTransformChange,
            )

            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
            AppText(
                text = stringResource(R.string.barcode_scan_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        }
    }
}

/** 内容输入 + 格式选择 + 生成/扫描操作。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentAndFormatCard(
    content: String,
    format: BarcodeFormatOption,
    isGenerating: Boolean,
    isScanning: Boolean,
    onContentChange: (String) -> Unit,
    onFormatChange: (BarcodeFormatOption) -> Unit,
    onGenerate: () -> Unit,
    onScan: () -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        ) {
            AppText(
                text = stringResource(R.string.barcode_section_content),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            AppTextField(
                value = content,
                onValueChange = onContentChange,
                placeholder = stringResource(R.string.barcode_content_placeholder),
                singleLine = false,
            )

            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
            AppText(
                text = stringResource(R.string.barcode_section_format),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))

            if (showAll) {
                BarcodeFormatOption.entries
                    .groupBy { it.group }
                    .forEach { (group, options) ->
                        AppText(
                            text = stringResource(formatGroupRes(group)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                        FormatChipRow(
                            options = options,
                            selected = format,
                            onSelect = onFormatChange,
                        )
                        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                    }
            } else {
                // 折叠态只留 6 个常用项；当前选中项不在常用项时必须补进来，否则看不到选中态。
                val visible = BarcodeFormatOption.COMMON.let {
                    if (format in it) it else it + format
                }
                FormatChipRow(options = visible, selected = format, onSelect = onFormatChange)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
            ) {
                AppFilterChip(
                    selected = false,
                    onClick = { showAll = !showAll },
                    label = stringResource(
                        if (showAll) R.string.barcode_format_less else R.string.barcode_format_more,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
            AppPrimaryButton(
                text = if (isGenerating) {
                    stringResource(R.string.barcode_generating)
                } else {
                    stringResource(R.string.barcode_action_generate)
                },
                onClick = onGenerate,
                enabled = content.isNotBlank() && !isGenerating,
                leadingIcon = Icons.Filled.QrCode2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            AppOutlinedButton(
                text = if (isScanning) {
                    stringResource(R.string.barcode_scanning)
                } else {
                    stringResource(R.string.barcode_scan_entry)
                },
                onClick = onScan,
                enabled = !isScanning,
                leadingIcon = Icons.Filled.QrCodeScanner,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatChipRow(
    options: List<BarcodeFormatOption>,
    selected: BarcodeFormatOption,
    onSelect: (BarcodeFormatOption) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
    ) {
        options.forEach { option ->
            AppFilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = option.displayName,
            )
        }
    }
}

private fun formatGroupRes(group: BarcodeGroup): Int = when (group) {
    BarcodeGroup.OneDimensional -> R.string.barcode_group_1d
    BarcodeGroup.TwoDimensional -> R.string.barcode_group_2d
}

/**
 * 白纸预览：空态显示引导；生成后展示真实条码页。
 * 右上/底部浮动「编辑 / 全屏」，编辑时切换为 [LayerEditorViewport]（画布 + 条码图层），
 * 高度经 [animateDpAsState] 平滑过渡。
 */
@Composable
private fun BarcodePreview(
    previewBitmap: android.graphics.Bitmap?,
    generatedBitmap: android.graphics.Bitmap?,
    isGenerating: Boolean,
    editorOpen: Boolean,
    editTransform: PageLayerTransform,
    onOpenEditor: () -> Unit,
    onCloseEditor: () -> Unit,
    onEditTransformChange: (PageLayerTransform) -> Unit,
) {
    val glassActive = rememberLiquidGlassActive()
    var fullscreen by remember { mutableStateOf(false) }
    val editorState = remember(editorOpen) { PageLayerEditorState(editTransform) }
    LaunchedEffect(editorState) {
        snapshotFlow { editorState.transform }
            .distinctUntilChanged()
            .collect(onEditTransformChange)
    }
    val previewHeight by animateDpAsState(
        targetValue = if (editorOpen) {
            dimensionResource(ResR.dimen.dimen_image_preview_height) + 60.dp
        } else {
            dimensionResource(ResR.dimen.dimen_image_preview_height)
        },
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "barcodePreviewHeight",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(previewHeight)
            .pseudoGlassSurface(
                shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_m)),
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
            .glassAwareOutline(
                RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_m)),
                glassActive = false,
            ),
        shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_m)),
        color = Color.Transparent,
    ) {
        if (editorOpen) {
            BarcodeEditorPanel(
                layerBitmap = generatedBitmap,
                editorState = editorState,
                onDone = onCloseEditor,
            )
        } else if (previewBitmap == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isGenerating) {
                    AppLoadingIndicator(size = 28.dp)
                } else {
                    AppText(
                        text = stringResource(R.string.barcode_preview_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.barcode_title),
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
                    )
                }
                BarcodePreviewActions(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
                    canEdit = generatedBitmap != null,
                    canFullscreen = previewBitmap != null,
                    onEdit = onOpenEditor,
                    onFullscreen = { fullscreen = true },
                )
            }
        }
    }
    if (fullscreen && previewBitmap != null) {
        FullscreenImageDialog(
            image = previewBitmap.asImageBitmap(),
            onDismiss = { fullscreen = false },
        )
    }
}

@Composable
private fun BarcodePreviewActions(
    modifier: Modifier = Modifier,
    canEdit: Boolean,
    canFullscreen: Boolean,
    onEdit: () -> Unit,
    onFullscreen: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_s)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onEdit, enabled = canEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.barcode_action_edit))
            }
            TextButton(onClick = onFullscreen, enabled = canFullscreen) {
                Icon(
                    imageVector = Icons.Filled.Fullscreen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.barcode_action_fullscreen))
            }
        }
    }
}

/** 编辑模式：工具条 + 画布视口 + 完成入口。 */
@Composable
private fun BarcodeEditorPanel(
    layerBitmap: android.graphics.Bitmap?,
    editorState: PageLayerEditorState,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(R.string.barcode_editor_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.barcode_editor_done))
            }
        }
        LayerEditorToolbar(state = editorState)
        LayerEditorViewport(
            image = layerBitmap?.asImageBitmap(),
            state = editorState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        AppText(
            text = stringResource(R.string.barcode_editor_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        )
    }
}

@Composable
private fun BarcodeBottomBar(
    connectedName: String?,
    isConnecting: Boolean,
    isPrinting: Boolean,
    isSaving: Boolean,
    hasBarcode: Boolean,
    progress: PrintProgress?,
    canPrint: Boolean,
    onGoConnect: () -> Unit,
    onSave: () -> Unit,
    onPrint: () -> Unit,
    onPrintSingle: () -> Unit,
    onCancelPrint: () -> Unit,
) {
    val glassActive = rememberLiquidGlassActive()
    Surface(
        modifier = Modifier.nativeGlassSurface(
            shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_l)),
            enabled = glassActive,
            fallbackColor = MaterialTheme.colorScheme.surface,
        ),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            if (isPrinting) {
                BarcodePrintProgressLine(progress)
                Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppPrimaryButton(
                    text = stringResource(R.string.barcode_action_cancel_print),
                    onClick = onCancelPrint,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = when {
                            isConnecting -> stringResource(R.string.barcode_connecting)
                            connectedName != null -> stringResource(R.string.barcode_connected_to, connectedName)
                            else -> stringResource(R.string.barcode_device_not_selected)
                        },
                        color = if (connectedName != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (connectedName == null) {
                        TextButton(
                            onClick = onGoConnect,
                            enabled = !isConnecting,
                            contentPadding = PaddingValues(
                                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.barcode_action_go_connect),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppOutlinedButton(
                        text = stringResource(R.string.barcode_action_save),
                        onClick = onSave,
                        enabled = hasBarcode && !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(dimensionResource(ResR.dimen.dimen_global_btn_height_min)),
                        leadingIcon = Icons.Filled.Download,
                    )
                    AppPrimaryButton(
                        text = if (isSaving) {
                            stringResource(R.string.barcode_saving)
                        } else {
                            stringResource(R.string.barcode_action_print)
                        },
                        onClick = onPrint,
                        enabled = canPrint && !isSaving,
                        leadingIcon = Icons.Filled.Print,
                        modifier = Modifier.weight(1.6f),
                    )
                    AppIconButton(
                        onClick = onPrintSingle,
                        imageVector = Icons.Filled.Filter1,
                        contentDescription = stringResource(R.string.barcode_action_print_single),
                        enabled = canPrint && !isSaving,
                        size = dimensionResource(ResR.dimen.dimen_global_btn_height_min),
                    )
                }
            }
        }
    }
}

@Composable
private fun BarcodePrintProgressLine(progress: PrintProgress?) {
    val phase = progress?.phase
    AppText(
        text = when (phase) {
            PrintPhase.CoolingDown -> stringResource(
                R.string.barcode_progress_cooling,
                ((progress?.coolingRemainingMs ?: 0L) / 1000L).toInt() + 1,
            )
            PrintPhase.Printing -> stringResource(R.string.barcode_progress_printing)
            PrintPhase.Finishing -> stringResource(R.string.barcode_progress_finishing)
            else -> stringResource(R.string.barcode_progress_preparing)
        },
        color = if (phase == PrintPhase.CoolingDown) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
    )
    if (phase == PrintPhase.Printing || phase == PrintPhase.CoolingDown) {
        val target = if (phase == PrintPhase.CoolingDown) {
            1f
        } else {
            progress?.let {
                ((it.segmentIndex + 1f) / it.segmentCount.coerceAtLeast(1)).coerceIn(0f, 1f)
            } ?: 0f
        }
        val animated by animateFloatAsState(
            targetValue = target,
            animationSpec = rememberAppMotion().effectSpec(),
            label = "barcodePrintProgress",
        )
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        )
    } else {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        )
    }
}

private fun printErrorRes(error: PrintError): Int = when (error) {
    PrintError.NotConnected -> R.string.barcode_err_not_connected
    PrintError.Permission -> R.string.barcode_err_permission
    PrintError.Timeout -> R.string.barcode_err_timeout
    PrintError.WriteFailed -> R.string.barcode_err_write_failed
    PrintError.RenderFailed -> R.string.barcode_err_render_failed
    PrintError.Cancelled -> R.string.barcode_err_cancelled
    PrintError.Generic -> R.string.barcode_err_generic
}

private fun printerStatusRes(status: PrinterStatus): Int = when (status) {
    PrinterStatus.OutOfPaper -> R.string.barcode_status_out_of_paper
    PrinterStatus.CoverOpen -> R.string.barcode_status_cover_open
    PrinterStatus.Overheat -> R.string.barcode_status_overheat
    PrinterStatus.LowBattery -> R.string.barcode_status_low_battery
}

/** 打印机过热冷却提示：展示预计剩余秒数，可关闭。 */
@Composable
private fun OverheatDialog(
    remainingMs: Long,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AppTextButton(text = stringResource(ResR.string.label_ok), onClick = onDismiss)
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.Thermostat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { AppText(text = stringResource(ResR.string.printer_overheat_title)) },
        text = {
            AppText(
                text = if (remainingMs > 0L) {
                    stringResource(
                        ResR.string.printer_overheat_message,
                        ((remainingMs + 999) / 1000L).toInt(),
                    )
                } else {
                    stringResource(ResR.string.printer_overheat_message_unknown)
                },
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun BarcodeScreenPreview() {
    BarcodeScreen(
        state = BarcodeUiState(),
        onBack = {},
        onGoConnect = {},
        onContentChange = {},
        onFormatChange = {},
        onGenerate = {},
        onOpenEditor = {},
        onCloseEditor = {},
        onEditTransformChange = {},
        onSave = {},
        onPrint = {},
        onPrintSingle = {},
        onCancelPrint = {},
        onScan = {},
        onApplyScanResult = {},
        onClearScanResult = {},
        onClearResult = {},
        onClearNotice = {},
    )
}
