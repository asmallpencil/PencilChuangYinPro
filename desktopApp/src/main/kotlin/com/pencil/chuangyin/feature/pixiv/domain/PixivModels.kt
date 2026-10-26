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
    val cover: String get() = pages.firstOrNull()?.thumb ?: ""
}

/** 插画评论区的一条评论。 */
data class PixivComment(
    val id: Int,
    val comment: String,
    val date: String,
    val user: PixivUser,
    val hasReplies: Boolean,
)

/** 评论列表分页结果。 */
data class PixivCommentListResult(
    val items: List<PixivComment>,
    val nextUrl: String?,
)

/** 插画列表分页结果（桌面端简化版，无需 Android 侧的额外包装）。 */
data class PixivIllustListResult(
    val items: List<PixivIllust>,
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
)

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
