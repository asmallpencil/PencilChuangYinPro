package com.pencil.chuangyin.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.pencil.chuangyin.core.theme.LiquidGlassButtonVariant
import com.pencil.chuangyin.core.theme.LiquidGlassWidgetKind
import com.pencil.chuangyin.core.theme.LocalLiquidGlassWidgetHost
import com.pencil.chuangyin.resources.R as ResR

/**
 * 全局图标按钮：默认 M3 [IconButton]，液态玻璃开启时改用 **C++ 原生玻璃**圆形图标按钮。
 *
 * 原生路径要求：
 * - 图标为 [ImageVector]（Compose 离屏渲染为白色字形位图 → GL 纹理 → 原生按文字色着色）；
 * - [tint] 为 null（使用主题默认前景色）。指定自定义 [tint] 时原生层无法逐控件着色，自动回退 M3。
 *
 * @param contentDescription 无障碍描述（须来自 stringResource）。
 * @param tint 自定义图标颜色；null 表示沿用主题默认（原生路径仅在此情形生效）。
 * @param size 按钮边长（默认全局触摸目标 48dp，与 M3 IconButton 一致）。
 */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    size: Dp = dimensionResource(ResR.dimen.dimen_global_touch_target),
) {
    val host = LocalLiquidGlassWidgetHost.current
    if (enabled && tint == null && rememberLiquidGlassActive() && host != null) {
        // 方形占位框经原生 capsule 圆角即为圆形；图标由位图纹理绘制、原生按文字色着色。
        Box(
            modifier = modifier
                .size(size)
                .nativeGlassWidget(
                    kind = LiquidGlassWidgetKind.Button,
                    key = remember { Any() },
                    label = emptyList(),
                    state = emptyList(),
                    variant = LiquidGlassButtonVariant.Icon,
                    icon = rememberNativeGlassIcon(imageVector),
                ) { onClick() },
        )
        return
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
    ) {
        AppIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint ?: LocalContentColor.current,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppIconButtonPreview() {
    AppIconButton(onClick = {}, imageVector = Icons.Filled.Close, contentDescription = null)
}
