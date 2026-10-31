package com.pencil.chuangyin.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.theme.LOADING_DURATION_MAX_MS
import com.pencil.chuangyin.core.theme.LOADING_DURATION_MIN_MS
import com.pencil.chuangyin.core.theme.LOADING_PARTICLE_MAX
import com.pencil.chuangyin.core.theme.LOADING_PARTICLE_MIN
import com.pencil.chuangyin.core.theme.LOADING_PULSE_MAX_MS
import com.pencil.chuangyin.core.theme.LOADING_PULSE_MIN_MS
import com.pencil.chuangyin.core.theme.LOADING_ROTATION_MAX_MS
import com.pencil.chuangyin.core.theme.LOADING_ROTATION_MIN_MS
import com.pencil.chuangyin.core.theme.LOADING_STROKE_MAX
import com.pencil.chuangyin.core.theme.LOADING_STROKE_MIN
import com.pencil.chuangyin.core.theme.LOADING_TRAIL_MAX
import com.pencil.chuangyin.core.theme.LOADING_TRAIL_MIN
import com.pencil.chuangyin.core.theme.LoadingAnimationStyle
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.resources.R as ResR
import kotlin.math.roundToInt

/**
 * 「加载动画」独立页面：样式选择 + 6 项全局参数 + 实时预览。
 *
 * 预览直接复用 [AppLoadingIndicator]，而它读取的是
 * [com.pencil.chuangyin.core.theme.LocalLoadingAnimation]（由 AppTheme 从主题状态下发），
 * 所以参数经 DataStore 落盘后预览即时变化，全局所有页面的加载指示器一起生效。
 */
@Composable
fun LoadingSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoadingSettingsScreen(
        state = state,
        onBack = onBack,
        onStyleSelected = viewModel::setLoadingAnimationStyle,
        onParticleCountChange = viewModel::setLoadingParticleCount,
        onTrailChange = viewModel::setLoadingTrailSpan,
        onDurationChange = viewModel::setLoadingDurationMs,
        onPulseChange = viewModel::setLoadingPulseDurationMs,
        onRotationChange = viewModel::setLoadingRotationDurationMs,
        onStrokeChange = viewModel::setLoadingStrokeWidth,
        modifier = modifier,
    )
}

@Composable
fun LoadingSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onStyleSelected: (LoadingAnimationStyle) -> Unit,
    onParticleCountChange: (Int) -> Unit,
    onTrailChange: (Float) -> Unit,
    onDurationChange: (Int) -> Unit,
    onPulseChange: (Int) -> Unit,
    onRotationChange: (Int) -> Unit,
    onStrokeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = state.themeState
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    SettingsSubPage(
        title = stringResource(R.string.settings_menu_loading),
        onBack = onBack,
        modifier = modifier,
    ) {
        SettingsGroup(
            label = stringResource(R.string.settings_label_loading),
            visible = appeared,
            delayMillis = 0,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                    vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
                ),
            ) {
                LoadingPreview()
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                LoadingStyleRow(
                    selected = theme.loadingAnimationStyle,
                    onSelected = onStyleSelected,
                )
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                LoadingParamSlider(
                    label = stringResource(R.string.settings_loading_particles),
                    valueText = theme.loadingParticleCount.toString(),
                    value = theme.loadingParticleCount.toFloat(),
                    range = LOADING_PARTICLE_MIN.toFloat()..LOADING_PARTICLE_MAX.toFloat(),
                    steps = LOADING_PARTICLE_MAX - LOADING_PARTICLE_MIN - 1,
                    onValueChange = { onParticleCountChange(it.roundToInt()) },
                )
                LoadingParamSlider(
                    label = stringResource(R.string.settings_loading_trail),
                    valueText = twoDecimals(theme.loadingTrailSpan),
                    value = theme.loadingTrailSpan,
                    range = LOADING_TRAIL_MIN..LOADING_TRAIL_MAX,
                    steps = ((LOADING_TRAIL_MAX - LOADING_TRAIL_MIN) / 0.01f).roundToInt() - 1,
                    onValueChange = onTrailChange,
                )
                LoadingParamSlider(
                    label = stringResource(R.string.settings_loading_loop),
                    valueText = stringResource(R.string.settings_loading_seconds, theme.loadingDurationMs / 1000f),
                    value = theme.loadingDurationMs.toFloat(),
                    range = LOADING_DURATION_MIN_MS.toFloat()..LOADING_DURATION_MAX_MS.toFloat(),
                    steps = (LOADING_DURATION_MAX_MS - LOADING_DURATION_MIN_MS) / LOADING_DURATION_STEP_MS - 1,
                    onValueChange = { onDurationChange(it.roundToInt()) },
                )
                LoadingParamSlider(
                    label = stringResource(R.string.settings_loading_pulse),
                    valueText = stringResource(R.string.settings_loading_seconds, theme.loadingPulseDurationMs / 1000f),
                    value = theme.loadingPulseDurationMs.toFloat(),
                    range = LOADING_PULSE_MIN_MS.toFloat()..LOADING_PULSE_MAX_MS.toFloat(),
                    steps = (LOADING_PULSE_MAX_MS - LOADING_PULSE_MIN_MS) / LOADING_PULSE_STEP_MS - 1,
                    onValueChange = { onPulseChange(it.roundToInt()) },
                )
                LoadingParamSlider(
                    label = stringResource(R.string.settings_loading_rotate),
                    valueText = stringResource(R.string.settings_loading_seconds, theme.loadingRotationDurationMs / 1000f),
                    value = theme.loadingRotationDurationMs.toFloat(),
                    range = LOADING_ROTATION_MIN_MS.toFloat()..LOADING_ROTATION_MAX_MS.toFloat(),
                    steps = (LOADING_ROTATION_MAX_MS - LOADING_ROTATION_MIN_MS) / LOADING_ROTATION_STEP_MS - 1,
                    onValueChange = { onRotationChange(it.roundToInt()) },
                )
                LoadingParamSlider(
                    label = stringResource(R.string.settings_loading_stroke),
                    valueText = twoDecimals(theme.loadingStrokeWidth),
                    value = theme.loadingStrokeWidth,
                    range = LOADING_STROKE_MIN..LOADING_STROKE_MAX,
                    steps = ((LOADING_STROKE_MAX - LOADING_STROKE_MIN) / 0.1f).roundToInt() - 1,
                    onValueChange = onStrokeChange,
                )
            }
        }
        SettingsGroupNote(text = stringResource(R.string.settings_loading_hint))
    }
}

/** 实时预览：左侧是当前的加载指示器，右侧是行为说明。 */
@Composable
private fun LoadingPreview() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            AppLoadingIndicator(
                size = 64.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(16.dp))
        AppText(
            text = stringResource(R.string.settings_loading_preview),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 加载动画样式选项：横向 chip，选中时用主色容器 + 勾选图标。 */
@Composable
private fun LoadingStyleRow(
    selected: LoadingAnimationStyle,
    onSelected: (LoadingAnimationStyle) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(LoadingAnimationStyle.entries, key = { it.id }) { style ->
            LoadingStyleChip(
                label = stringResource(loadingStyleLabel(style)),
                selected = selected == style,
                onClick = { onSelected(style) },
            )
        }
    }
}

@Composable
private fun LoadingStyleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        AppText(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}

/** 参数滑杆：标题带上当前值，避免用户拖动时看不到数值。 */
@Composable
private fun LoadingParamSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    SectionLabel("$label · $valueText")
    AppSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        steps = steps.coerceAtLeast(0),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 两位小数展示（避免 Float.toString 出现 0.3799999 之类的长尾）。 */
private fun twoDecimals(value: Float): String =
    ((value * 100f).roundToInt() / 100f).toString()

/** 时长类滑杆的步长（毫秒），与 math-curve-loaders 的可调分辨率保持一致量级。 */
private const val LOADING_DURATION_STEP_MS = 200
private const val LOADING_PULSE_STEP_MS = 200
private const val LOADING_ROTATION_STEP_MS = 1000

private fun loadingStyleLabel(style: LoadingAnimationStyle): Int = when (style) {
    LoadingAnimationStyle.Classic -> R.string.loading_classic
    LoadingAnimationStyle.ThinkingRose -> R.string.loading_thinking_rose
    LoadingAnimationStyle.ThinkingFive -> R.string.loading_thinking_five
    LoadingAnimationStyle.ThinkingNine -> R.string.loading_thinking_nine
    LoadingAnimationStyle.RoseOrbit -> R.string.loading_rose_orbit
    LoadingAnimationStyle.RoseTwo -> R.string.loading_rose_two
    LoadingAnimationStyle.RoseThree -> R.string.loading_rose_three
    LoadingAnimationStyle.RoseFour -> R.string.loading_rose_four
    LoadingAnimationStyle.LissajousDrift -> R.string.loading_lissajous_drift
    LoadingAnimationStyle.LemniscateBloom -> R.string.loading_lemniscate_bloom
    LoadingAnimationStyle.HypotrochoidLoop -> R.string.loading_hypotrochoid_loop
    LoadingAnimationStyle.SpiralThree -> R.string.loading_spiral_three
    LoadingAnimationStyle.SpiralFour -> R.string.loading_spiral_four
    LoadingAnimationStyle.SpiralFive -> R.string.loading_spiral_five
    LoadingAnimationStyle.SpiralSix -> R.string.loading_spiral_six
    LoadingAnimationStyle.ButterflyPhase -> R.string.loading_butterfly_phase
    LoadingAnimationStyle.CardioidGlow -> R.string.loading_cardioid_glow
    LoadingAnimationStyle.CardioidHeart -> R.string.loading_cardioid_heart
    LoadingAnimationStyle.HeartWave -> R.string.loading_heart_wave
    LoadingAnimationStyle.SpiralSearch -> R.string.loading_spiral_search
    LoadingAnimationStyle.FourierFlow -> R.string.loading_fourier_flow
}
