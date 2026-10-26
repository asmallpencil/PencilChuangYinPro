package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.ai.AgentTool
import com.pencil.chuangyin.desktop.ai.ChatMessage
import com.pencil.chuangyin.desktop.ai.ChatRole
import com.pencil.chuangyin.desktop.ai.ToolContext
import com.pencil.chuangyin.desktop.ai.ToolOutput
import com.pencil.chuangyin.desktop.image.BinarizeAlgorithm
import com.pencil.chuangyin.desktop.image.ImageProcessOptions
import com.pencil.chuangyin.desktop.image.ImageProcessor
import com.pencil.chuangyin.desktop.printer.PrinterController
import com.pencil.chuangyin.desktop.printer.RasterPage
import com.pencil.chuangyin.desktop.printer.TextPrintRunner
import com.pencil.chuangyin.desktop.text.PrintAlign
import com.pencil.chuangyin.desktop.text.PrintOptions
import com.pencil.chuangyin.desktop.text.PrintPhase
import com.pencil.chuangyin.desktop.text.PrintProgress
import com.pencil.chuangyin.desktop.text.PrintResult
import com.pencil.chuangyin.desktop.text.TextDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * 打印类工具集合：文本 / 图片 / 聊天记录。
 *
 * 与安卓端 `PrintTools` 语义一致：内容直接交给热敏打印管线（RFCOMM / SPP 蓝牙通道）下发；
 * 未连接打印机时直接返回可读错误，交由模型提示用户前往首页连接，而不是抛出异常。
 */
class PrintTools(private val workspace: Workspace) {

    val tools: List<AgentTool> = listOf(
        agentTool(
            name = ToolNames.PRINT_TEXT,
            description = "用蓝牙热敏打印机打印一段文字。可设置标题、字号、对齐与加粗。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.CONTENT),
                properties = mapOf(
                    ToolParams.CONTENT to ToolSchema.string("要打印的正文内容。"),
                    ToolParams.TITLE to ToolSchema.string("可选标题，会以更大字号打印在正文上方。"),
                    ToolParams.FONT_SIZE to ToolSchema.number("正文字号，范围 8..64，默认 14。"),
                    ToolParams.ALIGN to ToolSchema.string("对齐方式。", ToolParamValues.ALIGN_VALUES),
                    ToolParams.BOLD to ToolSchema.boolean("是否加粗，默认 false。"),
                ),
            ),
        ) { args, context -> printText(args, context) },

        agentTool(
            name = ToolNames.PRINT_IMAGE,
            description = "用蓝牙热敏打印机打印一张图片文件。可指定黑白化算法、阈值与旋转角度。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.PATH),
                properties = mapOf(
                    ToolParams.PATH to ToolSchema.string("图片文件路径（相对路径以 agent 工作目录为基准）。"),
                    ToolParams.ALGORITHM to ToolSchema.string("黑白化算法。", ToolParamValues.BINARIZE_VALUES),
                    ToolParams.THRESHOLD to ToolSchema.integer("二值化阈值 0..255，默认 149。"),
                    ToolParams.ROTATION to ToolSchema.integer("顺时针旋转角度，仅支持 0/90/180/270。"),
                ),
            ),
        ) { args, context -> printImage(args, context) },

        agentTool(
            name = ToolNames.PRINT_CONVERSATION,
            description = "用蓝牙热敏打印机打印当前这段对话的聊天记录。",
            parameters = ToolSchema.parameters(
                required = emptyList(),
                properties = mapOf(
                    ToolParams.TITLE to ToolSchema.string("可选标题，默认「聊天记录」。"),
                ),
            ),
        ) { args, context -> printConversation(args, context) },

        agentTool(
            name = ToolNames.PRINTER_STATUS,
            description = "查询打印机连接状态、故障、电量与纸张宽度等实时信息。",
            parameters = ToolSchema.parameters(emptyList(), emptyMap()),
        ) { _, _ -> ToolOutput(PrintBridge.statusText()) },

        agentTool(
            name = ToolNames.TEST_FEED,
            description = "让打印机走一小段空白纸，用于验证连接与协议是否正常。",
            parameters = ToolSchema.parameters(emptyList(), emptyMap()),
        ) { _, context -> testFeed(context) },
    )

    private suspend fun printText(args: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val content = args.stringOrNull(ToolParams.CONTENT) ?: return@withContext failure("正文内容不能为空")
        if (content.isBlank()) return@withContext failure("正文内容不能为空")
        if (!PrintBridge.isPrinterConnected()) return@withContext failure(ERROR_NOT_CONNECTED)

        val doc = TextDoc(
            title = args.stringOrNull(ToolParams.TITLE) ?: "",
            content = content,
            fontSizeSp = args.doubleOrNull(ToolParams.FONT_SIZE)
                ?.toFloat()
                ?.coerceIn(TextDoc.FONT_SIZE_RANGE)
                ?: PrintDefaults.FONT_SIZE,
            align = parseAlign(args.stringOrNull(ToolParams.ALIGN)),
            bold = args.booleanOr(ToolParams.BOLD, false),
        )
        context.reportProgress("正在打印文字")
        printDocument(doc, content, "文字", context)
    }

    private suspend fun printImage(args: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val path = args.stringOrNull(ToolParams.PATH) ?: return@withContext failure("缺少参数 path")
        val file = runCatching { workspace.resolveForRead(path) }
            .getOrElse { return@withContext failure(it.message ?: "图片路径无效") }
        if (file.isDirectory) return@withContext failure("${workspace.displayPath(file)} 是目录，不是图片文件。")
        if (!PrintBridge.isPrinterConnected()) return@withContext failure(ERROR_NOT_CONNECTED)

        val source = runCatching { ImageProcessor.decode(file.readBytes()) }.getOrNull()
            ?: return@withContext failure("无法解码图片：${workspace.displayPath(file)}")

        val options = ImageProcessOptions(
            algorithm = parseAlgorithm(args.stringOrNull(ToolParams.ALGORITHM)),
            threshold = args.intOr(ToolParams.THRESHOLD, ImageProcessOptions.DEFAULT_THRESHOLD)
                .coerceIn(ImageProcessOptions.MIN_THRESHOLD, ImageProcessOptions.MAX_THRESHOLD),
            rotationDegrees = normalizeRotation(args.intOr(ToolParams.ROTATION, 0)),
        )

        context.reportProgress("正在打印图片")
        val prepared = runCatching { ImageProcessor.prepare(source, options) }
            .getOrElse { return@withContext failure("图片处理失败：${it.message}") }

        val result = PrinterController.printPages(
            pages = listOf(RasterPage.from(prepared, options.threshold)),
            options = PrintOptions(),
            onProgress = { progress -> context.reportProgress(describeProgress(progress)) },
        )
        when (result) {
            is PrintResult.Success -> ToolOutput("图片已发送到打印机。")
            is PrintResult.Error -> failure("打印失败：" + PrintBridge.errorText(result.code, result.detail))
        }
    }

    private suspend fun printConversation(args: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        if (!PrintBridge.isPrinterConnected()) return@withContext failure(ERROR_NOT_CONNECTED)
        val rendered = renderTranscript(context.transcript())
        if (rendered.isBlank()) return@withContext failure("当前对话还没有可打印的内容。")

        val doc = TextDoc(
            title = args.stringOrNull(ToolParams.TITLE) ?: PrintDefaults.CONVERSATION_TITLE,
            content = rendered,
        )
        context.reportProgress("正在打印聊天记录")
        printDocument(doc, rendered, "聊天记录", context)
    }

    /** 走纸自测：确认蓝牙通道与打印头可用。 */
    private suspend fun testFeed(context: ToolContext): ToolOutput {
        if (!PrintBridge.isPrinterConnected()) return failure(ERROR_NOT_CONNECTED)
        context.reportProgress("正在走纸自测")
        return when (val result = PrinterController.testFeed()) {
            is PrintResult.Success -> ToolOutput("走纸指令已发送，请观察打印机是否吐纸。")
            is PrintResult.Error -> failure("走纸失败：" + PrintBridge.errorText(result.code, result.detail))
        }
    }

    /** 通过 [TextPrintRunner] 把 [TextDoc] 真正下发到打印机。 */
    private suspend fun printDocument(
        doc: TextDoc,
        content: String,
        kind: String,
        context: ToolContext,
    ): ToolOutput = when (
        val result = TextPrintRunner.run(
            doc = doc,
            content = content,
            customFontPath = null,
            options = PrintOptions(),
            onProgress = { progress -> context.reportProgress(describeProgress(progress)) },
        )
    ) {
        is PrintResult.Success -> ToolOutput("${kind}已发送到打印机。")
        is PrintResult.Error -> failure("打印失败：" + PrintBridge.errorText(result.code, result.detail))
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

    /** 对齐方式字符串 → [PrintAlign]，未提供时回退左对齐。 */
    private fun parseAlign(raw: String?): PrintAlign = when (raw?.trim()?.lowercase()) {
        ToolParamValues.ALIGN_CENTER -> PrintAlign.Center
        ToolParamValues.ALIGN_END -> PrintAlign.End
        else -> PrintAlign.Start
    }

    /** 算法名 → [BinarizeAlgorithm]，未提供或无法识别时回退默认算法。 */
    private fun parseAlgorithm(raw: String?): BinarizeAlgorithm {
        val name = raw?.trim().orEmpty()
        return BinarizeAlgorithm.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: BinarizeAlgorithm.FixedThreshold
    }

    /** 把任意角度归一化到 0/90/180/270，避免 [ImageProcessOptions] 的入参校验抛异常。 */
    private fun normalizeRotation(degrees: Int): Int {
        val normalized = ((degrees % 360) + 360) % 360
        val stepped = (normalized / 90) * 90
        return ImageProcessOptions.ROTATION_STEPS.minByOrNull { kotlin.math.abs(it - stepped) } ?: 0
    }

    private companion object {
        const val LABEL_USER = "我"
        const val LABEL_ASSISTANT = "助手"
        const val ERROR_NOT_CONNECTED = "打印机未连接，请提示用户到首页连接蓝牙打印机后重试。"
    }
}

/** 打印工具默认值。 */
private object PrintDefaults {
    const val FONT_SIZE = 14f
    const val CONVERSATION_TITLE = "聊天记录"
}

/** 打印进度 → 供模型/界面展示的简短描述。 */
private fun describeProgress(progress: PrintProgress): String = when (progress.phase) {
    PrintPhase.Preparing -> "正在准备打印"
    PrintPhase.Printing -> "正在打印（第 ${progress.pageDone} 页）"
    PrintPhase.CoolingDown -> "打印头过热，冷却中"
    PrintPhase.Finishing -> "正在收尾"
}
