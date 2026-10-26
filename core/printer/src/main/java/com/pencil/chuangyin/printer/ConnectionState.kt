package com.pencil.chuangyin.printer

/**
 * 打印机连接状态（全局单例共享）：所有页面通过 [PrinterController.connectionState]
 * 观察同一份连接事实，避免各页面各自维护“是否已连接”导致状态漂移。
 */
sealed interface ConnectionState {

    /** 未连接。 */
    data object Disconnected : ConnectionState

    /** 正在连接某台设备。 */
    data class Connecting(val device: PrinterDevice) : ConnectionState

    /** 已连接。 */
    data class Connected(val device: PrinterDevice) : ConnectionState
}
