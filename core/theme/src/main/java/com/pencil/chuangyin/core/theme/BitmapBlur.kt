package com.pencil.chuangyin.core.theme

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 软件位图模糊：先按模糊半径等比缩小（绘制时上采样天然平滑），再做两轮可分离盒式模糊。
 *
 * 存在意义：Compose 的 `Modifier.blur` / RenderEffect 仅 Android 12(API31)+ 生效，
 * 低版本会静默降级为“完全不模糊”，导致“背景模糊程度”滑杆在旧机型上看似无效。
 * 本实现为纯 Bitmap 运算，minSdk 23 全版本一致生效；背景图先缩小到百像素级再处理，
 * IO 线程耗时通常在 10ms 内，不会造成明显卡顿。
 *
 * @param source 原始解码位图（不会被回收）。
 * @param blurRadiusDp 模糊半径（dp），0 返回原图。
 * @param density 屏幕密度，用于 dp→px 换算。
 * @return 模糊后的小尺寸位图（调用方按 ContentScale.Crop 上采样铺满）。
 */
internal fun blurBitmap(source: Bitmap, blurRadiusDp: Float, density: Float): Bitmap {
    val radiusDp = blurRadiusDp.coerceAtLeast(0f)
    if (radiusDp <= 0f) return source
    // 缩小倍率：半径越大缩得越小，上采样越柔（约 2x..12x）。
    val downscale = (1.6f + radiusDp * 0.45f)
    val targetWidth = max(2, (source.width / downscale).roundToInt())
    val ratio = source.height.toFloat() / source.width.toFloat()
    val targetHeight = max(2, (targetWidth * ratio).roundToInt())
    var working = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    val boxRadius = when {
        radiusDp * density < 10f -> 1
        radiusDp * density < 26f -> 2
        else -> 3
    }
    // 两轮分离盒式模糊，逼近高斯的观感（首轮输出回收，避免内存翻倍）。
    working = boxBlurPass(working, boxRadius).also { if (it != working) working.recycle() }
    return boxBlurPass(working, boxRadius).also { if (it != working) working.recycle() }
}

/**
 * 供 app 模块调用的公开入口：对底层整页位图做软件盒式模糊（minSdk 23 全版本生效）。
 * 用于液态玻璃导航栏在低版本（无 RenderEffect，API<31）上的真模糊兜底，
 * 避免旧实现静默降级为“不模糊”的假毛玻璃。
 */
fun applySoftwareBlur(source: Bitmap, blurRadiusDp: Float, density: Float): Bitmap =
    blurBitmap(source, blurRadiusDp, density)

/** 一轮“横向 + 纵向”盒式模糊（滑动窗口累加，O(n)）。 */
private fun boxBlurPass(source: Bitmap, radius: Int): Bitmap {
    val width = source.width
    val height = source.height
    val input = IntArray(width * height)
    source.getPixels(input, 0, width, 0, 0, width, height)
    val horizontal = IntArray(width * height)
    boxBlurHorizontal(input, horizontal, width, height, radius)
    val output = IntArray(width * height)
    boxBlurVertical(horizontal, output, width, height, radius)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(output, 0, width, 0, 0, width, height)
    }
}

private fun boxBlurHorizontal(
    input: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    val window = radius * 2 + 1
    for (row in 0 until height) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        val rowOffset = row * width
        // 初始窗口：[-radius, radius]，越界像素按边缘像素补齐。
        for (x in -radius..radius) {
            val pixel = input[rowOffset + x.coerceIn(0, width - 1)]
            a += pixel ushr 24 and 0xFF
            r += pixel shr 16 and 0xFF
            g += pixel shr 8 and 0xFF
            b += pixel and 0xFF
        }
        for (x in 0 until width) {
            output[rowOffset + x] = (a / window shl 24) or
                (r / window shl 16) or
                (g / window shl 8) or
                (b / window)
            // 滑窗：移出左像素，移入右像素（边缘 clamp）。
            val removeX = (x - radius).coerceAtLeast(0)
            val addX = (x + radius + 1).coerceAtMost(width - 1)
            val remove = input[rowOffset + removeX]
            val add = input[rowOffset + addX]
            a += (add ushr 24 and 0xFF) - (remove ushr 24 and 0xFF)
            r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
            g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

private fun boxBlurVertical(
    input: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    val window = radius * 2 + 1
    for (col in 0 until width) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (y in -radius..radius) {
            val pixel = input[y.coerceIn(0, height - 1) * width + col]
            a += pixel ushr 24 and 0xFF
            r += pixel shr 16 and 0xFF
            g += pixel shr 8 and 0xFF
            b += pixel and 0xFF
        }
        for (y in 0 until height) {
            output[y * width + col] = (a / window shl 24) or
                (r / window shl 16) or
                (g / window shl 8) or
                (b / window)
            val removeY = (y - radius).coerceAtLeast(0)
            val addY = (y + radius + 1).coerceAtMost(height - 1)
            val remove = input[removeY * width + col]
            val add = input[addY * width + col]
            a += (add ushr 24 and 0xFF) - (remove ushr 24 and 0xFF)
            r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
            g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
}
