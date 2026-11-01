package com.pencil.chuangyin.feature.ai.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.agent.runtime.ToolCallScope
import com.pencil.chuangyin.printer.BinarizeAlgorithm
import com.pencil.chuangyin.printer.ImagePrintOptions
import com.pencil.chuangyin.printer.ImageProcessOptions
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterController
import com.pencil.chuangyin.printer.PrinterSettings
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 内容生成工具：图表与排版笔记。
 *
 * 与 [TableTools] 一样，只生成图片并放进对话预览，默认不打印。用户确认后再用
 * `print_image` 打印同一个文件；打印时使用固定阈值，保证文字/线条粗细稳定。
 */
@Singleton
class StudioTools @Inject constructor(
    private val workspace: Workspace,
    private val scope: ToolCallScope,
    private val printer: PrinterController,
    private val settings: PrinterSettings,
) {

    /**
     * 生成柱状图或折线图。labels 与 values 一一对应；数值与单位会自动标注在图内。
     */
    @Tool
    suspend fun create_chart(
        @Param(description = "图表类型，bar（柱状图）或 line（折线图）。") type: ChartType?,
        @Param(description = "图表标题。") title: String?,
        @Param(description = "横轴分类标签数组，例如 [\"周一\",\"周二\",\"周三\"]。") labels: List<String>,
        @Param(description = "与 labels 一一对应的数值数组，例如 [12, 18, 9]。") values: List<Double>,
        @Param(description = "可选数值单位，例如 \"元\"、\"人\"、\"%\"。") unit: String?,
        @Param(description = "图片高度像素，默认 320，范围 220..1400。") height: Int?,
        @Param(description = "生成后是否立即打印，默认 false。只有用户明确要求打印时才设为 true。") print: Boolean?,
        @Param(description = "可选文件名（不含路径），默认自动命名，扩展名固定为 .png。") file_name: String?,
    ): ToolOutput {
        if (labels.isEmpty()) return ToolOutput("缺少参数 labels，或分类标签为空。", isError = true)
        if (values.isEmpty()) return ToolOutput("缺少参数 values，或数值为空。", isError = true)
        if (values.any { !it.isFinite() }) return ToolOutput("数值中不能包含 NaN 或无穷大。", isError = true)

        val count = max(labels.size, values.size)
        val safeLabels = List(count) { index -> labels.getOrElse(index) { "${index + 1}" } }
        val safeValues = List(count) { index -> values.getOrElse(index) { 0.0 } }

        val canvasWidth = settings.printWidthPx().coerceAtLeast(MIN_CANVAS_WIDTH)
        val canvasHeight = (height ?: DEFAULT_HEIGHT).coerceIn(MIN_HEIGHT, MAX_HEIGHT)
        val chartType = type ?: ChartType.BAR

        scope.current?.reportProgress("正在绘制图表")
        val bitmap = withContext(Dispatchers.IO) {
            StudioRenderer.renderChart(chartType, title?.trim(), safeLabels, safeValues, unit, canvasWidth, canvasHeight)
        }
        val target = resolveTargetFile(file_name)
        val saved = withContext(Dispatchers.IO) {
            runCatching { workspace.writeBytes(target, StudioRenderer.encodePng(bitmap)) }.getOrDefault(false)
        }
        val width = bitmap.width
        val bitmapHeight = bitmap.height
        bitmap.recycle()
        if (!saved) return ToolOutput("图表保存失败：${workspace.displayPath(target)}", isError = true)

        val label = if (chartType == ChartType.BAR) "柱状图" else "折线图"
        val builder = StringBuilder()
        builder.appendLine("已生成$label ${workspace.displayPath(target)}（${width}×${bitmapHeight}px，${safeValues.size} 组数据）。")
        builder.appendLine("图表已附在对话中，用户可以直接预览。")
        if (print == true) {
            builder.appendLine(printGenerated(target))
        } else {
            builder.appendLine("未打印：请先让用户确认预览；用户确认后再调用 print_image 打印这个文件。")
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

    /**
     * 生成排版笔记图片：标题 + 自动换行的正文，适合打印通知、清单、说明文字等。
     */
    @Tool
    suspend fun create_note(
        @Param(description = "可选标题，会以更大字号居中显示在正文上方。") title: String?,
        @Param(description = "正文内容，支持换行。") content: String,
        @Param(description = "正文字号像素，默认 26，范围 12..56。") font_size: Int?,
        @Param(description = "生成后是否立即打印，默认 false。只有用户明确要求打印时才设为 true。") print: Boolean?,
        @Param(description = "可选文件名（不含路径），默认自动命名，扩展名固定为 .png。") file_name: String?,
    ): ToolOutput {
        if (content.isBlank()) return ToolOutput("正文内容不能为空。", isError = true)

        val fontSize = (font_size?.toFloat() ?: StudioRenderer.DEFAULT_NOTE_FONT)
            .coerceIn(StudioRenderer.MIN_NOTE_FONT, StudioRenderer.MAX_NOTE_FONT)
        val canvasWidth = settings.printWidthPx().coerceAtLeast(MIN_CANVAS_WIDTH)
        val titleValue = title?.trim()?.takeIf { it.isNotEmpty() }

        scope.current?.reportProgress("正在排版笔记")
        val bitmap = withContext(Dispatchers.IO) {
            StudioRenderer.renderNote(titleValue, content, fontSize, canvasWidth)
        }
        val target = resolveTargetFile(file_name)
        val saved = withContext(Dispatchers.IO) {
            runCatching { workspace.writeBytes(target, StudioRenderer.encodePng(bitmap)) }.getOrDefault(false)
        }
        val width = bitmap.width
        val height = bitmap.height
        bitmap.recycle()
        if (!saved) return ToolOutput("笔记保存失败：${workspace.displayPath(target)}", isError = true)

        val builder = StringBuilder()
        builder.appendLine("已生成笔记图片 ${workspace.displayPath(target)}（${width}×${height}px）。")
        builder.appendLine("笔记已附在对话中，用户可以直接预览。")
        if (print == true) {
            builder.appendLine(printGenerated(target))
        } else {
            builder.appendLine("未打印：请先让用户确认预览；用户确认后再调用 print_image 打印这个文件。")
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

    private suspend fun printGenerated(file: File): String {
        if (!printer.isConnected) return "未打印：打印机未连接，请提示用户到首页连接后再试。"
        val printable = workspace.materialize(file) ?: return "打印失败：无法读取 ${file.name}。"
        val bitmap = BitmapFactory.decodeFile(printable.absolutePath)
            ?: return "打印失败：无法解码生成的图片。"
        scope.current?.reportProgress("正在打印生成内容")
        val result = printer.printImagePages(
            pageCount = 1,
            options = ImagePrintOptions(
                process = ImageProcessOptions(
                    algorithm = BinarizeAlgorithm.FixedThreshold,
                    threshold = DOCUMENT_THRESHOLD,
                ),
            ),
            pageLoader = { bitmap },
        )
        return when (result) {
            is PrintResult.Success -> "已发送到打印机。"
            is PrintResult.Error -> "打印失败：${result.code}${result.detail?.let { "（$it）" } ?: ""}"
        }
    }

    private fun resolveTargetFile(fileName: String?): File {
        val base = fileName?.trim()?.takeIf { it.isNotEmpty() }?.let(::sanitize)
            ?.takeIf { it.isNotEmpty() }
            ?: (FILE_PREFIX + System.currentTimeMillis())
        return workspace.uniqueTarget("$base.png")
    }

    private fun sanitize(raw: String): String {
        val withoutExt = raw.removeSuffix(".png")
        return withoutExt.map { ch ->
            if (ch.isLetterOrDigit() || ch == '_' || ch == '-') ch else '_'
        }.joinToString("")
    }

    private companion object {
        const val FILE_PREFIX = "studio_"
        const val MIN_CANVAS_WIDTH = 240
        const val DEFAULT_HEIGHT = 320
        const val MIN_HEIGHT = 220
        const val MAX_HEIGHT = 1400
        const val DOCUMENT_THRESHOLD = 160
    }
}
