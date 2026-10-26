package com.pencil.chuangyin.feature.pixiv.data

import com.pencil.chuangyin.feature.pixiv.core.network.NovelWebTextParser
import com.pencil.chuangyin.feature.pixiv.core.network.PixivApi
import com.pencil.chuangyin.feature.pixiv.data.local.NovelBookmarkEntry
import com.pencil.chuangyin.feature.pixiv.data.local.PixivBookmarkStore
import com.pencil.chuangyin.feature.pixiv.data.local.PixivHistoryStore
import com.pencil.chuangyin.feature.pixiv.data.local.PixivNovelBookmarkStore
import com.pencil.chuangyin.feature.pixiv.data.local.PixivPrefsStore
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.feature.pixiv.domain.PixivComment
import com.pencil.chuangyin.feature.pixiv.domain.PixivCommentListResult
import com.pencil.chuangyin.feature.pixiv.domain.PixivListResult
import com.pencil.chuangyin.feature.pixiv.domain.PixivNovel
import com.pencil.chuangyin.feature.pixiv.domain.PixivNovelListResult
import com.pencil.chuangyin.feature.pixiv.domain.PixivSearchSort
import com.pencil.chuangyin.feature.pixiv.domain.PixivSearchTarget
import com.pencil.chuangyin.feature.pixiv.domain.filterNovelR18
import com.pencil.chuangyin.feature.pixiv.domain.filterR18
import com.pencil.chuangyin.feature.pixiv.domain.stripNovelMarkers
import com.pencil.chuangyin.feature.pixiv.domain.toDomain
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_MANGA
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 插画/漫画/小说数据统一入口：列表/详情/相关/收藏/分页/原图下载/历史/长期下载。 */
@Singleton
class PixivRepository @Inject constructor(
    private val api: PixivApi,
    private val downloader: PixivImageDownloader,
    private val downloadManager: PixivDownloadManager,
    private val historyStore: PixivHistoryStore,
    private val bookmarkStore: PixivBookmarkStore,
    private val novelBookmarkStore: PixivNovelBookmarkStore,
    private val prefsStore: PixivPrefsStore,
) {

    /** 功能设置（默认模式/历史开关/高清图/R18 过滤）暴露给 UI 与数据层共用。 */
    val prefs = prefsStore

    /** 本地收藏快照（详情页爱心与服务端同步写入）。 */
    val bookmarks = bookmarkStore.bookmarks

    /** 本地小说收藏快照。 */
    val novelBookmarks = novelBookmarkStore.bookmarks

    // ── 插画 ──────────────────────────────────────────────────────────────────

    suspend fun recommended(): PixivListResult =
        api.getRecommend().toResult()

    suspend fun ranking(mode: String, date: String? = null): PixivListResult =
        api.getRanking(mode = mode, date = date).toResult()

    suspend fun search(
        word: String,
        sort: PixivSearchSort = PixivSearchSort.DATE_DESC,
        target: PixivSearchTarget = PixivSearchTarget.PARTIAL_TAG,
    ): PixivListResult =
        api.getSearchIllust(
            word = word,
            sort = sort.apiValue,
            searchTarget = target.apiValue,
        ).toResult()

    suspend fun nextPage(url: String): PixivListResult =
        api.getIllustPage(url).toResult()

    suspend fun detail(illustId: Int): PixivIllust =
        api.getIllustDetail(illustId).illust.toDomain()

    suspend fun related(illustId: Int): PixivListResult =
        api.getIllustRelated(illustId).toResult()

    /** 插画评论区（分页拉取）。 */
    suspend fun comments(illustId: Int): PixivCommentListResult =
        api.getIllustComments(illustId).toCommentResult()

    /** 评论续拉。 */
    suspend fun commentNextPage(url: String): PixivCommentListResult =
        api.getCommentsPage(url).toCommentResult()

    /**
     * 发表插画评论，返回接口回执中的评论（正常情况下非空，供 UI 立即上屏）。
     * 回执缺失时返回 null，由上层退化为重新拉取第一页。
     */
    suspend fun addComment(illustId: Int, comment: String): PixivComment? =
        api.addIllustComment(illustId = illustId, comment = comment).comment?.toDomain()

    /** 切换收藏；restrict=public 首期固定公开收藏。 */
    suspend fun setBookmarked(illustId: Int, bookmarked: Boolean) {
        if (bookmarked) {
            api.addBookmark(illustId = illustId, restrict = "public")
        } else {
            api.deleteBookmark(illustId = illustId)
        }
    }

    suspend fun downloadForPrint(
        illust: PixivIllust,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<File> = downloader.downloadAllPages(illust, onProgress)

    // ── 漫画 ──────────────────────────────────────────────────────────────────

    suspend fun mangaRecommended(): PixivListResult =
        api.getMangaRecommend().toResult()

    /** 漫画排行榜（mode 取 day_manga/week_manga/month_manga 等，复用 illust/ranking）。 */
    suspend fun mangaRanking(mode: String, date: String? = null): PixivListResult =
        api.getRanking(mode = mode, date = date).toResult()

    suspend fun searchManga(
        word: String,
        sort: PixivSearchSort = PixivSearchSort.DATE_DESC,
        target: PixivSearchTarget = PixivSearchTarget.PARTIAL_TAG,
    ): PixivListResult {
        // App API 无独立漫画搜索端点：复用插画搜索，客户端按 type == "manga" 过滤。
        val page = api.getSearchIllust(
            word = word,
            sort = sort.apiValue,
            searchTarget = target.apiValue,
        ).toResult()
        return page.copy(items = page.items.filter { it.type == MEDIA_MANGA })
    }

    /** 速览漫步（offset 分页；下一页 URL 为空时可用本方法按条数续拉）。 */
    suspend fun walkthrough(offset: Int? = null): PixivListResult =
        api.getWalkthrough(offset).toResult()

    // ── 小说 ──────────────────────────────────────────────────────────────────

    suspend fun novelRanking(mode: String = NOVEL_RANK_DEFAULT, lang: String? = null): PixivNovelListResult =
        api.getNovelRanking(mode = mode, lang = lang).toNovelResult()

    suspend fun novelNextPage(url: String): PixivNovelListResult =
        api.getNovelPage(url).toNovelResult()

    /** 小说搜索（与插画搜索平行，走 /v1/search/novel）。 */
    suspend fun searchNovel(
        word: String,
        sort: PixivSearchSort = PixivSearchSort.DATE_DESC,
        target: PixivSearchTarget = PixivSearchTarget.PARTIAL_TAG,
        lang: String? = null,
    ): PixivNovelListResult =
        api.getSearchNovel(
            word = word,
            sort = sort.apiValue,
            searchTarget = target.apiValue,
            lang = lang,
        ).toNovelResult()

    suspend fun novelDetail(novelId: Int): PixivNovel =
        api.getNovelDetail(novelId).novel.toDomain()

    /**
     * 小说正文（已清洗 [newpage] 等标记）。
     * 主路径走 /webview/v2/novel（HTML 内嵌 JSON，旧 v1/novel/text 对部分作品恒 404）；
     * webview 拉取或解析失败时，再用旧纯文本端点兜底，两条路都失败才向上抛错。
     */
    suspend fun novelText(novelId: Int): String {
        val raw = withContext(Dispatchers.IO) {
            val webText = runCatching {
                api.getNovelWeb(novelId).use { body ->
                    NovelWebTextParser.parse(body.string())?.text
                }
            }.getOrNull()
            webText?.takeIf { it.isNotBlank() }
                ?: api.getNovelTextLegacy(novelId).text
        }
        return stripNovelMarkers(raw)
    }

    /** 切换小说收藏；restrict=public 首期固定公开收藏。 */
    suspend fun setNovelBookmarked(novel: PixivNovel, bookmarked: Boolean) {
        if (bookmarked) {
            api.addNovelBookmark(novelId = novel.id, restrict = "public")
        } else {
            api.deleteNovelBookmark(novelId = novel.id)
        }
        syncNovelBookmark(novel, bookmarked)
    }

    /** 小说爱心切换成功后同步本地小说收藏快照（供侧边栏「收藏」页展示）。 */
    suspend fun syncNovelBookmark(novel: PixivNovel, bookmarked: Boolean) {
        if (bookmarked) novelBookmarkStore.add(novel) else novelBookmarkStore.remove(novel.id)
    }

    suspend fun removeNovelBookmark(novelId: Int) = novelBookmarkStore.remove(novelId)

    suspend fun clearNovelBookmarks() = novelBookmarkStore.clear()

    /** 小说下载：把标题+正文保存为本地文本文件。 */
    suspend fun downloadNovel(novel: PixivNovel, text: String): File =
        withContext(Dispatchers.IO) {
            downloadManager.saveNovel(novelId = novel.id, title = novel.title, text = text)
        }

    /** 小说相关推荐。 */
    suspend fun novelRelated(novelId: Int): PixivNovelListResult =
        api.getNovelRelated(novelId).toNovelResult()

    // ── 历史 / 收藏 / 长期下载 ───────────────────────────────────────────────────

    suspend fun recordHistory(illust: PixivIllust) {
        if (!prefsStore.historyEnabled.value) return
        historyStore.add(illust)
    }

    /** 详情页爱心切换成功后同步本地收藏快照（供侧边栏「收藏」页展示）。 */
    suspend fun syncBookmark(illust: PixivIllust, bookmarked: Boolean) {
        if (bookmarked) bookmarkStore.add(illust) else bookmarkStore.remove(illust.id)
    }

    suspend fun removeBookmark(illustId: Int) = bookmarkStore.remove(illustId)

    suspend fun clearBookmarks() = bookmarkStore.clear()

    /** 入队「下载全部原图」长期保存任务（侧边栏下载进度可见）。 */
    fun enqueueDownload(illust: PixivIllust) = downloadManager.enqueue(illust)

    private suspend fun com.pencil.chuangyin.feature.pixiv.data.dto.IllustListResponseDto.toResult():
        PixivListResult {
        // R18 过滤集中在仓库层：推荐/榜单/搜索/相关/漫步所有列表统一生效。
        // 读内存缓存值（StateFlow），避免每个列表响应都挂起等 DataStore 磁盘读。
        val r18Filter = prefsStore.r18FilterEnabled.value
        return PixivListResult(
            items = illusts.map { it.toDomain() }.filterR18(r18Filter),
            nextUrl = nextUrl,
        )
    }

    private suspend fun com.pencil.chuangyin.feature.pixiv.data.dto.NovelListResponseDto.toNovelResult():
        PixivNovelListResult {
        val r18Filter = prefsStore.r18FilterEnabled.value
        return PixivNovelListResult(
            items = novels.map { it.toDomain() }.filterNovelR18(r18Filter),
            nextUrl = nextUrl,
        )
    }

    private fun com.pencil.chuangyin.feature.pixiv.data.dto.CommentListResponseDto.toCommentResult():
        PixivCommentListResult = PixivCommentListResult(
            items = comments.map { it.toDomain() },
            nextUrl = nextUrl,
        )

    companion object {
        const val NOVEL_RANK_DEFAULT = "day"
    }
}
