package com.pencil.chuangyin.feature.camera.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.roundToInt

/**
 * 拍照 AI 处理工具链的本地执行体（纯像素运算，无网络、无状态）。
 *
 * 设计约定：
 * - **任何函数都不回收入参 [Bitmap]**，输出一定是新位图（调用方负责回收旧图）；
 * - 所有参数在入口处收敛到合法区间，越界值不会破坏色彩矩阵；
 * - 锐化对超大图自动跳过（像素循环是 CPU 密集操作，宁可少一步也不让处理卡住）。
 */
internal object ImageOps {

    /** 允许做锐化的最大像素数（约 2000×1500 的一半），超过则跳过锐化只做调色。 */
    private const val MAX_SHARPEN_PIXELS = 3_200_000L

    private const val SATURATION_GAIN = 0.9f
    private const val CONTRAST_GAIN = 0.85f
    private const val BRIGHTNESS_GAIN = 48f
    private const val MAX_SHARPEN_AMOUNT = 0.45f
    private const val SHARPEN_PIXEL_SCALE = 0.9f

    /** 最长边缩放到 [maxEdge]（只缩不放）；已足够小时返回一份不可变副本。 */
    fun fitMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (maxEdge <= 0 || longest <= maxEdge) return src.copy(Bitmap.Config.ARGB_8888, false)
        val scale = maxEdge.toFloat() / longest
        val width = (src.width * scale).roundToInt().coerceAtLeast(1)
        val height = (src.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, width, height, true)
    }

    /**
     * 调色：亮度（-1~1 偏移）、对比度（-1~1，绕中灰缩放）、饱和度（-1~1）。
     * 用 [ColorMatrix] 一次性交给 Skia，避免逐像素循环。
     */
    fun tune(
        src: Bitmap,
        brightness: Float,
        contrast: Float,
        saturation: Float,
    ): Bitmap {
        val matrix = ColorMatrix()
        matrix.setSaturation((1f + saturation.coerceIn(-1f, 1f) * SATURATION_GAIN).coerceAtLeast(0f))
        val scale = (1f + contrast.coerceIn(-1f, 1f) * CONTRAST_GAIN).coerceAtLeast(0.2f)
        val offset = 128f * (1f - scale) + brightness.coerceIn(-1f, 1f) * BRIGHTNESS_GAIN
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, offset,
                    0f, scale, 0f, 0f, offset,
                    0f, 0f, scale, 0f, offset,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        return applyMatrix(src, matrix)
    }

    /**
     * 文稿美白：灰度化 → 抬白场/压黑场（levels）→ 中间调提亮，得到"白底黑字"干净纸面。
     *
     * [strength] 来自用户设置（0~1）；强度越高白场越低、中间调越亮，文字仍保留抗锯齿边缘。
     */
    fun whiten(src: Bitmap, strength: Float): Bitmap {
        val level = strength.coerceIn(0f, 1f)
        if (level <= 0f) return src.copy(Bitmap.Config.ARGB_8888, false)
        val whitePoint = 236f - 46f * level
        val blackPoint = 6f + 8f * level
        val gamma = 1f / (1f + 0.55f * level)
        val range = (whitePoint - blackPoint).coerceAtLeast(1f)

        // 灰度滤镜先把三通道拉平，再按 LUT 映射，避免逐像素做三次浮点除法。
        val gray = applyMatrix(src, ColorMatrix().apply { setSaturation(0f) })
        val width = gray.width
        val height = gray.height
        if (width <= 0 || height <= 0) return gray
        val pixels = IntArray(width * height)
        gray.getPixels(pixels, 0, width, 0, 0, width, height)
        val lut = IntArray(256) { value ->
            val normalized = ((value - blackPoint) / range).coerceIn(0f, 1f)
            (Math.pow(normalized.toDouble(), gamma.toDouble()) * 255.0).roundToInt().coerceIn(0, 255)
        }
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val grayValue = lut[((pixel ushr 16) and 0xFF)]
            pixels[index] = (0xFF shl 24) or (grayValue shl 16) or (grayValue shl 8) or grayValue
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        if (gray !== out) runCatching { gray.recycle() }
        return out
    }

    /**
     * 智能混合增强：调色 + 适度锐化，让实景照片在热敏纸上仍有层次与细节。
     *
     * [strength] 控制整体力度；三个色调参数由 AI 决策（-1~1），最终按 [strength] 缩放。
     */
    fun mixedEnhance(
        src: Bitmap,
        strength: Float,
        brightness: Float,
        contrast: Float,
        saturation: Float,
    ): Bitmap {
        val level = strength.coerceIn(0f, 1f)
        val base = if (level <= 0f) {
            src.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            tune(
                src = src,
                brightness = brightness.coerceIn(-1f, 1f) * level,
                contrast = (0.12f + contrast.coerceIn(-1f, 1f) * 0.88f) * level,
                saturation = saturation.coerceIn(-1f, 1f) * level,
            )
        }
        val sharpenAmount = (level * MAX_SHARPEN_AMOUNT).coerceIn(0f, MAX_SHARPEN_AMOUNT)
        if (sharpenAmount <= 0f) return base
        val sharpened = sharpen(base, sharpenAmount)
        if (sharpened !== base) runCatching { base.recycle() }
        return sharpened
    }

    /**
     * 3×3 反锐化掩模（非锐化卷积）：中心 1+4k、四邻 -k。
     * 超过 [MAX_SHARPEN_PIXELS] 时返回副本（跳过锐化），避免主线程外也长时间占用 CPU。
     */
    fun sharpen(src: Bitmap, amount: Float): Bitmap {
        val width = src.width
        val height = src.height
        if (width < 3 || height < 3 || amount <= 0f ||
            width.toLong() * height > MAX_SHARPEN_PIXELS
        ) {
            return src.copy(Bitmap.Config.ARGB_8888, false)
        }
        val input = IntArray(width * height)
        src.getPixels(input, 0, width, 0, 0, width, height)
        val output = input.copyOf()
        val k = (amount * SHARPEN_PIXEL_SCALE).coerceIn(0f, MAX_SHARPEN_AMOUNT)
        val centerWeight = 1f + 4f * k
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val index = row + x
                val center = input[index]
                val left = input[index - 1]
                val right = input[index + 1]
                val up = input[index - width]
                val down = input[index + width]
                val red = sharpenChannel(center, left, right, up, down, 16, centerWeight, k)
                val green = sharpenChannel(center, left, right, up, down, 8, centerWeight, k)
                val blue = sharpenChannel(center, left, right, up, down, 0, centerWeight, k)
                output[index] = (center and 0xFF000000.toInt()) or (red shl 16) or (green shl 8) or blue
            }
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun sharpenChannel(
        center: Int,
        left: Int,
        right: Int,
        up: Int,
        down: Int,
        shift: Int,
        centerWeight: Float,
        k: Float,
    ): Int {
        val c = (center shr shift) and 0xFF
        val l = (left shr shift) and 0xFF
        val r = (right shr shift) and 0xFF
        val u = (up shr shift) and 0xFF
        val d = (down shr shift) and 0xFF
        return (c * centerWeight - k * (l + r + u + d)).roundToInt().coerceIn(0, 255)
    }

    /** 用色彩矩阵绘制到新位图（不改入参）。 */
    private fun applyMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
