package com.pencil.chuangyin.feature.pixiv.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * P 站接口原始 DTO（kotlinx.serialization）。
 * 仅保留自研客户端首期需要的字段；Json 配置 ignoreUnknownKeys=true，
 * 因此接口返回的其余字段安全忽略。
 */

/**
 * 头像地址集合。App API（app-api.pixiv.net）实际返回的是 px_16x16 / px_50x50 / px_170x170，
 * 早先只映射了 `medium`（Web 端字段），导致用户头像恒为 null、评论区与作者信息里的头像全部空白。
 * 这里补齐三个尺寸，并保留 `medium` 作为兜底。
 */
@Serializable
data class ProfileImagesDto(
    val medium: String? = null,
    @SerialName("px_16x16") val px16: String? = null,
    @SerialName("px_50x50") val px50: String? = null,
    @SerialName("px_170x170") val px170: String? = null,
) {
    /** 取可用的最大尺寸头像；全缺时返回 null，由 UI 显示占位底色。 */
    val bestUrl: String?
        get() = px170 ?: px50 ?: px16 ?: medium
}

@Serializable
data class PixivUserDto(
    val id: Int = 0,
    val name: String = "",
    val account: String? = null,
    @SerialName("profile_image_urls") val profileImages: ProfileImagesDto? = null,
    @SerialName("is_followed") val isFollowed: Boolean = false,
)

@Serializable
data class TagDto(
    val name: String,
    @SerialName("translated_name") val translatedName: String? = null,
)

@Serializable
data class SeriesDto(
    val id: Int,
    val title: String,
)

/** 单图时接口在 image_urls 中给出的三种尺寸。 */
@Serializable
data class IllustImageUrlsDto(
    @SerialName("square_medium") val squareMedium: String,
    val medium: String,
    val large: String,
)

/** 多图（manga）时 meta_single_page 给出原图。 */
@Serializable
data class MetaSinglePageDto(
    @SerialName("original_image_url") val originalImageUrl: String? = null,
)

/** meta_pages 中每页的尺寸集合。 */
@Serializable
data class MetaPageImageUrlsDto(
    @SerialName("square_medium") val squareMedium: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val original: String? = null,
)

@Serializable
data class MetaPageDto(
    @SerialName("image_urls") val imageUrls: MetaPageImageUrlsDto,
)

@Serializable
data class IllustDto(
    val id: Int,
    val title: String,
    val type: String = "illust",
    val caption: String = "",
    val user: PixivUserDto,
    val tags: List<TagDto> = emptyList(),
    @SerialName("create_date") val createDate: String = "",
    @SerialName("page_count") val pageCount: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("sanity_level") val sanityLevel: Int = 0,
    @SerialName("x_restrict") val xRestrict: Int = 0,
    val series: SeriesDto? = null,
    @SerialName("image_urls") val imageUrls: IllustImageUrlsDto? = null,
    @SerialName("meta_single_page") val metaSinglePage: MetaSinglePageDto? = null,
    @SerialName("meta_pages") val metaPages: List<MetaPageDto> = emptyList(),
    @SerialName("total_view") val totalView: Int = 0,
    @SerialName("total_bookmarks") val totalBookmarks: Int = 0,
    @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
)

/** 推荐 / 排行榜 / 搜索 / 相关 共用列表响应（仅取 illusts 与 next_url）。 */
@Serializable
data class IllustListResponseDto(
    val illusts: List<IllustDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
data class IllustDetailResponseDto(
    val illust: IllustDto,
)

// ── 小说（二期）───────────────────────────────────────────────────────────────

/** 小说封面图（小说列表的 image_urls 字段，三档尺寸均可空）。 */
@Serializable
data class NovelImageUrlsDto(
    @SerialName("square_medium") val squareMedium: String? = null,
    val medium: String? = null,
    val large: String? = null,
)

/**
 * 小说列表/详情条目。系列字段（series）在部分接口中可能返回空数组，
 * 这里干脆不声明——Json 配置 ignoreUnknownKeys，异常 token 直接跳过。
 */
@Serializable
data class NovelDto(
    val id: Int,
    val title: String = "",
    val caption: String = "",
    @SerialName("image_urls") val imageUrls: NovelImageUrlsDto? = null,
    val tags: List<TagDto> = emptyList(),
    val user: PixivUserDto? = null,
    @SerialName("create_date") val createDate: String = "",
    @SerialName("page_count") val pageCount: Int = 1,
    @SerialName("text_length") val textLength: Int = 0,
    @SerialName("x_restrict") val xRestrict: Int = 0,
    @SerialName("total_view") val totalView: Int = 0,
    @SerialName("total_bookmarks") val totalBookmarks: Int = 0,
    @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
)

@Serializable
data class NovelListResponseDto(
    val novels: List<NovelDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
data class NovelDetailResponseDto(
    val novel: NovelDto,
)

/** /v1/novel/text 返回的纯正文（含 [newpage] 等标记，由上层清洗）。 */
@Serializable
data class NovelTextResponseDto(
    @SerialName("novel_text") val text: String = "",
)

/**
 * /webview/v2/novel 页面内嵌 JSON 的最小映射：只取正文所需字段。
 * images/illusts 空集合时可能返回 [] 而非 {}；这里不声明，靠 ignoreUnknownKeys 整键跳过。
 */
@Serializable
data class NovelWebPayloadDto(
    val id: String = "",
    val title: String = "",
    val text: String = "",
)

// ── 评论（三期）──────────────────────────────────────────────────────────────

/**
 * 单条评论。`user` 给出默认值而非必填：v3 评论接口在个别场景（已注销作者）下
 * 可能缺少 user 字段，避免整页评论因一条脏数据反序列化失败而全部消失。
 */
@Serializable
data class CommentDto(
    val id: Int = 0,
    val comment: String = "",
    @SerialName("date") val date: String = "",
    val user: PixivUserDto = PixivUserDto(),
    @SerialName("has_replies") val hasReplies: Boolean = false,
)

@Serializable
data class CommentListResponseDto(
    val comments: List<CommentDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

/** v1/illust/comment/add 成功响应：返回刚发布的评论。 */
@Serializable
data class CommentAddResponseDto(
    val comment: CommentDto? = null,
)

// ── 鉴权 / token ─────────────────────────────────────────────────────────────

@Serializable
data class OAuthProfileImagesDto(
    @SerialName("px_16x16") val px16: String? = null,
    @SerialName("px_50x50") val px50: String? = null,
    @SerialName("px_170x170") val px170: String = "",
)

@Serializable
data class OAuthUserDto(
    val id: Int,
    val name: String,
    val account: String = "",
    @SerialName("mail_address") val mailAddress: String = "",
    @SerialName("x_restrict") val xRestrict: Int = 0,
    @SerialName("profile_image_urls") val profileImages: OAuthProfileImagesDto? = null,
)

@Serializable
data class OAuthTokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int = 3600,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("refresh_token") val refreshToken: String,
    val user: OAuthUserDto,
)
