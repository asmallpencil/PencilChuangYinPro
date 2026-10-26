package com.pencil.chuangyin.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * 分隔线：颜色取 colorScheme，厚度/横向边距由参数控制，替代 Awesome UI 的 CustomBreak。
 */
@Composable
fun AppDivider(
    modifier: Modifier = Modifier,
    thicknessDp: Dp = Dp.Hairline,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    Box(modifier.fillMaxWidth().height(thicknessDp).background(color))
}