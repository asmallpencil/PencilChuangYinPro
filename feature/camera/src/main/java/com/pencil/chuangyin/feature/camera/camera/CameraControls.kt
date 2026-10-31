package com.pencil.chuangyin.feature.camera.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pencil.chuangyin.feature.camera.R
import com.pencil.chuangyin.resources.R as ResR

/**
 * 顶部栏：仅保留关闭（拍题式精简顶栏）。
 */
@Composable
internal fun CameraTopBar(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_camera_top_padding),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.Filled.Close,
            description = stringResource(R.string.camera_close),
            onClick = onClose,
        )
    }
}

/**
 * 底部控制区：闪光灯 + 快门。
 *
 * 拍照保持极简——不提供裁切模式、AI 处理与相册入口，这些能力分别在
 * 「文稿扫描」（FairScan）和图片打印页的「从相册选」里。
 */
@Composable
internal fun CameraBottomBar(
    flashAuto: Boolean,
    capturing: Boolean,
    onShutter: () -> Unit,
    onToggleFlash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to CameraTokens.panel.copy(alpha = 0f),
                    0.35f to CameraTokens.panel.copy(alpha = 0.55f),
                    1f to CameraTokens.panel.copy(alpha = 0.75f),
                ),
            )
            .navigationBarsPadding()
            .padding(
                top = dimensionResource(ResR.dimen.dimen_camera_controls_padding),
                bottom = dimensionResource(ResR.dimen.dimen_camera_bottom_padding),
                start = dimensionResource(ResR.dimen.dimen_global_padding_l),
                end = dimensionResource(ResR.dimen.dimen_global_padding_l),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = if (flashAuto) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                description = stringResource(
                    if (flashAuto) R.string.camera_flash_auto else R.string.camera_flash_off,
                ),
                onClick = onToggleFlash,
                tint = if (flashAuto) MaterialTheme.colorScheme.primary else CameraTokens.contentSecondary,
            )

            ShutterButton(enabled = !capturing, onClick = onShutter)

            // 右侧留白与左侧闪光灯等宽，保证快门居中。
            Box(modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_camera_side_btn_size)))
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.9f else 1f, label = "shutter")
    val description = stringResource(R.string.camera_shutter)
    Box(
        modifier = Modifier
            .size(dimensionResource(ResR.dimen.dimen_camera_shutter_size))
            .scale(scale)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dimensionResource(ResR.dimen.dimen_camera_shutter_size))
                .border(
                    width = dimensionResource(ResR.dimen.dimen_camera_shutter_ring),
                    color = CameraTokens.content.copy(alpha = if (enabled) 1f else 0.5f),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(dimensionResource(ResR.dimen.dimen_camera_shutter_inner))
                .clip(CircleShape)
                .background(CameraTokens.content.copy(alpha = if (enabled) 1f else 0.5f)),
        )
    }
}

@Composable
internal fun CircleIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CameraTokens.content,
) {
    Box(
        modifier = modifier
            .size(dimensionResource(ResR.dimen.dimen_camera_side_btn_size))
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint)
    }
}
