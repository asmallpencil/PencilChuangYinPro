package com.pencil.chuangyin.core.scanner

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.QRCodeDetector

/**
 * 二维码解码器。
 *
 * 用于「扫二维码导入代理配置」：用户从相册选一张二维码图片，这里负责解码。
 * 使用 OpenCV 的 QRCodeDetector，避免为了一个小功能引入单独的扫码库。
 */
interface QrCodeDecoder {

    /** 解码位图中的二维码；失败或没有二维码时返回 null。 */
    fun decode(bitmap: Bitmap): String?
}

@Singleton
class OpenCvQrCodeDecoder @Inject constructor() : QrCodeDecoder {

    private val available: Boolean = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)

    override fun decode(bitmap: Bitmap): String? {
        if (!available || bitmap.isRecycled) return null
        val scaled = scaleDown(bitmap)
        val rgba = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(scaled, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val detector = QRCodeDetector()
            val multi = mutableListOf<String>()
            val multiOk = runCatching { detector.detectAndDecodeMulti(gray, multi) }.getOrDefault(false)
            if (multiOk) {
                multi.firstOrNull { it.isNotBlank() }
            } else {
                runCatching { detector.detectAndDecode(gray) }.getOrNull()?.takeIf { it.isNotBlank() }
            }
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { rgba.release() }
            runCatching { gray.release() }
            if (scaled !== bitmap) runCatching { scaled.recycle() }
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE_PX) return bitmap
        val scale = MAX_EDGE_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private companion object {
        // 二维码在 1600px 内已足够识别；过大只会拖慢解码。
        const val MAX_EDGE_PX = 1600
    }
}
