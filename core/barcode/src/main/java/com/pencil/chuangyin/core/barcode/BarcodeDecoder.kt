package com.pencil.chuangyin.core.barcode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import kotlin.math.max
import kotlin.math.roundToInt

/** 一次成功解码的结果。 */
data class DecodedBarcode(
    /** 条码承载的原始文本。 */
    val text: String,
    /** 格式名（与 ZXing [com.google.zxing.BarcodeFormat] 一致，如 QR_CODE / EAN_13）。 */
    val formatName: String,
    /** ZXing 返回的定位点（图像像素坐标），供调试/叠加提示使用。 */
    val resultPoints: List<PointF>,
)

/**
 * 通用条码解码：接受任意位图，自动尝试全部 ZXing 支持的格式。
 *
 * 使用 RGBLuminanceSource + HybridBinarizer + TRY_HARDER，
 * 对手机屏幕翻拍、轻微倾斜、低对比度的条码有较好的识别率。
 */
object BarcodeDecoder {

    /** 解码相册图片时允许的最大长边（避免超大原图 OOM）。 */
    private const val MAX_IMAGE_EDGE = 2000

    /** 解码一张已加载的位图。识别失败或异常时返回 null。 */
    fun decode(bitmap: Bitmap): DecodedBarcode? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return runCatching {
            val source = RGBLuminanceSource(width, height, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            val hints = mapOf<DecodeHintType, Any>(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
            )
            val result = MultiFormatReader().apply { setHints(hints) }.decodeWithState(binary)
            result.toDecodedBarcode()
        }.getOrNull()
    }

    /**
     * 从内容 URI 读取图片并解码。
     *
     * 先只读边界按长边 ≤ [MAX_IMAGE_EDGE] 下采样，再读取 EXIF 方向做像素归一，
     * 避免相册原图（数千万像素）直接整张载入。
     */
    fun decodeFromUri(context: Context, uri: Uri): DecodedBarcode? {
        val bitmap = loadScaledBitmap(context, uri) ?: return null
        val decoded = decode(bitmap)
        runCatching { bitmap.recycle() }
        return decoded
    }

    /** 读取图片：边界采样 + EXIF 旋转 + 超长边缩放。 */
    private fun loadScaledBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_EDGE)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        val degrees = readExifRotation(resolver, uri)
        val rotated = if (degrees != 0f) {
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                Matrix().apply { postRotate(degrees) },
                true,
            )
        } else {
            decoded
        }
        if (rotated !== decoded) decoded.recycle()

        val longest = max(rotated.width, rotated.height)
        if (longest <= MAX_IMAGE_EDGE) {
            rotated
        } else {
            val ratio = MAX_IMAGE_EDGE / longest.toFloat()
            val scaled = Bitmap.createScaledBitmap(
                rotated,
                (rotated.width * ratio).roundToInt().coerceAtLeast(1),
                (rotated.height * ratio).roundToInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== rotated) rotated.recycle()
            scaled
        }
    }.getOrNull()

    /** EXIF 方向 → 顺时针旋转角度。 */
    private fun readExifRotation(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): Float = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val orientation = ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)

    /** 采样倍率：保证下采样后的长边仍不小于目标值的一半。 */
    private fun calculateInSampleSize(width: Int, height: Int, targetEdge: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= targetEdge) {
            sample *= 2
        }
        return sample
    }

    private fun Result.toDecodedBarcode(): DecodedBarcode = DecodedBarcode(
        text = text.orEmpty(),
        formatName = barcodeFormat?.name ?: "UNKNOWN",
        resultPoints = resultPoints?.map { PointF(it.x, it.y) }.orEmpty(),
    )
}
