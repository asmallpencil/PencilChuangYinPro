package com.pencil.chuangyin.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.theme.LoadingAnimationStyle
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
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppSwitch
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.GlassPanel
import com.pencil.chuangyin.core.ui.components.LiquidGlassLayerScope
import com.pencil.chuangyin.core.ui.components.RgbColorPicker
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.ui.motion.pressScale
import com.pencil.chuangyin.resources.R as ResR
import kotlin.math.roundToInt

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
        autoConnectEnabled = state.autoConnectEnabled,
        onAutoConnectChange = viewModel::setAutoConnectEnabled,
        outputDirName = state.outputDirName,
        outputTreeName = state.outputTreeName,
        onSetOutputDirName = viewModel::setOutputDirName,
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
    autoConnectEnabled: Boolean = true,
    onAutoConnectChange: (Boolean) -> Unit = {},
    outputDirName: String = com.pencil.chuangyin.core.ui.data.OUTPUT_DIR_DEFAULT,
    outputTreeName: String = "",
    onSetOutputDirName: (String) -> Unit = {},
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
        // 分组依次错峰浮现：卡片像被"放"到页面上，而不是一次性整屏刷出来。
        var appeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appeared = true }
        val outputDesc = if (outputTreeName.isNotBlank()) {
            outputTreeName
        } else {
            stringResource(R.string.settings_menu_output_default, outputDirName)
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m))
                .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_xl)),
        ) {
            SettingsGroup(
                label = stringResource(R.string.settings_group_ai),
                visible = appeared,
                delayMillis = 0,
            ) {
                SettingsEntry(
                    icon = Icons.Filled.AutoAwesome,
                    title = stringResource(R.string.settings_menu_ai),
                    desc = stringResource(R.string.settings_menu_ai_desc),
                    onClick = onOpenAiConfig,
                )
            }
            SettingsGroup(
                label = stringResource(R.string.settings_group_appearance),
                visible = appeared,
                delayMillis = 70,
            ) {
                SettingsEntry(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.settings_menu_visual),
                    desc = stringResource(R.string.settings_menu_visual_desc),
                    onClick = onOpenVisual,
                )
                SettingsEntryDivider()
                SettingsEntry(
                    icon = Icons.Filled.BlurOn,
                    title = stringResource(R.string.settings_menu_liquid_glass),
                    desc = stringResource(R.string.settings_menu_liquid_glass_desc),
                    onClick = onOpenLiquidGlass,
                )
            }
            // 纸张/设备相关设置从「外观与动效」中独立出来，归入「功能设定」。
            SettingsGroup(
                label = stringResource(R.string.settings_group_function),
                visible = appeared,
                delayMillis = 140,
            ) {
                SettingsEntry(
                    icon = Icons.Filled.FormatSize,
                    title = stringResource(R.string.settings_menu_size),
                    desc = stringResource(R.string.settings_menu_size_desc),
                    onClick = onOpenSize,
                )
                SettingsEntryDivider()
                AutoConnectEntry(
                    checked = autoConnectEnabled,
                    onCheckedChange = onAutoConnectChange,
                )
                AppText(
                    text = stringResource(R.string.settings_auto_connect_logic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = dimensionResource(ResR.dimen.dimen_global_padding_m),
                        end = dimensionResource(ResR.dimen.dimen_global_padding_m),
                        bottom = dimensionResource(ResR.dimen.dimen_global_padding_m),
                    ),
                )
            }
            SettingsGroup(
                label = stringResource(R.string.settings_group_output),
                visible = appeared,
                delayMillis = 210,
            ) {
                SettingsEntry(
                    icon = Icons.Filled.FolderOpen,
                    title = stringResource(R.string.settings_menu_output),
                    desc = stringResource(R.string.settings_menu_output_desc, outputDesc),
                    onClick = { showOutputDialog = true },
                )
                SettingsEntryDivider()
                SettingsEntry(
                    icon = Icons.Filled.Policy,
                    title = stringResource(R.string.settings_menu_licenses),
                    desc = stringResource(R.string.settings_menu_licenses_desc),
                    onClick = onOpenLicenses,
                )
            }
        }
    }

    if (showOutputDialog) {
        OutputPathDialog(
            currentName = outputTreeName,
            dirName = outputDirName,
            onDirNameChange = onSetOutputDirName,
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
    dirName: String,
    onDirNameChange: (String) -> Unit,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 目录名写入 DataStore 是异步的，输入框若直接绑 dirName 会逐字延迟/吞字，
    // 因此本地维护一份输入值，每次输入即同步回写设置。
    var nameInput by remember { mutableStateOf(dirName) }
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
                    // 目录名决定未授权 SAF 时的公共目录（Pictures/<目录名>/…）。
                    AppTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            onDirNameChange(it)
                        },
                        label = stringResource(R.string.settings_output_dialog_name_label),
                        placeholder = com.pencil.chuangyin.core.ui.data.OUTPUT_DIR_DEFAULT,
                        modifier = Modifier.fillMaxWidth(),
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
            // 产物输出目录弹窗保持实心卡片，不做液态玻璃。
            glass = false,
        )
    }
}

/** 设置分组：小标题 + 圆角卡片容器，整组带错峰入场。 */
@Composable
private fun SettingsGroup(
    label: String,
    visible: Boolean,
    delayMillis: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 240, delayMillis = delayMillis)) +
            slideInVertically(tween(durationMillis = 320, delayMillis = delayMillis)) { it / 10 },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AppText(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = dimensionResource(ResR.dimen.dimen_global_padding_m),
                    top = dimensionResource(ResR.dimen.dimen_global_padding_l),
                    bottom = dimensionResource(ResR.dimen.dimen_global_padding_s),
                ),
            )
            // 设置分类栏是内容容器（非控件），按需求启用真液态玻璃；不可用时回退原 M3 容器色。
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_l)),
                enabled = rememberLiquidGlassActive(),
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(content = content)
            }
        }
    }
}

/** 卡片内的一行设置项：图标胶囊 + 标题/说明 + 右侧箭头，按压有物理回弹。 */
@Composable
private fun SettingsEntry(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
    ) {
        Box(
            modifier = Modifier
                .size(dimensionResource(ResR.dimen.dimen_global_touch_target) - 8.dp)
                .clip(RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_m)))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            AppText(text = title, style = MaterialTheme.typography.titleMedium)
            AppText(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
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

/** 自动连接开关行：图标 + 标题/说明 + 开关，仅切换状态，不进入子页。 */
@Composable
private fun AutoConnectEntry(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
    ) {
        Box(
            modifier = Modifier
                .size(dimensionResource(ResR.dimen.dimen_global_touch_target) - 8.dp)
                .clip(RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_m)))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = stringResource(R.string.settings_menu_auto_connect),
                style = MaterialTheme.typography.titleMedium,
            )
            AppText(
                text = stringResource(R.string.settings_menu_auto_connect_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 组内分隔线：左侧让出图标宽度，视觉上从文字起始处断开。 */
@Composable
private fun SettingsEntryDivider() {
    Box(
        modifier = Modifier.padding(
            start = dimensionResource(ResR.dimen.dimen_global_padding_m) +
                dimensionResource(ResR.dimen.dimen_global_touch_target) - 8.dp +
                dimensionResource(ResR.dimen.dimen_global_padding_m),
        ),
    ) {
        AppDivider()
    }
}

@Composable
internal fun BackIcon(onBack: () -> Unit) {
    AppIconButton(
        onClick = onBack,
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(ResR.string.label_back),
        // 设置页返回按钮保持实心，不做玻璃（避免与顶栏玻璃叠加）。
        glass = false,
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
        onLoadingStyleSelected = viewModel::setLoadingAnimationStyle,
        onLoadingParticleCountChange = viewModel::setLoadingParticleCount,
        onLoadingTrailChange = viewModel::setLoadingTrailSpan,
        onLoadingDurationChange = viewModel::setLoadingDurationMs,
        onLoadingPulseChange = viewModel::setLoadingPulseDurationMs,
        onLoadingRotationChange = viewModel::setLoadingRotationDurationMs,
        onLoadingStrokeChange = viewModel::setLoadingStrokeWidth,
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
    onLoadingStyleSelected: (LoadingAnimationStyle) -> Unit,
    onLoadingParticleCountChange: (Int) -> Unit,
    onLoadingTrailChange: (Float) -> Unit,
    onLoadingDurationChange: (Int) -> Unit,
    onLoadingPulseChange: (Int) -> Unit,
    onLoadingRotationChange: (Int) -> Unit,
    onLoadingStrokeChange: (Float) -> Unit,
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

            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))

            LoadingAnimationSection(
                theme = theme,
                onStyleSelected = onLoadingStyleSelected,
                onParticleCountChange = onLoadingParticleCountChange,
                onTrailChange = onLoadingTrailChange,
                onDurationChange = onLoadingDurationChange,
                onPulseChange = onLoadingPulseChange,
                onRotationChange = onLoadingRotationChange,
                onStrokeChange = onLoadingStrokeChange,
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
 * 加载动画区：样式横向选择 + 6 项全局参数滑杆 + 实时预览。
 *
 * 预览直接复用 [AppLoadingIndicator]，而它读取的是 [com.pencil.chuangyin.core.theme.LocalLoadingAnimation]
 * （由 AppTheme 从主题状态下发），所以这里的调整经 DataStore 落盘后预览即时变化，
 * 同时全局所有页面的加载指示器一起生效。
 */
@Composable
private fun LoadingAnimationSection(
    theme: ThemeState,
    onStyleSelected: (LoadingAnimationStyle) -> Unit,
    onParticleCountChange: (Int) -> Unit,
    onTrailChange: (Float) -> Unit,
    onDurationChange: (Int) -> Unit,
    onPulseChange: (Int) -> Unit,
    onRotationChange: (Int) -> Unit,
    onStrokeChange: (Float) -> Unit,
) {
    SectionLabel(stringResource(R.string.settings_label_loading))
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
            text = stringResource(R.string.settings_loading_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(LoadingAnimationStyle.entries, key = { it.id }) { style ->
            LoadingStyleChip(
                label = stringResource(loadingStyleLabel(style)),
                selected = theme.loadingAnimationStyle == style,
                onClick = { onStyleSelected(style) },
            )
        }
    }
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

/** 加载动画样式选项：横向 chip，选中时用主色容器 + 勾选图标。 */
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

private fun parseHexColor(input: String): Long? {
    var hex = input.trim().removePrefix("#")
    if (hex.length == 3) {
        hex = buildString { hex.forEach { append(it).append(it) } }
    }
    if (hex.length != 6 && hex.length != 8) return null
    val value = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) 0xFF000000.toLong() or value else value
}
