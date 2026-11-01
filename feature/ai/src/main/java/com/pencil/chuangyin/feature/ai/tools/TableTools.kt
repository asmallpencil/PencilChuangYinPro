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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 表格生成工具。
 *
 * 职责边界：**只负责生成高质量表格图片并放进对话预览**，不自动打印。用户确认预览后，
 * 再调用 `print_image` 打印同一个文件。这样可以避免模型「生成 → 立刻打印 → 推翻重做 →
 * 再打印」的浪费行为，也避免把有问题的表格直接送到纸上。
 */
@Singleton
class TableTools @Inject constructor(
    private val workspace: Workspace,
    private val scope: ToolCallScope,
    private val printer: PrinterController,
    private val settings: PrinterSettings,
) {

    /**
     * 把结构化数据渲染成表格图片。headers 为列名数组；rows 每行一个字符串、单元格用 | 分隔
     * （如 "姓名|年龄|城市"）。列数不足补空、超出截断；内容过宽时自动缩小字号，仍放不下则截断并提示。
     */
    @Tool
    suspend fun create_table(
        @Param(description = "表头列名数组，例如 [\"姓名\",\"年龄\",\"城市\"]。") headers: List<String>,
        @Param(description = "数据行数组，每行单元格以 | 分隔，例如 [\"张三|28|北京\",\"李四|31|上海\"]。") rows: List<String>,
        @Param(description = "可选表格标题。") title: String?,
        @Param(description = "字号像素，默认 24，范围 9..48。") font_size: Int?,
        @Param(description = "生成后是否立即打印，默认 false。只有用户明确要求打印时才设为 true。") print: Boolean?,
        @Param(description = "可选文件名（不含路径），默认自动命名，扩展名固定为 .png。") file_name: String?,
    ): ToolOutput {
        if (headers.isEmpty()) return ToolOutput("缺少参数 headers，或表头为空。", isError = true)
        val grid = rows.map { line -> line.split('|').map { it.trim() } }
        if (grid.isEmpty()) return ToolOutput("缺少参数 rows，或数据行为空。", isError = true)

        val fontSize = (font_size?.toFloat() ?: StudioRenderer.DEFAULT_TABLE_FONT)
            .coerceIn(StudioRenderer.MIN_TABLE_FONT, StudioRenderer.MAX_TABLE_FONT)
        val titleValue = title?.trim()?.takeIf { it.isNotEmpty() }
        val canvasWidth = settings.printWidthPx().coerceAtLeast(MIN_CANVAS_WIDTH)

        scope.current?.reportProgress("正在绘制表格")
        val result = withContext(Dispatchers.IO) {
            StudioRenderer.renderTable(titleValue, headers, grid, fontSize, canvasWidth)
        }
        val bitmap = result.bitmap
        val target = resolveTargetFile(file_name)
        val saved = withContext(Dispatchers.IO) {
            runCatching { workspace.writeBytes(target, StudioRenderer.encodePng(bitmap)) }.getOrDefault(false)
        }
        val width = bitmap.width
        val height = bitmap.height
        bitmap.recycle()
        if (!saved) return ToolOutput("表格保存失败：${workspace.displayPath(target)}", isError = true)

        val builder = StringBuilder()
        builder.appendLine("已生成表格图片 ${workspace.displayPath(target)}（${width}×${height}px，${grid.size} 行 × ${headers.size} 列）。")
        builder.appendLine("表格已附在对话中，用户可以直接预览。")
        if (result.truncatedCount > 0) {
            builder.appendLine("注意：有 ${result.truncatedCount} 个单元格内容过长被截断（已加省略号），可减少列数或缩短文字后重新生成。")
        }
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

    /** 打印刚生成的表格：固定阈值，避免抖动算法把笔画打散成粗细不均的灰点。 */
    private suspend fun printGenerated(file: File): String {
        if (!printer.isConnected) return "未打印：打印机未连接，请提示用户到首页连接后再试。"
        val printable = workspace.materialize(file) ?: return "打印失败：无法读取 ${file.name}。"
        val bitmap = BitmapFactory.decodeFile(printable.absolutePath)
            ?: return "打印失败：无法解码生成的表格图片。"
        scope.current?.reportProgress("正在打印表格")
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
            is PrintResult.Success -> "表格已发送到打印机。"
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
        const val FILE_PREFIX = "table_"
        const val MIN_CANVAS_WIDTH = 240
        /** 文本/表格类内容用固定阈值：笔画粗细稳定，不会出现抖动噪点。 */
        const val DOCUMENT_THRESHOLD = 160
    }
}
