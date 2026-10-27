package com.pencil.chuangyin.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview

/**
 * 语义化文本：将 Awesome UI 的 CustomText 重写为纯 M3 + 零硬编码版本。
 * 所有颜色取自 [MaterialTheme.colorScheme]，样式来自主题 Typography。
 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        textAlign = align,
        maxLines = maxLines,
        minLines = minLines,
        overflow = overflow,
    )
}

@Preview(showBackground = true)
@Composable
private fun AppTextPreview() {
    AppText(text = "预览文本", style = MaterialTheme.typography.headlineSmall)
}