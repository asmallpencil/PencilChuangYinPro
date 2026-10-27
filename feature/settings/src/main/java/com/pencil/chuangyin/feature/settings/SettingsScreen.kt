package com.pencil.chuangyin.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.theme.BACKGROUND_ALPHA_MIN
import com.pencil.chuangyin.core.theme.BACKGROUND_BLUR_MAX_DP
import com.pencil.chuangyin.core.theme.CornerStyle
import com.pencil.chuangyin.core.theme.LIQUID_GLASS_BLUR_MAX_DP
import com.pencil.chuangyin.core.theme.LIQUID_GLASS_LENS_AMOUNT_MAX_DP
import com.pencil.chuangyin.core.theme.LIQUID_GLASS_LENS_HEIGHT_MAX_DP
import com.pencil.chuangyin.core.theme.LIQUID_GLASS_SPECTRAL_MAX
import com.pencil.chuangyin.core.theme.LIQUID_GLASS_TINT_ALPHA_MIN
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.theme.MotionPreset
import com.pencil.chuangyin.core.theme.OUTLINE_ALPHA_DEFAULT
import com.pencil.chuangyin.core.theme.OUTLINE_ALPHA_MIN
import com.pencil.chuangyin.core.theme.OUTLINE_WIDTH_MAX_DP
import com.pencil.chuangyin.core.theme.OUTLINE_WIDTH_MIN_DP
import com.pencil.chuangyin.core.theme.OutlineColorMode
import com.pencil.chuangyin.core.theme.PARTICLE_SPEED_MAX
import com.pencil.chuangyin.core.theme.PARTICLE_SPEED_MIN
import com.pencil.chuangyin.core.theme.ParticleType
import com.pencil.chuangyin.core.theme.SURFACE_ALPHA_MIN
import com.pencil.chuangyin.core.theme.ThemeState
import com.pencil.chuangyin.core.theme.cornerShape
import com.pencil.chuangyin.core.ui.components.AppAlertDialog
import com.pencil.chuangyin.core.ui.components.AppDivider
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppSwitch
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.LiquidGlassLayerScope
import com.pencil.chuangyin.core.ui.components.RgbColorPicker
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.resources.R as ResR

// ===================== 设置主菜单 =====================

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenVisual: () -> Unit,
    onOpenLiquidGlass: () -> Unit,
    onOpenSize: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenAiConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        onBack = onBack,
        onOpenVisual = onOpenVisual,
        onOpenLiquidGlass = onOpenLiquidGlass,
        onOpenSize = onOpenSize,
        onOpenLicenses = onOpenLicenses,
        onOpenAiConfig = onOpenAiConfig,
        outputDirName = state.outputDirName,
        outputTreeName = state.outputTreeName,
        onSetOutputTree = viewModel::setOutputTree,
        onClearOutputTree = viewModel::clearOutputTree,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenVisual: () -> Unit,
    onOpenLiquidGlass: () -> Unit,
    onOpenSize: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenAiConfig: () -> Unit = {},
    outputDirName: String = com.pencil.chuangyin.core.ui.data.OUTPUT_DIR_DEFAULT,
    outputTreeName: String = "",
    onSetOutputTree: (String) -> Unit = {},
    onClearOutputTree: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showOutputDialog by remember { mutableStateOf(false) }
    // SAF 目录选择器：Android 11+ 无存储权限时，这是唯一能把产物写到用户可见目录的办法。
    val pickOutputTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            onSetOutputTree(uri.toString())
            showOutputDialog = false
        }
    }
    val glassActive = rememberLiquidGlassActive()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.settings_title)) },
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
                .verticalScroll(rememberScrollState()),
        ) {
            MenuEntry(
                title = stringResource(R.string.settings_menu_ai),
                desc = stringResource(R.string.settings_menu_ai_desc),
                onClick = onOpenAiConfig,
            )
            AppDivider()
            MenuEntry(
                title = stringResource(R.string.settings_menu_visual),
                desc = stringResource(R.string.settings_menu_visual_desc),
                onClick = onOpenVisual,
            )
            AppDivider()
            MenuEntry(
                title = stringResource(R.string.settings_menu_liquid_glass),
                desc = stringResource(R.string.settings_menu_liquid_glass_desc),
                onClick = onOpenLiquidGlass,
            )
            AppDivider()
            MenuEntry(
                title = stringResource(R.string.settings_menu_size),
                desc = stringResource(R.string.settings_menu_size_desc),
                onClick = onOpenSize,
            )
            AppDivider()
            val outputDesc = if (outputTreeName.isNotBlank()) {
                outputTreeName
            } else {
                stringResource(R.string.settings_menu_output_default, outputDirName)
            }
            MenuEntry(
                title = stringResource(R.string.settings_menu_output),
                desc = stringResource(R.string.settings_menu_output_desc, outputDesc),
                onClick = { showOutputDialog = true },
            )
            AppDivider()
            MenuEntry(
                title = stringResource(R.string.settings_menu_licenses),
                desc = stringResource(R.string.settings_menu_licenses_desc),
                onClick = onOpenLicenses,
            )
            AppDivider()
        }
    }

    if (showOutputDialog) {
        OutputPathDialog(
            currentName = outputTreeName,
            onPick = { pickOutputTree.launch(null) },
            onClear = {
                onClearOutputTree()
                showOutputDialog = false
            },
            onDismiss = { showOutputDialog = false },
        )
    }
}

@Composable
private fun OutputPathDialog(
    currentName: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
        AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { AppText(text = stringResource(R.string.settings_output_dialog_title)) },
            text = {
                Column {
                    AppText(
                        text = stringResource(R.string.settings_output_dialog_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
                    AppText(
                        text = if (currentName.isBlank()) {
                            stringResource(R.string.settings_output_dialog_none)
                        } else {
                            stringResource(R.string.settings_output_dialog_current, currentName)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                AppTextButton(text = stringResource(R.string.settings_output_dialog_pick), onClick = onPick)
            },
            dismissButton = {
                Row {
                    if (currentName.isNotBlank()) {
                        AppTextButton(text = stringResource(R.string.settings_output_dialog_clear), onClick = onClear)
                    }
                    AppTextButton(text = stringResource(ResR.string.action_cancel), onClick = onDismiss)
                }
            },
        )
    }
}

@Composable
private fun MenuEntry(title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(text = title, style = MaterialTheme.typography.titleMedium)
            AppText(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun BackIcon(onBack: () -> Unit) {
    AppIconButton(
        onClick = onBack,
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(ResR.string.label_back),
    )
}

// ===================== 画面设置 =====================

@Composable
fun VisualSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    VisualSettingsScreen(
        state = state,
        onBack = onBack,
        onSeedSelected = viewModel::setSeedColor,
        onDarkChange = viewModel::setDarkTheme,
        onCornerStyleSelected = viewModel::setCornerStyle,
        onCornerStrengthChange = viewModel::setCornerStrength,
        onMotionSelected = viewModel::setMotionPreset,
        onOutlineEnabledChange = viewModel::setOutlineEnabled,
        onOutlineWidthChange = viewModel::setOutlineWidth,
        onOutlineAlphaChange = viewModel::setOutlineAlpha,
        onOutlineColorModeSelected = viewModel::setOutlineColorMode,
        onOutlineColorChange = viewModel::setOutlineArgb,
        onSnowfallChange = viewModel::setSnowfallEnabled,
        onParticleTypeSelected = viewModel::setParticleType,
        onParticleSpeedChange = viewModel::setParticleSpeed,
        onBackgroundUriChange = viewModel::setBackgroundUri,
        onBackgroundAlphaChange = viewModel::setBackgroundAlpha,
        onSurfaceAlphaChange = viewModel::setSurfaceAlpha,
        onBlurChange = viewModel::setBackgroundBlur,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSeedSelected: (Long) -> Unit,
    onDarkChange: (Boolean) -> Unit,
    onCornerStyleSelected: (CornerStyle) -> Unit,
    onCornerStrengthChange: (Float) -> Unit,
    onMotionSelected: (MotionPreset) -> Unit,
    onOutlineEnabledChange: (Boolean) -> Unit,
    onOutlineWidthChange: (Float) -> Unit,
    onOutlineAlphaChange: (Float) -> Unit,
    onOutlineColorModeSelected: (OutlineColorMode) -> Unit,
    onOutlineColorChange: (Long) -> Unit,
    onSnowfallChange: (Boolean) -> Unit,
    onParticleTypeSelected: (ParticleType) -> Unit,
    onParticleSpeedChange: (Float) -> Unit,
    onBackgroundUriChange: (String?) -> Unit,
    onBackgroundAlphaChange: (Float) -> Unit,
    onSurfaceAlphaChange: (Float) -> Unit,
    onBlurChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = state.themeState
    val glassActive = rememberLiquidGlassActive()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.settings_visual_title)) },
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
            SectionLabel(stringResource(R.string.settings_label_seed_color))
            RgbColorPicker(
                color = Color(theme.seedArgb),
                onColorChange = { color ->
                    onSeedSelected(color.toArgb().toLong() and 0xFFFFFFFFL)
                },
                barHeight = dimensionResource(ResR.dimen.dimen_color_picker_hue_bar_height),
                selectorRadius = dimensionResource(ResR.dimen.dimen_color_picker_selector_radius),
                selectorStrokeWidth = dimensionResource(ResR.dimen.dimen_color_picker_selector_stroke),
                previewSize = dimensionResource(ResR.dimen.dimen_color_picker_preview_size),
            )

            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
            ColorCodeRow(currentArgb = theme.seedArgb, onColorApplied = onSeedSelected)

            AppDivider(Modifier.padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_m)))

            ToggleRow(
                checked = theme.darkTheme,
                onCheckedChange = onDarkChange,
                label = stringResource(R.string.settings_label_dark),
            )

            AppDivider(Modifier.padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_m)))

            SectionLabel(stringResource(R.string.settings_label_shape))
            CornerStylePicker(
                selected = theme.cornerStyle,
                strength = theme.cornerStrength,
                onSelected = onCornerStyleSelected,
            )
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            SectionLabel(stringResource(R.string.settings_label_corner_strength))
            AppSlider(
                value = theme.cornerStrength,
                onValueChange = onCornerStrengthChange,
                enabled = theme.cornerStyle != CornerStyle.Square,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))

            SectionLabel(stringResource(R.string.settings_label_motion))
            AppSegmentedRow(
                options = MotionPreset.entries.map { stringResource(motionLabel(it)) },
                selectedIndex = MotionPreset.entries.indexOf(theme.motionPreset),
                onSelect = { onMotionSelected(MotionPreset.entries[it]) },
            )

            AppDivider(Modifier.padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_m)))

            SectionLabel(stringResource(R.string.settings_label_outline))
            ToggleRow(
                checked = theme.outlineEnabled,
                onCheckedChange = onOutlineEnabledChange,
                label = stringResource(R.string.settings_outline_enabled),
            )

            SectionLabel(stringResource(R.string.settings_label_outline_width))
            AppSlider(
                value = theme.outlineWidthDp,
                onValueChange = onOutlineWidthChange,
                enabled = theme.outlineEnabled,
                valueRange = OUTLINE_WIDTH_MIN_DP..OUTLINE_WIDTH_MAX_DP,
                steps = (((OUTLINE_WIDTH_MAX_DP - OUTLINE_WIDTH_MIN_DP) / 0.5f).toInt() - 1),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(stringResource(R.string.settings_label_outline_opacity))
            AppSlider(
                value = theme.outlineAlpha,
                onValueChange = onOutlineAlphaChange,
                enabled = theme.outlineEnabled,
                valueRange = OUTLINE_ALPHA_MIN..1f,
                modifier = Modifier.fillMaxWidth(),
            )

            AppSegmentedRow(
                options = OutlineColorMode.entries.map { stringResource(outlineModeLabel(it)) },
                selectedIndex = OutlineColorMode.entries.indexOf(theme.outlineColorMode),
                onSelect = { onOutlineColorModeSelected(OutlineColorMode.entries[it]) },
                enabled = theme.outlineEnabled,
            )

            if (theme.outlineEnabled && theme.outlineColorMode == OutlineColorMode.Rgb) {
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
                RgbColorPicker(
                    color = Color(theme.outlineArgb),
                    onColorChange = { color ->
                        onOutlineColorChange(color.toArgb().toLong() and 0xFFFFFFFFL)
                    },
                    barHeight = dimensionResource(ResR.dimen.dimen_color_picker_hue_bar_height),
                    selectorRadius = dimensionResource(ResR.dimen.dimen_color_picker_selector_radius),
                    selectorStrokeWidth = dimensionResource(ResR.dimen.dimen_color_picker_selector_stroke),
                    previewSize = dimensionResource(ResR.dimen.dimen_color_picker_preview_size),
                )
            }

            AppDivider(Modifier.padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_m)))
            ToggleRow(
                checked = theme.snowfallEnabled,
                onCheckedChange = onSnowfallChange,
                label = stringResource(R.string.settings_label_snowfall),
            )
            SectionLabel(stringResource(R.string.settings_label_particle_type))
            AppSegmentedRow(
                options = ParticleType.entries.map { stringResource(particleTypeLabel(it)) },
                selectedIndex = ParticleType.entries.indexOf(theme.particleType),
                onSelect = { onParticleTypeSelected(ParticleType.entries[it]) },
                enabled = theme.snowfallEnabled,
            )
            SectionLabel(stringResource(R.string.settings_label_particle_speed))
            AppSlider(
                value = theme.particleSpeed,
                onValueChange = onParticleSpeedChange,
                enabled = theme.snowfallEnabled,
                valueRange = PARTICLE_SPEED_MIN..PARTICLE_SPEED_MAX,
                modifier = Modifier.fillMaxWidth(),
            )

            AppDivider(Modifier.padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_m)))

            BackgroundSection(
                backgroundUri = theme.backgroundUri,
                backgroundAlpha = theme.backgroundAlpha,
                surfaceAlpha = theme.surfaceAlpha,
                backgroundBlur = theme.backgroundBlur,
                onUriChange = onBackgroundUriChange,
                onAlphaChange = onBackgroundAlphaChange,
                onSurfaceAlphaChange = onSurfaceAlphaChange,
                onBlurChange = onBlurChange,
            )
        }
    }
}

// ===================== 复用小组件 =====================

@Composable
internal fun SectionLabel(text: String) {
    AppText(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
    )
}

@Composable
internal fun ToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(text = label, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 自定义背景区：系统相册选图（Photo Picker，免存储权限）+ 不透明度滑杆 + 清除。
 */
@Composable
private fun BackgroundSection(
    backgroundUri: String?,
    backgroundAlpha: Float,
    surfaceAlpha: Float,
    backgroundBlur: Float,
    onUriChange: (String?) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onSurfaceAlphaChange: (Float) -> Unit,
    onBlurChange: (Float) -> Unit,
) {
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onUriChange(uri.toString())
        }
    }
    SectionLabel(stringResource(R.string.settings_label_background))
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppOutlinedButton(
            text = stringResource(R.string.settings_background_pick),
            onClick = {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
        if (backgroundUri != null) {
            Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            AppTextButton(
                text = stringResource(R.string.settings_background_clear),
                onClick = { onUriChange(null) },
                contentColor = MaterialTheme.colorScheme.error,
            )
        }
    }
    SectionLabel(stringResource(R.string.settings_background_opacity))
    AppSlider(
        value = backgroundAlpha,
        onValueChange = onAlphaChange,
        enabled = backgroundUri != null,
        valueRange = BACKGROUND_ALPHA_MIN..1f,
        modifier = Modifier.fillMaxWidth(),
    )
    SectionLabel(stringResource(R.string.settings_surface_opacity))
    AppSlider(
        value = surfaceAlpha,
        onValueChange = onSurfaceAlphaChange,
        enabled = backgroundUri != null,
        valueRange = SURFACE_ALPHA_MIN..1f,
        modifier = Modifier.fillMaxWidth(),
    )
    SectionLabel(stringResource(R.string.settings_background_blur))
    AppSlider(
        value = backgroundBlur,
        onValueChange = onBlurChange,
        enabled = backgroundUri != null,
        valueRange = 0f..BACKGROUND_BLUR_MAX_DP,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColorCodeRow(
    currentArgb: Long,
    onColorApplied: (Long) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(dimensionResource(R.dimen.settings_current_color_size))
                    .clip(RoundedCornerShape(dimensionResource(R.dimen.settings_palette_gap)))
                    .background(Color(currentArgb))
                    .border(
                        width = dimensionResource(R.dimen.settings_swatch_border),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(dimensionResource(R.dimen.settings_palette_gap)),
                    ),
            )
            Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            AppText(
                text = stringResource(R.string.settings_label_current_color),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AppTextField(
                value = input,
                onValueChange = { input = it; hasError = false },
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.settings_color_code),
                placeholder = stringResource(R.string.settings_color_code_hint),
                isError = hasError,
                errorMessage = stringResource(R.string.settings_color_code_invalid),
                singleLine = true,
            )
            Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            AppPrimaryButton(
                text = stringResource(R.string.settings_color_code_apply),
                onClick = {
                    val parsed = parseHexColor(input)
                    if (parsed != null) {
                        onColorApplied(parsed)
                        hasError = false
                    } else {
                        hasError = true
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CornerStylePicker(
    selected: CornerStyle,
    strength: Float,
    onSelected: (CornerStyle) -> Unit,
) {
    val spacing = dimensionResource(ResR.dimen.dimen_global_padding_m)
    val previewSize = dimensionResource(R.dimen.settings_corner_preview_size)
    val previewBase = dimensionResource(R.dimen.settings_corner_preview_base)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        CornerStyle.entries.forEach { style ->
            val isSelected = style == selected
            val previewShape = cornerShape(style, strength, previewBase)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelected(style) },
            ) {
                Box(
                    modifier = Modifier
                        .size(previewSize)
                        .clip(previewShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .border(
                            width = dimensionResource(R.dimen.settings_swatch_border),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = previewShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                AppText(
                    text = stringResource(cornerStyleName(style)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsMenuPreview() {
    SettingsScreen(
        onBack = {},
        onOpenVisual = {},
        onOpenLiquidGlass = {},
        onOpenSize = {},
        onOpenLicenses = {},
        onOpenAiConfig = {},
    )
}

// ---- 文案映射 ----
private fun cornerStyleName(style: CornerStyle): Int = when (style) {
    CornerStyle.Rounded -> R.string.corner_rounded
    CornerStyle.Square -> R.string.corner_square
    CornerStyle.Diamond -> R.string.corner_diamond
    CornerStyle.Parallelogram -> R.string.corner_parallelogram
}

private fun motionLabel(preset: MotionPreset): Int = when (preset) {
    MotionPreset.Full -> R.string.motion_full
    MotionPreset.Expressive -> R.string.motion_expressive
    MotionPreset.Reduced -> R.string.motion_reduced
}

private fun outlineModeLabel(mode: OutlineColorMode): Int = when (mode) {
    OutlineColorMode.Seed -> R.string.outline_mode_seed
    OutlineColorMode.Rgb -> R.string.outline_mode_rgb
    OutlineColorMode.Rainbow -> R.string.outline_mode_rainbow
}

private fun particleTypeLabel(type: ParticleType): Int = when (type) {
    ParticleType.Snow -> R.string.particle_snow
    ParticleType.Rain -> R.string.particle_rain
    ParticleType.Leaf -> R.string.particle_leaf
    ParticleType.Petal -> R.string.particle_petal
}

private fun parseHexColor(input: String): Long? {
    var hex = input.trim().removePrefix("#")
    if (hex.length == 3) {
        hex = buildString { hex.forEach { append(it).append(it) } }
    }
    if (hex.length != 6 && hex.length != 8) return null
    val value = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) 0xFF000000.toLong() or value else value
}
