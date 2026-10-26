package com.pencil.chuangyin.feature.pixiv.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.pencil.chuangyin.feature.pixiv.core.network.PixivOAuthApi
import com.pencil.chuangyin.feature.pixiv.data.dto.OAuthTokenResponseDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 登录会话的内存事实来源：拦截器同步读 token，UI 订阅 [state]。
 * 启动时从 DataStore 阻塞加载一次（数据极小），随后与持久层双向同步。
 */
@Singleton
class SessionManager @Inject constructor(
    private val accountStore: PixivAccountStore,
    private val oAuthApi: PixivOAuthApi,
) {
    private val refreshMutex = Mutex()

    @Volatile
    private var lastRefreshMs: Long = 0L

    private val _state = MutableStateFlow<PixivAccount?>(null)
    val state: StateFlow<PixivAccount?> = _state

    init {
        // 让 OkHttp 拦截器在首个请求即可同步取到已保存 token。
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            _state.value = accountStore.accountFlow.first()
        }
    }

    fun current(): PixivAccount? = _state.value

    fun isLoggedIn(): Boolean = _state.value != null

    suspend fun setAccount(account: PixivAccount) {
        accountStore.save(account)
        _state.value = account
    }

    suspend fun clear() {
        accountStore.clear()
        _state.value = null
    }

    /**
     * 用 refresh_token 静默换新 access_token（单飞互斥 + 1s 内去重）。
     * 失败抛出异常，由调用方决定回到登录页。
     */
    suspend fun refresh(): String = refreshMutex.withLock {
        val old = _state.value ?: error("未登录，无法刷新 token")
        if (System.currentTimeMillis() - lastRefreshMs < 1000L) {
            return@withLock old.accessToken
        }
        val response = oAuthApi.postRefreshAuthToken(refreshToken = old.refreshToken)
        val updated = response.toAccount(fallbackRefreshToken = old.refreshToken)
        accountStore.save(updated)
        _state.value = updated
        lastRefreshMs = System.currentTimeMillis()
        updated.accessToken
    }
}

/** token 响应 → 本地账号；刷新轮换时以响应中的 refresh_token 为准，缺失再回退旧值。 */
fun OAuthTokenResponseDto.toAccount(fallbackRefreshToken: String): PixivAccount = PixivAccount(
    accessToken = accessToken,
    refreshToken = refreshToken.ifBlank { fallbackRefreshToken },
    userId = user.id,
    name = user.name,
    account = user.account,
    avatarUrl = user.profileImages?.px170.orEmpty(),
)
