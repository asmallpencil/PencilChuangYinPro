package com.pencil.chuangyin.feature.ai.tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.agent.runtime.ToolCallScope
import com.pencil.chuangyin.core.barcode.BarcodeDecoder
import com.pencil.chuangyin.core.barcode.BarcodeFormatOption
import com.pencil.chuangyin.core.barcode.BarcodeGenerator
import com.pencil.chuangyin.printer.BinarizeAlgorithm
import com.pencil.chuangyin.printer.ImagePrintOptions
import com.pencil.chuangyin.printer.ImageProcessOptions
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterController
import com.pencil.chuangyin.printer.PrinterSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** create_barcode 的条码格式（名称与 ZXing / [BarcodeFormatOption] 枚举严格一致）。 */
enum class BarcodeFormat {
    UPC_A,
    UPC_E,
    EAN_8,
    EAN_13,
    CODE_39,
    CODE_93,
    CODE_128,
    CODABAR,
    ITF,
    RSS_14,
    RSS_EXPANDED,
    QR_CODE,
    DATA_MATRIX,
    AZTEC,
    PDF_417,
}

/**
 * 条码类工具：把文本编码为一维码/二维码图片，或从图片中识别条码内容。
 *
 * 生成产物写入 agent 工作区，可选直接交给 core:printer 打印；
 * 识别失败与未连接打印均返回可读提示，不抛出异常。
 */
@Singleton
class BarcodeTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workspace: Workspace,
    private val scope: ToolCallScope,
    private val printer: PrinterController,
    private val settings: PrinterSettings,
) {

    /**
     * 将文本生成条码图片并保存到工作区（可选直接打印）。
     * 一维码（如 CODE_128 / EAN_13）需要符合该格式的字符内容；二维码支持任意文本。
     */
    @Tool
    suspend fun create_barcode(
        @Param(description = "条码承载的文本内容。") content: String,
        @Param(description = "条码格式，默认 QR_CODE。一维码要求内容符合该格式规范。") format: BarcodeFormat?,
        @Param(description = "图片宽度像素，默认使用当前打印头宽度（通常 384）。") width: Int?,
        @Param(description = "图片高度像素；不填则一维码 140、二维码与宽度相同。") height: Int?,
        @Param(description = "可选输出文件名（不含路径），默认自动命名。") file_name: String?,
        @Param(description = "是否保存到工作区，默认 true。") save: Boolean?,
        @Param(description = "生成后是否立即打印，默认 false。") print: Boolean?,
    ): ToolOutput {
        if (content.isEmpty()) return ToolOutput("条码内容不能为空", isError = true)
        val option = format?.let { BarcodeFormatOption.fromName(it.name) }
            ?: BarcodeFormatOption.QR_CODE
        val printWidth = settings.printWidthPx().coerceAtLeast(BarcodeGenerator.DEFAULT_WIDTH_PX)

        scope.current?.reportProgress("正在生成${option.displayName}")
        val bitmap = withContext(Dispatchers.IO) {
            BarcodeGenerator.encode(
                content = content,
                option = option,
                widthPx = width ?: printWidth,
                heightPx = height ?: 0,
            )
        } ?: return ToolOutput(
            "生成失败：内容与 ${option.displayName} 格式不匹配（一维码通常只接受数字或特定字符集），或尺寸参数非法。",
            isError = true,
        )

        val outWidth = bitmap.width
        val outHeight = bitmap.height
        val shouldSave = save ?: true
        val builder = StringBuilder()
        builder.appendLine("已生成 ${option.displayName} 条码（$outWidth×$outHeight）。")

        var savedFile: java.io.File? = null
        if (shouldSave) {
            val target = workspace.uniqueTarget(
                (file_name?.trim()?.takeIf { it.isNotEmpty() } ?: (FILE_PREFIX + System.currentTimeMillis())) + ".png",
            )
            val bytes = withContext(Dispatchers.IO) {
                ByteArrayOutputStream().use { buffer ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
                    buffer.toByteArray()
                }
            }
            if (!withContext(Dispatchers.IO) { workspace.writeBytes(target, bytes) }) {
                bitmap.recycle()
                return ToolOutput("条码图片保存失败：${workspace.displayPath(target)}", isError = true)
            }
            savedFile = target
            builder.appendLine("已保存到 ${workspace.displayPath(target)}。")
        }

        if (print == true) {
            builder.appendLine(withContext(Dispatchers.IO) { printBitmap(bitmap) })
        }
        bitmap.recycle()

        val artifacts = savedFile?.let { file ->
            listOf(
                ToolArtifact(
                    id = UUID.randomUUID().toString(),
                    kind = ToolArtifactKind.IMAGE,
                    path = file.absolutePath,
                    label = file.name,
                ),
            )
        }.orEmpty()
        return ToolOutput(content = builder.toString().trim(), artifacts = artifacts)
    }

    /** 识别图片文件中的条码/二维码，返回格式与承载文本。 */
    @Tool
    suspend fun scan_barcode(
        @Param(description = "图片文件路径（相对路径以工作区为基准），支持 png/jpg 等常见格式。") path: String,
    ): ToolOutput {
        val file = runCatching { workspace.resolveForRead(path.trim()) }
            .getOrElse { return ToolOutput(it.message ?: "图片路径无效", isError = true) }
        if (file.isDirectory) return ToolOutput("${workspace.displayPath(file)} 是目录，不是图片文件。", isError = true)

        val readable = withContext(Dispatchers.IO) { workspace.materialize(file) }
            ?: return ToolOutput("无法读取图片：${workspace.displayPath(file)}", isError = true)

        scope.current?.reportProgress("正在识别条码")
        val decoded = withContext(Dispatchers.IO) {
            BarcodeDecoder.decodeFromUri(context, Uri.fromFile(readable))
        } ?: return ToolOutput(
            "未能识别图片中的条码：${workspace.displayPath(file)}（请确认条码完整、清晰且未被过度压缩）。",
            isError = true,
        )

        return ToolOutput(
            buildString {
                appendLine("识别成功，格式：${decoded.formatName}")
                appendLine("内容：${decoded.text}")
                appendLine("定位点数：${decoded.resultPoints.size}")
            }.trim(),
        )
    }

    /** 把生成好的条码位图交给打印管线；固定阈值二值化避免抖动破坏条码模块边缘。 */
    private suspend fun printBitmap(bitmap: Bitmap): String {
        if (!printer.isConnected) return "未打印：打印机未连接，请提示用户到首页连接后再试。"
        scope.current?.reportProgress("正在打印条码")
        val process = ImageProcessOptions(algorithm = BinarizeAlgorithm.FixedThreshold)
        return when (val result = printer.printImagePages(
            pageCount = 1,
            options = ImagePrintOptions(process = process),
            pageLoader = { bitmap },
        )) {
            is PrintResult.Success -> "已发送到打印机。"
            is PrintResult.Error -> "打印失败：${result.code}${result.detail?.let { "（$it）" } ?: ""}"
        }
    }

    private companion object {
        const val FILE_PREFIX = "barcode_"
    }
}
