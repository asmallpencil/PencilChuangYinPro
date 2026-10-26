package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.ai.AgentTool
import com.pencil.chuangyin.desktop.ai.ToolContext
import com.pencil.chuangyin.desktop.ai.ToolOutput
import com.pencil.chuangyin.desktop.text.PrinterCommand
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * 表格打印工具：把结构化数据渲染为等宽字体的网格表格图片，保存到工作区并（可选）直接打印。
 *
 * 覆盖「按要求制作一个表格并打印」这类需求，避免模型手工拼 ASCII 表格再逐行打印。
 */
class TableTools(private val workspace: Workspace) {

    val tools: List<AgentTool> = listOf(
        agentTool(
            name = ToolNames.PRINT_TABLE,
            description = "把数据渲染成表格图片并保存到工作区，默认直接打印（未连接打印机时仅保存文件）。" +
                "headers 为列名数组；rows 每行一个字符串、单元格用 | 分隔（如 \"姓名|年龄\"）。" +
                "列数不足自动补空、超出自动截断。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.HEADERS, ToolParams.ROWS),
                properties = mapOf(
                    ToolParams.HEADERS to ToolSchema.stringArray("表头列名数组。"),
                    ToolParams.ROWS to ToolSchema.stringArray("数据行数组，每行单元格以 | 分隔。"),
                    ToolParams.TITLE to ToolSchema.string("可选表格标题。"),
                    ToolParams.FONT_SIZE to ToolSchema.integer("字号像素，默认 26。"),
                    ToolParams.PRINT_AFTER_DRAW to ToolSchema.boolean("生成后是否立即打印，默认 true。"),
                    ToolParams.FILE_NAME to ToolSchema.string("可选文件名（不含路径），默认自动命名。"),
                ),
            ),
        ) { args, context -> printTable(args, context) },
    )

    private suspend fun printTable(args: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val headers = args.stringList(ToolParams.HEADERS)
        if (headers.isEmpty()) return@withContext failure("缺少参数 headers，或表头为空。")
        val grid = args.stringList(ToolParams.ROWS).map { line -> line.split('|').map { it.trim() } }
        if (grid.isEmpty()) return@withContext failure("缺少参数 rows，或数据行为空。")

        val fontSizeValue = args.intOr(ToolParams.FONT_SIZE, DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        val titleValue = args.stringOrNull(ToolParams.TITLE)
        val canvasWidth = PrinterCommand.PRINT_WIDTH_PX.coerceAtLeast(MIN_CANVAS_WIDTH)

        context.reportProgress("正在绘制表格")
        val image = renderTable(titleValue, headers, grid, fontSizeValue.toFloat(), canvasWidth)
        val base = args.stringOrNull(ToolParams.FILE_NAME) ?: (FILE_PREFIX + System.currentTimeMillis())
        val target = workspace.uniqueTarget("$base.png")
        val saved = runCatching {
            target.parentFile?.let { if (!it.exists()) it.mkdirs() }
            ImageIO.write(image, "png", target)
        }.getOrDefault(false)
        if (!saved) return@withContext failure("表格保存失败：${workspace.displayPath(target)}")

        val builder = StringBuilder()
        builder.appendLine("已生成表格 ${workspace.displayPath(target)}（${image.width}×${image.height}，${grid.size} 行）。")
        if (args.booleanOr(ToolParams.PRINT_AFTER_DRAW, true)) {
            builder.appendLine(PrintBridge.printImageFile(target, "表格"))
        }
        ToolOutput(
            content = builder.toString().trim(),
            artifacts = listOf(imageArtifact(target)),
        )
    }

    /** 渲染等宽表格：按内容计算列宽，超宽时等比缩小字号以适配打印头宽度。 */
    private fun renderTable(
        title: String?,
        headers: List<String>,
        rows: List<List<String>>,
        fontSize: Float,
        canvasWidth: Int,
    ): BufferedImage {
        val columns = headers.size
        val border = BORDER_WIDTH
        val available = (canvasWidth - (columns + 1) * border).coerceAtLeast(1f)

        val probe = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try {
            fun measure(scale: Float): FloatArray {
                probe.font = Font(FONT_FAMILY, Font.PLAIN, (fontSize * scale).toInt().coerceAtLeast(1))
                val metrics = probe.fontMetrics
                val padding = fontSize * scale * CELL_PADDING_RATIO
                return FloatArray(columns) { column ->
                    var widest = metrics.stringWidth(headers[column]).toFloat()
                    rows.forEach { row ->
                        widest = max(widest, metrics.stringWidth(row.getOrElse(column) { "" }).toFloat())
                    }
                    widest + padding * 2
                }
            }

            var widths = measure(1f)
            var total = widths.sum()
            if (total > available) {
                val scale = (available / total).coerceIn(MIN_FONT_SCALE, 1f)
                widths = measure(scale)
                total = widths.sum()
            }
            val effectiveFont = (fontSize * (available / total).coerceAtMost(1f))
                .coerceAtLeast(MIN_FONT_SIZE.toFloat())

            val rowHeight = effectiveFont * ROW_HEIGHT_RATIO
            val titleHeight = if (title.isNullOrBlank()) 0f else effectiveFont * TITLE_HEIGHT_RATIO
            val tableWidth = total + (columns + 1) * border
            val tableHeight = rowHeight * (rows.size + 1) + border
            val bitmapWidth = canvasWidth
            val bitmapHeight = (titleHeight + tableHeight + border * 2).toInt().coerceAtLeast(1)

            val image = BufferedImage(bitmapWidth, bitmapHeight, BufferedImage.TYPE_INT_ARGB)
            val g = ImageOps.graphicsOf(image)
            try {
                g.color = Color.WHITE
                g.fillRect(0, 0, bitmapWidth, bitmapHeight)

                val offsetX = ((canvasWidth - tableWidth) / 2f).coerceAtLeast(0f)
                val offsetY = border

                if (!title.isNullOrBlank()) {
                    g.color = Color.BLACK
                    g.font = Font(FONT_FAMILY, Font.PLAIN, (effectiveFont * TITLE_FONT_RATIO).toInt().coerceAtLeast(1))
                    val titleMetrics = g.fontMetrics
                    val titleWidth = titleMetrics.stringWidth(title).toFloat()
                    g.drawString(title, canvasWidth / 2f - titleWidth / 2f, offsetY + titleHeight * 0.75f)
                }

                val tableTop = offsetY + titleHeight
                val totalRows = rows.size + 1
                val tableBottom = tableTop + tableHeight

                // 表头底色（首行）
                g.color = HEADER_COLOR
                g.fillRect(offsetX.toInt(), tableTop.toInt(), tableWidth.toInt(), rowHeight.toInt())

                // 横线：每行上边界 + 最底部一条
                g.color = Color.BLACK
                g.stroke = BasicStroke(border)
                for (line in 0..totalRows) {
                    val y = tableTop + line * rowHeight
                    g.drawLine(offsetX.toInt(), y.toInt(), (offsetX + tableWidth).toInt(), y.toInt())
                }

                // 竖线
                g.drawLine(offsetX.toInt(), tableTop.toInt(), offsetX.toInt(), tableBottom.toInt())
                var x = offsetX
                widths.forEach { columnWidth ->
                    x += columnWidth + border
                    g.drawLine(x.toInt(), tableTop.toInt(), x.toInt(), tableBottom.toInt())
                }

                // 文本：逐行垂直居中绘制
                g.font = Font(FONT_FAMILY, Font.PLAIN, effectiveFont.toInt().coerceAtLeast(1))
                val metrics = g.fontMetrics
                val baselineOffset = (rowHeight - (metrics.ascent + metrics.descent)) / 2f + metrics.ascent
                val textGap = effectiveFont * CELL_PADDING_RATIO

                fun drawRow(cells: List<String>, rowTop: Float) {
                    var cellX = offsetX + border / 2f
                    headers.indices.forEach { column ->
                        val text = cells.getOrElse(column) { "" }
                        g.drawString(text, cellX + textGap, rowTop + baselineOffset)
                        cellX += widths[column] + border
                    }
                }
                drawRow(headers, tableTop)
                rows.forEachIndexed { index, row -> drawRow(row, tableTop + (index + 1) * rowHeight) }
            } finally {
                g.dispose()
            }
            return image
        } finally {
            probe.dispose()
        }
    }

    private companion object {
        const val FILE_PREFIX = "table_"
        const val DEFAULT_FONT_SIZE = 26
        const val MIN_FONT_SIZE = 8
        const val MAX_FONT_SIZE = 64
        const val MIN_CANVAS_WIDTH = 64
        const val BORDER_WIDTH = 2f
        const val CELL_PADDING_RATIO = 0.45f
        const val ROW_HEIGHT_RATIO = 2.0f
        const val TITLE_HEIGHT_RATIO = 2.0f
        const val TITLE_FONT_RATIO = 1.2f
        const val MIN_FONT_SCALE = 0.35f
        const val FONT_FAMILY = "Monospaced"
        val HEADER_COLOR: Color = Color(0xEE, 0xEE, 0xEE)
    }
}
