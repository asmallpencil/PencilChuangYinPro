package com.pencil.chuangyin.feature.pixiv.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.feature.pixiv.core.auth.PixivAccount
import com.pencil.chuangyin.feature.pixiv.core.auth.PixivAuthRepository
import com.pencil.chuangyin.feature.pixiv.data.PixivRepository
import com.pencil.chuangyin.feature.pixiv.data.local.PixivBlockedTagsStore
import com.pencil.chuangyin.feature.pixiv.data.local.PixivPrefsStore
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.feature.pixiv.domain.PixivListResult
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

    /** 下拉刷新：保留现有列表，只替换内容，避免刷新瞬间整屏闪空。 */
    fun refresh() = loadFirstPage(keepItems = true)

    /**
     * @param keepItems 下拉刷新时为 true：列表保持原样（最多重排），旧数据在请求返回前
     *   继续可见；为 false 时（切分区/切模式）先清空再加载。
     */
    private fun loadFirstPage(section: String = _state.value.section, keepItems: Boolean = false) {
        val wasEmpty = _state.value.items.isEmpty() && _state.value.novels.isEmpty()
        val keep = keepItems && !wasEmpty
        _state.update {
            it.copy(
                section = section,
                items = if (keep) it.items else emptyList(),
                novels = if (keep) it.novels else emptyList(),
                initialLoading = !keep && wasEmpty,
                refreshing = keep || !wasEmpty,
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
            val result = runCatching { fetchIllustFirstPage(mode, section) }
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
        // refreshing 期间占位列表还是旧的，此时续拉会把旧页接到新列表后面，直接跳过。
        if (current.loadingMore || current.initialLoading || current.refreshing) return
        // 上一次续拉失败后不再自动重试（避免"到底"时对接口连打），等下拉刷新重置 error。
        if (current.error) return
        val url = current.nextUrl
        if (url == null) {
            // 接口不再给出下一页地址（推荐流/榜单末尾）时，重新取一批当前分区并只追加新作品，
            // 让用户在主页永远刷不到底：内容见底就换一批，而不是卡住或提示"没有更多了"。
            recycleFirstPage()
            return
        }
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
                        onFailure = { _ -> it.copy(loadingMore = false, error = true) },
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
                    onFailure = { _ -> it.copy(loadingMore = false, error = true) },
                )
            }
        }
    }

    /** 插画/漫画的「当前分区首页」请求：推荐与榜单在这里分流。 */
    private suspend fun fetchIllustFirstPage(mode: String, section: String): PixivListResult = when {
        mode == MEDIA_MANGA && section == SECTION_RECOMMEND -> repository.mangaRecommended()
        mode == MEDIA_MANGA -> repository.mangaRanking(mode = section)
        section == SECTION_RECOMMEND -> repository.recommended()
        else -> repository.ranking(mode = section)
    }

    /**
     * 没有下一页地址时的循环续拉：再取一次当前分区首页，只把「列表里还没有的作品」接到末尾。
     * 若整页都是重复内容（接口对同一分区返回固定集合），就停在这里（error=true 阻止自动重试），
     * 等用户下拉刷新重新开始，避免对接口无意义地连打。
     */
    private fun recycleFirstPage() {
        val mode = _state.value.mediaMode
        val section = _state.value.section
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            if (mode == MEDIA_NOVEL) {
                val result = runCatching { repository.novelRanking(section) }
                _state.update { current ->
                    result.fold(
                        onSuccess = { page ->
                            val known = current.novels.mapTo(mutableSetOf()) { it.id }
                            val fresh = page.items.filter { known.add(it.id) }
                            if (fresh.isEmpty()) {
                                current.copy(loadingMore = false, error = true)
                            } else {
                                current.copy(
                                    novels = current.novels + fresh,
                                    nextUrl = page.nextUrl,
                                    loadingMore = false,
                                )
                            }
                        },
                        onFailure = { current.copy(loadingMore = false, error = true) },
                    )
                }
                return@launch
            }
            val result = runCatching { fetchIllustFirstPage(mode, section) }
            _state.update { current ->
                result.fold(
                    onSuccess = { page ->
                        val known = current.items.mapTo(mutableSetOf()) { it.id }
                        val fresh = page.items.filter { known.add(it.id) }
                        if (fresh.isEmpty()) {
                            current.copy(loadingMore = false, error = true)
                        } else {
                            current.copy(
                                items = current.items + fresh.filterBlockedTags(blockedTags.value),
                                nextUrl = page.nextUrl,
                                loadingMore = false,
                            )
                        }
                    },
                    onFailure = { current.copy(loadingMore = false, error = true) },
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
