package com.pencil.chuangyin.core.ui.components.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pencil.chuangyin.core.ui.components.AppFilterChip
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.GlassTopAppBar
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.resources.R as ResR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 统一阅读器（小说阅读器 / 文本查看器双模式）。
 *
 * - **小说阅读器**：按当前字号把正文切成一页页「撑满」的页面，左右滑动或
 *   底栏按钮翻页；顶栏设置可调底色、字号、字色与亮度，字号变化会重排页数。
 * - **文本查看器**：一条式连续滚动，右侧有一条贯穿内容高度的快捷拖动条。
 * - 两种模式都支持在**本地内容**上快捷修改（[editable]）；在线阅读时由调用方
 *   传 `editable = false`，编辑按钮置灰禁用。
 *
 * 排版偏好由 [ReaderSettingsStore] 持久化，所有入口共享。
 *
 * @param title 顶栏标题。
 * @param content 正文（不含标题；标题由调用方决定是否并入）。
 * @param defaultMode 场景决定的默认模式（P 站 / 本地保存默认小说阅读器，
 *        AI 打印的文本预览默认文本查看器），用户可在右上角切换。
 * @param editable 是否允许快捷修改正文。
 * @param onContentChange 保存修改后的正文；为空时编辑入口不显示。
 * @param actions 顶栏右侧的附加操作（分享 / 打印等）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppReader(
    title: String,
    content: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    defaultMode: ReaderMode = ReaderMode.NOVEL,
    editable: Boolean = false,
    onContentChange: ((String) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember(context) { readerSettingsStore(context) }
    val settings by store.settings.collectAsState(initial = ReaderSettings())
    val scope = rememberCoroutineScope()
    val motion = rememberAppMotion()

    var mode by rememberSaveable(title) { mutableStateOf(defaultMode) }
    var editing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    val canEdit = editable && onContentChange != null
    val background = Color(settings.backgroundArgb)
    val textColor = Color(settings.textColorArgb)

    val bodyStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = settings.fontSizeSp.sp,
        lineHeight = (settings.fontSizeSp * LINE_HEIGHT_RATIO).sp,
        color = textColor,
    )

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        scope.launch { store.update(transform) }
    }

    Box(modifier = modifier.fillMaxSize().background(background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopAppBar(
                title = {
                    AppText(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    AppIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(ResR.string.label_back),
                        tint = textColor,
                    )
                },
                actions = {
                    if (editing) {
                        AppIconButton(
                            onClick = {
                                onContentChange?.invoke(draft)
                                editing = false
                            },
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(ResR.string.reader_edit_save),
                            tint = textColor,
                        )
                        AppIconButton(
                            onClick = { editing = false },
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(ResR.string.reader_edit_cancel),
                            tint = textColor,
                        )
                    } else {
                        AppIconButton(
                            onClick = {
                                draft = content
                                editing = true
                            },
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(
                                if (canEdit) ResR.string.reader_edit
                                else ResR.string.reader_edit_online_disabled,
                            ),
                            enabled = canEdit,
                            tint = textColor,
                        )
                        AppIconButton(
                            onClick = { showSettings = true },
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(ResR.string.reader_settings_title),
                            tint = textColor,
                        )
                        AppIconButton(
                            onClick = {
                                mode = if (mode == ReaderMode.NOVEL) ReaderMode.TEXT else ReaderMode.NOVEL
                            },
                            imageVector = if (mode == ReaderMode.NOVEL) {
                                Icons.Filled.Article
                            } else {
                                Icons.Filled.MenuBook
                            },
                            contentDescription = stringResource(
                                if (mode == ReaderMode.NOVEL) ResR.string.reader_switch_to_text
                                else ResR.string.reader_switch_to_novel,
                            ),
                            tint = textColor,
                        )
                        // 附加操作用阅读器文字色作为默认前景色：深色背景下图标不会「隐身」，
                        // 且 tint 保持为 null，液态玻璃原生路径依然可用。
                        CompositionLocalProvider(LocalContentColor provides textColor) {
                            actions()
                        }
                    }
                },
                fallbackColor = background,
            )
            // 可选的页眉（作者 / 标签等）：位于顶栏与正文之间，占用真实高度，
            // 分页会据此重新计算每页容量，不会出现最后一行被盖住。
            header?.invoke()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (editing) {
                    ReaderEditor(
                        value = draft,
                        onValueChange = { draft = it },
                        style = bodyStyle,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = {
                            fadeIn(motion.effect) togetherWith fadeOut(motion.effect)
                        },
                        label = "reader-mode",
                    ) { current ->
                        when (current) {
                            ReaderMode.NOVEL -> NovelPager(
                                content = content,
                                style = bodyStyle,
                                textColor = textColor,
                            )
                            ReaderMode.TEXT -> ScrollTextViewer(
                                content = content,
                                style = bodyStyle,
                                textColor = textColor,
                            )
                        }
                    }
                }
            }
        }
        if (settings.brightness < ReaderSettings.MAX_BRIGHTNESS) {
            // 亮度用暗色叠层实现：不拦截触摸（无点击手势），只压暗画面。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(alpha = 1f - settings.brightness),
                    ),
            )
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = settings,
            onSettingsChange = { next -> updateSettings { next } },
            onDismiss = { showSettings = false },
        )
    }
}

/** 小说阅读器：把正文按字号切页，左右滑动 / 底栏按钮翻页。 */
@Composable
private fun NovelPager(
    content: String,
    style: TextStyle,
    textColor: Color,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val horizontalPadding = PAGE_HORIZONTAL_PADDING
    val verticalPadding = PAGE_VERTICAL_PADDING

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(density) { (maxWidth - horizontalPadding * 2).roundToPx() }
        val heightPx = with(density) { (maxHeight - verticalPadding * 2).roundToPx() }

        // 排版按页渐进产出：先在后台算出一小批页（当前可见页立刻用新样式渲染），
        // 其余页面继续在下一个时间片里补全；主线程不再一次性重排整本书。
        var paginating by remember { mutableStateOf(false) }
        val pagesState = remember { mutableStateOf<List<Int>?>(null) }
        LaunchedEffect(content, style, widthPx, heightPx) {
            if (widthPx <= 0 || heightPx <= 0) {
                pagesState.value = listOf(0, content.length)
                return@LaunchedEffect
            }
            paginating = true
            try {
                paginateFlow(content, style, measurer, widthPx, heightPx)
                    .flowOn(Dispatchers.Default)
                    .collect { pagesState.value = it }
            } finally {
                paginating = false
            }
        }

        val pageStarts = pagesState.value
        if (pageStarts == null || pageStarts.size < 2) {
            AppLoadingIndicator(
                color = textColor,
                modifier = Modifier.align(Alignment.Center),
            )
            return@BoxWithConstraints
        }

        val pageCount = pageStarts.size - 1
        val pagerState = rememberPagerState(pageCount = { pageCount })
        val scope = rememberCoroutineScope()
        // 字号 / 窗口尺寸变化导致页数重排时，回到原阅读位置所在的页，而不是跳回第一页。
        var anchorChar by remember { mutableIntStateOf(0) }
        LaunchedEffect(pagerState.currentPage, pageStarts, paginating) {
            val start = pageStarts.getOrElse(pagerState.currentPage) { 0 }
            // 重排期间分页结果从 0 渐进产出，当前页可能被夹到「已完成区间」的末尾；
            // 此时不要把锚点跟着回退，否则会丢失用户原本的阅读位置。
            if (start >= anchorChar || !paginating) anchorChar = start
        }
        LaunchedEffect(pageStarts, paginating) {
            if (paginating) return@LaunchedEffect
            val target = pageStarts.indexOfLast { it <= anchorChar }.coerceAtLeast(0)
            if (target != pagerState.currentPage && target in 0 until pageCount) {
                pagerState.scrollToPage(target)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1,
            ) { page ->
                val start = pageStarts[page]
                val end = pageStarts[page + 1]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                ) {
                    Text(
                        text = content.substring(start, end),
                        style = style,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            NovelBottomBar(
                page = pagerState.currentPage,
                pageCount = pageCount,
                textColor = textColor,
                onPrev = {
                    if (pagerState.currentPage > 0) {
                        // 翻页同样走滑动动画，与手势翻页手感一致。
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                },
                onNext = {
                    if (pagerState.currentPage < pageCount - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            )
        }
    }
}

/** 底栏翻页器：上一页 / 页码 / 下一页。 */
@Composable
private fun NovelBottomBar(
    page: Int,
    pageCount: Int,
    textColor: Color,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppIconButton(
            onClick = onPrev,
            imageVector = Icons.Filled.ChevronLeft,
            contentDescription = stringResource(ResR.string.reader_prev_page),
            enabled = page > 0,
            tint = textColor,
            size = READER_PAGE_BUTTON_SIZE,
        )
        AppText(
            text = stringResource(ResR.string.reader_page_indicator, page + 1, pageCount),
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            align = TextAlign.Center,
        )
        AppIconButton(
            onClick = onNext,
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = stringResource(ResR.string.reader_next_page),
            enabled = page < pageCount - 1,
            tint = textColor,
            size = READER_PAGE_BUTTON_SIZE,
        )
    }
}

/** 文本查看器：分段懒加载的连续滚动 + 右侧贯穿式快捷拖动条。 */
@Composable
private fun ScrollTextViewer(
    content: String,
    style: TextStyle,
    textColor: Color,
) {
    // 正文按「几百字一段」切块，交给 LazyColumn 懒组合：
    // 调整字号/字色时只有屏幕内的几块会重新排版，未进入视口的块在滑入时
    // 直接用新样式即时渲染，避免一次性重排几万字造成掉帧。
    val chunks = remember(content) { splitTextChunks(content, TEXT_CHUNK_CHARS) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = PAGE_HORIZONTAL_PADDING,
                    end = PAGE_HORIZONTAL_PADDING + QUICK_SCROLL_TOUCH_WIDTH,
                ),
            contentPadding = PaddingValues(vertical = PAGE_VERTICAL_PADDING),
        ) {
            items(chunks.size, key = { it }) { index ->
                Text(
                    text = chunks[index],
                    style = style,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        LazyQuickScrollBar(
            listState = listState,
            color = textColor,
            onJump = { ratio ->
                if (chunks.isNotEmpty()) {
                    val target = (ratio * (chunks.size - 1)).toInt()
                        .coerceIn(0, chunks.size - 1)
                    scope.launch { listState.scrollToItem(target) }
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = PAGE_VERTICAL_PADDING),
        )
    }
}

/** 懒列表版快捷拖动条：由 [LazyListState] 可见项位置换算滚动比例。 */
@Composable
private fun LazyQuickScrollBar(
    listState: LazyListState,
    color: Color,
    onJump: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = listState.layoutInfo
    val first = info.visibleItemsInfo.firstOrNull()
    val fraction = if (first == null || info.totalItemsCount <= 1) {
        0f
    } else {
        val itemHeight = first.size.coerceAtLeast(1)
        ((first.index + first.offset.toFloat() / itemHeight) / info.totalItemsCount)
            .coerceIn(0f, 1f)
    }
    QuickScrollBar(fraction = fraction, onJump = onJump, color = color, modifier = modifier)
}

/** 贯穿内容的快捷拖动条：拖动或点按轨道即跳到对应位置。 */
@Composable
private fun QuickScrollBar(
    fraction: Float,
    onJump: (Float) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var barHeightPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (dragging) 1f else 0.55f,
        label = "quick-scroll-alpha",
    )
    val thumbHeightPx = (barHeightPx * VIEWPORT_THUMB_RATIO).coerceIn(
        with(density) { QUICK_SCROLL_MIN_THUMB.toPx() },
        with(density) { (QUICK_SCROLL_MIN_THUMB * 2.5f).toPx() },
    )

    fun jumpTo(y: Float) {
        if (barHeightPx <= 0) return
        onJump((y / barHeightPx.toFloat()).coerceIn(0f, 1f))
    }

    Box(
        modifier = modifier
            .width(QUICK_SCROLL_TOUCH_WIDTH)
            .onSizeChanged { barHeightPx = it.height }
            .pointerInput(barHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    jumpTo(change.position.y)
                }
            }
            .pointerInput(barHeightPx) {
                detectTapGestures { offset -> jumpTo(offset.y) }
            },
    ) {
        // 轨道：贯穿整个内容高度。
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(QUICK_SCROLL_TRACK_WIDTH)
                .fillMaxHeight()
                .background(color.copy(alpha = 0.18f * alpha), RoundedCornerShape(50)),
        )
        // 拇指：高度近似表示当前可见内容占比，位置随滚动线性移动。
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(QUICK_SCROLL_TRACK_WIDTH)
                .height(with(density) { thumbHeightPx.toDp() })
                .offset {
                    IntOffset(
                        x = 0,
                        y = ((barHeightPx - thumbHeightPx) * fraction).toInt().coerceAtLeast(0),
                    )
                }
                .background(color.copy(alpha = 0.75f * alpha), RoundedCornerShape(50)),
        )
    }
}

/** 编辑模式：整段正文的全屏编辑面板；保存后才回写调用方内容。 */
@Composable
private fun ReaderEditor(
    value: String,
    onValueChange: (String) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = PAGE_HORIZONTAL_PADDING,
                vertical = PAGE_VERTICAL_PADDING,
            ),
    ) {
        if (value.isEmpty()) {
            AppText(
                text = stringResource(ResR.string.reader_edit_hint),
                style = style,
                color = style.color.copy(alpha = 0.5f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(style.color),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Default,
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 阅读设置：底色 / 字号 / 字色 / 亮度。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l),
                    vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
                ),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        ) {
            AppText(
                text = stringResource(ResR.string.reader_settings_title),
                style = MaterialTheme.typography.titleMedium,
            )
            ReaderSliderRow(
                label = stringResource(ResR.string.reader_font_size_label),
                valueText = stringResource(
                    ResR.string.reader_font_size_value,
                    settings.fontSizeSp.toInt(),
                ),
            ) {
                AppSlider(
                    value = settings.fontSizeSp,
                    onValueChange = { next ->
                        onSettingsChange(
                            settings.copy(
                                fontSizeSp = next.coerceIn(
                                    ReaderSettings.MIN_FONT_SIZE_SP,
                                    ReaderSettings.MAX_FONT_SIZE_SP,
                                ),
                            ),
                        )
                    },
                    valueRange = ReaderSettings.MIN_FONT_SIZE_SP..ReaderSettings.MAX_FONT_SIZE_SP,
                )
            }
            ReaderSliderRow(
                label = stringResource(ResR.string.reader_brightness_label),
                valueText = stringResource(
                    ResR.string.reader_brightness_value,
                    (settings.brightness * 100).toInt(),
                ),
            ) {
                AppSlider(
                    value = settings.brightness,
                    onValueChange = { next ->
                        onSettingsChange(
                            settings.copy(
                                brightness = next.coerceIn(
                                    ReaderSettings.MIN_BRIGHTNESS,
                                    ReaderSettings.MAX_BRIGHTNESS,
                                ),
                            ),
                        )
                    },
                    valueRange = ReaderSettings.MIN_BRIGHTNESS..ReaderSettings.MAX_BRIGHTNESS,
                )
            }
            AppText(
                text = stringResource(ResR.string.reader_background_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(READER_CHIP_SPACING)) {
                READER_BACKGROUNDS.forEach { preset ->
                    AppFilterChip(
                        selected = settings.backgroundArgb == preset.color,
                        onClick = { onSettingsChange(settings.copy(backgroundArgb = preset.color)) },
                        label = stringResource(preset.labelRes),
                    )
                }
            }
            AppText(
                text = stringResource(ResR.string.reader_text_color_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(READER_CHIP_SPACING)) {
                READER_TEXT_COLORS.forEach { preset ->
                    AppFilterChip(
                        selected = settings.textColorArgb == preset.color,
                        onClick = { onSettingsChange(settings.copy(textColorArgb = preset.color)) },
                        label = stringResource(preset.labelRes),
                    )
                }
            }
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        }
    }
}

@Composable
private fun ReaderSliderRow(
    label: String,
    valueText: String,
    slider: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppText(text = label, style = MaterialTheme.typography.labelLarge)
            AppText(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        slider()
    }
}

/** 阅读底色预设。 */
internal data class ReaderColorPreset(val color: Int, val labelRes: Int)

internal val READER_BACKGROUNDS = listOf(
    ReaderColorPreset(0xFFF7F2E8.toInt(), ResR.string.reader_background_paper),
    ReaderColorPreset(0xFFF3E6C8.toInt(), ResR.string.reader_background_parchment),
    ReaderColorPreset(0xFFDCE8D5.toInt(), ResR.string.reader_background_green),
    ReaderColorPreset(0xFF141414.toInt(), ResR.string.reader_background_dark),
)

internal val READER_TEXT_COLORS = listOf(
    ReaderColorPreset(0xFF23201C.toInt(), ResR.string.reader_text_black),
    ReaderColorPreset(0xFF4A4A4A.toInt(), ResR.string.reader_text_gray),
    ReaderColorPreset(0xFF6B4A2F.toInt(), ResR.string.reader_text_brown),
    ReaderColorPreset(0xFFF2F2F2.toInt(), ResR.string.reader_text_white),
)

/**
 * 把正文按「几百字一段」切分，优先在自然断点（换行 / 句末标点 / 逗号 / 空格）处切开，
 * 避免在句子中间硬断开导致视觉上凭空多出一行。
 */
private fun splitTextChunks(content: String, chunkChars: Int): List<String> {
    if (content.isEmpty()) return emptyList()
    if (content.length <= chunkChars) return listOf(content)
    val chunks = ArrayList<String>((content.length / chunkChars) + 1)
    var start = 0
    while (start < content.length) {
        val limit = (start + chunkChars).coerceAtMost(content.length)
        val end = if (limit >= content.length) {
            content.length
        } else {
            findNaturalBreak(content, start, limit, chunkChars)
        }
        chunks += content.substring(start, end)
        start = end
    }
    return chunks
}

/** 从切分上限向前回退，找到不超过半个块长的最近自然断点；找不到就按上限硬切。 */
private fun findNaturalBreak(content: String, start: Int, limit: Int, chunkChars: Int): Int {
    val min = start + chunkChars / 2
    for (i in limit downTo min) {
        when (content[i - 1]) {
            '\n', '。', '！', '？', '；', '.', '!', '?', ';' -> return i
        }
    }
    for (i in limit downTo min) {
        when (content[i - 1]) {
            ' ', '，', ',', '、', '：', ':' -> return i
        }
    }
    return limit
}

/**
 * 把正文切成若干页，返回每页起始下标 + 末尾的 `content.length`。
 *
 * 采用「窗口 + 二分」：每页只测量最多 [PAGINATION_WINDOW_CHARS] 个字符的片段，
 * 通过二分找到「恰好撑满一页」的最大字符数，避免对整本小说做一次性超大排版。
 *
 * 逐批产出：先发一小批页让可见页立刻用新样式渲染，其余页面随后分批补全
 * （调用方用 flowOn 切到后台线程，主线程不再被整本重排阻塞）。
 */
private fun paginateFlow(
    content: String,
    style: TextStyle,
    measurer: TextMeasurer,
    widthPx: Int,
    heightPx: Int,
): Flow<List<Int>> = flow {
    if (content.isEmpty()) {
        emit(listOf(0))
        return@flow
    }
    val constraints = Constraints(maxWidth = widthPx)
    val result = ArrayList<Int>()
    var start = 0
    var pagesSinceEmit = 0
    fun fits(count: Int): Boolean {
        val end = (start + count).coerceAtMost(content.length)
        if (end <= start) return false
        val layout = measurer.measure(
            text = content.substring(start, end),
            style = style,
            constraints = constraints,
            maxLines = Int.MAX_VALUE,
        )
        return layout.size.height <= heightPx
    }
    while (start < content.length) {
        result += start
        val remaining = content.length - start
        var lo = 1
        var hi = remaining.coerceAtMost(PAGINATION_WINDOW_CHARS)
        if (!fits(lo)) {
            // 极端情况（可用区域连一行都放不下）：保证前进，避免死循环。
            start += 1
            continue
        }
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (fits(mid)) lo = mid else hi = mid - 1
        }
        start += lo
        // 跳过页首连续换行：避免出现整页只有空行的「假页」。
        while (start < content.length && content[start] == '\n') start++
        pagesSinceEmit++
        // 开头几页用更小的批次，保证可见页尽快出来；之后按固定批次继续补全。
        val batchSize = if (result.size <= PAGINATION_FIRST_BATCH_PAGES) {
            4
        } else {
            PAGINATION_PUBLISH_PAGES
        }
        if (pagesSinceEmit >= batchSize) {
            pagesSinceEmit = 0
            emit(result.toList() + content.length)
        }
    }
    result += content.length
    emit(result.toList())
}

private val PAGE_HORIZONTAL_PADDING: Dp = 20.dp
private val PAGE_VERTICAL_PADDING: Dp = 18.dp
private val QUICK_SCROLL_TOUCH_WIDTH: Dp = 28.dp
private val QUICK_SCROLL_TRACK_WIDTH: Dp = 4.dp
private val QUICK_SCROLL_MIN_THUMB: Dp = 36.dp
private val READER_PAGE_BUTTON_SIZE: Dp = 40.dp
private val READER_CHIP_SPACING: Dp = 8.dp

private const val PAGINATION_WINDOW_CHARS = 8_000
private const val LINE_HEIGHT_RATIO = 1.72f

/** 文本查看器每块字数（几百字一段）。 */
private const val TEXT_CHUNK_CHARS = 400

/** 渐进分页：开头先连着发几小批，之后每算这么多页发一批。 */
private const val PAGINATION_FIRST_BATCH_PAGES = 8
private const val PAGINATION_PUBLISH_PAGES = 32

/** 拖动条拇指高度 = 视口高度 * 该比例（近似表示「当前可见内容占比」）。 */
private const val VIEWPORT_THUMB_RATIO = 0.12f
