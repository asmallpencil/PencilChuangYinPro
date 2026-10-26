package com.pencil.chuangyin.feature.pixiv.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * P 站接口原始 DTO（kotlinx.serialization）—— 桌面端与 Android 端共用同一套映射。
 */

@Serializable
data class ProfileImagesDto(
    val medium: String? = null,
    @SerialName("px_16x16") val px16: String? = null,
    @SerialName("px_50x50") val px50: String? = null,
    @SerialName("px_170x170") val px170: String? = null,
) {
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

@Serializable
data class IllustImageUrlsDto(
    @SerialName("square_medium") val squareMedium: String,
    val medium: String,
    val large: String,
)

@Serializable
data class MetaSinglePageDto(
    @SerialName("original_image_url") val originalImageUrl: String? = null,
)

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

@Serializable
data class IllustListResponseDto(
    val illusts: List<IllustDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
data class IllustDetailResponseDto(
    val illust: IllustDto,
)

@Serializable
data class NovelImageUrlsDto(
    @SerialName("square_medium") val squareMedium: String? = null,
    val medium: String? = null,
    val large: String? = null,
)

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

@Serializable
data class NovelTextResponseDto(
    @SerialName("novel_text") val text: String = "",
)

@Serializable
data class NovelWebPayloadDto(
    val id: String = "",
    val title: String = "",
    val text: String = "",
)

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

@Serializable
data class CommentAddResponseDto(
    val comment: CommentDto? = null,
)

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
