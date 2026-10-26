package com.pencil.chuangyin.printer

/** 文本行对齐方式（core:printer 不自依赖 Compose，故独立定义打印语义）。 */
enum class PrintAlign { Start, Center, End }

/**
 * 可打印文本文档：feature:text 编辑态与 core:printer 渲染态共享的契约模型。
 * 尺寸单位为打印像素域的字号（直接用作 Canvas textSize），由渲染实现结合 384 点宽度排版。
 */
data class TextDoc(
    val title: String = "",
    val content: String = "",
    val fontSizeSp: Float = 14f,
    val titleSizeSp: Float = 20f,
    val lineSpacingMultiplier: Float = 1.4f,
    val align: PrintAlign = PrintAlign.Start,
    val bold: Boolean = false,
) {
    companion object {
        // 384 点宽下：64px 约每行 6 个汉字，已接近热敏小票的实用最大字号。
        val FONT_SIZE_RANGE = 8f..64f
        val TITLE_SIZE_RANGE = 12f..80f
        const val DEFAULT_FONT_SIZE = 14f
        const val DEFAULT_TITLE_SIZE = 20f
        const val MIN_LINE_SPACING = 1.0f
        const val MAX_LINE_SPACING = 2.5f

        /**
         * 把超长文本切成若干段：尽量在换行边界切，避免破坏段落；
         * 单段超过 [maxChars] 且无换行时硬切，保证长度有上界。
         * 各段将在同一打印任务内被连续渲染/输出，物理上无缝衔接。
         */
        fun segment(content: String, maxChars: Int = PrinterCommand.TEXT_SEGMENT_CHARS): List<String> {
            if (content.length <= maxChars) return listOf(content)
            val segments = ArrayList<String>()
            var remaining = content
            while (remaining.length > maxChars) {
                var cut = remaining.lastIndexOf('\n', maxChars)
                if (cut <= 0) cut = maxChars // 段落超长：硬切
                segments.add(remaining.substring(0, cut))
                remaining = remaining.substring(cut).trimStart('\n', '\r')
            }
            if (remaining.isNotEmpty()) segments.add(remaining)
            return segments
        }
    }
}
