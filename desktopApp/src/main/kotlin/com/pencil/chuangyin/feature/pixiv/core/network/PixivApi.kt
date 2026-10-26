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

/** app-api.pixiv.net 业务接口。 */
interface PixivApi {

    @GET("v1/illust/recommended?filter=for_ios&include_ranking_label=true")
    suspend fun getRecommend(): IllustListResponseDto

    @GET("v1/manga/recommended?filter=for_ios&include_ranking_label=true")
    suspend fun getMangaRecommend(): IllustListResponseDto

    @GET("v1/walkthrough/illusts")
    suspend fun getWalkthrough(@Query("offset") offset: Int? = null): IllustListResponseDto

    @GET("v1/illust/ranking?filter=for_android")
    suspend fun getRanking(
        @Query("mode") mode: String,
        @Query("date") date: String? = null,
    ): IllustListResponseDto

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

    @GET
    suspend fun getIllustPage(@Url url: String): IllustListResponseDto

    // ── 评论 ──────────────────────────────────────────────────────────────────

    /** 必须走 v3：v1/illust/comments 已被下线（恒 404）。 */
    @GET("v3/illust/comments")
    suspend fun getIllustComments(
        @Query("illust_id") illustId: Int,
        @Query("offset") offset: Int? = null,
    ): CommentListResponseDto

    @FormUrlEncoded
    @POST("v1/illust/comment/add")
    suspend fun addIllustComment(
        @Field("illust_id") illustId: Int,
        @Field("comment") comment: String,
    ): CommentAddResponseDto

    @GET
    suspend fun getCommentsPage(@Url url: String): CommentListResponseDto

    @GET("v2/user/bookmarks/illust")
    suspend fun getIllustBookmarksList(
        @Query("user_id") userId: Int,
        @Query("restrict") restrict: String = "public",
    ): IllustListResponseDto

    // ── 小说（桌面端暂未接入 UI，保留接口）────────────────────────────────────

    @GET("v1/novel/ranking?filter=for_android")
    suspend fun getNovelRanking(
        @Query("mode") mode: String,
        @Query("date") date: String? = null,
        @Query("lang") lang: String? = null,
    ): NovelListResponseDto

    @GET("v2/novel/detail")
    suspend fun getNovelDetail(@Query("novel_id") novelId: Int): NovelDetailResponseDto

    @GET("webview/v2/novel")
    suspend fun getNovelWeb(@Query("id") novelId: Int): ResponseBody

    @GET("v1/novel/text")
    suspend fun getNovelTextLegacy(@Query("novel_id") novelId: Int): NovelTextResponseDto

    @GET
    suspend fun getNovelPage(@Url url: String): NovelListResponseDto
}
