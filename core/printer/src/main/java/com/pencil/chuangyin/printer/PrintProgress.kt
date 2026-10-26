package com.pencil.chuangyin.printer

/** 打印所处阶段（供 UI 展示“分段/冷却/收尾”等状态）。 */
enum class PrintPhase { Preparing, Printing, CoolingDown, Finishing }

/**
 * 打印进度回调载荷。长文本会被自动分段、分页连续打印，本对象描述整体推进位置，
 * 供后期复杂功能（任务队列、批量打印、进度条/通知）订阅，不影响打印主流程。
 *
 * @param phase 当前阶段。
 * @param segmentIndex 当前文本段序号（0 起）。
 * @param segmentCount 文本段总数。
 * @param pageDone 已成功发送的页数。
 * @param pageCountInSegment 当前段总页数。
 * @param coolingRemainingMs 过热冷却剩余估计毫秒（仅 [PrintPhase.CoolingDown] 时有意义）。
 */
data class PrintProgress(
    val phase: PrintPhase,
    val segmentIndex: Int = 0,
    val segmentCount: Int = 1,
    val pageDone: Int = 0,
    val pageCountInSegment: Int = 0,
    val coolingRemainingMs: Long = 0L,
)
