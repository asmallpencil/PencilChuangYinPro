package com.pencil.chuangyin.printer

/**
 * 灰度 → 单色素（黑白）转换算法。
 *
 * 采用「算法插件化」结构：调用方只依赖本枚举，具体实现由
 * [DitherAlgorithms.strategyFor] 注册分发（参考 s_y2printer / libdither 的架构），
 * 新增算法不需要改动调用方。
 *
 * - [Auto]：智能自适应，分析直方图双峰性、暗部占比与局部梯度后自动选路，默认算法；
 * - [FixedThreshold]：固定阈值二值化（纯阈值，线稿/文字最锐利）；
 * - [OtsuThreshold]：Otsu 全局自动阈值，按最大类间方差自动找到分割点；
 * - [FloydSteinberg]：Floyd–Steinberg 误差扩散抖动，照片灰阶过渡自然；
 * - [JarvisJudiceNinke]：JJN 误差扩散，扩散核更大更平滑，适合高分辨率照片；
 * - [Stucki]：Stucki 误差扩散，细节与平滑的折中；
 * - [Atkinson]：Atkinson 误差扩散抖动，对比度更高、高光更干净；
 * - [StructureAware]：结构感知误差扩散，蛇形扫描 + 按局部梯度抑制跨边缘扩散，
 *   显著减少「虫状」伪影并保住边缘细节；
 * - [BayerOrdered]：Bayer 8×8 有序抖动，规则网点、纹理稳定；
 * - [BlueNoise]：蓝噪声（IGN）抖动，点分布均匀、无低频斑块，视觉最舒适；
 * - [SobelEdge]：Sobel 算子边缘检测，输出黑白线稿图（描边效果）；
 * - [SauvolaAdaptive]：Sauvola 局部自适应阈值，专为白纸黑字文档/试卷设计，
 *   可消除纸张阴影、光照不均。
 */
enum class BinarizeAlgorithm {
    Auto,
    FixedThreshold,
    OtsuThreshold,
    FloydSteinberg,
    JarvisJudiceNinke,
    Stucki,
    Atkinson,
    StructureAware,
    BayerOrdered,
    BlueNoise,
    SobelEdge,
    SauvolaAdaptive,
}

/**
 * 图片打印前的处理参数（与设备侧 [ImagePrintOptions] 正交）。
 *
 * @param algorithm 黑白化算法，默认 [BinarizeAlgorithm.Auto]（按图像内容自动选路）。
 * @param threshold 阈值 0..255：固定阈值/有序抖动算法直接使用；边缘检测用作梯度灵敏度；
 *   Otsu/Sauvola/误差扩散中作为微调偏置。默认 149（略高于中点，线稿更锐利）。
 * @param rotationDegrees 顺时针旋转角度，仅支持 0/90/180/270。
 */
data class ImageProcessOptions(
    val algorithm: BinarizeAlgorithm = DEFAULT_ALGORITHM,
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

        /** 默认算法：智能自适应，避免「一刀切」阈值在不同图像上都表现平庸。 */
        @JvmField
        val DEFAULT_ALGORITHM = BinarizeAlgorithm.Auto

        /** 旋转滑杆可选档位。 */
        val ROTATION_STEPS = listOf(0, 90, 180, 270)
    }
}
