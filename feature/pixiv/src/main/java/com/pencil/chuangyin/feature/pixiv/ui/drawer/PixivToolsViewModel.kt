package com.pencil.chuangyin.feature.pixiv.ui.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.feature.pixiv.core.auth.PixivAccount
import com.pencil.chuangyin.feature.pixiv.core.auth.PixivAuthRepository
import com.pencil.chuangyin.feature.pixiv.data.PixivCacheManager
import com.pencil.chuangyin.feature.pixiv.data.PixivCacheSizes
import com.pencil.chuangyin.feature.pixiv.data.PixivDownloadManager
import com.pencil.chuangyin.feature.pixiv.data.PixivSavedImage
import com.pencil.chuangyin.feature.pixiv.data.local.BookmarkEntry
import com.pencil.chuangyin.feature.pixiv.data.local.HistoryEntry
import com.pencil.chuangyin.feature.pixiv.data.local.NovelBookmarkEntry
import com.pencil.chuangyin.feature.pixiv.data.local.PixivBlockedTagsStore
import com.pencil.chuangyin.feature.pixiv.data.local.PixivBookmarkStore
import com.pencil.chuangyin.feature.pixiv.data.local.PixivHistoryStore
import com.pencil.chuangyin.feature.pixiv.data.local.PixivNovelBookmarkStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 侧边栏各工具页（账号/历史/下载进度/图片管理/屏蔽标签/运行缓存）共享的 ViewModel。
 * 数据源均为进程级单例（Store/Manager），不同返回栈入口拿到的状态始终一致。
 */
@HiltViewModel
class PixivToolsViewModel @Inject constructor(
    private val authRepository: PixivAuthRepository,
    private val historyStore: PixivHistoryStore,
    private val bookmarkStore: PixivBookmarkStore,
    private val novelBookmarkStore: PixivNovelBookmarkStore,
    private val blockedTagsStore: PixivBlockedTagsStore,
    private val downloadManager: PixivDownloadManager,
    private val cacheManager: PixivCacheManager,
) : ViewModel() {

    val account: StateFlow<PixivAccount?> = authRepository.account
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.account.value)

    val history: StateFlow<List<HistoryEntry>> = historyStore.history
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** REQ6：本地收藏（详情页爱心收藏后在此页可见）。 */
    val bookmarks: StateFlow<List<BookmarkEntry>> = bookmarkStore.bookmarks
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** REQ5：本地小说收藏（小说阅读页爱心收藏后在此页可见）。 */
    val novelBookmarks: StateFlow<List<NovelBookmarkEntry>> = novelBookmarkStore.bookmarks
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val blockedTags: StateFlow<Set<String>> = blockedTagsStore.tags
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val downloadTasks: StateFlow<List<com.pencil.chuangyin.feature.pixiv.data.PixivDownloadTask>> =
        downloadManager.tasks

    val savedImages: StateFlow<List<PixivSavedImage>> = downloadManager.savedImages

    private val _cacheSizes = MutableStateFlow<PixivCacheSizes?>(null)
    val cacheSizes: StateFlow<PixivCacheSizes?> = _cacheSizes.asStateFlow()

    init {
        refreshCache()
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun addBlockedTag(tag: String) {
        viewModelScope.launch { blockedTagsStore.add(tag) }
    }

    fun removeBlockedTag(tag: String) {
        viewModelScope.launch { blockedTagsStore.remove(tag) }
    }

    fun removeHistory(illustId: Int) {
        viewModelScope.launch { historyStore.remove(illustId) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }

    /** REQ6：移除单条本地收藏（不影响服务端；详情页重新爱心即可恢复）。 */
    fun removeBookmark(illustId: Int) {
        viewModelScope.launch { bookmarkStore.remove(illustId) }
    }

    fun clearBookmarks() {
        viewModelScope.launch { bookmarkStore.clear() }
    }

    /** REQ5：移除单条本地小说收藏（不影响服务端）。 */
    fun removeNovelBookmark(novelId: Int) {
        viewModelScope.launch { novelBookmarkStore.remove(novelId) }
    }

    fun clearNovelBookmarks() {
        viewModelScope.launch { novelBookmarkStore.clear() }
    }

    fun removeDownloadTask(illustId: Int) = downloadManager.removeTask(illustId)

    fun deleteImage(image: PixivSavedImage) = downloadManager.deleteImage(image)

    fun deleteAllImages() = downloadManager.deleteAllImages()

    /** 图片管理页每次进入/删除后重新扫描本地文件（含打印缓存文件）。 */
    fun refreshSavedImages() = downloadManager.refreshSavedImages()

    /** zip 建档快捷查看：临时解压并返回页文件（离开时调用 [closeArchive] 回收）。 */
    suspend fun openArchive(image: PixivSavedImage): List<java.io.File> =
        downloadManager.extractArchive(image)

    /** 多图预览统一入口：压缩包解压 / 多页散图聚合直接返回页文件（离开时调用 [closeArchive] 回收）。 */
    suspend fun openPages(image: PixivSavedImage): List<java.io.File> =
        downloadManager.openPages(image)

    /** 取压缩包首张页图作为图片管理封面（只解压首页，进入时才完整解压）。 */
    suspend fun archiveCover(image: PixivSavedImage): java.io.File? =
        downloadManager.archiveFirstPageFile(image)

    fun closeArchive(illustId: Int) = downloadManager.clearViewCache(illustId)

    fun refreshCache() {
        viewModelScope.launch { _cacheSizes.value = cacheManager.snapshot() }
    }

    /** 清缓存后刷新体积；返回最新体积供 UI 弹提示。 */
    fun clearCache() {
        viewModelScope.launch {
            cacheManager.clearCache()
            _cacheSizes.value = cacheManager.snapshot()
        }
    }
}
