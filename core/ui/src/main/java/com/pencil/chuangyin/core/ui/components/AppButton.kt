package com.pencil.chuangyin.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.theme.LiquidGlassWidgetKind
import com.pencil.chuangyin.core.theme.LocalControlOutline
import com.pencil.chuangyin.core.theme.LocalLiquidGlassWidgetHost
import com.pencil.chuangyin.core.theme.controlOutline
import com.pencil.chuangyin.core.theme.cornerSafeInset
import com.pencil.chuangyin.resources.R as ResR

/**
 * Awesome UI 按钮的零硬编码重写：主按钮 / 次按钮 / 描边按钮，行为经 onClick 参数暴露。
 * 主题描边开启时统一隐藏 M3 原生边框，只保留主题描边（避免双重边框错位/偏移）。
 *
 * 禁用态（如条件未就绪的「打印/保存」）**不绘制主题描边**：厚重描边会把灰色禁用按钮
 * 重新框成一个「看起来可点」的控件，误导用户；此时回退 M3 的浅色默认边框，语义清晰。
 *
 * 液态玻璃：控件开关开启、按钮可用且原生覆盖层可用时，改用 C++ 原生玻璃按钮（透明占位盒 +
 * 原生绘制与点击，文案/尺寸由原生层按占位盒渲染，见 [NativeGlassButton]）。
 *
 * 前置图标有两种写法：
 * - [leadingIcon]（推荐）：传 [ImageVector]，原生层把图标渲染成位图纹理并与文字**并排**
 *   居中绘制（`lgx::PillButton::draw` 已内置 icon + label 布局），因此带图标按钮同样走真玻璃；
 * - [leadingContent]：任意 Composable，原生层无法表达，带此参数的按钮自动回退 M3 实现。
 *
 * 是否走 C++ 原生玻璃按钮：需启用、无任意 [leadingContent]、且原生覆盖层已注入。
 */
@Composable
private fun useNativeButton(enabled: Boolean, leadingContent: Any?): Boolean =
    enabled && leadingContent == null && rememberLiquidGlassActive() &&
        LocalLiquidGlassWidgetHost.current != null

/**
 * 原生玻璃按钮的公共骨架：透明占位盒承载原生绘制与点击。
 *
 * - [fillWidth] = true（主/次按钮）：按全局按钮高度铺满宽度，原生层在盒内居中绘制；
 * - [fillWidth] = false（描边按钮）：用不可见内容按 M3 度量撑出自适应宽度。
 *
 * 三处按钮的原生分支此前完全重复，抽到这里统一维护。调用前提见 [useNativeButton]
 * （[leadingContent] 非空时不会走到这里，故此处内容仅含图标/文字）。
 */
@Composable
private fun NativeGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    leadingIcon: ImageVector?,
    fillWidth: Boolean,
) {
    val boxModifier = if (fillWidth) {
        modifier
            .fillMaxWidth()
            .height(dimensionResource(ResR.dimen.dimen_global_btn_height_min))
    } else {
        modifier
    }
    Box(
        modifier = boxModifier.nativeGlassWidget(
            kind = LiquidGlassWidgetKind.Button,
            key = remember { Any() },
            label = listOf(text),
            state = emptyList(),
            textSizeSp = MaterialTheme.typography.labelLarge.fontSize.value,
            icon = leadingIcon?.let { rememberNativeGlassIcon(it) },
        ) { onClick() },
    ) {
        if (!fillWidth) {
            // 原生层自行居中绘制；这里用不可见内容按 M3 度量撑出自适应宽度。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .alpha(0f)
                    .padding(appButtonContentPadding()),
            ) {
                AppButtonContent(text, leadingContent = null, leadingIcon = leadingIcon)
            }
        }
    }
}

/**
 * 按钮内容（M3 回退路径）：图标 + 文字横向排列，无前置内容时退化为纯文字。
 * 主题形状收起后仍沿用 M3 默认间距，与原生层 `BUTTON_ICON_GAP` 观感一致。
 */
@Composable
private fun RowScope.AppButtonContent(
    text: String,
    leadingContent: @Composable (() -> Unit)?,
    leadingIcon: ImageVector?,
) {
    if (leadingContent == null && leadingIcon == null) {
        Text(text)
        return
    }
    if (leadingContent != null) {
        leadingContent()
    } else {
        Icon(imageVector = leadingIcon!!, contentDescription = null)
    }
    Text(
        text = text,
        modifier = Modifier.padding(start = dimensionResource(ResR.dimen.dimen_global_padding_s)),
    )
}

/** 按钮内容内边距：以全局 padding_l（=M3 默认 24dp）为基准，按当前边角风格/强度
 * 追加形状安全内边距，防止高档菱形/平行四边形切掉靠边文字（按钮使用 shapes.medium，档系数 1）。
 * 垂直内边距沿用 M3 默认值，只放宽水平方向。
 */
@Composable
private fun appButtonContentPadding(): PaddingValues {
    val base = dimensionResource(ResR.dimen.dimen_global_padding_l)
    val safe = cornerSafeInset(shapeScale = 1f)
    val defaultVertical = ButtonDefaults.ContentPadding
    return PaddingValues(
        start = base + safe,
        end = base + safe,
        top = defaultVertical.calculateTopPadding(),
        bottom = defaultVertical.calculateBottomPadding(),
    )
}

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingContent: @Composable (() -> Unit)? = null,
) {
    if (useNativeButton(enabled, leadingContent)) {
        NativeGlassButton(text, onClick, modifier, leadingIcon, fillWidth = true)
        return
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(ResR.dimen.dimen_global_btn_height_min))
            .enabledControlOutline(enabled),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = appButtonContentPadding(),
    ) {
        AppButtonContent(text, leadingContent, leadingIcon)
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingContent: @Composable (() -> Unit)? = null,
) {
    if (useNativeButton(enabled, leadingContent)) {
        NativeGlassButton(text, onClick, modifier, leadingIcon, fillWidth = true)
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(ResR.dimen.dimen_global_btn_height_min))
            .enabledControlOutline(enabled),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = outlinedBorderOrNull(enabled),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = appButtonContentPadding(),
    ) {
        AppButtonContent(text, leadingContent, leadingIcon)
    }
}

/**
 * 内容自适应宽度的描边按钮（用于 FlowRow 中的“导入TXT/导入压缩包”等入口）。
 * 与 [AppSecondaryButton] 同样自动适配主题描边，仅宽度策略不同。
 */
@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingContent: @Composable (() -> Unit)? = null,
) {
    if (useNativeButton(enabled, leadingContent)) {
        NativeGlassButton(text, onClick, modifier, leadingIcon, fillWidth = false)
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.enabledControlOutline(enabled),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = outlinedBorderOrNull(enabled),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = appButtonContentPadding(),
    ) {
        AppButtonContent(text, leadingContent, leadingIcon)
    }
}

/**
 * 主题描边包装：**仅在按钮可用时**绘制。
 *
 * 禁用按钮若仍套上主题描边，会被框成一个视觉上「可点」的控件（用户反馈：未连接打印机时
 * 右侧「打印」按钮仍被厚重描边包围）；且禁用态本就该弱化，不该再加高对比描边。
 */
@Composable
private fun Modifier.enabledControlOutline(enabled: Boolean): Modifier =
    if (enabled) controlOutline(MaterialTheme.shapes.medium) else this

/**
 * 描边按钮的边框策略：
 * - 主题描边开启**且按钮可用** → 主题描边统一绘制，去掉 M3 原生 1dp 边框，避免双重边框偏移；
 * - 其余情况（主题描边关闭 / 按钮禁用）→ 保留 M3 默认浅色边框，保证仍有可见轮廓。
 */
@Composable
private fun outlinedBorderOrNull(enabled: Boolean): BorderStroke? {
    val outline = LocalControlOutline.current
    return if (outline.enabled && enabled) {
        null
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun AppButtonsPreview() {
    Column {
        AppPrimaryButton(text = "主按钮", onClick = {})
        AppSecondaryButton(text = "次按钮", onClick = {})
        AppOutlinedButton(text = "描边按钮", onClick = {})
    }
}
