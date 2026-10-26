package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.ai.AgentTool
import com.pencil.chuangyin.desktop.ai.ToolContext
import com.pencil.chuangyin.desktop.ai.ToolOutput
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * 图片处理工具：对图片做缩放、旋转、裁剪、灰度、翻转、格式转换，产物保存到 agent 目录。
 *
 * 源图可位于可读根内任意位置（含 P 站下载、用户桌面），因此「下载图片 → 处理 → 打印」可一条链完成。
 * 已连接蓝牙打印机时 `print` 参数会立即下发打印，未连接则仅保存文件并说明。
 */
class ImageTools(private val workspace: Workspace) {

    val tools: List<AgentTool> = listOf(
        agentTool(
            name = ToolNames.EDIT_IMAGE,
            description = "对图片做处理并保存为新文件（可选立即打印）。operation 取值：" +
                "resize（用 width/height 或 scale 缩放）、thumbnail（用 width 作最长边生成缩略图）、" +
                "rotate（用 degrees 顺时针旋转）、crop（用 crop_x/crop_y/crop_width/crop_height 裁剪）、" +
                "grayscale（转灰度）、flip（用 flip_horizontal 选择水平/垂直翻转）、convert（用 format 转换格式）。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.PATH, ToolParams.OPERATION),
                properties = mapOf(
                    ToolParams.PATH to ToolSchema.string("源图片路径；相对路径以 agent 工作目录为基准。"),
                    ToolParams.OPERATION to ToolSchema.string(
                        "处理操作。",
                        ToolParamValues.IMAGE_OPERATIONS,
                    ),
                    ToolParams.WIDTH to ToolSchema.integer("目标宽度像素（resize/thumbnail 用）。"),
                    ToolParams.HEIGHT to ToolSchema.integer("目标高度像素（resize 用）。"),
                    ToolParams.SCALE to ToolSchema.number("缩放倍率（resize 用），如 0.5 表示缩小一半。"),
                    ToolParams.DEGREES to ToolSchema.integer("旋转角度，顺时针（rotate 用）。"),
                    ToolParams.CROP_X to ToolSchema.integer("裁剪起点 X（crop 用，默认 0）。"),
                    ToolParams.CROP_Y to ToolSchema.integer("裁剪起点 Y（crop 用，默认 0）。"),
                    ToolParams.CROP_WIDTH to ToolSchema.integer("裁剪宽度（crop 用，默认剩余宽度）。"),
                    ToolParams.CROP_HEIGHT to ToolSchema.integer("裁剪高度（crop 用，默认剩余高度）。"),
                    ToolParams.FLIP_HORIZONTAL to ToolSchema.boolean("翻转方向（flip 用）：true 水平，false 垂直，默认水平。"),
                    ToolParams.FORMAT to ToolSchema.string("输出格式。", ToolParamValues.IMAGE_FORMATS),
                    ToolParams.QUALITY to ToolSchema.integer("JPEG 质量 1..100，默认 90。"),
                    ToolParams.FILE_NAME to ToolSchema.string("可选输出文件名（不含路径），默认自动命名。"),
                    ToolParams.PRINT_AFTER_DRAW to ToolSchema.boolean(
                        "生成后是否立即打印（未连接打印机时仅保存文件），默认 false。",
                    ),
                ),
            ),
        ) { args, context -> editImage(args, context) },
    )

    private suspend fun editImage(args: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val path = args.stringOrNull(ToolParams.PATH) ?: return@withContext fileFailure("缺少参数 path")
        val op = args.stringOrNull(ToolParams.OPERATION)?.lowercase(Locale.US)
            ?: return@withContext fileFailure("缺少参数 operation")
        if (op !in ToolParamValues.IMAGE_OPERATIONS) {
            return@withContext fileFailure("不支持的操作：$op，可选：${ToolParamValues.IMAGE_OPERATIONS.joinToString("、")}")
        }
        val source = runCatching { workspace.resolveForRead(path) }
            .getOrElse { return@withContext fileFailure(it.message ?: "图片路径无效") }
        if (source.isDirectory) return@withContext fileFailure("${workspace.displayPath(source)} 是目录，不是图片。")

        val decoded = runCatching { ImageIO.read(source) }.getOrNull()
            ?: return@withContext fileFailure("无法解码图片：${workspace.displayPath(source)}")

        context.reportProgress("正在处理图片")
        val edited = runCatching {
            applyOperation(
                decoded,
                op,
                args.intOrNull(ToolParams.WIDTH),
                args.intOrNull(ToolParams.HEIGHT),
                args.doubleOrNull(ToolParams.SCALE),
                args.intOrNull(ToolParams.DEGREES),
                args.intOrNull(ToolParams.CROP_X),
                args.intOrNull(ToolParams.CROP_Y),
                args.intOrNull(ToolParams.CROP_WIDTH),
                args.intOrNull(ToolParams.CROP_HEIGHT),
                args.booleanOr(ToolParams.FLIP_HORIZONTAL, true),
            )
        }.getOrElse { return@withContext fileFailure("图片处理失败（参数可能不合法）：${it.message}") }

        val resolvedFormat = resolveFormat(args.stringOrNull(ToolParams.FORMAT))
        val requested = args.stringOrNull(ToolParams.FILE_NAME)
        val quality = args.intOr(ToolParams.QUALITY, DEFAULT_QUALITY).coerceIn(1, 100)
        val target = writableTarget(resolvedFormat, requested)
        val saved = ImageOps.save(edited, target, resolvedFormat, quality)
            ?: return@withContext fileFailure("图片保存失败：${workspace.displayPath(target)}")

        val builder = StringBuilder()
        builder.appendLine("已完成 $op，输出 ${workspace.displayPath(saved)}（${edited.width}×${edited.height}）。")
        if (resolvedFormat == ToolParamValues.FORMAT_WEBP) {
            builder.appendLine("说明：桌面端暂无 WebP 编码器，已按 PNG 输出。")
        }
        if (args.booleanOr(ToolParams.PRINT_AFTER_DRAW, false)) {
            builder.appendLine(PrintBridge.printImageFile(saved, "图片"))
        }
        ToolOutput(
            content = builder.toString().trim(),
            artifacts = listOf(imageArtifact(saved)),
        )
    }

    /** 按操作类型变换位图；返回的位图可能等于入参（无需变换时）或新实例。 */
    private fun applyOperation(
        image: BufferedImage,
        operation: String,
        width: Int?,
        height: Int?,
        scale: Double?,
        degrees: Int?,
        cropX: Int?,
        cropY: Int?,
        cropWidth: Int?,
        cropHeight: Int?,
        flipHorizontal: Boolean,
    ): BufferedImage = when (operation) {
        ToolParamValues.OP_GRAYSCALE -> ImageOps.grayscale(image)
        ToolParamValues.OP_RESIZE -> resize(image, width, height, scale)
        ToolParamValues.OP_THUMBNAIL -> thumbnail(image, width)
        ToolParamValues.OP_ROTATE -> ImageOps.rotate(image, degrees ?: 0)
        ToolParamValues.OP_CROP -> crop(image, cropX ?: 0, cropY ?: 0, cropWidth, cropHeight)
        ToolParamValues.OP_FLIP -> ImageOps.flip(image, flipHorizontal)
        else -> image // convert 只改格式
    }

    private fun resize(image: BufferedImage, width: Int?, height: Int?, scale: Double?): BufferedImage {
        val (targetWidth, targetHeight) = when {
            scale != null && scale > 0.0 ->
                (image.width * scale).roundToInt() to (image.height * scale).roundToInt()

            width != null && height != null -> width to height
            width != null -> width to (image.height * width / image.width.toDouble()).roundToInt()
            height != null -> (image.width * height / image.height.toDouble()).roundToInt() to height
            else -> image.width to image.height
        }
        val outWidth = targetWidth.coerceIn(MIN_EDGE, MAX_EDGE)
        val outHeight = targetHeight.coerceIn(MIN_EDGE, MAX_EDGE)
        if (outWidth == image.width && outHeight == image.height) return image
        return ImageOps.scale(image, outWidth, outHeight)
    }

    private fun thumbnail(image: BufferedImage, width: Int?): BufferedImage {
        val maxEdge = (width ?: DEFAULT_THUMBNAIL_EDGE).coerceIn(MIN_EDGE, MAX_EDGE)
        val longest = maxOf(image.width, image.height)
        if (longest <= maxEdge) return image
        val ratio = maxEdge / longest.toDouble()
        val outWidth = (image.width * ratio).roundToInt().coerceAtLeast(MIN_EDGE)
        val outHeight = (image.height * ratio).roundToInt().coerceAtLeast(MIN_EDGE)
        return ImageOps.scale(image, outWidth, outHeight)
    }

    private fun crop(image: BufferedImage, cropX: Int, cropY: Int, cropWidth: Int?, cropHeight: Int?): BufferedImage {
        val x = cropX.coerceIn(0, image.width - 1)
        val y = cropY.coerceIn(0, image.height - 1)
        val width = (cropWidth ?: (image.width - x)).coerceIn(1, image.width - x)
        val height = (cropHeight ?: (image.height - y)).coerceIn(1, image.height - y)
        return ImageOps.crop(image, x, y, width, height)
    }

    private fun resolveFormat(raw: String?): String {
        val value = raw?.lowercase(Locale.US)
        if (value != null && value in ToolParamValues.IMAGE_FORMATS) return value
        return ToolParamValues.FORMAT_PNG
    }

    private fun writableTarget(format: String, fileName: String?): File {
        val extension = when (format) {
            ToolParamValues.FORMAT_JPEG -> ".jpg"
            ToolParamValues.FORMAT_WEBP -> ".webp"
            else -> ".png"
        }
        val base = fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: (FILE_PREFIX + System.currentTimeMillis())
        return workspace.uniqueTarget(base + extension)
    }

    private companion object {
        const val FILE_PREFIX = "edited_"
        const val DEFAULT_QUALITY = 90
        const val DEFAULT_THUMBNAIL_EDGE = 256
        const val MIN_EDGE = 1
        const val MAX_EDGE = 4096
    }
}
