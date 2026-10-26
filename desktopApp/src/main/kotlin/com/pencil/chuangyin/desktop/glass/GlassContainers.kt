package com.pencil.chuangyin.desktop.glass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.theme.LocalLiquidGlassControlsEnabled

/**
 * 卡片容器：玻璃生效时用玻璃面料，关闭时退化为 surfaceContainerLow 纯色卡片。
 * 结构对齐安卓端的 Card(nativeGlassSurface + glassAwareOutline) 组合。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    elevation: Float = 3f,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassEnabled = LocalLiquidGlassControlsEnabled.current
    Column(
        modifier = modifier
            .glassSurface(
                shape = shape,
                enabled = glassEnabled,
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                elevation = if (glassEnabled) elevation else 0f,
            )
            .glassAwareOutline(shape, glassEnabled)
            .padding(contentPadding),
        content = content,
    )
}

/** 无内边距玻璃面板（用于自绘内容区，如网格容器、详情页头图）。 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    elevation: Float = 2f,
    fallbackColor: androidx.compose.ui.graphics.Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassEnabled = LocalLiquidGlassControlsEnabled.current
    Column(
        modifier = modifier
            .glassSurface(
                shape = shape,
                enabled = glassEnabled,
                fallbackColor = fallbackColor ?: MaterialTheme.colorScheme.surfaceContainerLow,
                elevation = if (glassEnabled) elevation else 0f,
            )
            .glassAwareOutline(shape, glassEnabled),
        content = content,
    )
}

/** 小药丸标签（用于标签、计数等）。 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        elevation = 0f,
        content = content,
    )
}
