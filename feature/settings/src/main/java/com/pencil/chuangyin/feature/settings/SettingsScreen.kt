package com.pencil.chuangyin.feature.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wallpaper
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.proxy.model.ProxyScope
import com.pencil.chuangyin.core.theme.CornerStyle
import com.pencil.chuangyin.core.theme.MotionPreset
import com.pencil.chuangyin.core.theme.OUTLINE_ALPHA_MIN
import com.pencil.chuangyin.core.theme.OUTLINE_WIDTH_MAX_DP
import com.pencil.chuangyin.core.theme.OUTLINE_WIDTH_MIN_DP
import com.pencil.chuangyin.core.theme.OutlineColorMode
import com.pencil.chuangyin.core.theme.ThemeState
import com.pencil.chuangyin.core.theme.cornerShape
import com.pencil.chuangyin.core.ui.components.AppDivider
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppSwitch
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.GlassPanel
import com.pencil.chuangyin.core.ui.components.RgbColorPicker
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.ui.motion.pressScale
import com.pencil.chuangyin.resources.R as ResR

// ===================== 设置主菜单 =====================

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenVisual: () -> Unit,
    onOpenLoading: () -> Unit,
    onOpenBackground: () -> Unit,
    onOpenLiquidGlass: () -> Unit,
    onOpenPrint: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenAiConfig: () -> Unit,
    onOpenProxy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        onBack = onBack,
        onOpenVisual = onOpenVisual,
        onOpenLoading = onOpenLoading,
        onOpenBackground = onOpenBackground,
        onOpenLiquidGlass = onOpenLiquidGlass,
        onOpenPrint = onOpenPrint,
        onOpenLicenses = onOpenLicenses,
        onOpenAiConfig = onOpenAiConfig,
        onOpenProxy = onOpenProxy,
        proxyEnabled = state.proxyEnabled,
        proxyScope = state.proxyScope,
        proxyConfigCount = state.proxyConfigCount,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenVisual: () -> Unit,
    onOpenLoading: () -> Unit,
    onOpenBackground: () -> Unit,
    onOpenLiquidGlass: () -> Unit,
    onOpenPrint: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenAiConfig: () -> Unit = {},
    onOpenProxy: () -> Unit = {},
    proxyEnabled: Boolean = false,
    proxyScope: ProxyScope = ProxyScope.PixivOnly,
    proxyConfigCount: Int = 0,
    modifier: Modifier = Modifier,
) {
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
        val proxyScopeText = stringResource(
            if (proxyScope == ProxyScope.Global) {
                R.string.settings_proxy_scope_global
            } else {
                R.string.settings_proxy_scope_pixiv
            },
        )
        // 三态摘要：无配置 / 已开启（含范围）/ 已关闭（含范围），进页面前就能判断当前状态。
        val proxyDesc = when {
            proxyConfigCount == 0 -> stringResource(R.string.settings_menu_proxy_desc_empty)
            proxyEnabled -> stringResource(R.string.settings_menu_proxy_desc_on, proxyScopeText)
            else -> stringResource(R.string.settings_menu_proxy_desc_off, proxyScopeText)
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m))
                .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_xl)),
        ) {
            // 打印与输出：热敏纸宽度、自动连接、产物目录都收进「打印设置」子页，
            // 主菜单只留一个入口，避免功能项把设置首页撑成流水账。
            SettingsGroup(
                label = stringResource(R.string.settings_group_print),
                visible = appeared,
                delayMillis = 0,
            ) {
                SettingsEntry(
                    icon = Icons.Filled.Print,
                    title = stringResource(R.string.settings_menu_print),
                    desc = stringResource(R.string.settings_menu_print_desc),
                    onClick = onOpenPrint,
                )
            }
            SettingsGroup(
                label = stringResource(R.string.settings_group_ai),
                visible = appeared,
                delayMillis = 70,
            ) {
                SettingsEntry(
                    icon = Icons.Filled.AutoAwesome,
                    title = stringResource(R.string.settings_menu_ai),
                    desc = stringResource(R.string.settings_menu_ai_desc),
                    onClick = onOpenAiConfig,
                )
            }
            // 外观按「静态外观 / 动画 / 背景装饰」拆成三页，各自只保留同类设置项。
            SettingsGroup(
                label = stringResource(R.string.settings_group_appearance),
                visible = appeared,
                delayMillis = 140,
            ) {
                SettingsEntry(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.settings_menu_visual),
                    desc = stringResource(R.string.settings_menu_visual_desc),
                    onClick = onOpenVisual,
                )
                SettingsEntryDivider()
                SettingsEntry(
                    icon = Icons.Filled.Animation,
                    title = stringResource(R.string.settings_menu_loading),
                    desc = stringResource(R.string.settings_menu_loading_desc),
                    onClick = onOpenLoading,
                )
                SettingsEntryDivider()
                SettingsEntry(
                    icon = Icons.Filled.Wallpaper,
                    title = stringResource(R.string.settings_menu_background),
                    desc = stringResource(R.string.settings_menu_background_desc),
                    onClick = onOpenBackground,
                )
                SettingsEntryDivider()
                SettingsEntry(
                    icon = Icons.Filled.BlurOn,
                    title = stringResource(R.string.settings_menu_liquid_glass),
                    desc = stringResource(R.string.settings_menu_liquid_glass_desc),
                    onClick = onOpenLiquidGlass,
                )
            }
            SettingsGroup(
                label = stringResource(R.string.settings_group_network),
                visible = appeared,
                delayMillis = 210,
            ) {
                SettingsEntry(
                    icon = Icons.Filled.Router,
                    title = stringResource(R.string.settings_menu_proxy),
                    desc = proxyDesc,
                    onClick = onOpenProxy,
                )
            }
            SettingsGroup(
                label = stringResource(R.string.settings_group_about),
                visible = appeared,
                delayMillis = 280,
            ) {
                SettingsEntry(
                    icon = Icons.Filled.Policy,
                    title = stringResource(R.string.settings_menu_licenses),
                    desc = stringResource(R.string.settings_menu_licenses_desc),
                    onClick = onOpenLicenses,
                )
            }
        }
    }
}

/** 设置分组：小标题 + 圆角卡片容器，整组带错峰入场。子页面复用同一容器保证观感一致。 */
@Composable
internal fun SettingsGroup(
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
internal fun SettingsEntry(
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

/** 卡片内的开关行：图标胶囊 + 标题/说明 + 开关，仅切换状态，不进入子页。 */
@Composable
internal fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
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
                .background(
                    if (enabled) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            )
            AppText(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 组内说明文字：放在卡片下方，用于解释某项功能的完整行为（例如自动连接逻辑）。 */
@Composable
internal fun SettingsGroupNote(text: String) {
    AppText(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = dimensionResource(ResR.dimen.dimen_global_padding_m),
            end = dimensionResource(ResR.dimen.dimen_global_padding_m),
            top = dimensionResource(ResR.dimen.dimen_global_padding_s),
        ),
    )
}

/** 组内分隔线：左侧让出图标宽度，视觉上从文字起始处断开。 */
@Composable
internal fun SettingsEntryDivider() {
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

/** 子页通用骨架：玻璃顶栏 + 可滚动内容列，统一各设置子页的进出场与留白。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassActive = rememberLiquidGlassActive()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = title) },
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
            content()
        }
    }
}

// ===================== 主题外观 =====================

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
        modifier = modifier,
    )
}

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
    modifier: Modifier = Modifier,
) {
    val theme = state.themeState
    SettingsSubPage(
        title = stringResource(R.string.settings_visual_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
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
        onOpenLoading = {},
        onOpenBackground = {},
        onOpenLiquidGlass = {},
        onOpenPrint = {},
        onOpenLicenses = {},
        onOpenAiConfig = {},
        onOpenProxy = {},
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

private fun parseHexColor(input: String): Long? {
    var hex = input.trim().removePrefix("#")
    if (hex.length == 3) {
        hex = buildString { hex.forEach { append(it).append(it) } }
    }
    if (hex.length != 6 && hex.length != 8) return null
    val value = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) 0xFF000000.toLong() or value else value
}
