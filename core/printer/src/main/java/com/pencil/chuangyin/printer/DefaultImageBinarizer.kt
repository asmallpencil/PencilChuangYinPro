package com.pencil.chuangyin.printer

import android.graphics.Bitmap
import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ImageBinarizer] 默认实现：所有算法先转灰度，再交给 [DitherAlgorithms] 的策略插件处理，
 * 最后统一输出纯白底（0xFFFFFFFF）纯黑点（0xFF000000）的 ARGB 位图。
 *
 * 算法本身不在此处实现：新增/调整算法只需扩展 [BinarizeAlgorithm] 与
 * [DitherAlgorithms]，本类保持稳定。
 *
 * 性能约束：输入应先经 [ImagePreparer] 降采样到打印工作分辨率（约 384~1200 px 宽），
 * 本类只做逐像素/积分图计算，时间与内存均为 O(宽×高)。
 */
@Singleton
class DefaultImageBinarizer @Inject constructor() : ImageBinarizer {

    override fun process(src: Bitmap, options: ImageProcessOptions): Bitmap {
        val width = src.width
        val height = src.height
        val gray = toGray(src, width, height)
        val black = DitherAlgorithms
            .strategyFor(options.algorithm)
            .process(gray, width, height, options.threshold)
        return writeResult(black, width, height)
    }

    /** 读取像素并转为 0..255 灰度（同时处理透明像素为白底）。 */
    private fun toGray(src: Bitmap, width: Int, height: Int): FloatArray {
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        return FloatArray(width * height) { i ->
            val p = pixels[i]
            if (Color.alpha(p) == 0) {
                255f
            } else {
                (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p))
            }
        }
    }

    private fun writeResult(black: BooleanArray, width: Int, height: Int): Bitmap {
        val out = IntArray(width * height) { i -> if (black[i]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }
}
