package com.pencil.chuangyin.feature.barcode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.pencil.chuangyin.core.ui.editor.PageLayerTransform
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 把「白纸页 + 条码图层」的编辑结果合成为最终打印位图。
 *
 * 与图片打印页的 ImagePageComposer 同一套几何语义：
 * 页宽跟随打印头（默认 384 点），图层默认按页宽等比铺满，平移量是相对页宽/页高的倍数；
 * 超出页宽的部分被裁切，向下平移会延长纸张。
 */
object BarcodePageComposer {

    /** 默认打印头宽度（与 core:printer 一致）。 */
    const val DEFAULT_PAGE_WIDTH_PX = 384

    /**
     * 合成一页。
     *
     * @param source 条码图层位图。
     * @param transform 图层变换。
     * @param pageWidthPx 页宽（取 PrinterSettings.printWidthPx()）。
     * @param copyEvenIfUnchanged 为 true 时即使无需变换也返回新位图
     *   （打印/保存场景必须传入，避免把预览中的同一实例交给调用方回收）。
     */
    fun compose(
        source: Bitmap,
        transform: PageLayerTransform,
        pageWidthPx: Int = DEFAULT_PAGE_WIDTH_PX,
        copyEvenIfUnchanged: Boolean = false,
    ): Bitmap {
        val pageWidth = pageWidthPx.coerceAtLeast(8)
        val adjusted = transform.layerScale != 1f ||
            transform.offsetX != 0f ||
            transform.offsetY != 0f
        if (!adjusted && source.width == pageWidth && !copyEvenIfUnchanged) return source

        val baseRatio = pageWidth.toFloat() / source.width.toFloat().coerceAtLeast(1f)
        val drawWidth = source.width * baseRatio * transform.layerScale
        val drawHeight = source.height * baseRatio * transform.layerScale
        val extraHeight = (drawHeight * transform.offsetY).coerceAtLeast(0f)
        val pageHeight = max(drawHeight + extraHeight, 1f).roundToInt()

        val page = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        val matrix = Matrix().apply {
            setScale(drawWidth / source.width, drawHeight / source.height)
            postTranslate(
                (pageWidth - drawWidth) / 2f + transform.offsetX * pageWidth,
                transform.offsetY * drawHeight,
            )
        }
        canvas.drawBitmap(source, matrix, paint)
        return page
    }
}
