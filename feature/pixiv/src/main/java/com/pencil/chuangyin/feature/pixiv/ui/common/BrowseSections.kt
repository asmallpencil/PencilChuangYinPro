package com.pencil.chuangyin.feature.pixiv.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pencil.chuangyin.feature.pixiv.domain.PixivRankModes
import com.pencil.chuangyin.resources.R as ResR

/** 浏览分区：推荐 + 安全榜单模式。 */
const val SECTION_RECOMMEND = "recommend"

/** 内容模式：插画 / 漫画 / 小说（三期把小说提为与插画/漫画同级的内容模式）。 */
const val MEDIA_ILLUST = "illust"
const val MEDIA_MANGA = "manga"
const val MEDIA_NOVEL = "novel"

val browseSections: List<String> = listOf(SECTION_RECOMMEND) + PixivRankModes.SAFE

val mangaSections: List<String> = listOf(SECTION_RECOMMEND) + PixivRankModes.MANGA_SAFE

/**
 * 小说榜单（P 站小说仅提供 ranking，无独立推荐）。
 * 注意：app-api `v1/novel/ranking` 不提供 month 月榜（与插画榜不同，月榜请求会直接报错），
 * 官方客户端小说榜 SFW 全量为：日榜 / 周榜 / 受男性欢迎 / 受女性欢迎。
 */
val novelSections: List<String> = listOf("day", "week", "day_male", "day_female")

/** 媒体模式芯片组（插画 / 漫画 / 小说）。 */
@Composable
fun pixivMediaChipGroup(selectedMode: String): PixivChipGroup = PixivChipGroup(
    selectedKey = selectedMode,
    options = listOf(
        PixivChipOption(MEDIA_ILLUST, stringResource(ResR.string.pixiv_mode_illust)),
        PixivChipOption(MEDIA_MANGA, stringResource(ResR.string.pixiv_mode_manga)),
        PixivChipOption(MEDIA_NOVEL, stringResource(ResR.string.pixiv_mode_novel)),
    ),
)

/** 榜单芯片组：榜单集合跟随媒体模式切换。 */
@Composable
fun pixivSectionChipGroup(mediaMode: String, selectedSection: String): PixivChipGroup {
    val sections = when (mediaMode) {
        MEDIA_MANGA -> mangaSections
        MEDIA_NOVEL -> novelSections
        else -> browseSections
    }
    val options = mutableListOf<PixivChipOption>()
    for (key in sections) {
        options += PixivChipOption(key, browseSectionLabel(key))
    }
    return PixivChipGroup(selectedKey = selectedSection, options = options)
}

@Composable
fun browseSectionLabel(key: String): String = when (key) {
    SECTION_RECOMMEND -> stringResource(ResR.string.pixiv_recommend)
    "day" -> stringResource(ResR.string.pixiv_rank_day)
    "week" -> stringResource(ResR.string.pixiv_rank_week)
    "month" -> stringResource(ResR.string.pixiv_rank_month)
    "week_original" -> stringResource(ResR.string.pixiv_rank_week_original)
    "week_rookie" -> stringResource(ResR.string.pixiv_rank_week_rookie)
    "day_male" -> stringResource(ResR.string.pixiv_rank_day_male)
    "day_female" -> stringResource(ResR.string.pixiv_rank_day_female)
    "day_manga" -> stringResource(ResR.string.pixiv_rank_day_manga)
    else -> key
}
