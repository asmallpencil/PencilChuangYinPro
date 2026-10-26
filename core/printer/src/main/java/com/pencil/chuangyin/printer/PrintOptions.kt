package com.pencil.chuangyin.printer

/**
 * 单次打印任务的设备侧参数（与文本文档内容正交）。
 * UI 调整项经此契约传入打印管线，默认值集中在此，禁止散落硬编码。
 *
 * @param thickness 打印浓度/热能档位。
 * @param feedBefore 打印前走纸行数（ESC J n）。
 * @param feedAfter 打印后走纸行数（撕纸位）。
 * @param threshold 二值化阈值 0..255，越小越浅的灰也判为黑。
 * @param copies 批量份数：整个任务重复执行的次数（1 = 单遍）。
 */
data class PrintOptions(
    val thickness: Int = PrinterCommand.DEFAULT_THICKNESS,
    val feedBefore: Int = PrinterCommand.FEED_BEFORE,
    val feedAfter: Int = PrinterCommand.FEED_AFTER,
    val threshold: Int = PrinterCommand.DEFAULT_THRESHOLD,
    val copies: Int = 1,
) {
    init {
        require(thickness in PrinterCommand.THICKNESS_MIN..PrinterCommand.THICKNESS_MAX) {
            "thickness 超出支持范围"
        }
        require(feedBefore in 0..PrinterCommand.FEED_MAX) { "feedBefore 超出支持范围" }
        require(feedAfter in 0..PrinterCommand.FEED_MAX) { "feedAfter 超出支持范围" }
        require(threshold in 0..255) { "threshold 必须在 0..255" }
        require(copies in COPIES_RANGE) { "copies 必须在 $COPIES_RANGE" }
    }

    companion object {
        /** UI 滑杆档位。 */
        val thicknessRange = PrinterCommand.THICKNESS_MIN..PrinterCommand.THICKNESS_MAX
        val feedAfterRange = PrinterCommand.FEED_AFTER_MIN..PrinterCommand.FEED_AFTER_MAX

        /** 批量份数范围。 */
        val COPIES_RANGE = 1..10

        /** 相邻两份之间的留白走纸行数。 */
        const val FEED_BETWEEN_COPIES = 80
    }
}
