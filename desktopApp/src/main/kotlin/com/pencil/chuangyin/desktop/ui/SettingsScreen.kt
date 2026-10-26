package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.glass.GlassCard
import com.pencil.chuangyin.desktop.theme.BACKGROUND_ALPHA_MIN
import com.pencil.chuangyin.desktop.theme.BACKGROUND_BLUR_MAX_DP
import com.pencil.chuangyin.desktop.theme.CornerStyle
import com.pencil.chuangyin.desktop.theme.DesktopThemeStore
import com.pencil.chuangyin.desktop.theme.LIQUID_GLASS_BLUR_MAX_DP
import com.pencil.chuangyin.desktop.theme.LIQUID_GLASS_LENS_AMOUNT_MAX_DP
import com.pencil.chuangyin.desktop.theme.LIQUID_GLASS_LENS_HEIGHT_MAX_DP
import com.pencil.chuangyin.desktop.theme.LIQUID_GLASS_SPECTRAL_MAX
import com.pencil.chuangyin.desktop.theme.LIQUID_GLASS_TINT_ALPHA_MIN
import com.pencil.chuangyin.desktop.theme.MotionPreset
import com.pencil.chuangyin.desktop.theme.OUTLINE_ALPHA_MIN
import com.pencil.chuangyin.desktop.theme.OUTLINE_WIDTH_MAX_DP
import com.pencil.chuangyin.desktop.theme.OUTLINE_WIDTH_MIN_DP
import com.pencil.chuangyin.desktop.theme.OutlineColorMode
import com.pencil.chuangyin.desktop.theme.SURFACE_ALPHA_MIN
import com.pencil.chuangyin.desktop.theme.SeedOption
import com.pencil.chuangyin.desktop.theme.ThemeState
import com.pencil.chuangyin.desktop.theme.cornerShape
import com.pencil.chuangyin.desktop.support.OutputPathStore
import com.pencil.chuangyin.desktop.support.PaperWidthStore
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.roundToInt

/**
 * 设置 —— 1:1 对齐安卓端 feature/settings/SettingsScreen.kt 的信息架构：
 * 设置是「菜单页」，点条目进入子页，返回回到菜单。
 * 菜单项与顺序与安卓端逐项一致：
 * AI 配置设置 → 画面设置 → 液态玻璃 → 尺寸设置 → 产物输出路径 → 开放源代码许可。
 *
 * 与安卓端只有能力层差异（不含 UI 结构差异）：
 * - 安卓「飘雪」粒子层由 C++ 渲染，桌面端主题模型暂无对应字段，未收录；
 * - 安卓的液态玻璃「开启前三重警告」源于 C++ 覆盖层崩溃风险，桌面端为 Skia 纯绘制，
 *   不会崩溃/抢焦点，因此直接开关（参数项与取值范围完全沿用安卓常量）。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var route by remember { mutableStateOf(SettingsRoute.Menu) }
    when (route) {
        SettingsRoute.Menu -> SettingsMenuPage(
            onBack = onBack,
            onOpen = { route = it },
        )

        SettingsRoute.AiConfig -> AiConfigPage(onBack = { route = SettingsRoute.Menu })

        SettingsRoute.Visual -> VisualThemePage(
            state = DesktopThemeStore.state.collectAsState().value,
            onBack = { route = SettingsRoute.Menu },
        )

        SettingsRoute.LiquidGlass -> LiquidGlassPage(
            state = DesktopThemeStore.state.collectAsState().value,
            onBack = { route = SettingsRoute.Menu },
        )

        SettingsRoute.Size -> SizeSettingsPage(onBack = { route = SettingsRoute.Menu })

        SettingsRoute.About -> AboutPage(onBack = { route = SettingsRoute.Menu })
    }
}

private enum class SettingsRoute { Menu, AiConfig, Visual, LiquidGlass, Size, About }

// ===================== 菜单页 =====================

@Composable
private fun SettingsMenuPage(
    onBack: () -> Unit,
    onOpen: (SettingsRoute) -> Unit,
) {
    val customOutputPath by OutputPathStore.customPath.collectAsState()
    var outputDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = "设置",
                navigationIcon = {
                    AppIconButton(
                        icon = AppIcon.Back,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
            )
        },
        containerColor = Color.Transparent,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            MenuEntry(
                title = "AI 配置设置",
                desc = "配置 OpenAI 兼容接口的模型",
                onClick = { onOpen(SettingsRoute.AiConfig) },
            )
            SettingsDivider()
            MenuEntry(
                title = "画面设置",
                desc = "主题、描边、粒子、背景",
                onClick = { onOpen(SettingsRoute.Visual) },
            )
            SettingsDivider()
            MenuEntry(
                title = "液态玻璃",
                desc = "渲染实现与详细参数",
                onClick = { onOpen(SettingsRoute.LiquidGlass) },
            )
            SettingsDivider()
            MenuEntry(
                title = "尺寸设置",
                desc = "热敏纸宽度",
                onClick = { onOpen(SettingsRoute.Size) },
            )
            SettingsDivider()
            MenuEntry(
                title = "产物输出路径",
                desc = "目录：${customOutputPath.ifBlank { OutputPathStore.displayPath() }}",
                onClick = { outputDialogOpen = true },
            )
            SettingsDivider()
            MenuEntry(
                title = "开放源代码许可",
                desc = "本应用所使用的开源项目",
                onClick = { onOpen(SettingsRoute.About) },
            )
            SettingsDivider()
        }
    }

    if (outputDialogOpen) {
        OutputPathDialog(
            currentPath = customOutputPath,
            onDismiss = { outputDialogOpen = false },
            onConfirm = { path ->
                OutputPathStore.setCustomPath(path)
                outputDialogOpen = false
            },
        )
    }
}

/** 产物输出路径弹窗：可直接编辑完整路径，也可用系统目录选择器挑一个（对齐安卓端 OutputPathDialog）。 */
@Composable
private fun OutputPathDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf(currentPath) }
    val defaultPath = remember { OutputPathStore.defaultDir().absolutePath }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "产物输出路径", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column {
                Text(
                    text = "完整路径",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                AppTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = defaultPath,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                AppSecondaryButton(
                    text = "选择目录…",
                    onClick = { pickDirectory()?.let { input = it } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "留空则使用默认目录：$defaultPath",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input.trim()) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

// ===================== 尺寸设置 =====================

/** 尺寸设置 —— 1:1 对齐安卓端 SizeSettingsScreen（热敏纸宽度 1–58 mm）。 */
@Composable
private fun SizeSettingsPage(onBack: () -> Unit) {
    val widthMm by PaperWidthStore.widthMm.collectAsState()

    SettingsPage(title = "尺寸设置", onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "热敏纸宽度",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$widthMm mm",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = widthMm.toFloat(),
            onValueChange = { value -> PaperWidthStore.setWidthMm(value.roundToInt()) },
            valueRange = PaperWidthStore.MIN_MM.toFloat()..PaperWidthStore.MAX_MM.toFloat(),
            steps = PaperWidthStore.MAX_MM - PaperWidthStore.MIN_MM - 1,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "范围 1–58 mm，对应打印头像素宽自动换算",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MenuEntry(title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppIconView(
            icon = AppIcon.ChevronRight,
            size = 20.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ===================== 视觉主题 =====================

@Composable
private fun VisualThemePage(state: ThemeState, onBack: () -> Unit) {
    SettingsPage(title = "视觉主题", onBack = onBack) {
        SectionLabel("主题色")
        SeedSwatchRow(
            selectedArgb = state.seedArgb,
            onSelected = { argb -> DesktopThemeStore.update { it.copy(seedArgb = argb) } },
        )
        Spacer(Modifier.height(12.dp))
        HexColorRow(
            currentArgb = state.seedArgb,
            onApplied = { argb -> DesktopThemeStore.update { it.copy(seedArgb = argb) } },
        )

        SettingsDivider()

        ToggleRow(
            checked = state.darkTheme,
            onCheckedChange = { enabled -> DesktopThemeStore.update { it.copy(darkTheme = enabled) } },
            label = "深色模式",
        )

        SettingsDivider()

        SectionLabel("形状")
        CornerStylePicker(
            selected = state.cornerStyle,
            strength = state.cornerStrength,
            onSelected = { style -> DesktopThemeStore.update { it.copy(cornerStyle = style) } },
        )
        Spacer(Modifier.height(12.dp))
        SliderRow(
            label = "形状强度",
            value = state.cornerStrength,
            valueText = "%.2f".format(state.cornerStrength),
            valueRange = 0f..1f,
            enabled = state.cornerStyle != CornerStyle.Square,
            onValueChange = { v -> DesktopThemeStore.update { it.copy(cornerStrength = v) } },
        )

        Spacer(Modifier.height(12.dp))
        SectionLabel("动效")
        AppSegmentedRow(
            options = MotionPreset.entries.map { it.label() },
            selectedIndex = MotionPreset.entries.indexOf(state.motionPreset),
            onSelected = { index ->
                DesktopThemeStore.update { it.copy(motionPreset = MotionPreset.entries[index]) }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        SettingsDivider()

        SectionLabel("控件描边")
        ToggleRow(
            checked = state.outlineEnabled,
            onCheckedChange = { enabled -> DesktopThemeStore.update { it.copy(outlineEnabled = enabled) } },
            label = "启用描边",
        )
        SliderRow(
            label = "描边宽度",
            value = state.outlineWidthDp,
            valueText = "%.1f dp".format(state.outlineWidthDp),
            valueRange = OUTLINE_WIDTH_MIN_DP..OUTLINE_WIDTH_MAX_DP,
            enabled = state.outlineEnabled,
            steps = (((OUTLINE_WIDTH_MAX_DP - OUTLINE_WIDTH_MIN_DP) / 0.5f).toInt() - 1),
            onValueChange = { v -> DesktopThemeStore.update { it.copy(outlineWidthDp = v) } },
        )
        SliderRow(
            label = "描边不透明度",
            value = state.outlineAlpha,
            valueText = "%.2f".format(state.outlineAlpha),
            valueRange = OUTLINE_ALPHA_MIN..1f,
            enabled = state.outlineEnabled,
            onValueChange = { v -> DesktopThemeStore.update { it.copy(outlineAlpha = v) } },
        )
        AppSegmentedRow(
            options = OutlineColorMode.entries.map { it.label() },
            selectedIndex = OutlineColorMode.entries.indexOf(state.outlineColorMode),
            onSelected = { index ->
                DesktopThemeStore.update { it.copy(outlineColorMode = OutlineColorMode.entries[index]) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.outlineEnabled && state.outlineColorMode == OutlineColorMode.Rgb) {
            Spacer(Modifier.height(12.dp))
            HexColorRow(
                currentArgb = state.outlineArgb,
                onApplied = { argb -> DesktopThemeStore.update { it.copy(outlineArgb = argb) } },
            )
        }

        SettingsDivider()

        SectionLabel("背景")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppPrimaryButton(
                text = if (state.backgroundPath.isNullOrBlank()) "选择背景图片" else "更换背景图片",
                onClick = {
                    pickImageFile()?.let { path ->
                        DesktopThemeStore.update { it.copy(backgroundPath = path) }
                    }
                },
                modifier = Modifier.weight(1f),
            )
            AppSecondaryButton(
                text = "清除",
                onClick = { DesktopThemeStore.update { it.copy(backgroundPath = null) } },
                enabled = !state.backgroundPath.isNullOrBlank(),
                modifier = Modifier.weight(1f),
            )
        }
        if (!state.backgroundPath.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.backgroundPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SliderRow(
            label = "背景不透明度",
            value = state.backgroundAlpha,
            valueText = "%.2f".format(state.backgroundAlpha),
            valueRange = BACKGROUND_ALPHA_MIN..1f,
            onValueChange = { v -> DesktopThemeStore.update { it.copy(backgroundAlpha = v) } },
        )
        SliderRow(
            label = "组件表面不透明度",
            value = state.surfaceAlpha,
            valueText = "%.2f".format(state.surfaceAlpha),
            valueRange = SURFACE_ALPHA_MIN..1f,
            onValueChange = { v -> DesktopThemeStore.update { it.copy(surfaceAlpha = v) } },
        )
        SliderRow(
            label = "背景模糊",
            value = state.backgroundBlur,
            valueText = "%.0f dp".format(state.backgroundBlur),
            valueRange = 0f..BACKGROUND_BLUR_MAX_DP,
            onValueChange = { v -> DesktopThemeStore.update { it.copy(backgroundBlur = v) } },
        )
    }
}

/** 六个种子色圆形色板（对齐安卓端 SeedOption 预设）。 */
@Composable
private fun SeedSwatchRow(selectedArgb: Long, onSelected: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SeedOption.entries.forEach { option ->
            val selected = (selectedArgb and 0xFFFFFF) == (option.color and 0xFFFFFF)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(option.color))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelected(option.color) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    AppIconView(
                        icon = AppIcon.Check,
                        size = 18.dp,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** 十六进制颜色输入行（对齐安卓端 ColorCodeRow）。 */
@Composable
private fun HexColorRow(currentArgb: Long, onApplied: (Long) -> Unit) {
    var input by remember(currentArgb) { mutableStateOf(argbToHex(currentArgb)) }
    var invalid by remember(currentArgb) { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppTextField(
            value = input,
            onValueChange = {
                input = it
                invalid = false
            },
            placeholder = "RRGGBB",
            onDone = {
                val argb = parseHexArgb(input)
                if (argb == null) invalid = true else onApplied(argb)
            },
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.small)
                .background(
                    parseHexArgb(input)?.let { Color(it) }
                        ?: MaterialTheme.colorScheme.surfaceVariant
                ),
        )
        AppSecondaryButton(
            text = "应用",
            onClick = {
                val argb = parseHexArgb(input)
                if (argb == null) invalid = true else onApplied(argb)
            },
        )
    }
    if (invalid) {
        Text(
            text = "颜色格式无效，请输入 RRGGBB 或 AARRGGBB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** 四种形状预览选择器（对齐安卓端 CornerStylePicker）。 */
@Composable
private fun CornerStylePicker(
    selected: CornerStyle,
    strength: Float,
    onSelected: (CornerStyle) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CornerStyle.entries.forEach { style ->
            val isSelected = style == selected
            Column(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onSelected(style) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(cornerShape(style, strength, 16.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = cornerShape(style, strength, 16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        AppIconView(
                            icon = AppIcon.Check,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = style.label(),
                    style = MaterialTheme.typography.labelMedium,
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

// ===================== 液态玻璃 =====================

@Composable
private fun LiquidGlassPage(state: ThemeState, onBack: () -> Unit) {
    SettingsPage(title = "液态玻璃", onBack = onBack) {
        SectionLabel("液态玻璃")
        Text(
            text = "开启后，控件与底部导航栏将使用玻璃材质渲染（参数与安卓端共用同一套取值）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        ToggleRow(
            checked = state.liquidGlassControlsEnabled,
            onCheckedChange = { enabled ->
                DesktopThemeStore.update { it.copy(liquidGlassControlsEnabled = enabled) }
            },
            label = "控件玻璃",
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            checked = state.liquidGlassNavbarEnabled,
            onCheckedChange = { enabled ->
                DesktopThemeStore.update { it.copy(liquidGlassNavbarEnabled = enabled) }
            },
            label = "导航栏玻璃",
        )

        SettingsDivider()

        SectionLabel("细节参数")
        SliderRow(
            label = "模糊强度",
            value = state.liquidGlassBlurDp,
            valueText = "%.1f".format(state.liquidGlassBlurDp),
            valueRange = 0f..LIQUID_GLASS_BLUR_MAX_DP,
            onValueChange = { v -> DesktopThemeStore.update { it.copy(liquidGlassBlurDp = v) } },
        )
        SliderRow(
            label = "透镜高度",
            value = state.liquidGlassLensHeightDp,
            valueText = "%.1f".format(state.liquidGlassLensHeightDp),
            valueRange = 0f..LIQUID_GLASS_LENS_HEIGHT_MAX_DP,
            onValueChange = { v -> DesktopThemeStore.update { it.copy(liquidGlassLensHeightDp = v) } },
        )
        SliderRow(
            label = "透镜位移",
            value = state.liquidGlassLensAmountDp,
            valueText = "%.1f".format(state.liquidGlassLensAmountDp),
            valueRange = 0f..LIQUID_GLASS_LENS_AMOUNT_MAX_DP,
            onValueChange = { v -> DesktopThemeStore.update { it.copy(liquidGlassLensAmountDp = v) } },
        )
        SliderRow(
            label = "边缘色散",
            value = state.liquidGlassSpectral,
            valueText = "%.2f".format(state.liquidGlassSpectral),
            valueRange = 0f..LIQUID_GLASS_SPECTRAL_MAX,
            onValueChange = { v -> DesktopThemeStore.update { it.copy(liquidGlassSpectral = v) } },
        )
        SliderRow(
            label = "着色不透明度",
            value = state.liquidGlassTintAlpha,
            valueText = "%.2f".format(state.liquidGlassTintAlpha),
            valueRange = LIQUID_GLASS_TINT_ALPHA_MIN..1f,
            onValueChange = { v -> DesktopThemeStore.update { it.copy(liquidGlassTintAlpha = v) } },
        )

        Spacer(Modifier.height(8.dp))
        ToggleRow(
            checked = state.liquidGlassShadowOn,
            onCheckedChange = { on -> DesktopThemeStore.update { it.copy(liquidGlassShadowOn = on) } },
            label = "投射阴影",
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            checked = state.liquidGlassGlassOn,
            onCheckedChange = { on -> DesktopThemeStore.update { it.copy(liquidGlassGlassOn = on) } },
            label = "玻璃折射",
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            checked = state.liquidGlassContourEdge,
            onCheckedChange = { on -> DesktopThemeStore.update { it.copy(liquidGlassContourEdge = on) } },
            label = "轮廓高光",
        )

        SettingsDivider()

        SectionLabel("预览")
        LiquidGlassPreview()
    }
}

@Composable
private fun LiquidGlassPreview() {
    var toggleOn by remember { mutableStateOf(true) }
    var segment by remember { mutableIntStateOf(1) }
    var sliderValue by remember { mutableStateOf(0.5f) }
    var text by remember { mutableStateOf("") }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "开关", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Switch(checked = toggleOn, onCheckedChange = { toggleOn = it })
        }
        Spacer(Modifier.height(12.dp))
        AppSegmentedRow(
            options = listOf("开关", "按钮", "滑动"),
            selectedIndex = segment,
            onSelected = { segment = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        AppTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = "预览输入框",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        AppPrimaryButton(
            text = "预览按钮",
            onClick = { toggleOn = !toggleOn },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ===================== 开源许可 =====================

@Composable
private fun AboutPage(onBack: () -> Unit) {
    SettingsPage(title = "开源许可", onBack = onBack) {
        SectionLabel("铅笔创印Pro 桌面版")
        Text(
            text = "版本 2.0.0",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "本项目由 AI 编程助手构建：Trae + DeepSeek V4.1F。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsDivider()

        SectionLabel("界面与液态玻璃")
        OpenSourceRows(
            listOf(
                "AndroidLiquidGlass" to "Apache License 2.0",
                "Kyant0/shapes" to "Apache License 2.0",
                "LiquidGlass-Cpp" to "Apache License 2.0",
                "Dear ImGui" to "MIT License",
                "MaterialKolor" to "MIT License",
            )
        )

        SettingsDivider()

        SectionLabel("UI 与运行时")
        OpenSourceRows(
            listOf(
                "Kotlin / Kotlin Coroutines" to "Apache License 2.0",
                "kotlinx.serialization" to "Apache License 2.0",
                "Jetpack Compose / Material 3" to "Apache License 2.0",
                "Compose Multiplatform" to "Apache License 2.0",
                "Skiko (Skia)" to "BSD 3-Clause",
            )
        )

        SettingsDivider()

        SectionLabel("网络与图像")
        OpenSourceRows(
            listOf(
                "OkHttp" to "Apache License 2.0",
                "Retrofit" to "Apache License 2.0",
                "Coil" to "Apache License 2.0",
                "OpenCV" to "Apache License 2.0",
                "Apache PDFBox" to "Apache License 2.0",
            )
        )

        SettingsDivider()

        SectionLabel("蓝牙打印")
        OpenSourceRows(
            listOf(
                "JNA (Java Native Access)" to "Apache-2.0 / LGPL-2.1",
                "jSerialComm" to "LGPL-3.0 / Apache-2.0",
            )
        )

        SettingsDivider()

        SectionLabel("其他")
        OpenSourceRows(
            listOf(
                "javax.inject" to "Apache License 2.0",
                "Google ADK for Kotlin" to "Apache License 2.0",
                "Gradle" to "Apache License 2.0",
            )
        )

        SettingsDivider()

        Text(
            text = "完整清单与上游链接见仓库 README 的「开源致谢」一节。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OpenSourceRows(items: List<Pair<String, String>>) {
    items.forEach { (name, license) ->
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = license,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ===================== 复用小组件（对齐安卓端同名组件） =====================

@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = title,
                navigationIcon = {
                    AppIconButton(
                        icon = AppIcon.Back,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
            )
        },
        containerColor = Color.Transparent,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .widthIn(max = 720.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun ToggleRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    steps: Int = 0,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

// ===================== 工具函数 =====================

private fun CornerStyle.label(): String = when (this) {
    CornerStyle.Rounded -> "圆角"
    CornerStyle.Square -> "直角"
    CornerStyle.Diamond -> "菱形"
    CornerStyle.Parallelogram -> "斜切"
}

private fun MotionPreset.label(): String = when (this) {
    MotionPreset.Full -> "完整"
    MotionPreset.Expressive -> "灵动"
    MotionPreset.Reduced -> "精简"
}

private fun OutlineColorMode.label(): String = when (this) {
    OutlineColorMode.Seed -> "主题色"
    OutlineColorMode.Rgb -> "自定义"
    OutlineColorMode.Rainbow -> "彩虹"
}

private fun pickImageFile(): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择背景图片"
        fileSelectionMode = JFileChooser.FILES_ONLY
        isAcceptAllFileFilterUsed = false
        fileFilter = FileNameExtensionFilter(
            "图片 (jpg / jpeg / png / webp / bmp)",
            "jpg", "jpeg", "png", "webp", "bmp",
        )
    }
    val chosen: File? = if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
    return chosen?.absolutePath
}

/** 用系统目录选择器挑一个输出目录（桌面端替代安卓端的路径文本输入）。 */
private fun pickDirectory(): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择产物输出目录"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        currentDirectory = runCatching { OutputPathStore.activeDir() }.getOrNull()
    }
    val chosen: File? = if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
    return chosen?.absolutePath
}

private fun argbToHex(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)

private fun parseHexArgb(raw: String): Long? {
    val cleaned = raw.trim().removePrefix("#")
    val body = when (cleaned.length) {
        6 -> "FF$cleaned"
        8 -> cleaned
        else -> return null
    }
    return body.toLongOrNull(16)?.takeIf { it in 0..0xFFFFFFFFL }
}
