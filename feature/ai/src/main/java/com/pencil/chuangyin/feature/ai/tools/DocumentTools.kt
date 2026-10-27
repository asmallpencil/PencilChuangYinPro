package com.pencil.chuangyin.feature.ai.tools

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Xml
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

/** 文件类别判定结果，决定 read_file 采用哪种读取策略。 */
internal enum class FileKind { Image, Pdf, Ooxml, Text, Video, Binary }

/**
 * 依据扩展名（必要时嗅探文件头）判定文件类别。
 *
 * 单独抽出以便 read_file 与 file_info 等工具共享同一套判定，避免各处散落扩展名列表。
 */
internal object FileKinds {

    private val IMAGE_EXTENSIONS =
        setOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif")

    private val OOXML_EXTENSIONS = setOf("docx", "xlsx", "pptx")

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "json", "csv", "tsv", "log", "xml", "html", "htm",
        "yml", "yaml", "ini", "toml", "properties", "gradle", "kts", "kt", "java", "js",
        "ts", "jsx", "tsx", "py", "c", "cpp", "h", "hpp", "cs", "go", "rs", "rb", "php",
        "sh", "bat", "cmd", "ps1", "sql", "srt", "vtt",
    )

    fun kindOf(file: File): FileKind {
        val ext = file.extension.lowercase(Locale.US)
        return when (ext) {
            in IMAGE_EXTENSIONS -> FileKind.Image
            "pdf" -> FileKind.Pdf
            in OOXML_EXTENSIONS -> FileKind.Ooxml
            in TEXT_EXTENSIONS -> FileKind.Text
            in VideoReader.VIDEO_EXTENSIONS -> FileKind.Video
            else -> sniff(file)
        }
    }

    fun isImageExtension(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.US) in IMAGE_EXTENSIONS

    /** 扩展名未知时的兜底：按文件头魔数与可打印字符比例推断。 */
    private fun sniff(file: File): FileKind {
        val head = readHead(file, 8)
        if (head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
            // 以 PK 开头基本是 zip 容器，Office 文档内部即为 XML，交由 OOXML 分支尝试提取。
            return FileKind.Ooxml
        }
        if (head.size >= 4 && head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() &&
            head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte()
        ) {
            return FileKind.Pdf
        }
        return if (looksLikeText(file)) FileKind.Text else FileKind.Binary
    }

    /** 截断读取文件头若干字节，用于魔数判断（minSdk 23 无 InputStream.readNBytes）。 */
    private fun readHead(file: File, max: Int): ByteArray {
        val buffer = ByteArray(max)
        var total = 0
        runCatching {
            file.inputStream().use { input ->
                while (total < max) {
                    val read = input.read(buffer, total, max - total)
                    if (read < 0) break
                    total += read
                }
            }
        }
        return if (total == max) buffer else buffer.copyOf(total)
    }

    /** 是否可当作 UTF-8 文本：无 NUL 字节且控制字符占比极低。 */
    private fun looksLikeText(file: File): Boolean {
        if (file.length() > TEXT_SNIFF_BYTES) return false
        val bytes = readHead(file, TEXT_SNIFF_BYTES)
        if (bytes.isEmpty()) return true
        var control = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v == 0) return false
            if (v < 0x09 || (v in 0x0E..0x1F)) control++
        }
        return control * 20 < bytes.size
    }

    private const val TEXT_SNIFF_BYTES = 4096
}

/**
 * 文档文本提取：把 OOXML（docx/xlsx/pptx）与 PDF 转换为模型可读的内容。
 *
 * Office 文档本质是 zip 容器，内部为 XML；这里用 [ZipFile] + [XmlPullParser] 直接
 * 抽取文本，零第三方依赖。PDF 无法抽取纯文本，改为逐页渲染为图片，交由多模态理解。
 */
internal object DocumentReader {

    /** 从 docx/xlsx/pptx 抽取纯文本；无法识别时返回 null。 */
    fun ooxmlText(file: File): String? {
        ZipFile(file).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            val builder = StringBuilder()
            when {
                names.contains(DOCX_DOCUMENT) -> {
                    zip.getInputStream(zip.getEntry(DOCX_DOCUMENT)).use { appendXmlText(it, builder) }
                }

                names.any { it.startsWith("xl/worksheets/") } -> appendXlsx(zip, builder)

                names.any { it.startsWith("ppt/slides/") } -> {
                    names.filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
                        .sortedWith(SLIDE_INDEX)
                        .forEachIndexed { index, name ->
                            builder.appendLine("【第 ${index + 1} 页】")
                            zip.getInputStream(zip.getEntry(name)).use { appendXmlText(it, builder) }
                        }
                }

                else -> return null
            }
            return builder.toString().trim().take(MAX_EXTRACT_CHARS).ifBlank { null }
        }
    }

    /**
     * 把 PDF 逐页渲染为 PNG 输出到 [outputDir]，返回已生成的文件（按页序）。
     *
     * 渲染倍率随页面尺寸自适应，保证最长边不超过 [MAX_RENDER_EDGE] 像素，兼顾清晰度与体积。
     */
    fun renderPdf(file: File, outputDir: File, maxPages: Int): List<File> {
        val pages = ArrayList<File>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val count = minOf(renderer.pageCount, maxPages)
                outputDir.mkdirs()
                for (index in 0 until count) {
                    renderer.openPage(index).use { page ->
                        val scale = (MAX_RENDER_EDGE.toFloat() / maxOf(page.width, page.height, 1))
                            .coerceIn(1f, 3f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val out = File(outputDir, "page_${index + 1}.png")
                        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                        pages += out
                    }
                }
            }
        }
        return pages
    }

    private fun appendXmlText(input: InputStream, builder: StringBuilder) {
        val parser = newParser(input)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (!text.isNullOrEmpty()) builder.append(text)
                }

                XmlPullParser.END_TAG -> when (parser.name.substringAfterLast(':')) {
                    "p" -> builder.append('\n')
                    "tab" -> builder.append('\t')
                    "br" -> builder.append('\n')
                }
            }
            event = parser.next()
        }
    }

    private fun appendXlsx(zip: ZipFile, builder: StringBuilder) {
        val shared = readSharedStrings(zip)
        zip.entries().asSequence()
            .filter { it.name.startsWith("xl/worksheets/") && it.name.endsWith(".xml") }
            .map { it.name }
            .sorted()
            .forEach { name ->
                builder.appendLine("【工作表 ${name.substringAfterLast('/')}】")
                zip.getInputStream(zip.getEntry(name)).use { appendSheet(it, shared, builder) }
            }
    }

    private fun readSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry(SHARED_STRINGS) ?: return emptyList()
        val result = ArrayList<String>()
        zip.getInputStream(entry).use { input ->
            val parser = newParser(input)
            var event = parser.eventType
            var current: StringBuilder? = null
            var inText = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.substringAfterLast(':')) {
                        "si" -> current = StringBuilder()
                        "t" -> inText = true
                    }

                    XmlPullParser.TEXT -> if (inText) current?.append(parser.text ?: "")

                    XmlPullParser.END_TAG -> when (parser.name.substringAfterLast(':')) {
                        "t" -> inText = false
                        "si" -> {
                            result += current?.toString().orEmpty()
                            current = null
                        }
                    }
                }
                event = parser.next()
            }
        }
        return result
    }

    private fun appendSheet(input: InputStream, shared: List<String>, builder: StringBuilder) {
        val parser = newParser(input)
        var event = parser.eventType
        var cellType: String? = null
        var cellValue: String? = null
        var inValue = false
        val row = ArrayList<String>()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfterLast(':')) {
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t")
                        cellValue = null
                    }

                    "v", "t" -> inValue = true
                }

                XmlPullParser.TEXT -> if (inValue) cellValue = (cellValue ?: "") + (parser.text ?: "")

                XmlPullParser.END_TAG -> when (parser.name.substringAfterLast(':')) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        row += resolveCell(cellType, cellValue, shared)
                        cellValue = null
                    }

                    "row" -> {
                        if (row.any { it.isNotEmpty() }) builder.appendLine(row.joinToString("\t"))
                        row.clear()
                    }
                }
            }
            event = parser.next()
        }
    }

    /** 单元格取值：t="s" 表示共享字符串下标，其余为字面量。 */
    private fun resolveCell(type: String?, value: String?, shared: List<String>): String {
        val raw = value ?: return ""
        if (type != "s") return raw
        val index = raw.trim().toIntOrNull() ?: return raw
        return shared.getOrNull(index) ?: raw
    }

    private fun newParser(input: InputStream): XmlPullParser {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        return parser
    }

    private const val DOCX_DOCUMENT = "word/document.xml"
    private const val SHARED_STRINGS = "xl/sharedStrings.xml"
    private const val MAX_EXTRACT_CHARS = 2_000_000
    private const val MAX_RENDER_EDGE = 1600

    /** slide1.xml < slide2.xml < ... < slide10.xml 的自然序比较键。 */
    private val SLIDE_INDEX = Comparator<String> { a, b ->
        val na = a.substringAfterLast("slide").substringBefore('.').toIntOrNull() ?: 0
        val nb = b.substringAfterLast("slide").substringBefore('.').toIntOrNull() ?: 0
        na.compareTo(nb)
    }
}
