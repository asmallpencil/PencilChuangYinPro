package com.pencil.chuangyin.printer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 低层蓝牙连接契约：仅负责字节收发与状态解码，不包含打印编排。
 * 上层 (PrinterController) 依赖本接口组织打印任务。
 */
interface PrinterConnection {
    val isConnected: Boolean

    /** 当前已连接设备名（未连接为 null）。 */
    val deviceName: String?

    /** 全局共享的连接状态（未连接/连接中/已连接）。 */
    val connectionState: StateFlow<ConnectionState>

    val statusEvents: Flow<PrinterStatus>

    /**
     * 设备实时状态快照：连接层周期轮询维护（故障自动过期消除 + 电量百分比）。
     * UI 展示"当前故障/电量"一律以本快照为准；[statusEvents] 仅作瞬时事件（如 SnackBar）。
     */
    val deviceState: StateFlow<PrinterDeviceState>

    fun connect(macAddress: String, deviceName: String, callback: (ConnectionResult) -> Unit)

    fun disconnect()

    /** 写入裸字节；失败时返回原因，便于上层展示与排障。 */
    fun write(bytes: ByteArray): WriteOutcome = write(bytes, 0, bytes.size)

    /** 区域写入（取消粒度用，避免大块位图发送期间无法响应用户取消）。 */
    fun write(bytes: ByteArray, offset: Int, length: Int): WriteOutcome

    /**
     * 主动下发私有状态查询（10 FF 40），诱导打印机在故障期间持续上报状态码，
     * 状态结果仍经 [statusEvents] 推送。主要用于过热冷却判定。
     */
    fun requestStatus()

    /**
     * 标记打印任务进行中：期间状态/电量轮询器不再下发查询字节，
     * 防止 QUERY_STATUS/QUERY_BATTERY 命令字节被注入 GS v 0 光栅数据流
     * 导致打印机把命令字节当像素解析（错位/乱码/色块丢失）。
     */
    fun setPrinting(printing: Boolean)

    /**
     * 紧急停止：由独立线程立即向打印机连发 STOP_PRINT_JOB（10 FF F1 45），
     * 不等打印任务的安全边界——用于用户在大批量输出中快速中止，尽量阻止固件
     * 继续消费已缓冲数据（疯狂吐纸）。
     */
    fun emergencyStop()
}

/** 蓝牙写操作结果（携带技术原因，供 UI/日志定位“打印机无动作”类问题）。 */
sealed interface WriteOutcome {
    data object Success : WriteOutcome
    data class Failure(val reason: String) : WriteOutcome
}