package com.pencil.chuangyin.feature.pixiv.core.network

import com.pencil.chuangyin.feature.pixiv.data.dto.NovelWebPayloadDto

/**
 * /webview/v2/novel 返回 HTML 页面，正文以内嵌 JSON 形式给出：
 * `novel: { ... }, isOwnWork: ...`。
 *
 * 参考 Pix-EzViewer NovelViewModel.parseWebNovel：
 * 懒惰匹配 `{.*?}` + isOwnWork 锚点，回溯定位到正确的闭合括号；DOTALL 让 `.` 跨行。
 */
object NovelWebTextParser {

    private val NOVEL_JSON = Regex("""novel:\s*(\{.*?\}),\s*isOwnWork""", RegexOption.DOT_MATCHES_ALL)

    /** 抽取失败（页面结构变化/未登录/被重定向）返回 null，由上层决定是否走旧端点兜底。 */
    fun parse(html: String): NovelWebPayloadDto? {
        val json = NOVEL_JSON.find(html)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { PixivJson.decodeFromString(NovelWebPayloadDto.serializer(), json) }
            .getOrNull()
            ?.takeIf { it.text.isNotBlank() }
    }
}
