package com.kyant.shapes

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class RectangleCornerRadii(
    val topStart: Dp = 0.dp,
    val topEnd: Dp = 0.dp,
    val bottomEnd: Dp = 0.dp,
    val bottomStart: Dp = 0.dp
)
