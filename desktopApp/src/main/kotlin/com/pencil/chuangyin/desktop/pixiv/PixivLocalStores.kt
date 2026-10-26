package com.pencil.chuangyin.desktop.pixiv

import java.util.prefs.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// ── 本地条目模型（与安卓端 PixivLocalStores.kt 逐字段一致，id 用 Long）────────────

@Serializable
data class HistoryEntry(
    val illustId: Long,
    val title: String,
    val userName: String,
    val coverUrl: String,
    val viewedAt: Long,
)

@Serializable
data class BookmarkEntry(
    val illustId: Long,
    val title: String,
    val userName: String,
    val coverUrl: String,
    val bookmarkedAt: Long,
)

@Serializable
data class NovelBookmarkEntry(
    val novelId: Long,
    val title: String,
    val userName: String,
    val coverUrl: String,
    val bookmarkedAt: Long,
)

@Serializable
data class BrowserBookmarkEntry(
    val url: String,
    val title: String,
    val addedAt: Long,
)

@Serializable
data class BrowserHistoryEntry(
    val url: String,
    val title: String,
    val visitedAt: Long,
)

/** 各 store 共用的落盘编码器（桌面端 Preferences 存 JSON 字符串）。 */
private val storeJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

private fun pixivDataPrefs(): Preferences =
    Preferences.userRoot().node("com/pencil/chuangyin/pro/pixiv/data")

/** 浏览历史（最多 [MAX_HISTORY] 条，新的在最前，同一作品去重）。 */
class PixivHistoryStore {

    private val prefs = pixivDataPrefs()

    private val _history = MutableStateFlow(
        decode(prefs.get(KEY, null), ListSerializer(HistoryEntry.serializer())),
    )
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    @Synchronized
    fun add(illust: PixivIllust) {
        val entry = HistoryEntry(
            illustId = illust.id,
            title = illust.title,
            userName = illust.authorName,
            coverUrl = illust.coverUrl,
            viewedAt = System.currentTimeMillis(),
        )
        val current = _history.value.toMutableList()
        current.removeAll { it.illustId == entry.illustId }
        current.add(0, entry)
        commit(current)
    }

    @Synchronized
    fun remove(illustId: Long) {
        val current = _history.value.toMutableList()
        current.removeAll { it.illustId == illustId }
        commit(current)
    }

    @Synchronized
    fun clear() {
        commit(emptyList())
    }

    private fun commit(list: List<HistoryEntry>) {
        val trimmed = list.take(MAX_HISTORY)
        _history.value = trimmed
        runCatching {
            prefs.put(KEY, storeJson.encodeToString(ListSerializer(HistoryEntry.serializer()), trimmed))
            prefs.flush()
        }
    }

    companion object {
        const val MAX_HISTORY = 200
        private const val KEY = "history_json"
    }
}

/** 作品收藏（本地，最多 500 条）。 */
class PixivBookmarkStore {

    private val prefs = pixivDataPrefs()

    private val _bookmarks = MutableStateFlow(
        decode(prefs.get(KEY, null), ListSerializer(BookmarkEntry.serializer())),
    )
    val bookmarks: StateFlow<List<BookmarkEntry>> = _bookmarks.asStateFlow()

    @Synchronized
    fun add(illust: PixivIllust) {
        val entry = BookmarkEntry(
            illustId = illust.id,
            title = illust.title,
            userName = illust.authorName,
            coverUrl = illust.coverUrl,
            bookmarkedAt = System.currentTimeMillis(),
        )
        val current = _bookmarks.value.toMutableList()
        current.removeAll { it.illustId == entry.illustId }
        current.add(0, entry)
        commit(current)
    }

    @Synchronized
    fun remove(illustId: Long) {
        val current = _bookmarks.value.toMutableList()
        current.removeAll { it.illustId == illustId }
        commit(current)
    }

    @Synchronized
    fun clear() {
        commit(emptyList())
    }

    private fun commit(list: List<BookmarkEntry>) {
        val trimmed = list.take(MAX_BOOKMARKS)
        _bookmarks.value = trimmed
        runCatching {
            prefs.put(KEY, storeJson.encodeToString(ListSerializer(BookmarkEntry.serializer()), trimmed))
            prefs.flush()
        }
    }

    companion object {
        const val MAX_BOOKMARKS = 500
        private const val KEY = "bookmarks_json"
    }
}

/** 小说收藏（本地，最多 500 条）。 */
class PixivNovelBookmarkStore {

    private val prefs = pixivDataPrefs()

    private val _bookmarks = MutableStateFlow(
        decode(prefs.get(KEY, null), ListSerializer(NovelBookmarkEntry.serializer())),
    )
    val bookmarks: StateFlow<List<NovelBookmarkEntry>> = _bookmarks.asStateFlow()

    @Synchronized
    fun add(novel: PixivNovel) {
        val entry = NovelBookmarkEntry(
            novelId = novel.id,
            title = novel.title,
            userName = novel.authorName,
            coverUrl = novel.coverUrl,
            bookmarkedAt = System.currentTimeMillis(),
        )
        val current = _bookmarks.value.toMutableList()
        current.removeAll { it.novelId == entry.novelId }
        current.add(0, entry)
        commit(current)
    }

    @Synchronized
    fun remove(novelId: Long) {
        val current = _bookmarks.value.toMutableList()
        current.removeAll { it.novelId == novelId }
        commit(current)
    }

    @Synchronized
    fun clear() {
        commit(emptyList())
    }

    private fun commit(list: List<NovelBookmarkEntry>) {
        val trimmed = list.take(MAX_BOOKMARKS)
        _bookmarks.value = trimmed
        runCatching {
            prefs.put(KEY, storeJson.encodeToString(ListSerializer(NovelBookmarkEntry.serializer()), trimmed))
            prefs.flush()
        }
    }

    companion object {
        const val MAX_BOOKMARKS = 500
        private const val KEY = "novel_bookmarks_json"
    }
}

/** 屏蔽标签（作品不会出现在推荐 / 榜单 / 搜索结果中）。 */
class PixivBlockedTagsStore {

    private val prefs = pixivDataPrefs()

    private val _tags = MutableStateFlow(
        decode(prefs.get(KEY, null), ListSerializer(String.serializer())).toSet(),
    )
    val tags: StateFlow<Set<String>> = _tags.asStateFlow()

    @Synchronized
    fun add(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        commit(_tags.value + trimmed)
    }

    @Synchronized
    fun remove(tag: String) {
        commit(_tags.value - tag)
    }

    private fun commit(tags: Set<String>) {
        _tags.value = tags
        runCatching {
            prefs.put(KEY, storeJson.encodeToString(ListSerializer(String.serializer()), tags.toList()))
            prefs.flush()
        }
    }

    companion object {
        private const val KEY = "blocked_tags"
    }
}

/** 浏览偏好（默认内容模式 + 三个开关），全部以 StateFlow 暴露。 */
class PixivPrefsStore {

    private val prefs = pixivDataPrefs()

    private val _defaultMediaMode = MutableStateFlow(
        mediaTypeOf(prefs.get(KEY_MEDIA_MODE, null) ?: DEFAULT_MEDIA_MODE),
    )
    val defaultMediaMode: StateFlow<PixivMediaType> = _defaultMediaMode.asStateFlow()

    private val _historyEnabled = MutableStateFlow(prefs.getBoolean(KEY_HISTORY, true))
    val historyEnabled: StateFlow<Boolean> = _historyEnabled.asStateFlow()

    private val _hiResImages = MutableStateFlow(prefs.getBoolean(KEY_HI_RES, false))
    val hiResImages: StateFlow<Boolean> = _hiResImages.asStateFlow()

    private val _r18FilterEnabled = MutableStateFlow(prefs.getBoolean(KEY_R18, true))
    val r18FilterEnabled: StateFlow<Boolean> = _r18FilterEnabled.asStateFlow()

    @Synchronized
    fun setDefaultMediaMode(mode: PixivMediaType) {
        _defaultMediaMode.value = mode
        runCatching {
            prefs.put(KEY_MEDIA_MODE, mode.key)
            prefs.flush()
        }
    }

    @Synchronized
    fun setHistoryEnabled(enabled: Boolean) {
        _historyEnabled.value = enabled
        runCatching {
            prefs.putBoolean(KEY_HISTORY, enabled)
            prefs.flush()
        }
    }

    @Synchronized
    fun setHiResImages(enabled: Boolean) {
        _hiResImages.value = enabled
        runCatching {
            prefs.putBoolean(KEY_HI_RES, enabled)
            prefs.flush()
        }
    }

    @Synchronized
    fun setR18FilterEnabled(enabled: Boolean) {
        _r18FilterEnabled.value = enabled
        runCatching {
            prefs.putBoolean(KEY_R18, enabled)
            prefs.flush()
        }
    }

    companion object {
        /** 默认内容模式（与安卓端 DEFAULT_MEDIA_MODE 一致）。 */
        const val DEFAULT_MEDIA_MODE = "illust"

        private const val KEY_MEDIA_MODE = "default_media_mode"
        private const val KEY_HISTORY = "history_enabled"
        private const val KEY_HI_RES = "hi_res_images"
        private const val KEY_R18 = "r18_filter_enabled"

        private fun mediaTypeOf(value: String): PixivMediaType = when (value) {
            "manga" -> PixivMediaType.Manga
            "novel" -> PixivMediaType.Novel
            else -> PixivMediaType.Illust
        }
    }
}

/** 桌面端内嵌浏览器书签/历史暂不落地（浏览器走系统默认浏览器）。 */
class PixivBrowserStore {

    private val prefs = pixivDataPrefs()

    private val _bookmarks = MutableStateFlow(
        decode(prefs.get(KEY_BOOKMARKS, null), ListSerializer(BrowserBookmarkEntry.serializer())),
    )
    val bookmarks: StateFlow<List<BrowserBookmarkEntry>> = _bookmarks.asStateFlow()

    private val _history = MutableStateFlow(
        decode(prefs.get(KEY_HISTORY, null), ListSerializer(BrowserHistoryEntry.serializer())),
    )
    val history: StateFlow<List<BrowserHistoryEntry>> = _history.asStateFlow()

    @Synchronized
    fun addBookmark(url: String, title: String) {
        if (url.isBlank()) return
        val current = _bookmarks.value.toMutableList()
        current.removeAll { it.url == url }
        current.add(0, BrowserBookmarkEntry(url, title, System.currentTimeMillis()))
        val trimmed = current.take(MAX_BROWSER_ITEMS)
        _bookmarks.value = trimmed
        persist(KEY_BOOKMARKS, storeJson.encodeToString(ListSerializer(BrowserBookmarkEntry.serializer()), trimmed))
    }

    @Synchronized
    fun removeBookmark(url: String) {
        val trimmed = _bookmarks.value.filterNot { it.url == url }
        _bookmarks.value = trimmed
        persist(KEY_BOOKMARKS, storeJson.encodeToString(ListSerializer(BrowserBookmarkEntry.serializer()), trimmed))
    }

    @Synchronized
    fun recordHistory(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val current = _history.value.toMutableList()
        current.removeAll { it.url == url }
        current.add(0, BrowserHistoryEntry(url, title, System.currentTimeMillis()))
        val trimmed = current.take(MAX_BROWSER_ITEMS)
        _history.value = trimmed
        persist(KEY_HISTORY, storeJson.encodeToString(ListSerializer(BrowserHistoryEntry.serializer()), trimmed))
    }

    @Synchronized
    fun clearBookmarks() {
        _bookmarks.value = emptyList()
        persist(KEY_BOOKMARKS, "[]")
    }

    @Synchronized
    fun clearHistory() {
        _history.value = emptyList()
        persist(KEY_HISTORY, "[]")
    }

    private fun persist(key: String, value: String) {
        runCatching {
            prefs.put(key, value)
            prefs.flush()
        }
    }

    companion object {
        const val MAX_BROWSER_ITEMS = 500
        private const val KEY_BOOKMARKS = "browser_bookmarks_json"
        private const val KEY_HISTORY = "browser_history_json"
    }
}

/** 媒体类型 → 落盘用 key（与安卓端 MEDIA_* 常量一致）。 */
val PixivMediaType.key: String
    get() = when (this) {
        PixivMediaType.Illust -> "illust"
        PixivMediaType.Manga -> "manga"
        PixivMediaType.Novel -> "novel"
    }

/** 容错读取：缺省或解析失败直接回到空列表。 */
private fun <T : Any> decode(raw: String?, serializer: KSerializer<List<T>>): List<T> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { storeJson.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
}
