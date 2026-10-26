package com.pencil.chuangyin.desktop.text

/**
 * 文本打印数据模型 —— 与安卓端 `core/printer` 的同名类型逐字段对齐：
 * `TextDoc` / `PrintOptions` / `PrinterCommand` / 打印进度与结果枚举。
 *
 * 桌面端通过 RFCOMM / SPP 蓝牙通道下发同一套字节流，因此这里逐字段保留完整契约，
 * 使页面逻辑（进度行、错误文案映射、过热提示）与安卓端共用同一套状态机。
 */

/** 对齐方式（与安卓端 PrintAlign 一致）。 */
enum class PrintAlign { Start, Center, End }

/** 待打印文本文档（与安卓端 TextDoc 一致）。 */
data class TextDoc(
    val title: String = "",
    val content: String = "",
    val fontSizeSp: Float = DEFAULT_FONT_SIZE,
    val titleSizeSp: Float = DEFAULT_TITLE_SIZE,
    val lineSpacingMultiplier: Float = DEFAULT_LINE_SPACING,
    val align: PrintAlign = PrintAlign.Start,
    val bold: Boolean = false,
) {
    companion object {
        val FONT_SIZE_RANGE = 8f..64f
        val TITLE_SIZE_RANGE = 12f..80f
        const val DEFAULT_FONT_SIZE = 14f
        const val DEFAULT_TITLE_SIZE = 20f
        const val DEFAULT_LINE_SPACING = 1.4f
        const val MIN_LINE_SPACING = 1.0f
        const val MAX_LINE_SPACING = 2.5f
    }
}

/** 打印参数（与安卓端 PrintOptions 一致）。 */
data class PrintOptions(
    val thickness: Int = PrinterCommand.DEFAULT_THICKNESS,
    val feedBefore: Int = PrinterCommand.FEED_BEFORE,
    val feedAfter: Int = PrinterCommand.FEED_AFTER,
    val threshold: Int = PrinterCommand.DEFAULT_THRESHOLD,
    val copies: Int = 1,
) {
    companion object {
        val thicknessRange = 1..5
        val feedAfterRange = 20..200
        val COPIES_RANGE = 1..10
        const val FEED_BETWEEN_COPIES = 80
    }
}

/** 打印指令常量（与安卓端 PrinterCommand 一致）。 */
object PrinterCommand {
    const val PRINT_WIDTH_PX = 384
    const val PAGE_TARGET_HEIGHT_PX = 768
    const val TEXT_SEGMENT_CHARS = 8_000

    const val THICKNESS_MIN = 1
    const val THICKNESS_MAX = 5
    const val DEFAULT_THICKNESS = 1
    const val DEFAULT_THRESHOLD = 128
    const val FEED_BEFORE = 10
    const val FEED_AFTER = 100
    const val FEED_AFTER_MIN = 20
    const val FEED_AFTER_MAX = 200
}

/** 打印失败原因（与安卓端 PrintError 一致）。 */
enum class PrintError {
    NotConnected,
    Permission,
    Timeout,
    WriteFailed,
    RenderFailed,
    Cancelled,
    Generic,
}

/** 打印阶段（与安卓端 PrintPhase 一致）。 */
enum class PrintPhase { Preparing, Printing, CoolingDown, Finishing }

/** 打印进度（与安卓端 PrintProgress 一致）。 */
data class PrintProgress(
    val phase: PrintPhase = PrintPhase.Preparing,
    val segmentIndex: Int = 0,
    val segmentCount: Int = 1,
    val pageDone: Int = 0,
    val pageCountInSegment: Int = 1,
    val coolingRemainingMs: Long = 0L,
)

/** 打印结果（与安卓端 PrintResult 一致）。 */
sealed interface PrintResult {
    data object Success : PrintResult
    data class Error(val code: PrintError, val detail: String? = null) : PrintResult
}

/** 打印机状态事件（与安卓端 PrinterStatus 一致）。 */
enum class PrinterStatus { OutOfPaper, CoverOpen, Overheat, LowBattery }
