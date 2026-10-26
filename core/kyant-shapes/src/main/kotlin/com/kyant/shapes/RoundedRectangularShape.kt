package com.kyant.shapes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 圆角矩形的通用抽象：[corners] 返回四角像素半径（左上/右上/右下/左下）。
 *
 * 原实现来自开源项目 Kyant0/shapes（Apache-2.0），此处按需内置，
 * 供液态玻璃透镜折射（Lens）读取真实圆角半径使用。
 */
interface RoundedRectangularShape : Shape {

    val style: RoundedCornerStyle
        get() = RoundedCornerStyle.Circular

    fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Corners

    fun copy(style: RoundedCornerStyle): RoundedRectangularShape

    data class Corners(
        val topLeft: Float,
        val topRight: Float,
        val bottomRight: Float,
        val bottomLeft: Float
    )
}
