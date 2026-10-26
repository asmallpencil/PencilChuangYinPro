package com.pencil.chuangyin.core.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 把 [ImageVector] 离屏渲染为「字形 + alpha」的位图，供 C++ 原生液态玻璃层着色绘制。
 *
 * 为什么需要：原生库仅内置 11 个矢量图标，无法覆盖工程内全部 Material 图标；因此把任意
 * 图标在 Compose 侧渲染成位图，经 JNI 上传为 GL 纹理，原生层按控件文字色着色（只借用
 * 字形 alpha），主题换色无需重传位图。
 *
 * 位图规格：边长 [sizeDp] dp（按当前 density 换算像素）、ARGB_8888、字形透明底。
 * 结果按 [vector] / [sizeDp] / density 记忆，避免每帧重建。
 */
@Composable
fun rememberNativeGlassIcon(
    vector: ImageVector,
    sizeDp: Int = DEFAULT_NATIVE_GLASS_ICON_DP,
): ImageBitmap {
    val density = LocalDensity.current
    val painter = rememberVectorPainter(vector)
    return remember(vector, sizeDp, density.density) {
        renderToWhiteBitmap(sizeDp, density) { w, h ->
            with(painter) {
                draw(
                    size = Size(w, h),
                    // 以纯白替换 RGB，仅保留字形 alpha；原生层把该纹理与前景色**相乘**后绘制
                    // （texIconPainter → ImDrawList::AddImageQuad(tint)）。必须用白色，相乘才能
                    // 得到任意前景色；若用黑色，任何前景色相乘后仍为黑，亮色模式下图标恒黑。
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
        }
    }
}

/** 渲染尺寸基准（dp）：与 Material 图标标准尺寸一致。 */
private const val DEFAULT_NATIVE_GLASS_ICON_DP = 24

/** 离屏绘制到 ARGB_8888 位图（透明底），返回包裹后的 [ImageBitmap]。 */
private inline fun renderToWhiteBitmap(
    sizeDp: Int,
    density: Density,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.(w: Float, h: Float) -> Unit,
): ImageBitmap {
    val sizePx = (sizeDp * density.density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap.asImageBitmap())
    val size = Size(sizePx.toFloat(), sizePx.toFloat())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, size) {
        draw(size.width, size.height)
    }
    return bitmap.asImageBitmap()
}
