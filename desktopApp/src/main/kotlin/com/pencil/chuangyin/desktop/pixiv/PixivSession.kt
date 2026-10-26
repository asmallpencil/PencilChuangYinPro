package com.pencil.chuangyin.desktop.pixiv

import java.util.prefs.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * P 站登录态 —— 对齐安卓端 SessionManager：
 * 内存里以 StateFlow 暴露账号，落盘（桌面用 Preferences 代替 DataStore）保证重启后免登录。
 *
 * 关键点（用户强调）：登录态只在「P 站 tab」内被消费，应用启动不依赖它，
 * 未登录时 P 站 tab 展示 NotLoggedIn 登录墙，其余 tab 完全可用。
 */
class PixivSession {

    private val prefs: Preferences by lazy {
        Preferences.userRoot().node("com/pencil/chuangyin/pro/pixiv")
    }

    private val _account = MutableStateFlow(restore())
    val account: StateFlow<PixivAccount?> = _account.asStateFlow()

    val isLoggedIn: Boolean get() = _account.value != null

    val accessToken: String? get() = _account.value?.accessToken

    private val refreshMutex = Mutex()
    private var lastRefreshAt = 0L

    fun save(account: PixivAccount) {
        _account.value = account
        runCatching {
            prefs.put("access_token", account.accessToken)
            prefs.put("refresh_token", account.refreshToken)
            prefs.put("user_id", account.userId)
            prefs.put("name", account.name)
            prefs.put("account", account.account)
            prefs.put("avatar", account.avatarUrl ?: "")
            prefs.flush()
        }
    }

    fun clear() {
        _account.value = null
        runCatching {
            prefs.remove("access_token")
            prefs.remove("refresh_token")
            prefs.remove("user_id")
            prefs.remove("name")
            prefs.remove("account")
            prefs.remove("avatar")
            prefs.flush()
        }
    }

    /** 用 refresh_token 换新 access_token；并发调用单飞，1 秒内重复调用直接返回当前 token。 */
    suspend fun refresh(api: PixivClient): String? = refreshMutex.withLock {
        val now = System.currentTimeMillis()
        if (api === lastApi && now - lastRefreshAt < 1000L) {
            return@withLock _account.value?.accessToken
        }
        val current = _account.value ?: return@withLock null
        val renewed = api.refreshToken(current.refreshToken)
        if (renewed == null) {
            clear()
            return@withLock null
        }
        lastApi = api
        lastRefreshAt = now
        save(renewed)
        renewed.accessToken
    }

    private var lastApi: PixivClient? = null

    private fun restore(): PixivAccount? {
        return runCatching {
            val access = prefs.get("access_token", null)
            val refresh = prefs.get("refresh_token", null)
            if (access.isNullOrBlank() || refresh.isNullOrBlank()) return null
            PixivAccount(
                accessToken = access,
                refreshToken = refresh,
                userId = prefs.get("user_id", "").orEmpty(),
                name = prefs.get("name", "").orEmpty(),
                account = prefs.get("account", "").orEmpty(),
                avatarUrl = prefs.get("avatar", "").orEmpty().ifBlank { null },
            )
        }.getOrNull()
    }
}

/** 桌面端单例容器（替代 Hilt）。 */
object PixivGraph {
    val session: PixivSession by lazy { PixivSession() }
    val client: PixivClient by lazy { PixivClient(session) }

    // ── 本地数据层（与安卓端 5 个 DataStore 一一对应）────────────────────────────
    val historyStore: PixivHistoryStore by lazy { PixivHistoryStore() }
    val bookmarkStore: PixivBookmarkStore by lazy { PixivBookmarkStore() }
    val novelBookmarkStore: PixivNovelBookmarkStore by lazy { PixivNovelBookmarkStore() }
    val blockedTagsStore: PixivBlockedTagsStore by lazy { PixivBlockedTagsStore() }
    val prefsStore: PixivPrefsStore by lazy { PixivPrefsStore() }
    val browserStore: PixivBrowserStore by lazy { PixivBrowserStore() }
    val downloadManager: PixivDownloadManager by lazy { PixivDownloadManager() }

    init {
        PixivPaths.ensureBaseDirs()
    }
}
