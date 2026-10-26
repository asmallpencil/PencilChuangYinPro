package com.pencil.chuangyin.core.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

/**
 * 图标统一入口：既支持 M3 矢量图标 [ImageVector]，也支持 drawable 位图资源
 * （570 图标包可直接放入 core:resources 的 drawable 并通过 [Modifier.drawableRes] 绑定引入）。
 * [contentDescription] 必须来自 stringResource，保证无障碍与零硬编码。
 */
@Composable
fun AppIcon(
    modifier: Modifier = Modifier,
    imageVector: ImageVector? = null,
    drawableRes: Int? = null,
    contentDescription: String?,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    when {
        imageVector != null -> Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
        drawableRes != null -> Icon(
            painter = painterResource(drawableRes),
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
    }
}

/** 便捷重载：contentDescription 直接传字符串资源 id。 */
@Composable
fun AppIconRes(
    modifier: Modifier = Modifier,
    imageVector: ImageVector? = null,
    drawableRes: Int? = null,
    contentDescriptionRes: Int? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val desc = contentDescriptionRes?.let { stringResource(it) }
    AppIcon(modifier, imageVector, drawableRes, desc, tint)
}