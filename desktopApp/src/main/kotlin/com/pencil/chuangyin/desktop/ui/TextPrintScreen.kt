package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.glass.glassAwareOutline
import com.pencil.chuangyin.desktop.glass.glassSurface
import com.pencil.chuangyin.desktop.navigation.DesktopNavigator
import com.pencil.chuangyin.desktop.text.MountedTxtFile
import com.pencil.chuangyin.desktop.text.PrintAlign
import com.pencil.chuangyin.desktop.text.PrintError
import com.pencil.chuangyin.desktop.text.PrintOptions
import com.pencil.chuangyin.desktop.text.PrintPhase
import com.pencil.chuangyin.desktop.text.PrintProgress
import com.pencil.chuangyin.desktop.text.PrintResult
import com.pencil.chuangyin.desktop.text.PrinterStatus
import com.pencil.chuangyin.desktop.text.TextDoc
import com.pencil.chuangyin.desktop.text.TextEditorStore
import com.pencil.chuangyin.desktop.text.TextEditorUiState
import com.pencil.chuangyin.desktop.text.TextNotice
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

// 尺寸对齐安卓端 dimens.xml（dimen_global_padding_s/m/l、dimen_shape_corner_l、
// dimen_text_editor_collapsed_height、dimen_text_preview_max_height）。
private val PADDING_S = 8.dp
private val PADDING_M = 16.dp
private val PADDING_L = 24.dp
private val BOTTOM_BAR_CORNER = 28.dp
private val COLLAPSED_EDITOR_HEIGHT = 160.dp
private val PREVIEW_MAX_HEIGHT = 280.dp

/** 走纸量取整步长（与安卓端 FEED_STEP 一致）。 */
private const val FEED_STEP = 10

/** 内容区最大宽度：桌面大窗口下保持安卓平板形态的阅读行宽，不做重排。 */
private val CONTENT_MAX_WIDTH = 960.dp

/**
 * 文本打印页 —— 逐条复刻安卓端 `feature/text/TextEditorScreen.kt`：
 * 顶栏 + 标题输入 + 正文编辑（收缩态/全屏）+ 已挂载 TXT 列表 + 打印参数 +
 * 导入 TXT/压缩包/字体 + 排版三项 + WYSIWYG 预览 + 底部玻璃操作条。
 *
 * 桌面端与安卓端共用同一套蓝牙热敏打印链路：连接态由 PrinterConnectionStore 镜像，
 * 未连接时底栏呈现「未连接 + 去连接打印机」，点击直接打开「连接打印机」页。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TextPrintScreen(onBack: () -> Unit) {
    val state by TextEditorStore.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var overheatDismissed by remember { mutableStateOf(false) }

    val cooling = state.isPrinting && state.overheatActive
    // 过热冷却期间打印按钮保持可点：点击不重复下单，仅重新弹出冷却提示。
    val canPrint = state.connectedName != null && (!state.isPrinting || cooling)
    LaunchedEffect(cooling) { if (cooling) overheatDismissed = false }
    val onPrintGuarded: () -> Unit = { if (cooling) overheatDismissed = false else TextEditorStore.print() }

    val lastMessage = when (val result = state.lastResult) {
        is PrintResult.Success -> "打印成功"
        is PrintResult.Error -> printErrorText(result.code)
        null -> null
    }
    val statusMessage = state.printerStatus?.let { printerStatusText(it) }
    val noticeMessage = when (val notice = state.notice) {
        is TextNotice.Mounted -> "已挂载 ${notice.count} 个文件，共 ${notice.chars} 字"
        is TextNotice.ArchiveEmpty -> "压缩包根目录未找到 TXT 文件"
        is TextNotice.Saved -> "已保存 ${notice.count} 张预览图到输出目录"
        is TextNotice.SaveFailed -> "保存失败，请重试"
        null -> null
    }
    val errorResult = state.lastResult as? PrintResult.Error

    LaunchedEffect(lastMessage) {
        // 成功用 Snackbar 轻提示；失败改用对话框持久展示（见下），避免用户错过原因。
        if (state.lastResult is PrintResult.Success) {
            snackbarHostState.showSnackbar(lastMessage ?: "")
            TextEditorStore.clearResult()
        }
    }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            TextEditorStore.clearPrinterStatus()
        }
    }
    LaunchedEffect(noticeMessage) {
        noticeMessage?.let {
            snackbarHostState.showSnackbar(it)
            TextEditorStore.clearNotice()
        }
    }

    if (errorResult != null) {
        val detail = errorResult.detail
        AlertDialog(
            onDismissRequest = { TextEditorStore.clearResult() },
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
                AppTextButton(text = "好的", onClick = { TextEditorStore.clearResult() })
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    if (cooling && !overheatDismissed) {
        OverheatDialog(
            remainingMs = state.progress?.coolingRemainingMs ?: 0L,
            onDismiss = { overheatDismissed = true },
        )
    }

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = "文本打印",
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
            EditorBottomBar(
                connectedName = state.connectedName,
                isConnecting = state.isConnecting,
                isPrinting = state.isPrinting,
                progress = state.progress,
                canPrint = canPrint,
                canSave = state.previewImage != null && !state.isSaving,
                onGoConnect = { DesktopNavigator.navigate(DesktopNavigator.Page.Connect) },
                onPrint = onPrintGuarded,
                onSave = { TextEditorStore.savePreview() },
                onCancelPrint = { TextEditorStore.cancelPrint() },
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
                AppTextField(
                    value = state.doc.title,
                    onValueChange = { TextEditorStore.updateTitle(it) },
                    label = "标题",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(PADDING_M))

                if (state.mountedFiles.isNotEmpty()) {
                    MountedFileList(
                        files = state.mountedFiles,
                        onRemove = { TextEditorStore.removeMountedFile(it) },
                        onMove = { index, delta -> TextEditorStore.moveMountedFile(index, delta) },
                    )
                } else {
                    CollapsedContentEditor(
                        value = state.doc.content,
                        charCount = state.totalChars,
                        onValueChange = { TextEditorStore.updateContent(it) },
                        onExpand = { TextEditorStore.setEditorExpanded(true) },
                    )
                }

                Spacer(modifier = Modifier.height(PADDING_M))
                AppDivider()
                Spacer(modifier = Modifier.height(PADDING_M))

                PrintParameterControls(
                    doc = state.doc,
                    options = state.options,
                    batchEnabled = state.batchEnabled,
                    copies = state.copies,
                    onAlignChange = { TextEditorStore.updateAlign(it) },
                    onBoldChange = { TextEditorStore.updateBold(it) },
                    onThicknessChange = { TextEditorStore.updateThickness(it) },
                    onFeedAfterChange = { TextEditorStore.updateFeedAfter(it) },
                    onBatchChange = { TextEditorStore.setBatchEnabled(it) },
                    onCopiesChange = { TextEditorStore.setCopies(it) },
                )

                // 导入操作：TXT（系统多选）/ ZIP / 字体
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = PADDING_S),
                    horizontalArrangement = Arrangement.spacedBy(PADDING_S),
                    verticalArrangement = Arrangement.spacedBy(PADDING_S),
                ) {
                    ImportButton(
                        text = "导入TXT",
                        icon = AppIcon.TextSnippet,
                        onClick = {
                            val files = chooseTxtFiles()
                            if (files.isNotEmpty()) TextEditorStore.importTxt(files)
                        },
                    )
                    ImportButton(
                        text = "导入压缩包",
                        icon = AppIcon.FolderZip,
                        onClick = {
                            chooseZipFile()?.let { TextEditorStore.importTxtArchive(it) }
                        },
                    )
                    ImportButton(
                        text = "导入字体",
                        icon = null,
                        onClick = {
                            chooseFontFile()?.let { TextEditorStore.importFont(it) }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(PADDING_L))

                // 排版三项紧贴预览区上方，调整即可看到预览变化。
                TypographyControls(
                    doc = state.doc,
                    onFontSizeChange = { TextEditorStore.updateFontSize(it) },
                    onTitleSizeChange = { TextEditorStore.updateTitleSize(it) },
                    onLineSpacingChange = { TextEditorStore.updateLineSpacing(it) },
                )
                PreviewCard(state = state)
                Spacer(modifier = Modifier.height(PADDING_L))
            }
        }
    }

    // 全屏正文编辑覆盖层：必须晚于 Scaffold 绘制，否则会被页面内容盖住。
    if (state.editorExpanded) {
        FullScreenContentEditor(
            value = state.doc.content,
            onValueChange = { TextEditorStore.updateContent(it) },
            onDismiss = { TextEditorStore.setEditorExpanded(false) },
        )
    }
}

@Composable
private fun ImportButton(text: String, icon: AppIcon?, onClick: () -> Unit) {
    AppOutlinedButton(
        text = text,
        onClick = onClick,
        leadingIcon = icon,
    )
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
            Text(
                text = "正文",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "共 $charCount 字",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppTextButton(text = "展开", onClick = onExpand)
        }
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "在此输入要打印的正文…",
            singleLine = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(COLLAPSED_EDITOR_HEIGHT),
        )
    }
}

/** 全屏正文编辑页：整窗覆盖层（对齐安卓端 Dialog + 顶栏完成返回）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenContentEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Scaffold(
            topBar = {
                GlassTopAppBar(
                    title = "编辑正文",
                    subtitle = "共 ${value.length} 字",
                    navigationIcon = {
                        AppIconButton(
                            icon = AppIcon.Back,
                            contentDescription = "返回",
                            onClick = onDismiss,
                        )
                    },
                    actions = {
                        AppTextButton(text = "完成", onClick = onDismiss)
                    },
                )
            },
            containerColor = Color.Transparent,
        ) { inner ->
            AppTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = "在此输入要打印的正文…",
                singleLine = false,
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .padding(PADDING_M),
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
    val shape = MaterialTheme.shapes.medium
    Column {
        Text(
            text = "已挂载 TXT 文件（${files.size}）· 按顺序连续打印",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(PADDING_S))
        files.forEachIndexed { index, file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // dimen_shape_corner_sharp / 2 = 2dp，与安卓端列表项间距一致。
                    .padding(vertical = 2.dp)
                    .glassSurface(
                        shape = shape,
                        fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .glassAwareOutline(shape)
                    .padding(horizontal = PADDING_M, vertical = PADDING_S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = PADDING_S)
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "共 ${file.charCount} 字",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                AppIconButton(
                    icon = AppIcon.ArrowDropUp,
                    contentDescription = "上移",
                    onClick = { onMove(index, -1) },
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    enabled = index > 0,
                )
                AppIconButton(
                    icon = AppIcon.ArrowDropDown,
                    contentDescription = "下移",
                    onClick = { onMove(index, 1) },
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    enabled = index < files.lastIndex,
                )
                AppIconButton(
                    icon = AppIcon.Close,
                    contentDescription = "移除",
                    onClick = { onRemove(index) },
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/** 打印设备侧参数：对齐/加粗/浓度/走纸（字号等排版项移至预览区上方）。 */
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
    Text(
        text = "对齐",
        style = MaterialTheme.typography.titleMedium,
    )
    val aligns = listOf(PrintAlign.Start, PrintAlign.Center, PrintAlign.End)
    AppSegmentedRow(
        options = aligns.map { alignLabel(it) },
        selectedIndex = aligns.indexOf(doc.align).coerceAtLeast(0),
        onSelected = { onAlignChange(aligns[it]) },
        showCheck = true,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PADDING_M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "加粗",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        AppSwitch(checked = doc.bold, onCheckedChange = onBoldChange)
    }

    AppDivider(modifier = Modifier.padding(vertical = PADDING_M))

    LabeledSlider(
        label = "打印浓度",
        valueLabel = "${options.thickness} 档",
        value = options.thickness.toFloat(),
        valueRange = PrintOptions.thicknessRange.first.toFloat()..PrintOptions.thicknessRange.last.toFloat(),
        steps = PrintOptions.thicknessRange.last - PrintOptions.thicknessRange.first - 1,
        onValueChange = { onThicknessChange(it.toInt()) },
    )
    LabeledSlider(
        label = "打印后走纸",
        valueLabel = "${options.feedAfter} 行",
        value = options.feedAfter.toFloat(),
        valueRange = PrintOptions.feedAfterRange.first.toFloat()..PrintOptions.feedAfterRange.last.toFloat(),
        steps = (PrintOptions.feedAfterRange.last - PrintOptions.feedAfterRange.first) / FEED_STEP - 1,
        onValueChange = { onFeedAfterChange(((it + FEED_STEP / 2f) / FEED_STEP).toInt() * FEED_STEP) },
    )

    AppDivider(modifier = Modifier.padding(vertical = PADDING_M))

    // 批量 = 份数：整批任务重复执行 copies 次，未开启固定 1 份。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "批量打印",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        AppSwitch(checked = batchEnabled, onCheckedChange = onBatchChange)
    }
    if (batchEnabled) {
        LabeledSlider(
            label = "份数",
            valueLabel = "$copies 份",
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
    Text(
        text = "排版（字号/行距）",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = PADDING_S),
    )
    LabeledSlider(
        label = "字号",
        valueLabel = "${doc.fontSizeSp.toInt()} sp",
        value = doc.fontSizeSp,
        valueRange = TextDoc.FONT_SIZE_RANGE,
        onValueChange = onFontSizeChange,
    )
    LabeledSlider(
        label = "标题字号",
        valueLabel = "${doc.titleSizeSp.toInt()} sp",
        value = doc.titleSizeSp,
        valueRange = TextDoc.TITLE_SIZE_RANGE,
        onValueChange = onTitleSizeChange,
    )
    LabeledSlider(
        label = "行距",
        valueLabel = String.format("%.1f", doc.lineSpacingMultiplier) + "×",
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
            .padding(top = PADDING_S),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            Text(
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
 * 真实 WYSIWYG 预览：位图由与打印管线相同的渲染器产出（384 点宽），
 * 短行居中/靠右等排版效果与实际出纸完全一致。预览区有界可滚动，不会撑长页面；
 * 超长文本不生成位图，改显分段连续打印提示。
 */
@Composable
private fun PreviewCard(state: TextEditorUiState) {
    val shape = MaterialTheme.shapes.medium
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "打印预览",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            state.customFontName?.let { fontName ->
                Text(
                    text = fontName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PADDING_S)
                // 预览区随页面滚动：与安卓端一致固定用伪玻璃（玻璃关闭态 + 控件描边），
                // 避免真玻璃采样滞后造成滚动抖动。
                .glassSurface(
                    shape = shape,
                    enabled = false,
                    fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                .glassAwareOutline(shape, glassActive = false),
        ) {
            val preview = state.previewImage
            when {
                state.isDocTooLargeForPreview -> Text(
                    text = "文本较长（${state.totalChars} 字），将自动分段连续打印，为保证流畅此处不提供预览",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PADDING_L),
                )

                preview == null -> Text(
                    text = "输入内容后显示预览",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PADDING_L),
                )

                else -> {
                    val image = remember(preview) { preview.toComposeImageBitmap() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = PREVIEW_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Image(
                            bitmap = image,
                            contentDescription = "打印预览",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(PADDING_S),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
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
            PrintProgressLine(progress)
            Spacer(modifier = Modifier.height(PADDING_S))
        } else {
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
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PADDING_S),
            horizontalArrangement = Arrangement.spacedBy(PADDING_M),
        ) {
            if (isPrinting) {
                AppSecondaryButton(
                    text = "取消打印",
                    onClick = onCancelPrint,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // 与图片打印页同位：连接前为「去连接」，连接后该位置为「保存」（保存预览图）。
                if (connectedName == null) {
                    AppSecondaryButton(
                        text = "去连接打印机",
                        onClick = onGoConnect,
                        enabled = !isConnecting,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    AppOutlinedButton(
                        text = "保存",
                        onClick = onSave,
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                        leadingIcon = AppIcon.Download,
                    )
                }
                AppPrimaryButton(
                    text = "打印",
                    onClick = onPrint,
                    enabled = canPrint,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PrintProgressLine(progress: PrintProgress?) {
    val phase = progress?.phase
    Text(
        text = when (phase) {
            PrintPhase.CoolingDown ->
                "过热保护冷却中，剩余约 ${((progress?.coolingRemainingMs ?: 0L) / 1000L).toInt() + 1} 秒；冷却结束将从断点无缝续打"

            PrintPhase.Printing ->
                "正在打印：第 ${(progress?.segmentIndex ?: 0) + 1}/${progress?.segmentCount ?: 1} 段 · " +
                    "第 ${(progress?.pageDone ?: 0) + 1}/${(progress?.pageCountInSegment ?: 1).coerceAtLeast(1)} 页"

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
        LinearProgressIndicator(
            progress = {
                if (phase == PrintPhase.CoolingDown) 1f else progress?.let {
                    val total = it.segmentCount.coerceAtLeast(1)
                    val segRatio = 1f / total
                    val pageRatio = if (it.pageCountInSegment > 0) {
                        it.pageDone.toFloat() / it.pageCountInSegment
                    } else {
                        0f
                    }
                    ((it.segmentIndex + pageRatio) * segRatio).coerceIn(0f, 1f)
                } ?: 0f
            },
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

private fun alignLabel(align: PrintAlign): String = when (align) {
    PrintAlign.Start -> "左对齐"
    PrintAlign.Center -> "居中"
    PrintAlign.End -> "右对齐"
}

private fun printErrorText(error: PrintError): String = when (error) {
    PrintError.NotConnected -> "未连接打印机"
    PrintError.Permission -> "缺少蓝牙权限"
    PrintError.Timeout -> "连接超时，请靠近打印机后重试"
    PrintError.WriteFailed -> "数据写入失败"
    PrintError.RenderFailed -> "打印渲染失败"
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
private fun OverheatDialog(remainingMs: Long, onDismiss: () -> Unit) {
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

// ===================== 文件选择（替代安卓端系统文档选择器） =====================

private fun chooseTxtFiles(): List<File> {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择 TXT 文件（可多选）"
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = true
        isAcceptAllFileFilterUsed = false
        fileFilter = FileNameExtensionFilter("TXT 文本 (*.txt)", "txt")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.toList()
    } else {
        emptyList()
    }
}

private fun chooseZipFile(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择 TXT 压缩包"
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

private fun chooseFontFile(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择字体文件"
        fileSelectionMode = JFileChooser.FILES_ONLY
        isAcceptAllFileFilterUsed = false
        fileFilter = FileNameExtensionFilter("字体文件 (*.ttf, *.otf)", "ttf", "otf")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}
