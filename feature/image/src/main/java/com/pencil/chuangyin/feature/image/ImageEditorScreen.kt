package com.pencil.chuangyin.feature.image

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Filter1
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import com.pencil.chuangyin.core.ui.editor.FullscreenImageDialog
import com.pencil.chuangyin.core.ui.editor.LayerEditorToolbar
import com.pencil.chuangyin.core.ui.editor.LayerEditorViewport
import com.pencil.chuangyin.core.ui.editor.PageLayerEditorState
import com.pencil.chuangyin.core.ui.editor.PageLayerTransform
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pencil.chuangyin.core.ui.components.AppAlertDialog
import com.pencil.chuangyin.core.ui.components.AppLiquidDialog
import com.pencil.chuangyin.core.ui.components.AppFilterChip
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppSourceAction
import com.pencil.chuangyin.core.ui.components.AppSourceCard
import com.pencil.chuangyin.core.ui.components.AppSwitch
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.components.LiquidGlassLayerScope
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.core.ui.tutorial.CoachMarkHost
import com.pencil.chuangyin.core.ui.tutorial.CoachMarkStep
import com.pencil.chuangyin.core.ui.tutorial.coachMarkAnchor
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.pseudoGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.theme.glassAwareOutline
import com.pencil.chuangyin.printer.BinarizeAlgorithm
import com.pencil.chuangyin.printer.ImageProcessOptions
import com.pencil.chuangyin.printer.PrintError
import com.pencil.chuangyin.printer.PrintOptions
import com.pencil.chuangyin.printer.PrintPhase
import com.pencil.chuangyin.printer.PrintProgress
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterStatus
import com.pencil.chuangyin.resources.R as ResR
import com.pencil.chuangyin.feature.image.R
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ImageEditorRoute(
    onBack: () -> Unit,
    onGoConnect: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenPixivGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ImageEditorViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 拍照页确认后回到本页（ON_RESUME）时消费拍摄成品；首次进入 drain 为空，无副作用。
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.consumeTransferredShots()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 选择页打开时，系统返回键先回到编辑页而不是退出整个页面。
    BackHandler(enabled = state.pick != null) { viewModel.cancelPick() }

    // 系统相机通道：拍照写入 libFileProvider 授权的缓存文件，返回后追加到本页。
    val context = LocalContext.current
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    val systemCameraLauncher = rememberLauncherForActivityResult(TakePictureWithGrant()) { success ->
        val uri = cameraOutputUri
        cameraOutputUri = null
        if (success && uri != null) viewModel.importSystemCameraShot(uri)
    }
    val launchSystemCamera: () -> Unit = {
        runCatching {
            val dir = File(context.cacheDir, "camera_shots").apply { mkdirs() }
            val file = File(dir, "capture_${System.nanoTime()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraOutputUri = uri
            systemCameraLauncher.launch(uri)
        }.onFailure {
            Toast.makeText(context, R.string.image_camera_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    val pick = state.pick
    if (pick != null) {
        ImagePickScreen(
            pick = pick,
            onBack = viewModel::cancelPick,
            onToggle = viewModel::togglePickSelection,
            onToggleSelectAll = viewModel::togglePickSelectAll,
            onConfirm = viewModel::confirmPick,
            onEnsureThumbnail = viewModel::ensurePickThumbnail,
            modifier = modifier,
        )
    } else {
        ImageEditorScreen(
            state = state,
            onBack = onBack,
            onGoConnect = onGoConnect,
            onOpenPixivGallery = onOpenPixivGallery,
            onImportGallery = viewModel::importGallery,
            onCamera = onOpenCamera,
            onSystemCamera = launchSystemCamera,
            onScanner = onOpenScanner,
            onImportPdf = viewModel::importPdf,
            onImportArchive = viewModel::importArchive,
            onPageChange = viewModel::setCurrentPage,
            onRemoveCurrent = viewModel::removeCurrentPage,
            onClearAll = viewModel::clearAll,
            onAlgorithmChange = viewModel::setAlgorithm,
            onThresholdChange = viewModel::setThreshold,
            onRotationChange = viewModel::setRotation,
            onBatchChange = viewModel::setBatchEnabled,
            onCopiesChange = viewModel::setCopies,
            onResetPageOptions = viewModel::resetPageOptions,
            onOpenEditor = viewModel::openEditor,
            onCloseEditor = viewModel::closeEditor,
            onEditTransformChange = viewModel::updateEditTransform,
            onSave = viewModel::save,
            onPrint = viewModel::print,
            onPrintSingle = viewModel::printSingle,
            onCancelPrint = viewModel::cancelPrint,
            onClearResult = viewModel::clearResult,
            onClearNotice = viewModel::clearNotice,
            modifier = modifier,
        )
    }
}

/**
 * 图片打印页：
 * 顶部 2×2 来源入口（相册/拍照/PDF/压缩包）→ 白底纸张预览（横向翻页）
 * → 黑白化算法与阈值/旋转/批量开关 → 底部保存 + 打印。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ImageEditorScreen(
    state: ImageEditorUiState,
    onBack: () -> Unit,
    onGoConnect: () -> Unit,
    onOpenPixivGallery: () -> Unit,
    onImportGallery: (List<android.net.Uri>, android.content.ContentResolver) -> Unit,
    onCamera: () -> Unit,
    onSystemCamera: () -> Unit,
    onScanner: () -> Unit,
    onImportPdf: (android.net.Uri) -> Unit,
    onImportArchive: (android.net.Uri) -> Unit,
    onPageChange: (Int) -> Unit,
    onRemoveCurrent: () -> Unit,
    onClearAll: () -> Unit,
    onAlgorithmChange: (BinarizeAlgorithm) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onRotationChange: (Int) -> Unit,
    onBatchChange: (Boolean) -> Unit,
    onCopiesChange: (Int) -> Unit,
    onResetPageOptions: () -> Unit,
    onOpenEditor: () -> Unit,
    onCloseEditor: () -> Unit,
    onEditTransformChange: (PageLayerTransform) -> Unit,
    onSave: () -> Unit,
    onPrint: () -> Unit,
    onPrintSingle: () -> Unit,
    onCancelPrint: () -> Unit,
    onClearResult: () -> Unit,
    onClearNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(GALLERY_MAX_ITEMS),
    ) { uris ->
        if (uris.isNotEmpty()) onImportGallery(uris, context.contentResolver)
    }
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onImportPdf(uri) }
    // 「压缩包」入口：只接受 zip，导入其中的图片页。
    val archiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onImportArchive(uri) }

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

    // 拍照按钮的「分裂」态：点一下展开为「系统相机 / 软件内摄像页」两条通道。
    var cameraSplitOpen by remember { mutableStateOf(false) }

    val cooling = state.isPrinting && state.overheatActive
    // 过热冷却期间打印按钮保持可点：点击不重复下单，仅重新弹出冷却提示。
    val canPrint = state.connectedDevice != null && (!state.isPrinting || cooling) && state.pages.isNotEmpty()
    var overheatDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(cooling) { if (cooling) overheatDismissed = false }
    val onPrintGuarded: () -> Unit = { if (cooling) overheatDismissed = false else onPrint() }
    val onPrintSingleGuarded: () -> Unit = { if (cooling) overheatDismissed = false else onPrintSingle() }

    val noticeMessage = when (val notice = state.notice) {
        is ImageNotice.Added -> stringResource(R.string.image_notice_added, notice.pages)
        is ImageNotice.Saved -> stringResource(R.string.image_save_success, notice.count)
        is ImageNotice.ArchiveEmpty -> stringResource(R.string.image_notice_archive_empty)
        is ImageNotice.PdfFailed -> stringResource(R.string.image_notice_pdf_failed)
        is ImageNotice.ImportFailed -> stringResource(R.string.image_notice_import_failed)
        is ImageNotice.SaveFailed -> stringResource(R.string.image_save_failed)
        null -> null
    }
    val statusMessage = state.printerStatus?.let { stringResource(printerStatusRes(it)) }
    val errorResult = state.lastResult as? PrintResult.Error
    val glassActive = rememberLiquidGlassActive()

    LaunchedEffect(state.lastResult) {
        if (state.lastResult is PrintResult.Success) {
            snackbarHostState.showSnackbar(context.getString(R.string.image_print_success))
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
            stringResource(R.string.image_print_failed_detail, detail)
        } else {
            null
        }
        AppLiquidDialog(
            onDismissRequest = onClearResult,
            title = stringResource(R.string.image_print_failed_title),
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

    CoachMarkHost(
        pageKey = "image_print",
        steps = listOf(
            CoachMarkStep(
                anchor = "image_import",
                title = stringResource(ResR.string.tutorial_image_import_title),
                description = stringResource(ResR.string.tutorial_image_import_desc),
            ),
        ),
        modifier = modifier,
    ) {
    Scaffold(
        modifier = Modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.image_title)) },
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
            ImageBottomBar(
                connectedName = state.connectedDevice?.name,
                isConnecting = state.isConnecting,
                isPrinting = state.isPrinting,
                isSaving = state.isSaving,
                hasPages = state.pages.isNotEmpty(),
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
                // 只避开顶栏：底部 inset 转为滚动内容的尾部留白，使页面内容可以绘制到
                // 底栏玻璃之后——底栏玻璃折射到的是真实页面内容而非纯色主题底
                //（纯色背景 + 玻璃着色 = 一块不透明底板的观感）。
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
            // 来源入口：一个白色圆角卡片，大「+」为主入口（相册多选），
            // 右侧圆形图标入口（文稿扫描/相机/PDF/压缩包/P 站图库）。
            AppSourceCard(
                primaryAction = AppSourceAction(
                    icon = Icons.Filled.Add,
                    label = stringResource(R.string.image_source_gallery),
                    onClick = {
                        runCatching {
                            galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    },
                ),
                primarySize = 64.dp,
                primaryIconSize = 30.dp,
                actionSize = 46.dp,
                actionIconSize = 22.dp,
                modifier = Modifier.coachMarkAnchor("image_import"),
                actions = listOf(
                    AppSourceAction(
                        icon = Icons.Filled.DocumentScanner,
                        label = stringResource(R.string.image_source_scanner),
                        onClick = onScanner,
                    ),
                    AppSourceAction(
                        icon = Icons.Filled.CameraAlt,
                        label = stringResource(R.string.image_source_camera),
                        onClick = { cameraSplitOpen = !cameraSplitOpen },
                    ),
                    AppSourceAction(
                        icon = Icons.Filled.PictureAsPdf,
                        label = stringResource(R.string.image_source_pdf),
                        onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                    ),
                    AppSourceAction(
                        icon = Icons.Filled.FolderZip,
                        label = stringResource(R.string.image_source_archive),
                        onClick = {
                            archiveLauncher.launch(
                                arrayOf("application/zip", "application/x-zip-compressed"),
                            )
                        },
                    ),
                    // 「P 站图库」入口：与其余导入入口同级，直接取用已保存的图片。
                    AppSourceAction(
                        icon = Icons.Filled.Inventory2,
                        label = stringResource(ResR.string.pixiv_open_repository),
                        onClick = onOpenPixivGallery,
                    ),
                ),
            )
            CameraSplitPanel(
                visible = cameraSplitOpen,
                onSystemCamera = {
                    cameraSplitOpen = false
                    onSystemCamera()
                },
                onInAppCamera = {
                    cameraSplitOpen = false
                    onCamera()
                },
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))

            // 页码 / 删除 / 清空：收进一张近不透明的卡片，与页面背景彻底分离
            //（旧版直接浮在背景图上，文字与按钮互相压叠、读不清）。
            if (state.pages.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                            vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = stringResource(
                                    R.string.image_page_indicator,
                                    state.currentIndex + 1,
                                    state.pages.size,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            // 删除：仅移除当前预览页一张；清空：移除全部页。
                            TextButton(onClick = onRemoveCurrent) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(text = stringResource(R.string.image_action_delete))
                            }
                            TextButton(onClick = onClearAll) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(text = stringResource(R.string.image_action_clear))
                            }
                        }
                        // 本页已单独调整时给出一键恢复沿用第 1 页参数的入口。
                        if (state.currentPageCustomized) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppText(
                                    text = stringResource(R.string.image_page_options_custom),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                AppTextButton(
                                    text = stringResource(R.string.image_action_reset_page),
                                    onClick = onResetPageOptions,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            }

            PreviewPaper(
                pages = state.pages,
                currentIndex = state.currentIndex,
                previewBitmap = state.previewBitmap,
                isPreparing = state.isPreparing,
                editorOpen = state.editorOpen,
                editorLayerBitmap = state.editorLayerBitmap,
                editTransform = state.editTransform,
                onPageChange = onPageChange,
                onOpenEditor = onOpenEditor,
                onCloseEditor = onCloseEditor,
                onEditTransformChange = onEditTransformChange,
            )

            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))

            AlgorithmControls(
                algorithm = state.processOptions.algorithm,
                threshold = state.processOptions.threshold,
                rotationDegrees = state.processOptions.rotationDegrees,
                onAlgorithmChange = onAlgorithmChange,
                onThresholdChange = onThresholdChange,
                onRotationChange = onRotationChange,
            )

            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))

            BatchSwitchCard(
                enabled = state.batchEnabled,
                copies = state.copies,
                onEnabledChange = onBatchChange,
                onCopiesChange = onCopiesChange,
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        }
    }
    }
}

/**
 * 「拍照」按钮的分裂面板：点一下按钮后，内容从按钮中线向两侧分开——
 * 左「系统相机」（ACTION_IMAGE_CAPTURE）与右「软件内摄像页」（CameraX 取景）。
 * 分裂过程由 [AnimatedVisibility] 的纵向展开与两侧卡片的水平位移共同驱动。
 */
@Composable
private fun CameraSplitPanel(
    visible: Boolean,
    onSystemCamera: () -> Unit,
    onInAppCamera: () -> Unit,
) {
    val split by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "cameraSplit",
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) +
            expandVertically(expandFrom = Alignment.Top, animationSpec = tween(260)),
        exit = fadeOut(animationSpec = tween(180)) +
            shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(200)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
            horizontalArrangement = Arrangement.spacedBy(
                dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
        ) {
            SplitCameraOption(
                icon = Icons.Filled.PhotoCamera,
                title = stringResource(R.string.image_camera_system),
                description = stringResource(R.string.image_camera_system_desc),
                onClick = onSystemCamera,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = (1f - split) * size.width * 0.35f
                        alpha = split
                    },
            )
            SplitCameraOption(
                icon = Icons.Filled.CameraAlt,
                title = stringResource(R.string.image_camera_inapp),
                description = stringResource(R.string.image_camera_inapp_desc),
                onClick = onInAppCamera,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = -(1f - split) * size.width * 0.35f
                        alpha = split
                    },
            )
        }
    }
}

@Composable
private fun SplitCameraOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_s)),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            Column {
                AppText(text = title, style = MaterialTheme.typography.labelLarge)
                AppText(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * ACTION_IMAGE_CAPTURE 带 URI 授权：
 * 官方 [ActivityResultContracts.TakePicture] 不会给 EXTRA_OUTPUT 的 FileProvider URI 加授权标志，
 * 部分系统相机会因此写入失败；这里显式带上读写授权，兼容面更广。
 */
private class TakePictureWithGrant : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, input)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}

/**
 * 白底纸张预览：空态显示引导文案；多页时可左右滑动（与打印顺序一致），
 * 仅渲染当前页真实位图，其它页显示页码占位，保证上百页也不卡顿/不 OOM。
 *
 * 预览区同时是「编辑器」的入口：右上角浮动「编辑 / 全屏」，进入编辑后切换为
 * [LayerEditorViewport]（画布 + 图片图层）。编辑模式的高度与普通预览不同，
 * 通过 [animateDpAsState] 平滑过渡，避免布局跳动。
 */
@Composable
private fun PreviewPaper(
    pages: List<ImagePageInfo>,
    currentIndex: Int,
    previewBitmap: android.graphics.Bitmap?,
    isPreparing: Boolean,
    editorOpen: Boolean,
    editorLayerBitmap: android.graphics.Bitmap?,
    editTransform: PageLayerTransform,
    onPageChange: (Int) -> Unit,
    onOpenEditor: () -> Unit,
    onCloseEditor: () -> Unit,
    onEditTransformChange: (PageLayerTransform) -> Unit,
) {
    val glassActive = rememberLiquidGlassActive()
    var fullscreen by remember { mutableStateOf(false) }
    val editorState = remember(currentIndex, editorOpen) { PageLayerEditorState(editTransform) }
    // 编辑过程中的参数变化实时回写 ViewModel（退出时才真正合成，避免每帧重渲染）。
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
        label = "imagePreviewHeight",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(previewHeight)
            // 预览区随页面滚动，走真玻璃会因背景纹理滞后一帧而在滑动时抖动 → 固定用伪玻璃。
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
            EditorPanel(
                layerBitmap = editorLayerBitmap,
                editorState = editorState,
                onDone = onCloseEditor,
            )
        } else if (pages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppText(
                    text = stringResource(R.string.image_preview_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // 页数变化（删除/新增）时重建 pager，避免删到末页后停留在越界页号。
                androidx.compose.runtime.key(pages.size) {
                    val pagerState = rememberPagerState(
                        initialPage = currentIndex.coerceIn(0, pages.lastIndex),
                        pageCount = { pages.size },
                    )
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.settledPage }
                            .distinctUntilChanged()
                            .collect(onPageChange)
                    }
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (page == currentIndex && previewBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    // 大图缩小显示必须用 High（mipmap 三线性）：默认 Low 双线性
                                    // 在 6~8 倍下采样时严重走样，高清图预览成"马赛克"。
                                    Image(
                                        bitmap = previewBitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.image_title),
                                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
                                    )
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (page == currentIndex && isPreparing) {
                                        AppLoadingIndicator(size = 28.dp)
                                        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                                    }
                                    AppText(
                                        text = stringResource(R.string.image_page_indicator, page + 1, pages.size),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                // 浮动操作条：近不透明底，避免压在纸张图形上看不清。
                PreviewActions(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
                    canEdit = currentIndex in pages.indices,
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

/** 预览区浮动「编辑 / 全屏」按钮组。 */
@Composable
private fun PreviewActions(
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
                Text(text = stringResource(R.string.image_action_edit))
            }
            TextButton(onClick = onFullscreen, enabled = canFullscreen) {
                Icon(
                    imageVector = Icons.Filled.Fullscreen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.image_action_fullscreen))
            }
        }
    }
}

/** 编辑模式：工具条 + 画布视口 + 完成入口。 */
@Composable
private fun EditorPanel(
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
                text = stringResource(R.string.image_editor_title),
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
                Text(text = stringResource(R.string.image_editor_done))
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
            text = stringResource(R.string.image_editor_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        )
    }
}

/** 算法选择（全部 12 种可达）+ 算法说明 + 阈值/旋转滑杆。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlgorithmControls(
    algorithm: BinarizeAlgorithm,
    threshold: Int,
    rotationDegrees: Int,
    onAlgorithmChange: (BinarizeAlgorithm) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onRotationChange: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
        ) {
            AppText(
                text = stringResource(R.string.image_section_algorithm),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))

            // 常用算法直接展示，其余收进「更多算法」：12 个 chip 全部平铺会让面板过密。
            var showAll by remember { mutableStateOf(false) }
            val allAlgorithms = listOf(
                BinarizeAlgorithm.Auto,
                BinarizeAlgorithm.StructureAware,
                BinarizeAlgorithm.FloydSteinberg,
                BinarizeAlgorithm.JarvisJudiceNinke,
                BinarizeAlgorithm.Stucki,
                BinarizeAlgorithm.Atkinson,
                BinarizeAlgorithm.BayerOrdered,
                BinarizeAlgorithm.BlueNoise,
                BinarizeAlgorithm.OtsuThreshold,
                BinarizeAlgorithm.FixedThreshold,
                BinarizeAlgorithm.SauvolaAdaptive,
                BinarizeAlgorithm.SobelEdge,
            )
            // 当前选中的算法必须始终可见，否则收起时会看不到选中项。
            val visibleAlgorithms = if (showAll) {
                allAlgorithms
            } else {
                allAlgorithms.take(6).let { if (algorithm in it) it else it + algorithm }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
            ) {
                visibleAlgorithms.forEach { option ->
                    AppFilterChip(
                        selected = algorithm == option,
                        onClick = { onAlgorithmChange(option) },
                        label = stringResource(algorithmLabelRes(option)),
                    )
                }
                AppFilterChip(
                    selected = false,
                    onClick = { showAll = !showAll },
                    label = stringResource(
                        if (showAll) R.string.image_alg_less else R.string.image_alg_more,
                    ),
                )
            }
            AppText(
                text = stringResource(algorithmDescRes(algorithm)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
            )

            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))

            LabeledSlider(
                label = stringResource(R.string.image_label_threshold, threshold),
                value = threshold.toFloat(),
                valueRange = ImageProcessOptions.MIN_THRESHOLD.toFloat()..ImageProcessOptions.MAX_THRESHOLD.toFloat(),
                onValueChange = onThresholdChange,
            )
            LabeledSlider(
                label = stringResource(R.string.image_label_rotation, rotationDegrees),
                value = rotationDegrees.toFloat(),
                valueRange = 0f..ImageProcessOptions.ROTATION_STEPS.last().toFloat(),
                steps = ImageProcessOptions.ROTATION_STEPS.size - 2,
                onValueChange = onRotationChange,
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AppText(text = label, style = MaterialTheme.typography.titleMedium)
        AppSlider(
            value = value,
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BatchSwitchCard(
    enabled: Boolean,
    copies: Int,
    onEnabledChange: (Boolean) -> Unit,
    onCopiesChange: (Int) -> Unit,
) {
    val glassActive = rememberLiquidGlassActive()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .nativeGlassSurface(
                shape = MaterialTheme.shapes.medium,
                enabled = glassActive,
                fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            .glassAwareOutline(MaterialTheme.shapes.medium, glassActive),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = stringResource(R.string.image_batch_print),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            // 批量 = 打印份数：整批任务重复执行 copies 次（例：3 张 × 2 份 = 6 张）。
            if (enabled) {
                AppSlider(
                    value = copies.toFloat(),
                    onValueChange = { onCopiesChange(it.toInt()) },
                    valueRange = PrintOptions.COPIES_RANGE.first.toFloat()..PrintOptions.COPIES_RANGE.last.toFloat(),
                    steps = PrintOptions.COPIES_RANGE.last - PrintOptions.COPIES_RANGE.first - 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppText(
                    text = stringResource(R.string.image_copies, copies),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                )
            }
        }
    }
}

@Composable
private fun ImageBottomBar(
    connectedName: String?,
    isConnecting: Boolean,
    isPrinting: Boolean,
    isSaving: Boolean,
    hasPages: Boolean,
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
            // 底栏是浮在内容上的玻璃工具条：圆角必须与原生玻璃面同一形状（dimen_shape_corner_l）。
            // 传 0 会让原生层按旧语义走胶囊，窄长栏两端被画成半圆并产生透镜/色散扭曲。
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
                ImagePrintProgressLine(progress)
                Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppPrimaryButton(
                    text = stringResource(R.string.image_action_cancel_print),
                    onClick = onCancelPrint,
                )
            } else {
                // 状态行：连接状态文本（weight 占位）+ 未连接时「去连接打印机」紧凑按钮放其右侧，
                // 不再挤占下方保存/打印按钮（旧版宽药丸会把保存按钮整个顶掉）。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = when {
                            isConnecting -> stringResource(R.string.image_connecting)
                            connectedName != null -> stringResource(R.string.image_connected_to, connectedName)
                            else -> stringResource(R.string.image_device_not_selected)
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
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.image_action_go_connect),
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
                    // 保存按钮始终可见：未连接打印机也能把处理后的图片存到相册。
                    AppOutlinedButton(
                        text = stringResource(R.string.image_action_save),
                        onClick = onSave,
                        enabled = hasPages && !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(dimensionResource(ResR.dimen.dimen_global_btn_height_min)),
                        leadingIcon = Icons.Filled.Download,
                    )
                    AppPrimaryButton(
                        text = if (isSaving) {
                            stringResource(R.string.image_saving)
                        } else {
                            stringResource(R.string.image_action_print)
                        },
                        onClick = onPrint,
                        enabled = canPrint && !isSaving,
                        leadingIcon = Icons.Filled.Print,
                        modifier = Modifier.weight(1.6f),
                    )
                    // 右侧 1:1 图标按钮：仅打印当前页一张，不受批量开关影响。
                    AppIconButton(
                        onClick = onPrintSingle,
                        imageVector = Icons.Filled.Filter1,
                        contentDescription = stringResource(R.string.image_action_print_single),
                        enabled = canPrint && !isSaving,
                        size = dimensionResource(ResR.dimen.dimen_global_btn_height_min),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePrintProgressLine(progress: PrintProgress?) {
    val phase = progress?.phase
    AppText(
        text = when (phase) {
            PrintPhase.CoolingDown -> stringResource(
                R.string.image_progress_cooling,
                ((progress?.coolingRemainingMs ?: 0L) / 1000L).toInt() + 1,
            )
            PrintPhase.Printing -> stringResource(
                R.string.image_progress_printing,
                (progress?.segmentIndex ?: 0) + 1,
                progress?.segmentCount?.coerceAtLeast(1) ?: 1,
            )
            PrintPhase.Finishing -> stringResource(R.string.image_progress_finishing)
            else -> stringResource(R.string.image_progress_preparing)
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
            label = "imagePrintProgress",
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

private fun algorithmLabelRes(algorithm: BinarizeAlgorithm): Int = when (algorithm) {
    BinarizeAlgorithm.Auto -> R.string.image_alg_auto
    BinarizeAlgorithm.FixedThreshold -> R.string.image_alg_none
    BinarizeAlgorithm.OtsuThreshold -> R.string.image_alg_otsu
    BinarizeAlgorithm.FloydSteinberg -> R.string.image_alg_floyd
    BinarizeAlgorithm.JarvisJudiceNinke -> R.string.image_alg_jjn
    BinarizeAlgorithm.Stucki -> R.string.image_alg_stucki
    BinarizeAlgorithm.Atkinson -> R.string.image_alg_atkinson
    BinarizeAlgorithm.StructureAware -> R.string.image_alg_structure
    BinarizeAlgorithm.BayerOrdered -> R.string.image_alg_bayer
    BinarizeAlgorithm.BlueNoise -> R.string.image_alg_bluenoise
    BinarizeAlgorithm.SobelEdge -> R.string.image_alg_sobel
    BinarizeAlgorithm.SauvolaAdaptive -> R.string.image_alg_sauvola
}

private fun algorithmDescRes(algorithm: BinarizeAlgorithm): Int = when (algorithm) {
    BinarizeAlgorithm.Auto -> R.string.image_alg_desc_auto
    BinarizeAlgorithm.FixedThreshold -> R.string.image_alg_desc_none
    BinarizeAlgorithm.OtsuThreshold -> R.string.image_alg_desc_otsu
    BinarizeAlgorithm.FloydSteinberg -> R.string.image_alg_desc_floyd
    BinarizeAlgorithm.JarvisJudiceNinke -> R.string.image_alg_desc_jjn
    BinarizeAlgorithm.Stucki -> R.string.image_alg_desc_stucki
    BinarizeAlgorithm.Atkinson -> R.string.image_alg_desc_atkinson
    BinarizeAlgorithm.StructureAware -> R.string.image_alg_desc_structure
    BinarizeAlgorithm.BayerOrdered -> R.string.image_alg_desc_bayer
    BinarizeAlgorithm.BlueNoise -> R.string.image_alg_desc_bluenoise
    BinarizeAlgorithm.SobelEdge -> R.string.image_alg_desc_sobel
    BinarizeAlgorithm.SauvolaAdaptive -> R.string.image_alg_desc_sauvola
}

private fun printErrorRes(error: PrintError): Int = when (error) {
    PrintError.NotConnected -> R.string.image_err_not_connected
    PrintError.Permission -> R.string.image_err_permission
    PrintError.Timeout -> R.string.image_err_timeout
    PrintError.WriteFailed -> R.string.image_err_write_failed
    PrintError.RenderFailed -> R.string.image_err_render_failed
    PrintError.Cancelled -> R.string.image_err_cancelled
    PrintError.Generic -> R.string.image_err_generic
}

private fun printerStatusRes(status: PrinterStatus): Int = when (status) {
    PrinterStatus.OutOfPaper -> R.string.image_status_out_of_paper
    PrinterStatus.CoverOpen -> R.string.image_status_cover_open
    PrinterStatus.Overheat -> R.string.image_status_overheat
    PrinterStatus.LowBattery -> R.string.image_status_low_battery
}

/**
 * 打印机过热冷却提示：展示预计剩余秒数，可关闭；
 * 冷却结束前打印按钮点击只会重新弹出本提示，不会重复下单。
 */
@Composable
private fun OverheatDialog(
    remainingMs: Long,
    onDismiss: () -> Unit,
) {
    LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
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
}

private const val GALLERY_MAX_ITEMS = 100

@Preview(showBackground = true)
@Composable
private fun ImageEditorScreenPreview() {
    ImageEditorScreen(
        state = ImageEditorUiState(),
        onBack = {},
        onGoConnect = {},
        onOpenPixivGallery = {},
        onImportGallery = { _, _ -> },
        onCamera = {},
        onSystemCamera = {},
        onScanner = {},
        onImportPdf = {},
        onImportArchive = {},
        onPageChange = {},
        onRemoveCurrent = {},
        onClearAll = {},
        onAlgorithmChange = {},
        onThresholdChange = {},
        onRotationChange = {},
        onBatchChange = {},
        onCopiesChange = {},
        onResetPageOptions = {},
        onOpenEditor = {},
        onCloseEditor = {},
        onEditTransformChange = {},
        onSave = {},
        onPrint = {},
        onPrintSingle = {},
        onCancelPrint = {},
        onClearResult = {},
        onClearNotice = {},
    )
}
