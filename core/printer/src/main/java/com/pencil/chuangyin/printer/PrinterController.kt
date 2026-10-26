package com.pencil.chuangyin.printer

import android.graphics.Bitmap
import android.graphics.Typeface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 打印控制器门面：feature 层唯一依赖的打印入口。
 * 负责 连接/断开、文本渲染→分页→光栅编码→指令编排 的完整打印管线。
 * 具体实现（蓝牙）由 Hilt 绑定，替换实现不影响任何调用方。
 *
 * 作为“文本打印基础能力”，本接口刻意预留充分的扩展点：
 * - [printDocument] 暴露 [PrintProgress] 进度回调，支持任务队列、批量、通知栏进度；
 * - [cancelPrint] 可在任意页/段边界安全中止，已发内容不重复、不撕裂；
 * - [printBitmap] 供图片/标签等更复杂功能复用底层打印链路。
 */
interface PrinterController {
    val isConnected: Boolean

    /** 全局共享连接状态：连接页、文本页等所有页面订阅同一事实来源。 */
    val connectionState: StateFlow<ConnectionState>

    val statusEvents: Flow<PrinterStatus>

    /**
     * 设备实时状态快照（当前故障集合 + 电量百分比）：连接层周期轮询维护，
     * 故障恢复后自动从快照消除。UI 持续展示类信息以此为准。
     */
    val deviceState: StateFlow<PrinterDeviceState>

    fun connect(macAddress: String, deviceName: String, callback: (ConnectionResult) -> Unit)

    fun disconnect()

    /**
     * 打印文本文档（含自定义字体）。
     *
     * 自动完成：超长文本分段、分页渲染、255 行光栅分带、过热保护冷却等待，
     * 全部在同一个打印任务内连续执行，跨页/跨段内容无缝衔接、不丢失、不乱码。
     *
     * @param options 浓度/走纸等设备侧参数。
     * @param onProgress 进度回调（段/页/冷却），在 IO 线程触发，UI 需自行切线程。
     */
    suspend fun printDocument(
        doc: TextDoc,
        typeface: Typeface?,
        options: PrintOptions = PrintOptions(),
        onProgress: (PrintProgress) -> Unit = {},
    ): PrintResult

    /** 直接打印一张位图（自动按打印宽度缩放、二值化、分条带发送）。 */
    suspend fun printBitmap(
        bitmap: Bitmap,
        options: PrintOptions = PrintOptions(),
    ): PrintResult

    /**
     * 批量打印图片页（图片打印的基础能力，供相册/PDF/压缩包/拍照等所有上层来源复用）。
     *
     * 采用懒加载：控制器逐页调用 [pageLoader] 取原图，预处理（旋转/缩放/黑白化）、
     * 分条带发送完成后立即回收该页位图，再加载下一页——因此即使一次选择上百张图片，
     * 任意时刻内存中也只有当前页，不会 OOM 或卡死。
     *
     * 同样具备：整任务一次使能/停止、255 行分带确认、过热保护无缝等待、安全取消。
     *
     * @param pageCount 总页数。
     * @param options 算法/阈值/旋转/浓度/走纸/份数参数。
     * @param onProgress 进度（段/页语义复用为 当前页/总页数），在 IO 线程触发。
     * @param processSelector 可选：按页返回各自的图像处理参数（多图单独调整时传入）；
     *   为 null 时所有页统一使用 [ImagePrintOptions.process]。
     * @param pageLoader 按序号返回该页原始位图；返回的位图由控制器在用完后回收。
     */
    suspend fun printImagePages(
        pageCount: Int,
        options: ImagePrintOptions = ImagePrintOptions(),
        onProgress: (PrintProgress) -> Unit = {},
        processSelector: ((pageIndex: Int) -> ImageProcessOptions)? = null,
        pageLoader: suspend (pageIndex: Int) -> Bitmap,
    ): PrintResult

    /** 请求取消当前打印：在下一个页/段安全边界停止，不重复发送已成功的内容。 */
    fun cancelPrint()

    /**
     * 走纸自测：发送最小打印任务（私有使能 → 唤醒 → 走纸 → 停止），不打印内容。
     * 用于在连接页快速验证“通道 + 私有握手”是否正常。
     */
    suspend fun testFeed(): PrintResult
}
