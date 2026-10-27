package com.pencil.chuangyin.feature.pixiv.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppSkeletonBox
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.motion.animateGridItemMotion
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.resources.R as ResR

/** 双列作品网格（含上拉加载更多）。 */
@Composable
fun IllustGrid(
    items: List<PixivIllust>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(dimensionResource(ResR.dimen.dimen_pixiv_grid_padding)),
    canLoadMore: Boolean = false,
    loadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    listState: LazyGridState = rememberLazyGridState(),
) {
    LaunchedEffect(items.size, canLoadMore, loadingMore) {
        if (!canLoadMore || loadingMore || onLoadMore == null) return@LaunchedEffect
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (last >= items.size - 3) onLoadMore()
    }

    val spacing = dimensionResource(ResR.dimen.dimen_pixiv_grid_spacing)
    val motion = rememberAppMotion()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items(items.size, key = { items[it].id }) { index ->
            IllustCard(
                illust = items[index],
                onClick = { onOpen(items[index].id) },
                // 增删/重排走 spatial spring：换源、刷新、筛选时卡片带惯性滑到新位置。
                modifier = animateGridItemMotion(motion),
            )
        }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppLoadingIndicator()
                }
            }
        }
    }
}

/**
 * 作品卡片：M3 卡片容器（surfaceContainerLow）+ 固定 3:4 图片区 + 固定两行标题。
 *
 * 两点都是刻意的：
 * 1. 容器色：文字必须有底，直接浮在页面背景上会散成一堆没有归属的灰字；
 *    这里沿用项目内容卡约定（surfaceContainerLow，随主题"控件不透明度"一起透出壁纸），
 *    而不是写死颜色或走 tonalElevation —— 后者的 surfaceColorAtElevation 会派生出不透明实色，
 *    覆盖掉主题对 surface 系颜色的 alpha 处理。
 * 2. 图片区固定宽高比：原来按每张作品的原图比例（0.62~1.6）动态设置，
 *    同一行的卡片高矮参差；改成统一 3:4 + Crop 裁剪后卡片高度才整齐。
 */
@Composable
fun IllustCard(
    illust: PixivIllust,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.clickable(onClick = onClick)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(CARD_IMAGE_ASPECT_RATIO),
            ) {
                // 用 rememberAsyncImagePainter + 状态覆盖层替代 SubcomposeAsyncImage：
                // 网格有几十张卡片同时存在，后者每张都走 Subcompose（loading/error 槽），
                // 是进入 P 站时的主要掉帧源；普通 painter 只在状态翻转时触发一次重组。
                val painter = rememberAsyncImagePainter(
                    model = illust.cover,
                    contentScale = ContentScale.Crop,
                )
                Image(
                    painter = painter,
                    contentDescription = illust.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> AppSkeletonBox(
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape,
                    )
                    is AsyncImagePainter.State.Error -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BrokenImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> Unit
                }

                if (illust.isBookmarked) {
                    BadgeBox(Modifier.align(Alignment.TopEnd)) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (illust.pageCount > 1) {
                    BadgeBox(Modifier.align(Alignment.BottomEnd)) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            // 标题固定占两行：一行标题的卡片与两行标题的卡片高度一致。
            AppText(
                text = illust.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = CARD_TITLE_LINES,
                minLines = CARD_TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
            )
        }
    }
}

/** 卡片图片区的统一宽高比（3:4），保证双列卡片高度整齐。 */
private const val CARD_IMAGE_ASPECT_RATIO = 0.75f

/** 标题固定行数（两行），短标题也占满两行高度。 */
private const val CARD_TITLE_LINES = 2

/** 半透明圆角小角标容器。 */
@Composable
private fun BadgeBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_s))
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .padding(6.dp),
    ) { content() }
}
