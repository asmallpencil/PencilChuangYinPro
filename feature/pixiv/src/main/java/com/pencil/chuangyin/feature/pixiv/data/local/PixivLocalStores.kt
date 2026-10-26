package com.pencil.chuangyin.feature.pixiv.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pencil.chuangyin.feature.pixiv.core.network.PixivJson
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.feature.pixiv.domain.PixivNovel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pixivHistoryDataStore by preferencesDataStore(name = "pixiv_history")
private val Context.pixivPrefsDataStore by preferencesDataStore(name = "pixiv_prefs")
private val Context.pixivBookmarksDataStore by preferencesDataStore(name = "pixiv_bookmarks")
private val Context.pixivNovelBookmarksDataStore by preferencesDataStore(name = "pixiv_novel_bookmarks")
private val Context.pixivBrowserDataStore by preferencesDataStore(name = "pixiv_browser")

/** 浏览历史条目（可离线展示的最小作品快照）。 */
@Serializable
data class HistoryEntry(
    val illustId: Int,
    val title: String,
    val userName: String,
    val coverUrl: String,
    val viewedAt: Long,
)

/** 收藏条目（详情页点爱心成功后写入，供侧边栏「收藏」页离线展示）。 */
@Serializable
data class BookmarkEntry(
    val illustId: Int,
    val title: String,
    val userName: String,
    val coverUrl: String,
    val bookmarkedAt: Long,
)

/** 小说收藏条目（小说详情页点爱心成功后写入，供侧边栏「收藏」页离线展示）。 */
@Serializable
data class NovelBookmarkEntry(
    val novelId: Int,
    val title: String,
    val userName: String,
    val coverUrl: String,
    val bookmarkedAt: Long,
)

/** 内置浏览器书签。 */
@Serializable
data class BrowserBookmarkEntry(
    val url: String,
    val title: String,
    val addedAt: Long,
)

/** 内置浏览器历史（隐身模式下不记录）。 */
@Serializable
data class BrowserHistoryEntry(
    val url: String,
    val title: String,
    val visitedAt: Long,
)

/** 作品浏览历史（DataStore + JSON，最近 [MAX_HISTORY] 条，新的在前）。替代 Room 历史表。 */
@Singleton
class PixivHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("history_json")

    val history: Flow<List<HistoryEntry>> = context.pixivHistoryDataStore.data.map { prefs ->
        runCatching {
            prefs[key]?.let { PixivJson.decodeFromString<List<HistoryEntry>>(it) }
        }.getOrNull().orEmpty()
    }

    /** 记录一次浏览；同一作品再次浏览会顶到最前。 */
    suspend fun add(illust: PixivIllust) {
        context.pixivHistoryDataStore.edit { prefs ->
            val current = runCatching {
                prefs[key]?.let { PixivJson.decodeFromString<MutableList<HistoryEntry>>(it) }
            }.getOrNull() ?: mutableListOf()
            current.removeAll { it.illustId == illust.id }
            current.add(
                0,
                HistoryEntry(
                    illustId = illust.id,
                    title = illust.title,
                    userName = illust.user.name,
                    coverUrl = illust.cover,
                    viewedAt = System.currentTimeMillis(),
                ),
            )
            prefs[key] = PixivJson.encodeToString(current.take(MAX_HISTORY))
        }
    }

    suspend fun remove(illustId: Int) {
        context.pixivHistoryDataStore.edit { prefs ->
            val current = runCatching {
                prefs[key]?.let { PixivJson.decodeFromString<MutableList<HistoryEntry>>(it) }
            }.getOrNull() ?: mutableListOf()
            current.removeAll { it.illustId == illustId }
            prefs[key] = PixivJson.encodeToString(current)
        }
    }

    suspend fun clear() {
        context.pixivHistoryDataStore.edit { it.clear() }
    }

    private companion object {
        const val MAX_HISTORY = 200
    }
}

/** 作品收藏（DataStore + JSON，最近 [MAX_BOOKMARKS] 条，新的在前）。与服务端爱心状态保持同步。 */
@Singleton
class PixivBookmarkStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("bookmarks_json")

    val bookmarks: Flow<List<BookmarkEntry>> = context.pixivBookmarksDataStore.data.map { prefs ->
        runCatching {
            prefs[key]?.let { PixivJson.decodeFromString<List<BookmarkEntry>>(it) }
        }.getOrNull().orEmpty()
    }

    /** 收藏一个作品；同一作品再次收藏会顶到最前。 */
    suspend fun add(illust: PixivIllust) {
        context.pixivBookmarksDataStore.edit { prefs ->
            val current = runCatching {
                prefs[key]?.let { PixivJson.decodeFromString<MutableList<BookmarkEntry>>(it) }
            }.getOrNull() ?: mutableListOf()
            current.removeAll { it.illustId == illust.id }
            current.add(
                0,
                BookmarkEntry(
                    illustId = illust.id,
                    title = illust.title,
                    userName = illust.user.name,
                    coverUrl = illust.cover,
                    bookmarkedAt = System.currentTimeMillis(),
                ),
            )
            prefs[key] = PixivJson.encodeToString(current.take(MAX_BOOKMARKS))
        }
    }

    suspend fun remove(illustId: Int) {
        context.pixivBookmarksDataStore.edit { prefs ->
            val current = runCatching {
                prefs[key]?.let { PixivJson.decodeFromString<MutableList<BookmarkEntry>>(it) }
            }.getOrNull() ?: mutableListOf()
            current.removeAll { it.illustId == illustId }
            prefs[key] = PixivJson.encodeToString(current)
        }
    }

    suspend fun clear() {
        context.pixivBookmarksDataStore.edit { it.clear() }
    }

    private companion object {
        const val MAX_BOOKMARKS = 500
    }
}

/** 小说收藏（DataStore + JSON，与服务端小说爱心状态保持同步）。 */
@Singleton
class PixivNovelBookmarkStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("novel_bookmarks_json")

    val bookmarks: Flow<List<NovelBookmarkEntry>> = context.pixivNovelBookmarksDataStore.data.map { prefs ->
        runCatching {
            prefs[key]?.let { PixivJson.decodeFromString<List<NovelBookmarkEntry>>(it) }
        }.getOrNull().orEmpty()
    }

    /** 收藏一部小说；同一小说再次收藏会顶到最前。 */
    suspend fun add(novel: PixivNovel) {
        context.pixivNovelBookmarksDataStore.edit { prefs ->
            val current = runCatching {
                prefs[key]?.let { PixivJson.decodeFromString<MutableList<NovelBookmarkEntry>>(it) }
            }.getOrNull() ?: mutableListOf()
            current.removeAll { it.novelId == novel.id }
            current.add(
                0,
                NovelBookmarkEntry(
                    novelId = novel.id,
                    title = novel.title,
                    userName = novel.user?.name.orEmpty(),
                    coverUrl = novel.cover,
                    bookmarkedAt = System.currentTimeMillis(),
                ),
            )
            prefs[key] = PixivJson.encodeToString(current.take(MAX_BOOKMARKS))
        }
    }

    suspend fun remove(novelId: Int) {
        context.pixivNovelBookmarksDataStore.edit { prefs ->
            val current = runCatching {
                prefs[key]?.let { PixivJson.decodeFromString<MutableList<NovelBookmarkEntry>>(it) }
            }.getOrNull() ?: mutableListOf()
            current.removeAll { it.novelId == novelId }
            prefs[key] = PixivJson.encodeToString(current)
        }
    }

    suspend fun clear() {
        context.pixivNovelBookmarksDataStore.edit { it.clear() }
    }

    private companion object {
        const val MAX_BOOKMARKS = 500
    }
}

/** 屏蔽标签（DataStore StringSet）：浏览/榜单/搜索结果在客户端按标签过滤。 */
@Singleton
class PixivBlockedTagsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringSetPreferencesKey("blocked_tags")

    val tags: Flow<Set<String>> = context.pixivPrefsDataStore.data.map { it[key].orEmpty() }

    suspend fun add(tag: String) {
        val normalized = tag.trim()
        if (normalized.isEmpty()) return
        context.pixivPrefsDataStore.edit { prefs ->
            prefs[key] = (prefs[key].orEmpty() + normalized)
        }
    }

    suspend fun remove(tag: String) {
        context.pixivPrefsDataStore.edit { prefs ->
            prefs[key] = (prefs[key].orEmpty() - tag)
        }
    }
}

/**
 * P 站功能设置（DataStore，与屏蔽标签共用 pixiv_prefs）：
 * 默认内容模式 / 是否记录浏览历史 / 是否加载高清大图 / R18 内容过滤。
 * 仿原版可落地、可兼容的设置项。
 */
@Singleton
class PixivPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val defaultMediaModeKey = stringPreferencesKey("default_media_mode")
    private val historyEnabledKey = booleanPreferencesKey("history_enabled")
    private val hiResImagesKey = booleanPreferencesKey("hi_res_images")
    private val r18FilterKey = booleanPreferencesKey("r18_filter")

    /**
     * 单例级共享作用域：偏好流仅订阅一次 DataStore，值常驻内存。
     * 仓库层每次列表过滤直接读 [StateFlow.value]，避免每页响应都触发磁盘读（卡顿优化）。
     */
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val defaultMediaMode: StateFlow<String> =
        context.pixivPrefsDataStore.data.map { it[defaultMediaModeKey] ?: DEFAULT_MEDIA_MODE }
            .stateIn(storeScope, SharingStarted.Eagerly, DEFAULT_MEDIA_MODE)

    val historyEnabled: StateFlow<Boolean> =
        context.pixivPrefsDataStore.data.map { it[historyEnabledKey] ?: true }
            .stateIn(storeScope, SharingStarted.Eagerly, true)

    val hiResImages: StateFlow<Boolean> =
        context.pixivPrefsDataStore.data.map { it[hiResImagesKey] ?: false }
            .stateIn(storeScope, SharingStarted.Eagerly, false)

    /** R18 过滤默认开启：列表结果中 xRestrict>0 的作品被剔除。 */
    val r18FilterEnabled: StateFlow<Boolean> =
        context.pixivPrefsDataStore.data.map { it[r18FilterKey] ?: true }
            .stateIn(storeScope, SharingStarted.Eagerly, true)

    suspend fun setDefaultMediaMode(mode: String) {
        context.pixivPrefsDataStore.edit { it[defaultMediaModeKey] = mode }
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        context.pixivPrefsDataStore.edit { it[historyEnabledKey] = enabled }
    }

    suspend fun setHiResImages(enabled: Boolean) {
        context.pixivPrefsDataStore.edit { it[hiResImagesKey] = enabled }
    }

    suspend fun setR18FilterEnabled(enabled: Boolean) {
        context.pixivPrefsDataStore.edit { it[r18FilterKey] = enabled }
    }

    companion object {
        /** 默认内容模式（插画）。 */
        const val DEFAULT_MEDIA_MODE = "illust"
    }
}

/**
 * 内置浏览器数据（DataStore pixiv_browser）：书签 + 访问历史（隐身模式由上层跳过写入）。
 */
@Singleton
class PixivBrowserStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val bookmarksKey = stringPreferencesKey("bookmarks_json")
    private val historyKey = stringPreferencesKey("history_json")

    val bookmarks: Flow<List<BrowserBookmarkEntry>> =
        context.pixivBrowserDataStore.data.map { prefs ->
            runCatching {
                prefs[bookmarksKey]?.let {
                    PixivJson.decodeFromString<List<BrowserBookmarkEntry>>(it)
                }
            }.getOrNull().orEmpty()
        }

    val history: Flow<List<BrowserHistoryEntry>> =
        context.pixivBrowserDataStore.data.map { prefs ->
            runCatching {
                prefs[historyKey]?.let {
                    PixivJson.decodeFromString<List<BrowserHistoryEntry>>(it)
                }
            }.getOrNull().orEmpty()
        }

    suspend fun addBookmark(url: String, title: String) {
        if (url.isBlank()) return
        context.pixivBrowserDataStore.edit { prefs ->
            val current = runCatching {
                prefs[bookmarksKey]?.let {
                    PixivJson.decodeFromString<MutableList<BrowserBookmarkEntry>>(it)
                }
            }.getOrNull() ?: mutableListOf()
            current.removeAll { it.url == url }
            current.add(0, BrowserBookmarkEntry(url = url, title = title.ifBlank { url }, addedAt = System.currentTimeMillis()))
            prefs[bookmarksKey] = PixivJson.encodeToString(current.take(MAX_BROWSER_ITEMS))
        }
    }

    suspend fun removeBookmark(url: String) {
        context.pixivBrowserDataStore.edit { prefs ->
            val current = runCatching {
                prefs[bookmarksKey]?.let {
                    PixivJson.decodeFromString<MutableList<BrowserBookmarkEntry>>(it)
                }
            }.getOrNull() ?: mutableListOf()
            current.removeAll { it.url == url }
            prefs[bookmarksKey] = PixivJson.encodeToString(current)
        }
    }

    suspend fun recordHistory(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        context.pixivBrowserDataStore.edit { prefs ->
            val current = runCatching {
                prefs[historyKey]?.let {
                    PixivJson.decodeFromString<MutableList<BrowserHistoryEntry>>(it)
                }
            }.getOrNull() ?: mutableListOf()
            current.removeAll { it.url == url }
            current.add(0, BrowserHistoryEntry(url = url, title = title.ifBlank { url }, visitedAt = System.currentTimeMillis()))
            prefs[historyKey] = PixivJson.encodeToString(current.take(MAX_BROWSER_ITEMS))
        }
    }

    suspend fun clearBookmarks() {
        context.pixivBrowserDataStore.edit { it.remove(bookmarksKey) }
    }

    suspend fun clearHistory() {
        context.pixivBrowserDataStore.edit { it.remove(historyKey) }
    }

    private companion object {
        const val MAX_BROWSER_ITEMS = 500
    }
}
