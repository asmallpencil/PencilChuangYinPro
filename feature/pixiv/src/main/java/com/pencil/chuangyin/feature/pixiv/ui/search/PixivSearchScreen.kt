package com.pencil.chuangyin.feature.pixiv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.ui.components.AppFilterChip
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.ui.motion.animateItemMotion
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.pixiv.domain.PixivNovel
import com.pencil.chuangyin.feature.pixiv.domain.PixivSearchSort
import com.pencil.chuangyin.feature.pixiv.domain.PixivSearchTarget
import com.pencil.chuangyin.feature.pixiv.ui.PixivImageProvider
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenError
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenLoading
import com.pencil.chuangyin.feature.pixiv.ui.common.IllustGrid
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_ILLUST
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_MANGA
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_NOVEL
import com.pencil.chuangyin.feature.pixiv.ui.common.NovelRow
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val gap = dimensionResource(ResR.dimen.dimen_pixiv_chip_spacing)
    val glassActive = rememberLiquidGlassActive()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { androidx.compose.material3.Text(stringResource(ResR.string.pixiv_search)) },
                navigationIcon = {
                    AppIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(ResR.string.label_back),
                        glass = false,
                    )
                },
                modifier = Modifier.nativeGlassSurface(
                    shape = RoundedCornerShape(0.dp),
                    enabled = glassActive,
                    fallbackColor = MaterialTheme.colorScheme.surface,
                ),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = pad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AppTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        placeholder = stringResource(ResR.string.pixiv_search_hint),
                        singleLine = true,
                    )
                }
                AppIconButton(
                    onClick = onSearch,
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(ResR.string.pixiv_search),
                )
            }

            // 插画/漫画/小说分区搜索（与浏览页一致，不再把不同作品混在一个结果集）。
            AppSegmentedRow(
                options = listOf(
                    stringResource(ResR.string.pixiv_mode_illust),
                    stringResource(ResR.string.pixiv_mode_manga),
                    stringResource(ResR.string.pixiv_mode_novel),
                ),
                selectedIndex = when (state.mediaMode) {
                    MEDIA_MANGA -> 1
                    MEDIA_NOVEL -> 2
                    else -> 0
                },
                onSelect = { index ->
                    onModeChange(
                        when (index) {
                            1 -> MEDIA_MANGA
                            2 -> MEDIA_NOVEL
                            else -> MEDIA_ILLUST
                        },
                    )
                },
                modifier = Modifier.padding(horizontal = pad, vertical = gap),
            )

            AppSegmentedRow(
                options = listOf(
                    stringResource(ResR.string.pixiv_sort_new),
                    stringResource(ResR.string.pixiv_sort_old),
                ),
                selectedIndex = if (state.sort == PixivSearchSort.DATE_DESC) 0 else 1,
                onSelect = { index ->
                    onSortChange(
                        if (index == 0) PixivSearchSort.DATE_DESC else PixivSearchSort.DATE_ASC,
                    )
                },
                modifier = Modifier.padding(horizontal = pad, vertical = gap),
            )

            val targets = PixivSearchTarget.entries
            LazyRow(
                contentPadding = PaddingValues(horizontal = pad),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(targets, key = { it.name }) { target ->
                    AppFilterChip(
                        selected = target == state.target,
                        onClick = { onTargetChange(target) },
                        label = searchTargetLabel(target),
                    )
                }
            }

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

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SearchControlsPreview() {
    Column {
        AppTextField(value = "初音ミク", onValueChange = {}, placeholder = "搜索作品 / 标签")
        AppSegmentedRow(options = listOf("最新", "最旧"), selectedIndex = 0, onSelect = {})
    }
}
