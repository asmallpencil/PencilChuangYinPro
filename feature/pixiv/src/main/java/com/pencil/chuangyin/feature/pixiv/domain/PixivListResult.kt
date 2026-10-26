package com.pencil.chuangyin.feature.pixiv.domain

/** 一页可续拉的插画列表结果。[nextUrl] 为空表示没有更多。 */
data class PixivListResult(
    val items: List<PixivIllust>,
    val nextUrl: String?,
)

/** 一页可续拉的小说列表结果。 */
data class PixivNovelListResult(
    val items: List<PixivNovel>,
    val nextUrl: String?,
)
