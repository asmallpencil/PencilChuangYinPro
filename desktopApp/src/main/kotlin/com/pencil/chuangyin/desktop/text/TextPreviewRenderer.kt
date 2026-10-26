package com.pencil.chuangyin.desktop.text

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface

/**
 * 文本预览渲染器 —— 逐行复刻安卓端 `AndroidTextBitmapRenderer`：
 *  - 固定 384 点宽（热敏纸宽）、白底、单页高上限，标题（强制加粗、单行）+ 正文顺序排布；
 *  - 逐视觉行实测宽度后显式计算 x（Start=0 / Center / End），保证末行对齐确定生效；
 *  - 加粗 = 约 5% 字身描边（FILL_AND_STROKE），热敏二值化后仍明显。
 *
 * 与安卓端的差异仅在排版引擎：Android 用 StaticLayout 求换行，桌面用 Skia 字体实测宽度
 * 做贪心折行（CJK 逐字可断，西文按空格断词），最终位图尺寸、行高、对齐结果一致。
 */
object TextPreviewRenderer {

    /** 粗体描边宽度占字号比例（与安卓端 BOLD_STROKE_WIDTH_RATIO 一致）。 */
    private const val BOLD_STROKE_WIDTH_RATIO = 0.05f

    private val typefaceCache = ConcurrentHashMap<String, Typeface?>()

    /** 读取自定义字体（结果按路径缓存；失败返回 null 时回退系统字体）。 */
    fun loadTypeface(path: String?): Typeface? {
        if (path.isNullOrBlank()) return null
        return typefaceCache.getOrPut(path) {
            runCatching {
                val file = File(path)
                if (!file.isFile) return@runCatching null
                FontMgr.default.makeFromData(Data.makeFromBytes(file.readBytes()), 0)
            }.getOrNull()
        }
    }

    /** 系统默认字体：优先中文字族，避免中文渲染成方框。 */
    private val defaultTypeface: Typeface? by lazy {
        val mgr = FontMgr.default
        listOf(
            "Microsoft YaHei",
            "Microsoft YaHei UI",
            "SimSun",
            "Noto Sans CJK SC",
            "Noto Sans SC",
            "Arial Unicode MS",
        ).firstNotNullOfOrNull { family ->
            runCatching { mgr.matchFamilyStyle(family, FontStyle.NORMAL) }.getOrNull()
        } ?: runCatching { mgr.matchFamilyStyle(null, FontStyle.NORMAL) }.getOrNull()
    }

    /**
     * 渲染预览位图：与打印管线同参数（384 点宽 + 预览高度上限）。
     * [maxHeightPx] 为预览区高度上限（安卓端 PREVIEW_MAX_HEIGHT_PX = 1500）。
     */
    fun renderPreview(doc: TextDoc, customFontPath: String?, maxHeightPx: Int): Image? = runCatching {
        val typeface = loadTypeface(customFontPath) ?: defaultTypeface
        val lines = flatten(doc, typeface)
        if (lines.isEmpty()) {
            return@runCatching blankImage(PrinterCommand.PRINT_WIDTH_PX, 1)
        }

        val pageHeightPx = maxHeightPx.coerceAtLeast(1)
        val group = ArrayList<Line>()
        var groupHeight = 0
        for (line in lines) {
            if (groupHeight > 0 && groupHeight + line.height > pageHeightPx) break
            group += line
            groupHeight += line.height
        }

        val width = PrinterCommand.PRINT_WIDTH_PX
        val height = groupHeight.coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(width, height)
        val canvas = surface.canvas
        canvas.clear(0xFFFFFFFF.toInt())
        var y = 0
        for (line in group) {
            if (line.text.isNotEmpty()) {
                canvas.drawString(
                    line.text,
                    line.x,
                    (y + line.baselineInset).toFloat(),
                    line.font,
                    line.paint,
                )
            }
            y += line.height
        }
        surface.makeImageSnapshot()
    }.getOrNull()

    /**
     * 渲染**打印用**的分页位图：按页高把整篇文档切成若干页，不丢内容。
     *
     * 与 [renderPreview] 的区别：预览只取首页且限制高度上限；打印必须把所有行排完，
     * 每页高度为 [pageHeightPx]（安卓端 PAGE_TARGET_HEIGHT_PX = 768）。
     */
    fun renderPrintPages(
        doc: TextDoc,
        customFontPath: String?,
        pageHeightPx: Int = PrinterCommand.PAGE_TARGET_HEIGHT_PX,
    ): List<Image> = runCatching {
        val typeface = loadTypeface(customFontPath) ?: defaultTypeface
        val lines = flatten(doc, typeface)
        if (lines.isEmpty()) return@runCatching emptyList<Image>()

        val pageHeight = pageHeightPx.coerceAtLeast(1)
        val pages = ArrayList<Image>()
        var group = ArrayList<Line>()
        var groupHeight = 0
        for (line in lines) {
            if (groupHeight > 0 && groupHeight + line.height > pageHeight) {
                pages += drawLines(group, groupHeight)
                group = ArrayList()
                groupHeight = 0
            }
            group += line
            groupHeight += line.height
        }
        if (group.isNotEmpty()) pages += drawLines(group, groupHeight)
        pages
    }.getOrDefault(emptyList())

    /** 预览图另存为 PNG（保存按钮用）；失败返回 null。 */
    fun encodePng(image: Image): ByteArray? = runCatching {
        image.encodeToData(EncodedImageFormat.PNG)?.bytes
    }.getOrNull()

    /** 把一组行绘制成一张 384 点宽的白底位图。 */
    private fun drawLines(lines: List<Line>, height: Int): Image {
        val width = PrinterCommand.PRINT_WIDTH_PX
        val surface = Surface.makeRasterN32Premul(width, height.coerceAtLeast(1))
        val canvas = surface.canvas
        canvas.clear(0xFFFFFFFF.toInt())
        var y = 0
        for (line in lines) {
            if (line.text.isNotEmpty()) {
                canvas.drawString(
                    line.text,
                    line.x,
                    (y + line.baselineInset).toFloat(),
                    line.font,
                    line.paint,
                )
            }
            y += line.height
        }
        return surface.makeImageSnapshot()
    }

    private fun blankImage(width: Int, height: Int): Image {
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.clear(0xFFFFFFFF.toInt())
        return surface.makeImageSnapshot()
    }

    /** 标题（单行，若有，强制加粗）+ 正文，顺序展开为统一的可绘制行序列。 */
    private fun flatten(doc: TextDoc, typeface: Typeface?): List<Line> {
        val result = ArrayList<Line>()
        if (doc.title.isNotBlank()) {
            val (font, paint) = textPaint(doc.titleSizeSp, typeface, bold = true)
            val lineHeight = lineHeight(font, doc.lineSpacingMultiplier)
            val baselineInset = baselineInset(font)
            // 标题仅取首行（对齐安卓端 maxLines = 1）。
            val first = breakLines(doc.title, font, PrinterCommand.PRINT_WIDTH_PX.toFloat()).firstOrNull() ?: ""
            result += lineOf(first, font, paint, doc.align, lineHeight, baselineInset)
        }
        if (doc.content.isNotEmpty()) {
            val (font, paint) = textPaint(doc.fontSizeSp, typeface, bold = doc.bold)
            val lineHeight = lineHeight(font, doc.lineSpacingMultiplier)
            val baselineInset = baselineInset(font)
            breakLines(doc.content, font, PrinterCommand.PRINT_WIDTH_PX.toFloat()).forEach { text ->
                result += lineOf(text, font, paint, doc.align, lineHeight, baselineInset)
            }
        }
        return result
    }

    private fun lineOf(
        text: String,
        font: Font,
        paint: Paint,
        align: PrintAlign,
        lineHeight: Int,
        baselineInset: Int,
    ): Line {
        val lineWidth = if (text.isEmpty()) 0f else font.measureTextWidth(text)
        val width = PrinterCommand.PRINT_WIDTH_PX.toFloat()
        val x = when (align) {
            PrintAlign.Start -> 0f
            PrintAlign.Center -> ((width - lineWidth) / 2f).coerceAtLeast(0f)
            PrintAlign.End -> (width - lineWidth).coerceAtLeast(0f)
        }
        return Line(text = text, x = x, height = lineHeight, baselineInset = baselineInset, font = font, paint = paint)
    }

    private fun textPaint(sizeSp: Float, typeface: Typeface?, bold: Boolean): Pair<Font, Paint> {
        val font = Font(typeface, sizeSp)
        val paint = Paint().apply {
            isAntiAlias = true
            color = 0xFF000000.toInt()
            if (bold) {
                // 与安卓端一致：加粗在 384 点热敏输出上必须肉眼可辨，故叠加约 5% 字身描边。
                mode = PaintMode.STROKE_AND_FILL
                strokeWidth = sizeSp * BOLD_STROKE_WIDTH_RATIO
            }
        }
        return font to paint
    }

    private fun lineHeight(font: Font, multiplier: Float): Int {
        val metrics = font.metrics
        val base = metrics.descent - metrics.ascent + metrics.leading
        return ceil(base * multiplier).toInt().coerceAtLeast(1)
    }

    private fun baselineInset(font: Font): Int = ceil(-font.metrics.ascent).toInt().coerceAtLeast(1)

    /**
     * 贪心折行：段落（按 \n 切分）内逐字符累加实测宽度，超出宽度时回退到最近的可断点
     * （CJK 字符前、空格/连字符后）；无可用断点则硬断。
     */
    private fun breakLines(text: String, font: Font, maxWidth: Float): List<String> {
        val out = ArrayList<String>()
        var paragraphStart = 0
        while (true) {
            val nl = text.indexOf('\n', paragraphStart)
            val end = if (nl < 0) text.length else nl
            wrapParagraph(text.substring(paragraphStart, end), font, maxWidth, out)
            if (nl < 0) break
            paragraphStart = nl + 1
        }
        return out
    }

    private fun wrapParagraph(paragraph: String, font: Font, maxWidth: Float, out: MutableList<String>) {
        if (paragraph.isEmpty()) {
            out += ""
            return
        }
        var lineStart = 0
        var lastBreak = -1
        var width = 0f
        var i = 0
        while (i < paragraph.length) {
            val ch = paragraph[i]
            if (isCjk(ch) && i > lineStart) lastBreak = i
            val charWidth = font.measureTextWidth(ch.toString())
            if (width + charWidth > maxWidth && i > lineStart) {
                val cut = if (lastBreak > lineStart) lastBreak else i
                out += paragraph.substring(lineStart, cut)
                lineStart = cut
                while (lineStart < paragraph.length && paragraph[lineStart] == ' ') lineStart++
                i = lineStart
                width = 0f
                lastBreak = -1
                continue
            }
            width += charWidth
            i++
            if (ch == ' ' || ch == '-' || ch == '/') lastBreak = i
        }
        out += paragraph.substring(lineStart)
    }

    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x2E80..0x9FFF ||
            code in 0xF900..0xFAFF ||
            code in 0xFE30..0xFE4F ||
            code in 0xFF00..0xFFEF ||
            code in 0x3000..0x303F
    }

    private class Line(
        val text: String,
        val x: Float,
        val height: Int,
        val baselineInset: Int,
        val font: Font,
        val paint: Paint,
    )
}
