package com.pencil.chuangyin.feature.pixiv.domain

import com.pencil.chuangyin.feature.pixiv.data.dto.NovelDto

/** 小说领域模型（与 [PixivIllust] 平行，供小说模式列表/阅读器使用）。 */
data class PixivNovel(
    val id: Int,
    val title: String,
    val caption: String,
    val user: PixivUser?,
    val tags: List<PixivTag>,
    val cover: String,
    val createDate: String,
    val pageCount: Int,
    val textLength: Int,
    val xRestrict: Int,
    val totalBookmarks: Int,
    val isBookmarked: Boolean,
)

fun NovelDto.toDomain(): PixivNovel = PixivNovel(
    id = id,
    title = title,
    caption = caption,
    user = user?.let {
        PixivUser(
            id = it.id,
            name = it.name,
            account = it.account.orEmpty(),
            avatarUrl = it.profileImages?.bestUrl,
            isFollowed = it.isFollowed,
        )
    },
    tags = tags.map { it.toDomain() },
    cover = imageUrls?.squareMedium ?: imageUrls?.medium.orEmpty(),
    createDate = createDate,
    pageCount = pageCount,
    textLength = textLength,
    xRestrict = xRestrict,
    totalBookmarks = totalBookmarks,
    isBookmarked = isBookmarked,
)

/**
 * 清洗小说正文标记，得到适合纯文本阅读器展示的内容：
 * - [newpage] 转为分页分隔（连续空行 + 居中分隔符）；
 * - [chapter:标题] 提取标题；
 * - [[rb:原文>注音]] 保留原文并附注读音；[[jumpuri:链接>文字]] 保留作者写的文字（URL 常为外部地址或数字内部跳转 ID，不适合阅读/打印）；
 * - [jumpuri:链接=文字] 旧格式保留文字；[pixivimage]/[uploadedimage] 等占位移除；
 * - 其余未知方括号标记保守移除（不影响阅读）。
 */
fun stripNovelMarkers(raw: String): String = raw
    .replace(Regex("""\[\[rb:(.*?)>(.*?)]]"""), "$1($2)")
    .replace(Regex("""\[\[jumpuri:.*?>(.*?)]]"""), "$1")
    .replace(Regex("\\[chapter:[^]]*]")) { match ->
        val title = match.value.removeSurrounding("[", "]").substringAfter("chapter:", "")
        "\n\n$title\n\n"
    }
    .replace("[newpage]", "\n\n　　◇　　◇　　◇\n\n")
    .replace(Regex("\\[jumpuri:[^=\\]]*=([^]]*)]"), "$1")
    .replace(Regex("\\[/?jumpuri]"), "")
    .replace(Regex("""\[(pixivimage|uploadedimage|jump)[^\]]*]"""), "")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()
