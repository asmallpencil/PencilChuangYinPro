package com.pencil.chuangyin.desktop.printer

import java.io.ByteArrayOutputStream

/** 把一组 0..255 的整数打包成字节数组（避免满屏 `.toByte()`）。 */
private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

/**
 * 打印机协议常量与指令构造 —— 逐字节复刻安卓端 `com.beeprt.sdk` 封装出的报文。
 *
 * 安卓端由厂商 SDK 生成这些私有指令；桌面端无法复用 SDK（只有 Android AAR），
 * 因此按逆向得到的字节序列直接在 JVM 侧构造，保证输出与安卓端完全一致。
 */
object PrinterProtocol {

    /** 打印头宽度（像素点）。 */
    const val PRINT_WIDTH_PX = 384

    /** 每页目标高度，用于文本排版分页。 */
    const val PAGE_TARGET_HEIGHT_PX = 768

    /** 光栅数据单条最大高度（`GS v 0` 的 y 高字节限制决定）。 */
    const val RASTER_BAND_HEIGHT = 255

    /** 单次写入上限，安卓端 SDK 按 1024 字节分块下发。 */
    const val CHUNK_SIZE = 1024

    /** 分块之间的间隔（毫秒）。 */
    const val CHUNK_DELAY_MS = 1L

    /** 光栅条带之间的间隔（毫秒），给打印头留出缓存时间。 */
    const val RASTER_BAND_DELAY_MS = 20L

    /** 连续两条指令之间的间隔（毫秒）。 */
    const val COMMAND_GAP_MS = 2L

    /** 多份打印之间的走纸点数。 */
    const val FEED_BETWEEN_COPIES = 80

    /** 连接超时（毫秒）。 */
    const val CONNECT_TIMEOUT_MS = 15_000

    /** 状态轮询周期（毫秒）。 */
    const val STATE_POLL_INTERVAL_MS = 3_000L

    /** 故障状态有效期（毫秒）；超时未再上报即视为恢复。 */
    const val STATUS_TTL_MS = 7_000L

    /** 每 N 次轮询查一次电量。 */
    const val BATTERY_POLL_EVERY_N_TICKS = 5

    /** 「测试走纸」走纸点数。 */
    const val TEST_FEED_DOTS = 60

    /** 二值化阈值（与安卓端一致）。 */
    const val DEFAULT_THRESHOLD = 128

    /** 使能打印机。 */
    val ENABLE_PRINTER: ByteArray = bytes(0x10, 0xFF, 0xF1, 0x02)

    /** 使能打印机（扩展）。 */
    val ENABLE_PRINTER_EXTRA: ByteArray = bytes(0x1F, 0xB2, 0x10)

    /** 停止当前打印任务。 */
    val STOP_PRINT_JOB: ByteArray = bytes(0x10, 0xFF, 0xF1, 0x45)

    /** `ESC @` 初始化。 */
    val INIT_PRINTER: ByteArray = bytes(0x1B, 0x40)

    /** 唤醒：12 个 NUL。 */
    val WAKEUP: ByteArray = ByteArray(12)

    /** 查询状态。 */
    val QUERY_STATUS: ByteArray = bytes(0x10, 0xFF, 0x40)

    /** 查询电量。 */
    val QUERY_BATTERY: ByteArray = bytes(0x10, 0xFF, 0x50, 0xF1)

    /** 设置浓度/加热等级 1..5。 */
    fun setThickness(level: Int): ByteArray {
        val n = level.coerceIn(1, 5)
        return bytes(0x10, 0xFF, 0x10, 0x00, n)
    }

    /**
     * 走纸 N 点 —— `ESC J n` 单条最多 255 点，超出部分拆成多条。
     */
    fun feedDots(total: Int): ByteArray {
        var remaining = total.coerceAtLeast(0)
        if (remaining == 0) return ByteArray(0)
        val out = ByteArrayOutputStream()
        while (remaining > 0) {
            val chunk = minOf(remaining, 255)
            out.write(0x1B)
            out.write(0x4A)
            out.write(chunk)
            remaining -= chunk
        }
        return out.toByteArray()
    }

    /**
     * `GS v 0` 光栅位图头。
     *
     * 注意第三字节必须是 ASCII `'0'`（0x30），这是该指令的字面量组成部分，
     * 不是模式位；模式位是第四个字节 [mode]。
     */
    fun rasterHeader(widthBytes: Int, height: Int, mode: Int = 0): ByteArray = bytes(
        0x1D, 0x76, 0x30, mode,
        widthBytes and 0xFF, (widthBytes shr 8) and 0xFF,
        height and 0xFF, (height shr 8) and 0xFF,
    )
}
