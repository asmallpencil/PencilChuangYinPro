package com.pencil.chuangyin.desktop.pixiv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── 网络 DTO（字段与安卓端 PixivDtos.kt 一一对应）───────────────────────────────

@Serializable
data class ProfileImagesDto(
    val medium: String? = null,
    @SerialName("px_16x16") val px16: String? = null,
    @SerialName("px_50x50") val px50: String? = null,
    @SerialName("px_170x170") val px170: String? = null,
) {
    /**
     * 头像优先取 170，其次 50 / 16 / medium。
     * 安卓端历史 bug 是只映射了 medium 导致头像恒空，这里保留正确的回退链。
     */
    val bestUrl: String?
        get() = px170 ?: px50 ?: px16 ?: medium
}

@Serializable
data class PixivUserDto(
    val id: String = "",
    val name: String = "",
    val account: String = "",
    @SerialName("profile_images") val profileImages: ProfileImagesDto? = null,
    @SerialName("is_followed") val isFollowed: Boolean = false,
)

@Serializable
data class TagDto(
    val name: String = "",
    @SerialName("translated_name") val translatedName: String? = null,
)

@Serializable
data class ImageUrlsDto(
    @SerialName("px_128x128") val px128: String? = null,
    @SerialName("square_medium") val squareMedium: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val original: String? = null,
)

@Serializable
data class MetaSinglePageDto(
    @SerialName("original_image_url") val originalImageUrl: String? = null,
)

@Serializable
data class MetaPageDto(
    @SerialName("image_urls") val imageUrls: ImageUrlsDto? = null,
)

@Serializable
data class IllustDto(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val caption: String = "",
    val user: PixivUserDto? = null,
    val tags: List<TagDto> = emptyList(),
    @SerialName("create_date") val createDate: String = "",
    @SerialName("page_count") val pageCount: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("sanity_level") val sanityLevel: Int = 0,
    @SerialName("x_restrict") val xRestrict: Int = 0,
    @SerialName("image_urls") val imageUrls: ImageUrlsDto? = null,
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
    val illust: IllustDto = IllustDto(),
)

@Serializable
data class CommentDto(
    val id: String = "",
    val comment: String = "",
    val date: String = "",
    val user: PixivUserDto? = null,
    @SerialName("has_replies") val hasReplies: Boolean = false,
    val stamp: CommentStampDto? = null,
)

/** 贴图评论（stamp）：纯贴图评论的 `comment` 为空串，正文只能靠贴图呈现。 */
@Serializable
data class CommentStampDto(
    @SerialName("stamp_id") val stampId: Int = 0,
    @SerialName("stamp_url") val stampUrl: String = "",
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
data class OAuthTokenResponseDto(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("expires_in") val expiresIn: Int = 3600,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("refresh_token") val refreshToken: String = "",
    val user: OAuthUserDto? = null,
)

@Serializable
data class OAuthUserDto(
    val id: String = "",
    val name: String = "",
    val account: String = "",
    @SerialName("profile_images") val profileImages: ProfileImagesDto? = null,
)

// ── 领域模型（UI 层只依赖这些）────────────────────────────────────────────────

data class PixivAccount(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val name: String,
    val account: String,
    val avatarUrl: String?,
)

data class PixivIllust(
    val id: Long,
    val title: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String?,
    val coverUrl: String,
    val pageUrls: List<String>,
    val pageCount: Int,
    val width: Int,
    val height: Int,
    val totalBookmarks: Int,
    val totalView: Int,
    val tags: List<String>,
    val isBookmarked: Boolean,
    val caption: String,
    val createDate: String,
    val xRestrict: Int,
)

data class PixivComment(
    val id: String,
    val text: String,
    val date: String,
    val userName: String,
    val avatarUrl: String?,
    val stampUrl: String? = null,
)

data class IllustPage(
    val items: List<PixivIllust>,
    val nextUrl: String?,
)

fun IllustDto.toModel(): PixivIllust {
    val cover = imageUrls?.squareMedium ?: imageUrls?.medium ?: imageUrls?.large ?: ""
    val pages = buildList {
        metaPages.forEach { page ->
            val url = page.imageUrls?.large ?: page.imageUrls?.original ?: page.imageUrls?.medium
            if (!url.isNullOrBlank()) add(url)
        }
        if (isEmpty()) {
            val single = metaSinglePage?.originalImageUrl
                ?: imageUrls?.large ?: imageUrls?.medium ?: imageUrls?.original
            if (!single.isNullOrBlank()) add(single)
        }
    }
    return PixivIllust(
        id = id.toLongOrNull() ?: 0L,
        title = title,
        authorId = user?.id.orEmpty(),
        authorName = user?.name.orEmpty().ifBlank { "未知作者" },
        authorAvatar = user?.profileImages?.bestUrl,
        coverUrl = cover,
        pageUrls = pages,
        pageCount = pageCount.coerceAtLeast(1),
        width = width,
        height = height,
        totalBookmarks = totalBookmarks,
        totalView = totalView,
        tags = tags.map { it.translatedName?.takeIf(String::isNotBlank) ?: it.name },
        isBookmarked = isBookmarked,
        caption = caption,
        createDate = createDate,
        xRestrict = xRestrict,
    )
}

fun CommentDto.toModel(): PixivComment = PixivComment(
    id = id,
    text = comment,
    date = date,
    userName = user?.name.orEmpty().ifBlank { "匿名" },
    avatarUrl = user?.profileImages?.bestUrl,
    stampUrl = stamp?.stampUrl?.takeIf { it.isNotBlank() },
)
