package com.pencil.chuangyin.feature.pixiv.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** OAuth PKCE（S256）—— 桌面端用 java.util.Base64 替代 android.util.Base64。 */
object Pkce {
    private val b64: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun generateVerifier(): String {
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        return b64.encodeToString(random)
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return b64.encodeToString(digest)
    }

    /** 官方 App 内嵌登录页地址。 */
    fun loginUrl(challenge: String): String =
        "https://app-api.pixiv.net/web/v1/login" +
            "?code_challenge_method=S256&client=pixiv-android&code_challenge=$challenge"
}
