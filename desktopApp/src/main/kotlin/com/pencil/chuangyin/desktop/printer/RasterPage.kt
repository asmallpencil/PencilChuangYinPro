package com.pencil.chuangyin.desktop.printer

import java.awt.image.BufferedImage

/**
 * 一页已编码好的光栅数据（按 255 行切成若干条带）。
 *
 * 为什么不直接持有 [BufferedImage]：一整篇长文可能排到上百页，384x768 的 ARGB 位图每页约 1.2MB，
 * 全部驻留会撑爆 JVM 堆。这里在拿到位图的当下就编码成字节（每页约 36KB），随即释放位图，
 * 内存占用降低一个数量级，且打印时无需再做灰度计算。
 */
class RasterPage(val bands: List<ByteArray>) {

    val isEmpty: Boolean get() = bands.isEmpty()

    companion object {
        /** 由位图编码成一页（位图宽度即打印宽度，通常为 384）。 */
        fun from(image: BufferedImage, threshold: Int = PrinterProtocol.DEFAULT_THRESHOLD): RasterPage {
            val ranges = RasterEncoder.bandRanges(image.height)
            return RasterPage(
                ranges.map { range ->
                    RasterEncoder.encodeBand(
                        image = image,
                        top = range.first,
                        height = range.last - range.first + 1,
                        threshold = threshold,
                    )
                },
            )
        }
    }
}
