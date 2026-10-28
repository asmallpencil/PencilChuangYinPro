package com.pencil.chuangyin.feature.ai.tools

import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ToolOutput
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 联网检索工具集合：让 Agent 能像官方客户端一样查询网络资料。
 *
 * 不依赖任何付费 API Key，也不依赖百度（百度对非浏览器请求的人机验证极重，命中率极低）。
 * `web_search` 走「多引擎链」：先 cn.bing.com 的 RSS（结构化），再 cn.bing.com 的 HTML 结果页
 * （解析 `b_algo` 列表并还原 bing 跳转链接），随后 Mojeek、DuckDuckGo 轻量页、SearXNG 实例兜底；
 * 命中即返回，并在结果头如实标注实际使用的引擎。`web_fetch` 抓取指定 URL 并转为纯文本，
 * 对常见的「人机验证 / 安全验证」拦截页给出明确提示而不是把验证码文本当成正文。
 *
 * 两者都属只读操作（见 [ToolPermissions]），在「每次审批 / 工作区外审批」模式下免二次确认，
 * 保证联网检索随时可用；失败以 [ToolOutput.isError] 回灌给模型，不抛异常中断任务。
 */
@Singleton
class WebTools @Inject constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** 搜索用的短超时客户端：某个引擎被墙/限流时尽快切下一个，不拖死整条链。 */
    private val searchClient: OkHttpClient = client.newBuilder()
        .connectTimeout(SEARCH_CONNECT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(SEARCH_READ_SECONDS, TimeUnit.SECONDS)
        .callTimeout(SEARCH_CALL_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * 联网搜索：返回若干条「标题 + 链接 + 摘要」。
     *
     * @param max_results 条数上限（1..20）
     */
    @Tool
    suspend fun web_search(
        @Param(description = "搜索关键词，可包含自然语言。") query: String,
        @Param(description = "返回结果条数上限，默认 8，最大 20。") max_results: Int?,
    ): ToolOutput {
        val term = query.trim()
        if (term.isEmpty()) return ToolOutput("搜索关键词不能为空。", isError = true)
        val limit = (max_results ?: DEFAULT_RESULTS).coerceIn(1, MAX_RESULTS)

        return withContext(Dispatchers.IO) {
            val attempts = mutableListOf<String>()
            val engines = listOf(
                "Bing" to { searchBingRss(term, limit) },
                "Bing(网页)" to { searchBingHtml(term, limit) },
                "Mojeek" to { searchMojeek(term, limit) },
                "DuckDuckGo" to { searchDuckDuckGo(term, limit) },
                "SearXNG" to { searchSearx(term, limit) },
            )
            for ((name, search) in engines) {
                val hits = runCatching { search() }.getOrNull()
                if (!hits.isNullOrEmpty()) {
                    val note = if (attempts.isEmpty()) "" else "（前序引擎无结果：${attempts.joinToString("、")}）"
                    return@withContext ToolOutput(formatResults(name, term, hits) + note)
                }
                attempts += name
            }
            ToolOutput(
                "未能获取「$term」的搜索结果：已依次尝试 ${attempts.joinToString("、")}，" +
                    "均无返回。常见原因是网络不可达或被限流。可稍后重试，或把已知网址交给 web_fetch 直接抓取。",
                isError = true,
            )
        }
    }

    /**
     * 抓取网页并转为纯文本。
     *
     * 会剥离脚本/样式/标签，解码 HTML 实体并压缩空白，按 max_chars 截断；图片、PDF 等非文本
     * 内容无法在此阅读，如为图片请改用其他工具。
     */
    @Tool
    suspend fun web_fetch(
        @Param(description = "要抓取的网页地址，需以 http:// 或 https:// 开头。") url: String,
        @Param(description = "返回正文最大字符数，默认 20000。") max_chars: Int?,
    ): ToolOutput {
        val target = url.trim()
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return ToolOutput("仅支持 http/https 网址，当前为：$target", isError = true)
        }
        val limit = (max_chars ?: DEFAULT_FETCH_CHARS).coerceIn(500, MAX_FETCH_CHARS)

        return withContext(Dispatchers.IO) {
            val response = runCatching {
                client.newCall(request(target)).execute()
            }.getOrElse { return@withContext failure(it, target) }

            response.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext ToolOutput("抓取失败：HTTP ${resp.code} ${resp.message}", isError = true)
                }
                val raw = readLimited(resp, MAX_HTML_BYTES)
                val contentType = resp.header("Content-Type").orEmpty()
                if (contentType.contains("text/html", ignoreCase = true) || raw.trimStart().startsWith("<")) {
                    // 很多国内站点会对非浏览器请求返回「安全验证 / 人机验证」页，
                    // 直接当成正文喂给模型会得到一堆无意义内容，这里显式识别并回报。
                    val blocked = ANTI_BOT_MARKERS.firstOrNull { raw.contains(it) }
                    if (blocked != null) {
                        return@withContext ToolOutput(
                            "抓取被拦截：$target 返回了人机/安全验证页（命中「$blocked」）。" +
                                "该站点限制非浏览器访问，建议改用其它来源，或先 web_search 找同主题的其它站点。",
                            isError = true,
                        )
                    }
                    val title = extractTitle(raw)
                    val text = htmlToText(raw)
                    if (text.isBlank()) {
                        return@withContext ToolOutput("已抓取 $target ，但未解析出可读文本（可能是纯脚本页面或需要登录）。", isError = true)
                    }
                    val header = if (title.isNullOrBlank()) "" else "标题：$title\n来源：$target\n\n"
                    ToolOutput(header + truncate(text, limit))
                } else {
                    ToolOutput("来源：$target\n\n" + truncate(raw.trim(), limit))
                }
            }
        }
    }

    // ---- 搜索引擎实现（多引擎链，命中即返回） ----

    /** Bing RSS：结构化、最省解析成本；国内走 cn.bing.com。 */
    private fun searchBingRss(query: String, limit: Int): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val urls = listOf(
            "https://cn.bing.com/search?q=$encoded&format=rss&count=$limit",
            "https://www.bing.com/search?q=$encoded&format=rss&count=$limit",
        )
        for (url in urls) {
            val xml = fetchText(url) ?: continue
            val hits = parseRssItems(xml).take(limit)
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }

    /** Bing HTML 结果页：解析 `b_algo` 条目，并还原 `bing.com/ck/a?...&u=` 跳转链接。 */
    private fun searchBingHtml(query: String, limit: Int): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val html = fetchText("https://cn.bing.com/search?q=$encoded&count=$limit") ?: return emptyList()
        val hits = mutableListOf<SearchHit>()
        for (item in BING_ITEM.findAll(html)) {
            val block = item.value
            val link = BING_LINK.find(block) ?: continue
            val href = decodeBingUrl(decodeEntities(link.groupValues[1]))
            val title = htmlToText(link.groupValues[2])
            if (href.isBlank() || title.isBlank()) continue
            val snippet = BING_SNIPPET.find(block)?.groupValues?.get(1)
                ?.let { htmlToText(it) }
                .orEmpty()
            hits += SearchHit(title, href, snippet)
            if (hits.size >= limit) break
        }
        return hits
    }

    /** Mojeek：独立索引、对脚本请求友好，作为 bing 之外的第二个可用源。 */
    private fun searchMojeek(query: String, limit: Int): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val html = fetchText("https://www.mojeek.com/search?q=$encoded") ?: return emptyList()
        val hits = mutableListOf<SearchHit>()
        for (match in MOJEEK_LINK.findAll(html)) {
            val href = decodeEntities(match.groupValues[1])
            val title = htmlToText(match.groupValues[2])
            if (href.isBlank() || title.isBlank()) continue
            hits += SearchHit(title, href, "")
            if (hits.size >= limit) break
        }
        return hits
    }

    private fun searchDuckDuckGo(query: String, limit: Int): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        for (url in listOf("https://html.duckduckgo.com/html/?q=$encoded", "https://lite.duckduckgo.com/lite/?q=$encoded")) {
            val html = fetchText(url) ?: continue
            val hits = parseDuckDuckGo(html).take(limit)
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }

    /** SearXNG 公共实例：HTML 结果页，实例之间互为备份。 */
    private fun searchSearx(query: String, limit: Int): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        for (host in SEARX_HOSTS) {
            val html = fetchText("$host/search?q=$encoded") ?: continue
            val hits = mutableListOf<SearchHit>()
            for (item in SEARX_ARTICLE.findAll(html)) {
                val block = item.value
                val link = SEARX_LINK.find(block) ?: continue
                val href = decodeEntities(link.groupValues[1])
                val title = htmlToText(link.groupValues[2])
                if (href.isBlank() || title.isBlank() || !href.startsWith("http")) continue
                val snippet = SEARX_CONTENT.find(block)?.groupValues?.get(1)
                    ?.let { htmlToText(it) }
                    .orEmpty()
                hits += SearchHit(title, href, snippet)
                if (hits.size >= limit) break
            }
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }

    /**
     * 还原 bing 的跳转链接：
     * `https://www.bing.com/ck/a?!&&p=...&u=a1aHR0cHM6...` 里的 `u` 参数是 `a1` + base64url(真实地址)。
     */
    private fun decodeBingUrl(href: String): String {
        if (!href.contains("/ck/a")) return href
        val marker = Regex("[?&]u=a1([^&]+)")
        val encoded = marker.find(href)?.groupValues?.get(1) ?: return href
        val decoded = runCatching {
            val bytes = android.util.Base64.decode(encoded, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
            String(bytes, Charsets.UTF_8)
        }.getOrNull()
        return if (decoded.isNullOrBlank()) href else decoded
    }

    private fun fetchText(url: String, search: Boolean = true): String? {
        val call = if (search) searchClient else client
        val response = runCatching { call.newCall(request(url)).execute() }.getOrNull() ?: return null
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
        return runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
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
        // 压缩行内空白并丢弃空行
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

    private fun failure(error: Throwable, target: String): ToolOutput =
        ToolOutput("抓取 $target 失败：${error.message ?: error.javaClass.simpleName}", isError = true)

    private data class SearchHit(val title: String, val url: String, val summary: String)

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L
        const val CALL_TIMEOUT_SECONDS = 45L

        /** 搜索链中单个引擎的超时：慢引擎要被快速跳过。 */
        const val SEARCH_CONNECT_SECONDS = 8L
        const val SEARCH_READ_SECONDS = 12L
        const val SEARCH_CALL_SECONDS = 15L
        const val DEFAULT_RESULTS = 8
        const val MAX_RESULTS = 20
        const val DEFAULT_FETCH_CHARS = 20_000
        const val MAX_FETCH_CHARS = 60_000
        const val MAX_HTML_BYTES = 3L * 1024 * 1024

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

        /** 公共 SearXNG 实例（HTML 结果页，互为备份）。 */
        val SEARX_HOSTS = listOf(
            "https://searx.be",
            "https://search.inetol.net",
        )

        /** 常见「人机 / 安全验证」页特征串，命中即判定为被拦截。 */
        val ANTI_BOT_MARKERS = listOf(
            "百度安全验证",
            "请输入验证码",
            "人机验证",
            "安全验证",
            "访问验证",
            "cf-browser-verification",
            "Just a moment...",
            "Attention Required",
            "unusual traffic",
        )

        val RSS_ITEM = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
        val BING_ITEM = Regex("<li class=\"b_algo\".*?</li>", RegexOption.DOT_MATCHES_ALL)
        val BING_LINK = Regex(
            "<h2[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val BING_SNIPPET = Regex("<p[^>]*>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val MOJEEK_LINK = Regex(
            "<a[^>]+href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val SEARX_ARTICLE = Regex("<article class=\"result[^\"]*\">(.*?)</article>", RegexOption.DOT_MATCHES_ALL)
        val SEARX_LINK = Regex("<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val SEARX_CONTENT = Regex("<p class=\"content\">(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
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
