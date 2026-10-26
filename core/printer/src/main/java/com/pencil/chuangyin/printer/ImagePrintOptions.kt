package com.pencil.chuangyin.printer

/**
 * 图片打印任务的设备侧参数（与图像处理参数 [ImageProcessOptions] 正交）。
 *
 * @param process 旋转/阈值/黑白化算法参数。
 * @param thickness 打印浓度/热能档位。
 * @param feedBefore 任务开始前走纸点数。
 * @param feedAfter 全部图片完成后的收尾走纸点数（撕纸位）。
 * @param feedBetweenPages 批量打印时相邻图片之间的留白走纸点数。
 * @param copies 批量份数：整批图片重复打印的次数（1 = 单遍）。
 */
data class ImagePrintOptions(
    val process: ImageProcessOptions = ImageProcessOptions(),
    val thickness: Int = PrinterCommand.DEFAULT_THICKNESS,
    val feedBefore: Int = PrinterCommand.FEED_BEFORE,
    val feedAfter: Int = PrinterCommand.FEED_AFTER,
    val feedBetweenPages: Int = FEED_BETWEEN_DEFAULT,
    val copies: Int = 1,
) {
    init {
        require(thickness in PrinterCommand.THICKNESS_MIN..PrinterCommand.THICKNESS_MAX) {
            "thickness 超出支持范围"
        }
        require(feedBefore in 0..PrinterCommand.FEED_MAX) { "feedBefore 超出支持范围" }
        require(feedAfter in 0..PrinterCommand.FEED_MAX) { "feedAfter 超出支持范围" }
        require(feedBetweenPages in 0..PrinterCommand.FEED_MAX) { "feedBetweenPages 超出支持范围" }
        require(copies in PrintOptions.COPIES_RANGE) { "copies 必须在 ${PrintOptions.COPIES_RANGE}" }
    }

    companion object {
        /** 批量图片之间默认留白（约 10mm）。 */
        const val FEED_BETWEEN_DEFAULT = 80

        val thicknessRange = PrinterCommand.THICKNESS_MIN..PrinterCommand.THICKNESS_MAX
        val feedAfterRange = PrinterCommand.FEED_AFTER_MIN..PrinterCommand.FEED_AFTER_MAX
        val feedBetweenRange = 0..PrinterCommand.FEED_AFTER_MAX
    }
}
