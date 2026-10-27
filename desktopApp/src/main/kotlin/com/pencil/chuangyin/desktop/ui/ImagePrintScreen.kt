package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.glass.glassAwareOutline
import com.pencil.chuangyin.desktop.glass.glassSurface
import com.pencil.chuangyin.desktop.image.BinarizeAlgorithm
import com.pencil.chuangyin.desktop.image.ImageEditorStore
import com.pencil.chuangyin.desktop.image.ImageNotice
import com.pencil.chuangyin.desktop.image.ImagePageInfo
import com.pencil.chuangyin.desktop.image.ImageProcessOptions
import com.pencil.chuangyin.desktop.image.PickItem
import com.pencil.chuangyin.desktop.image.PickKind
import com.pencil.chuangyin.desktop.image.PickUiState
import com.pencil.chuangyin.desktop.navigation.DesktopNavigator
import com.pencil.chuangyin.desktop.text.PrintError
import com.pencil.chuangyin.desktop.text.PrintOptions
import com.pencil.chuangyin.desktop.text.PrintPhase
import com.pencil.chuangyin.desktop.text.PrintProgress
import com.pencil.chuangyin.desktop.text.PrintResult
import com.pencil.chuangyin.desktop.text.PrinterStatus
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import com.pencil.chuangyin.desktop.ui.motion.DesktopLoadingIndicator
import com.pencil.chuangyin.desktop.ui.motion.animateGridItemMotion
import com.pencil.chuangyin.desktop.ui.motion.animatedProgress
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.flow.distinctUntilChanged

// 尺寸对齐安卓端 dimens.xml（dimen_global_padding_s/m/l、dimen_global_btn_height_min、
// dimen_global_touch_target、dimen_shape_corner_s/m/l、dimen_image_preview_height）。
private val PADDING_S = 8.dp
private val PADDING_M = 16.dp
private val PADDING_L = 24.dp
private val TOUCH_TARGET = 48.dp
private val SHAPE_S = 8.dp
private val SHAPE_M = 16.dp
private val PREVIEW_HEIGHT = 380.dp
private val BOTTOM_BAR_CORNER = 28.dp
private val BUTTON_HEIGHT = 56.dp

/** 内容区最大宽度：桌面大窗口下保持安卓平板形态的阅读行宽，不做重排。 */
private val CONTENT_MAX_WIDTH = 960.dp

/** 选择页网格列数（与安卓端一致）。 */
private const val PICK_GRID_COLUMNS = 3

/**
 * 图片打印页 —— 逐条复刻安卓端 `feature/image/ImageEditorScreen.kt` + `ImagePickScreen.kt`：
 * 2×2 来源入口 → 页码/删除/清空 → 逐页参数覆盖提示 → 白纸预览（可翻页）→
 * 抖动算法 + 阈值/旋转 → 批量份数 → 底部玻璃操作条；PDF/压缩包导入后进入通用选择页。
 *
 * 桌面端与安卓端共用同一套蓝牙热敏打印链路：连接态由 PrinterConnectionStore 镜像，
 * 未连接时底栏呈现「未连接 + 去连接打印机」，点击直接打开「连接打印机」页。
 * 拍照入口在桌面端无硬件，改为提示使用本地图片。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImagePrintScreen(onBack: () -> Unit) {
    val state by ImageEditorStore.state.collectAsState()
    val pick = state.pick
    if (pick != null) {
        ImagePickContent(
            pick = pick,
            onBack = { ImageEditorStore.cancelPick() },
            onToggle = { ImageEditorStore.togglePickSelection(it) },
            onToggleSelectAll = { ImageEditorStore.togglePickSelectAll() },
            onConfirm = { ImageEditorStore.confirmPick() },
            onEnsureThumbnail = { ImageEditorStore.ensurePickThumbnail(it) },
        )
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var cameraHint by remember { mutableStateOf(false) }
    var overheatDismissed by remember { mutableStateOf(false) }

    val cooling = state.isPrinting && state.overheatActive
    // 过热冷却期间打印按钮保持可点：点击不重复下单，仅重新弹出冷却提示。
    val canPrint = state.connectedName != null && (!state.isPrinting || cooling)
    LaunchedEffect(cooling) { if (cooling) overheatDismissed = false }
    val onPrintGuarded: () -> Unit = {
        if (cooling) overheatDismissed = false else ImageEditorStore.print()
    }

    val statusMessage = state.printerStatus?.let { printerStatusText(it) }
    val noticeMessage = when (val notice = state.notice) {
        is ImageNotice.Added -> "已添加 ${notice.pages} 页"
        is ImageNotice.Saved -> "已保存 ${notice.count} 张到输出目录"
        ImageNotice.ArchiveEmpty -> "压缩包中未找到图片"
        ImageNotice.PdfFailed -> "PDF 打开失败"
        ImageNotice.ImportFailed -> "导入失败，请重试"
        ImageNotice.SaveFailed -> "保存失败，请重试"
        null -> null
    }
    val errorResult = state.lastResult as? PrintResult.Error

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            ImageEditorStore.clearPrinterStatus()
        }
    }
    LaunchedEffect(noticeMessage) {
        noticeMessage?.let {
            snackbarHostState.showSnackbar(it)
            ImageEditorStore.clearNotice()
        }
    }

    if (errorResult != null) {
        val detail = errorResult.detail
        AlertDialog(
            onDismissRequest = { ImageEditorStore.clearResult() },
            title = { Text("打印失败") },
            text = {
                Text(
                    if (!detail.isNullOrBlank()) {
                        printErrorText(errorResult.code) + "\n\n技术信息：" + detail
                    } else {
                        printErrorText(errorResult.code)
                    }
                )
            },
            confirmButton = {
                AppTextButton(text = "好的", onClick = { ImageEditorStore.clearResult() })
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    if (cooling && !overheatDismissed) {
        ImageOverheatDialog(
            remainingMs = state.progress?.coolingRemainingMs ?: 0L,
            onDismiss = { overheatDismissed = true },
        )
    }

    if (cameraHint) {
        AlertDialog(
            onDismissRequest = { cameraHint = false },
            title = { Text("拍照") },
            text = { Text("桌面端暂无摄像头拍摄能力，请使用「从相册选」导入本地图片。") },
            confirmButton = {
                AppTextButton(text = "知道了", onClick = { cameraHint = false })
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = "图片打印",
                navigationIcon = {
                    AppIconButton(
                        icon = AppIcon.Back,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            ImageBottomBar(
                connectedName = state.connectedName,
                isConnecting = state.isConnecting,
                isPrinting = state.isPrinting,
                isSaving = state.isSaving,
                hasPages = state.pages.isNotEmpty(),
                progress = state.progress,
                canPrint = canPrint,
                onGoConnect = { DesktopNavigator.navigate(DesktopNavigator.Page.Connect) },
                onSave = { ImageEditorStore.save() },
                onPrint = onPrintGuarded,
                onPrintSingle = { ImageEditorStore.printSingle() },
                onCancelPrint = { ImageEditorStore.cancelPrint() },
            )
        },
        containerColor = Color.Transparent,
    ) { inner ->
        Column(
            modifier = Modifier
                // 只避开顶栏：底部 inset 转为滚动内容的尾部留白，使页面内容可以绘制到
                // 底栏玻璃之后（与安卓端一致）。
                .padding(top = inner.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PADDING_M)
                .padding(
                    top = PADDING_M,
                    bottom = inner.calculateBottomPadding() + PADDING_M,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth()) {
                SourceButtonRow(
                    first = SourceAction(AppIcon.ImageTool, "从相册选") {
                        val files = chooseImageFiles()
                        if (files.isNotEmpty()) ImageEditorStore.importGallery(files)
                    },
                    second = SourceAction(AppIcon.CameraTool, "拍照") { cameraHint = true },
                )
                Spacer(modifier = Modifier.height(PADDING_S))
                SourceButtonRow(
                    first = SourceAction(AppIcon.PicturePdf, "导入PDF") {
                        choosePdfFile()?.let { ImageEditorStore.importPdf(it) }
                    },
                    second = SourceAction(AppIcon.FolderZip, "导入压缩包") {
                        chooseArchiveFile()?.let { ImageEditorStore.importArchive(it) }
                    },
                )
                Spacer(modifier = Modifier.height(PADDING_M))

                if (state.pages.isNotEmpty()) {
                    PageActionRow(
                        currentIndex = state.currentIndex,
                        pageCount = state.pages.size,
                        onRemoveCurrent = { ImageEditorStore.removeCurrentPage() },
                        onClearAll = { ImageEditorStore.clearAll() },
                    )
                    if (state.currentPageCustomized) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "本页已单独调整",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            AppTextButton(
                                text = "恢复沿用首页参数",
                                onClick = { ImageEditorStore.resetPageOptions() },
                            )
                        }
                    }
                }

                PreviewPaper(
                    pages = state.pages,
                    currentIndex = state.currentIndex,
                    previewBitmap = state.previewBitmap,
                    isPreparing = state.isPreparing,
                    onPageChange = { ImageEditorStore.setCurrentPage(it) },
                )

                Spacer(modifier = Modifier.height(PADDING_L))

                AlgorithmControls(
                    algorithm = state.processOptions.algorithm,
                    threshold = state.processOptions.threshold,
                    rotationDegrees = state.processOptions.rotationDegrees,
                    onAlgorithmChange = { ImageEditorStore.setAlgorithm(it) },
                    onThresholdChange = { ImageEditorStore.setThreshold(it) },
                    onRotationChange = { ImageEditorStore.setRotation(it) },
                )

                Spacer(modifier = Modifier.height(PADDING_M))

                BatchSwitchCard(
                    enabled = state.batchEnabled,
                    copies = state.copies,
                    onEnabledChange = { ImageEditorStore.setBatchEnabled(it) },
                    onCopiesChange = { ImageEditorStore.setCopies(it) },
                )
                Spacer(modifier = Modifier.height(PADDING_S))
            }
        }
    }
}

// ===================== 来源入口 =====================

private class SourceAction(
    val icon: AppIcon,
    val text: String,
    val onClick: () -> Unit,
)

@Composable
private fun SourceButtonRow(first: SourceAction, second: SourceAction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PADDING_S),
    ) {
        SourceButton(action = first, modifier = Modifier.weight(1f))
        SourceButton(action = second, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SourceButton(action: SourceAction, modifier: Modifier = Modifier) {
    AppOutlinedButton(
        text = action.text,
        onClick = action.onClick,
        modifier = modifier.height(BUTTON_HEIGHT),
        leadingIcon = action.icon,
        height = BUTTON_HEIGHT,
    )
}

// ===================== 页码操作 =====================

@Composable
private fun PageActionRow(
    currentIndex: Int,
    pageCount: Int,
    onRemoveCurrent: () -> Unit,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "第 ${currentIndex + 1} / $pageCount 页",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        // 删除：仅移除当前预览页一张；清空：移除全部页。
        AppTextButton(text = "删除", onClick = onRemoveCurrent)
        AppTextButton(text = "清空", onClick = onClearAll)
    }
}

// ===================== 预览 =====================

/**
 * 白底纸张预览：空态显示引导文案；多页时可左右滑动（与打印顺序一致），
 * 仅渲染当前页真实位图，其它页显示页码占位，保证上百页也不卡顿/不 OOM。
 */
@Composable
private fun PreviewPaper(
    pages: List<ImagePageInfo>,
    currentIndex: Int,
    previewBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    isPreparing: Boolean,
    onPageChange: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(SHAPE_M)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT)
            // 预览区随页面滚动，玻璃材质固定在伪玻璃（与安卓端一致，避免滑动时纹理滞后）。
            .glassSurface(
                shape = shape,
                enabled = false,
                fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            .glassAwareOutline(shape, glassActive = false),
        shape = shape,
        color = Color.Transparent,
    ) {
        if (pages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "选择图片后显示打印预览",
                    color = Color.Gray,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            // 页数变化（删除/新增）时重建 pager，避免删到末页后停留在越界页号。
            key(pages.size) {
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
                                // 大图缩小显示必须用 High（mipmap 三线性）：默认双线性在
                                // 6~8 倍下采样时严重走样，高清图预览成「马赛克」。
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription = "打印预览",
                                    contentScale = ContentScale.FillWidth,
                                    filterQuality = FilterQuality.High,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(PADDING_S),
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (page == currentIndex && isPreparing) {
                                    DesktopLoadingIndicator(size = 28.dp)
                                    Spacer(modifier = Modifier.height(PADDING_S))
                                }
                                Text(
                                    text = "第 ${page + 1} / ${pages.size} 页",
                                    color = Color.Gray,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== 算法与参数 =====================

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
    Text(
        text = "抖动算法",
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(modifier = Modifier.height(PADDING_S))

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PADDING_S),
    ) {
        ALGORITHMS.forEach { option ->
            AppFilterChip(
                selected = algorithm == option,
                onClick = { onAlgorithmChange(option) },
                label = algorithmLabel(option),
            )
        }
    }
    Text(
        text = algorithmDescription(algorithm),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = PADDING_S),
    )

    Spacer(modifier = Modifier.height(PADDING_M))

    LabeledSlider(
        label = "二值化阈值: $threshold",
        value = threshold.toFloat(),
        valueRange = ImageProcessOptions.MIN_THRESHOLD.toFloat()..ImageProcessOptions.MAX_THRESHOLD.toFloat(),
        onValueChange = onThresholdChange,
    )
    LabeledSlider(
        label = "旋转角度: $rotationDegrees°",
        value = rotationDegrees.toFloat(),
        valueRange = 0f..ImageProcessOptions.ROTATION_STEPS.last().toFloat(),
        steps = ImageProcessOptions.ROTATION_STEPS.size - 2,
        onValueChange = onRotationChange,
    )
}

private val ALGORITHMS = listOf(
    BinarizeAlgorithm.FixedThreshold,
    BinarizeAlgorithm.FloydSteinberg,
    BinarizeAlgorithm.Atkinson,
    BinarizeAlgorithm.SobelEdge,
    BinarizeAlgorithm.SauvolaAdaptive,
)

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
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
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = shape,
                fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            .glassAwareOutline(shape),
        shape = shape,
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PADDING_M),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "批量打印",
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
                    valueRange = PrintOptions.COPIES_RANGE.first.toFloat()..
                        PrintOptions.COPIES_RANGE.last.toFloat(),
                    steps = PrintOptions.COPIES_RANGE.last - PrintOptions.COPIES_RANGE.first - 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "份数: $copies",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = PADDING_S),
                )
            }
        }
    }
}

// ===================== 底栏 =====================

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
    // 底栏是浮在内容上的玻璃工具条：圆角与安卓端 dimen_shape_corner_l(28dp) 一致。
    val shape = RoundedCornerShape(BOTTOM_BAR_CORNER)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = shape,
                fallbackColor = MaterialTheme.colorScheme.surface,
            )
            .glassAwareOutline(shape)
            .padding(PADDING_M),
    ) {
        if (isPrinting) {
            ImagePrintProgressLine(progress)
            Spacer(modifier = Modifier.height(PADDING_S))
            AppPrimaryButton(text = "取消打印", onClick = onCancelPrint)
        } else {
            // 状态行：连接状态文本（weight 占位）+ 未连接时「去连接打印机」紧凑按钮放其右侧，
            // 不再挤占下方保存/打印按钮。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        isConnecting -> "连接中…"
                        connectedName != null -> "已连接：$connectedName"
                        else -> "未连接打印机"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (connectedName != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                if (connectedName == null) {
                    AppTextButton(
                        text = "去连接打印机",
                        onClick = onGoConnect,
                        enabled = !isConnecting,
                    )
                }
            }
            Spacer(modifier = Modifier.height(PADDING_S))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PADDING_M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppOutlinedButton(
                    text = "保存",
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = hasPages && !isSaving,
                    leadingIcon = AppIcon.Download,
                    height = BUTTON_HEIGHT,
                )
                AppPrimaryButton(
                    text = if (isSaving) "正在保存…" else "打印",
                    onClick = onPrint,
                    modifier = Modifier.weight(1.6f),
                    enabled = canPrint && !isSaving,
                    leadingIcon = AppIcon.Printer,
                    height = BUTTON_HEIGHT,
                )
                AppIconButton(
                    icon = AppIcon.FilterOne,
                    contentDescription = "单张打印",
                    onClick = onPrintSingle,
                    enabled = canPrint && !isSaving,
                    size = BUTTON_HEIGHT,
                )
            }
        }
    }
}

@Composable
private fun ImagePrintProgressLine(progress: PrintProgress?) {
    val phase = progress?.phase
    Text(
        text = when (phase) {
            PrintPhase.CoolingDown ->
                "过热保护冷却中，剩余约 ${((progress?.coolingRemainingMs ?: 0L) / 1000L).toInt() + 1} 秒；冷却结束将从断点无缝续打"

            PrintPhase.Printing ->
                "正在打印：第 ${(progress?.segmentIndex ?: 0) + 1}/${progress?.segmentCount ?: 1} 页"

            PrintPhase.Finishing -> "收尾走纸中…"
            else -> "正在准备打印…"
        },
        style = MaterialTheme.typography.bodyMedium,
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
            val total = progress?.segmentCount?.coerceAtLeast(1) ?: 1
            (((progress?.segmentIndex ?: 0) + 1).toFloat() / total).coerceIn(0f, 1f)
        }
        val animated = animatedProgress(target, "imagePrintProgress")
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PADDING_S),
        )
    } else {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PADDING_S),
        )
    }
}

// ===================== 选择页（PDF 页码 / 压缩包图片） =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImagePickContent(
    pick: PickUiState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onConfirm: () -> Unit,
    onEnsureThumbnail: (String) -> Unit,
) {
    val allSelected = pick.items.isNotEmpty() && pick.items.all { it.key in pick.selected }
    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = when (pick.kind) {
                    PickKind.PDF -> "选择PDF页面"
                    PickKind.ARCHIVE -> "选择压缩包中的图片"
                },
                navigationIcon = {
                    AppIconButton(
                        icon = AppIcon.Back,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
            )
        },
        bottomBar = {
            val shape = RoundedCornerShape(0.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(
                        shape = shape,
                        fallbackColor = MaterialTheme.colorScheme.surface,
                    )
                    .padding(PADDING_M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(
                    text = if (allSelected) "取消全选" else "全选",
                    onClick = onToggleSelectAll,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "已选择 ${pick.selected.size}/${pick.items.size} 张",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                AppOutlinedButton(
                    text = "添加",
                    onClick = onConfirm,
                    enabled = pick.selected.isNotEmpty(),
                )
            }
        },
        containerColor = Color.Transparent,
    ) { inner ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(PICK_GRID_COLUMNS),
            modifier = Modifier
                .padding(inner)
                .fillMaxSize(),
            contentPadding = PaddingValues(PADDING_M),
            horizontalArrangement = Arrangement.spacedBy(PADDING_S),
            verticalArrangement = Arrangement.spacedBy(PADDING_S),
        ) {
            items(items = pick.items, key = { it.key }) { item ->
                PickCell(
                    item = item,
                    selected = item.key in pick.selected,
                    thumbnail = pick.thumbnails[item.key],
                    onToggle = { onToggle(item.key) },
                    onEnsureThumbnail = { onEnsureThumbnail(item.key) },
                    modifier = animateGridItemMotion(),
                )
            }
        }
    }
}

@Composable
private fun PickCell(
    item: PickItem,
    selected: Boolean,
    thumbnail: androidx.compose.ui.graphics.ImageBitmap?,
    onToggle: () -> Unit,
    onEnsureThumbnail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(item.key) { onEnsureThumbnail() }
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(SHAPE_S))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                DesktopLoadingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    size = TOUCH_TARGET * 0.6f,
                )
            }
            // 选中角标（截图同款：右上角主色底白勾）。
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(PADDING_S)
                        .size(TOUCH_TARGET * 0.6f)
                        .clip(RoundedCornerShape(SHAPE_S))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIconView(
                        icon = AppIcon.Check,
                        // 与安卓端 Icon 默认尺寸一致（24dp，未显式指定 size）。
                        size = 24.dp,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Text(
            text = pickItemLabel(item),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = PADDING_S / 2),
        )
    }
}

private fun pickItemLabel(item: PickItem): String = when (item) {
    is PickItem.PdfPage -> "第${item.pageIndex + 1}页"
    is PickItem.ArchiveImage -> item.path
}

// ===================== 文案映射 =====================

private fun algorithmLabel(algorithm: BinarizeAlgorithm): String = when (algorithm) {
    BinarizeAlgorithm.FixedThreshold -> "无"
    BinarizeAlgorithm.FloydSteinberg -> "Floyd"
    BinarizeAlgorithm.Atkinson -> "Atkinson"
    BinarizeAlgorithm.SobelEdge -> "Sobel"
    BinarizeAlgorithm.SauvolaAdaptive -> "Sauvola"
}

private fun algorithmDescription(algorithm: BinarizeAlgorithm): String = when (algorithm) {
    BinarizeAlgorithm.FixedThreshold -> "固定阈值二值化 · 线稿/文字最锐利"
    BinarizeAlgorithm.FloydSteinberg -> "Floyd–Steinberg 误差扩散 · 照片灰阶自然"
    BinarizeAlgorithm.Atkinson -> "Atkinson 误差扩散 · 高对比、高光干净"
    BinarizeAlgorithm.SobelEdge -> "Sobel 算子边缘检测 · 黑白线稿"
    BinarizeAlgorithm.SauvolaAdaptive -> "Sauvola 局部自适应阈值 · 文档/试卷去阴影"
}

private fun printErrorText(error: PrintError): String = when (error) {
    PrintError.NotConnected -> "未连接打印机"
    PrintError.Permission -> "缺少蓝牙权限"
    PrintError.Timeout -> "连接超时，请靠近打印机后重试"
    PrintError.WriteFailed -> "数据写入失败"
    PrintError.RenderFailed -> "图片处理失败"
    PrintError.Cancelled -> "打印已取消"
    PrintError.Generic -> "打印出错，请重试"
}

private fun printerStatusText(status: PrinterStatus): String = when (status) {
    PrinterStatus.OutOfPaper -> "打印机缺纸"
    PrinterStatus.CoverOpen -> "打印机开盖"
    PrinterStatus.Overheat -> "打印机过热"
    PrinterStatus.LowBattery -> "打印机电量低"
}

/**
 * 打印机过热冷却提示：展示预计剩余秒数，可关闭；
 * 冷却结束前打印按钮点击只会重新弹出本提示，不会重复下单。
 */
@Composable
private fun ImageOverheatDialog(remainingMs: Long, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            AppIconView(
                icon = AppIcon.Thermometer,
                size = 24.dp,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("打印机过热") },
        text = {
            Text(
                if (remainingMs > 0L) {
                    "打印机正在冷却，预计还需 ${((remainingMs + 999) / 1000L).toInt()} 秒；" +
                        "降温后将自动继续打印，无需重新开始。"
                } else {
                    "打印机正在冷却；降温后将自动继续打印，无需重新开始。"
                }
            )
        },
        confirmButton = { AppTextButton(text = "好的", onClick = onDismiss) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

// ===================== 文件选择（替代安卓端系统选择器） =====================

private fun chooseImageFiles(): List<File> {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择图片（可多选）"
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = true
        isAcceptAllFileFilterUsed = false
        fileFilter = FileNameExtensionFilter(
            "图片文件 (*.jpg, *.jpeg, *.png, *.webp, *.bmp, *.gif)",
            "jpg", "jpeg", "png", "webp", "bmp", "gif",
        )
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.toList()
    } else {
        emptyList()
    }
}

private fun choosePdfFile(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择 PDF 文件"
        fileSelectionMode = JFileChooser.FILES_ONLY
        isAcceptAllFileFilterUsed = false
        fileFilter = FileNameExtensionFilter("PDF 文档 (*.pdf)", "pdf")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

private fun chooseArchiveFile(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择图片压缩包"
        fileSelectionMode = JFileChooser.FILES_ONLY
        isAcceptAllFileFilterUsed = false
        fileFilter = FileNameExtensionFilter("ZIP 压缩包 (*.zip)", "zip")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}
