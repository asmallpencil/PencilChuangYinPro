package com.pencil.chuangyin.feature.pixiv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.feature.pixiv.domain.PixivNovel
import com.pencil.chuangyin.resources.R as ResR

private const val NOVEL_TAG_PREVIEW = 4

/** 小说列表通用条目：封面 + 标题 + 作者 + 字数 + 标签预览。浏览/榜单/搜索/收藏等列表共用。 */
@Composable
fun NovelRow(novel: PixivNovel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
    ) {
        Box(
            modifier = Modifier
                .width(dimensionResource(ResR.dimen.dimen_pixiv_novel_cover_width))
                .height(dimensionResource(ResR.dimen.dimen_pixiv_novel_cover_height))
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                .clip(MaterialTheme.shapes.small),
        ) {
            if (novel.cover.isNotBlank()) {
                PixivCoverImage(
                    model = novel.cover,
                    contentDescription = novel.title,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            AppText(
                text = novel.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
            )
            AppText(
                text = novel.user?.name.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
            )
            AppText(
                text = stringResource(ResR.string.pixiv_novel_text_length, novel.textLength),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
            )
            if (novel.tags.isNotEmpty()) {
                AppText(
                    text = novel.tags.take(NOVEL_TAG_PREVIEW).joinToString(" ") { "#${it.name}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                )
            }
        }
    }
}