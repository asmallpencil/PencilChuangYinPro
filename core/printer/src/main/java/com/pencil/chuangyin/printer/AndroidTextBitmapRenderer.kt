package com.pencil.chuangyin.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 android.graphics.Canvas + StaticLayout 的文本渲染实现。
 *
 * 关键设计：
 * - StaticLayout 只用于“计算换行”，真正绘制时逐行进行，并按每行实测宽度显式计算
 *   横向偏移——因此即使全文不足一行，居中/右对齐也确定生效（不依赖 StaticLayout
 *   对末行对齐的不确定行为）。
 * - 按固定页高流式产出 384 点宽位图，任意时刻只持有一页，适配 17 万字级巨量文本。
 */
@Singleton
class AndroidTextBitmapRenderer @Inject constructor(
    private val printerSettings: PrinterSettings,
) : TextBitmapRenderer {

    override fun renderPages(
        doc: TextDoc,
        typeface: Typeface?,
        onPage: (Bitmap, Int, Int) -> Unit,
    ) {
        val lines = flatten(doc, typeface)
        emitPages(lines, PrinterCommand.PAGE_TARGET_HEIGHT_PX, limitPages = Int.MAX_VALUE, onPage)
    }

    override fun renderPreview(doc: TextDoc, typeface: Typeface?, maxHeightPx: Int): Bitmap {
        val width = printerSettings.printWidthPx()
        val lines = flatten(doc, typeface)
        var preview: Bitmap? = null
        emitPages(lines, maxHeightPx.coerceAtLeast(1), limitPages = 1) { bitmap, _, _ ->
            preview = bitmap
        }
        return preview ?: Bitmap.createBitmap(
            width, 1, Bitmap.Config.ARGB_8888,
        ).apply { eraseColor(Color.WHITE) }
    }

    /** 标题（单行，若有）+ 正文，顺序展开为统一的可绘制行序列。 */
    private fun flatten(doc: TextDoc, typeface: Typeface?): List<Line> {
        val width = printerSettings.printWidthPx()
        val result = ArrayList<Line>()
        if (doc.title.isNotBlank()) {
            val paint = textPaint(doc.titleSizeSp, typeface, bold = true)
            val layout = staticLayout(doc.title, width, paint, doc.lineSpacingMultiplier, maxLines = 1)
            result += collectLines(doc.title, layout, paint, width, doc.align)
        }
        if (doc.content.isNotEmpty()) {
            val paint = textPaint(doc.fontSizeSp, typeface, bold = doc.bold)
            val layout = staticLayout(doc.content, width, paint, doc.lineSpacingMultiplier, maxLines = Int.MAX_VALUE)
            result += collectLines(doc.content, layout, paint, width, doc.align)
        }
        return result
    }

    /** 把 StaticLayout 的每个视觉行转为带“实测宽度/基线/行高/画笔”的可绘制行。 */
    private fun collectLines(
        text: String,
        layout: StaticLayout,
        paint: TextPaint,
        width: Int,
        align: PrintAlign,
    ): List<Line> {
        val lines = ArrayList<Line>(layout.lineCount)
        for (i in 0 until layout.lineCount) {
            val start = layout.getLineStart(i)
            val end = layout.getLineVisibleEnd(i)
            val run = if (end > start) text.substring(start, end) else ""
            val lineWidth = if (run.isEmpty()) 0f else paint.measureText(run)
            val x = when (align) {
                PrintAlign.Start -> 0f
                PrintAlign.Center -> ((width - lineWidth) / 2f).coerceAtLeast(0f)
                PrintAlign.End -> (width - lineWidth).coerceAtLeast(0f)
            }
            lines += Line(
                text = run,
                x = x,
                height = layout.getLineBottom(i) - layout.getLineTop(i),
                baselineInset = layout.getLineBaseline(i) - layout.getLineTop(i),
                paint = paint,
            )
        }
        return lines
    }

    /** 按页高把行聚组，逐组绘制为白底位图并回调。 */
    private fun emitPages(
        lines: List<Line>,
        pageHeightPx: Int,
        limitPages: Int,
        onPage: (Bitmap, Int, Int) -> Unit,
    ) {
        if (lines.isEmpty()) {
            val empty = Bitmap.createBitmap(
                printerSettings.printWidthPx(), 1, Bitmap.Config.ARGB_8888,
            ).apply { eraseColor(Color.WHITE) }
            onPage(empty, 0, 1)
            return
        }

        val groups = ArrayList<IntRange>()
        var groupStart = 0
        var groupHeight = 0
        for (i in lines.indices) {
            val h = lines[i].height
            if (groupHeight > 0 && groupHeight + h > pageHeightPx) {
                groups += groupStart until i
                groupStart = i
                groupHeight = 0
            }
            groupHeight += h
        }
        groups += groupStart..lines.lastIndex
        val pageCount = groups.size.coerceAtMost(limitPages)

        for (pageIndex in 0 until pageCount) {
            val range = groups[pageIndex]
            var height = 0
            for (i in range) height += lines[i].height
            val bitmap = Bitmap.createBitmap(
                printerSettings.printWidthPx(),
                height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            var y = 0
            for (i in range) {
                val line = lines[i]
                if (line.text.isNotEmpty()) {
                    canvas.drawText(line.text, line.x, (y + line.baselineInset).toFloat(), line.paint)
                }
                y += line.height
            }
            onPage(bitmap, pageIndex, pageCount)
        }
    }

    /**
     * 粗体在廉价热敏机上必须“肉眼可辨”：原生 Typeface.BOLD 对中文多为合成粗体、
     * 膨胀仅约 1%~2% 字身，二值化后几乎看不出。这里叠加 fakeBold + FILL_AND_STROKE
     * 描边（约 5% 字身宽度），使开关粗体前后在 384 点热敏输出上差异显著。
     */
    private fun textPaint(sizeSp: Float, typeface: Typeface?, bold: Boolean): TextPaint {
        val base = typeface ?: Typeface.DEFAULT
        val tf = if (bold) Typeface.create(base, Typeface.BOLD) else base
        return TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = tf
            textSize = sizeSp
            color = Color.BLACK
            if (bold) {
                isFakeBoldText = true
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = sizeSp * BOLD_STROKE_WIDTH_RATIO
            }
        }
    }

    private fun staticLayout(
        text: String,
        width: Int,
        paint: TextPaint,
        lineSpacingMultiplier: Float,
        maxLines: Int,
    ): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            // 统一用普通对齐排版，仅借其换行结果；实际对齐在逐行绘制时显式完成。
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .build()

    private class Line(
        val text: String,
        val x: Float,
        val height: Int,
        val baselineInset: Int,
        val paint: TextPaint,
    )
}

/** 粗体描边宽度占字号比例（5% 字身，热敏二值化后仍明显）。 */
private const val BOLD_STROKE_WIDTH_RATIO = 0.05f
