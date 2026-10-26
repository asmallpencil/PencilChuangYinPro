package com.pencil.chuangyin.printer

/**
 * 灰度 → 单色素（黑白）转换算法。
 *
 * 全部使用学名称呼，便于后期扩展更多算法（有序抖动、自适应阈值变体等）：
 * - [FixedThreshold]：固定阈值二值化（纯阈值，线稿/文字最锐利）；
 * - [FloydSteinberg]：Floyd–Steinberg 误差扩散抖动，照片灰阶过渡自然；
 * - [Atkinson]：Atkinson 误差扩散抖动，对比度更高、高光更干净；
 * - [SobelEdge]：Sobel 算子边缘检测，输出黑白线稿图（描边效果）；
 * - [SauvolaAdaptive]：Sauvola 局部自适应阈值二值化，专为白纸黑字文档/试卷设计，
 *   可消除纸张阴影、光照不均，文字保持纯黑、背景保持纯白。
 */
enum class BinarizeAlgorithm {
    FixedThreshold,
    FloydSteinberg,
    Atkinson,
    SobelEdge,
    SauvolaAdaptive,
}

/**
 * 图片打印前的处理参数（与设备侧 [ImagePrintOptions] 正交）。
 *
 * @param algorithm 黑白化算法。
 * @param threshold 阈值 0..255：固定阈值算法直接使用；边缘检测用作梯度灵敏度；
 *   自适应算法中作为微调偏置。默认 149（逆向推荐区间偏中，兼顾照片与文档）。
 * @param rotationDegrees 顺时针旋转角度，仅支持 0/90/180/270。
 */
data class ImageProcessOptions(
    val algorithm: BinarizeAlgorithm = BinarizeAlgorithm.FixedThreshold,
    val threshold: Int = DEFAULT_THRESHOLD,
    val rotationDegrees: Int = 0,
) {
    init {
        require(threshold in 0..255) { "threshold 必须在 0..255" }
        require(rotationDegrees % 90 == 0 && rotationDegrees in 0..270) {
            "rotationDegrees 仅支持 0/90/180/270"
        }
    }

    companion object {
        const val MIN_THRESHOLD = 0
        const val MAX_THRESHOLD = 255
        const val DEFAULT_THRESHOLD = 149

        /** 旋转滑杆可选档位。 */
        val ROTATION_STEPS = listOf(0, 90, 180, 270)
    }
}
