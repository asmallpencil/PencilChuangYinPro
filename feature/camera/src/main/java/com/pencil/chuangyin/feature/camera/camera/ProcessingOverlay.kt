package com.pencil.chuangyin.feature.camera.camera

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.feature.camera.ProcessStage
import com.pencil.chuangyin.feature.camera.R
import com.pencil.chuangyin.resources.R as ResR
import kotlinx.coroutines.delay

/**
 * 拍摄后自动处理动画：LoadingIndicator + 分阶段文案。
 * 文档模式按 边缘→透视→光照→文字 轮换，常规模式仅一条处理文案。
 */
@Composable
internal fun ProcessingOverlay(
    stage: ProcessStage,
    modifier: Modifier = Modifier,
) {
    val docStages = if (stage == ProcessStage.Normal) {
        listOf(R.string.camera_processing_normal)
    } else {
        listOf(
            R.string.camera_processing_edge,
            R.string.camera_processing_warp,
            R.string.camera_processing_light,
            R.string.camera_processing_text,
        )
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(stage) {
        index = 0
        while (docStages.size > 1) {
            delay(STAGE_INTERVAL_MS)
            index = (index + 1) % docStages.size
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
            .padding(dimensionResource(ResR.dimen.dimen_camera_process_padding)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // M3 Expressive 的 morphing 指示器：在多种形状之间连续变形并旋转，
        // 比 MD2 的"死板转圈"更能传达"正在精细处理"的观感。
        AppLoadingIndicator(color = CameraTokens.content)
        AnimatedContent(
            targetState = docStages[index],
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "processingText",
            modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_camera_process_text_gap)),
        ) { resId ->
            AppText(
                text = stringResource(resId),
                style = MaterialTheme.typography.bodyLarge,
                color = CameraTokens.content,
            )
        }
    }
}

private const val STAGE_INTERVAL_MS = 350L
private const val SCRIM_ALPHA = 0.78f
