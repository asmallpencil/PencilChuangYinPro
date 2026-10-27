package com.pencil.chuangyin.feature.pixiv.ui.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.motion.animateItemMotion
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.pixiv.data.PixivRepository
import com.pencil.chuangyin.feature.pixiv.domain.PixivNovel
import com.pencil.chuangyin.feature.pixiv.ui.PixivImageProvider
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenEmpty
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenError
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenLoading
import com.pencil.chuangyin.feature.pixiv.ui.common.NovelRow
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivSubScaffold
import com.pencil.chuangyin.resources.R as ResR
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PixivNovelListState(
    val items: List<PixivNovel> = emptyList(),
    val initialLoading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val nextUrl: String? = null,
)

@HiltViewModel
class PixivNovelListViewModel @Inject constructor(
    private val repository: PixivRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PixivNovelListState())
    val state: StateFlow<PixivNovelListState> = _state.asStateFlow()

    init {
        loadFirst()
    }

    fun retry() = loadFirst()

    private fun loadFirst() {
        _state.update { it.copy(initialLoading = true, error = false) }
        viewModelScope.launch {
            runCatching { repository.novelRanking() }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = page.items,
                            initialLoading = false,
                            nextUrl = page.nextUrl,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(initialLoading = false, error = true) } }
        }
    }

    fun loadMore() {
        val url = _state.value.nextUrl ?: return
        if (_state.value.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching { repository.novelNextPage(url) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = it.items + page.items,
                            nextUrl = page.nextUrl,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }
}

@Composable
fun PixivNovelListRoute(
    onBack: () -> Unit,
    onOpenNovel: (Int) -> Unit,
    viewModel: PixivNovelListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PixivImageProvider {
        PixivSubScaffold(title = stringResource(ResR.string.pixiv_novel_title), onBack = onBack) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        state.initialLoading -> FullScreenLoading()
                        state.error && state.items.isEmpty() -> FullScreenError(
                            message = stringResource(ResR.string.pixiv_load_failed),
                            onRetry = viewModel::retry,
                        )
                        state.items.isEmpty() -> FullScreenEmpty(
                            message = stringResource(ResR.string.pixiv_novel_empty),
                        )
                        else -> {
                            val motion = rememberAppMotion()
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(
                                    dimensionResource(ResR.dimen.dimen_global_padding_s),
                                ),
                            ) {
                                items(state.items, key = { it.id }) { novel ->
                                    NovelRow(
                                        novel = novel,
                                        onClick = { onOpenNovel(novel.id) },
                                        modifier = animateItemMotion(motion),
                                    )
                                }
                                item {
                                    LaunchedEffect(state.items.size) {
                                        if (state.nextUrl != null) viewModel.loadMore()
                                    }
                                    if (state.loadingMore) {
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
                    }
                }
            }
        }
    }
}

// 小说条目统一走共享组件 ui.common.NovelRow（榜单/浏览/搜索/收藏复用）。
