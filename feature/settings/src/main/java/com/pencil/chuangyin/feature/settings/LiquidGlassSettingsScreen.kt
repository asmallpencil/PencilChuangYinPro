package com.pencil.chuangyin.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.theme.LIQUID_GLASS_BLUR_MAX_DP
import com.pencil.chuangyin.core.theme.LIQUID_GLASS_LENS_AMOUNT_MAX_DP
import com.pencil.chuangyin.core.theme.LIQUID_GLASS_LENS_HEIGHT_MAX_DP
import com.pencil.chuangyin.core.theme.LIQUID_GLASS_SPECTRAL_MAX
import com.pencil.chuangyin.core.theme.LIQUID_GLASS_TINT_ALPHA_MIN
import com.pencil.chuangyin.core.theme.LiquidGlassEngine
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.theme.LiquidGlassRenderer
import com.pencil.chuangyin.core.theme.LocalLiquidGlassCppAvailable
import com.pencil.chuangyin.core.ui.components.AppAlertDialog
import com.pencil.chuangyin.core.ui.components.AppLiquidDialog
import com.pencil.chuangyin.core.ui.components.AppDivider
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppSwitch
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.components.LiquidGlassLayerScope
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.pseudoGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.resources.R as ResR

// ===================== 液态玻璃设置 =====================

@Composable
fun LiquidGlassSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiquidGlassSettingsScreen(
        state = state,
        onBack = onBack,
        onLiquidGlassControlsChange = viewModel::setLiquidGlassControlsEnabled,
        onLiquidGlassRendererChange = viewModel::setLiquidGlassRenderer,
        onLiquidGlassNavbarChange = viewModel::setLiquidGlassNavbarEnabled,
        onLiquidGlassBlurChange = viewModel::setLiquidGlassBlur,
        onLiquidGlassLensHeightChange = viewModel::setLiquidGlassLensHeight,
        onLiquidGlassLensAmountChange = viewModel::setLiquidGlassLensAmount,
        onLiquidGlassSpectralChange = viewModel::setLiquidGlassSpectral,
        onLiquidGlassTintAlphaChange = viewModel::setLiquidGlassTintAlpha,
        onLiquidGlassShadowChange = viewModel::setLiquidGlassShadowOn,
        onLiquidGlassGlassChange = viewModel::setLiquidGlassGlassOn,
        onLiquidGlassContourChange = viewModel::setLiquidGlassContourEdge,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onLiquidGlassControlsChange: (Boolean) -> Unit = {},
    onLiquidGlassRendererChange: (LiquidGlassRenderer) -> Unit = {},
    onLiquidGlassNavbarChange: (Boolean) -> Unit = {},
    onLiquidGlassBlurChange: (Float) -> Unit = {},
    onLiquidGlassLensHeightChange: (Float) -> Unit = {},
    onLiquidGlassLensAmountChange: (Float) -> Unit = {},
    onLiquidGlassSpectralChange: (Float) -> Unit = {},
    onLiquidGlassTintAlphaChange: (Float) -> Unit = {},
    onLiquidGlassShadowChange: (Boolean) -> Unit = {},
    onLiquidGlassGlassChange: (Boolean) -> Unit = {},
    onLiquidGlassContourChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val theme = state.themeState
    var lgWarningTarget by remember { mutableStateOf<LiquidGlassTarget?>(null) }
    var lgWarningStep by remember { mutableIntStateOf(0) }
    val glassActive = rememberLiquidGlassActive()
    val cppAvailable = LocalLiquidGlassCppAvailable.current
    val kyantAvailable = LiquidGlassEngine.kyantAvailable()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.settings_liquid_glass_title)) },
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            SectionLabel(stringResource(R.string.settings_label_liquid_glass))
            AppText(
                text = stringResource(R.string.settings_liquid_glass_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))

            // 实现方式：C++ 原生覆盖层 / Kotlin Compose，二者共用同一套开关与样式参数。
            SectionLabel(stringResource(R.string.settings_lg_impl))
            AppSegmentedRow(
                options = listOf(
                    stringResource(R.string.settings_lg_impl_cpp),
                    stringResource(R.string.settings_lg_impl_kyant),
                ),
                selectedIndex = if (theme.liquidGlassRenderer == LiquidGlassRenderer.Cpp) 0 else 1,
                onSelect = { index ->
                    onLiquidGlassRendererChange(
                        if (index == 0) LiquidGlassRenderer.Cpp else LiquidGlassRenderer.Kyant,
                    )
                },
            )
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            AppText(
                text = stringResource(R.string.settings_lg_impl_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val implUnavailable = when {
                theme.liquidGlassRenderer == LiquidGlassRenderer.Cpp && !cppAvailable ->
                    R.string.settings_lg_impl_cpp_unavailable
                theme.liquidGlassRenderer == LiquidGlassRenderer.Kyant && !kyantAvailable ->
                    R.string.settings_lg_impl_kyant_unavailable
                else -> null
            }
            if (implUnavailable != null) {
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppText(
                    text = stringResource(implUnavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))

            ToggleRow(
                checked = theme.liquidGlassControlsEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        lgWarningTarget = LiquidGlassTarget.Controls
                        lgWarningStep = 0
                    } else {
                        onLiquidGlassControlsChange(false)
                    }
                },
                label = stringResource(R.string.settings_liquid_glass_controls),
            )
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            ToggleRow(
                checked = theme.liquidGlassNavbarEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        lgWarningTarget = LiquidGlassTarget.Navbar
                        lgWarningStep = 0
                    } else {
                        onLiquidGlassNavbarChange(false)
                    }
                },
                label = stringResource(R.string.settings_liquid_glass_navbar),
            )

            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))

            SectionLabel(stringResource(R.string.settings_lg_details))

            SectionLabel(stringResource(R.string.settings_lg_blur))
            AppSlider(
                value = theme.liquidGlassBlurDp,
                onValueChange = onLiquidGlassBlurChange,
                valueRange = 0f..LIQUID_GLASS_BLUR_MAX_DP,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel(stringResource(R.string.settings_lg_lens_height))
            AppSlider(
                value = theme.liquidGlassLensHeightDp,
                onValueChange = onLiquidGlassLensHeightChange,
                valueRange = 0f..LIQUID_GLASS_LENS_HEIGHT_MAX_DP,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel(stringResource(R.string.settings_lg_lens_amount))
            AppSlider(
                value = theme.liquidGlassLensAmountDp,
                onValueChange = onLiquidGlassLensAmountChange,
                valueRange = 0f..LIQUID_GLASS_LENS_AMOUNT_MAX_DP,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel(stringResource(R.string.settings_lg_spectral))
            AppSlider(
                value = theme.liquidGlassSpectral,
                onValueChange = onLiquidGlassSpectralChange,
                valueRange = 0f..LIQUID_GLASS_SPECTRAL_MAX,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel(stringResource(R.string.settings_lg_tint_alpha))
            AppSlider(
                value = theme.liquidGlassTintAlpha,
                onValueChange = onLiquidGlassTintAlphaChange,
                valueRange = LIQUID_GLASS_TINT_ALPHA_MIN..1f,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            ToggleRow(
                checked = theme.liquidGlassShadowOn,
                onCheckedChange = onLiquidGlassShadowChange,
                label = stringResource(R.string.settings_lg_shadow),
            )
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            ToggleRow(
                checked = theme.liquidGlassGlassOn,
                onCheckedChange = onLiquidGlassGlassChange,
                label = stringResource(R.string.settings_lg_glass),
            )
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            ToggleRow(
                checked = theme.liquidGlassContourEdge,
                onCheckedChange = onLiquidGlassContourChange,
                label = stringResource(R.string.settings_lg_contour),
            )

            AppDivider(Modifier.padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_m)))

            LiquidGlassPreview()
        }
    }

    val target = lgWarningTarget
    if (target != null) {
        LiquidGlassWarningDialog(
            step = lgWarningStep,
            onContinue = {
                if (lgWarningStep < 2) {
                    lgWarningStep += 1
                } else {
                    when (target) {
                        LiquidGlassTarget.Controls -> onLiquidGlassControlsChange(true)
                        LiquidGlassTarget.Navbar -> onLiquidGlassNavbarChange(true)
                    }
                    lgWarningTarget = null
                }
            },
            onDismiss = { lgWarningTarget = null },
        )
    }
}

/** 液态玻璃开关目标：控件坞 / 导航栏。 */
private enum class LiquidGlassTarget { Controls, Navbar }

/**
 * 液态玻璃开启前的多级警告对话框：必须依次确认 3 次才会真正开启，
 * 明确告知崩溃与卡顿风险（用户要求「启动前会多次警告用户」）。
 */
@Composable
private fun LiquidGlassWarningDialog(
    step: Int,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val titleRes = when (step) {
        0 -> R.string.settings_lg_warn1_title
        1 -> R.string.settings_lg_warn2_title
        else -> R.string.settings_lg_warn3_title
    }
    val bodyRes = when (step) {
        0 -> R.string.settings_lg_warn1_body
        1 -> R.string.settings_lg_warn2_body
        else -> R.string.settings_lg_warn3_body
    }
    AppLiquidDialog(
        onDismissRequest = onDismiss,
        title = stringResource(titleRes),
        message = stringResource(bodyRes),
        confirmLabel = stringResource(
            if (step < 2) R.string.settings_lg_warn_continue
            else R.string.settings_lg_warn_confirm,
        ),
        onConfirm = onContinue,
        dismissLabel = stringResource(ResR.string.action_cancel),
    )
}

/**
 * 液态玻璃快捷预览区：在玻璃「子背景」容器内集中展示开关 / 分段行 / 滑动条 / 输入框 / 按钮，
 * 参数调整后即时反映（全部由 core:ui 的液态玻璃控件渲染）。
 */
@Composable
private fun LiquidGlassPreview() {
    var previewOn by remember { mutableStateOf(true) }
    var previewSegment by remember { mutableIntStateOf(1) }
    var previewValue by remember { mutableStateOf(0.5f) }
    var previewText by remember { mutableStateOf("") }
    val shape = MaterialTheme.shapes.medium
    SectionLabel(stringResource(R.string.settings_lg_preview))
    AppText(
        text = stringResource(R.string.settings_lg_preview_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 预览区随设置页滚动，走真玻璃会因背景纹理滞后一帧而在滑动时抖动 → 固定用伪玻璃。
            .pseudoGlassSurface(shape = shape, color = MaterialTheme.colorScheme.surface)
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(R.string.settings_lg_preview_toggle),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.weight(1f))
            AppSwitch(checked = previewOn, onCheckedChange = { previewOn = it })
        }
        AppSegmentedRow(
            options = listOf(
                stringResource(R.string.settings_lg_preview_toggle),
                stringResource(R.string.settings_lg_preview_button),
                stringResource(R.string.settings_lg_preview_slider),
            ),
            selectedIndex = previewSegment,
            onSelect = { previewSegment = it },
        )
        AppSlider(
            value = previewValue,
            onValueChange = { previewValue = it },
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = previewText,
            onValueChange = { previewText = it },
            label = stringResource(R.string.settings_lg_preview_field),
            placeholder = stringResource(R.string.settings_lg_preview_field),
            singleLine = true,
        )
        AppPrimaryButton(
            text = stringResource(R.string.settings_lg_preview_button),
            onClick = { previewOn = !previewOn },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
