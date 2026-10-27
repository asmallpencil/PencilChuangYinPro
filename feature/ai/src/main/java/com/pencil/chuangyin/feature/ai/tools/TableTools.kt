package com.pencil.chuangyin.feature.ai.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.agent.runtime.ToolCallScope
import com.pencil.chuangyin.printer.ImagePrintOptions
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterController
import com.pencil.chuangyin.printer.PrinterSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 表格打印工具：把结构化数据渲染为等宽字体的网格表格图片，保存到工作区并（默认）直接打印。
 *
 * 覆盖「按要求制作一个表格并打印」这类需求，避免模型手工拼 ASCII 表格再逐行打印。
 */
@Singleton
class TableTools @Inject constructor(
    private val workspace: Workspace,
    private val scope: ToolCallScope,
    private val printer: PrinterController,
    private val settings: PrinterSettings,
) {

    /**
     * 把数据渲染成表格图片并保存到工作区，默认立即打印。headers 为列名数组；
     * rows 每行一个字符串、单元格用 | 分隔（如 "姓名|年龄"）。列数不足自动补空、超出自动截断。
     */
    @Tool
    suspend fun print_table(
        @Param(description = "表头列名数组。") headers: List<String>,
        @Param(description = "数据行数组，每行单元格以 | 分隔。") rows: List<String>,
        @Param(description = "可选表格标题。") title: String?,
        @Param(description = "字号像素，默认 26。") font_size: Int?,
        @Param(description = "生成后是否立即打印，默认 true。") print: Boolean?,
        @Param(description = "可选文件名（不含路径），默认自动命名。") file_name: String?,
    ): ToolOutput {
        if (headers.isEmpty()) return fileFailure("缺少参数 headers，或表头为空。")
        val grid = rows.map { line -> line.split('|').map { it.trim() } }
        if (grid.isEmpty()) return fileFailure("缺少参数 rows，或数据行为空。")

        val fontSizeValue = (font_size ?: DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        val titleValue = title?.trim()?.takeIf { it.isNotEmpty() }
        val canvasWidth = settings.printWidthPx().coerceAtLeast(MIN_CANVAS_WIDTH)

        scope.current?.reportProgress("正在绘制表格")
        val bitmap = withContext(Dispatchers.IO) {
            renderTable(titleValue, headers, grid, fontSizeValue.toFloat(), canvasWidth)
        }
        val base = file_name?.trim()?.takeIf { it.isNotEmpty() } ?: (FILE_PREFIX + System.currentTimeMillis())
        val target = workspace.uniqueTarget("$base.png")
        val saved = withContext(Dispatchers.IO) { savePng(bitmap, target) }
        val width = bitmap.width
        val height = bitmap.height
        bitmap.recycle()
        if (!saved) return fileFailure("表格保存失败：${workspace.displayPath(target)}")

        val builder = StringBuilder()
        builder.appendLine("已生成表格 ${workspace.displayPath(target)}（$width×$height，${grid.size} 行）。")
        if (print ?: true) {
            builder.appendLine(printTableFile(target))
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

    /** 渲染等宽表格：按内容计算列宽，超宽时等比缩小字号以适配打印头宽度。 */
    private fun renderTable(
        title: String?,
        headers: List<String>,
        rows: List<List<String>>,
        fontSize: Float,
        canvasWidth: Int,
    ): Bitmap {
        val columns = headers.size
        val border = BORDER_WIDTH
        val available = (canvasWidth - (columns + 1) * border).coerceAtLeast(1f)

        fun measure(scale: Float): FloatArray {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                textSize = fontSize * scale
            }
            val padding = fontSize * scale * CELL_PADDING_RATIO
            return FloatArray(columns) { column ->
                var widest = paint.measureText(headers[column])
                rows.forEach { row -> widest = max(widest, paint.measureText(row.getOrElse(column) { "" })) }
                widest + padding * 2
            }.also { paint.typeface = null }
        }

        var widths = measure(1f)
        var total = widths.sum()
        if (total > available) {
            val scale = (available / total).coerceIn(MIN_FONT_SCALE, 1f)
            widths = measure(scale)
            total = widths.sum()
        }
        val effectiveFont = (fontSize * (available / total).coerceAtMost(1f)).coerceAtLeast(MIN_FONT_SIZE.toFloat())

        val rowHeight = effectiveFont * ROW_HEIGHT_RATIO
        val titleHeight = if (title.isNullOrBlank()) 0f else effectiveFont * TITLE_HEIGHT_RATIO
        val tableWidth = total + (columns + 1) * border
        val tableHeight = rowHeight * (rows.size + 1) + border
        val bitmapWidth = canvasWidth
        val bitmapHeight = (titleHeight + tableHeight + border * 2).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val offsetX = ((canvasWidth - tableWidth) / 2f).coerceAtLeast(0f)
        val offsetY = border

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = effectiveFont
            color = Color.BLACK
        }
        val borderPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = border
        }
        val headerPaint = Paint().apply { color = Color.parseColor("#EEEEEE") }

        if (!title.isNullOrBlank()) {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                textSize = effectiveFont * TITLE_FONT_RATIO
                color = Color.BLACK
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(title, canvasWidth / 2f, offsetY + titleHeight * 0.75f, titlePaint)
        }

        val tableTop = offsetY + titleHeight
        val totalRows = rows.size + 1
        val tableBottom = tableTop + tableHeight

        // 表头底色（首行）
        canvas.drawRect(offsetX, tableTop, offsetX + tableWidth, tableTop + rowHeight, headerPaint)

        // 横线：每行上边界 + 最底部一条
        for (line in 0..totalRows) {
            val y = tableTop + line * rowHeight
            canvas.drawLine(offsetX, y, offsetX + tableWidth, y, borderPaint)
        }

        // 竖线
        canvas.drawLine(offsetX, tableTop, offsetX, tableBottom, borderPaint)
        var x = offsetX
        widths.forEach { columnWidth ->
            x += columnWidth + border
            canvas.drawLine(x, tableTop, x, tableBottom, borderPaint)
        }

        // 文本：逐行垂直居中绘制
        val metrics = textPaint.fontMetrics
        val baselineOffset = (rowHeight - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
        fun drawRow(cells: List<String>, rowTop: Float) {
            var cellX = offsetX + border / 2f
            headers.indices.forEach { column ->
                val text = cells.getOrElse(column) { "" }
                canvas.drawText(text, cellX + effectiveFont * CELL_PADDING_RATIO, rowTop + baselineOffset, textPaint)
                cellX += widths[column] + border
            }
        }
        drawRow(headers, tableTop)
        rows.forEachIndexed { index, row -> drawRow(row, tableTop + (index + 1) * rowHeight) }

        return bitmap
    }

    private fun savePng(bitmap: Bitmap, target: File): Boolean = runCatching {
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
        workspace.writeBytes(target, buffer.toByteArray())
    }.getOrDefault(false)

    private suspend fun printTableFile(file: File): String {
        if (!printer.isConnected) return "未打印：打印机未连接，请提示用户到首页连接后再试。"
        val printable = workspace.materialize(file) ?: return "打印失败：无法读取 ${file.name}。"
        scope.current?.reportProgress("正在打印表格")
        return when (val result = printer.printImagePages(
            pageCount = 1,
            options = ImagePrintOptions(),
            pageLoader = { BitmapFactory.decodeFile(printable.absolutePath) },
        )) {
            is PrintResult.Success -> "表格已发送到打印机。"
            is PrintResult.Error -> "打印失败：${result.code}${result.detail?.let { "（$it）" } ?: ""}"
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
    }
}
