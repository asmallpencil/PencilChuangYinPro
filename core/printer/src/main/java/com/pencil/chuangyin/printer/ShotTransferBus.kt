package com.pencil.chuangyin.printer

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 拍照打印 → 图片打印页 的一次性结果传递总线。
 *
 * 拍照页确认后把成品位图写入缓存文件并 [offer]；
 * 图片打印页 ViewModel 在进入时 [drain] 消费一次（drain 即清空），
 * 避免旋转/重组导致同批文件被重复加载。
 *
 * 放在 core:printer 而非 app 层：feature:camera 与 feature:image 都只可依赖 core 层，
 * 双方经此总线解耦，互不直接依赖。
 */
@Singleton
class ShotTransferBus @Inject constructor() {

    private val lock = Any()
    private val pending = ArrayDeque<File>()

    fun offer(files: List<File>) {
        synchronized(lock) { pending.addAll(files) }
    }

    /** 取出并清空当前待处理文件。 */
    fun drain(): List<File> = synchronized(lock) {
        if (pending.isEmpty()) emptyList() else pending.toList().also { pending.clear() }
    }

    companion object {
        /** 拍摄成品缓存目录名（cacheDir 下）。 */
        const val SHOT_DIR = "shot_shots"
    }
}
