package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.image.ImageProcessOptions
import com.pencil.chuangyin.desktop.image.ImageProcessor
import com.pencil.chuangyin.desktop.printer.ConnectionState
import com.pencil.chuangyin.desktop.printer.PrinterConnectionStore
import com.pencil.chuangyin.desktop.printer.PrinterController
import com.pencil.chuangyin.desktop.printer.PrinterProtocol
import com.pencil.chuangyin.desktop.printer.RasterPage
import com.pencil.chuangyin.desktop.support.OutputPathStore
import com.pencil.chuangyin.desktop.text.PrintError
import com.pencil.chuangyin.desktop.text.PrintOptions
import com.pencil.chuangyin.desktop.text.PrintResult
import com.pencil.chuangyin.desktop.text.PrinterStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面端的「打印」桥接层。
 *
 * 与安卓端一致：已连接蓝牙热敏打印机时，工具直接把内容下发到打印机；
 * 未连接（或下发失败）时退化为「渲染结果落盘」，通过 [previewNote] 生成面向用户的说明文案，
 * 并把文件作为 artifact 返回，用户可稍后在「图片打印」页连接打印机再打。
 */
internal object PrintBridge {

    /** 打印机是否已连接。 */
    fun isPrinterConnected(): Boolean = PrinterConnectionStore.isConnected

    /** 设备标签（test_feed 工具使用）。 */
    fun deviceLabel(): String = PrinterConnectionStore.connectedName ?: "未连接打印机"

    /** 打印机状态文案（printer_status 工具使用）。 */
    fun statusText(): String = buildString {
        appendLine("平台：Windows 桌面版")
        when (val connection = PrinterConnectionStore.connectionState.value) {
            is ConnectionState.Connected -> {
                appendLine("连接状态：已连接")
                appendLine("设备名称：${connection.device.displayName}")
                connection.device.macAddress?.let { appendLine("设备地址：$it") }
                connection.device.comPort?.let { appendLine("串口：$it") }
            }

            is ConnectionState.Connecting -> appendLine("连接状态：正在连接 ${connection.device.displayName}")
            ConnectionState.Disconnected -> appendLine("连接状态：未连接")
        }
        val device = PrinterConnectionStore.deviceState.value
        appendLine("电量：" + (device.batteryPercent?.let { "$it%" } ?: "未知"))
        appendLine(
            "当前故障：" +
                if (device.activeStatuses.isEmpty()) "无" else device.activeStatuses.joinToString("、") { statusLabel(it) },
        )
        appendLine("热敏纸宽度：58 mm（打印头 ${PrinterProtocol.PRINT_WIDTH_PX} px）")
        appendLine("输出目录：${OutputPathStore.displayPath()}")
        if (!isPrinterConnected()) {
            append("提示：未连接打印机，打印类工具会将排版结果保存到输出目录；可在首页「打印机」胶囊进入「连接打印机」页配对。")
        }
    }

    /**
     * 生成「已渲染但未打印」的统一说明文案。
     *
     * @param file 渲染产物
     * @param kind 产物类型，例如「文本」「图片」「表格」「绘图」「聊天记录」
     */
    fun previewNote(file: File, kind: String): String = buildString {
        append(kind)
        append("已排版完成，当前未连接热敏打印机，已保存为：")
        append(file.absolutePath)
        append("。连接打印机后可在「图片打印」页继续调整并打印。")
    }

    /**
     * 尝试把一张已落盘的图片送到打印机；未连接或失败时回退为 [previewNote]。
     *
     * 用于「生成后立即打印」类工具（绘图 / 表格 / 图片处理）。
     */
    suspend fun printImageFile(file: File, kind: String): String {
        if (!isPrinterConnected()) return previewNote(file, kind)
        val raw = runCatching { ImageProcessor.decode(file.readBytes()) }.getOrNull()
            ?: return previewNote(file, kind)
        val prepared = runCatching { ImageProcessor.prepare(raw, ImageProcessOptions()) }.getOrNull()
            ?: return previewNote(file, kind)
        return when (val result = PrinterController.printPages(listOf(RasterPage.from(prepared)), PrintOptions())) {
            is PrintResult.Success -> "$kind 已发送到打印机（文件保留在 ${file.absolutePath}）。"
            is PrintResult.Error -> buildString {
                append(kind)
                append("打印失败：")
                append(errorText(result.code, result.detail))
                append("，文件已保存为：")
                append(file.absolutePath)
                append("。")
            }
        }
    }

    /** 打印结果 → 可读文案（供工具回传给模型）。 */
    fun errorText(code: PrintError, detail: String?): String =
        code.name + (detail?.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "")

    /** 输出目录中按前缀 + 时间戳生成一个新的输出文件。 */
    fun newOutputFile(prefix: String, extension: String): File {
        val dir = OutputPathStore.activeDir()
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        var candidate = File(dir, "$prefix$stamp.$extension")
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$prefix${stamp}_$index.$extension")
            index++
        }
        return candidate
    }
}

/** 故障枚举 → 中文标签。 */
internal fun statusLabel(status: PrinterStatus): String = when (status) {
    PrinterStatus.OutOfPaper -> "缺纸"
    PrinterStatus.CoverOpen -> "开盖"
    PrinterStatus.Overheat -> "过热"
    PrinterStatus.LowBattery -> "电量低"
}
