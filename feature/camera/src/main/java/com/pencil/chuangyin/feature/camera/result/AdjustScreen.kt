package com.pencil.chuangyin.feature.camera.result

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.feature.camera.CameraViewModel
import com.pencil.chuangyin.feature.camera.ProcessStage
import com.pencil.chuangyin.feature.camera.R
import com.pencil.chuangyin.feature.camera.camera.CameraTokens
import com.pencil.chuangyin.feature.camera.camera.ProcessingOverlay
import com.pencil.chuangyin.resources.R as ResR
import kotlin.math.roundToInt

/**
 * 角点调整页（文档模式）：自动检测的边框在此交由用户核对——拖动 4 角/4 边中点对准纸边，
 * 点"生成"后按四角透视拉直 + 去阴影 + 黑白文档化，进入结果页；点"重拍"返回取景器。
 */
@Composable
internal fun AdjustScreen(
    viewModel: CameraViewModel,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = viewModel.adjustBitmap ?: return
    var points by remember(bitmap) { mutableStateOf(viewModel.adjustQuad) }
    val busy = viewModel.adjustBusy

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CameraTokens.scrim),
    ) {
        AdjustCanvas(
            bitmap = bitmap,
            points = points,
            onPointsChange = { points = it },
            modifier = Modifier.fillMaxSize(),
        )

        // 顶部操作提示。
        AppText(
            text = stringResource(R.string.camera_adjust_hint),
            style = MaterialTheme.typography.labelLarge,
            color = CameraTokens.content,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m))
                .clip(MaterialTheme.shapes.large)
                .background(CameraTokens.scrim.copy(alpha = 0.55f))
                .padding(
                    horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l),
                    vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                ),
        )

        // 底部：重拍 / 生成。
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(CameraTokens.scrim.copy(alpha = 0.55f))
                .navigationBarsPadding()
                .padding(
                    horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l),
                    vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
                )
                .let { it },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdjustAction(
                icon = Icons.Filled.Close,
                label = stringResource(R.string.camera_result_retake),
                containerColor = CameraTokens.content.copy(alpha = 0.18f),
                iconColor = CameraTokens.content,
                enabled = !busy,
                onClick = {
                    viewModel.retake()
                    onRetake()
                },
            )
            AdjustAction(
                icon = Icons.Filled.Check,
                label = stringResource(R.string.camera_adjust_apply),
                containerColor = CameraTokens.content,
                iconColor = MaterialTheme.colorScheme.primary,
                enabled = !busy,
                onClick = { viewModel.applyAdjust(points) },
            )
        }

        if (busy) {
            // 最上层：吞掉全部指针事件，防止拉直计算期间操作/回收同一位图。
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

/** 原图显示（Fit）+ [CropOverlay] 角点编辑。 */
@Composable
private fun AdjustCanvas(
    bitmap: Bitmap,
    points: List<Offset>,
    onPointsChange: (List<Offset>) -> Unit,
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(offX.roundToInt(), offY.roundToInt()),
                        dstSize = IntSize(contentW.roundToInt(), contentH.roundToInt()),
                    )
                },
        )

        CropOverlay(
            points = points,
            onPointsChange = onPointsChange,
            contentSize = androidx.compose.ui.geometry.Size(contentW, contentH),
            modifier = Modifier
                .offset(x = with(density) { offX.toDp() }, y = with(density) { offY.toDp() })
                .width(with(density) { contentW.toDp() })
                .height(with(density) { contentH.toDp() }),
        )
    }
}

@Composable
private fun AdjustAction(
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
