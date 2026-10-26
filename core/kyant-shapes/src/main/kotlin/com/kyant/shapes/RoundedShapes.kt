package com.kyant.shapes

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** 统一圆角的圆角矩形（对应 Kyant0/shapes 的 RoundedRectangle）。 */
data class RoundedRectangle(
    val cornerRadius: Dp = 0.dp,
    override val style: RoundedCornerStyle = RoundedCornerStyle.Circular
) : RoundedRectangularShape {

    override fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): RoundedRectangularShape.Corners {
        val radius = with(density) { cornerRadius.toPx() }
        val maxRadius = minOf(size.width, size.height) / 2f
        val r = radius.coerceAtMost(maxRadius)
        return RoundedRectangularShape.Corners(r, r, r, r)
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radius = with(density) { cornerRadius.toPx() }
            .coerceAtMost(minOf(size.width, size.height) / 2f)
        return Outline.Rounded(
            RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(radius))
        )
    }

    override fun copy(style: RoundedCornerStyle): RoundedRectangle = copy(style = style)
}

/** 直角矩形（对应 Kyant0/shapes 的 Rectangle）。 */
object Rectangle : RoundedRectangularShape {

    override fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): RoundedRectangularShape.Corners = RoundedRectangularShape.Corners(0f, 0f, 0f, 0f)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Rectangle(Rect(0f, 0f, size.width, size.height))

    override fun copy(style: RoundedCornerStyle): Rectangle = this
}

/** 四角独立圆角矩形（对应 Kyant0/shapes 的 UnevenRoundedRectangle）。 */
data class UnevenRoundedRectangle(
    val cornerRadii: RectangleCornerRadii = RectangleCornerRadii(),
    override val style: RoundedCornerStyle = RoundedCornerStyle.Circular
) : RoundedRectangularShape {

    constructor(
        topStart: Dp = 0.dp,
        topEnd: Dp = 0.dp,
        bottomEnd: Dp = 0.dp,
        bottomStart: Dp = 0.dp,
        style: RoundedCornerStyle = RoundedCornerStyle.Circular
    ) : this(RectangleCornerRadii(topStart, topEnd, bottomEnd, bottomStart), style)

    override fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): RoundedRectangularShape.Corners {
        val maxRadius = minOf(size.width, size.height) / 2f
        val isLtr = layoutDirection == LayoutDirection.Ltr
        val topStart = with(density) { cornerRadii.topStart.toPx() }
        val topEnd = with(density) { cornerRadii.topEnd.toPx() }
        val bottomEnd = with(density) { cornerRadii.bottomEnd.toPx() }
        val bottomStart = with(density) { cornerRadii.bottomStart.toPx() }
        return RoundedRectangularShape.Corners(
            (if (isLtr) topStart else topEnd).coerceAtMost(maxRadius),
            (if (isLtr) topEnd else topStart).coerceAtMost(maxRadius),
            (if (isLtr) bottomEnd else bottomStart).coerceAtMost(maxRadius),
            (if (isLtr) bottomStart else bottomEnd).coerceAtMost(maxRadius)
        )
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val maxRadius = minOf(size.width, size.height) / 2f
        val isLtr = layoutDirection == LayoutDirection.Ltr
        val topStart = with(density) { cornerRadii.topStart.toPx() }.coerceAtMost(maxRadius)
        val topEnd = with(density) { cornerRadii.topEnd.toPx() }.coerceAtMost(maxRadius)
        val bottomEnd = with(density) { cornerRadii.bottomEnd.toPx() }.coerceAtMost(maxRadius)
        val bottomStart = with(density) { cornerRadii.bottomStart.toPx() }.coerceAtMost(maxRadius)
        return Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                topLeft = CornerRadius(if (isLtr) topStart else topEnd),
                topRight = CornerRadius(if (isLtr) topEnd else topStart),
                bottomRight = CornerRadius(if (isLtr) bottomEnd else bottomStart),
                bottomLeft = CornerRadius(if (isLtr) bottomStart else bottomEnd)
            )
        )
    }

    override fun copy(style: RoundedCornerStyle): UnevenRoundedRectangle = copy(style = style)
}
