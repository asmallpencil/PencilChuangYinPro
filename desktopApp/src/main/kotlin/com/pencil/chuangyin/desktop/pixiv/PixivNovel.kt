package com.pencil.chuangyin.desktop.pixiv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── 小说 DTO（字段与安卓端 PixivDtos.kt 的小说段一一对应）─────────────────────────

@Serializable
data class NovelImageUrlsDto(
    @SerialName("square_medium") val squareMedium: String? = null,
    val medium: String? = null,
    val large: String? = null,
)

@Serializable
data class NovelDto(
    val id: String = "",
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
    val novel: NovelDto = NovelDto(),
)

@Serializable
data class NovelTextResponseDto(
    @SerialName("novel_text") val novelText: String = "",
)

// ── 领域模型 ────────────────────────────────────────────────────────────────────

data class PixivNovel(
    val id: Long,
    val title: String,
    val caption: String,
    val authorName: String,
    val authorAvatar: String?,
    val coverUrl: String,
    val textLength: Int,
    val tags: List<String>,
    val pageCount: Int,
    val totalView: Int,
    val totalBookmarks: Int,
    val xRestrict: Int,
    val createDate: String,
    val isBookmarked: Boolean,
)

data class NovelPage(
    val items: List<PixivNovel>,
    val nextUrl: String?,
)

fun NovelDto.toModel(): PixivNovel = PixivNovel(
    id = id.toLongOrNull() ?: 0L,
    title = title.ifBlank { "无标题" },
    caption = caption,
    authorName = user?.name.orEmpty().ifBlank { "未知作者" },
    authorAvatar = user?.profileImages?.bestUrl,
    coverUrl = imageUrls?.large ?: imageUrls?.medium ?: imageUrls?.squareMedium.orEmpty(),
    textLength = textLength,
    tags = tags.map { it.translatedName?.takeIf(String::isNotBlank) ?: it.name },
    pageCount = pageCount.coerceAtLeast(1),
    totalView = totalView,
    totalBookmarks = totalBookmarks,
    xRestrict = xRestrict,
    createDate = createDate,
    isBookmarked = isBookmarked,
)
