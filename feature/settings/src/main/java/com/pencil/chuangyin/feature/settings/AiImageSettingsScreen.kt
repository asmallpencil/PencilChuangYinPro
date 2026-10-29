package com.pencil.chuangyin.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.agent.aiimage.AiImageScene
import com.pencil.chuangyin.core.agent.aiimage.AiImageSettings
import com.pencil.chuangyin.core.agent.aiimage.AiImageSettingsBounds
import com.pencil.chuangyin.core.agent.config.AiModelConfig
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.resources.R as ResR

/** 「AI 图片处理设置」独立页面：与 AI 打印共用接口配置，此处只放图片处理专属参数。 */
@Composable
fun AiImageSettingsRoute(
    onBack: () -> Unit,
    onOpenAiConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AiImageSettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    AiImageSettingsScreen(
        settings = settings,
        activeModel = activeModel,
        onSelectScene = viewModel::setDefaultScene,
        onOutputMaxEdgeChange = viewModel::setOutputMaxEdgePx,
        onWhitenStrengthChange = viewModel::setWhitenStrength,
        onEnhanceStrengthChange = viewModel::setEnhanceStrength,
        onFallbackChange = viewModel::setFallbackToLocal,
        onOpenAiConfig = onOpenAiConfig,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiImageSettingsScreen(
    settings: AiImageSettings,
    activeModel: AiModelConfig?,
    onSelectScene: (AiImageScene) -> Unit,
    onOutputMaxEdgeChange: (Int) -> Unit,
    onWhitenStrengthChange: (Float) -> Unit,
    onEnhanceStrengthChange: (Float) -> Unit,
    onFallbackChange: (Boolean) -> Unit,
    onOpenAiConfig: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassActive = rememberLiquidGlassActive()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.settings_ai_image_title)) },
                navigationIcon = { BackIcon(onBack) },
                modifier = Modifier.nativeGlassSurface(
                    shape = RoundedCornerShape(0.dp),
                    enabled = glassActive,
                    fallbackColor = MaterialTheme.colorScheme.surface,
                ),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        var appeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appeared = true }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_xl)),
        ) {
            SettingsGroup(
                label = stringResource(R.string.settings_ai_image_group_model),
                visible = appeared,
                delayMillis = 0,
            ) {
                ModelRow(activeModel = activeModel, onOpenAiConfig = onOpenAiConfig)
                SettingsGroupNote(text = stringResource(R.string.settings_ai_image_model_hint))
            }
            SettingsGroup(
                label = stringResource(R.string.settings_ai_image_group_scene),
                visible = appeared,
                delayMillis = 70,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                        vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
                    ),
                ) {
                    AppSegmentedRow(
                        options = listOf(
                            stringResource(R.string.settings_ai_image_scene_real),
                            stringResource(R.string.settings_ai_image_scene_document),
                        ),
                        selectedIndex = if (settings.defaultScene == AiImageScene.Document) 1 else 0,
                        onSelect = { index ->
                            onSelectScene(
                                if (index == 1) AiImageScene.Document else AiImageScene.RealScene,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingsGroupNote(text = stringResource(R.string.settings_ai_image_scene_hint))
            }
            SettingsGroup(
                label = stringResource(R.string.settings_ai_image_group_output),
                visible = appeared,
                delayMillis = 140,
            ) {
                SliderBlock(
                    title = stringResource(R.string.settings_ai_image_output_edge),
                    valueText = stringResource(
                        R.string.settings_ai_image_output_edge_value,
                        settings.outputMaxEdgePx,
                    ),
                    hint = stringResource(R.string.settings_ai_image_output_edge_hint),
                ) {
                    AppSlider(
                        value = settings.outputMaxEdgePx.toFloat(),
                        onValueChange = { onOutputMaxEdgeChange(it.toInt()) },
                        valueRange = AiImageSettingsBounds.OUTPUT_EDGE_MIN_PX.toFloat()..
                            AiImageSettingsBounds.OUTPUT_EDGE_MAX_PX.toFloat(),
                        // 每 100px 一档，避免滑杆停在无意义的个位数值上。
                        steps = OUTPUT_EDGE_STEP_PX,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SliderBlock(
                    title = stringResource(R.string.settings_ai_image_whiten),
                    valueText = stringResource(
                        R.string.settings_ai_image_percent_value,
                        (settings.whitenStrength * PERCENT).toInt(),
                    ),
                    hint = stringResource(R.string.settings_ai_image_whiten_hint),
                ) {
                    AppSlider(
                        value = settings.whitenStrength,
                        onValueChange = onWhitenStrengthChange,
                        valueRange = AiImageSettingsBounds.STRENGTH_MIN..AiImageSettingsBounds.STRENGTH_MAX,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SliderBlock(
                    title = stringResource(R.string.settings_ai_image_enhance),
                    valueText = stringResource(
                        R.string.settings_ai_image_percent_value,
                        (settings.enhanceStrength * PERCENT).toInt(),
                    ),
                    hint = stringResource(R.string.settings_ai_image_enhance_hint),
                ) {
                    AppSlider(
                        value = settings.enhanceStrength,
                        onValueChange = onEnhanceStrengthChange,
                        valueRange = AiImageSettingsBounds.STRENGTH_MIN..AiImageSettingsBounds.STRENGTH_MAX,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            SettingsGroup(
                label = stringResource(R.string.settings_ai_image_fallback),
                visible = appeared,
                delayMillis = 210,
            ) {
                SettingSwitchRow(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.settings_ai_image_fallback),
                    desc = stringResource(R.string.settings_ai_image_fallback_desc),
                    checked = settings.fallbackToLocal,
                    onCheckedChange = onFallbackChange,
                )
                SettingsGroupNote(text = stringResource(R.string.settings_ai_image_fallback_hint))
            }
        }
    }
}

/** 当前模型行：有可用模型时展示名称与模型名，否则展示未配置，并统一提供跳转入口。 */
@Composable
private fun ModelRow(
    activeModel: AiModelConfig?,
    onOpenAiConfig: () -> Unit,
) {
    val usable = activeModel?.isUsable == true
    SettingsEntry(
        icon = Icons.Filled.Tune,
        title = if (usable) {
            activeModel?.displayName.orEmpty().ifBlank { activeModel?.modelName.orEmpty() }
        } else {
            stringResource(R.string.settings_ai_image_model_none)
        },
        desc = if (usable) {
            stringResource(
                R.string.settings_ai_image_model_ready,
                activeModel?.displayName.orEmpty().ifBlank { activeModel?.modelName.orEmpty() },
                activeModel?.modelName.orEmpty(),
            )
        } else {
            stringResource(R.string.settings_ai_image_open_config)
        },
        onClick = onOpenAiConfig,
    )
}

/** 滑杆区块：标题 + 数值 + 滑杆 + 说明，三个参数共用同一视觉节奏。 */
@Composable
private fun SliderBlock(
    title: String,
    valueText: String,
    hint: String,
    slider: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(
            horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
            vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
        ),
    ) {
        AppText(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        AppText(
            text = valueText,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        slider()
        AppText(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 输出最长边的档位数：总区间按 [OUTPUT_EDGE_STEP_PX] 等分。 */
private const val OUTPUT_EDGE_STEP_PX =
    (AiImageSettingsBounds.OUTPUT_EDGE_MAX_PX - AiImageSettingsBounds.OUTPUT_EDGE_MIN_PX) / 100 - 1

private const val PERCENT = 100f
