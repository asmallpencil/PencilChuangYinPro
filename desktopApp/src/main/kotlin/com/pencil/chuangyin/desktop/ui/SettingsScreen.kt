package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pencil.chuangyin.desktop.ui.motion.StaggerIn
import com.pencil.chuangyin.desktop.ui.motion.pressScale
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .widthIn(max = 720.dp),
        ) {
            StaggerIn(index = 0) {
                SettingsGroupCard(label = "智能能力") {
                    MenuEntry(
                        icon = AppIcon.AiTool,
                        title = "AI 配置设置",
                        desc = "配置 OpenAI 兼容接口的模型",
                        onClick = { onOpen(SettingsRoute.AiConfig) },
                    )
                }
            }
            StaggerIn(index = 1) {
                SettingsGroupCard(label = "外观与动效") {
                    MenuEntry(
                        icon = AppIcon.PhotoLibrary,
                        title = "画面设置",
                        desc = "主题、描边、粒子、背景",
                        onClick = { onOpen(SettingsRoute.Visual) },
                    )
                    SettingsEntryDivider()
                    MenuEntry(
                        icon = AppIcon.Ink,
                        title = "液态玻璃",
                        desc = "渲染实现与详细参数",
                        onClick = { onOpen(SettingsRoute.LiquidGlass) },
                    )
                    SettingsEntryDivider()
                    MenuEntry(
                        icon = AppIcon.Paper,
                        title = "尺寸设置",
                        desc = "热敏纸宽度",
                        onClick = { onOpen(SettingsRoute.Size) },
                    )
                }
            }
            StaggerIn(index = 2) {
                SettingsGroupCard(label = "输出与关于") {
                    MenuEntry(
                        icon = AppIcon.FolderZip,
                        title = "产物输出路径",
                        desc = "根目录：${customOutputPath.ifBlank { OutputPathStore.displayPath() }}（AI/P站/创作各自子目录）",
                        onClick = { outputDialogOpen = true },
                    )
                    SettingsEntryDivider()
                    MenuEntry(
                        icon = AppIcon.Description,
                        title = "开放源代码许可",
                        desc = "本应用所使用的开源项目",
                        onClick = { onOpen(SettingsRoute.About) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
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

/** 设置分组：小标题 + 玻璃圆角卡片容器（对齐安卓端 SettingsGroup）。 */
@Composable
private fun SettingsGroupCard(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
        )
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(vertical = 4.dp),
            content = content,
        )
    }
}

/** 卡片内的一行设置项：图标胶囊 + 标题/说明 + 右侧箭头，按下有物理回弹。 */
@Composable
private fun MenuEntry(
    icon: AppIcon,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.98f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            AppIconView(
                icon = icon,
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
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

/** 组内分隔线：让出图标胶囊宽度，从文字起始处断开（对齐安卓端 SettingsEntryDivider）。 */
@Composable
private fun SettingsEntryDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 72.dp, end = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    )
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

/**
 * 开源项目清单：按名称字母序，排名不分先后。
 * 无论参与方式是「源码 vendor」「Gradle 依赖」还是「移植/仿写/参照」，
 * 所有项目一视同仁，贡献同等重要。与安卓端 feature:settings 及仓库 README 保持一致。
 */
private data class OpenSourceEntry(
    val name: String,
    val author: String,
    val license: String,
    val contribution: String,
)

private val OPEN_SOURCE_LIST: List<OpenSourceEntry> = listOf(
    OpenSourceEntry(
        "Accompanist Permissions", "Google", "Apache-2.0",
        "相机、相册、蓝牙等运行时权限的封装，是拍照扫描与打印入口的前置门。",
    ),
    OpenSourceEntry(
        "Android Gradle Plugin", "Google", "Apache-2.0",
        "Android 构建插件：多模块工程、CMake 原生构建、R8 混淆与资源压缩均由其驱动。",
    ),
    OpenSourceEntry(
        "AndroidLiquidGlass", "Kyant0", "Apache-2.0",
        "液态玻璃的起点。core:backdrop 即其裁剪移植版，提供模糊、透镜折射、边缘高光、噪点与弹簧动画的完整参数体系。",
    ),
    OpenSourceEntry(
        "AndroidX Activity Compose", "Google", "Apache-2.0",
        "Compose 宿主 Activity，以及启动系统相机与文件选择器的通道。",
    ),
    OpenSourceEntry(
        "AndroidX CameraX", "Google", "Apache-2.0",
        "拍照扫描的取景与图像采集管线，实时把帧交给 OpenCV 做文档识别。",
    ),
    OpenSourceEntry(
        "AndroidX Core KTX", "Google", "Apache-2.0",
        "Kotlin 扩展与基础能力，全模块通用底座。",
    ),
    OpenSourceEntry(
        "AndroidX DataStore", "Google", "Apache-2.0",
        "设置项、AI 模型配置、P 站账号令牌的键值持久化。",
    ),
    OpenSourceEntry(
        "AndroidX ExifInterface", "Google", "Apache-2.0",
        "相册照片的 EXIF 方向归一，避免导入图片打印出来是躺倒的。",
    ),
    OpenSourceEntry(
        "AndroidX Fragment KTX", "Google", "Apache-2.0",
        "P 站模块承载 MainHostFragment 的宿主结构所需的编译基础。",
    ),
    OpenSourceEntry(
        "AndroidX Hilt Navigation Compose", "Google", "Apache-2.0",
        "把注入的 ViewModel 绑定到导航图，各页面状态持有者的接线方式。",
    ),
    OpenSourceEntry(
        "AndroidX Lifecycle", "Google", "Apache-2.0",
        "ViewModel 与生命周期感知，全部页面状态流的承载者。",
    ),
    OpenSourceEntry(
        "AndroidX Navigation Compose", "Google", "Apache-2.0",
        "三个顶级 Tab（首页 / P 站 / AI）与各功能页之间的导航图与返回栈。",
    ),
    OpenSourceEntry(
        "AndroidX Test Ext JUnit", "Google", "Apache-2.0",
        "扫描管线的仪器测试运行器（androidTest），用来验证文档边缘检测在斜拍、出界、暗光下的鲁棒性。",
    ),
    OpenSourceEntry(
        "Apache PDFBox", "Apache 基金会", "Apache-2.0",
        "桌面端 PDF 导入：取页数、按页栅格化（桌面端没有 Android 的 PdfRenderer）。",
    ),
    OpenSourceEntry(
        "BaseRecyclerViewAdapterHelper (BRVAH)", "CymChad", "MIT",
        "列表适配与多类型 Item 的组织方式。P 站模块移植自 Pix-EzViewer，而它以 include(\":BRVAH\") 源码子模块内建本库，本项目沿用同一套列表与分页思路。",
    ),
    OpenSourceEntry(
        "Coil", "coil-kt 团队", "Apache-2.0",
        "图片加载与内存/磁盘缓存，P 站插图、图库缩略图与打印预览全靠它。",
    ),
    OpenSourceEntry(
        "Compose Multiplatform", "JetBrains", "Apache-2.0",
        "桌面端整套 UI 运行时，与 Android 共用同一套 Composition 与绘制模型，才可能做到双端 UI 统一。",
    ),
    OpenSourceEntry(
        "Dear ImGui", "Omar Cornut & 全体贡献者", "MIT",
        "Android 端 C++ 即时模式 UI 框架，承载液态玻璃的光学实验与调试面板。",
    ),
    OpenSourceEntry(
        "Google ADK for Kotlin", "Google", "Apache-2.0",
        "AI 打印助手的工具协议内核：注解 + KSP 在编译期生成工具包装器。",
    ),
    OpenSourceEntry(
        "Gradle", "Gradle Inc.", "Apache-2.0",
        "构建系统本体；仓库自带 wrapper 8.13，克隆即可构建。",
    ),
    OpenSourceEntry(
        "Hilt / Dagger", "Google / Square", "Apache-2.0",
        "全项目依赖注入：打印控制器、P 站会话、AI 引擎、各 ViewModel 均由它装配。",
    ),
    OpenSourceEntry(
        "ImageCorrection", "jtwws", "上游未声明",
        "文档扫描的双路径思路来源：路径 A（Otsu 分离白纸 + 形态学清理 + 最大外轮廓）与路径 B（Otsu 派生双阈值 Canny + 轮廓四边形近似）。core:scanner 的 PageDetector 按这两条路径实现。",
    ),
    OpenSourceEntry(
        "JNA (Java Native Access)", "JNA 社区", "Apache-2.0 / LGPL-2.1",
        "桌面端绕过 Java 蓝牙栈限制，直接调用 Winsock AF_BTH 做 RFCOMM 直连打印机。",
    ),
    OpenSourceEntry(
        "javax.inject", "JSR-330", "Apache-2.0",
        "@Inject 标准注解；桌面端复用 Android 侧会话类时保持两边注入写法一致。",
    ),
    OpenSourceEntry(
        "JetBrains Annotations", "JetBrains", "Apache-2.0",
        "编译期空值注解，液态玻璃与形状库的 API 契约标注。",
    ),
    OpenSourceEntry(
        "Jetpack Compose", "Google / JetBrains", "Apache-2.0",
        "全部声明式 UI。液态玻璃组件、打印预览、P 站图廊、AI 对话流全是 Compose。",
    ),
    OpenSourceEntry(
        "jSerialComm", "Fazecast", "LGPL-3.0 / Apache-2.0",
        "桌面端读写「蓝牙链接上的标准串行 (COMx)」虚拟串口；无 COM 口时退回 RFCOMM 直连。",
    ),
    OpenSourceEntry(
        "JUnit 4", "JUnit 团队", "EPL-1.0",
        "测试框架本体，扫描管线测试用例的断言与运行器基础。",
    ),
    OpenSourceEntry(
        "Ketch", "khushpanchal", "上游未声明",
        "图片下载管线的组织方式（队列、暂停/恢复、进度回调）。P 站模块移植自 Pix-EzViewer，而它以 include(\":ketch\") 源码子模块内建本库，本项目的下载与缓存流程沿用同一套分层：队列 → 进度 → 落盘 → 通知 UI。",
    ),
    OpenSourceEntry(
        "Kotlin", "JetBrains", "Apache-2.0",
        "全项目开发语言（Android + 桌面端 + Gradle 脚本）。",
    ),
    OpenSourceEntry(
        "kotlinx.coroutines", "JetBrains", "Apache-2.0",
        "协程与 Flow。打印分包节流、AI 的 SSE 流式读取、P 站分页加载全靠它。",
    ),
    OpenSourceEntry(
        "kotlinx.serialization", "JetBrains", "Apache-2.0",
        "JSON 序列化：P 站接口响应、AI 工具参数、会话与配置持久化。",
    ),
    OpenSourceEntry(
        "KSP (Kotlin Symbol Processing)", "Google / JetBrains", "Apache-2.0",
        "编译期代码生成：Hilt 的依赖图、ADK 的工具包装器都由它在构建期生成。",
    ),
    OpenSourceEntry(
        "Kyant0/shapes", "Kyant0", "Apache-2.0",
        "圆角矩形与连续曲率形状 API，是液态玻璃透镜折射的几何基础。",
    ),
    OpenSourceEntry(
        "LiquidGlass-Cpp", "默檐 (SilentEaves)", "Apache-2.0",
        "Android 端 C++ 玻璃渲染管线的来源实现，提供完整的渲染顺序、采样与高光参数。",
    ),
    OpenSourceEntry(
        "Material 3（含 Expressive）", "Google / JetBrains", "Apache-2.0",
        "组件与设计语言，Expressive 提供动态形状与色彩过渡。",
    ),
    OpenSourceEntry(
        "Material Components for Android", "Google", "Apache-2.0",
        "传统视图体系的组件基座；承载 MainHostFragment 时需本库在 classpath 可见。",
    ),
    OpenSourceEntry(
        "Material Icons Extended", "Google", "Apache-2.0",
        "扩展图标集，各页面的动作图标来源。",
    ),
    OpenSourceEntry(
        "MaterialKolor", "Jordond", "MIT",
        "由壁纸/图片生成动态配色，是壁纸取色与自适应主题的算法来源。",
    ),
    OpenSourceEntry(
        "Math Curve Loaders", "paidax01", "上游未声明",
        "加载动画的曲线来源：玫瑰线、李萨如、内摆线、心脏线、傅里叶波形等 20 条数学参数化曲线，以及「粒子尾迹 + 淡轨迹」的数据驱动渲染模型。core:ui 的 MathCurveLoadingIndicator 按其曲线定义与驱动参数移植。",
    ),
    OpenSourceEntry(
        "OkHttp", "Square", "Apache-2.0",
        "HTTP/2、连接池与 SSE 流式读取；P 站接口、图片下载、AI 流式响应都跑在它上面。",
    ),
    OpenSourceEntry(
        "OpenCV", "OpenCV 团队", "Apache-2.0",
        "文档扫描的算法引擎：自适应二值、Otsu、Canny、四边形近似、透视变换与去阴影。",
    ),
    OpenSourceEntry(
        "Operit AI", "AAswordman", "LGPL-3.0",
        "AI Agent 运行时的架构范本：工具调用的确认门（不可逆操作先问用户）、分档权限模式（总是询问 / 仅危险操作 / 放行）、上下文折叠与工具结果回灌。core:agent 的 ToolConfirmationGate、AgentPermission、ContextFolder 按这套模型实现。",
    ),
    OpenSourceEntry(
        "PiPixiv", "darriousliu", "Apache-2.0",
        "P 站 App API 的接口定义与请求签名方式。评论接口的必填参数与 header 组合即参照其实现对齐，否则会表现为「评论区永远显示暂无评论」。",
    ),
    OpenSourceEntry(
        "Pix-EzViewer", "Ultranity / Perol_Notsfsssf", "MIT",
        "P 站功能的直接来源：① PixivConfig 的 client_id / client_secret / UA / X-Client-Hash 盐值复刻自其 RestClient；② Pkce.kt 直接移植自其 Pkce 实现；③ PixivApi 接口清单与小说解析参照其 PixivApiService 与 NovelViewModel.parseWebNovel；④ 账号持久化替代了它的 Room 用户表；⑤ 页面信息架构与 UI 组织方式也以它为蓝本。",
    ),
    OpenSourceEntry(
        "Retrofit", "Square", "Apache-2.0",
        "P 站 App API 的声明式 HTTP 封装，接口定义即文档。",
    ),
    OpenSourceEntry(
        "Skia / Skiko", "Google / JetBrains", "BSD-3-Clause",
        "桌面端的位图绘制与栅格化后端；文本逐行位图、图片抖动与预览都经由它。",
    ),
)

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

        Text(
            text = "以下 ${OPEN_SOURCE_LIST.size} 个项目按名称字母序排列，排名不分先后。" +
                "无论参与方式是源码移植、构建依赖还是实现参照，贡献同等重要，共同鸣谢。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsDivider()

        // 与安卓端开源许可页一致：每个项目一张玻璃卡片。
        OPEN_SOURCE_LIST.forEach { entry ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                elevation = 1f,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(text = entry.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${entry.author} · ${entry.license}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entry.contribution,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        SettingsDivider()

        Text(
            text = "完整清单、上游链接与许可原文见仓库 README 的「开源致谢」一节与 licenses/ 目录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        StaggerIn(index = 0, offsetDivisor = 20) {
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
