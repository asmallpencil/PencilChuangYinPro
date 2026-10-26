package com.pencil.chuangyin.desktop.ai.tools

import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer

/**
 * 文件类型判定与内容抽取（桌面版，对齐安卓端 `DocumentTools`）。
 *
 * 与安卓端的差异仅在底层库：
 * - PDF 渲染：`PdfRenderer` → PDFBox 的 `PDFRenderer`；
 * - Office 文本抽取：`XmlPullParser` → 正则抽取（docx/pptx 的 `w:t`/`a:t`、xlsx 的 sharedStrings + 单元格）。
 */

internal enum class FileKind { Image, Pdf, Ooxml, Text, Binary }

internal object FileKinds {

    val IMAGE_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "webp", "bmp", "gif", "tif", "tiff",
    )

    val OOXML_EXTENSIONS = setOf("docx", "xlsx", "pptx")

    val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "json", "csv", "tsv", "log", "xml", "html", "htm", "yml", "yaml",
        "kt", "kts", "java", "py", "js", "ts", "c", "h", "cpp", "hpp", "cs", "go", "rs", "rb", "php",
        "css", "scss", "sh", "bat", "ps1", "gradle", "properties", "ini", "toml", "conf", "svg",
    )

    fun kindOf(file: File): FileKind {
        val ext = file.extension.lowercase()
        return when {
            ext in IMAGE_EXTENSIONS -> FileKind.Image
            ext in OOXML_EXTENSIONS -> FileKind.Ooxml
            ext in TEXT_EXTENSIONS -> FileKind.Text
            else -> when (sniff(file)) {
                Magic.Pdf -> FileKind.Pdf
                Magic.Zip -> if (ext == "pdf") FileKind.Pdf else FileKind.Ooxml
                else -> if (looksLikeText(file)) FileKind.Text else FileKind.Binary
            }
        }
    }

    fun isImageExtension(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    private enum class Magic { Pdf, Zip, Other }

    /** 读文件头魔数判定容器类型：`%PDF` → PDF，`PK` → zip（Office / 普通压缩包）。 */
    private fun sniff(file: File): Magic {
        val head = readHead(file, 4)
        return when {
            head.size >= 4 && head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() &&
                head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte() -> Magic.Pdf
            head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> Magic.Zip
            else -> Magic.Other
        }
    }

    private fun readHead(file: File, count: Int): ByteArray = runCatching {
        file.inputStream().use { input -> input.readNBytes(count) }
    }.getOrDefault(ByteArray(0))

    /** 无扩展名时按文件头抽样判断是否像文本（无 NUL 字节、可解码）。 */
    private fun looksLikeText(file: File): Boolean = runCatching {
        if (file.length() > TEXT_SNIFF_BYTES * 4) return@runCatching false
        val bytes = file.inputStream().use { input -> input.readNBytes(TEXT_SNIFF_BYTES) }
        if (bytes.isEmpty()) return@runCatching false
        if (bytes.any { it == 0.toByte() }) return@runCatching false
        String(bytes, Charsets.UTF_8).none { it.code in 0x00..0x08 || it.code in 0x0E..0x1F }
    }.getOrDefault(false)

    private const val TEXT_SNIFF_BYTES = 4096
}

internal object DocumentReader {

    /** 单页渲染的最长边（与安卓端 MAX_RENDER_EDGE 一致）。 */
    private const val MAX_RENDER_EDGE = 1600

    /** 抽取文本的字符上限，防止超大表格/文档撑爆上下文。 */
    private const val MAX_EXTRACT_CHARS = 2_000_000

    /**
     * 把 PDF 前 [maxPages] 页渲染为 PNG，返回落盘文件（按页码顺序）。
     */
    fun renderPdf(file: File, outputDir: File, maxPages: Int): List<File> {
        if (!outputDir.exists()) outputDir.mkdirs()
        val pages = ArrayList<File>()
        PDDocument.load(file).use { document ->
            val renderer = PDFRenderer(document)
            val count = minOf(document.numberOfPages, maxPages)
            for (index in 0 until count) {
                val image = renderer.renderImageWithDPI(index, RENDER_DPI, ImageType.RGB)
                val longest = maxOf(image.width, image.height)
                val target = if (longest > MAX_RENDER_EDGE) {
                    val scale = MAX_RENDER_EDGE.toDouble() / longest
                    val width = (image.width * scale).toInt().coerceAtLeast(1)
                    val height = (image.height * scale).toInt().coerceAtLeast(1)
                    val scaled = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
                    scaled.createGraphics().apply {
                        setRenderingHint(
                            java.awt.RenderingHints.KEY_INTERPOLATION,
                            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                        )
                        drawImage(image, 0, 0, width, height, null)
                        dispose()
                    }
                    scaled
                } else {
                    image
                }
                val out = File(outputDir, "page_${index + 1}.png")
                runCatching { ImageIO.write(target, "png", out) }
                if (out.isFile) pages += out
            }
        }
        return pages
    }

    /**
     * 从 docx / xlsx / pptx 的 zip 容器中抽取纯文本；抽取失败返回空串。
     */
    fun ooxmlText(file: File): String = runCatching {
        ZipFile(file).use { zip ->
            when (file.extension.lowercase()) {
                "docx" -> docxText(zip)
                "pptx" -> pptxText(zip)
                "xlsx" -> xlsxText(zip)
                else -> ""
            }
        }
    }.getOrDefault("").take(MAX_EXTRACT_CHARS)

    private fun docxText(zip: ZipFile): String {
        val entry = zip.getEntry("word/document.xml") ?: return ""
        val xml = zip.getInputStream(entry).use { String(it.readBytes(), Charsets.UTF_8) }
        val builder = StringBuilder()
        // 段落内 w:t 顺序拼接；</w:p> 视为换行。
        val token = Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>|</w:p>|<w:tab\\s*/>|<w:br\\s*/>", RegexOption.DOT_MATCHES_ALL)
        token.findAll(xml).forEach { match ->
            val raw = match.groupValues[1]
            when {
                raw.isNotEmpty() -> builder.append(decodeXmlEntities(raw))
                match.value.startsWith("</w:p") -> builder.append('\n')
                match.value.startsWith("<w:tab") -> builder.append('\t')
                else -> builder.append('\n')
            }
        }
        return builder.toString().trim()
    }

    private fun pptxText(zip: ZipFile): String {
        val slides = zip.entries().asSequence()
            .filter { it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy { it.name.filter(Char::isDigit).toIntOrNull() ?: 0 }
            .toList()
        if (slides.isEmpty()) return ""
        val builder = StringBuilder()
        slides.forEachIndexed { index, entry ->
            val xml = zip.getInputStream(entry).use { String(it.readBytes(), Charsets.UTF_8) }
            builder.appendLine("## 第 ${index + 1} 页")
            Regex("<a:t>(.*?)</a:t>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { match ->
                builder.appendLine(decodeXmlEntities(match.groupValues[1]))
            }
            builder.appendLine()
        }
        return builder.toString().trim()
    }

    private fun xlsxText(zip: ZipFile): String {
        val shared = readSharedStrings(zip)
        val sheets = zip.entries().asSequence()
            .filter { it.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .sortedBy { it.name.filter(Char::isDigit).toIntOrNull() ?: 0 }
            .toList()
        if (sheets.isEmpty()) return ""
        val builder = StringBuilder()
        sheets.forEachIndexed { index, entry ->
            val xml = zip.getInputStream(entry).use { String(it.readBytes(), Charsets.UTF_8) }
            builder.appendLine("## 工作表 ${index + 1}")
            Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { row ->
                val cells = Regex("<c([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(row.groupValues[1])
                    .map { cell ->
                        val attrs = cell.groupValues[1]
                        val body = cell.groupValues[2]
                        val value = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
                            .find(body)?.groupValues?.get(1).orEmpty()
                        val inline = Regex("<is>.*?<t[^>]*>(.*?)</t>.*?</is>", RegexOption.DOT_MATCHES_ALL)
                            .find(body)?.groupValues?.get(1)
                        when {
                            inline != null -> decodeXmlEntities(inline)
                            attrs.contains("t=\"s\"") -> shared.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
                            else -> decodeXmlEntities(value)
                        }
                    }
                    .filter { it.isNotEmpty() }
                    .joinToString(" | ")
                if (cells.isNotEmpty()) builder.appendLine(cells)
            }
            builder.appendLine()
        }
        return builder.toString().trim()
    }

    private fun readSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val xml = zip.getInputStream(entry).use { String(it.readBytes(), Charsets.UTF_8) }
        return Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
            Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(si.groupValues[1])
                .joinToString("") { decodeXmlEntities(it.groupValues[1]) }
        }.toList()
    }

    private fun decodeXmlEntities(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    /** PDFBox 渲染 DPI：150 兼顾清晰度与体积。 */
    private const val RENDER_DPI = 150f
}
