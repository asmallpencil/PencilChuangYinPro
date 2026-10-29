package com.pencil.chuangyin.printer

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 外部来源图片进入图片打印页时的接收策略。
 *
 * - [PICK]：多张时先进入选择会话，由用户勾选后加入打印页（拍照连拍 / 外部批量导入）；
 * - [APPEND_ALL]：按传入顺序全部直接加入打印页（P 站图库已由用户显式多选，
 *   顺序即打印顺序，再让用户选一遍属于重复劳动）。
 */
enum class ShotTransferMode { PICK, APPEND_ALL }

/** 一次待接管的图片批次：文件列表 + 接收策略。 */
data class ShotTransferBatch(
    val files: List<File>,
    val mode: ShotTransferMode,
)

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
    private var pendingMode: ShotTransferMode = ShotTransferMode.PICK

    /** 投递一批待处理文件；[mode] 决定打印页如何接收（默认逐张选择）。 */
    fun offer(files: List<File>, mode: ShotTransferMode = ShotTransferMode.PICK) {
        if (files.isEmpty()) return
        synchronized(lock) {
            pending.addAll(files)
            // 后投递的策略覆盖先前的：下一次 drain 永远以最新意图为准。
            pendingMode = mode
        }
    }

    /** 取出并清空当前待处理文件。 */
    fun drain(): List<File> = synchronized(lock) {
        if (pending.isEmpty()) {
            emptyList()
        } else {
            pendingMode = ShotTransferMode.PICK
            pending.toList().also { pending.clear() }
        }
    }

    /** 取出并清空当前批次（文件 + 接收策略）；无待处理内容时返回 null。 */
    fun drainBatch(): ShotTransferBatch? = synchronized(lock) {
        if (pending.isEmpty()) {
            null
        } else {
            val batch = ShotTransferBatch(pending.toList(), pendingMode)
            pending.clear()
            pendingMode = ShotTransferMode.PICK
            batch
        }
    }

    companion object {
        /** 拍摄成品缓存目录名（cacheDir 下）。 */
        const val SHOT_DIR = "shot_shots"
    }
}
