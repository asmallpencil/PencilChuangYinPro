package com.pencil.chuangyin.printer

import javax.inject.Inject
import javax.inject.Singleton

/** 外部来源（P 站小说）→ 文本打印页的一次性文本载荷。 */
data class TextTransferPayload(
    val title: String,
    val content: String,
)

/**
 * 外部页面 → 文本打印页 的一次性文本传递总线（与 [ShotTransferBus] 同模式）。
 *
 * 来源页把标题+正文 [offer]；文本打印页 ViewModel 进入时 [drain] 消费一次（drain 即清空），
 * 避免旋转/重组导致文本被重复载入。
 *
 * 放在 core:printer：feature:pixiv 与 feature:text 都只可依赖 core 层，双方经此总线解耦。
 */
@Singleton
class TextTransferBus @Inject constructor() {

    private val lock = Any()
    private var pending: TextTransferPayload? = null

    fun offer(payload: TextTransferPayload) {
        synchronized(lock) { pending = payload }
    }

    /** 取出并清空当前待处理文本。 */
    fun drain(): TextTransferPayload? = synchronized(lock) {
        pending.also { pending = null }
    }
}
