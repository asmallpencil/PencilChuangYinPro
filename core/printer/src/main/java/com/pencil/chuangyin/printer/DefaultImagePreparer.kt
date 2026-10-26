package com.pencil.chuangyin.printer

import android.graphics.Bitmap
import android.graphics.Matrix
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ImagePreparer] 默认实现：
 * 1. 旋转（仅 0/90/180/270，避免非正交角度引入黑边与缩放歧义）；
 * 2. 等比缩放到打印头宽度（384 点），超宽照片在此一次性降采样，后续算法与编码都很轻；
 * 3. 交给 [ImageBinarizer] 黑白化。
 *
 * 中间位图及时回收，任意时刻至多持有 2 张工作位图，批量长图也不会内存膨胀。
 */
@Singleton
class DefaultImagePreparer @Inject constructor(
    private val binarizer: ImageBinarizer,
    private val printerSettings: PrinterSettings,
) : ImagePreparer {

    override fun prepare(src: Bitmap, options: ImageProcessOptions): Bitmap {
        var work = src
        var rotated: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            if (options.rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(options.rotationDegrees.toFloat()) }
                rotated = Bitmap.createBitmap(work, 0, 0, work.width, work.height, matrix, true)
                work = rotated
            }
            val targetWidth = printerSettings.printWidthPx()
            scaled = if (work.width != targetWidth) {
                scaleToWidth(work, targetWidth)
            } else {
                work
            }
            return binarizer.process(scaled, options)
        } finally {
            if (rotated != null && rotated != src) rotated.recycle()
            if (scaled != null && scaled != work && scaled != src) scaled.recycle()
        }
    }

    private fun scaleToWidth(src: Bitmap, targetWidth: Int): Bitmap {
        if (src.width == targetWidth) return src
        val ratio = targetWidth.toFloat() / src.width
        val targetHeight = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)
    }
}
