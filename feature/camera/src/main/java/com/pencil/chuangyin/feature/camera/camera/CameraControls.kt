package com.pencil.chuangyin.feature.camera.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pencil.chuangyin.core.agent.aiimage.AiImageScene
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.feature.camera.CameraMode
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
 * 底部控制区：「裁切方式」小标题 + 四档模式导航（自动 / 手动 / AI 处理 / 不处理）
 * + AI 模式下额外出现的「被摄类型」子导航（实景 / 文稿）
 * + 相册 / 快门 / 闪光灯。背后为从透明到深色的渐变 scrim，保证白色控件在任意画面上可见。
 */
@Composable
internal fun CameraBottomBar(
    mode: CameraMode,
    aiScene: AiImageScene,
    flashAuto: Boolean,
    onSelectMode: (CameraMode) -> Unit,
    onSelectAiScene: (AiImageScene) -> Unit,
    onShutter: () -> Unit,
    onPickGallery: () -> Unit,
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
        AppText(
            text = stringResource(R.string.camera_crop_mode_title),
            style = MaterialTheme.typography.labelMedium,
            color = CameraTokens.contentSecondary,
        )
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))

        AppSegmentedRow(
            options = listOf(
                stringResource(R.string.camera_mode_auto),
                stringResource(R.string.camera_mode_manual),
                stringResource(R.string.camera_mode_ai),
                stringResource(R.string.camera_mode_none),
            ),
            selectedIndex = when (mode) {
                CameraMode.Auto -> 0
                CameraMode.Manual -> 1
                CameraMode.Ai -> 2
                CameraMode.None -> 3
            },
            onSelect = { index ->
                onSelectMode(
                    when (index) {
                        0 -> CameraMode.Auto
                        1 -> CameraMode.Manual
                        2 -> CameraMode.Ai
                        else -> CameraMode.None
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // AI 处理的子导航：只在选中 AI 时出现，选择"拍的是实景还是文稿"。
        AnimatedVisibility(
            visible = mode == CameraMode.Ai,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
                AppText(
                    text = stringResource(R.string.camera_ai_scene_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = CameraTokens.contentSecondary,
                )
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppSegmentedRow(
                    options = listOf(
                        stringResource(R.string.camera_ai_scene_real),
                        stringResource(R.string.camera_ai_scene_document),
                    ),
                    selectedIndex = if (aiScene == AiImageScene.Document) 1 else 0,
                    onSelect = { index ->
                        onSelectAiScene(
                            if (index == 1) AiImageScene.Document else AiImageScene.RealScene,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GalleryButton(onClick = onPickGallery)

            ShutterButton(onClick = onShutter)

            CircleIconButton(
                icon = if (flashAuto) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                description = stringResource(
                    if (flashAuto) R.string.camera_flash_auto else R.string.camera_flash_off,
                ),
                onClick = onToggleFlash,
                tint = if (flashAuto) MaterialTheme.colorScheme.primary else CameraTokens.contentSecondary,
            )
        }
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "shutter")
    val description = stringResource(R.string.camera_shutter)
    Box(
        modifier = Modifier
            .size(dimensionResource(ResR.dimen.dimen_camera_shutter_size))
            .scale(scale)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dimensionResource(ResR.dimen.dimen_camera_shutter_size))
                .border(
                    width = dimensionResource(ResR.dimen.dimen_camera_shutter_ring),
                    color = CameraTokens.content,
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(dimensionResource(ResR.dimen.dimen_camera_shutter_inner))
                .clip(CircleShape)
                .background(CameraTokens.content),
        )
    }
}

@Composable
private fun GalleryButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = stringResource(R.string.camera_gallery),
            tint = CameraTokens.content,
        )
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s) / 2))
        AppText(
            text = stringResource(R.string.camera_gallery),
            style = MaterialTheme.typography.labelSmall,
            color = CameraTokens.contentSecondary,
        )
    }
}

@Composable
internal fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = CameraTokens.content,
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
