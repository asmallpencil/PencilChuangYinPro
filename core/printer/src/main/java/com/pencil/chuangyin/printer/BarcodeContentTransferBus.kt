package com.pencil.chuangyin.printer

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通用扫描器 → 条码打印页 的一次性内容传递总线（与 [TextTransferBus] 同模式）。
 *
 * 扫描结果页点「填入条码内容」时 [offer]；条码页 ViewModel 常驻订阅 [pending]，
 * 读取后立即 [consume]，避免重组或旋转导致内容重复填入。
 */
@Singleton
class BarcodeContentTransferBus @Inject constructor() {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(text: String) {
        _pending.value = text
    }

    fun consume() {
        _pending.value = null
    }
}
