package com.pencil.chuangyin.desktop.printer

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

/**
 * 位图 → `GS v 0` 光栅数据编码器。
 *
 * 与安卓端 `PrinterController.printBands` 的打包规则完全一致：
 *  - 每行按 8 点一个字节，MSB 在左（第 0 点在高位）；
 *  - 透明像素按白处理；
 *  - 灰度 `0.299R + 0.587G + 0.114B < threshold` 判为黑点（置 1）。
 */
object RasterEncoder {

    /** 把整页高度切成若干条带（每带至多 [bandHeight] 行）。 */
    fun bandRanges(
        height: Int,
        bandHeight: Int = PrinterProtocol.RASTER_BAND_HEIGHT,
    ): List<IntRange> {
        if (height <= 0) return emptyList()
        val safeBand = bandHeight.coerceAtLeast(1)
        val result = ArrayList<IntRange>((height + safeBand - 1) / safeBand)
        var top = 0
        while (top < height) {
            val bottom = minOf(top + safeBand, height) - 1
            result += top..bottom
            top = bottom + 1
        }
        return result
    }

    /**
     * 编码一页中的一段（`top` 起始行，共 `height` 行）。
     *
     * 返回值已包含 `GS v 0` 指令头，可直接写入传输层。
     */
    fun encodeBand(
        image: BufferedImage,
        top: Int,
        height: Int,
        threshold: Int = PrinterProtocol.DEFAULT_THRESHOLD,
    ): ByteArray {
        val width = image.width
        if (width <= 0 || height <= 0) return ByteArray(0)
        val widthBytes = (width + 7) / 8
        val out = ByteArrayOutputStream(widthBytes * height + 8)
        out.write(PrinterProtocol.rasterHeader(widthBytes, height))

        val rowPixels = IntArray(width)
        val rowBytes = ByteArray(widthBytes)
        for (y in 0 until height) {
            val srcY = top + y
            java.util.Arrays.fill(rowBytes, 0)
            if (srcY in 0 until image.height) {
                image.getRGB(0, srcY, width, 1, rowPixels, 0, width)
                for (x in 0 until width) {
                    if (!isBlack(rowPixels[x], threshold)) continue
                    val byteIndex = x shr 3
                    val bit = 0x80 shr (x and 7)
                    rowBytes[byteIndex] = (rowBytes[byteIndex].toInt() or bit).toByte()
                }
            }
            out.write(rowBytes, 0, widthBytes)
        }
        return out.toByteArray()
    }

    private fun isBlack(argb: Int, threshold: Int): Boolean {
        val alpha = (argb ushr 24) and 0xFF
        if (alpha == 0) return false
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        return gray < threshold
    }
}
