package com.pencil.chuangyin.feature.pixiv.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.feature.pixiv.data.PixivRepository
import com.pencil.chuangyin.feature.pixiv.data.local.PixivBlockedTagsStore
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.feature.pixiv.domain.PixivListResult
import com.pencil.chuangyin.feature.pixiv.domain.PixivNovel
import com.pencil.chuangyin.feature.pixiv.domain.PixivNovelListResult
import com.pencil.chuangyin.feature.pixiv.domain.PixivSearchSort
import com.pencil.chuangyin.feature.pixiv.domain.PixivSearchTarget
import com.pencil.chuangyin.feature.pixiv.domain.filterBlockedTags
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_ILLUST
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_MANGA
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_NOVEL
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PixivSearchState(
    val query: String = "",
    val mediaMode: String = MEDIA_ILLUST,
    val sort: PixivSearchSort = PixivSearchSort.DATE_DESC,
    val target: PixivSearchTarget = PixivSearchTarget.PARTIAL_TAG,
    val results: List<PixivIllust> = emptyList(),
    val novelResults: List<PixivNovel> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val searched: Boolean = false,
    val nextUrl: String? = null,
)

@HiltViewModel
class PixivSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PixivRepository,
    blockedTagsStore: PixivBlockedTagsStore,
) : ViewModel() {

    private val initialQuery: String = savedStateHandle.get<String>(NAV_ARG_WORD).orEmpty()
    private val initialMode: String? = savedStateHandle.get<String>(NAV_ARG_MODE)

    private val _state = MutableStateFlow(
        PixivSearchState(query = initialQuery, mediaMode = initialMode ?: MEDIA_ILLUST),
    )
    val state: StateFlow<PixivSearchState> = _state.asStateFlow()

    private val blockedTags: StateFlow<Set<String>> = blockedTagsStore.tags
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        // 从详情页点击标签跳转过来时，带 word 参数并自动搜索。
        if (initialQuery.isNotBlank()) search()
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun setMediaMode(mode: String) {
        _state.update { it.copy(mediaMode = mode) }
        if (_state.value.searched && _state.value.query.isNotBlank()) search()
    }

    fun setSort(sort: PixivSearchSort) {
        _state.update { it.copy(sort = sort) }
        if (_state.value.searched && _state.value.query.isNotBlank()) search()
    }

    fun setTarget(target: PixivSearchTarget) {
        _state.update { it.copy(target = target) }
        if (_state.value.searched && _state.value.query.isNotBlank()) search()
    }

    fun search() {
        val word = _state.value.query.trim()
        if (word.isBlank() || _state.value.loading) return
        _state.update {
            it.copy(
                loading = true,
                error = false,
                searched = true,
                results = emptyList(),
                novelResults = emptyList(),
                nextUrl = null,
                loadingMore = false,
            )
        }
        viewModelScope.launch {
            val mode = _state.value.mediaMode
            val result = runCatching {
                when (mode) {
                    MEDIA_NOVEL -> repository.searchNovel(
                        word = word,
                        sort = _state.value.sort,
                        target = _state.value.target,
                    )
                    MEDIA_MANGA -> repository.searchManga(
                        word = word,
                        sort = _state.value.sort,
                        target = _state.value.target,
                    )
                    else -> repository.search(
                        word = word,
                        sort = _state.value.sort,
                        target = _state.value.target,
                    )
                }
            }
            _state.update {
                result.fold(
                    onSuccess = { page ->
                        when (page) {
                            is PixivNovelListResult -> it.copy(
                                loading = false,
                                novelResults = page.items,
                                nextUrl = page.nextUrl,
                            )
                            is PixivListResult -> it.copy(
                                loading = false,
                                results = page.items.filterBlockedTags(blockedTags.value),
                                nextUrl = page.nextUrl,
                            )
                            else -> it.copy(loading = false)
                        }
                    },
                    onFailure = { _ -> it.copy(loading = false, error = true) },
                )
            }
        }
    }

    fun loadMore() {
        val url = _state.value.nextUrl ?: return
        if (_state.value.loadingMore || _state.value.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val result = runCatching {
                if (_state.value.mediaMode == MEDIA_NOVEL) {
                    repository.novelNextPage(url)
                } else {
                    repository.nextPage(url)
                }
            }
            _state.update {
                result.fold(
                    onSuccess = { page ->
                        when (page) {
                            is PixivNovelListResult -> it.copy(
                                novelResults = it.novelResults + page.items,
                                nextUrl = page.nextUrl,
                                loadingMore = false,
                            )
                            is PixivListResult -> it.copy(
                                results = (it.results + page.items).filterBlockedTags(blockedTags.value),
                                nextUrl = page.nextUrl,
                                loadingMore = false,
                            )
                            else -> it.copy(loadingMore = false)
                        }
                    },
                    onFailure = { _ -> it.copy(loadingMore = false) },
                )
            }
        }
    }

    companion object {
        const val NAV_ARG_WORD = "word"
        const val NAV_ARG_MODE = "mode"
    }
}
