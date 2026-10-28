package com.pencil.chuangyin.printer

import android.graphics.Bitmap
import android.graphics.Typeface
import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * 默认打印控制器：私有任务封装 + ESC/POS 数据指令（严格复刻逆向 com.beeprt.sdk 时序）。
 *
 * 巨量文本可靠性设计：
 * 1. 全文先按换行边界 [TextDoc.segment] 分段（每段 ≤ 8000 字），逐段排版，杜绝超大布局；
 * 2. 每段经 [TextBitmapRenderer.renderPages] 按 768px 流式分页，内存中始终只有一页；
 * 3. 每页二值化后再按 255 行切成多条 GS v 0 光栅，逐带确认写入后才推进，
 *    因此跨段/跨页内容首尾相接，不会乱码、错位或丢内容；
 * 4. 整个过程只开启一次打印任务（enable…stop），过热时仅在安全边界暂停发送，
 *    降温后续传，已发内容不重发；
 * 5. 取消同样在页/带安全边界生效。
 */
@Singleton
class DefaultPrinterController @Inject constructor(
    private val connection: PrinterConnection,
    private val renderer: TextBitmapRenderer,
    private val encoder: RasterEncoder,
    private val imagePreparer: ImagePreparer,
) : PrinterController {

    override val isConnected: Boolean get() = connection.isConnected
    override val connectionState: StateFlow<ConnectionState> get() = connection.connectionState
    override val statusEvents: Flow<PrinterStatus> get() = connection.statusEvents
    override val deviceState: StateFlow<PrinterDeviceState> get() = connection.deviceState

    @Volatile private var cancelRequested = false

    /**
     * 任务互斥锁：同一时刻只允许一个打印任务占用蓝牙通道。
     * 防止“取消→按钮变回打印→疯狂连点”导致两个任务并发写同一条流、
     * 字节交错把打印机固件打挂（表现为疯狂吐白纸、杀 App 也停不下来）。
     */
    private val jobMutex = Mutex()

    override fun connect(macAddress: String, deviceName: String, callback: (ConnectionResult) -> Unit) {
        connection.connect(macAddress, deviceName, callback)
    }

    override fun disconnect() {
        cancelRequested = true
        connection.emergencyStop()
        connection.disconnect()
    }

    override fun cancelPrint() {
        // 不等页/带边界：立即由独立线程向打印机连发 STOP，尽量清空固件侧任务；
        // 主任务循环最迟在下一个 512 字节分块处检测到标志并退出。
        cancelRequested = true
        connection.emergencyStop()
    }

    override suspend fun printDocument(
        doc: TextDoc,
        typeface: Typeface?,
        options: PrintOptions,
        onProgress: (PrintProgress) -> Unit,
    ): PrintResult = runGuardedJob { lastOverheatMs ->
        runTextJob(doc, typeface, options, onProgress, lastOverheatMs)
    }

    override suspend fun printImagePages(
        pageCount: Int,
        options: ImagePrintOptions,
        onProgress: (PrintProgress) -> Unit,
        processSelector: ((pageIndex: Int) -> ImageProcessOptions)?,
        pageLoader: suspend (pageIndex: Int) -> Bitmap,
    ): PrintResult {
        if (pageCount <= 0) {
            return PrintResult.Error(PrintError.Generic, "没有可打印的图片")
        }
        return runGuardedJob { lastOverheatMs ->
            runImageJob(pageCount, options, onProgress, lastOverheatMs, processSelector, pageLoader)
        }
    }

    /**
     * 所有打印任务共用的边界守卫：任务互斥、复位取消标志、校验连接、收集过热信号时间戳，
     * 并把内部 [JobAborted]/其它异常统一转换为 [PrintResult]。
     */
    private suspend fun runGuardedJob(
        block: suspend (AtomicLong) -> PrintResult,
    ): PrintResult = withContext(Dispatchers.IO) {
        if (!jobMutex.tryLock()) {
            Log.w(TAG, "已有打印任务在执行，拒绝新任务")
            return@withContext PrintResult.Error(PrintError.Generic, "打印机忙，请等待当前任务结束或先取消")
        }
        try {
            // 通知连接层：打印任务进行中，停止状态/电量轮询，
            // 防止查询字节注入光栅数据流导致错位/乱码。
            connection.setPrinting(true)
            cancelRequested = false
            if (!connection.isConnected) {
                return@withContext PrintResult.Error(PrintError.NotConnected, "connectionState 不是 Connected")
            }
            // 过热信号时间戳：接收线程在故障期间会持续（或经轮询诱导）上报 Overheat。
            val lastOverheatMs = AtomicLong(0L)
            val collector = launch(Dispatchers.Unconfined) {
                connection.statusEvents.collect { status ->
                    if (status == PrinterStatus.Overheat) {
                        lastOverheatMs.set(System.currentTimeMillis())
                    }
                }
            }
            val result = runCatching {
                block(lastOverheatMs)
            }.getOrElse { t ->
                if (t is JobAborted) t.result else {
                    Log.e(TAG, "打印异常", t)
                    PrintResult.Error(PrintError.Generic, t.message)
                }
            }
            collector.cancel()
            result
        } finally {
            connection.setPrinting(false)
            jobMutex.unlock()
        }
    }

    /**
     * 图片批量任务主体（挂起，运行于 IO 线程；逐页懒加载，内存恒定）。
     *
     * 严格复刻原厂 PrinterUtil$6$1 多图时序（逐张 Glide 回调验证）——**每张图片都是
     * 一个完整独立的打印任务**：
     * enable+附加 → setThickness → enable+附加 → 唤醒 → ESC J 前导 10 点
     * → **单条 GS v 0（y=整页全高，不做 255 行分带）** → 末张 ESC J 100 点 → stop；
     * 非末张位图发完立即 stop，下一张重新 enable，前导 10 点即页间留白。
     * 旧实现“整批一个任务 + 多条分带 GS v 0 + 页间 ESC J”会让该固件从第二张起
     * 缓冲指针错位（乱码/错位/色块丢失/规律性变淡），已废弃。
     */
    private suspend fun runImageJob(
        pageCount: Int,
        options: ImagePrintOptions,
        onProgress: (PrintProgress) -> Unit,
        lastOverheatMs: AtomicLong,
        processSelector: ((Int) -> ImageProcessOptions)?,
        pageLoader: suspend (Int) -> Bitmap,
    ): PrintResult {
        var stopped = false
        try {
            onProgress(PrintProgress(PrintPhase.Preparing))
            val totalSteps = pageCount * options.copies
            Log.i(TAG, "图片打印开始，共 $pageCount 页 × ${options.copies} 份，算法=${options.process.algorithm}")

            for (copy in 0 until options.copies) {
                for (index in 0 until pageCount) {
                    val step = copy * pageCount + index
                    val isLastStep = step == totalSteps - 1
                    throwIfCancelled()
                    afterCoolingCheck(lastOverheatMs) { remainingMs ->
                        onProgress(imageProgress(PrintPhase.CoolingDown, step, totalSteps, remainingMs))
                    }
                    onProgress(imageProgress(PrintPhase.Printing, step, totalSteps, 0L))

                    val raw = try {
                        pageLoader(index)
                    } catch (aborted: JobAborted) {
                        throw aborted
                    } catch (t: Throwable) {
                        Log.e(TAG, "第 ${index + 1} 页图片加载失败", t)
                        throw JobAborted(PrintResult.Error(PrintError.RenderFailed, t.message))
                    }
                    val processed = try {
                        val pageProcess = processSelector?.invoke(index) ?: options.process
                        imagePreparer.prepare(raw, pageProcess)
                    } catch (t: Throwable) {
                        runCatching { raw.recycle() }
                        Log.e(TAG, "第 ${index + 1} 页图片处理失败", t)
                        throw JobAborted(PrintResult.Error(PrintError.RenderFailed, t.message))
                    }
                    if (processed != raw) runCatching { raw.recycle() }
                    try {
                        beginJob(options.thickness)?.let { throw JobAborted(it) }
                        if (options.feedBefore > 0) {
                            send(PrinterCommand.feedDots(options.feedBefore), "前导走纸")
                                ?.let { throw JobAborted(it) }
                        }
                        // 整页位图一条 GS v 0（头部 y 为全高，数据按原厂 1024/1ms 节流连续推送）。
                        val raster = try {
                            encoder.encode(processed, PrinterCommand.MODE_NORMAL, PrinterCommand.DEFAULT_THRESHOLD)
                        } catch (t: Throwable) {
                            Log.e(TAG, "第 ${index + 1} 页位图编码失败", t)
                            throw JobAborted(PrintResult.Error(PrintError.RenderFailed, t.message))
                        }
                        send(raster, "GS v 0 整页位图")?.let { throw JobAborted(it) }
                        if (isLastStep) {
                            send(PrinterCommand.feedDots(options.feedAfter), "收尾走纸")
                                ?.let { throw JobAborted(it) }
                        }
                        endJob()?.let { throw JobAborted(it) }
                        // v1.0.9.1 已修复多图乱码根因（蓝牙多写入者字节污染：写锁串行化 +
                        // 打印期间暂停轮询），页间不再人为停顿，连续下发下一张。
                    } finally {
                        runCatching { processed.recycle() }
                    }
                }
            }

            onProgress(PrintProgress(PrintPhase.Finishing, segmentIndex = totalSteps, segmentCount = totalSteps))
            stopped = true
            Log.i(TAG, "图片打印完成，共 $pageCount 页 × ${options.copies} 份")
            return PrintResult.Success
        } catch (aborted: JobAborted) {
            return aborted.result
        } finally {
            if (!stopped) bestEffortStop()
        }
    }

    private fun imageProgress(
        phase: PrintPhase,
        index: Int,
        pageCount: Int,
        coolingRemainingMs: Long,
    ) = PrintProgress(
        phase = phase,
        segmentIndex = index,
        segmentCount = pageCount,
        pageDone = 0,
        pageCountInSegment = 1,
        coolingRemainingMs = coolingRemainingMs,
    )

    override suspend fun printBitmap(bitmap: Bitmap, options: PrintOptions): PrintResult =
        runGuardedJob { runSingleBitmapJob(bitmap, options) }

    private fun runSingleBitmapJob(bitmap: Bitmap, options: PrintOptions): PrintResult {
        val prepared = runCatching {
            encoder.prepareForPrint(bitmap, PrinterCommand.DEFAULT_THRESHOLD)
        }.getOrElse {
            return PrintResult.Error(PrintError.RenderFailed, it.toString())
        }
        var ended = false
        try {
            beginJob(options.thickness)?.let { return it }
            if (options.feedBefore > 0) {
                send(PrinterCommand.feedDots(options.feedBefore), "前导走纸")?.let { return it }
            }
            // 与多图同一语义：整页一条 GS v 0（全高），不做 255 行分带。
            val raster = encoder.encode(prepared, PrinterCommand.MODE_NORMAL, PrinterCommand.DEFAULT_THRESHOLD)
            send(raster, "GS v 0 整页位图")?.let { return it }
            send(PrinterCommand.feedDots(options.feedAfter), "收尾走纸")?.let { return it }
            endJob()?.let { return it }
            ended = true
            return PrintResult.Success
        } catch (aborted: JobAborted) {
            return aborted.result
        } finally {
            if (!ended) bestEffortStop()
            if (prepared != bitmap) runCatching { prepared.recycle() }
        }
    }

    override suspend fun testFeed(): PrintResult = runGuardedJob { runTestFeedJob() }

    private fun runTestFeedJob(): PrintResult {
        var ended = false
        try {
            beginJob(PrinterCommand.DEFAULT_THICKNESS)?.let { return it }
            send(PrinterCommand.feedDots(PrinterCommand.TEST_FEED_DOTS), "自测走纸")?.let { return it }
            endJob()?.let { return it }
            ended = true
            return PrintResult.Success
        } catch (aborted: JobAborted) {
            return aborted.result
        } finally {
            if (!ended) bestEffortStop()
        }
    }

    /** 文本任务主体（阻塞式，运行于 IO 线程）。 */
    private fun runTextJob(
        doc: TextDoc,
        typeface: Typeface?,
        options: PrintOptions,
        onProgress: (PrintProgress) -> Unit,
        lastOverheatMs: AtomicLong,
    ): PrintResult {
        var stopped = false
        try {
            onProgress(PrintProgress(PrintPhase.Preparing))
            // 1~4：使能 → 浓度 → 唤醒 → 前导走纸。
            beginJob(options.thickness)?.let { return it }
            if (options.feedBefore > 0) {
                send(PrinterCommand.feedDots(options.feedBefore), "前导走纸")?.let { return it }
            }

            val segments = TextDoc.segment(doc.content)
            var pageDone = 0
            val totalSegments = segments.size * options.copies
            Log.i(TAG, "分段数=${segments.size} × ${options.copies} 份，总字符=${doc.content.length}")

            for (copy in 0 until options.copies) {
                segments.forEachIndexed { segmentIndex, segment ->
                    throwIfCancelled()
                    // 每一份都是完整副本：标题在每次的第一段打印；其余段仅正文，物理上连续拼接。
                    val segmentDoc = doc.copy(
                        content = segment,
                        title = if (segmentIndex == 0) doc.title else "",
                    )
                    val globalSegment = copy * segments.size + segmentIndex
                    renderer.renderPages(segmentDoc, typeface) { page, _, pageCountInSegment ->
                        throwIfCancelled()
                        afterCoolingCheck(lastOverheatMs) { remainingMs ->
                            onProgress(
                                PrintProgress(
                                    phase = PrintPhase.CoolingDown,
                                    segmentIndex = globalSegment,
                                    segmentCount = totalSegments,
                                    pageDone = pageDone,
                                    pageCountInSegment = pageCountInSegment,
                                    coolingRemainingMs = remainingMs,
                                ),
                            )
                        }
                        onProgress(
                            PrintProgress(
                                phase = PrintPhase.Printing,
                                segmentIndex = globalSegment,
                                segmentCount = totalSegments,
                                pageDone = pageDone,
                                pageCountInSegment = pageCountInSegment,
                            ),
                        )
                        val prepared = runCatching {
                            encoder.prepareForPrint(page, PrinterCommand.DEFAULT_THRESHOLD)
                        }.getOrElse {
                            if (it is JobAborted) throw it
                            Log.e(TAG, "页面二值化失败", it)
                            throw JobAborted(PrintResult.Error(PrintError.RenderFailed, it.message))
                        }
                        try {
                            printBands(prepared, lastOverheatMs) { remainingMs ->
                                onProgress(
                                    PrintProgress(
                                        phase = if (remainingMs > 0L) PrintPhase.CoolingDown else PrintPhase.Printing,
                                        segmentIndex = globalSegment,
                                        segmentCount = totalSegments,
                                        pageDone = pageDone,
                                        pageCountInSegment = pageCountInSegment,
                                        coolingRemainingMs = remainingMs,
                                    ),
                                )
                            }?.let { error -> throw JobAborted(error) }
                        } finally {
                            runCatching { prepared.recycle() }
                            if (prepared != page) runCatching { page.recycle() }
                        }
                        pageDone++
                    }
                }
                if (copy < options.copies - 1) {
                    send(PrinterCommand.feedDots(PrintOptions.FEED_BETWEEN_COPIES), "份间留白")
                        ?.let { return it }
                }
            }

            // 收尾走纸 → 停止任务。
            onProgress(PrintProgress(PrintPhase.Finishing, pageDone = pageDone))
            send(PrinterCommand.feedDots(options.feedAfter), "收尾走纸")?.let { return it }
            endJob()?.let { return it }
            stopped = true
            Log.i(TAG, "文本打印完成，共 $pageDone 页")
            return PrintResult.Success
        } catch (aborted: JobAborted) {
            return aborted.result
        } finally {
            if (!stopped) bestEffortStop()
        }
    }

    /** 将一页位图按 255 行切成多条 GS v 0 顺序下发；每条成功写入后才推进。 */
    private fun printBands(
        prepared: Bitmap,
        lastOverheatMs: AtomicLong,
        pageProgress: (coolingRemainingMs: Long) -> Unit,
    ): PrintResult.Error? {
        var y = 0
        var bandIndex = 0
        while (y < prepared.height) {
            throwIfCancelled()
            afterCoolingCheck(lastOverheatMs) { rem -> pageProgress(rem) }
            val bandHeight = minOf(PrinterCommand.RASTER_BAND_HEIGHT, prepared.height - y)
            val band = Bitmap.createBitmap(prepared, 0, y, prepared.width, bandHeight)
            val raster = try {
                encoder.encode(band, PrinterCommand.MODE_NORMAL, PrinterCommand.DEFAULT_THRESHOLD)
            } catch (t: Throwable) {
                runCatching { band.recycle() }
                Log.e(TAG, "条带编码失败", t)
                return PrintResult.Error(PrintError.RenderFailed, t.message)
            }
            val outcome = send(raster, "GS v 0 条带$bandIndex")
            runCatching { band.recycle() }
            outcome?.let { return it }
            y += bandHeight
            bandIndex++
            if (y < prepared.height) Thread.sleep(PrinterCommand.RASTER_BAND_DELAY_MS)
        }
        return null
    }

    private fun throwIfCancelled() {
        if (cancelRequested) {
            Log.i(TAG, "打印被用户取消")
            throw JobAborted(PrintResult.Error(PrintError.Cancelled))
        }
    }

    /**
     * 记录式观察：某点打印前/后的过热检查。
     *
     * 重要：v1.0.9 改为**非阻塞**——本机固件自带过热自主散热（过热→红灯停→十几秒后
     * 恢复绿灯继续打印），App 不应在固件已完成散热后仍掐断打印强制倒计时（旧逻辑导致
     * “机器恢复打印了才弹 10s 倒计时”的荒唐体验）。此处只做状态观察与冷却进度上报，
     * 不再休眠阻塞打印流，实际散热由固件自主完成。
     */
    private fun afterCoolingCheck(
        lastOverheatMs: AtomicLong,
        onCooling: (remainingMs: Long) -> Unit = {},
    ) {
        val last = lastOverheatMs.get()
        if (last == 0L) return
        val since = System.currentTimeMillis() - last
        if (since < PrinterCommand.OVERHEAT_OBSERVE_WINDOW_MS) {
            onCooling((PrinterCommand.OVERHEAT_OBSERVE_WINDOW_MS - since).coerceAtLeast(0L))
        }
    }

    /**
     * 单个打印任务开始（严格按原厂 PrinterUtil$6$1 行 80..121 顺序）：
     * enable(10 FF F1 02 + 1F B2 10) → setThickness → **再次 enable** → 唤醒 12×NUL。
     * 每张图片都走一遍该序列；缺第二次 enable 时部分固件第二张起不消费位图。
     */
    private fun beginJob(thickness: Int): PrintResult.Error? {
        send(PrinterCommand.ENABLE_PRINTER, "使能")?.let { return it }
        send(PrinterCommand.ENABLE_PRINTER_EXTRA, "使能附加")?.let { return it }
        Thread.sleep(PrinterCommand.COMMAND_GAP_MS)
        send(PrinterCommand.setThickness(thickness), "浓度")?.let { return it }
        send(PrinterCommand.ENABLE_PRINTER, "使能(二次)")?.let { return it }
        send(PrinterCommand.ENABLE_PRINTER_EXTRA, "使能附加(二次)")?.let { return it }
        // ESC @ 复位：清零 ESC/POS 数据模式与行缓冲，确保上一任务的光栅解析残留
        // 不影响本任务（否则第二张图起会出现行偏移/错位/乱码）。
        send(PrinterCommand.INIT_PRINTER, "复位数据模式")?.let { return it }
        send(PrinterCommand.WAKEUP, "唤醒")?.let { return it }
        return null
    }

    private fun endJob(): PrintResult.Error? = send(PrinterCommand.STOP_PRINT_JOB, "停止任务")

    private fun bestEffortStop() {
        runCatching { connection.write(PrinterCommand.STOP_PRINT_JOB) }
    }

    /**
     * 发送完整字节序列（任务边界/整块位图）。不在控制器层切碎：连接层严格按原厂
     * 1024 字节/块 + 1ms 间隔节流（[BluetoothPrinterConnection.write]），控制器若再
     * 切成 512 小块会绕过该节流，发包过快会导致廉价固件丢块、规律性变淡。
     * 取消的即时性由 [emergencyStop] 独立线程连发 STOP 保证，不依赖此处切碎。
     */
    private fun send(bytes: ByteArray, label: String): PrintResult.Error? {
        if (bytes.isEmpty()) return null
        throwIfCancelled()
        val outcome = connection.write(bytes, 0, bytes.size)
        return if (outcome is WriteOutcome.Failure) {
            Log.e(TAG, "$label 发送失败：${outcome.reason}")
            // 数据异常兜底：状态仍显示「已连接」但字节已无法送达（设备断联/固件异常）
            // → 立即断开刷新全局状态，交由 PrinterAutoConnect 重新连接；
            // 同时取消当前任务，避免继续对失效通道发送剩余内容。
            if (connection.isConnected) {
                cancelRequested = true
                Log.w(TAG, "检测到设备数据异常，主动断开以触发自动重连")
                connection.disconnect()
            }
            PrintResult.Error(PrintError.WriteFailed, "$label：${outcome.reason}")
        } else {
            null
        }
    }

    /** 把打印错误从渲染回调内层抛到任务边界统一处理。 */
    private class JobAborted(val result: PrintResult.Error) : Exception()

    private companion object {
        const val TAG = "PencilPrinter"
    }
}
