package com.pencil.chuangyin.feature.ai.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 图表类型。 */
enum class ChartType { BAR, LINE }

/**
 * AI 内容生成工具的统一位图渲染器：表格、图表、排版笔记。
 *
 * 设计目标：
 * - **预览即打印**：渲染完成后立即做一次硬阈值二值化（默认 128），屏幕上看到的黑白
 *   与热敏打印结果完全一致，不会因为打印管线再做抖动而出现「同一条线一边粗一边细」。
 * - **不缩放文字**：所有文字都按最终像素尺寸直接绘制，列宽不够时只缩短文字/降字号，
 *   绝不把整张位图再缩放，避免笔画粗细随列宽变化。
 * - **中文友好**：统一使用系统无衬线字体（[Typeface.DEFAULT]），不依赖等宽字体，
 *   中文与数字混排不会错位。
 */
internal object StudioRenderer {

    const val DEFAULT_TABLE_FONT = 24f
    const val MIN_TABLE_FONT = 9f
    const val MAX_TABLE_FONT = 48f
    const val DEFAULT_NOTE_FONT = 26f
    const val MIN_NOTE_FONT = 12f
    const val MAX_NOTE_FONT = 56f

    private const val BORDER = 2f
    private const val OUTER_MARGIN = 14f
    private const val BINARIZE_THRESHOLD = 128

    /**
     * 超采样倍数：按逻辑尺寸绘制，输出 2 倍像素位图。
     *
     * 打印头只有 384 点宽，若直接在 384 像素上绘制，聊天里的预览会被放大成马赛克，
     * 细节笔画也会糊掉（用户反馈「画质被压缩、文字缺部件」）。这里保持与打印一致的
     * 版式（列宽/字号/换行都不变），只是以 2 倍分辨率渲染，预览清晰、打印时再高质量降采样。
     */
    private const val SUPERSAMPLE = 2f

    /** 表格渲染结果。 */
    class TableResult(val bitmap: Bitmap, val truncatedCount: Int)

    /** 渲染一张带表头、网格线与标题的表格。 */
    fun renderTable(
        title: String?,
        headers: List<String>,
        rows: List<List<String>>,
        requestedFont: Float,
        canvasWidth: Int,
    ): TableResult {
        val columns = headers.size
        val usable = (canvasWidth - OUTER_MARGIN * 2 - (columns + 1) * BORDER).coerceAtLeast(columns.toFloat())
        val textPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
            textSize = requestedFont
            color = Color.BLACK
        }

        var fontSize = requestedFont.coerceIn(MIN_TABLE_FONT, MAX_TABLE_FONT)
        textPaint.textSize = fontSize
        var widths = measureColumns(headers, rows, textPaint, fontSize, columns)
        var tableWidth = widths.sum() + (columns + 1) * BORDER
        var truncated = 0

        // 列宽不够时先逐级降字号；降到下限后按比例压缩列宽，并在绘制时截断文字。
        while (tableWidth > usable && fontSize > MIN_TABLE_FONT) {
            fontSize -= 1f
            textPaint.textSize = fontSize
            widths = measureColumns(headers, rows, textPaint, fontSize, columns)
            tableWidth = widths.sum() + (columns + 1) * BORDER
        }
        if (tableWidth > usable) {
            val factor = usable / tableWidth
            widths = FloatArray(columns) { widths[it] * factor }
            tableWidth = widths.sum() + (columns + 1) * BORDER
            truncated = countOverflow(headers, rows, textPaint, widths, fontSize)
        }

        val metrics = textPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val padding = max(6f, fontSize * 0.45f)
        val rowHeight = max(textHeight + padding * 2, fontSize * 1.7f)
        val titleSize = fontSize * 1.3f
        val titleArea = if (title.isNullOrBlank()) 0f else titleSize * 2.1f
        val tableHeight = (rows.size + 1) * (rowHeight + BORDER) + BORDER
        val bitmapHeight = (OUTER_MARGIN * 2 + titleArea + tableHeight).roundToInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(
            (canvasWidth * SUPERSAMPLE).roundToInt(),
            (bitmapHeight * SUPERSAMPLE).roundToInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(SUPERSAMPLE, SUPERSAMPLE)

        if (!title.isNullOrBlank()) {
            val titlePaint = Paint().apply {
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
                textSize = titleSize
                color = Color.BLACK
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                truncate(titlePaint, title, canvasWidth - OUTER_MARGIN * 2),
                canvasWidth / 2f,
                OUTER_MARGIN + titleSize * 1.55f,
                titlePaint,
            )
        }

        val tableLeft = ((canvasWidth - tableWidth) / 2f).coerceAtLeast(OUTER_MARGIN)
        val tableTop = OUTER_MARGIN + titleArea

        // 表头浅色底：先铺底色，再压网格线，保证线条干净。
        val headerPaint = Paint().apply { color = Color.parseColor("#E6E6E6") }
        canvas.drawRect(
            tableLeft + BORDER,
            tableTop + BORDER,
            tableLeft + tableWidth - BORDER,
            tableTop + BORDER + rowHeight,
            headerPaint,
        )

        val gridPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = BORDER
            style = Paint.Style.FILL
        }
        // 横线（顶部、每行之间、底部）
        val tableBottom = tableTop + tableHeight
        for (index in 0..rows.size + 1) {
            val y = tableTop + index * (rowHeight + BORDER)
            canvas.drawRect(tableLeft, y, tableLeft + tableWidth, min(y + BORDER, tableBottom), gridPaint)
        }
        // 竖线
        var x = tableLeft
        canvas.drawRect(x, tableTop, x + BORDER, tableBottom, gridPaint)
        widths.forEach { width ->
            x += width + BORDER
            canvas.drawRect(x, tableTop, min(x + BORDER, tableLeft + tableWidth), tableBottom, gridPaint)
        }

        val headerPaintText = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textSize = fontSize
            color = Color.BLACK
        }
        val bodyPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
            textSize = fontSize
            color = Color.BLACK
        }

        fun drawRow(cells: List<String>, rowIndex: Int, bold: Boolean) {
            val paint = if (bold) headerPaintText else bodyPaint
            val cellTop = tableTop + BORDER + rowIndex * (rowHeight + BORDER)
            var cellLeft = tableLeft + BORDER
            headers.indices.forEach { column ->
                val maxTextWidth = widths[column] - padding * 2
                val text = truncate(paint, cells.getOrElse(column) { "" }, maxTextWidth)
                val baseline = cellTop + (rowHeight - textHeight) / 2f - metrics.ascent
                canvas.drawText(text, cellLeft + padding, baseline, paint)
                cellLeft += widths[column] + BORDER
            }
        }

        drawRow(headers, 0, bold = true)
        rows.forEachIndexed { index, row -> drawRow(row, index + 1, bold = false) }

        binarize(bitmap)
        return TableResult(bitmap, truncated)
    }

    /** 渲染柱状图或折线图。 */
    fun renderChart(
        type: ChartType,
        title: String?,
        labels: List<String>,
        values: List<Double>,
        unit: String?,
        canvasWidth: Int,
        canvasHeight: Int,
    ): Bitmap {
        val count = min(labels.size, values.size)
        val width = canvasWidth
        val height = canvasHeight.coerceIn(220, 1400)
        val bitmap = Bitmap.createBitmap(
            (width * SUPERSAMPLE).roundToInt(),
            (height * SUPERSAMPLE).roundToInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(SUPERSAMPLE, SUPERSAMPLE)

        val left = 48f
        val right = width - 20f
        val top = if (title.isNullOrBlank()) 26f else 58f
        val bottom = height - 40f
        if (right <= left || bottom <= top || count <= 0) return bitmap

        val titlePaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textSize = 26f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        if (!title.isNullOrBlank()) {
            canvas.drawText(truncate(titlePaint, title, width - 24f), width / 2f, 36f, titlePaint)
        }

        val maxValue = max(values.take(count).maxOrNull() ?: 1.0, 1e-6)
        val minValue = min(values.take(count).minOrNull() ?: 0.0, 0.0)
        val span = (maxValue - minValue).takeIf { it > 1e-6 } ?: 1.0

        val axisPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(left, top, left, bottom, axisPaint)
        canvas.drawLine(left, bottom, right, bottom, axisPaint)

        val labelPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
            textSize = 16f
            color = Color.BLACK
        }
        val gridPaint = Paint().apply {
            color = Color.parseColor("#999999")
            strokeWidth = 1f
        }
        // 水平网格与 Y 轴刻度
        repeat(5) { index ->
            val ratio = index / 4f
            val y = bottom - ratio * (bottom - top)
            canvas.drawLine(left, y, right, y, gridPaint)
            val value = minValue + span * ratio
            val text = formatValue(value) + (unit?.trim()?.takeIf { it.isNotEmpty() }?.let { " $it" } ?: "")
            canvas.drawText(text, 2f, y + 5f, labelPaint)
        }

        val plotWidth = right - left
        val slot = plotWidth / count
        val zeroY = bottom - ((0.0 - minValue) / span).toFloat() * (bottom - top)

        if (type == ChartType.BAR) {
            val barPaint = Paint().apply { color = Color.BLACK }
            val barWidth = (slot * 0.58f).coerceAtLeast(3f)
            values.take(count).forEachIndexed { index, raw ->
                val value = raw
                val x = left + slot * index + (slot - barWidth) / 2f
                val valueY = bottom - ((value - minValue) / span).toFloat() * (bottom - top)
                val topY = min(valueY, zeroY)
                val bottomY = max(valueY, zeroY)
                canvas.drawRect(RectF(x, topY, x + barWidth, bottomY), barPaint)
                val valueText = formatValue(value)
                val valueWidth = labelPaint.measureText(valueText)
                val textX = (x + barWidth / 2f - valueWidth / 2f).coerceIn(0f, width - valueWidth)
                val textY = if (value >= 0) valueY - 6f else valueY + 18f
                canvas.drawText(valueText, textX, textY, labelPaint)
            }
        } else {
            val pointPaint = Paint().apply {
                color = Color.BLACK
                strokeWidth = 2f
                style = Paint.Style.STROKE
            }
            val dotPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            var previousX = 0f
            var previousY = 0f
            values.take(count).forEachIndexed { index, value ->
                val x = left + slot * index + slot / 2f
                val y = bottom - ((value - minValue) / span).toFloat() * (bottom - top)
                if (index > 0) canvas.drawLine(previousX, previousY, x, y, pointPaint)
                canvas.drawCircle(x, y, 3f, dotPaint)
                previousX = x
                previousY = y
            }
        }

        // X 轴标签：过密时抽稀，且按槽宽截断。
        val step = if (count > 10) count / 10 + 1 else 1
        labels.take(count).forEachIndexed { index, label ->
            if (index % step != 0) return@forEachIndexed
            val x = left + slot * index + slot / 2f
            val text = truncate(labelPaint, label, slot * step + 4f)
            val textWidth = labelPaint.measureText(text)
            canvas.drawText(text, (x - textWidth / 2f).coerceIn(0f, width - textWidth), bottom + 22f, labelPaint)
        }

        binarize(bitmap)
        return bitmap
    }

    /** 渲染一张标题 + 自动换行正文的笔记图片。 */
    fun renderNote(
        title: String?,
        content: String,
        requestedFont: Float,
        canvasWidth: Int,
    ): Bitmap {
        val fontSize = requestedFont.coerceIn(MIN_NOTE_FONT, MAX_NOTE_FONT)
        val margin = 26f
        val titleSize = fontSize * 1.35f
        val titleArea = if (title.isNullOrBlank()) 0f else titleSize * 2.0f
        val lineHeight = fontSize * 1.5f

        val bodyPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
            textSize = fontSize
            color = Color.BLACK
        }
        val titlePaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textSize = titleSize
            color = Color.BLACK
        }

        val maxWidth = canvasWidth - margin * 2
        val lines = wrap(bodyPaint, content, maxWidth)
        val bitmapHeight = (margin * 2 + titleArea + lines.size * lineHeight).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(
            (canvasWidth * SUPERSAMPLE).roundToInt(),
            (bitmapHeight * SUPERSAMPLE).roundToInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(SUPERSAMPLE, SUPERSAMPLE)

        if (!title.isNullOrBlank()) {
            val text = truncate(titlePaint, title, maxWidth)
            canvas.drawText(text, (canvasWidth - titlePaint.measureText(text)) / 2f, margin + titleSize, titlePaint)
            val dividerY = margin + titleArea - titleSize * 0.45f
            canvas.drawRect(margin, dividerY, canvasWidth - margin, dividerY + 2f, Paint().apply { color = Color.BLACK })
        }

        val metrics = bodyPaint.fontMetrics
        var baseline = margin + titleArea - metrics.ascent
        lines.forEach { line ->
            canvas.drawText(line, margin, baseline, bodyPaint)
            baseline += lineHeight
        }

        binarize(bitmap)
        return bitmap
    }

    /** PNG 编码。 */
    fun encodePng(bitmap: Bitmap): ByteArray {
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
        return buffer.toByteArray()
    }

    /** 把彩色位图硬阈值成纯黑白：预览与打印一致，避免打印管线再抖动导致笔画粗细不均。 */
    private fun binarize(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return
        val row = IntArray(width)
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val color = row[x]
                val alpha = Color.alpha(color)
                val gray = if (alpha == 0) {
                    255
                } else {
                    (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
                }
                row[x] = if (gray < BINARIZE_THRESHOLD) Color.BLACK else Color.WHITE
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
    }

    private fun measureColumns(
        headers: List<String>,
        rows: List<List<String>>,
        paint: Paint,
        fontSize: Float,
        columns: Int,
    ): FloatArray {
        val padding = max(6f, fontSize * 0.45f) * 2
        return FloatArray(columns) { column ->
            var widest = paint.measureText(headers.getOrElse(column) { "" })
            rows.forEach { row ->
                widest = max(widest, paint.measureText(row.getOrElse(column) { "" }))
            }
            widest + padding
        }
    }

    private fun countOverflow(
        headers: List<String>,
        rows: List<List<String>>,
        paint: Paint,
        widths: FloatArray,
        fontSize: Float,
    ): Int {
        val padding = max(6f, fontSize * 0.45f) * 2
        var count = 0
        headers.indices.forEach { column ->
            if (paint.measureText(headers[column]) > widths[column] - padding) count++
            rows.forEach { row ->
                if (paint.measureText(row.getOrElse(column) { "" }) > widths[column] - padding) count++
            }
        }
        return count
    }

    /** 超出宽度时截断并补省略号。 */
    private fun truncate(paint: Paint, text: String, maxWidth: Float): String {
        if (maxWidth <= 0f || text.isEmpty()) return ""
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellipsisWidth = paint.measureText(ellipsis)
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--
        }
        return if (end <= 0) "" else text.substring(0, end) + ellipsis
    }

    /** 按像素宽度逐字换行，兼容中英文混排与显式换行。 */
    private fun wrap(paint: Paint, text: String, maxWidth: Float): List<String> {
        val result = ArrayList<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { paragraph ->
            if (paragraph.isEmpty()) {
                result += ""
                return@forEach
            }
            var line = StringBuilder()
            var lineWidth = 0f
            paragraph.forEach { ch ->
                val charWidth = paint.measureText(ch.toString())
                if (lineWidth + charWidth > maxWidth && line.isNotEmpty()) {
                    result += line.toString()
                    line = StringBuilder()
                    lineWidth = 0f
                }
                line.append(ch)
                lineWidth += charWidth
            }
            if (line.isNotEmpty()) result += line.toString()
        }
        return result
    }

    private fun formatValue(value: Double): String {
        val rounded = if (abs(value - value.roundToInt()) < 0.05) value.roundToInt().toString()
        else String.format(java.util.Locale.US, "%.1f", value)
        return rounded
    }
}
