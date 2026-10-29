package com.pencil.chuangyin.core.ui.components.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.LocalImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.pencil.chuangyin.core.ui.components.AppFilterChip
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.resources.R as ResR
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** 查看器内的图片编辑工具。 */
internal enum class ViewerEditTool { NONE, CROP, DOODLE, TEXT }

/**
 * 全屏图片查看器（M3 动效 + 液态玻璃 + 轻量编辑）。
 *
 * - 多图时左右滑动翻页，并在屏幕正下方 4/5 处提供「上一张 / 下一张」悬浮操作栏；
 * - 顶栏编辑按钮展开折叠工具条：裁切 / 涂鸦 / 文字 / 还原 / 保存副本；
 * - 编辑结果按页缓存，翻页后再回来仍是编辑后的样子，导出与否由调用方决定。
 *
 * @param models 待查看的图片（URL / File / Bitmap 均可，交给 Coil 解码）。
 * @param onPageChanged 当前页变化回调（分享当前页等）。
 * @param onExportEdited 保存编辑结果的回调；为 null 时工具条不显示「保存副本」。
 *        返回 true 表示保存成功（用于提示）。
 * @param actions 顶栏右侧附加操作（分享 / 打印等）。
 */
@Composable
fun AppImageViewerDialog(
    models: List<Any?>,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    contentDescription: String? = null,
    onPageChanged: ((Int) -> Unit)? = null,
    onExportEdited: (suspend (index: Int, bitmap: Bitmap) -> Boolean)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (models.isEmpty()) return
    val context = LocalContext.current
    val glassActive = rememberLiquidGlassActive()
    val motion = rememberAppMotion()
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, models.lastIndex),
    ) { models.size }
    LaunchedEffect(pagerState, models.size) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
            .collect { page -> onPageChanged?.invoke(page) }
    }

    // 每页编辑后的位图（key = 页序号）；原始 model 保持不变，随时可还原。
    val editedPages = remember { mutableStateMapOf<Int, Bitmap>() }
    var toolbarOpen by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(ViewerEditTool.NONE) }
    var editBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editBitmapPage by remember { mutableStateOf(-1) }
    var loadingEdit by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<ImageEditSession?>(null) }

    // 编辑样式（涂鸦颜色/粗细、文字颜色/字号）在一次查看会话内保持。
    var doodleColor by remember { mutableStateOf(Color.White) }
    var doodleWidthDp by remember { mutableStateOf(4f) }
    var textColor by remember { mutableStateOf(Color.White) }
    var textSizeSp by remember { mutableStateOf(28f) }

    val currentPage = pagerState.currentPage

    fun closeEditing() {
        tool = ViewerEditTool.NONE
        session = null
        editError = false
    }

    fun openTool(next: ViewerEditTool) {
        if (tool == next) {
            closeEditing()
            return
        }
        val page = pagerState.currentPage
        tool = next
        editError = false
        if (editBitmapPage == page && editBitmap != null) {
            session = ImageEditSession(editBitmap!!)
            return
        }
        session = null
        loadingEdit = true
        scope.launch {
            val bitmap = loadEditableBitmap(context, models[page])
            loadingEdit = false
            if (bitmap == null) {
                editError = true
                tool = ViewerEditTool.NONE
            } else {
                editBitmap = bitmap
                editBitmapPage = page
                session = ImageEditSession(bitmap)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        horizontal = dimensionResource(ResR.dimen.dimen_global_padding_s),
                        vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconButton(
                    onClick = onDismiss,
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(ResR.string.pixiv_gallery_close_preview),
                    tint = Color.White,
                )
                AppText(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                )
                AppIconButton(
                    onClick = {
                        toolbarOpen = !toolbarOpen
                        if (!toolbarOpen) closeEditing()
                    },
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(ResR.string.viewer_edit_tools),
                    tint = Color.White,
                )
                actions()
            }
            AnimatedVisibility(
                visible = toolbarOpen,
                enter = expandVertically(motion.spatialSpec()) + fadeIn(motion.effect),
                exit = shrinkVertically(motion.spatialSpec()) + fadeOut(motion.effect),
            ) {
                ViewerEditToolbar(
                    tool = tool,
                    hasEdited = editedPages.containsKey(currentPage),
                    hasSession = session != null,
                    canSave = onExportEdited != null,
                    doodleColor = doodleColor,
                    doodleWidthDp = doodleWidthDp,
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    onToolChange = ::openTool,
                    onDoodleColorChange = { doodleColor = it },
                    onDoodleWidthChange = { doodleWidthDp = it },
                    onTextColorChange = { textColor = it },
                    onTextSizeChange = { textSizeSp = it },
                    onApply = {
                        val s = session
                        val page = editBitmapPage
                        if (s != null && page >= 0) {
                            editedPages[page] = s.render()
                        }
                        closeEditing()
                    },
                    onCancel = { closeEditing() },
                    onReset = {
                        editedPages.remove(currentPage)
                        // 还原后重新进入编辑需要用未编辑的原图。
                        if (editBitmapPage == currentPage) {
                            editBitmap = null
                            editBitmapPage = -1
                        }
                        closeEditing()
                    },
                    onSave = {
                        val bitmap = editedPages[currentPage] ?: editBitmap
                        val export = onExportEdited
                        if (bitmap != null && export != null && !exporting) {
                            // 导出可能写相册 / SAF，放到协程里避免阻塞主线程。
                            exporting = true
                            scope.launch {
                                val ok = runCatching { export(currentPage, bitmap) }
                                    .getOrDefault(false)
                                exporting = false
                                saveMessage = if (ok) {
                                    context.getString(ResR.string.viewer_edit_saved)
                                } else {
                                    context.getString(ResR.string.viewer_edit_save_failed)
                                }
                            }
                        }
                    },
                )
            }
            if (loadingEdit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AppLoadingIndicator(color = Color.White, size = 20.dp)
                    AppText(
                        text = stringResource(ResR.string.viewer_edit_loading),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(start = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                    )
                }
            }
            if (editError) {
                AppText(
                    text = stringResource(ResR.string.viewer_edit_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    align = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                )
            }
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val edited = editedPages[page]
                    if (edited != null) {
                        AppZoomableImage(
                            model = edited,
                            contentDescription = contentDescription,
                        )
                    } else {
                        AppZoomableImage(
                            model = models[page],
                            contentDescription = contentDescription,
                        )
                    }
                }
                val editingBitmap = editBitmap
                val activeSession = session
                if (tool != ViewerEditTool.NONE && editingBitmap != null &&
                    activeSession != null && editBitmapPage == currentPage
                ) {
                    ImageEditSurface(
                        session = activeSession,
                        tool = tool,
                        doodleColor = doodleColor,
                        doodleWidthDp = doodleWidthDp,
                        textColor = textColor,
                        textSizeSp = textSizeSp,
                    )
                }
                // 悬浮翻页栏：屏幕正下方 4/5 处（距底部 1/5 屏高），编辑时隐藏避免误触。
                // 用 Column 承载两层悬浮层，规避 BoxWithConstraintsScope 的 DSL 标记对
                // ColumnScope 版 AnimatedVisibility 的隐式接收者限制。
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = maxHeight * NAV_BAR_BOTTOM_OFFSET_RATIO),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // 底部提示（保存结果）：位于悬浮翻页栏上方，避免遮挡翻页按钮。
                    AnimatedVisibility(
                        visible = saveMessage != null,
                        enter = fadeIn(motion.effect) + expandVertically(motion.spatialSpec()),
                        exit = fadeOut(motion.effect) + shrinkVertically(motion.spatialSpec()),
                    ) {
                        AppText(
                            text = saveMessage.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            align = TextAlign.Center,
                            modifier = Modifier
                                .padding(
                                    bottom = dimensionResource(ResR.dimen.dimen_global_touch_target),
                                )
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(
                                    horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                                    vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                                ),
                        )
                    }
                    AnimatedVisibility(
                        visible = models.size > 1 && tool == ViewerEditTool.NONE,
                        enter = fadeIn(motion.effect) + expandVertically(motion.spatialSpec()),
                        exit = fadeOut(motion.effect) + shrinkVertically(motion.spatialSpec()),
                    ) {
                        ViewerNavBar(
                            page = currentPage,
                            pageCount = models.size,
                            onPrev = {
                                if (currentPage > 0) {
                                    scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                                }
                            },
                            onNext = {
                                if (currentPage < models.lastIndex) {
                                    scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // 保存提示在短时间后自动消失，避免常驻遮挡画面。
    LaunchedEffect(saveMessage) {
        if (saveMessage != null) {
            kotlinx.coroutines.delay(SAVE_MESSAGE_DURATION_MS)
            saveMessage = null
        }
    }
}

/**
 * 编辑会话：持有原图与全部待应用笔画 / 文字 / 裁切框。
 *
 * 与 UI 解耦（纯 Bitmap 操作），便于在不同查看入口复用与单元测试。
 */
internal class ImageEditSession(val base: Bitmap) {
    var crop by mutableStateOf<RectF?>(null)
    val strokes = mutableStateListOf<DoodleStroke>()
    val texts = mutableStateListOf<TextStamp>()
    var liveStroke by mutableStateOf<DoodleStroke?>(null)
    var pendingTextPosition by mutableStateOf<Offset?>(null)

    /** 把当前会话内容合成为一张新位图（不修改 [base]）。 */
    fun render(): Bitmap {
        var result = base
        if (strokes.isNotEmpty() || liveStroke != null || texts.isNotEmpty()) {
            val mutable = base.copy(Bitmap.Config.ARGB_8888, true) ?: base
            val canvas = android.graphics.Canvas(mutable)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            (strokes + listOfNotNull(liveStroke)).forEach { stroke ->
                if (stroke.points.size >= 2) {
                    paint.color = stroke.color
                    paint.strokeWidth = stroke.widthPx
                    val path = android.graphics.Path()
                    path.moveTo(stroke.points.first().x, stroke.points.first().y)
                    stroke.points.drop(1).forEach { point -> path.lineTo(point.x, point.y) }
                    canvas.drawPath(path, paint)
                }
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                textAlign = Paint.Align.LEFT
            }
            texts.forEach { stamp ->
                textPaint.color = stamp.color
                textPaint.textSize = stamp.sizePx
                canvas.drawText(stamp.text, stamp.position.x, stamp.position.y, textPaint)
            }
            result = mutable
        }
        val rect = crop
        if (rect != null && !rect.isEmpty) {
            val left = rect.left.toInt().coerceIn(0, max(0, result.width - 1))
            val top = rect.top.toInt().coerceIn(0, max(0, result.height - 1))
            val width = rect.width().toInt().coerceIn(1, result.width - left)
            val height = rect.height().toInt().coerceIn(1, result.height - top)
            if (width > 1 && height > 1 &&
                (width != result.width || height != result.height)
            ) {
                result = Bitmap.createBitmap(result, left, top, width, height)
            }
        }
        return result
    }
}

/** 一条涂鸦笔画（坐标为原图像素）。 */
internal data class DoodleStroke(
    val points: List<Offset>,
    val color: Int,
    val widthPx: Float,
)

/** 一个文字贴片（坐标为原图像素，position 为文字基线左端）。 */
internal data class TextStamp(
    val text: String,
    val position: Offset,
    val color: Int,
    val sizePx: Float,
)

/** 编辑画布：按 Fit 显示原图，并叠加裁切框 / 笔画 / 文字。 */
@Composable
private fun ImageEditSurface(
    session: ImageEditSession,
    tool: ViewerEditTool,
    doodleColor: Color,
    doodleWidthDp: Float,
    textColor: Color,
    textSizeSp: Float,
) {
    val density = LocalDensity.current
    val bitmap = session.base
    var showTextInput by remember { mutableStateOf(false) }
    var textDraft by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val scale = min(widthPx / bitmap.width, heightPx / bitmap.height).takeIf { it > 0f } ?: 1f
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val originX = (widthPx - drawWidth) / 2f
        val originY = (heightPx - drawHeight) / 2f
        val image = remember(bitmap) { bitmap.asImageBitmap() }

        // 裁切框默认居中 80%，用户拖动后再调整。
        LaunchedEffect(bitmap, tool) {
            if (tool == ViewerEditTool.CROP && session.crop == null) {
                session.crop = RectF(
                    bitmap.width * CROP_DEFAULT_INSET,
                    bitmap.height * CROP_DEFAULT_INSET,
                    bitmap.width * (1f - CROP_DEFAULT_INSET),
                    bitmap.height * (1f - CROP_DEFAULT_INSET),
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(originX.toInt(), originY.toInt()),
                dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt()),
                filterQuality = FilterQuality.Medium,
            )
            // 笔画
            (session.strokes + listOfNotNull(session.liveStroke)).forEach { stroke ->
                if (stroke.points.size >= 2) {
                    val path = Path()
                    path.moveTo(
                        originX + stroke.points.first().x * scale,
                        originY + stroke.points.first().y * scale,
                    )
                    stroke.points.drop(1).forEach { point ->
                        path.lineTo(originX + point.x * scale, originY + point.y * scale)
                    }
                    drawPath(
                        path = path,
                        color = Color(stroke.color),
                        style = Stroke(
                            width = stroke.widthPx * scale,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
            // 文字
            session.texts.forEach { stamp ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stamp.color
                    textSize = stamp.sizePx * scale
                    typeface = android.graphics.Typeface.DEFAULT
                }
                drawContext.canvas.nativeCanvas.drawText(
                    stamp.text,
                    originX + stamp.position.x * scale,
                    originY + stamp.position.y * scale,
                    paint,
                )
            }
            // 裁切遮罩与边角
            val rect = session.crop
            if (tool == ViewerEditTool.CROP && rect != null) {
                val l = originX + rect.left * scale
                val t = originY + rect.top * scale
                val r = originX + rect.right * scale
                val b = originY + rect.bottom * scale
                val scrim = Color.Black.copy(alpha = 0.55f)
                drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(size.width, t))
                drawRect(scrim, topLeft = Offset(0f, b), size = Size(size.width, size.height - b))
                drawRect(scrim, topLeft = Offset(0f, t), size = Size(l, b - t))
                drawRect(scrim, topLeft = Offset(r, t), size = Size(size.width - r, b - t))
                drawRect(
                    color = Color.White,
                    topLeft = Offset(l, t),
                    size = Size(r - l, b - t),
                    style = Stroke(width = 2.dp.toPx()),
                )
                CROP_HANDLES.forEach { (hx, hy) ->
                    val cx = if (hx == 0f) l else r
                    val cy = if (hy == 0f) t else b
                    drawCircle(
                        color = Color.White,
                        radius = CROP_HANDLE_RADIUS.toPx(),
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        radius = CROP_HANDLE_RADIUS.toPx() * 0.5f,
                        center = Offset(cx, cy),
                    )
                }
            }
        }

        // 手势：裁切（移动 / 四角缩放 / 重新拉框）、涂鸦、文字（点按定位）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tool, scale, originX, originY) {
                    when (tool) {
                        ViewerEditTool.CROP -> {
                            var dragMode = CropDragMode.NONE
                            var anchor = Offset.Zero
                            detectDragGestures(
                                onDragStart = { position ->
                                    val rectScreen = session.crop?.let { r ->
                                        RectF(
                                            originX + r.left * scale,
                                            originY + r.top * scale,
                                            originX + r.right * scale,
                                            originY + r.bottom * scale,
                                        )
                                    }
                                    dragMode = resolveCropDrag(rectScreen, position, CROP_HANDLE_SLOP_PX)
                                    anchor = position
                                    if (dragMode == CropDragMode.NEW) {
                                        val bx = ((position.x - originX) / scale)
                                            .coerceIn(0f, bitmap.width.toFloat())
                                        val by = ((position.y - originY) / scale)
                                            .coerceIn(0f, bitmap.height.toFloat())
                                        session.crop = RectF(bx, by, bx, by)
                                    }
                                },
                                onDragEnd = {
                                    val rect = session.crop
                                    if (rect != null && (rect.width() < 8f || rect.height() < 8f)) {
                                        session.crop = null
                                    }
                                    dragMode = CropDragMode.NONE
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                val rect = session.crop ?: return@detectDragGestures
                                val dx = dragAmount.x / scale
                                val dy = dragAmount.y / scale
                                when (dragMode) {
                                    CropDragMode.MOVE -> rect.offset(dx, dy)
                                    CropDragMode.NEW -> {
                                        val cx = ((change.position.x - originX) / scale)
                                            .coerceIn(0f, bitmap.width.toFloat())
                                        val cy = ((change.position.y - originY) / scale)
                                            .coerceIn(0f, bitmap.height.toFloat())
                                        val ax = ((anchor.x - originX) / scale)
                                            .coerceIn(0f, bitmap.width.toFloat())
                                        val ay = ((anchor.y - originY) / scale)
                                            .coerceIn(0f, bitmap.height.toFloat())
                                        session.crop = RectF(
                                            min(ax, cx), min(ay, cy), max(ax, cx), max(ay, cy),
                                        )
                                    }
                                    CropDragMode.TOP_LEFT -> {
                                        rect.left += dx
                                        rect.top += dy
                                    }
                                    CropDragMode.TOP_RIGHT -> {
                                        rect.right += dx
                                        rect.top += dy
                                    }
                                    CropDragMode.BOTTOM_LEFT -> {
                                        rect.left += dx
                                        rect.bottom += dy
                                    }
                                    CropDragMode.BOTTOM_RIGHT -> {
                                        rect.right += dx
                                        rect.bottom += dy
                                    }
                                    CropDragMode.NONE -> Unit
                                }
                                // 统一夹取到图像范围内，并保持 left<right / top<bottom。
                                val r = session.crop ?: return@detectDragGestures
                                val l = r.left.coerceIn(0f, bitmap.width.toFloat() - 1f)
                                val t = r.top.coerceIn(0f, bitmap.height.toFloat() - 1f)
                                val rr = r.right.coerceIn(l + 1f, bitmap.width.toFloat())
                                val bb = r.bottom.coerceIn(t + 1f, bitmap.height.toFloat())
                                session.crop = RectF(l, t, rr, bb)
                            }
                        }
                        ViewerEditTool.DOODLE -> {
                            detectDragGestures(
                                onDragStart = { position ->
                                    session.liveStroke = DoodleStroke(
                                        points = listOf(
                                            Offset(
                                                ((position.x - originX) / scale)
                                                    .coerceIn(0f, bitmap.width.toFloat()),
                                                ((position.y - originY) / scale)
                                                    .coerceIn(0f, bitmap.height.toFloat()),
                                            ),
                                        ),
                                        color = doodleColor.toArgb(),
                                        widthPx = with(density) { doodleWidthDp.dp.toPx() } / scale,
                                    )
                                },
                                onDragEnd = {
                                    session.liveStroke?.let { stroke ->
                                        if (stroke.points.size >= 2) session.strokes.add(stroke)
                                    }
                                    session.liveStroke = null
                                },
                                onDragCancel = { session.liveStroke = null },
                            ) { change, _ ->
                                change.consume()
                                val stroke = session.liveStroke ?: return@detectDragGestures
                                val point = Offset(
                                    ((change.position.x - originX) / scale)
                                        .coerceIn(0f, bitmap.width.toFloat()),
                                    ((change.position.y - originY) / scale)
                                        .coerceIn(0f, bitmap.height.toFloat()),
                                )
                                session.liveStroke = stroke.copy(points = stroke.points + point)
                            }
                        }
                        ViewerEditTool.TEXT -> {
                            detectTapGestures { position ->
                                session.pendingTextPosition = Offset(
                                    ((position.x - originX) / scale)
                                        .coerceIn(0f, bitmap.width.toFloat()),
                                    ((position.y - originY) / scale)
                                        .coerceIn(0f, bitmap.height.toFloat()),
                                )
                                textDraft = ""
                                showTextInput = true
                            }
                        }
                        ViewerEditTool.NONE -> Unit
                    }
                },
        ) {
            AppText(
                text = stringResource(
                    when (tool) {
                        ViewerEditTool.CROP -> ResR.string.viewer_edit_crop
                        ViewerEditTool.DOODLE -> ResR.string.viewer_edit_doodle
                        ViewerEditTool.TEXT -> ResR.string.viewer_edit_text
                        ViewerEditTool.NONE -> ResR.string.viewer_edit_tools
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_m)),
            )
        }
    }

    if (showTextInput) {
        ViewerTextInputDialog(
            value = textDraft,
            onValueChange = { textDraft = it },
            onDismiss = {
                showTextInput = false
                session.pendingTextPosition = null
            },
            onConfirm = {
                val position = session.pendingTextPosition
                if (position != null && textDraft.isNotBlank()) {
                    val sizePx = with(density) { textSizeSp.sp.toPx() }
                    session.texts.add(
                        TextStamp(
                            text = textDraft,
                            // 点按位置作为文字基线，向下留出一个字高避免压住点击处。
                            position = Offset(position.x, position.y + sizePx),
                            color = textColor.toArgb(),
                            sizePx = sizePx,
                        ),
                    )
                }
                showTextInput = false
                session.pendingTextPosition = null
            },
        )
    }
}

/** 文字输入弹窗（编辑工具用；无液态玻璃，避免嵌套玻璃面叠层）。 */
@Composable
private fun ViewerTextInputDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(RoundCornerLarge))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_l)),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        ) {
            AppText(
                text = stringResource(ResR.string.viewer_edit_text),
                style = MaterialTheme.typography.titleSmall,
            )
            AppTextField(
                value = value,
                onValueChange = onValueChange,
                label = stringResource(ResR.string.viewer_edit_text_hint),
                singleLine = false,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AppTextButton(
                    onClick = onDismiss,
                    text = stringResource(ResR.string.viewer_edit_cancel),
                )
                AppTextButton(
                    onClick = onConfirm,
                    text = stringResource(ResR.string.viewer_edit_apply),
                )
            }
        }
    }
}

/** 编辑工具条：折叠栏内容（工具选择 + 样式 + 应用/取消/还原/保存）。 */
@Composable
private fun ViewerEditToolbar(
    tool: ViewerEditTool,
    hasEdited: Boolean,
    hasSession: Boolean,
    canSave: Boolean,
    doodleColor: Color,
    doodleWidthDp: Float,
    textColor: Color,
    textSizeSp: Float,
    onToolChange: (ViewerEditTool) -> Unit,
    onDoodleColorChange: (Color) -> Unit,
    onDoodleWidthChange: (Float) -> Unit,
    onTextColorChange: (Color) -> Unit,
    onTextSizeChange: (Float) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    val glassActive = rememberLiquidGlassActive()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nativeGlassSurface(
                shape = RoundedCornerShape(0.dp),
                enabled = glassActive,
                fallbackColor = Color.Black.copy(alpha = 0.45f),
            )
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
        verticalArrangement = Arrangement.spacedBy(
            dimensionResource(ResR.dimen.dimen_global_padding_s),
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s))) {
            AppIconButton(
                onClick = { onToolChange(ViewerEditTool.CROP) },
                imageVector = Icons.Filled.Crop,
                contentDescription = stringResource(ResR.string.viewer_edit_crop),
                tint = if (tool == ViewerEditTool.CROP) Color(0xFF7FB2FF) else Color.White,
                glass = false,
            )
            AppIconButton(
                onClick = { onToolChange(ViewerEditTool.DOODLE) },
                imageVector = Icons.Filled.Brush,
                contentDescription = stringResource(ResR.string.viewer_edit_doodle),
                tint = if (tool == ViewerEditTool.DOODLE) Color(0xFF7FB2FF) else Color.White,
                glass = false,
            )
            AppIconButton(
                onClick = { onToolChange(ViewerEditTool.TEXT) },
                imageVector = Icons.Filled.TextFields,
                contentDescription = stringResource(ResR.string.viewer_edit_text),
                tint = if (tool == ViewerEditTool.TEXT) Color(0xFF7FB2FF) else Color.White,
                glass = false,
            )
            Row(modifier = Modifier.weight(1f)) {}
            if (tool != ViewerEditTool.NONE) {
                AppIconButton(
                    onClick = onApply,
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(ResR.string.viewer_edit_apply),
                    tint = Color.White,
                    enabled = hasSession,
                    glass = false,
                )
                AppIconButton(
                    onClick = onCancel,
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(ResR.string.viewer_edit_cancel),
                    tint = Color.White,
                    glass = false,
                )
            } else {
                AppIconButton(
                    onClick = onReset,
                    imageVector = Icons.Filled.Restore,
                    contentDescription = stringResource(ResR.string.viewer_edit_reset),
                    tint = Color.White,
                    enabled = hasEdited,
                    glass = false,
                )
                if (canSave) {
                    AppIconButton(
                        onClick = onSave,
                        imageVector = Icons.Filled.SaveAlt,
                        contentDescription = stringResource(ResR.string.viewer_edit_save),
                        tint = Color.White,
                        enabled = hasEdited,
                        glass = false,
                    )
                }
            }
        }
        if (tool == ViewerEditTool.DOODLE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
            ) {
                EDIT_COLORS.forEach { color ->
                    ColorSwatch(
                        color = color,
                        selected = color == doodleColor,
                        onClick = { onDoodleColorChange(color) },
                    )
                }
                AppSlider(
                    value = doodleWidthDp,
                    onValueChange = onDoodleWidthChange,
                    valueRange = DOODLE_MIN_WIDTH_DP..DOODLE_MAX_WIDTH_DP,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (tool == ViewerEditTool.TEXT) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
            ) {
                EDIT_COLORS.forEach { color ->
                    ColorSwatch(
                        color = color,
                        selected = color == textColor,
                        onClick = { onTextColorChange(color) },
                    )
                }
                AppSlider(
                    value = textSizeSp,
                    onValueChange = onTextSizeChange,
                    valueRange = TEXT_MIN_SIZE_SP..TEXT_MAX_SIZE_SP,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 颜色圆点（编辑工具条用）。 */
@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) SELECTED_SWATCH_RING else 1.dp,
                color = if (selected) Color(0xFF7FB2FF) else Color.White.copy(alpha = 0.5f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/** 悬浮翻页栏：上一张 / 页码 / 下一张。 */
@Composable
private fun ViewerNavBar(
    page: Int,
    pageCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val glassActive = rememberLiquidGlassActive()
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .nativeGlassSurface(
                shape = CircleShape,
                enabled = glassActive,
                fallbackColor = Color.Black.copy(alpha = 0.42f),
            )
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
    ) {
        AppIconButton(
            onClick = onPrev,
            imageVector = Icons.Filled.ChevronLeft,
            contentDescription = stringResource(ResR.string.viewer_prev_page),
            tint = Color.White,
            enabled = page > 0,
            size = NAV_BUTTON_SIZE,
            glass = false,
        )
        AppText(
            text = stringResource(ResR.string.viewer_page_indicator, page + 1, pageCount),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            align = TextAlign.Center,
        )
        AppIconButton(
            onClick = onNext,
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = stringResource(ResR.string.viewer_next_page),
            tint = Color.White,
            enabled = page < pageCount - 1,
            size = NAV_BUTTON_SIZE,
            glass = false,
        )
    }
}

/**
 * 单张可缩放图片（ZoomImage）：全屏查看器与各业务页共用。
 *
 * 双指缩放 / 双击放大 / 缩放态拖动 / 超大图子采样由 ZoomImage 负责；
 * 未放大时的水平拖动交给外层 Pager，实现「缩放」与「翻页」共存。
 */
@Composable
fun AppZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalImageLoader.current,
) {
    var state by remember(model) { mutableStateOf<AsyncImagePainter.State?>(null) }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CoilZoomAsyncImage(
            model = model,
            contentDescription = contentDescription,
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            scrollBar = null,
            onState = { state = it },
        )
        when (state) {
            is AsyncImagePainter.State.Loading -> AppLoadingIndicator(color = Color.White)
            is AsyncImagePainter.State.Error -> Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
            )
            else -> Unit
        }
    }
}

/** 解码用于编辑的位图：关闭硬件位图（否则无法 copy / 绘制）并限制最大边，避免 OOM。 */
internal suspend fun loadEditableBitmap(context: Context, model: Any?): Bitmap? = runCatching {
    val request = ImageRequest.Builder(context)
        .data(model)
        .allowHardware(false)
        .size(EDIT_MAX_EDGE_PX, EDIT_MAX_EDGE_PX)
        .build()
    val result = context.imageLoader.execute(request)
    val drawable = result.drawable as? BitmapDrawable ?: return@runCatching null
    val bitmap = drawable.bitmap
    if (bitmap.config == Bitmap.Config.ARGB_8888 && !bitmap.isRecycled) {
        bitmap
    } else {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }
}.getOrNull()

/** 裁切框拖动模式。 */
private enum class CropDragMode { NONE, NEW, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** 四角手柄（归一化：0 = 左/上，1 = 右/下）。 */
private val CROP_HANDLES = listOf(
    0f to 0f,
    1f to 0f,
    0f to 1f,
    1f to 1f,
)

/** 根据手指落点判断当前裁切手势：先判四角，再判内部移动，否则重新拉框。 */
private fun resolveCropDrag(rect: RectF?, position: Offset, slopPx: Float): CropDragMode {
    if (rect == null) return CropDragMode.NEW
    val corners = listOf(
        CropDragMode.TOP_LEFT to Offset(rect.left, rect.top),
        CropDragMode.TOP_RIGHT to Offset(rect.right, rect.top),
        CropDragMode.BOTTOM_LEFT to Offset(rect.left, rect.bottom),
        CropDragMode.BOTTOM_RIGHT to Offset(rect.right, rect.bottom),
    )
    corners.forEach { (mode, corner) ->
        if ((position - corner).getDistance() <= slopPx) return mode
    }
    return if (rect.contains(position.x, position.y)) CropDragMode.MOVE else CropDragMode.NEW
}

private val RoundCornerLarge = 24.dp
private val SWATCH_SIZE = 26.dp
private val SELECTED_SWATCH_RING = 2.5.dp
private val NAV_BUTTON_SIZE = 40.dp
private const val NAV_BAR_BOTTOM_OFFSET_RATIO = 0.2f
private const val CROP_DEFAULT_INSET = 0.1f
private const val CROP_HANDLE_SLOP_PX = 64f
private val CROP_HANDLE_RADIUS = 9.dp
private const val DOODLE_MIN_WIDTH_DP = 1f
private const val DOODLE_MAX_WIDTH_DP = 20f
private const val TEXT_MIN_SIZE_SP = 12f
private const val TEXT_MAX_SIZE_SP = 96f
private const val EDIT_MAX_EDGE_PX = 3000
private const val SAVE_MESSAGE_DURATION_MS = 2000L

/** 编辑工具条可选颜色：白 / 黑 / 红 / 蓝 / 绿。 */
private val EDIT_COLORS = listOf(
    Color.White,
    Color.Black,
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF43A047),
)
