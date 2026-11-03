package com.pencil.chuangyin.feature.barcode.scan

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * CameraX 实时帧 → ZXing 解码。
 *
 * 高识别率的几个关键点：
 * 1. 只取 YUV 的 Y 平面（灰度已够用），先把行跨距压成连续数组，再按 `rotationDegrees` 做坐标映射，
 *    不做整帧位图旋转，避免每帧大块分配与 GC 抖动；
 * 2. 每帧先按当前方向解码，一维码再补一次 90° 方向（横放/竖放都能扫）；
 * 3. `TRY_HARDER` + `ALSO_INVERTED`，兼容反色码、屏幕上翻拍的码；
 * 4. 全画幅解码（不只取景框），配合约 14fps 节流，"随手甩过"也能命中；
 * 5. 相同内容 1.2s 内去重，避免连续回调把结果页反复顶起。
 */
class BarcodeLiveAnalyzer(
    private val modeProvider: () -> ScanMode,
    private val enabledProvider: () -> Boolean,
    private val onDecoded: (text: String, formatName: String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val qrFormats = listOf(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.AZTEC,
        BarcodeFormat.PDF_417,
    )
    private val barFormats = listOf(
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
        BarcodeFormat.EAN_8,
        BarcodeFormat.EAN_13,
        BarcodeFormat.CODE_39,
        BarcodeFormat.CODE_93,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODABAR,
        BarcodeFormat.ITF,
        BarcodeFormat.RSS_14,
        BarcodeFormat.RSS_EXPANDED,
    )

    private var lastAttemptAt = 0L
    private var lastHitText: String? = null
    private var lastHitAt = 0L
    private var hintsMode: ScanMode? = null
    private val reader = MultiFormatReader()

    /** 复用的灰度缓冲：分析是单线程串行的，可以安全复用同一块内存。 */
    private var luma = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        try {
            if (!enabledProvider()) return
            val now = SystemClock.uptimeMillis()
            if (now - lastAttemptAt < FRAME_INTERVAL_MS) return
            lastAttemptAt = now

            val plane = image.planes.firstOrNull() ?: return
            if (image.width <= 0 || image.height <= 0) return
            val data = extractLuminance(
                buffer = plane.buffer,
                width = image.width,
                height = image.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
            ) ?: return
            val rotation = image.imageInfo.rotationDegrees
            val mode = modeProvider()
            applyHints(mode)

            // 主方向：竖屏时 rotation=90，一维码扫描行会被转成竖直方向，因此需要补一次反向 90°；
            // 二维码本身不依赖方向，只解一次，把每帧算力留给下一帧（识别更快）。
            val hit = decode(data, image.width, image.height, rotation, mode)
                ?: if (mode == ScanMode.Barcode) {
                    decode(data, image.width, image.height, (rotation + 270) % 360, mode)
                } else {
                    null
                } ?: return

            val text = hit.first
            if (text.isBlank()) return
            if (text == lastHitText && now - lastHitAt < HIT_DEDUPE_MS) return
            lastHitText = text
            lastHitAt = now
            onDecoded(text, hit.second)
        } catch (_: Throwable) {
            // 单帧解码失败不影响下一帧；上层会继续取流。
        } finally {
            image.close()
        }
    }

    /** 把 Y 平面（可能带行跨距）压成连续灰度数组；缓冲区不足时返回 null。 */
    private fun extractLuminance(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
    ): ByteArray? {
        val size = width * height
        if (luma.size < size) luma = ByteArray(size)
        val out = luma
        val limit = buffer.limit()

        // 常见情况：行跨距等于宽度、像素跨距 1，一次 bulk 拷贝即可。
        if (pixelStride == 1 && rowStride == width) {
            buffer.position(0)
            buffer.get(out, 0, min(size, limit))
            return out
        }

        for (y in 0 until height) {
            val start = y * rowStride
            if (start >= limit) break
            val available = min(width * pixelStride, limit - start)
            if (pixelStride == 1) {
                buffer.position(start)
                buffer.get(out, y * width, available)
            } else {
                val count = min(width, available / pixelStride)
                for (x in 0 until count) {
                    out[y * width + x] = buffer.get(start + x * pixelStride)
                }
            }
        }
        return out
    }

    private fun applyHints(mode: ScanMode) {
        if (hintsMode == mode) return
        hintsMode = mode
        val formats = when (mode) {
            ScanMode.Qr -> qrFormats
            ScanMode.Barcode -> barFormats
        }
        reader.setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to formats,
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.ALSO_INVERTED to true,
                DecodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
            ),
        )
    }

    /** @return 文本 + 格式名；未识别返回 null。 */
    private fun decode(
        data: ByteArray,
        dataWidth: Int,
        dataHeight: Int,
        rotation: Int,
        mode: ScanMode,
    ): Pair<String, String>? = try {
        val source = RotatedPlanarLuminanceSource(
            data = data,
            dataWidth = dataWidth,
            dataHeight = dataHeight,
            rotation = rotation,
        )
        val binary = BinaryBitmap(HybridBinarizer(source))
        val result = reader.decodeWithState(binary)
        val text = result.text.orEmpty()
        if (text.isBlank()) null else text to (result.barcodeFormat?.name ?: "UNKNOWN")
    } catch (_: Throwable) {
        reader.reset()
        null
    }

    /**
     * 灰度的旋转视图：把 `(x, y) → (dx, dy)` 的映射放进取行/取矩阵函数，
     * 任意旋转方向都不需要额外旋转整帧，且数组访问比 ByteBuffer 逐点读取快得多。
     */
    private class RotatedPlanarLuminanceSource(
        private val data: ByteArray,
        private val dataWidth: Int,
        private val dataHeight: Int,
        rotation: Int,
    ) : LuminanceSource(
        if (rotation % 180 == 0) dataWidth else dataHeight,
        if (rotation % 180 == 0) dataHeight else dataWidth,
    ) {

        private val normalized = ((rotation % 360) + 360) % 360

        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val out = row?.takeIf { it.size >= width } ?: ByteArray(width)
            val rowLength = width
            when (normalized) {
                90 -> {
                    for (x in 0 until rowLength) {
                        out[x] = data[(dataHeight - 1 - x) * dataWidth + y]
                    }
                }

                180 -> {
                    val base = (dataHeight - 1 - y) * dataWidth
                    for (x in 0 until rowLength) {
                        out[x] = data[base + (dataWidth - 1 - x)]
                    }
                }

                270 -> {
                    for (x in 0 until rowLength) {
                        out[x] = data[x * dataWidth + (dataWidth - 1 - y)]
                    }
                }

                else -> System.arraycopy(data, y * dataWidth, out, 0, min(rowLength, dataWidth))
            }
            return out
        }

        override fun getMatrix(): ByteArray {
            val out = ByteArray(width * height)
            val row = ByteArray(width)
            for (y in 0 until height) {
                getRow(y, row)
                System.arraycopy(row, 0, out, y * width, width)
            }
            return out
        }
    }

    private companion object {
        /** 约 14fps，兼顾识别速度与功耗。 */
        const val FRAME_INTERVAL_MS = 70L
        const val HIT_DEDUPE_MS = 1200L
    }
}
