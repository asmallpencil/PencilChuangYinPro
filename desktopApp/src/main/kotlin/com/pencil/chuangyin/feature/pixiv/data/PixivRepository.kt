package com.pencil.chuangyin.feature.pixiv.data

import com.pencil.chuangyin.feature.pixiv.core.network.PixivApi
import com.pencil.chuangyin.feature.pixiv.domain.PixivComment
import com.pencil.chuangyin.feature.pixiv.domain.PixivCommentListResult
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllustListResult
import com.pencil.chuangyin.feature.pixiv.domain.toDomain

/**
 * 桌面端仓库：只保留首期需要的插画浏览 + 评论 + 收藏。
 * 与 Android 端的 PixivRepository 保持同样的接口语义，便于后续对齐。
 */
class PixivRepository(
    private val api: PixivApi,
) {

    suspend fun recommend(): PixivIllustListResult = api.getRecommend().toResult()

    suspend fun ranking(mode: String = "day", date: String? = null): PixivIllustListResult =
        api.getRanking(mode = mode, date = date).toResult()

    suspend fun search(word: String, sort: String = "date_desc"): PixivIllustListResult =
        api.getSearchIllust(word = word, sort = sort).toResult()

    suspend fun nextIllustPage(nextUrl: String): PixivIllustListResult =
        api.getIllustPage(nextUrl).toResult()

    suspend fun detail(illustId: Int): PixivIllust =
        api.getIllustDetail(illustId).illust.toDomain()

    suspend fun related(illustId: Int): PixivIllustListResult =
        api.getIllustRelated(illustId).toResult()

    suspend fun comments(illustId: Int): PixivCommentListResult {
        val response = api.getIllustComments(illustId = illustId)
        return PixivCommentListResult(
            items = response.comments.map { it.toDomain() },
            nextUrl = response.nextUrl,
        )
    }

    suspend fun nextComments(nextUrl: String): PixivCommentListResult {
        val response = api.getCommentsPage(nextUrl)
        return PixivCommentListResult(
            items = response.comments.map { it.toDomain() },
            nextUrl = response.nextUrl,
        )
    }

    /** 发表评论；接口未回执新评论时返回 null（由 UI 兜底重新拉取列表）。 */
    suspend fun addComment(illustId: Int, text: String): PixivComment? =
        api.addIllustComment(illustId = illustId, comment = text).comment?.toDomain()

    suspend fun setBookmarked(illustId: Int, bookmarked: Boolean) {
        if (bookmarked) {
            api.addBookmark(illustId = illustId)
        } else {
            api.deleteBookmark(illustId = illustId)
        }
    }

    private fun com.pencil.chuangyin.feature.pixiv.data.dto.IllustListResponseDto.toResult() =
        PixivIllustListResult(
            items = illusts.map { it.toDomain() },
            nextUrl = nextUrl,
        )
}
