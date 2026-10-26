package com.pencil.chuangyin.printer

import android.graphics.Bitmap

/**
 * 图片二值化（灰度 → 黑白）处理器契约。
 *
 * 输出始终为 ARGB_8888、纯白底纯黑像素的位图，可直接进入光栅编码；
 * 预览与真实打印共用本接口的同一实现，保证所见即所得。
 *
 * 预留扩展点：新增抖动/自适应算法时只需扩展 [BinarizeAlgorithm] 与实现，
 * 所有调用方（图片打印、后期标签/贴纸/截图等）自动获得。
 */
interface ImageBinarizer {

    /** 按 [options] 指定算法把 [src] 转换为黑白位图；不修改也不回收 [src]。 */
    fun process(src: Bitmap, options: ImageProcessOptions): Bitmap
}
