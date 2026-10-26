package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.ai.AgentTool
import com.pencil.chuangyin.desktop.ai.ToolOutput
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 联网检索工具集合：让 Agent 能像官方客户端一样查询网络资料。
 *
 * 不依赖任何付费 API Key：`web_search` 抓取公开搜索页（Bing RSS 为主、DuckDuckGo HTML 兜底）
 * 解析出标题/链接/摘要；`web_fetch` 抓取指定 URL 并转为纯文本供模型阅读。
 *
 * 两者都属只读操作（见 [ToolPermissions]），在「每次审批 / 工作区外审批」模式下免二次确认，
 * 保证联网检索随时可用；失败以 [ToolOutput.isError] 回灌给模型，不抛异常中断任务。
 */
class WebTools {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val tools: List<AgentTool> = listOf(
        agentTool(
            name = ToolNames.WEB_SEARCH,
            description = "联网搜索：返回若干条「标题 + 链接 + 摘要」。主用 Bing 的 RSS 输出，失败时回退到 DuckDuckGo。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.QUERY),
                properties = mapOf(
                    ToolParams.QUERY to ToolSchema.string("搜索关键词，可包含自然语言。"),
                    ToolParams.MAX_RESULTS to ToolSchema.integer("返回结果条数上限，默认 8，最大 20。"),
                ),
            ),
        ) { args, _ -> webSearch(args) },

        agentTool(
            name = ToolNames.WEB_FETCH,
            description = "抓取网页并转为纯文本。会剥离脚本/样式/标签，解码 HTML 实体并压缩空白，" +
                "按 max_chars 截断；图片、PDF 等非文本内容无法在此阅读。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.URL),
                properties = mapOf(
                    ToolParams.URL to ToolSchema.string("要抓取的网页地址，需以 http:// 或 https:// 开头。"),
                    ToolParams.MAX_CHARS to ToolSchema.integer("返回正文最大字符数，默认 20000。"),
                ),
            ),
        ) { args, _ -> webFetch(args) },
    )

    private suspend fun webSearch(args: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val term = args.stringOrNull(ToolParams.QUERY) ?: return@withContext failure("搜索关键词不能为空。")
        val limit = args.intOr(ToolParams.MAX_RESULTS, DEFAULT_RESULTS).coerceIn(1, MAX_RESULTS)

        val bing = runCatching { searchBing(term, limit) }.getOrNull()
        if (!bing.isNullOrEmpty()) {
            return@withContext ToolOutput(formatResults("Bing", term, bing))
        }
        val ddg = runCatching { searchDuckDuckGo(term, limit) }.getOrNull()
        if (!ddg.isNullOrEmpty()) {
            return@withContext ToolOutput(formatResults("DuckDuckGo", term, ddg))
        }
        failure(
            "未能获取「$term」的搜索结果：可能是网络不可用、搜索引擎改版或请求被限流。" +
                "可稍后重试或改用 web_fetch 直接抓取已知网址。",
        )
    }

    private suspend fun webFetch(args: JsonObject): ToolOutput = withContext(Dispatchers.IO) {
        val target = args.stringOrNull(ToolParams.URL) ?: return@withContext failure("网址不能为空。")
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return@withContext failure("仅支持 http/https 网址，当前为：$target")
        }
        val limit = args.intOr(ToolParams.MAX_CHARS, DEFAULT_FETCH_CHARS).coerceIn(500, MAX_FETCH_CHARS)

        val response = runCatching { client.newCall(request(target)).execute() }
            .getOrElse { return@withContext fetchFailure(it, target) }

        response.use { resp ->
            if (!resp.isSuccessful) {
                return@withContext failure("抓取失败：HTTP ${resp.code} ${resp.message}")
            }
            val raw = readLimited(resp, MAX_HTML_BYTES)
            val contentType = resp.header("Content-Type").orEmpty()
            if (contentType.contains("text/html", ignoreCase = true) || raw.trimStart().startsWith("<")) {
                val title = extractTitle(raw)
                val text = htmlToText(raw)
                if (text.isBlank()) {
                    return@withContext failure("已抓取 $target ，但未解析出可读文本（可能是纯脚本页面或需要登录）。")
                }
                val header = if (title.isNullOrBlank()) "" else "标题：$title\n来源：$target\n\n"
                ToolOutput(header + truncate(text, limit))
            } else {
                ToolOutput("来源：$target\n\n" + truncate(raw.trim(), limit))
            }
        }
    }

    // ---- 搜索引擎实现 ----

    private fun searchBing(query: String, limit: Int): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.bing.com/search?q=$encoded&format=rss&count=$limit"
        val xml = fetchText(url) ?: return emptyList()
        return parseRssItems(xml).take(limit)
    }

    private fun searchDuckDuckGo(query: String, limit: Int): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://html.duckduckgo.com/html/?q=$encoded"
        val html = fetchText(url) ?: return emptyList()
        return parseDuckDuckGo(html).take(limit)
    }

    private fun fetchText(url: String): String? {
        val response = runCatching { client.newCall(request(url)).execute() }.getOrNull() ?: return null
        response.use { resp ->
            if (!resp.isSuccessful) return null
            return readLimited(resp, MAX_HTML_BYTES)
        }
    }

    /** 解析 Bing RSS 的 `<item>` 列表。 */
    private fun parseRssItems(xml: String): List<SearchHit> {
        val hits = mutableListOf<SearchHit>()
        for (item in RSS_ITEM.findAll(xml)) {
            val block = item.groupValues[1]
            val title = tagText(block, "title")
            val link = tagText(block, "link")
            val summary = tagText(block, "description")
            if (title.isNullOrBlank() || link.isNullOrBlank()) continue
            hits += SearchHit(title, link, summary.orEmpty())
        }
        return hits
    }

    /** 解析 DuckDuckGo 轻量 HTML 页的结果块。 */
    private fun parseDuckDuckGo(html: String): List<SearchHit> {
        val hits = mutableListOf<SearchHit>()
        for (match in DDG_RESULT.findAll(html)) {
            val block = match.groupValues[1]
            val titleMatch = DDG_TITLE.find(block) ?: continue
            val rawHref = titleMatch.groupValues[1]
            val title = htmlToText(titleMatch.groupValues[2])
            val href = decodeEntities(normalizeDuckUrl(rawHref))
            val snippet = DDG_SNIPPET.find(block)?.groupValues?.get(1)?.let { htmlToText(it) }.orEmpty()
            if (title.isBlank() || href.isBlank()) continue
            hits += SearchHit(title, href, snippet)
        }
        return hits
    }

    /** DuckDuckGo 的结果链接是 `//duckduckgo.com/l/?uddg=<encoded>` 形式，还原目标地址。 */
    private fun normalizeDuckUrl(href: String): String {
        val marker = "uddg="
        val index = href.indexOf(marker)
        if (index < 0) return if (href.startsWith("//")) "https:$href" else href
        val tail = href.substring(index + marker.length)
        val value = tail.substringBefore('&')
        return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }

    private fun formatResults(engine: String, query: String, hits: List<SearchHit>): String {
        val builder = StringBuilder()
        builder.appendLine("「$query」的搜索结果（$engine，共 ${hits.size} 条）：")
        hits.forEachIndexed { index, hit ->
            builder.appendLine()
            builder.appendLine("${index + 1}. ${hit.title}")
            builder.appendLine("   ${hit.url}")
            if (hit.summary.isNotBlank()) builder.appendLine("   ${hit.summary}")
        }
        builder.appendLine()
        builder.append("可用 web_fetch 打开其中任一链接查看全文。")
        return builder.toString().trim()
    }

    // ---- HTTP / 文本处理辅助 ----

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .build()

    /** 读取响应体但不超过 [maxBytes]，避免超大页面拖垮内存。 */
    private fun readLimited(response: Response, maxBytes: Long): String {
        val body = response.body ?: return ""
        val charset = body.contentType()?.charset() ?: Charsets.UTF_8
        body.byteStream().use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            var total = 0L
            while (total < maxBytes) {
                val read = input.read(chunk)
                if (read <= 0) break
                val allowed = minOf(read.toLong(), maxBytes - total).toInt()
                buffer.write(chunk, 0, allowed)
                total += allowed
            }
            return buffer.toString(charset.name())
        }
    }

    private fun extractTitle(html: String): String? =
        TITLE_TAG.find(html)?.groupValues?.get(1)?.let { htmlToText(it) }?.takeIf { it.isNotBlank() }

    /** 将 HTML 转为可读纯文本：去除脚本/样式，剥离标签，解码实体，压缩空白。 */
    private fun htmlToText(html: String): String {
        var text = html
        text = SCRIPT_STYLE.replace(text, " ")
        text = BLOCK_BREAK.replace(text, "\n")
        text = removeTags(text)
        text = decodeEntities(text)
        return text.lineSequence()
            .map { it.replace(HORIZONTAL_WS, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /** 逐字符剥离标签，跳过 script/style 之外的所有 `<...>`。 */
    private fun removeTags(html: String): String {
        val builder = StringBuilder(html.length)
        var inTag = false
        for (ch in html) {
            when {
                ch == '<' -> inTag = true
                ch == '>' -> {
                    inTag = false
                    builder.append(' ')
                }
                !inTag -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    private fun decodeEntities(text: String): String {
        if (text.indexOf('&') < 0) return text
        val builder = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch != '&') {
                builder.append(ch)
                index++
                continue
            }
            val semi = text.indexOf(';', index + 1)
            if (semi < 0 || semi - index > 10) {
                builder.append(ch)
                index++
                continue
            }
            val entity = text.substring(index + 1, semi)
            val decoded = decodeEntity(entity)
            if (decoded == null) {
                builder.append(ch)
                index++
            } else {
                builder.append(decoded)
                index = semi + 1
            }
        }
        return builder.toString()
    }

    private fun decodeEntity(entity: String): String? = when {
        entity.startsWith("#x") || entity.startsWith("#X") ->
            entity.drop(2).toIntOrNull(16)?.let { codePointToString(it) }

        entity.startsWith("#") ->
            entity.drop(1).toIntOrNull()?.let { codePointToString(it) }

        else -> NAMED_ENTITIES[entity.lowercase()]
    }

    private fun codePointToString(code: Int): String? =
        if (code in 1..0x10FFFF) runCatching { String(Character.toChars(code)) }.getOrNull() else null

    private fun truncate(text: String, limit: Int): String =
        if (text.length <= limit) text else text.take(limit) + "\n\n…（内容过长已截断，共 ${text.length} 字）"

    private fun tagText(block: String, tag: String): String? {
        val match = Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(block) ?: return null
        val raw = match.groupValues[1].removePrefix(CDATA_START).removeSuffix(CDATA_END)
        return decodeEntities(raw).trim().ifEmpty { null }
    }

    private fun fetchFailure(error: Throwable, target: String): ToolOutput =
        ToolOutput("抓取 $target 失败：${error.message ?: error.javaClass.simpleName}", isError = true)

    private data class SearchHit(val title: String, val url: String, val summary: String)

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L
        const val CALL_TIMEOUT_SECONDS = 45L
        const val DEFAULT_RESULTS = 8
        const val MAX_RESULTS = 20
        const val DEFAULT_FETCH_CHARS = 20_000
        const val MAX_FETCH_CHARS = 60_000
        const val MAX_HTML_BYTES = 3L * 1024 * 1024

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

        val RSS_ITEM = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
        val DDG_RESULT = Regex(
            "<div class=\"result(?:\\s[^\"]*)?\"[^>]*>(.*?)</div>\\s*(?:</div>|<div class=\"result__body)",
            RegexOption.DOT_MATCHES_ALL,
        )
        val DDG_TITLE = Regex("class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val DDG_SNIPPET = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val TITLE_TAG = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val SCRIPT_STYLE = Regex(
            "<(script|style|noscript|template|head)[^>]*>.*?</\\1>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val BLOCK_BREAK = Regex("(?i)<(br|/p|/div|/li|/tr|/h[1-6]|/table|/section|/article)\\s*/?>")
        val HORIZONTAL_WS = Regex("[\\t\\u000B\\f\\r ]+")

        const val CDATA_START = "<![CDATA["
        const val CDATA_END = "]]>"

        val NAMED_ENTITIES = mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " ",
            "ensp" to " ",
            "emsp" to " ",
            "hellip" to "…",
            "mdash" to "—",
            "ndash" to "–",
            "copy" to "©",
            "reg" to "®",
            "trade" to "™",
            "laquo" to "«",
            "raquo" to "»",
            "middot" to "·",
            "bull" to "•",
            "deg" to "°",
            "times" to "×",
            "divide" to "÷",
            "larr" to "←",
            "rarr" to "→",
            "harr" to "↔",
        )
    }
}
