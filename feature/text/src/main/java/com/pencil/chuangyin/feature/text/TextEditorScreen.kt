package com.pencil.chuangyin.feature.text

import android.content.ContentResolver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.theme.glassAwareOutline
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.ui.components.AppAlertDialog
import com.pencil.chuangyin.core.ui.components.AppLiquidDialog
import com.pencil.chuangyin.core.ui.components.AppDivider
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSecondaryButton
import com.pencil.chuangyin.core.ui.components.AppSourceAction
import com.pencil.chuangyin.core.ui.components.AppSourceCard
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppSwitch
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.LiquidGlassLayerScope
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.pseudoGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.printer.PrintAlign
import com.pencil.chuangyin.printer.PrintError
import com.pencil.chuangyin.printer.PrintOptions
import com.pencil.chuangyin.printer.PrintPhase
import com.pencil.chuangyin.printer.PrintProgress
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterStatus
import com.pencil.chuangyin.printer.TextDoc
import com.pencil.chuangyin.resources.R as ResR
import com.pencil.chuangyin.feature.text.R

@Composable
fun TextEditorRoute(
    onBack: () -> Unit,
    onGoConnect: () -> Unit,
    onOpenPixivGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TextEditorViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 进入页面时消费一次外部传入文本（P 站小说打印）；无载荷时为空操作。
    LaunchedEffect(Unit) { viewModel.consumeTransferredText() }
    TextEditorScreen(
        state = state,
        onBack = onBack,
        onGoConnect = onGoConnect,
        onOpenPixivGallery = onOpenPixivGallery,
        onTitleChange = viewModel::updateTitle,
        onContentChange = viewModel::updateContent,
        onFontSizeChange = viewModel::updateFontSize,
        onTitleSizeChange = viewModel::updateTitleSize,
        onLineSpacingChange = viewModel::updateLineSpacing,
        onAlignChange = viewModel::updateAlign,
        onBoldChange = viewModel::updateBold,
        onThicknessChange = viewModel::updateThickness,
        onFeedAfterChange = viewModel::updateFeedAfter,
        onBatchChange = viewModel::setBatchEnabled,
        onCopiesChange = viewModel::setCopies,
        onImportTxt = viewModel::importTxt,
        onImportZip = viewModel::importTxtArchive,
        onImportFont = viewModel::importFont,
        onRemoveFile = viewModel::removeMountedFile,
        onMoveFile = viewModel::moveMountedFile,
        onEditorExpandedChange = viewModel::setEditorExpanded,
        onPrint = viewModel::print,
        onCancelPrint = viewModel::cancelPrint,
        onSave = viewModel::savePreview,
        onClearResult = viewModel::clearResult,
        onClearNotice = viewModel::clearNotice,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TextEditorScreen(
    state: TextEditorUiState,
    onBack: () -> Unit,
    onGoConnect: () -> Unit,
    onOpenPixivGallery: () -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onTitleSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onAlignChange: (PrintAlign) -> Unit,
    onBoldChange: (Boolean) -> Unit,
    onThicknessChange: (Int) -> Unit,
    onFeedAfterChange: (Int) -> Unit,
    onBatchChange: (Boolean) -> Unit,
    onCopiesChange: (Int) -> Unit,
    onImportTxt: (List<android.net.Uri>, ContentResolver) -> Unit,
    onImportZip: (android.net.Uri, ContentResolver) -> Unit,
    onImportFont: (android.net.Uri, ContentResolver) -> Unit,
    onRemoveFile: (Int) -> Unit,
    onMoveFile: (Int, Int) -> Unit,
    onEditorExpandedChange: (Boolean) -> Unit,
    onPrint: () -> Unit,
    onCancelPrint: () -> Unit,
    onSave: () -> Unit,
    onClearResult: () -> Unit,
    onClearNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val glassActive = rememberLiquidGlassActive()

    val txtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) onImportTxt(uris, context.contentResolver)
    }
    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportZip(uri, context.contentResolver)
    }
    val fontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportFont(uri, context.contentResolver)
    }

    val cooling = state.isPrinting && state.overheatActive
    // 过热冷却期间打印按钮保持可点：点击不重复下单，仅重新弹出冷却提示。
    val canPrint = state.connectedDevice != null && (!state.isPrinting || cooling)
    var overheatDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(cooling) { if (cooling) overheatDismissed = false }
    val onPrintGuarded: () -> Unit = { if (cooling) overheatDismissed = false else onPrint() }

    val lastMessage = when (val result = state.lastResult) {
        is PrintResult.Success -> stringResource(R.string.text_print_success)
        is PrintResult.Error -> stringResource(printErrorRes(result.code))
        null -> null
    }
    val statusMessage = state.printerStatus?.let { stringResource(printerStatusRes(it)) }
    val noticeMessage = when (val notice = state.notice) {
        is TextNotice.Mounted -> stringResource(R.string.text_notice_mounted, notice.count, notice.chars)
        is TextNotice.ArchiveEmpty -> stringResource(R.string.text_notice_archive_empty)
        is TextNotice.Saved -> stringResource(R.string.text_notice_saved, notice.count)
        is TextNotice.SaveFailed -> stringResource(R.string.text_notice_save_failed)
        null -> null
    }
    val errorResult = state.lastResult as? PrintResult.Error

    LaunchedEffect(lastMessage) {
        // 成功用 Snackbar 轻提示；失败改用对话框持久展示（见下），避免用户错过原因。
        if (state.lastResult is PrintResult.Success) {
            snackbarHostState.showSnackbar(lastMessage ?: "")
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
            stringResource(R.string.text_print_failed_detail, detail)
        } else {
            null
        }
        AppLiquidDialog(
            onDismissRequest = onClearResult,
            title = stringResource(R.string.text_print_failed_title),
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

    if (state.editorExpanded) {
        FullScreenContentEditor(
            value = state.doc.content,
            onValueChange = onContentChange,
            onDismiss = { onEditorExpandedChange(false) },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.text_title)) },
                navigationIcon = {
                    AppIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(ResR.string.label_back),
                    )
                },
                actions = {
                    // 与图片打印页一致：直接从打印页进入 P 站仓库（已保存内容）挑素材。
                    AppIconButton(
                        onClick = onOpenPixivGallery,
                        imageVector = Icons.Filled.Inventory2,
                        contentDescription = stringResource(ResR.string.pixiv_open_repository),
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
            EditorBottomBar(
                connectedName = state.connectedDevice?.name,
                isConnecting = state.isConnecting,
                isPrinting = state.isPrinting,
                progress = state.progress,
                canPrint = canPrint,
                canSave = state.previewBitmap != null && !state.isSaving,
                onGoConnect = onGoConnect,
                onPrint = onPrintGuarded,
                onSave = onSave,
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m))
                .padding(
                    top = dimensionResource(ResR.dimen.dimen_global_padding_m),
                    bottom = innerPadding.calculateBottomPadding() +
                        dimensionResource(ResR.dimen.dimen_global_padding_m),
                ),
        ) {
            // 来源入口：与图片打印页同款的圆角卡片 + 圆形图标按钮（不设大圆「+」）。
            // 导入 TXT / 压缩包 / 字体原先挂在页面底部，需一路下滑才能找到，现上移到标题上方。
            AppSourceCard(
                actions = listOf(
                    AppSourceAction(
                        icon = Icons.AutoMirrored.Filled.TextSnippet,
                        label = stringResource(R.string.text_import_txt),
                        onClick = { txtLauncher.launch(arrayOf("text/plain", "text/*")) },
                    ),
                    AppSourceAction(
                        icon = Icons.Filled.FolderZip,
                        label = stringResource(R.string.text_import_zip),
                        onClick = {
                            zipLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                ),
                            )
                        },
                    ),
                    AppSourceAction(
                        icon = Icons.Filled.TextFields,
                        label = stringResource(R.string.text_import_font),
                        onClick = {
                            fontLauncher.launch(
                                arrayOf(
                                    "application/x-font-ttf", "font/ttf",
                                    "application/x-font-opentype", "font/otf",
                                    "application/octet-stream",
                                ),
                            )
                        },
                    ),
                ),
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))

            AppTextField(
                value = state.doc.title,
                onValueChange = onTitleChange,
                label = stringResource(R.string.text_label_title),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))

            if (state.mountedFiles.isNotEmpty()) {
                MountedFileList(
                    files = state.mountedFiles,
                    onRemove = onRemoveFile,
                    onMove = onMoveFile,
                )
            } else {
                CollapsedContentEditor(
                    value = state.doc.content,
                    charCount = state.totalChars,
                    onValueChange = onContentChange,
                    onExpand = { onEditorExpandedChange(true) },
                )
            }

            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
            AppDivider()
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))

            PrintParameterControls(
                doc = state.doc,
                options = state.options,
                batchEnabled = state.batchEnabled,
                copies = state.copies,
                onAlignChange = onAlignChange,
                onBoldChange = onBoldChange,
                onThicknessChange = onThicknessChange,
                onFeedAfterChange = onFeedAfterChange,
                onBatchChange = onBatchChange,
                onCopiesChange = onCopiesChange,
            )

            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))

            // 排版三项紧贴预览区上方，调整即可看到预览变化。
            TypographyControls(
                doc = state.doc,
                onFontSizeChange = onFontSizeChange,
                onTitleSizeChange = onTitleSizeChange,
                onLineSpacingChange = onLineSpacingChange,
            )
            PreviewCard(
                previewBitmap = state.previewBitmap,
                customFontName = state.customFontName,
                isDocTooLarge = state.isDocTooLargeForPreview,
                totalChars = state.totalChars,
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        }
    }
}

/**
 * 收缩态正文输入框：固定高度、内部上下滚动，再长的文本也不会把页面拖长；
 * 右侧「展开」进入全屏编辑页。
 */
@Composable
private fun CollapsedContentEditor(
    value: String,
    charCount: Int,
    onValueChange: (String) -> Unit,
    onExpand: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(R.string.text_label_content),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            AppText(
                text = stringResource(R.string.text_char_count, charCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onExpand, contentPadding = androidx.compose.foundation.layout.PaddingValues()) {
                Text(text = stringResource(R.string.text_action_expand))
            }
        }
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.text_content_placeholder),
            singleLine = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(ResR.dimen.dimen_text_editor_collapsed_height)),
        )
    }
}

/** 全屏正文编辑页：完整展示可滚动输入框，顶栏完成返回。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenContentEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
        ),
    ) {
        val glassActive = rememberLiquidGlassActive()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            AppText(text = stringResource(R.string.text_editor_expanded_title))
                            AppText(
                                text = stringResource(R.string.text_char_count, value.length),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        AppIconButton(
                            onClick = onDismiss,
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(ResR.string.label_back),
                        )
                    },
                    actions = {
                        AppTextButton(text = stringResource(R.string.text_action_collapse), onClick = onDismiss)
                    },
                    modifier = Modifier.nativeGlassSurface(
                        shape = RoundedCornerShape(0.dp),
                        enabled = glassActive,
                        fallbackColor = MaterialTheme.colorScheme.surface,
                    ),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { innerPadding ->
            AppTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = stringResource(R.string.text_content_placeholder),
                singleLine = false,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .imePadding()
                    .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
            )
        }
    }
}

/** 已挂载文件序列：序号 + 文件名/字数 + 上移/下移调整打印顺序 + 移除。 */
@Composable
private fun MountedFileList(
    files: List<MountedTxtFile>,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val glassActive = rememberLiquidGlassActive()
    Column {
        AppText(
            text = stringResource(R.string.text_mounted_files_label, files.size),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        files.forEachIndexed { index, file ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensionResource(ResR.dimen.dimen_shape_corner_sharp) / 2)
                    .nativeGlassSurface(
                        shape = MaterialTheme.shapes.medium,
                        enabled = glassActive,
                        fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .glassAwareOutline(MaterialTheme.shapes.medium, glassActive),
                shape = MaterialTheme.shapes.medium,
                color = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                            vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = stringResource(R.string.text_mounted_file_index, index + 1),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(start = dimensionResource(ResR.dimen.dimen_global_padding_s))) {
                        AppText(
                            text = file.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                        )
                        AppText(
                            text = stringResource(R.string.text_char_count, file.charCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    AppIconButton(
                        onClick = { onMove(index, -1) },
                        imageVector = Icons.Filled.ArrowDropUp,
                        contentDescription = stringResource(R.string.text_action_move_up),
                        enabled = index > 0,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    AppIconButton(
                        onClick = { onMove(index, 1) },
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = stringResource(R.string.text_action_move_down),
                        enabled = index < files.lastIndex,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    AppIconButton(
                        onClick = { onRemove(index) },
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.text_action_remove_file),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

/** 打印设备侧参数：对齐/加粗/浓度/走纸（字号等排版项移至预览区上方）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrintParameterControls(
    doc: TextDoc,
    options: PrintOptions,
    batchEnabled: Boolean,
    copies: Int,
    onAlignChange: (PrintAlign) -> Unit,
    onBoldChange: (Boolean) -> Unit,
    onThicknessChange: (Int) -> Unit,
    onFeedAfterChange: (Int) -> Unit,
    onBatchChange: (Boolean) -> Unit,
    onCopiesChange: (Int) -> Unit,
) {
    val motion = rememberAppMotion()
    AppText(
        text = stringResource(R.string.text_label_align),
        style = MaterialTheme.typography.titleMedium,
    )
    val aligns = listOf(PrintAlign.Start, PrintAlign.Center, PrintAlign.End)
    AppSegmentedRow(
        options = aligns.map { stringResource(alignLabel(it)) },
        selectedIndex = aligns.indexOf(doc.align).coerceAtLeast(0),
        onSelect = { onAlignChange(aligns[it]) },
        showCheck = true,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_m)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = stringResource(R.string.text_label_bold),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        AppSwitch(checked = doc.bold, onCheckedChange = onBoldChange)
    }

    AppDivider(modifier = Modifier.padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_m)))

    LabeledSlider(
        label = stringResource(R.string.text_label_thickness),
        valueLabel = stringResource(R.string.text_value_level, options.thickness),
        value = options.thickness.toFloat(),
        valueRange = PrintOptions.thicknessRange.first.toFloat()..PrintOptions.thicknessRange.last.toFloat(),
        steps = PrintOptions.thicknessRange.last - PrintOptions.thicknessRange.first - 1,
        onValueChange = { onThicknessChange(it.toInt()) },
    )
    LabeledSlider(
        label = stringResource(R.string.text_label_feed_after),
        valueLabel = stringResource(R.string.text_value_lines, options.feedAfter),
        value = options.feedAfter.toFloat(),
        valueRange = PrintOptions.feedAfterRange.first.toFloat()..PrintOptions.feedAfterRange.last.toFloat(),
        steps = (PrintOptions.feedAfterRange.last - PrintOptions.feedAfterRange.first) / FEED_STEP - 1,
        onValueChange = { onFeedAfterChange(((it + FEED_STEP / 2f) / FEED_STEP).toInt() * FEED_STEP) },
    )

    AppDivider(modifier = Modifier.padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_m)))

    // 批量 = 份数：整批任务重复执行 copies 次，未开启固定 1 份。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = stringResource(R.string.text_label_batch),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        AppSwitch(checked = batchEnabled, onCheckedChange = onBatchChange)
    }
    // 开关打开时份数滑块弹性撑开（收起时快速缩回），避免参数区"啪"地跳高一段。
    AnimatedVisibility(
        visible = batchEnabled,
        enter = motion.expandEnter(),
        exit = motion.shrinkExit(),
    ) {
        LabeledSlider(
            label = stringResource(R.string.text_label_copies),
            valueLabel = stringResource(R.string.text_value_copies, copies),
            value = copies.toFloat(),
            valueRange = PrintOptions.COPIES_RANGE.first.toFloat()..PrintOptions.COPIES_RANGE.last.toFloat(),
            steps = PrintOptions.COPIES_RANGE.last - PrintOptions.COPIES_RANGE.first - 1,
            onValueChange = { onCopiesChange(it.toInt()) },
        )
    }
}

/** 排版三项：字号/标题字号/行距（置于预览正上方）。 */
@Composable
private fun TypographyControls(
    doc: TextDoc,
    onFontSizeChange: (Float) -> Unit,
    onTitleSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
) {
    AppText(
        text = stringResource(R.string.text_typography_section),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_s)),
    )
    LabeledSlider(
        label = stringResource(R.string.text_label_font_size),
        valueLabel = stringResource(R.string.text_value_sp, doc.fontSizeSp.toInt()),
        value = doc.fontSizeSp,
        valueRange = TextDoc.FONT_SIZE_RANGE,
        onValueChange = onFontSizeChange,
    )
    LabeledSlider(
        label = stringResource(R.string.text_label_title_size),
        valueLabel = stringResource(R.string.text_value_sp, doc.titleSizeSp.toInt()),
        value = doc.titleSizeSp,
        valueRange = TextDoc.TITLE_SIZE_RANGE,
        onValueChange = onTitleSizeChange,
    )
    LabeledSlider(
        label = stringResource(R.string.text_label_line_spacing),
        valueLabel = stringResource(R.string.text_value_x, String.format("%.1f", doc.lineSpacingMultiplier)),
        value = doc.lineSpacingMultiplier,
        valueRange = TextDoc.MIN_LINE_SPACING..TextDoc.MAX_LINE_SPACING,
        onValueChange = onLineSpacingChange,
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            AppText(text = label, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            AppText(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 真实 WYSIWYG 预览：位图由与打印管线相同的 TextBitmapRenderer 产出（384 点宽），
 * 短行居中/靠右等排版效果与实际出纸完全一致。预览区有界可滚动，不会撑长页面；
 * 超长文本不生成位图，改显分段连续打印提示。
 */
@Composable
private fun PreviewCard(
    previewBitmap: android.graphics.Bitmap?,
    customFontName: String?,
    isDocTooLarge: Boolean,
    totalChars: Int,
) {
    val glassActive = rememberLiquidGlassActive()
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(R.string.text_preview),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (customFontName != null) {
                AppText(
                    text = customFontName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s))
                // 预览区随页面滚动，走真玻璃会因背景纹理滞后一帧而在滑动时抖动 → 固定用伪玻璃。
                .pseudoGlassSurface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                )
                .glassAwareOutline(MaterialTheme.shapes.medium, glassActive = false),
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
            tonalElevation = dimensionResource(ResR.dimen.dimen_shape_corner_s),
        ) {
            when {
                isDocTooLarge -> AppText(
                    text = stringResource(R.string.text_preview_large, totalChars),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(ResR.dimen.dimen_global_padding_l)),
                )

                previewBitmap == null -> AppText(
                    text = stringResource(R.string.text_preview_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(ResR.dimen.dimen_global_padding_l)),
                )

                else -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = dimensionResource(ResR.dimen.dimen_text_preview_max_height))
                        .verticalScroll(rememberScrollState()),
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.text_preview),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorBottomBar(
    connectedName: String?,
    isConnecting: Boolean,
    isPrinting: Boolean,
    progress: PrintProgress?,
    canPrint: Boolean,
    canSave: Boolean,
    onGoConnect: () -> Unit,
    onPrint: () -> Unit,
    onSave: () -> Unit,
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
                PrintProgressLine(progress)
                Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            } else {
                AppText(
                    text = when {
                        isConnecting -> stringResource(R.string.text_connecting)
                        connectedName != null -> stringResource(R.string.text_connected_to, connectedName)
                        else -> stringResource(R.string.text_device_not_selected)
                    },
                    color = if (connectedName != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
            ) {
                if (isPrinting) {
                    AppSecondaryButton(
                        text = stringResource(R.string.text_action_cancel_print),
                        onClick = onCancelPrint,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // 与图片打印页同位：连接前为"去连接"，连接后该位置为"保存"（保存预览图到相册）。
                    if (connectedName == null) {
                        AppSecondaryButton(
                            text = stringResource(R.string.text_action_go_connect),
                            onClick = onGoConnect,
                            enabled = !isConnecting,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        AppOutlinedButton(
                            text = stringResource(R.string.text_action_save),
                            onClick = onSave,
                            enabled = canSave,
                            modifier = Modifier
                                .weight(1f)
                                .height(dimensionResource(ResR.dimen.dimen_global_btn_height_min)),
                            leadingIcon = Icons.Filled.Download,
                        )
                    }
                    AppPrimaryButton(
                        text = stringResource(R.string.text_action_print),
                        onClick = onPrint,
                        enabled = canPrint,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrintProgressLine(progress: PrintProgress?) {
    val phase = progress?.phase
    AppText(
        text = when (phase) {
            PrintPhase.CoolingDown -> stringResource(
                R.string.text_progress_cooling,
                ((progress?.coolingRemainingMs ?: 0L) / 1000L).toInt() + 1,
            )

            PrintPhase.Printing -> stringResource(
                R.string.text_progress_printing,
                (progress?.segmentIndex ?: 0) + 1,
                progress?.segmentCount ?: 1,
                (progress?.pageDone ?: 0) + 1,
                (progress?.pageCountInSegment ?: 1).coerceAtLeast(1),
            )

            PrintPhase.Finishing -> stringResource(R.string.text_progress_finishing)
            else -> stringResource(R.string.text_progress_preparing)
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
            progress?.let {
                val total = (it.segmentCount.coerceAtLeast(1))
                val segRatio = 1f / total
                val pageRatio = if (it.pageCountInSegment > 0) {
                    it.pageDone.toFloat() / it.pageCountInSegment
                } else {
                    0f
                }
                ((it.segmentIndex + pageRatio) * segRatio).coerceIn(0f, 1f)
            } ?: 0f
        }
        val animated by animateFloatAsState(
            targetValue = target,
            animationSpec = rememberAppMotion().effectSpec(),
            label = "textPrintProgress",
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

@Composable
private fun alignLabel(align: PrintAlign): Int = when (align) {
    PrintAlign.Start -> R.string.text_align_start
    PrintAlign.Center -> R.string.text_align_center
    PrintAlign.End -> R.string.text_align_end
}

private fun printErrorRes(error: PrintError): Int = when (error) {
    PrintError.NotConnected -> R.string.text_err_not_connected
    PrintError.Permission -> R.string.text_err_permission
    PrintError.Timeout -> R.string.text_err_timeout
    PrintError.WriteFailed -> R.string.text_err_write_failed
    PrintError.RenderFailed -> R.string.text_err_render_failed
    PrintError.Cancelled -> R.string.text_err_cancelled
    PrintError.Generic -> R.string.text_err_generic
}

private fun printerStatusRes(status: PrinterStatus): Int = when (status) {
    PrinterStatus.OutOfPaper -> R.string.text_status_out_of_paper
    PrinterStatus.CoverOpen -> R.string.text_status_cover_open
    PrinterStatus.Overheat -> R.string.text_status_overheat
    PrinterStatus.LowBattery -> R.string.text_status_low_battery
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

private const val FEED_STEP = 10

@Preview(showBackground = true)
@Composable
private fun TextEditorScreenPreview() {
    TextEditorScreen(
        state = TextEditorUiState(
            doc = TextDoc(title = "示例标题", content = "示例正文内容"),
            totalChars = 6,
        ),
        onBack = {},
        onGoConnect = {},
        onOpenPixivGallery = {},
        onTitleChange = {},
        onContentChange = {},
        onFontSizeChange = {},
        onTitleSizeChange = {},
        onLineSpacingChange = {},
        onAlignChange = {},
        onBoldChange = {},
        onThicknessChange = {},
        onFeedAfterChange = {},
        onBatchChange = {},
        onCopiesChange = {},
        onImportTxt = { _, _ -> },
        onImportZip = { _, _ -> },
        onImportFont = { _, _ -> },
        onRemoveFile = {},
        onMoveFile = { _, _ -> },
        onEditorExpandedChange = {},
        onPrint = {},
        onCancelPrint = {},
        onSave = {},
        onClearResult = {},
        onClearNotice = {},
    )
}
