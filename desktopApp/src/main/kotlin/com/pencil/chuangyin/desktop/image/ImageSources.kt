package com.pencil.chuangyin.desktop.image

import androidx.compose.ui.graphics.ImageBitmap
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.text.Collator
import java.util.Locale
import java.util.zip.ZipInputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer

/**
 * 可打印图片来源 —— 对应安卓端 `feature/image/data/ImageSource.kt`：
 * 统一抽象相册文件、压缩包解压文件、PDF 页面三种来源。
 *
 * [load] 每次返回一张经过降采样的新位图，由调用方在用完后丢弃；
 * 批量打印上百页时内存中始终只有当前页，实现不得缓存大图。
 */
interface ImageSource {

    /** 用于展示/保存命名的标签。 */
    val name: String

    /** 解码为工作分辨率位图（长边 ≤ [MAX_SOURCE_EDGE]），失败抛异常由上层兜底。 */
    fun load(): BufferedImage

    /** 释放底层资源（PDF 句柄等）；普通文件为空操作。 */
    fun close() {}

    companion object {
        /** 源图解码长边上限：远超 384 点打印宽度，保留细节又不占内存。 */
        const val MAX_SOURCE_EDGE = 2_400

        /** PDF 页面栅格化的目标宽度（约 300dpi@58mm，再交给处理器缩到 384）。 */
        const val PDF_RENDER_WIDTH = 1_200
    }
}

/** 相册 / 压缩包解压后的本地文件来源。 */
class FileImageSource(
    private val file: File,
    override val name: String,
) : ImageSource {

    override fun load(): BufferedImage =
        ImageProcessor.loadScaled(file, ImageSource.MAX_SOURCE_EDGE)
            ?: error("无法解码图片：$name")
}

/** PDF 单页来源：共享同一个 [PdfDocumentBundle]，渲染时加锁（PDFRenderer 非线程安全）。 */
class PdfPageSource(
    private val bundle: PdfDocumentBundle,
    private val pageIndex: Int,
    override val name: String,
) : ImageSource {

    override fun load(): BufferedImage = bundle.renderPage(pageIndex, ImageSource.PDF_RENDER_WIDTH)
}

/**
 * PDF 文档句柄 —— 对应安卓端 `PdfDocumentBundle`：
 *  - Android `PdfRenderer(ParcelFileDescriptor)` → 桌面 `PDDocument.load(File)` + `PDFRenderer`；
 *  - 页面尺寸按 `cropBox` 取点值，旋转 90/270 时宽高互换后再折算 DPI，
 *    使输出宽度与安卓端 [ImageSource.PDF_RENDER_WIDTH] 一致。
 */
class PdfDocumentBundle private constructor(private val document: PDDocument) {

    private val renderer = PDFRenderer(document)

    val pageCount: Int get() = document.numberOfPages

    fun close() {
        runCatching { document.close() }
    }

    /** 按目标宽度栅格化一页（白底、RGB，无透明通道）。 */
    @Synchronized
    fun renderPage(pageIndex: Int, targetWidth: Int): BufferedImage {
        val (widthPoints, _) = pageSizePoints(pageIndex)
        val dpi = (targetWidth * 72f / widthPoints).coerceIn(MIN_RENDER_DPI, MAX_RENDER_DPI)
        val rendered = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB)
        // PDFBox 按 floor(widthPt * scale) 取整，宽度可能差 1~2 像素；补齐到目标宽度，
        // 保证后续 384 点缩放与安卓端输入尺寸一致。
        return if (rendered.width == targetWidth || rendered.width <= 0) {
            rendered
        } else {
            ImageProcessor.scaleToWidth(rendered, targetWidth)
        }
    }

    /** 渲染缩略图（长边 ≤ [edge]），供选择页网格使用；与打印渲染共用同一把锁。 */
    @Synchronized
    fun renderThumbnail(pageIndex: Int, edge: Int = PDF_THUMB_EDGE): BufferedImage? = runCatching {
        val (widthPoints, heightPoints) = pageSizePoints(pageIndex)
        val longEdgePoints = maxOf(widthPoints, heightPoints)
        val dpi = (edge * 72f / longEdgePoints).coerceIn(MIN_RENDER_DPI, MAX_RENDER_DPI)
        val rendered = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB)
        ImageProcessor.capEdge(rendered, edge)
    }.getOrNull()

    /** 页面宽高（点）：已按页面旋转角处理横竖互换。 */
    private fun pageSizePoints(pageIndex: Int): Pair<Float, Float> {
        val page = document.getPage(pageIndex)
        val box = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        val rawWidth = box.width.coerceAtLeast(1f)
        val rawHeight = box.height.coerceAtLeast(1f)
        return if (rotation == 90 || rotation == 270) {
            rawHeight to rawWidth
        } else {
            rawWidth to rawHeight
        }
    }

    companion object {
        /** 与安卓端 dimen 无关的渲染保护边界：过低会糊，过高会 OOM。 */
        private const val MIN_RENDER_DPI = 24f
        private const val MAX_RENDER_DPI = 600f
        private const val PDF_THUMB_EDGE = 320

        fun open(file: File): PdfDocumentBundle? = runCatching {
            val document = PDDocument.load(file)
            try {
                PdfDocumentBundle(document)
            } catch (t: Throwable) {
                runCatching { document.close() }
                throw t
            }
        }.getOrNull()
    }
}

/**
 * 压缩包图片导入 —— 对应安卓端 `ImageArchiveImporter`：
 *  - 先按 UTF-8 读条目名，出现乱码/空结果时用 GBK 重试（Windows 中文压缩包常见）；
 *  - 解压到缓存目录（`image_import_<nanoTime>`），按中文排序规则排序后返回。
 */
object ImageArchiveImporter {

    private const val THUMB_EDGE = 320

    private val IMAGE_SUFFIXES = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    data class ExtractedImage(val path: String, val file: File)

    data class ExtractedImages(val images: List<ExtractedImage>, val tempDir: File)

    /** 导入压缩包；未找到图片或读取失败返回 null（调用方提示「压缩包中未找到图片」）。 */
    fun import(archive: File, cacheDir: File): ExtractedImages? {
        var extracted = runCatching { extractOnce(archive, cacheDir, Charsets.UTF_8) }.getOrNull()
        val needGbk = extracted == null ||
            extracted.images.isEmpty() ||
            extracted.images.any { looksGarbled(it.path) }
        if (needGbk) {
            extracted?.let { cleanup(it.tempDir) }
            extracted = runCatching { extractOnce(archive, cacheDir, Charset.forName("GBK")) }.getOrNull()
        }
        val result = extracted ?: return null
        if (result.images.isEmpty()) {
            cleanup(result.tempDir)
            return null
        }
        val collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE)
        val sorted = result.images.sortedWith(compareBy(collator) { image -> image.path })
        return ExtractedImages(sorted, result.tempDir)
    }

    /** 解码缩略图（长边 ≤ [edge]），供选择页网格使用。 */
    fun decodeThumbnail(file: File, edge: Int = THUMB_EDGE): ImageBitmap? {
        val decoded = ImageProcessor.decodeThumbnail(file, edge) ?: return null
        return ImageProcessor.toImageBitmap(decoded)
    }

    fun cleanup(dir: File) {
        runCatching { dir.deleteRecursively() }
    }

    /** 桌面端缓存目录（安卓为 `context.cacheDir`）。 */
    fun cacheDir(): File = File(System.getProperty("java.io.tmpdir"), "pencil_chuangyin_cache").apply { mkdirs() }

    private fun extractOnce(archive: File, cacheDir: File, charset: Charset): ExtractedImages {
        val tempDir = File(cacheDir, "image_import_${System.nanoTime()}")
        if (!tempDir.mkdirs() && !tempDir.isDirectory) throw IOException("无法创建临时目录")
        try {
            val images = mutableListOf<ExtractedImage>()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            ZipInputStream(archive.inputStream().buffered(), charset).use { zip ->
                var entry = zip.nextEntry
                var index = 0
                while (entry != null) {
                    val path = normalizePath(entry.name)
                    if (!entry.isDirectory && isImageEntry(path)) {
                        val target = File(tempDir, "${index}_${path.substringAfterLast('/')}")
                        target.outputStream().buffered().use { output ->
                            var read = zip.read(buffer)
                            while (read > 0) {
                                output.write(buffer, 0, read)
                                read = zip.read(buffer)
                            }
                        }
                        images += ExtractedImage(path, target)
                        index++
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            return ExtractedImages(images, tempDir)
        } catch (t: Throwable) {
            cleanup(tempDir)
            throw t
        }
    }

    private fun isImageEntry(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase(Locale.ROOT) in IMAGE_SUFFIXES

    private fun normalizePath(raw: String): String =
        raw.replace('\\', '/').removePrefix("./").trimStart('/')

    /** 条目名出现替换字符说明编码猜错（UTF-8 解 GBK 字节的典型表现）。 */
    private fun looksGarbled(path: String): Boolean = path.contains('\uFFFD')
}
