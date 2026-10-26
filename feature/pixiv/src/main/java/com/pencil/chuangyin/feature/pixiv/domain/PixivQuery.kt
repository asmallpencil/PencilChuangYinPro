package com.pencil.chuangyin.feature.pixiv.domain

/** 榜单模式（首期仅安全模式；R18 待后续按账号等级开放）。 */
object PixivRankModes {
    val SAFE: List<String> = listOf(
        "day",
        "week",
        "month",
        "week_original",
        "week_rookie",
        "day_male",
        "day_female",
    )

    /** 漫画榜单安全模式（pixivez 实证可用；其余模式未验证，避免 404 不加入）。 */
    val MANGA_SAFE: List<String> = listOf("day_manga")
}

/** 搜索排序。popular_desc 需要 Premium，首期不提供。 */
enum class PixivSearchSort(val apiValue: String) {
    DATE_DESC("date_desc"),
    DATE_ASC("date_asc"),
}

/** 搜索匹配范围。 */
enum class PixivSearchTarget(val apiValue: String) {
    PARTIAL_TAG("partial_match_for_tags"),
    EXACT_TAG("exact_match_for_tags"),
    TITLE_CAPTION("title_and_caption"),
}

/** 客户端屏蔽标签过滤：命中任一屏蔽标签（原名/译名，忽略大小写与首尾空白）的作品剔除。 */
fun List<PixivIllust>.filterBlockedTags(blocked: Set<String>): List<PixivIllust> {
    if (blocked.isEmpty()) return this
    val needles = blocked.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toHashSet()
    if (needles.isEmpty()) return this
    return filterNot { illust ->
        illust.tags.any { tag ->
            tag.name.lowercase() in needles ||
                tag.translatedName?.lowercase() in needles
        }
    }
}

/** R18 过滤：开启时剔除 xRestrict>0（R-18/R-18G）的插画作品。 */
fun List<PixivIllust>.filterR18(enabled: Boolean): List<PixivIllust> =
    if (enabled) filter { it.xRestrict == 0 } else this

/** R18 过滤（小说）：开启时剔除 xRestrict>0 的小说。 */
fun List<PixivNovel>.filterNovelR18(enabled: Boolean): List<PixivNovel> =
    if (enabled) filter { it.xRestrict == 0 } else this
