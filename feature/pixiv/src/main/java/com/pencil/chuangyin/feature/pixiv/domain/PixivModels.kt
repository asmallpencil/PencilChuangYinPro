package com.pencil.chuangyin.feature.pixiv.domain

import com.pencil.chuangyin.feature.pixiv.data.dto.CommentDto
import com.pencil.chuangyin.feature.pixiv.data.dto.IllustDto
import com.pencil.chuangyin.feature.pixiv.data.dto.TagDto

/** 领域模型：UI / 仓库层只依赖这些干净类型，不触碰网络 DTO。 */

data class PixivUser(
    val id: Int,
    val name: String,
    val account: String,
    val avatarUrl: String?,
    val isFollowed: Boolean,
)

data class PixivTag(
    val name: String,
    val translatedName: String?,
)

/**
 * 插画的一页（多图作品的每一张）。
 * [thumb]/[medium]/[large] 为列表/详情用图，[original] 为打印所需原图。
 */
data class PixivPage(
    val thumb: String,
    val medium: String,
    val large: String,
    val original: String,
)

data class PixivIllust(
    val id: Int,
    val title: String,
    val caption: String,
    val user: PixivUser,
    val tags: List<PixivTag>,
    val createDate: String,
    val width: Int,
    val height: Int,
    val pageCount: Int,
    val type: String,
    val xRestrict: Int,
    val totalView: Int,
    val totalBookmarks: Int,
    val isBookmarked: Boolean,
    val pages: List<PixivPage>,
) {
    /** 封面缩略图（网格用）。 */
    val cover: String get() = pages.firstOrNull()?.thumb ?: ""
}

/** 插画评论区的一条评论。 */
data class PixivComment(
    val id: Int,
    val comment: String,
    val date: String,
    val user: PixivUser,
    val hasReplies: Boolean,
    /** 评论贴图地址；纯贴图评论的 [comment] 为空串，正文只由这里提供。 */
    val stampUrl: String? = null,
)

/** 评论列表分页结果。 */
data class PixivCommentListResult(
    val items: List<PixivComment>,
    val nextUrl: String?,
)

fun TagDto.toDomain() = PixivTag(name = name, translatedName = translatedName)

fun CommentDto.toDomain() = PixivComment(
    id = id,
    comment = comment,
    date = date,
    user = PixivUser(
        id = user.id,
        name = user.name,
        account = user.account.orEmpty(),
        avatarUrl = user.profileImages?.bestUrl,
        isFollowed = user.isFollowed,
    ),
    hasReplies = hasReplies,
    stampUrl = stamp?.stampUrl?.takeIf { it.isNotBlank() },
)

/**
 * 把接口 DTO 合并为统一的多页领域模型：
 * - 多图：meta_pages 每页给四档尺寸；
 * - 单图：用 image_urls 三档 + meta_single_page.original_image_url 合成一页。
 */
fun IllustDto.toDomain(): PixivIllust {
    val domainPages = if (metaPages.isNotEmpty()) {
        metaPages.map { p ->
            PixivPage(
                thumb = p.imageUrls.squareMedium.orEmpty(),
                medium = p.imageUrls.medium.orEmpty(),
                large = p.imageUrls.large.orEmpty(),
                original = p.imageUrls.original.orEmpty(),
            )
        }
    } else {
        listOf(
            PixivPage(
                thumb = imageUrls?.squareMedium.orEmpty(),
                medium = imageUrls?.medium.orEmpty(),
                large = imageUrls?.large.orEmpty(),
                original = metaSinglePage?.originalImageUrl.orEmpty(),
            )
        )
    }
    return PixivIllust(
        id = id,
        title = title,
        caption = caption,
        user = PixivUser(
            id = user.id,
            name = user.name,
            account = user.account.orEmpty(),
            avatarUrl = user.profileImages?.bestUrl,
            isFollowed = user.isFollowed,
        ),
        tags = tags.map { it.toDomain() },
        createDate = createDate,
        width = width,
        height = height,
        pageCount = pageCount,
        type = type,
        xRestrict = xRestrict,
        totalView = totalView,
        totalBookmarks = totalBookmarks,
        isBookmarked = isBookmarked,
        pages = domainPages,
    )
}
