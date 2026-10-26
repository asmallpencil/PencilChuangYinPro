package com.pencil.chuangyin.core.theme

import android.content.Context
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 根据边角类型 + 强度 + 基础尺寸构建单个 [Shape]。
 * 纯函数，Feature 层预览块与 [DimenShapeResolver] 共用，保证预览与全局主题一致。
 *
 * 强度灵敏度为基础档位的 [SHAPE_STRENGTH_GAIN] 倍：滑块位移产生 4 倍形变，
 * 满档圆角/切角由 Shape 实现自动 clamp 到组件短边的一半（不会畸形）。
 *
 * @param base 该档位的基础边距（圆角半径/切角的满档尺寸）。
 */
fun cornerShape(style: CornerStyle, strength: Float, base: Dp): Shape {
    val s = strength.coerceIn(0f, 1f) * SHAPE_STRENGTH_GAIN
    return when (style) {
        CornerStyle.Rounded -> RoundedCornerShape(base * s)
        CornerStyle.Square -> RoundedCornerShape(0.dp)
        CornerStyle.Diamond -> CutCornerShape(base * s)
        CornerStyle.Parallelogram -> CutCornerShape(
            topStart = base * s,
            topEnd = 0.dp,
            bottomEnd = base * s,
            bottomStart = 0.dp,
        )
    }
}

/**
 * 供 M3 [Shapes] 使用的 CornerBasedShape 版本，与 [cornerShape] 同构。
 */
private fun themeCornerShape(
    style: CornerStyle,
    strength: Float,
    base: Dp,
): androidx.compose.foundation.shape.CornerBasedShape =
    cornerShape(style, strength, base) as androidx.compose.foundation.shape.CornerBasedShape

/** 形状强度增益：用户滑块 0..1 映射为基础尺寸的 0..4 倍（灵敏度提升至原先的 4 倍）。 */
private const val SHAPE_STRENGTH_GAIN = 4f

/**
 * 根据边角偏好 + dimens 基础边距构建 M3 [Shapes]。
 * 尺寸一律来自 dimens.xml（零硬编码），实现集中在 Theme 层；
 * small/medium/large 三档基础边距按比例缩放。
 */
@Singleton
class DimenShapeResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : ShapeResolver {

    override fun shapes(style: CornerStyle, strength: Float): Shapes {
        val density = context.resources.displayMetrics.density
        val basePx = context.resources.getDimension(
            com.pencil.chuangyin.resources.R.dimen.dimen_shape_corner_m,
        )
        val base = (basePx / density).dp
        return Shapes(
            extraSmall = themeCornerShape(style, strength, base * 0.5f),
            small = themeCornerShape(style, strength, base * 0.75f),
            medium = themeCornerShape(style, strength, base),
            large = themeCornerShape(style, strength, base * 1.5f),
            extraLarge = themeCornerShape(style, strength, base * 2f),
        )
    }
}
