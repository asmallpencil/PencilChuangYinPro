package com.pencil.chuangyin.feature.ai.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.agent.runtime.ToolCallScope
import com.pencil.chuangyin.printer.ImagePrintOptions
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterController
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片处理工具：对图片做缩放、旋转、裁剪、灰度、翻转、格式转换，产物保存到 agent 目录。
 *
 * 源图可位于可读根内任意位置（含 P 站下载），因此「下载图片 → 处理 → 打印」可一条链完成。
 */
/** edit_image 的处理操作（官方生成器会把枚举值列表写进 Schema 的 enum）。 */
enum class ImageOperation { RESIZE, THUMBNAIL, ROTATE, CROP, GRAYSCALE, FLIP, CONVERT }

/** edit_image 的输出格式。 */
enum class ImageFormat { PNG, JPEG, WEBP }

@Singleton
class ImageTools @Inject constructor(
    private val workspace: Workspace,
    private val scope: ToolCallScope,
    private val printer: PrinterController,
) {

    /**
     * 对图片做处理并保存为新文件（可选直接打印）。operation 取值：
     * RESIZE（用 width/height 或 scale 缩放）、THUMBNAIL（用 width 作最长边生成缩略图）、
     * ROTATE（用 degrees 顺时针旋转）、CROP（用 crop_x/crop_y/crop_width/crop_height 裁剪）、
     * GRAYSCALE（转灰度）、FLIP（用 flip_horizontal 选择水平/垂直翻转）、CONVERT（用 format 转换格式）。
     */
    @Tool
    suspend fun edit_image(
        @Param(description = "源图片路径；相对路径以 agent 工作目录为基准。") path: String,
        @Param(description = "处理操作。") operation: ImageOperation,
        @Param(description = "目标宽度像素（resize/thumbnail 用）。") width: Int?,
        @Param(description = "目标高度像素（resize 用）。") height: Int?,
        @Param(description = "缩放倍率（resize 用），如 0.5 表示缩小一半。") scale: Double?,
        @Param(description = "旋转角度，顺时针（rotate 用）。") degrees: Int?,
        @Param(description = "裁剪起点 X（crop 用，默认 0）。") crop_x: Int?,
        @Param(description = "裁剪起点 Y（crop 用，默认 0）。") crop_y: Int?,
        @Param(description = "裁剪宽度（crop 用，默认剩余宽度）。") crop_width: Int?,
        @Param(description = "裁剪高度（crop 用，默认剩余高度）。") crop_height: Int?,
        @Param(description = "翻转方向（flip 用）：true 水平，false 垂直，默认水平。") flip_horizontal: Boolean?,
        @Param(description = "输出格式。") format: ImageFormat?,
        @Param(description = "JPEG/WebP 质量 1..100，默认 90。") quality: Int?,
        @Param(description = "可选输出文件名（不含路径），默认自动命名。") file_name: String?,
        @Param(description = "生成后是否立即打印，默认 false。") print: Boolean?,
    ): ToolOutput {
        val op = operation.name.lowercase(Locale.US)
        if (op !in ToolParamValues.IMAGE_OPERATIONS) {
            return fileFailure("不支持的操作：$op，可选：${ToolParamValues.IMAGE_OPERATIONS.joinToString("、")}")
        }
        val source = runCatching { workspace.resolveForRead(path.trim()) }
            .getOrElse { return fileFailure(it.message ?: "图片路径无效") }
        if (source.isDirectory) return fileFailure("${workspace.displayPath(source)} 是目录，不是图片。")

        val decoded = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(source.absolutePath) }
            ?: return fileFailure("无法解码图片：${workspace.displayPath(source)}")

        scope.current?.reportProgress("正在处理图片")
        val edited = withContext(Dispatchers.IO) {
            runCatching {
                applyOperation(decoded, op, width, height, scale, degrees, crop_x, crop_y, crop_width, crop_height, flip_horizontal)
            }.getOrElse {
                decoded.recycle()
                return@withContext null
            }
        } ?: return fileFailure("图片处理失败（参数可能不合法）。")

        if (edited !== decoded) decoded.recycle()

        val resolvedFormat = resolveFormat(format, op)
        val target = resolveTarget(file_name?.trim()?.takeIf { it.isNotEmpty() }, resolvedFormat)
        val qualityValue = (quality ?: DEFAULT_QUALITY).coerceIn(1, 100)
        val saved = withContext(Dispatchers.IO) { save(edited, target, resolvedFormat, qualityValue) }
        val outWidth = edited.width
        val outHeight = edited.height
        edited.recycle()
        if (!saved) return fileFailure("图片保存失败：${workspace.displayPath(target)}")

        val builder = StringBuilder()
        builder.appendLine("已完成 $op，输出 ${workspace.displayPath(target)}（$outWidth×$outHeight）。")
        if (print == true) {
            builder.appendLine(printResult(target))
        }
        return ToolOutput(
            content = builder.toString().trim(),
            artifacts = listOf(
                ToolArtifact(
                    id = UUID.randomUUID().toString(),
                    kind = ToolArtifactKind.IMAGE,
                    path = target.absolutePath,
                    label = target.name,
                ),
            ),
        )
    }

    /** 按操作类型变换位图；返回的位图可能等于入参（无需变换时）或新实例。 */
    private fun applyOperation(
        bitmap: Bitmap,
        operation: String,
        width: Int?,
        height: Int?,
        scale: Double?,
        degrees: Int?,
        cropX: Int?,
        cropY: Int?,
        cropWidth: Int?,
        cropHeight: Int?,
        flipHorizontal: Boolean?,
    ): Bitmap = when (operation) {
        ToolParamValues.OP_GRAYSCALE -> toGrayscale(bitmap)
        ToolParamValues.OP_RESIZE -> resize(bitmap, width, height, scale)
        ToolParamValues.OP_THUMBNAIL -> thumbnail(bitmap, width)
        ToolParamValues.OP_ROTATE -> rotate(bitmap, degrees ?: 0)
        ToolParamValues.OP_CROP -> crop(bitmap, cropX ?: 0, cropY ?: 0, cropWidth, cropHeight)
        ToolParamValues.OP_FLIP -> flip(bitmap, flipHorizontal ?: true)
        else -> bitmap // convert 只改格式
    }

    private fun resize(bitmap: Bitmap, width: Int?, height: Int?, scale: Double?): Bitmap {
        val scaleValue = scale?.toFloat()
        val (targetWidth, targetHeight) = when {
            scaleValue != null && scaleValue > 0f ->
                (bitmap.width * scaleValue).roundToInt() to (bitmap.height * scaleValue).roundToInt()

            width != null && height != null -> width to height
            width != null -> width to (bitmap.height * width / bitmap.width.toFloat()).roundToInt()
            height != null -> (bitmap.width * height / bitmap.height.toFloat()).roundToInt() to height
            else -> bitmap.width to bitmap.height
        }
        val outWidth = targetWidth.coerceIn(MIN_EDGE, MAX_EDGE)
        val outHeight = targetHeight.coerceIn(MIN_EDGE, MAX_EDGE)
        if (outWidth == bitmap.width && outHeight == bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, outWidth, outHeight, true)
    }

    private fun thumbnail(bitmap: Bitmap, width: Int?): Bitmap {
        val maxEdge = (width ?: DEFAULT_THUMBNAIL_EDGE).coerceIn(MIN_EDGE, MAX_EDGE)
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge / longest.toFloat()
        val outWidth = (bitmap.width * ratio).roundToInt().coerceAtLeast(MIN_EDGE)
        val outHeight = (bitmap.height * ratio).roundToInt().coerceAtLeast(MIN_EDGE)
        return Bitmap.createScaledBitmap(bitmap, outWidth, outHeight, true)
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun crop(bitmap: Bitmap, cropX: Int, cropY: Int, cropWidth: Int?, cropHeight: Int?): Bitmap {
        val x = cropX.coerceIn(0, bitmap.width - 1)
        val y = cropY.coerceIn(0, bitmap.height - 1)
        val width = (cropWidth ?: (bitmap.width - x)).coerceIn(1, bitmap.width - x)
        val height = (cropHeight ?: (bitmap.height - y)).coerceIn(1, bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    private fun flip(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) postScale(-1f, 1f) else postScale(1f, -1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    private fun resolveFormat(format: ImageFormat?, operation: String): String {
        val raw = format?.name?.lowercase(Locale.US)
        if (raw != null && raw in ToolParamValues.IMAGE_FORMATS) return raw
        return ToolParamValues.FORMAT_PNG
    }

    private fun resolveTarget(fileName: String?, format: String): File {
        val extension = when (format) {
            ToolParamValues.FORMAT_JPEG -> ".jpg"
            ToolParamValues.FORMAT_WEBP -> ".webp"
            else -> ".png"
        }
        val base = fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: (FILE_PREFIX + System.currentTimeMillis())
        return workspace.uniqueTarget(base + extension)
    }

    private fun save(bitmap: Bitmap, target: File, format: String, quality: Int): Boolean = runCatching {
        target.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val compress = when (format) {
            ToolParamValues.FORMAT_JPEG -> Bitmap.CompressFormat.JPEG
            ToolParamValues.FORMAT_WEBP -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.PNG
        }
        FileOutputStream(target).use { out -> bitmap.compress(compress, quality, out) }
    }.getOrDefault(false)

    private suspend fun printResult(file: File): String {
        if (!printer.isConnected) return "未打印：打印机未连接，请提示用户到首页连接后再试。"
        scope.current?.reportProgress("正在打印图片")
        return when (val result = printer.printImagePages(
            pageCount = 1,
            options = ImagePrintOptions(),
            pageLoader = { BitmapFactory.decodeFile(file.absolutePath) },
        )) {
            is PrintResult.Success -> "已发送到打印机。"
            is PrintResult.Error -> "打印失败：${result.code}${result.detail?.let { "（$it）" } ?: ""}"
        }
    }

    private companion object {
        const val FILE_PREFIX = "edited_"
        const val DEFAULT_QUALITY = 90
        const val DEFAULT_THUMBNAIL_EDGE = 256
        const val MIN_EDGE = 1
        const val MAX_EDGE = 4096
    }
}
