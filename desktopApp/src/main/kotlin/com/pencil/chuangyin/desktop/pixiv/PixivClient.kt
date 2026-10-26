package com.pencil.chuangyin.desktop.pixiv

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class PixivException(val code: Int, message: String) : Exception("HTTP $code: $message")

/**
 * P 站客户端（桌面端）——协议层与安卓端完全一致：
 * 同一套 ClientId / Secret / PKCE / App 头 / 分页 URL 透传 / v3 评论区。
 *
 * 实现选择：直接使用 OkHttp + kotlinx.serialization（不引入 Retrofit 接口层），
 * 减少桌面端的反射与代理开销，行为与安卓端 Retrofit 服务等价。
 */
class PixivClient(private val session: PixivSession) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("User-Agent", PixivConfig.USER_AGENT)
                .header("App-OS", "android")
                .header("App-OS-Version", "13")
                .header("App-Version", PixivConfig.APP_VER)
                .header("Accept-Language", "zh-CN")
            val clientTime = isoTime()
            builder.header("X-Client-Time", clientTime)
            builder.header("X-Client-Hash", md5(clientTime + PixivConfig.HASH_SALT))
            chain.proceed(builder.build())
        }
        .build()

    // ── OAuth ────────────────────────────────────────────────────────────────

    fun loginUrl(verifier: String): String = PixivConfig.loginUrl(Pkce.challenge(verifier))

    /** 用浏览器回调里的 code 换 token。 */
    suspend fun exchangeCode(code: String, verifier: String): PixivAccount? {
        val form = FormBody.Builder()
            .add("code", code)
            .add("code_verifier", verifier)
            .add("client_id", PixivConfig.CLIENT_ID)
            .add("client_secret", PixivConfig.CLIENT_SECRET)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", PixivConfig.REDIRECT_URI)
            .add("include_policy", "true")
            .build()
        val request = Request.Builder().url(PixivConfig.OAUTH_TOKEN_URL).post(form).build()
        val body = execute(request, allowRetry = false)
        val dto = json.decodeFromString(OAuthTokenResponseDto.serializer(), body)
        return dto.toAccount()
    }

    suspend fun refreshToken(refreshToken: String): PixivAccount? {
        val form = FormBody.Builder()
            .add("refresh_token", refreshToken)
            .add("client_id", PixivConfig.CLIENT_ID)
            .add("client_secret", PixivConfig.CLIENT_SECRET)
            .add("grant_type", "refresh_token")
            .add("include_policy", "true")
            .build()
        val request = Request.Builder().url(PixivConfig.OAUTH_TOKEN_URL).post(form).build()
        return runCatching {
            val body = execute(request, allowRetry = false)
            json.decodeFromString(OAuthTokenResponseDto.serializer(), body).toAccount(refreshToken)
        }.getOrNull()
    }

    // ── 浏览 ─────────────────────────────────────────────────────────────────

    /**
     * 拉取列表。nextUrl 非空时直接透传分页地址（P 站分页 URL 自带全部参数）。
     */
    suspend fun illusts(
        media: PixivMediaType,
        sectionKey: String,
        nextUrl: String? = null,
    ): IllustPage {
        require(media != PixivMediaType.Novel) { "小说列表请调用 novels()" }
        val url: String = nextUrl ?: buildUrl(
            when (media) {
                PixivMediaType.Illust ->
                    if (sectionKey == BrowseSections.RECOMMEND) "v1/illust/recommended" else "v1/illust/ranking"

                PixivMediaType.Manga ->
                    if (sectionKey == BrowseSections.RECOMMEND) "v1/manga/recommended" else "v1/illust/ranking"

                PixivMediaType.Novel -> "v1/novel/ranking"
            },
            *buildList {
                // 与安卓端一致：推荐流统一 for_ios，榜单统一 for_android
                add("filter" to if (sectionKey == BrowseSections.RECOMMEND) "for_ios" else "for_android")
                if (sectionKey != BrowseSections.RECOMMEND) add("mode" to sectionKey)
                if (media == PixivMediaType.Illust && sectionKey == BrowseSections.RECOMMEND) {
                    add("include_ranking_label" to "true")
                }
            }.toTypedArray(),
        )
        val dto = json.decodeFromString(IllustListResponseDto.serializer(), execute(get(url)))
        return IllustPage(
            items = dto.illusts.filter { it.id.isNotBlank() }.map { it.toModel() },
            nextUrl = dto.nextUrl,
        )
    }

    suspend fun search(word: String, page: Int = 1): IllustPage {
        val url = buildUrl(
            "v1/search/illust",
            "word" to word,
            "filter" to "for_android",
            "merge_plain_keyword_results" to "true",
            "search_target" to "partial_match_for_tags",
            "sort" to "date_desc",
            "page" to page.toString(),
        )
        val dto = json.decodeFromString(IllustListResponseDto.serializer(), execute(get(url)))
        return IllustPage(dto.illusts.map { it.toModel() }, dto.nextUrl)
    }

    /** 速览漫步（与安卓端 PixivApi.getWalkthrough 一致：v1/walkthrough/illusts + offset 分页）。 */
    suspend fun walkthrough(offset: Int? = null): IllustPage {
        val url = if (offset == null) {
            buildUrl("v1/walkthrough/illusts")
        } else {
            buildUrl("v1/walkthrough/illusts", "offset" to offset.toString())
        }
        val dto = json.decodeFromString(IllustListResponseDto.serializer(), execute(get(url)))
        return IllustPage(
            items = dto.illusts.filter { it.id.isNotBlank() }.map { it.toModel() },
            nextUrl = dto.nextUrl,
        )
    }

    /** 通用分页透传：next_url 自带全部参数，直接拉下一页。 */
    suspend fun nextPage(url: String): IllustPage {
        val dto = json.decodeFromString(IllustListResponseDto.serializer(), execute(get(url)))
        return IllustPage(
            items = dto.illusts.filter { it.id.isNotBlank() }.map { it.toModel() },
            nextUrl = dto.nextUrl,
        )
    }

    suspend fun illustDetail(illustId: Long): PixivIllust? {
        val url = buildUrl("v1/illust/detail", "illust_id" to illustId.toString(), "filter" to "for_android")
        return runCatching {
            val dto = json.decodeFromString(IllustDetailResponseDto.serializer(), execute(get(url)))
            dto.illust.toModel()
        }.getOrNull()
    }

    /** 相关作品（对齐安卓端 v2/illust/related，用于详情页底部横滑列表）。 */
    suspend fun related(illustId: Long, nextUrl: String? = null): IllustPage {
        val url = nextUrl ?: buildUrl(
            "v2/illust/related",
            "illust_id" to illustId.toString(),
            "filter" to "for_android",
        )
        val dto = json.decodeFromString(IllustListResponseDto.serializer(), execute(get(url)))
        return IllustPage(dto.illusts.map { it.toModel() }, dto.nextUrl)
    }

    // ── 评论（必须 v3，v1 已下线恒 404）────────────────────────────────────────

    suspend fun comments(illustId: Long, offset: Int = 0): Pair<List<PixivComment>, String?> {
        val url = buildUrl(
            "v3/illust/comments",
            "illust_id" to illustId.toString(),
            "offset" to offset.toString(),
        )
        val dto = json.decodeFromString(CommentListResponseDto.serializer(), execute(get(url)))
        return dto.comments.map { it.toModel() } to dto.nextUrl
    }

    suspend fun addComment(illustId: Long, text: String): PixivComment? {
        val form = FormBody.Builder()
            .add("illust_id", illustId.toString())
            .add("comment", text)
            .add("parent_comment_id", "")
            .build()
        val request = authBuilder(buildUrl("v1/illust/comment/add")).post(form).build()
        return runCatching {
            val dto = json.decodeFromString(CommentAddResponseDto.serializer(), execute(request))
            dto.comment?.toModel()
        }.getOrNull()
    }

    // ── 收藏 ─────────────────────────────────────────────────────────────────

    suspend fun setBookmark(illustId: Long, add: Boolean): Boolean {
        val request = if (add) {
            val form = FormBody.Builder()
                .add("illust_id", illustId.toString())
                .add("restrict", "public")
                .build()
            authBuilder(buildUrl("v2/illust/bookmark/add")).post(form).build()
        } else {
            val form = FormBody.Builder()
                .add("illust_id", illustId.toString())
                .build()
            authBuilder(buildUrl("v1/illust/bookmark/delete")).post(form).build()
        }
        return runCatching { execute(request); true }.getOrDefault(false)
    }

    suspend fun currentUser(): PixivAccount? = session.account.value

    /** 小说收藏：新设 / 取消（对齐安卓端 PixivApi 的 v2/novel/bookmark/add 与 v1/novel/bookmark/delete）。 */
    suspend fun setNovelBookmark(novelId: Long, add: Boolean): Boolean {
        val request = if (add) {
            val form = FormBody.Builder()
                .add("novel_id", novelId.toString())
                .add("restrict", "public")
                .build()
            authBuilder(buildUrl("v2/novel/bookmark/add")).post(form).build()
        } else {
            val form = FormBody.Builder()
                .add("novel_id", novelId.toString())
                .build()
            authBuilder(buildUrl("v1/novel/bookmark/delete")).post(form).build()
        }
        return runCatching { execute(request); true }.getOrDefault(false)
    }

    // ── 小说（对齐安卓端 PixivApi：只有榜单 + 详情 + 正文）────────────────────────

    suspend fun novels(sectionKey: String, nextUrl: String? = null): NovelPage {
        val url = nextUrl ?: buildUrl(
            "v1/novel/ranking",
            "filter" to "for_android",
            "mode" to sectionKey,
        )
        val dto = json.decodeFromString(NovelListResponseDto.serializer(), execute(get(url)))
        return NovelPage(
            items = dto.novels.filter { it.id.isNotBlank() }.map { it.toModel() },
            nextUrl = dto.nextUrl,
        )
    }

    suspend fun novelDetail(novelId: Long): PixivNovel? = runCatching {
        val url = buildUrl("v2/novel/detail", "novel_id" to novelId.toString(), "filter" to "for_android")
        json.decodeFromString(NovelDetailResponseDto.serializer(), execute(get(url))).novel.toModel()
    }.getOrNull()

    /**
     * 拉正文。优先 v1/novel/text（纯文本），失败回退 webview/v2/novel（HTML 内嵌 JSON）。
     * `[newpage]` 是 P 站的分页标记，统一替换成两个换行。
     */
    suspend fun novelText(novelId: Long): String {
        val direct = runCatching {
            val url = buildUrl("v1/novel/text", "novel_id" to novelId.toString())
            json.decodeFromString(NovelTextResponseDto.serializer(), execute(get(url))).novelText
        }.getOrNull()
        val raw = if (!direct.isNullOrBlank()) direct else webviewNovelText(novelId)
        return raw.replace("[newpage]", "\n\n").trim()
    }

    private suspend fun webviewNovelText(novelId: Long): String {
        val html = runCatching {
            execute(get(buildUrl("webview/v2/novel", "novel_id" to novelId.toString())))
        }.getOrNull().orEmpty()
        if (html.isBlank()) return ""
        val marker = html.indexOf("\"novel\":")
        val start = if (marker >= 0) html.indexOf("\"text\":\"", marker) else html.indexOf("\"text\":\"")
        if (start < 0) return ""
        val from = start + "\"text\":\"".length
        val sb = StringBuilder()
        var i = from
        while (i < html.length) {
            val ch = html[i]
            when {
                ch == '\\' && i + 1 < html.length -> {
                    sb.append(ch).append(html[i + 1])
                    i += 2
                }

                ch == '"' -> break
                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
        return runCatching {
            json.parseToJsonElement("\"${sb}\"").jsonPrimitive.content
        }.getOrDefault(sb.toString())
    }

    // ── 内部工具 ─────────────────────────────────────────────────────────────

    private fun buildUrl(path: String, vararg params: Pair<String, String>): String {
        val base = if (path.startsWith("http")) path.toHttpUrl() else (PixivConfig.API_BASE_URL + path).toHttpUrl()
        val builder = base.newBuilder()
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    private fun get(url: String): Request = authBuilder(url).get().build()

    private fun authBuilder(url: String): Request.Builder {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        session.accessToken?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

    private suspend fun execute(request: Request, allowRetry: Boolean = true): String =
        withContext(Dispatchers.IO) {
            var response = http.newCall(request).execute()
            if (response.code == 401 && allowRetry) {
                response.close()
                val newToken = session.refresh(this@PixivClient)
                if (newToken != null) {
                    val retried = request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .header("X-Pixiv-Retried", "1")
                        .build()
                    response = http.newCall(retried).execute()
                }
            }
            response.use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw PixivException(resp.code, body.take(300))
                body
            }
        }

    private fun OAuthTokenResponseDto.toAccount(fallbackRefresh: String? = null): PixivAccount? {
        if (accessToken.isBlank()) return null
        return PixivAccount(
            accessToken = accessToken,
            refreshToken = refreshToken.ifBlank { fallbackRefresh.orEmpty() },
            userId = user?.id.orEmpty(),
            name = user?.name?.ifBlank { user?.account.orEmpty() }.orEmpty(),
            account = user?.account.orEmpty(),
            avatarUrl = user?.profileImages?.bestUrl,
        )
    }

    private fun isoTime(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.US)
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(Date())
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 供 UI 判断是否 401（未登录/失效）。 */
    fun HttpUrl.describe(): String = toString()
}
