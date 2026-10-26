package com.pencil.chuangyin.feature.pixiv.core.auth

import com.pencil.chuangyin.feature.pixiv.core.network.PixivOAuthApi
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 登录态领域入口：授权码换 token、当前账号订阅、登出。 */
@Singleton
class PixivAuthRepository @Inject constructor(
    private val session: SessionManager,
    private val oAuthApi: PixivOAuthApi,
) {

    val account: StateFlow<PixivAccount?> = session.state

    fun isLoggedIn(): Boolean = session.isLoggedIn()

    /** WebView 拦截到 callback code 后用 PKCE verifier 换取并持久化账号。 */
    suspend fun loginWithCode(code: String, codeVerifier: String) {
        val response = oAuthApi.postAuthToken(code = code, codeVerifier = codeVerifier)
        session.setAccount(response.toAccount(fallbackRefreshToken = ""))
    }

    suspend fun logout() = session.clear()
}
