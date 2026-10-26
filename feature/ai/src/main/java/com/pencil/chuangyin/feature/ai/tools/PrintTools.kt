package com.pencil.chuangyin.feature.ai.tools

import android.graphics.BitmapFactory
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ChatRole
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.agent.runtime.ToolCallScope
import com.pencil.chuangyin.printer.BinarizeAlgorithm
import com.pencil.chuangyin.printer.ConnectionState
import com.pencil.chuangyin.printer.ImagePrintOptions
import com.pencil.chuangyin.printer.ImageProcessOptions
import com.pencil.chuangyin.printer.PrintAlign
import com.pencil.chuangyin.printer.PrintOptions
import com.pencil.chuangyin.printer.PrintProgress
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterController
import com.pencil.chuangyin.printer.PrinterSettings
import com.pencil.chuangyin.printer.PrinterStatus
import com.pencil.chuangyin.printer.TextDoc
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** print_text 的对齐方式（官方生成器会把枚举值列表写进 Schema 的 enum）。 */
enum class TextAlign { START, CENTER, END }

/** print_image 的黑白化算法，常量名与 [BinarizeAlgorithm] 枚举名严格一致。 */
enum class BinarizeOption { FixedThreshold, FloydSteinberg, Atkinson, SobelEdge, SauvolaAdaptive }

/**
 * 打印类工具集合：把「文本 / 图片 / 聊天记录」交给 core:printer 的打印管线。
 *
 * 未连接打印机时直接返回可读错误，交由模型提示用户前往首页连接，而不是抛出异常。
 */
@Singleton
class PrintTools @Inject constructor(
    private val printer: PrinterController,
    private val scope: ToolCallScope,
    private val workspace: Workspace,
    private val settings: PrinterSettings,
) {

    /** 用蓝牙打印机打印一段文字。可设置标题、字号、对齐与加粗。 */
    @Tool
    suspend fun print_text(
        @Param(description = "要打印的正文内容。") content: String,
        @Param(description = "可选标题，会以更大字号打印在正文上方。") title: String?,
        @Param(description = "正文字号，范围 8..64，默认 14。") font_size: Double?,
        @Param(description = "对齐方式。") align: TextAlign?,
        @Param(description = "是否加粗，默认 false。") bold: Boolean?,
    ): ToolOutput {
        if (content.isBlank()) return ToolOutput("正文内容不能为空", isError = true)
        if (!printer.isConnected) return ToolOutput(ERROR_NOT_CONNECTED, isError = true)

        val doc = TextDoc(
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: "",
            content = content,
            fontSizeSp = (font_size ?: PrintDefaults.FONT_SIZE.toDouble())
                .toFloat()
                .coerceIn(TextDoc.FONT_SIZE_RANGE),
            align = parseAlign(align),
            bold = bold ?: false,
        )
        scope.current?.reportProgress("正在打印文字")
        return when (val result = printer.printDocument(doc, null, PrintOptions())) {
            is PrintResult.Success -> ToolOutput("文字已发送到打印机。")
            is PrintResult.Error -> ToolOutput("打印失败：${result.code}${result.detail?.let { "（$it）" } ?: ""}", isError = true)
        }
    }

    /** 打印一张图片文件。可指定黑白化算法、阈值与旋转角度。 */
    @Tool
    suspend fun print_image(
        @Param(description = "图片文件路径（相对路径以工作区为基准）。") path: String,
        @Param(description = "黑白化算法。") algorithm: BinarizeOption?,
        @Param(description = "二值化阈值 0..255，默认 149。") threshold: Int?,
        @Param(description = "顺时针旋转角度，仅支持 0/90/180/270。") rotation: Int?,
    ): ToolOutput {
        val file = runCatching { workspace.resolveForRead(path.trim()) }
            .getOrElse { return ToolOutput(it.message ?: "图片路径无效", isError = true) }
        if (file.isDirectory) return ToolOutput("${workspace.displayPath(file)} 是目录，不是图片文件。", isError = true)
        if (!printer.isConnected) return ToolOutput(ERROR_NOT_CONNECTED, isError = true)

        val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
            ?: return ToolOutput("无法解码图片：${workspace.displayPath(file)}", isError = true)

        val process = ImageProcessOptions(
            algorithm = parseAlgorithm(algorithm),
            threshold = (threshold ?: ImageProcessOptions.DEFAULT_THRESHOLD)
                .coerceIn(ImageProcessOptions.MIN_THRESHOLD, ImageProcessOptions.MAX_THRESHOLD),
            rotationDegrees = normalizeRotation(rotation ?: 0),
        )
        scope.current?.reportProgress("正在打印图片")
        val result = printer.printImagePages(
            pageCount = 1,
            options = ImagePrintOptions(process = process),
            onProgress = { progress -> scope.current?.reportProgress(describeProgress(progress)) },
            pageLoader = { bitmap },
        )
        return when (result) {
            is PrintResult.Success -> ToolOutput("图片已发送到打印机。")
            is PrintResult.Error -> ToolOutput("打印失败：${result.code}${result.detail?.let { "（$it）" } ?: ""}", isError = true)
        }
    }

    /** 把当前这段对话的聊天记录打印出来。 */
    @Tool
    suspend fun print_conversation(
        @Param(description = "可选标题，默认「聊天记录」。") title: String?,
    ): ToolOutput {
        if (!printer.isConnected) return ToolOutput(ERROR_NOT_CONNECTED, isError = true)
        val rendered = renderTranscript(scope.current?.transcript().orEmpty())
        if (rendered.isBlank()) return ToolOutput("当前对话还没有可打印的内容。", isError = true)

        val doc = TextDoc(
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: PrintDefaults.CONVERSATION_TITLE,
            content = rendered,
        )
        scope.current?.reportProgress("正在打印聊天记录")
        return when (val result = printer.printDocument(doc, null, PrintOptions())) {
            is PrintResult.Success -> ToolOutput("聊天记录已发送到打印机。")
            is PrintResult.Error -> ToolOutput("打印失败：${result.code}${result.detail?.let { "（$it）" } ?: ""}", isError = true)
        }
    }

    /** 查询打印机连接状态、故障、电量与纸张宽度等实时信息。 */
    @Tool
    suspend fun printer_status(): ToolOutput {
        val builder = StringBuilder()
        when (val state = printer.connectionState.value) {
            is ConnectionState.Connected -> {
                builder.appendLine("连接状态：已连接")
                builder.appendLine("设备名称：${state.device.name}")
                builder.appendLine("设备地址：${state.device.address}")
            }

            is ConnectionState.Connecting -> {
                builder.appendLine("连接状态：正在连接 ${state.device.name}")
            }

            ConnectionState.Disconnected -> builder.appendLine("连接状态：未连接")
        }
        val device = printer.deviceState.value
        builder.appendLine("电量：" + (device.batteryPercent?.let { "$it%" } ?: "未知"))
        builder.appendLine(
            "当前故障：" +
                if (device.activeStatuses.isEmpty()) {
                    "无"
                } else {
                    device.activeStatuses.joinToString("、") { statusLabel(it) }
                },
        )
        builder.appendLine("热敏纸宽度：${settings.currentPaperWidthMm()} mm")
        builder.appendLine("打印头像素宽：${settings.printWidthPx()} px")
        return ToolOutput(builder.toString().trim())
    }

    /** 让打印机走一小段空白纸，用于验证连接与协议是否正常。 */
    @Tool
    suspend fun test_feed(): ToolOutput {
        if (!printer.isConnected) return ToolOutput(ERROR_NOT_CONNECTED, isError = true)
        scope.current?.reportProgress("正在走纸自测")
        return when (val result = printer.testFeed()) {
            is PrintResult.Success -> ToolOutput("走纸指令已发送，请观察打印机是否吐纸。")
            is PrintResult.Error -> ToolOutput("走纸失败：${result.code}${result.detail?.let { "（$it）" } ?: ""}", isError = true)
        }
    }

    /** 把会话消息渲染为纯文本：跳过系统/折叠摘要/工具结果，只保留用户与助手的可见内容。 */
    private fun renderTranscript(messages: List<ChatMessage>): String = buildString {
        messages.forEach { message ->
            if (message.isSummary || message.content.isBlank()) return@forEach
            val label = when (message.role) {
                ChatRole.User -> LABEL_USER
                ChatRole.Assistant -> LABEL_ASSISTANT
                else -> return@forEach
            }
            appendLine("$label：${message.content.trim()}")
            appendLine()
        }
    }

    private companion object {
        const val LABEL_USER = "我"
        const val LABEL_ASSISTANT = "助手"
    }
}

/** 打印工具默认值。 */
private object PrintDefaults {
    const val FONT_SIZE = 14
    const val CONVERSATION_TITLE = "聊天记录"
}

private const val ERROR_NOT_CONNECTED = "打印机未连接，请提示用户到首页连接蓝牙打印机后重试。"

/** [TextAlign] → [PrintAlign]，未提供时回退左对齐。 */
private fun parseAlign(raw: TextAlign?): PrintAlign = when (raw) {
    TextAlign.CENTER -> PrintAlign.Center
    TextAlign.END -> PrintAlign.End
    else -> PrintAlign.Start
}

/** [BinarizeOption] → [BinarizeAlgorithm]，未提供时回退默认算法。 */
private fun parseAlgorithm(raw: BinarizeOption?): BinarizeAlgorithm =
    raw?.let { BinarizeAlgorithm.valueOf(it.name) } ?: BinarizeAlgorithm.FixedThreshold

/** 把任意角度归一化到 0/90/180/270，避免 [ImageProcessOptions] 的入参校验抛异常。 */
private fun normalizeRotation(degrees: Int): Int {
    val normalized = ((degrees % 360) + 360) % 360
    val stepped = (normalized / 90) * 90
    return ImageProcessOptions.ROTATION_STEPS.minByOrNull { kotlin.math.abs(it - stepped) } ?: 0
}

/** 打印进度 → 供模型/界面展示的简短描述。 */
private fun describeProgress(progress: PrintProgress): String = when (progress.phase) {
    com.pencil.chuangyin.printer.PrintPhase.Preparing -> "正在准备打印"
    com.pencil.chuangyin.printer.PrintPhase.Printing ->
        "正在打印（第 ${progress.pageDone} 页）"

    com.pencil.chuangyin.printer.PrintPhase.CoolingDown -> "打印头过热，冷却中"
    com.pencil.chuangyin.printer.PrintPhase.Finishing -> "正在收尾"
}

/** 故障枚举 → 中文标签。 */
private fun statusLabel(status: PrinterStatus): String = when (status) {
    PrinterStatus.OutOfPaper -> "缺纸"
    PrinterStatus.CoverOpen -> "开盖"
    PrinterStatus.Overheat -> "过热"
    PrinterStatus.LowBattery -> "电量低"
}
