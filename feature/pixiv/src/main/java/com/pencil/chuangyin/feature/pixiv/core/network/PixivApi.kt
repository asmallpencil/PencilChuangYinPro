package com.pencil.chuangyin.feature.pixiv.core.network

import com.pencil.chuangyin.feature.pixiv.data.dto.CommentAddResponseDto
import com.pencil.chuangyin.feature.pixiv.data.dto.CommentListResponseDto
import com.pencil.chuangyin.feature.pixiv.data.dto.IllustDetailResponseDto
import com.pencil.chuangyin.feature.pixiv.data.dto.IllustListResponseDto
import com.pencil.chuangyin.feature.pixiv.data.dto.NovelDetailResponseDto
import com.pencil.chuangyin.feature.pixiv.data.dto.NovelListResponseDto
import com.pencil.chuangyin.feature.pixiv.data.dto.NovelTextResponseDto
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/** app-api.pixiv.net 业务接口（参考 Pix-EzViewer PixivApiService）。 */
interface PixivApi {

    @GET("v1/illust/recommended?filter=for_ios&include_ranking_label=true")
    suspend fun getRecommend(): IllustListResponseDto

    /** 漫画推荐（与插画推荐平行的独立入口）。 */
    @GET("v1/manga/recommended?filter=for_ios&include_ranking_label=true")
    suspend fun getMangaRecommend(): IllustListResponseDto

    /** 速览漫步：全屏沉浸式轮播推荐。 */
    @GET("v1/walkthrough/illusts")
    suspend fun getWalkthrough(@Query("offset") offset: Int? = null): IllustListResponseDto

    @GET("v1/illust/ranking?filter=for_android")
    suspend fun getRanking(
        @Query("mode") mode: String,
        @Query("date") date: String? = null,
    ): IllustListResponseDto

    /** 漫画搜索：App API 无独立漫画搜索端点，与插画搜索同走 /v1/search/illust，由仓库层按 type 过滤。 */
    @GET("v1/search/illust?filter=for_android&merge_plain_keyword_results=true")
    suspend fun getSearchIllust(
        @Query("word") word: String,
        @Query("sort") sort: String,
        @Query("search_target") searchTarget: String? = null,
        @Query("bookmark_num_min") bookmarkNumMin: Int? = null,
    ): IllustListResponseDto

    @GET("v1/illust/detail?filter=for_android")
    suspend fun getIllustDetail(
        @Query("illust_id") illustId: Int,
    ): IllustDetailResponseDto

    @GET("v2/illust/related?filter=for_android")
    suspend fun getIllustRelated(
        @Query("illust_id") illustId: Int,
    ): IllustListResponseDto

    @FormUrlEncoded
    @POST("v2/illust/bookmark/add")
    suspend fun addBookmark(
        @Field("illust_id") illustId: Int,
        @Field("restrict") restrict: String = "public",
        @Field("tags[]") tags: List<String>? = null,
    )

    @FormUrlEncoded
    @POST("v1/illust/bookmark/delete")
    suspend fun deleteBookmark(
        @Field("illust_id") illustId: Int,
    )

    /** 续拉任意含 next_url 的插画列表（推荐/排行/搜索/相关/漫步分页统一入口）。 */
    @GET
    suspend fun getIllustPage(@Url url: String): IllustListResponseDto

    // ── 评论 ──────────────────────────────────────────────────────────────────

    /**
     * 插画评论列表。
     * 注意：必须走 v3（v1/illust/comments 已被 App API 下线，恒返回 404，
     * 表现为「评论区永远显示暂无评论」）；与 PiPixiv 的参考实现保持一致。
     */
    @GET("v3/illust/comments")
    suspend fun getIllustComments(
        @Query("illust_id") illustId: Int,
        @Query("offset") offset: Int? = null,
    ): CommentListResponseDto

    /** 发表插画评论（成功时返回刚创建的评论）。 */
    @FormUrlEncoded
    @POST("v1/illust/comment/add")
    suspend fun addIllustComment(
        @Field("illust_id") illustId: Int,
        @Field("comment") comment: String,
    ): CommentAddResponseDto

    /** 续拉插画评论。 */
    @GET
    suspend fun getCommentsPage(@Url url: String): CommentListResponseDto

    /** 用户收藏的插画列表。 */
    @GET("v2/user/bookmarks/illust")
    suspend fun getIllustBookmarksList(
        @Query("user_id") userId: Int,
        @Query("restrict") restrict: String = "public",
    ): IllustListResponseDto

    // ── 小说 ──────────────────────────────────────────────────────────────────

    @GET("v1/novel/ranking?filter=for_android")
    suspend fun getNovelRanking(
        @Query("mode") mode: String,
        @Query("date") date: String? = null,
        @Query("lang") lang: String? = null,
    ): NovelListResponseDto

    @GET("v2/novel/detail")
    suspend fun getNovelDetail(@Query("novel_id") novelId: Int): NovelDetailResponseDto

    /**
     * 小说正文主入口（参考 Pix-EzViewer）：返回 HTML 页面，内嵌 `novel: {...}, isOwnWork` JSON。
     * 注意参数是 id 而非 novel_id；旧 v1/novel/text 对部分作品恒返回 404。
     */
    @GET("webview/v2/novel")
    suspend fun getNovelWeb(@Query("id") novelId: Int): ResponseBody

    /** 旧版纯文本正文，仅作 webview 解析失败时的兜底。 */
    @GET("v1/novel/text")
    suspend fun getNovelTextLegacy(@Query("novel_id") novelId: Int): NovelTextResponseDto

    /** 小说列表续拉。 */
    @GET
    suspend fun getNovelPage(@Url url: String): NovelListResponseDto

    /** 小说搜索（与插画搜索平行，走 /v1/search/novel）。 */
    @GET("v1/search/novel?filter=for_android")
    suspend fun getSearchNovel(
        @Query("word") word: String,
        @Query("sort") sort: String,
        @Query("search_target") searchTarget: String? = null,
        @Query("lang") lang: String? = null,
    ): NovelListResponseDto

    /** 小说收藏。 */
    @FormUrlEncoded
    @POST("v2/novel/bookmark/add")
    suspend fun addNovelBookmark(
        @Field("novel_id") novelId: Int,
        @Field("restrict") restrict: String = "public",
        @Field("tags[]") tags: List<String>? = null,
    )

    /** 取消小说收藏。 */
    @FormUrlEncoded
    @POST("v1/novel/bookmark/delete")
    suspend fun deleteNovelBookmark(
        @Field("novel_id") novelId: Int,
    )

    /** 用户收藏的小说列表。 */
    @GET("v2/user/bookmarks/novel")
    suspend fun getNovelBookmarksList(
        @Query("user_id") userId: Int,
        @Query("restrict") restrict: String = "public",
    ): NovelListResponseDto

    /** 小说相关推荐。 */
    @GET("v2/novel/related")
    suspend fun getNovelRelated(@Query("novel_id") novelId: Int): NovelListResponseDto
}
