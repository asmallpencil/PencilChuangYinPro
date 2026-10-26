package com.pencil.chuangyin.desktop.printer

import com.pencil.chuangyin.desktop.image.ImageProcessor
import com.pencil.chuangyin.desktop.text.PrintError
import com.pencil.chuangyin.desktop.text.PrintOptions
import com.pencil.chuangyin.desktop.text.PrintPhase
import com.pencil.chuangyin.desktop.text.PrintProgress
import com.pencil.chuangyin.desktop.text.PrintResult
import com.pencil.chuangyin.desktop.text.PrinterCommand
import com.pencil.chuangyin.desktop.text.TextDoc
import com.pencil.chuangyin.desktop.text.TextPreviewRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文本打印管线 —— 对齐安卓端「按 [PrinterCommand.TEXT_SEGMENT_CHARS] 分段渲染 + 逐页打印」：
 *
 * ```
 * 分段(不破代理对) → 每段排版成多页 384 点宽位图 → PNG 编码 → 解码为 BufferedImage
 *               → RasterPage(条带字节) → PrinterController.printPages()
 * ```
 *
 * 之所以「渲染一页就立即编码成条带」，是为了避免长文一次性把所有 ARGB 位图驻留内存
 * （384x768 ARGB ≈ 1.2MB/页，编码后约 36KB/页）。
 */
object TextPrintRunner {

    suspend fun run(
        doc: TextDoc,
        content: String,
        customFontPath: String?,
        options: PrintOptions,
        onProgress: (PrintProgress) -> Unit = {},
    ): PrintResult {
        if (content.isBlank() && doc.title.isBlank()) {
            return PrintResult.Error(PrintError.RenderFailed, "没有可打印的内容")
        }
        if (!PrinterConnectionStore.isConnected) return PrintResult.Error(PrintError.NotConnected)

        val segments = splitSegments(content, PrinterCommand.TEXT_SEGMENT_CHARS)
        if (segments.isEmpty()) {
            return PrintResult.Error(PrintError.RenderFailed, "没有可打印的内容")
        }

        val pages = ArrayList<RasterPage>()
        val rendered = withContext(Dispatchers.Default) {
            runCatching {
                segments.forEachIndexed { index, segment ->
                    onProgress(
                        PrintProgress(
                            phase = PrintPhase.Preparing,
                            segmentIndex = index,
                            segmentCount = segments.size,
                            pageDone = 0,
                            pageCountInSegment = 1,
                        ),
                    )
                    val images = TextPreviewRenderer.renderPrintPages(
                        doc = doc.copy(
                            title = if (index == 0) doc.title else "",
                            content = segment,
                        ),
                        customFontPath = customFontPath,
                    )
                    images.forEach { image ->
                        val png = TextPreviewRenderer.encodePng(image)
                            ?: error("页面编码失败")
                        val bitmap = ImageProcessor.decode(png)
                            ?: error("页面解码失败")
                        pages += RasterPage.from(bitmap, options.threshold)
                    }
                }
            }
        }
        if (rendered.isFailure) {
            return PrintResult.Error(
                PrintError.RenderFailed,
                rendered.exceptionOrNull()?.message ?: "渲染失败",
            )
        }
        if (pages.isEmpty()) return PrintResult.Error(PrintError.RenderFailed, "没有可打印的内容")

        return PrinterController.printPages(
            pages = pages,
            options = options,
            onProgress = { progress ->
                onProgress(
                    progress.copy(
                        segmentIndex = 0,
                        segmentCount = 1,
                        pageCountInSegment = pages.size,
                    ),
                )
            },
        )
    }

    /** 按字符数切分，且不把代理对（emoji 等）拆开。 */
    private fun splitSegments(content: String, size: Int): List<String> {
        if (content.isEmpty()) return emptyList()
        val segmentSize = size.coerceAtLeast(1)
        val out = ArrayList<String>()
        var start = 0
        while (start < content.length) {
            var end = (start + segmentSize).coerceAtMost(content.length)
            if (end < content.length && Character.isHighSurrogate(content[end - 1])) {
                end--
            }
            if (end <= start) end = (start + 1).coerceAtMost(content.length)
            out += content.substring(start, end)
            start = end
        }
        return out
    }
}
