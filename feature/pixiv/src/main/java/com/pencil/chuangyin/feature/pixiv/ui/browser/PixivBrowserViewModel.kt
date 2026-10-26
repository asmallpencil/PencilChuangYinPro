package com.pencil.chuangyin.feature.pixiv.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.feature.pixiv.data.local.BrowserBookmarkEntry
import com.pencil.chuangyin.feature.pixiv.data.local.BrowserHistoryEntry
import com.pencil.chuangyin.feature.pixiv.data.local.PixivBrowserStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 内置浏览器数据（书签 / 历史）。隐身模式的「不记历史」由 UI 层在调用 [recordHistory] 前判断。
 */
@HiltViewModel
class PixivBrowserViewModel @Inject constructor(
    private val browserStore: PixivBrowserStore,
) : ViewModel() {

    val bookmarks: StateFlow<List<BrowserBookmarkEntry>> = browserStore.bookmarks
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<BrowserHistoryEntry>> = browserStore.history
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addBookmark(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        viewModelScope.launch { browserStore.addBookmark(url, title.ifBlank { url }) }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { browserStore.removeBookmark(url) }
    }

    fun recordHistory(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        viewModelScope.launch { browserStore.recordHistory(url, title.ifBlank { url }) }
    }

    fun clearBookmarks() {
        viewModelScope.launch { browserStore.clearBookmarks() }
    }

    fun clearHistory() {
        viewModelScope.launch { browserStore.clearHistory() }
    }
}
