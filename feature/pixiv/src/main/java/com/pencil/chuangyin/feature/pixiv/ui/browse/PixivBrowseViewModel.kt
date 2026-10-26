package com.pencil.chuangyin.feature.pixiv.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.feature.pixiv.core.auth.PixivAccount
import com.pencil.chuangyin.feature.pixiv.core.auth.PixivAuthRepository
import com.pencil.chuangyin.feature.pixiv.data.PixivRepository
import com.pencil.chuangyin.feature.pixiv.data.local.PixivBlockedTagsStore
import com.pencil.chuangyin.feature.pixiv.data.local.PixivPrefsStore
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.feature.pixiv.domain.PixivNovel
import com.pencil.chuangyin.feature.pixiv.domain.filterBlockedTags
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_ILLUST
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_MANGA
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_NOVEL
import com.pencil.chuangyin.feature.pixiv.ui.common.SECTION_RECOMMEND
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PixivFeedState(
    val mediaMode: String = MEDIA_ILLUST,
    val section: String = SECTION_RECOMMEND,
    val items: List<PixivIllust> = emptyList(),
    val novels: List<PixivNovel> = emptyList(),
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val nextUrl: String? = null,
)

@HiltViewModel
class PixivBrowseViewModel @Inject constructor(
    private val repository: PixivRepository,
    private val authRepository: PixivAuthRepository,
    blockedTagsStore: PixivBlockedTagsStore,
    private val prefsStore: PixivPrefsStore,
) : ViewModel() {

    val account: StateFlow<PixivAccount?> = authRepository.account
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.account.value)

    /** 屏蔽标签集合（列表加载/续拉后本地过滤，变化时重新过滤当前列表）。 */
    val blockedTags: StateFlow<Set<String>> = blockedTagsStore.tags
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _state = MutableStateFlow(PixivFeedState())
    val state: StateFlow<PixivFeedState> = _state.asStateFlow()

    init {
        // 登录到达后自动加载；登出后清空。
        viewModelScope.launch {
            var wasLoggedIn = false
            account.collect { acc ->
                if (acc != null && !wasLoggedIn) {
                    wasLoggedIn = true
                    // 应用「功能设置」中的默认内容模式后再首次加载（读内存缓存）。
                    val defaultMode = prefsStore.defaultMediaMode.value
                    _state.update {
                        it.copy(
                            mediaMode = defaultMode,
                            section = if (defaultMode == MEDIA_NOVEL) NOVEL_DEFAULT_SECTION else SECTION_RECOMMEND,
                        )
                    }
                    loadFirstPage()
                } else if (acc == null && wasLoggedIn) {
                    wasLoggedIn = false
                    _state.value = PixivFeedState()
                }
            }
        }
        // 屏蔽标签变更时即时重过滤（无需重新请求）。
        viewModelScope.launch {
            blockedTags.collect { tags ->
                _state.update { it.copy(items = it.items.filterBlockedTags(tags)) }
            }
        }
    }

    /** 切换插画/漫画/小说模式：模式不同时分区重置并重新加载（小说默认落到「日榜」）。 */
    fun selectMediaMode(mode: String) {
        if (mode == _state.value.mediaMode) return
        val resetSection = if (mode == MEDIA_NOVEL) NOVEL_DEFAULT_SECTION else SECTION_RECOMMEND
        _state.update { it.copy(mediaMode = mode, section = resetSection) }
        loadFirstPage()
    }

    fun selectSection(section: String) {
        if (section == _state.value.section) return
        loadFirstPage(section = section)
    }

    fun refresh() = loadFirstPage()

    private fun loadFirstPage(section: String = _state.value.section) {
        val wasEmpty = _state.value.items.isEmpty() && _state.value.novels.isEmpty()
        _state.update {
            it.copy(
                section = section,
                items = emptyList(),
                novels = emptyList(),
                initialLoading = wasEmpty,
                refreshing = !wasEmpty,
                loadingMore = false,
                error = false,
                nextUrl = null,
            )
        }
        val mode = _state.value.mediaMode
        viewModelScope.launch {
            if (mode == MEDIA_NOVEL) {
                val result = runCatching { repository.novelRanking(section) }
                _state.update { current ->
                    result.fold(
                        onSuccess = { page ->
                            current.copy(
                                novels = page.items,
                                nextUrl = page.nextUrl,
                                initialLoading = false,
                                refreshing = false,
                                error = false,
                            )
                        },
                        onFailure = {
                            current.copy(initialLoading = false, refreshing = false, error = true)
                        },
                    )
                }
                return@launch
            }
            val result = runCatching {
                when {
                    mode == MEDIA_MANGA && section == SECTION_RECOMMEND ->
                        repository.mangaRecommended()
                    mode == MEDIA_MANGA ->
                        repository.mangaRanking(mode = section)
                    section == SECTION_RECOMMEND ->
                        repository.recommended()
                    else ->
                        repository.ranking(mode = section)
                }
            }
            _state.update { current ->
                result.fold(
                    onSuccess = { page ->
                        current.copy(
                            items = page.items.filterBlockedTags(blockedTags.value),
                            nextUrl = page.nextUrl,
                            initialLoading = false,
                            refreshing = false,
                            error = false,
                        )
                    },
                    onFailure = {
                        current.copy(initialLoading = false, refreshing = false, error = true)
                    },
                )
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val url = current.nextUrl ?: return
        if (current.loadingMore || current.initialLoading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            if (_state.value.mediaMode == MEDIA_NOVEL) {
                val result = runCatching { repository.novelNextPage(url) }
                _state.update {
                    result.fold(
                        onSuccess = { page ->
                            it.copy(
                                novels = it.novels + page.items,
                                nextUrl = page.nextUrl,
                                loadingMore = false,
                            )
                        },
                        onFailure = { _ -> it.copy(loadingMore = false) },
                    )
                }
                return@launch
            }
            val result = runCatching { repository.nextPage(url) }
            _state.update {
                result.fold(
                    onSuccess = { page ->
                        it.copy(
                            items = (it.items + page.items).filterBlockedTags(blockedTags.value),
                            nextUrl = page.nextUrl,
                            loadingMore = false,
                        )
                    },
                    onFailure = { _ -> it.copy(loadingMore = false) },
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    companion object {
        /** 小说模式默认分区：日榜（safe）。 */
        const val NOVEL_DEFAULT_SECTION = "day"
    }
}
