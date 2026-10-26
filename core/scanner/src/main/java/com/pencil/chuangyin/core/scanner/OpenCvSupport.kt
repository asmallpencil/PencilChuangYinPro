package com.pencil.chuangyin.core.scanner

import org.opencv.core.Mat

/** OpenCV [Mat] 作用域释放：块结束后无论异常与否都 release，杜绝原生内存泄漏。 */
internal inline fun <T : Mat, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        runCatching { release() }
    }
}

internal inline fun <R> mat(block: () -> Mat, action: (Mat) -> R): R {
    val m = block()
    try {
        return action(m)
    } finally {
        runCatching { m.release() }
    }
}

/** 取不小于 [min] 的最近奇数（OpenCV 核/自适应窗口尺寸要求正奇数）。 */
internal fun oddCeil(value: Int, min: Int = 3): Int {
    val v = value.coerceAtLeast(min)
    return if (v % 2 == 0) v + 1 else v
}
