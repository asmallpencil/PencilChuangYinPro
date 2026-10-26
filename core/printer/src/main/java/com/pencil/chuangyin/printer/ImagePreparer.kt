package com.pencil.chuangyin.printer

import android.graphics.Bitmap

/**
 * 图片打印前的几何 + 黑白化预处理契约。
 *
 * 负责：顺时针 90° 档旋转 → 等比缩放到打印头宽度（384 点）→ 调用 [ImageBinarizer]
 * 产出白底黑图。预览与打印共用本接口，保证所见即所得。
 *
 * 不修改也不回收输入 [src]；输出新位图，调用方负责回收。
 * 后期扩展（镜像、对比度增强、裁剪、自定义纸张宽度）在此接口上新增参数即可。
 */
interface ImagePreparer {

    /**
     * 完整预处理：旋转 → 缩放到打印宽 → 黑白化。
     *
     * @param src 原始位图（ARGB 即可）。
     * @param options 算法/阈值/旋转参数。
     * @return 384 点宽的白底纯黑 ARGB 位图。
     */
    fun prepare(src: Bitmap, options: ImageProcessOptions): Bitmap
}
