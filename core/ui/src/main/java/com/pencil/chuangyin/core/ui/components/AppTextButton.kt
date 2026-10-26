package com.pencil.chuangyin.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.pencil.chuangyin.core.theme.LiquidGlassButtonVariant
import com.pencil.chuangyin.core.theme.LiquidGlassWidgetKind
import com.pencil.chuangyin.core.theme.LocalLiquidGlassWidgetHost
import com.pencil.chuangyin.core.theme.controlOutline

/**
 * 全局文字按钮：默认 M3 [TextButton]，液态玻璃开启时改用 **C++ 原生玻璃**文字按钮。
 *
 * 原生文字按钮不绘制玻璃面（仅文字与按压反馈），因此 Composable 仍绘制文字（保留
 * 无障碍与精确排版）；原生层只在按压时提供缩放反馈。
 */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val host = LocalLiquidGlassWidgetHost.current
    if (enabled && rememberLiquidGlassActive() && host != null) {
        // 原生层不画玻璃面与文字（[LiquidGlassButtonVariant.Text]），故此处照常绘制文字，
        // 仅用原生通道承载按压反馈。
        Box(
            modifier = modifier.nativeGlassWidget(
                kind = LiquidGlassWidgetKind.Button,
                key = remember { Any() },
                label = listOf(text),
                state = emptyList(),
                textSizeSp = MaterialTheme.typography.labelLarge.fontSize.value,
                variant = LiquidGlassButtonVariant.Text,
            ) { onClick() },
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                modifier = Modifier
                    .alpha(0f)
                    .padding(AppTextButtonDefaults.ContentPadding),
            )
        }
        return
    }
    TextButton(
        onClick = onClick,
        modifier = modifier.controlOutline(MaterialTheme.shapes.small),
        enabled = enabled,
        contentPadding = AppTextButtonDefaults.ContentPadding,
    ) {
        Text(text, color = contentColor, style = MaterialTheme.typography.labelLarge)
    }
}

/** 文字按钮尺寸常量，与 M3 [TextButton] 默认内边距对齐。 */
private object AppTextButtonDefaults {
    val ContentPadding = PaddingValues(horizontal = androidx.compose.ui.unit.Dp(12f), vertical = androidx.compose.ui.unit.Dp(8f))
}

@Preview(showBackground = true)
@Composable
private fun AppTextButtonPreview() {
    AppTextButton(text = "文字按钮", onClick = {})
}
