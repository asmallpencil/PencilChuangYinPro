package com.pencil.chuangyin.feature.pixiv.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.motion.animateItemMotion
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.pixiv.domain.PixivNovel
import com.pencil.chuangyin.feature.pixiv.domain.PixivSearchSort
import com.pencil.chuangyin.feature.pixiv.domain.PixivSearchTarget
import com.pencil.chuangyin.feature.pixiv.ui.PixivImageProvider
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenError
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenLoading
import com.pencil.chuangyin.feature.pixiv.ui.common.IllustGrid
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_MANGA
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_NOVEL
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_ILLUST
import com.pencil.chuangyin.feature.pixiv.ui.common.NovelRow
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivChipGroup
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivChipOption
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivFilterChipRow
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivSearchTopBar
import com.pencil.chuangyin.resources.R as ResR

@Composable
fun PixivSearchRoute(
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onOpenNovel: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PixivSearchViewModel = hiltViewModel(),
) {
    PixivImageProvider {
        val state by viewModel.state.collectAsStateWithLifecycle()
        PixivSearchContent(
            modifier = modifier,
            state = state,
            onBack = onBack,
            onOpenDetail = onOpenDetail,
            onOpenNovel = onOpenNovel,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::search,
            onModeChange = viewModel::setMediaMode,
            onSortChange = viewModel::setSort,
            onTargetChange = viewModel::setTarget,
            onLoadMore = viewModel::loadMore,
        )
    }
}

@Composable
private fun PixivSearchContent(
    state: PixivSearchState,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onOpenNovel: (Int) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onModeChange: (String) -> Unit,
    onSortChange: (PixivSearchSort) -> Unit,
    onTargetChange: (PixivSearchTarget) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pad = dimensionResource(ResR.dimen.dimen_global_padding_m)

    Scaffold(
        modifier = modifier,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PixivSearchTopBar(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    onBack = onBack,
                    placeholder = stringResource(ResR.string.pixiv_search_hint),
                    backContentDescription = stringResource(ResR.string.label_back),
                    searchContentDescription = stringResource(ResR.string.pixiv_search),
                )
                // 内容模式 / 排序 / 标签范围合并为一条分组芯片行（组间竖线分隔）。
                PixivFilterChipRow(
                    groups = searchChipGroups(state),
                    onSelect = { groupIndex, key ->
                        when (groupIndex) {
                            0 -> onModeChange(key)
                            1 -> onSortChange(
                                if (key == PixivSearchSort.DATE_DESC.name) {
                                    PixivSearchSort.DATE_DESC
                                } else {
                                    PixivSearchSort.DATE_ASC
                                },
                            )
                            else -> onTargetChange(
                                PixivSearchTarget.entries.firstOrNull { it.name == key }
                                    ?: PixivSearchTarget.PARTIAL_TAG,
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> FullScreenLoading()
                    state.error -> FullScreenError(
                        message = stringResource(ResR.string.pixiv_load_failed),
                        onRetry = onSearch,
                    )
                    state.searched && (if (state.mediaMode == MEDIA_NOVEL) state.novelResults.isEmpty() else state.results.isEmpty()) ->
                        com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenEmpty(
                            message = stringResource(ResR.string.pixiv_empty),
                        )
                    state.mediaMode == MEDIA_NOVEL -> NovelResultList(
                        items = state.novelResults,
                        canLoadMore = state.nextUrl != null,
                        loadingMore = state.loadingMore,
                        onLoadMore = onLoadMore,
                        onOpen = onOpenNovel,
                        contentPadding = PaddingValues(pad),
                    )
                    else -> IllustGrid(
                        items = state.results,
                        onOpen = onOpenDetail,
                        canLoadMore = state.nextUrl != null,
                        loadingMore = state.loadingMore,
                        onLoadMore = onLoadMore,
                        // 未搜索时顶部留出间距，网格为空不滚动。
                        contentPadding = PaddingValues(pad),
                    )
                }
            }
        }
    }
}

/** 搜索页筛选分组：内容模式 / 排序 / 标签范围。 */
@Composable
private fun searchChipGroups(state: PixivSearchState): List<PixivChipGroup> {
    val media = PixivChipGroup(
        selectedKey = state.mediaMode,
        options = listOf(
            PixivChipOption(MEDIA_ILLUST, stringResource(ResR.string.pixiv_mode_illust)),
            PixivChipOption(MEDIA_MANGA, stringResource(ResR.string.pixiv_mode_manga)),
            PixivChipOption(MEDIA_NOVEL, stringResource(ResR.string.pixiv_mode_novel)),
        ),
    )
    val sort = PixivChipGroup(
        selectedKey = state.sort.name,
        options = listOf(
            PixivChipOption(PixivSearchSort.DATE_DESC.name, stringResource(ResR.string.pixiv_sort_new)),
            PixivChipOption(PixivSearchSort.DATE_ASC.name, stringResource(ResR.string.pixiv_sort_old)),
        ),
    )
    val targetOptions = mutableListOf<PixivChipOption>()
    for (target in PixivSearchTarget.entries) {
        targetOptions += PixivChipOption(target.name, searchTargetLabel(target))
    }
    return listOf(media, sort, PixivChipGroup(state.target.name, targetOptions))
}

@Composable
private fun searchTargetLabel(target: PixivSearchTarget): String = when (target) {
    PixivSearchTarget.PARTIAL_TAG -> stringResource(ResR.string.pixiv_target_partial_tag)
    PixivSearchTarget.EXACT_TAG -> stringResource(ResR.string.pixiv_target_exact_tag)
    PixivSearchTarget.TITLE_CAPTION -> stringResource(ResR.string.pixiv_target_title_caption)
}

/** 小说搜索结果列表：条目复用共享 NovelRow，滚到底自动续拉。 */
@Composable
private fun NovelResultList(
    items: List<PixivNovel>,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpen: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val motion = rememberAppMotion()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(items, key = { it.id }) { novel ->
            NovelRow(
                novel = novel,
                onClick = { onOpen(novel.id) },
                modifier = animateItemMotion(motion),
            )
        }
        item {
            LaunchedEffect(items.size) {
                if (canLoadMore) onLoadMore()
            }
            if (loadingMore) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppLoadingIndicator(size = 28.dp)
                }
            }
        }
    }
}
