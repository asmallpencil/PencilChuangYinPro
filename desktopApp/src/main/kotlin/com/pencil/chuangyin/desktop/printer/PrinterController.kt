package com.pencil.chuangyin.desktop.printer

import com.pencil.chuangyin.desktop.text.PrintError
import com.pencil.chuangyin.desktop.text.PrintOptions
import com.pencil.chuangyin.desktop.text.PrintPhase
import com.pencil.chuangyin.desktop.text.PrintProgress
import com.pencil.chuangyin.desktop.text.PrintResult
import com.pencil.chuangyin.desktop.text.PrinterCommand
import com.pencil.chuangyin.desktop.text.PrinterStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 打印控制器 —— 逐字节复刻安卓端 `DefaultPrinterController`。
 *
 * 任务流程（与安卓完全一致）：
 * ```
 * beginJob:  ENABLE → ENABLE_EXTRA → 2ms → setThickness → ENABLE → ENABLE_EXTRA → ESC @ → WAKEUP
 * per page:  feedBefore(仅首页) → 光栅条带(255 行/带，带间 20ms)
 * endJob:    feedAfter → STOP
 * 多份:      每份之间 FEED_BETWEEN_COPIES 走纸
 * ```
 */
object PrinterController {

    private const val OVERHEAT_MESSAGE = "喷头过热，请稍后再打印"
    private const val FEED_GAP_MS = 30L

    private val busy = AtomicBoolean(false)

    @Volatile
    private var cancelRequested = false

    val isBusy: Boolean get() = busy.get()

    /** 请求取消当前任务（下一处检查点生效）。 */
    fun cancel() {
        cancelRequested = true
    }

    /**
     * 打印若干页（每页必须是已按 384 点宽二值化、并编码为光栅条带的数据）。
     *
     * @param pages 已排版并编码好的页面，索引 0 为第一页
     */
    suspend fun printPages(
        pages: List<RasterPage>,
        options: PrintOptions,
        onProgress: (PrintProgress) -> Unit = {},
    ): PrintResult {
        if (pages.isEmpty()) return PrintResult.Error(PrintError.RenderFailed, "没有可打印的内容")
        if (!PrinterConnectionStore.isConnected) return PrintResult.Error(PrintError.NotConnected)
        if (!busy.compareAndSet(false, true)) {
            return PrintResult.Error(PrintError.Generic, "已有打印任务正在进行")
        }
        cancelRequested = false
        PrinterConnectionStore.setPrinting(true)

        val copies = options.copies.coerceIn(PrintOptions.COPIES_RANGE)
        val totalPages = pages.size * copies
        try {
            var done = 0
            onProgress(
                PrintProgress(
                    phase = PrintPhase.Preparing,
                    pageDone = 0,
                    pageCountInSegment = totalPages,
                ),
            )
            for (copy in 0 until copies) {
                ensurePrintable()
                beginJob(options.thickness)
                for ((index, page) in pages.withIndex()) {
                    ensurePrintable()
                    if (index == 0 && options.feedBefore > 0) {
                        PrinterConnectionStore.write(PrinterProtocol.feedDots(options.feedBefore))
                    }
                    printPage(page)
                    done++
                    onProgress(
                        PrintProgress(
                            phase = PrintPhase.Printing,
                            pageDone = done,
                            pageCountInSegment = totalPages,
                        ),
                    )
                }
                PrinterConnectionStore.write(PrinterProtocol.feedDots(options.feedAfter))
                PrinterConnectionStore.write(PrinterProtocol.STOP_PRINT_JOB)
                if (copy < copies - 1) {
                    PrinterConnectionStore.write(PrinterProtocol.feedDots(PrinterProtocol.FEED_BETWEEN_COPIES))
                    delay(FEED_GAP_MS)
                }
            }
            onProgress(
                PrintProgress(
                    phase = PrintPhase.Finishing,
                    pageDone = totalPages,
                    pageCountInSegment = totalPages,
                ),
            )
            return PrintResult.Success
        } catch (_: JobCancelled) {
            withContext(NonCancellable) { PrinterConnectionStore.emergencyStop() }
            return PrintResult.Error(PrintError.Cancelled)
        } catch (c: CancellationException) {
            withContext(NonCancellable) { PrinterConnectionStore.emergencyStop() }
            throw c
        } catch (_: OverheatAbort) {
            runCatching { PrinterConnectionStore.write(PrinterProtocol.STOP_PRINT_JOB) }
            return PrintResult.Error(PrintError.Generic, OVERHEAT_MESSAGE)
        } catch (e: BluetoothChannelException) {
            return PrintResult.Error(PrintError.WriteFailed, e.message)
        } catch (t: Throwable) {
            return PrintResult.Error(PrintError.Generic, t.message ?: t.javaClass.simpleName)
        } finally {
            PrinterConnectionStore.setPrinting(false)
            busy.set(false)
            cancelRequested = false
        }
    }

    /** 「测试走纸」：走一小段纸，用来确认通道与打印头可用。 */
    suspend fun testFeed(): PrintResult {
        if (!PrinterConnectionStore.isConnected) return PrintResult.Error(PrintError.NotConnected)
        if (!busy.compareAndSet(false, true)) return PrintResult.Error(PrintError.Generic, "打印机忙")
        PrinterConnectionStore.setPrinting(true)
        return try {
            beginJob(PrinterCommand.DEFAULT_THICKNESS)
            PrinterConnectionStore.write(PrinterProtocol.feedDots(PrinterProtocol.TEST_FEED_DOTS))
            PrinterConnectionStore.write(PrinterProtocol.STOP_PRINT_JOB)
            PrintResult.Success
        } catch (e: BluetoothChannelException) {
            PrintResult.Error(PrintError.WriteFailed, e.message)
        } catch (t: Throwable) {
            PrintResult.Error(PrintError.Generic, t.message ?: t.javaClass.simpleName)
        } finally {
            PrinterConnectionStore.setPrinting(false)
            busy.set(false)
        }
    }

    // ---------------------------------------------------------------- 内部实现

    private suspend fun beginJob(thickness: Int) {
        val connection = PrinterConnectionStore
        connection.write(PrinterProtocol.ENABLE_PRINTER)
        connection.write(PrinterProtocol.ENABLE_PRINTER_EXTRA)
        delay(PrinterProtocol.COMMAND_GAP_MS)
        connection.write(PrinterProtocol.setThickness(thickness))
        connection.write(PrinterProtocol.ENABLE_PRINTER)
        connection.write(PrinterProtocol.ENABLE_PRINTER_EXTRA)
        connection.write(PrinterProtocol.INIT_PRINTER)
        connection.write(PrinterProtocol.WAKEUP)
    }

    private suspend fun printPage(page: RasterPage) {
        val bands = page.bands
        for ((index, band) in bands.withIndex()) {
            if (cancelRequested) throw JobCancelled()
            PrinterConnectionStore.write(band)
            if (index != bands.lastIndex) delay(PrinterProtocol.RASTER_BAND_DELAY_MS)
        }
    }

    private fun ensurePrintable() {
        if (cancelRequested) throw JobCancelled()
        if (!PrinterConnectionStore.isConnected) {
            throw BluetoothChannelException(ConnectError.Generic, "打印机已断开")
        }
        if (PrinterConnectionStore.deviceState.value.activeStatuses.contains(PrinterStatus.Overheat)) {
            throw OverheatAbort()
        }
    }

    private class JobCancelled : Exception("打印已取消")

    private class OverheatAbort : Exception(OVERHEAT_MESSAGE)
}
