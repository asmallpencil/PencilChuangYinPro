package com.pencil.chuangyin.printer

import android.graphics.Bitmap

/**
 * 位图 → 热敏打印机光栅数据（GS v 0）编码器接口。
 * 编码细节（二值化、字节打包、MSB-first）由实现层封装，业务层只面对接口。
 */
interface RasterEncoder {
    /** 将 Bitmap 编码为完整光栅打印指令（含命令头 + 像素数据）。 */
    fun encode(bitmap: Bitmap, mode: Int = PrinterCommand.MODE_NORMAL, threshold: Int = PrinterCommand.DEFAULT_THRESHOLD): ByteArray

    /** 将 Bitmap 调整为适合 384 点打印宽度、限制最大高度并完成二值化。 */
    fun prepareForPrint(src: Bitmap, threshold: Int = PrinterCommand.DEFAULT_THRESHOLD): Bitmap
}