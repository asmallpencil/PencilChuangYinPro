package com.pencil.chuangyin.core.barcode

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 条码生成：把文本编码为可直接打印/保存的位图。
 *
 * 打印头宽度由调用方传入（通常为 [com.pencil.chuangyin.printer.PrinterSettings.printWidthPx]），
 * 一维码使用固定高度保证 384 点宽下仍可被常见扫描器识别；二维矩阵码默认正方形。
 */
object BarcodeGenerator {

    /** 默认生成宽度（58mm 热敏纸打印头点数）。 */
    const val DEFAULT_WIDTH_PX = 384

    /** 一维码默认高度：在 384 宽下兼顾可扫性与纸张占用。 */
    const val DEFAULT_1D_HEIGHT_PX = 140

    /** 默认静区边距。 */
    const val DEFAULT_MARGIN = 2

    /**
     * 生成条码位图。
     *
     * @param content 待编码文本（不做裁剪，条码内容中的空格可能具有语义）。
     * @param option 条码格式。
     * @param widthPx 目标宽度，默认 [DEFAULT_WIDTH_PX]。
     * @param heightPx 目标高度；0 表示按格式取默认（一维 140px、二维同宽）。
     * @param margin 静区边距（模块数）。
     * @return 生成失败（内容不符合该格式、参数非法等）时返回 null，不抛异常。
     */
    fun encode(
        content: String,
        option: BarcodeFormatOption,
        widthPx: Int = DEFAULT_WIDTH_PX,
        heightPx: Int = 0,
        margin: Int = DEFAULT_MARGIN,
    ): Bitmap? {
        if (content.isEmpty()) return null
        val width = widthPx.coerceIn(MIN_EDGE, MAX_EDGE)
        val height = if (heightPx > 0) {
            heightPx.coerceIn(MIN_EDGE, MAX_EDGE)
        } else if (option.isTwoDimensional) {
            width
        } else {
            DEFAULT_1D_HEIGHT_PX
        }
        return runCatching {
            val hints = HashMap<EncodeHintType, Any>().apply {
                put(EncodeHintType.CHARACTER_SET, Charsets.UTF_8.name())
                put(EncodeHintType.MARGIN, margin.coerceAtLeast(0))
                // 二维码的错误纠正级别：QR 用 M（约 15%），Aztec 用 33%，PDF417 用等级 2。
                when (option) {
                    BarcodeFormatOption.QR_CODE -> put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                    BarcodeFormatOption.AZTEC -> put(EncodeHintType.ERROR_CORRECTION, 33)
                    BarcodeFormatOption.PDF_417 -> put(EncodeHintType.ERROR_CORRECTION, 2)
                    else -> Unit
                }
            }
            val matrix = MultiFormatWriter().encode(content, option.zxingFormat, width, height, hints)
            matrixToBitmap(matrix.width, matrix.height) { x, y -> matrix.get(x, y) }
        }.getOrNull()
    }

    /** BitMatrix → 白底黑像素位图。 */
    private inline fun matrixToBitmap(width: Int, height: Int, isBlack: (Int, Int) -> Boolean): Bitmap {
        val pixels = IntArray(width * height)
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[index++] = if (isBlack(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private const val MIN_EDGE = 32
    private const val MAX_EDGE = 4096
}
