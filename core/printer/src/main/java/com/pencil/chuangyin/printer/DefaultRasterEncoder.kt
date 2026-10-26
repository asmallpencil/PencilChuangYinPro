package com.pencil.chuangyin.printer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 默认 [[RasterEncoder]] 实现：基于逆向 com.beeprt.sdk 的位图转光栅算法重写。
 * 规则：行优先，每字节 8 像素 MSB-first，黑色=1，透明→白，灰度 < 阈值为黑。
 */
@Singleton
class DefaultRasterEncoder @Inject constructor(
    private val printerSettings: PrinterSettings,
) : RasterEncoder {

    override fun encode(bitmap: Bitmap, mode: Int, threshold: Int): ByteArray {
        requireNotNull(bitmap) { "bitmap 不能为 null" }
        val safeMode = mode.coerceIn(0, 3)
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8

        val header = PrinterCommand.printRasterHeader(safeMode, widthBytes, height)
        val pixelData = encodePixels(bitmap, width, height, widthBytes, threshold)

        return ByteArray(header.size + pixelData.size).also { out ->
            header.copyInto(out, 0)
            pixelData.copyInto(out, header.size)
        }
    }

    override fun prepareForPrint(src: Bitmap, threshold: Int): Bitmap {
        var bmp = src
        // 仅按打印宽度等比缩放；纵向不压缩（长内容由打印控制器分条带下发）。
        // 注意：不做自动旋转——文本位图固定 384 点宽，短文本高<宽，
        // 自动旋转后再放大宽度会把一行字拉伸成超长纸（图片方向旋转留作后期显式选项）。
        bmp = scaleToPrintWidth(bmp, printerSettings.printWidthPx())
        // 确保 ARGB_8888
        if (bmp.config != Bitmap.Config.ARGB_8888) {
            val converted = bmp.copy(Bitmap.Config.ARGB_8888, false)
            if (bmp != src) bmp.recycle()
            bmp = converted
        }
        val result = binarize(bmp, threshold)
        if (bmp != src) bmp.recycle()
        return result
    }

    private fun encodePixels(bitmap: Bitmap, width: Int, height: Int, widthBytes: Int, threshold: Int): ByteArray {
        val pixelData = ByteArray(widthBytes * height)
        val pixels = IntArray(width)
        var index = 0
        for (y in 0 until height) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            for (xByte in 0 until widthBytes) {
                var bits = 0
                for (bit in 0 until 8) {
                    val x = bit + xByte * 8
                    if (x >= width) continue
                    val p = pixels[x]
                    val alpha = Color.alpha(p)
                    val gray = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
                    if (alpha != 0 && gray < threshold) {
                        bits = bits or (128 shr bit) // MSB first
                    }
                }
                pixelData[index++] = bits.toByte()
            }
        }
        return pixelData
    }

    private fun binarize(src: Bitmap, threshold: Int): Bitmap {
        val width = src.width
        val height = src.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val p = pixels[i]
            if (Color.alpha(p) == 0) {
                pixels[i] = Color.WHITE
                continue
            }
            val gray = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
            pixels[i] = if (gray < threshold) Color.BLACK else Color.WHITE
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun scaleToPrintWidth(src: Bitmap, targetWidth: Int): Bitmap =
        if (src.width == targetWidth) {
            src
        } else {
            scaleBitmap(src, targetWidth.toFloat() / src.width)
        }

    /**
     * 高质量缩放：一步用 Matrix（双线性）。大比例缩小（如 2400→384，6 倍+）双线性
     * 只采样 4 邻域会丢行丢列（打印成片发糊/锯齿），故先反复 1/2 降采样（每步聚合
     * 2×2 邻域，近似盒式滤波）到 2 倍以内，再一步 Matrix 缩到位。
     */
    private fun scaleBitmap(src: Bitmap, scale: Float): Bitmap {
        var current = src
        var owned = false
        var curScale = scale
        while (curScale <= 0.5f) {
            val half = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (owned && half != current) current.recycle()
            current = half
            owned = true
            curScale *= 2f
        }
        return if (curScale == 1f && owned) {
            current
        } else {
            val matrix = Matrix().apply { postScale(curScale, curScale) }
            val out = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
            if (owned && out != current) current.recycle()
            out
        }
    }
}