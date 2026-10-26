package com.pencil.chuangyin.feature.camera.result

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.feature.camera.CameraViewModel
import com.pencil.chuangyin.feature.camera.ProcessStage
import com.pencil.chuangyin.feature.camera.R
import com.pencil.chuangyin.feature.camera.camera.CameraTokens
import com.pencil.chuangyin.feature.camera.camera.ProcessingOverlay
import com.pencil.chuangyin.resources.R as ResR
import kotlin.math.roundToInt

private enum class EditMode { None, Whiteout, Crop }

/**
 * 拍摄结果页：
 * 顶部无栏（全屏看图），底部依次为编辑工具（涂白/撤销/裁剪）与大叉（重拍）/大勾（确认打印）。
 * 裁剪中底部切换为 取消/完成；裁剪异步计算期间全屏封锁交互，成功后才退出裁剪态。
 */
@Composable
internal fun ResultScreen(
    viewModel: CameraViewModel,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = viewModel.resultBitmap ?: return
    var editMode by remember { mutableStateOf(EditMode.None) }
    var cropPoints by remember(bitmap) {
        mutableStateOf(
            listOf(
                Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f),
            ),
        )
    }
    val busy = viewModel.cropBusy

    // 裁剪成功才退出裁剪态；失败保留裁剪框让用户调整或取消。
    LaunchedEffect(Unit) {
        viewModel.cropDone.collect { success ->
            if (success) editMode = EditMode.None
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CameraTokens.scrim),
    ) {
        ResultCanvas(
            bitmap = bitmap,
            editGeneration = viewModel.editGeneration,
            editMode = editMode,
            cropPoints = cropPoints,
            onCropPointsChange = { cropPoints = it },
            onCommitStroke = viewModel::commitStroke,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(CameraTokens.scrim.copy(alpha = 0.55f))
                .navigationBarsPadding()
                .padding(
                    horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l),
                    vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (editMode == EditMode.Crop) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                ) {
                    AppOutlinedButton(
                        text = stringResource(R.string.camera_edit_cancel),
                        onClick = { editMode = EditMode.None },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                    AppOutlinedButton(
                        text = stringResource(R.string.camera_edit_done),
                        onClick = { viewModel.applyCrop(cropPoints) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                EditToolbar(
                    whiteoutActive = editMode == EditMode.Whiteout,
                    canUndo = viewModel.canUndo && !busy,
                    enabled = !busy,
                    onWhiteout = {
                        editMode = if (editMode == EditMode.Whiteout) EditMode.None else EditMode.Whiteout
                    },
                    onUndo = viewModel::undo,
                    onCrop = { editMode = EditMode.Crop },
                )
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
                ConfirmActions(
                    enabled = !busy,
                    onRetake = { viewModel.retake(); onRetake() },
                    onConfirm = viewModel::confirm,
                )
            }
        }

        if (busy) {
            // 最上层：吞掉全部指针事件，防止裁剪计算期间操作/回收同一位图。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                ProcessingOverlay(stage = ProcessStage.Warp)
            }
        }
    }
}

@Composable
private fun EditToolbar(
    whiteoutActive: Boolean,
    canUndo: Boolean,
    enabled: Boolean,
    onWhiteout: () -> Unit,
    onUndo: () -> Unit,
    onCrop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ToolButton(
            icon = Icons.Filled.Brush,
            label = stringResource(R.string.camera_edit_whiteout),
            active = whiteoutActive,
            enabled = enabled,
            onClick = onWhiteout,
        )
        ToolButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            label = stringResource(R.string.camera_edit_undo),
            active = false,
            enabled = canUndo,
            onClick = onUndo,
        )
        ToolButton(
            icon = Icons.Filled.Crop,
            label = stringResource(R.string.camera_edit_crop),
            active = false,
            enabled = enabled,
            onClick = onCrop,
        )
    }
}

@Composable
private fun ConfirmActions(enabled: Boolean, onRetake: () -> Unit, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionColumn(
            icon = Icons.Filled.Close,
            label = stringResource(R.string.camera_result_retake),
            containerColor = CameraTokens.content.copy(alpha = 0.18f),
            iconColor = CameraTokens.content,
            enabled = enabled,
            onClick = onRetake,
        )
        ActionColumn(
            icon = Icons.Filled.Check,
            label = stringResource(R.string.camera_result_confirm),
            containerColor = CameraTokens.content,
            iconColor = MaterialTheme.colorScheme.primary,
            enabled = enabled,
            onClick = onConfirm,
        )
    }
}

@Composable
private fun ActionColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(dimensionResource(ResR.dimen.dimen_camera_result_action_size))
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = iconColor)
        }
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        AppText(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = CameraTokens.contentSecondary,
        )
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val tint = when {
        !enabled -> CameraTokens.content.copy(alpha = 0.35f)
        active -> MaterialTheme.colorScheme.primary
        else -> CameraTokens.content
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s) / 2))
        AppText(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/**
 * 图片显示区：按 ContentScale.Fit 计算内容矩形；
 * 涂白模式下手指轨迹先在叠加层预览，抬手把整笔提交给 ViewModel 写入位图（可按笔撤销）；
 * 裁剪模式下在内容矩形上叠加 [CropOverlay]。
 */
@Composable
private fun ResultCanvas(
    bitmap: Bitmap,
    editGeneration: Int,
    editMode: EditMode,
    cropPoints: List<Offset>,
    onCropPointsChange: (List<Offset>) -> Unit,
    onCommitStroke: (List<Offset>, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val scale = minOf(viewW / bitmap.width, viewH / bitmap.height)
        val contentW = bitmap.width * scale
        val contentH = bitmap.height * scale
        val offX = (viewW - contentW) / 2f
        val offY = (viewH - contentH) / 2f
        val brushBmpPx = with(density) {
            dimensionResource(ResR.dimen.dimen_camera_whiteout_brush).toPx()
        } / scale

        var currentStroke by remember { mutableStateOf<List<Offset>?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // 订阅 editGeneration：涂白提交/撤销/裁剪后强制重绘位图。
                    @Suppress("UnusedExpression")
                    editGeneration
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(offX.roundToInt(), offY.roundToInt()),
                        dstSize = IntSize(contentW.roundToInt(), contentH.roundToInt()),
                    )
                    currentStroke?.takeIf { it.isNotEmpty() }?.let { pts ->
                        val path = Path().apply {
                            moveTo(offX + pts[0].x * scale, offY + pts[0].y * scale)
                            pts.drop(1).forEach {
                                lineTo(offX + it.x * scale, offY + it.y * scale)
                            }
                        }
                        drawPath(
                            path = path,
                            color = androidx.compose.ui.graphics.Color.White,
                            style = Stroke(
                                width = brushBmpPx * scale,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                },
        )

        if (editMode == EditMode.Whiteout) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap, scale, offX, offY, brushBmpPx) {
                        fun toBitmapPoint(pos: Offset) = Offset(
                            ((pos.x - offX) / scale).coerceIn(0f, bitmap.width.toFloat()),
                            ((pos.y - offY) / scale).coerceIn(0f, bitmap.height.toFloat()),
                        )
                        detectDragGestures(
                            onDragStart = { start ->
                                currentStroke = listOf(toBitmapPoint(start))
                            },
                            onDragEnd = {
                                currentStroke?.let { pts ->
                                    if (pts.isNotEmpty()) onCommitStroke(pts, brushBmpPx)
                                }
                                currentStroke = null
                            },
                            onDragCancel = { currentStroke = null },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStroke = (currentStroke ?: emptyList()) +
                                    toBitmapPoint(change.position)
                            },
                        )
                    },
            )
        }

        if (editMode == EditMode.Crop) {
            val offsetXDp = with(density) { offX.toDp() }
            val offsetYDp = with(density) { offY.toDp() }
            val widthDp = with(density) { contentW.toDp() }
            val heightDp = with(density) { contentH.toDp() }
            CropOverlay(
                points = cropPoints,
                onPointsChange = onCropPointsChange,
                contentSize = Size(contentW, contentH),
                modifier = Modifier
                    .offset(x = offsetXDp, y = offsetYDp)
                    .width(widthDp)
                    .height(heightDp),
            )
        }
    }
}
