package com.pencil.chuangyin.feature.camera.camera

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.camera.ProcessStage
import com.pencil.chuangyin.feature.camera.R
import com.pencil.chuangyin.resources.R as ResR
import kotlinx.coroutines.delay

/**
 * 拍摄后自动处理动画：LoadingIndicator + 分阶段文案。
 *
 * - 文档模式按 边缘→透视→光照→文字 轮换，常规模式仅一条处理文案；
 * - [logLines] 非空时右上角出现日志开关，展开后显示实时处理日志（默认折叠）；
 * - [onCancel] 非空时左上角出现取消按钮（仅 AI 处理提供）。
 *
 * 处理日志属于"过程细节"，默认收起，避免遮挡主状态；展开与收起走 M3 的滑入淡出。
 */
@Composable
internal fun ProcessingOverlay(
    stage: ProcessStage,
    modifier: Modifier = Modifier,
    logLines: List<String> = emptyList(),
    onCancel: (() -> Unit)? = null,
) {
    val docStages = when (stage) {
        ProcessStage.Normal -> listOf(R.string.camera_processing_normal)
        ProcessStage.Ai -> listOf(R.string.camera_processing_ai)
        ProcessStage.Edge, ProcessStage.Warp, ProcessStage.Light, ProcessStage.Text -> listOf(
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

    // 日志展开状态按处理阶段重置：每次进入新的处理都从折叠开始。
    var logExpanded by remember(stage) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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

        if (onCancel != null) {
            CircleIconButton(
                icon = Icons.Filled.Close,
                description = stringResource(R.string.camera_log_cancel),
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(
                        start = dimensionResource(ResR.dimen.dimen_global_padding_l),
                        top = dimensionResource(ResR.dimen.dimen_global_padding_s),
                    ),
            )
        }

        if (logLines.isNotEmpty()) {
            CircleIconButton(
                icon = if (logExpanded) Icons.Filled.ExpandMore else Icons.Filled.Description,
                description = stringResource(
                    if (logExpanded) R.string.camera_log_collapse else R.string.camera_log_expand,
                ),
                onClick = { logExpanded = !logExpanded },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(
                        end = dimensionResource(ResR.dimen.dimen_global_padding_l),
                        top = dimensionResource(ResR.dimen.dimen_global_padding_s),
                    ),
            )
        }

        AiLogPanel(
            lines = logLines,
            visible = logExpanded,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(
                    start = dimensionResource(ResR.dimen.dimen_global_padding_l),
                    end = dimensionResource(ResR.dimen.dimen_global_padding_l),
                    top = LOG_PANEL_TOP_OFFSET,
                ),
        )
    }
}

/**
 * 处理日志面板：半透明深色卡片 + 可滚动日志列表，始终滚动到最新一行。
 * 只有 AI 处理会产出日志，内置算法的阶段不会出现该面板。
 */
@Composable
private fun AiLogPanel(
    lines: List<String>,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = rememberAppMotion()
    val scrollState = rememberScrollState()
    LaunchedEffect(lines.size, visible) {
        if (visible && lines.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
    }
    AnimatedVisibility(
        visible = visible && lines.isNotEmpty(),
        enter = fadeIn(motion.effectSpec()) + slideInVertically(motion.spatialFastSpec()) { -it / 3 },
        exit = fadeOut(motion.effectFastSpec()) + slideOutVertically(motion.spatialFastSpec()) { -it / 3 },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LOG_PANEL_CORNER))
                .background(Color.Black.copy(alpha = LOG_PANEL_ALPHA))
                .padding(LOG_PANEL_PADDING),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LOG_PANEL_PADDING / 2),
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = CameraTokens.contentSecondary,
                )
                AppText(
                    text = stringResource(R.string.camera_log_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = CameraTokens.contentSecondary,
                )
                AppText(
                    text = lines.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = CameraTokens.contentSecondary,
                )
            }
            Column(
                modifier = Modifier
                    .heightIn(max = LOG_PANEL_MAX_HEIGHT)
                    .verticalScroll(scrollState),
            ) {
                lines.forEach { line ->
                    AppText(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = CameraTokens.content,
                    )
                }
            }
        }
    }
}

private const val STAGE_INTERVAL_MS = 350L
private const val SCRIM_ALPHA = 0.78f
private const val LOG_PANEL_ALPHA = 0.82f
private val LOG_PANEL_TOP_OFFSET = 64.dp
private val LOG_PANEL_PADDING = 12.dp
private val LOG_PANEL_CORNER = 20.dp
private val LOG_PANEL_MAX_HEIGHT = 240.dp
