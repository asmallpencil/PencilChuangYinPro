package com.pencil.chuangyin.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.pencil.chuangyin.core.theme.LocalControlOutline
import com.pencil.chuangyin.core.theme.controlOutline
import com.pencil.chuangyin.core.theme.cornerSafeInset
import com.pencil.chuangyin.resources.R as ResR

/**
 * Awesome UI CustomEditText 的常规输入框零硬编码重写。
 * 值/变更经参数暴露，配色来自 colorScheme，圆角来自主题 Shapes。
 *
 * 边框/形状在外层 Box 统一绘制：主题描边开启时由 [controlOutline] 接管；
 * 关闭时按 M3 规范画 1dp 细边（错误态用 error 色）。输入框本体额外内缩
 * [cornerSafeInset]——M3 1.3 的 OutlinedTextField 不支持 contentPadding 参数，
 * 高档菱形/切角下默认 16dp 内边距不足以避开斜切边，靠边文字会被切掉。
 *
 * 液态玻璃：输入框底色由 **Compose 自绘的伪液态玻璃层**（[pseudoGlassSurface]）绘制。
 * 输入框常随页面/列表一起滚动，若走真玻璃（背景采样）会因纹理滞后一帧而在滑动中抖动，
 * 故此处固定使用伪玻璃；输入框本体保持透明以透出底色。
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
) {
    val shape = MaterialTheme.shapes.medium
    val outline = LocalControlOutline.current
    // 形状安全内边距：让输入文字/标签整体向内避开斜切边缘。
    val safeInset = cornerSafeInset(shapeScale = 1f)
    // M3 边框统一透明：边框由外层 Box 按全宽形状绘制，避免内缩后边框与形状脱节。
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!outline.enabled) {
                    Modifier.border(
                        width = dimensionResource(ResR.dimen.dimen_global_border),
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .pseudoGlassSurface(shape = shape)
            .controlOutline(shape),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = safeInset),
            enabled = enabled,
            label = label?.let { { AppText(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            placeholder = placeholder?.let { { AppText(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            singleLine = singleLine,
            shape = shape,
            colors = colors,
            isError = isError,
            supportingText = if (isError && errorMessage != null) {
                { AppText(errorMessage, color = MaterialTheme.colorScheme.error) }
            } else {
                null
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppTextFieldPreview() {
    AppTextField(value = "内容", onValueChange = {}, label = "标签")
}
