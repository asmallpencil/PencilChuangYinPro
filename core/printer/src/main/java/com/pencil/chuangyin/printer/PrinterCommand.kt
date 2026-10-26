package com.pencil.chuangyin.printer

/**
 * 打印机指令常量与构建器（core:printer 内部实现层）。
 *
 * 协议结论（依据逆向 com.beeprt.sdk v1.1.0 已验证时序）：
 * 该机是“私有任务封装 + ESC/POS 数据指令”的混合协议——
 * - 必须先发送私有 [ENABLE_PRINTER]（开始打印任务），之后固件才会消费数据；
 *   缺了它，单独发 ESC/POS（包括 ESC J 走纸）打印机会完全无反应。
 * - 位图用标准 ESC/POS GS v 0（第三字节必须是 ASCII '0'=0x30，不是 0x00）。
 * - 走纸用 ESC J n；任务结束发私有 [STOP_PRINT_JOB]。
 * 完整时序：enable → setThickness → wakeup → ESC J 前导 → GS v 0 位图 → ESC J 收尾 → stop。
 */
internal object PrinterCommand {

    // ==================== ESC/POS ====================
    const val ESC = 0x1B
    const val GS = 0x1D

    /** GS v 的子指令选择符 '0'（ASCII 0x30，注意不是数值 0）——逆向确认。 */
    const val RASTER_SELECTOR_ZERO = 0x30
    const val RASTER_COMMAND = 0x76 // 'v'

    /** 光栅模式 m：0=普通（单宽单高）。1/2/3 为倍宽倍高，保留供后期扩展。 */
    const val MODE_NORMAL = 0

    /**
     * 光栅位图命令头：GS v 0 m xL xH yL yH（8 字节，小端长度域）。
     * x = 每行字节数（384 点 → 48），y = 本次位图高度（行数）。
     * 数据长度 = x * y，打印机按此精确取走后续字节。
     */
    fun printRasterHeader(mode: Int, widthBytes: Int, height: Int): ByteArray = byteArrayOf(
        GS.toByte(),
        RASTER_COMMAND.toByte(),
        RASTER_SELECTOR_ZERO.toByte(),
        (mode and 0xFF).toByte(),
        (widthBytes and 0xFF).toByte(),
        ((widthBytes shr 8) and 0xFF).toByte(),
        (height and 0xFF).toByte(),
        ((height shr 8) and 0xFF).toByte(),
    )

    /**
     * 走纸指令：ESC J n（打印缓冲内容并走纸 n 点，n ∈ 0..255）。
     * 总点数超过 255 时自动拆成多条，返回拼接后的完整字节序列。
     */
    fun feedDots(totalDots: Int): ByteArray {
        var remaining = totalDots.coerceAtLeast(0)
        val out = ArrayList<Byte>()
        while (remaining > 0) {
            val n = minOf(remaining, FEED_MAX)
            out.add(ESC.toByte())
            out.add(0x4A)
            out.add((n and 0xFF).toByte())
            remaining -= n
        }
        return ByteArray(out.size) { out[it] }
    }

    // ==================== 私有任务控制（0x10 0xFF 前缀，逆向确认，必须发送） ====================
    /** 开始打印任务（使能）：0x10 0xFF 0xF1 0x02 */
    val ENABLE_PRINTER = byteArrayOf(0x10, 0xFF.toByte(), 0xF1.toByte(), 0x02)

    /** 使能附加序列：0x1F 0xB2 0x10（紧跟 ENABLE_PRINTER 发送）。 */
    val ENABLE_PRINTER_EXTRA = byteArrayOf(0x1F, 0xB2.toByte(), 0x10)

    /** 结束打印任务：0x10 0xFF 0xF1 0x45 */
    val STOP_PRINT_JOB = byteArrayOf(0x10, 0xFF.toByte(), 0xF1.toByte(), 0x45)

    /** 唤醒序列：12 个 NUL。 */
    val WAKEUP = ByteArray(WAKEUP_ZERO_COUNT)

    /**
     * ESC/POS 复位指令：ESC @（1B 40）。
     * 每次打印任务开始时发送，清零打印机的数据模式与行缓冲，
     * 杜绝上一个任务残留的光栅解析状态导致下一张图错位/乱码。
     */
    val INIT_PRINTER = byteArrayOf(ESC.toByte(), 0x40)

    /** 设置浓度：0x10 0xFF 0x10 0x00 n（n ∈ 1..5）。 */
    fun setThickness(thickness: Int): ByteArray = byteArrayOf(
        0x10,
        0xFF.toByte(),
        0x10,
        0x00,
        (thickness.coerceIn(THICKNESS_MIN, THICKNESS_MAX) and 0xFF).toByte(),
    )

    // ==================== 异步状态上报（机型私有，仅接收解析，不主动发送） ====================
    const val STATUS_PREFIX = 0xFF
    const val STATUS_OUT_OF_PAPER = 0x01
    const val STATUS_COVER_OPEN = 0x02
    const val STATUS_OVERHEAT = 0x03
    const val STATUS_LOW_BATTERY = 0x04

    /** 打印成功回执字节。 */
    const val PRINT_SUCCESS_BYTE = 0xAA

    /** 主动查询打印机状态：0x10 0xFF 0x40（过热等待期轮询，诱导故障状态持续上报）。 */
    val QUERY_STATUS = byteArrayOf(0x10, 0xFF.toByte(), 0x40)

    /**
     * 查询电池电量：0x10 0xFF 0x50 0xF1（逆向原厂 printerBatteryVolBY → d.t()）。
     * 响应为 2 字节帧，第 2 字节为电量原始值。
     */
    val QUERY_BATTERY = byteArrayOf(0x10, 0xFF.toByte(), 0x50.toByte(), 0xF1.toByte())

    /**
     * 连接保活轮询周期：定期查询状态刷新故障显示（故障恢复后不再上报，显示自动消除）。
     * 3s 周期配合 7s TTL：关盖/恢复后最坏约 7 秒内显示自愈。
     */
    const val STATE_POLL_INTERVAL_MS = 3_000L

    /** 故障状态显示保留时长：超过该时长未再次上报则视为已恢复（>2 个轮询周期防丢包误清）。 */
    const val STATUS_TTL_MS = 7_000L

    /** 电量查询节奏：每 N 次状态轮询附带一次电量查询（3s × 5 = 15s）。 */
    const val BATTERY_POLL_EVERY_N_TICKS = 5

    /** 电量响应等待超时：超过后放弃本次解析窗口，避免误吞后续杂散帧。 */
    const val BATTERY_REPLY_TIMEOUT_MS = 3_000L

    /** 用户取消时紧急停止指令的连发次数（提高固件在嘈杂字节流中识别 STOP 的概率）。 */
    const val EMERGENCY_STOP_REPEAT = 3

    /** 紧急停止连发间隔。 */
    const val EMERGENCY_STOP_GAP_MS = 60L

    const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    const val PRINT_WIDTH_PX = 384

    /** 单条 GS v 0 光栅命令的安全条带高度（廉价打印机缓冲有限，按 255 行分片最稳妥）。 */
    const val RASTER_BAND_HEIGHT = 255

    /**
     * 文本分页渲染的目标页高（像素）。长文本按行切成多页位图逐页打印，
     * 避免一次性生成超高 Bitmap 导致 OOM；页高取条带高度的整数倍附近，编码自然对齐。
     */
    const val PAGE_TARGET_HEIGHT_PX = 768

    /**
     * 超长文本分段字符数：先在“换行”边界附近把全文切成若干段分别排版，
     * 保证 17 万字也不会构造超大 StaticLayout；各段在同一打印任务内连续输出，无缝衔接。
     */
    const val TEXT_SEGMENT_CHARS = 8_000

    /**
     * 过热观察窗口（ms）：固件自主散热，App 仅在此窗口内短暂上报“散热中”状态供 UI 提示，
     * 不再阻塞打印。窗口过短会抖动，过长会让早已恢复的 UI 红字长时间残留。
     */
    const val OVERHEAT_OBSERVE_WINDOW_MS = 3_000L

    /** 相邻条带之间的发送间隔，给慢速打印机留出消费时间。 */
    const val RASTER_BAND_DELAY_MS = 20L

    /** 私有指令之间的间隔（贴近逆向实现，仅极短停顿）。 */
    const val COMMAND_GAP_MS = 2L

    const val THICKNESS_MIN = 1
    const val THICKNESS_MAX = 5
    const val DEFAULT_THICKNESS = 1

    /** 二值化阈值：逆向推荐固定 128；浓度由私有 setThickness 控制。 */
    const val DEFAULT_THRESHOLD = 128

    const val FEED_BEFORE = 10
    const val FEED_AFTER = 100
    const val TEST_FEED_DOTS = 60
    const val FEED_AFTER_MIN = 20
    const val FEED_AFTER_MAX = 200
    const val FEED_MAX = 255
    const val WAKEUP_ZERO_COUNT = 12
    const val CHUNK_SIZE = 1024
    const val CHUNK_DELAY_MS = 1L

    /** 所有 socket 策略合计的连接超时时长。 */
    const val CONNECT_TIMEOUT_MS = 15_000L

    /** 单个 socket 策略的连接尝试时长。 */
    const val CONNECT_ATTEMPT_TIMEOUT_MS = 4_000L
}
