package com.pencil.chuangyin.feature.pixiv.core.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** OAuth PKCE（S256），移植自 Pix-EzViewer Pkce。 */
object Pkce {
    fun generateVerifier(): String {
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        return Base64.encodeToString(
            random,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    /** 官方 App 内嵌登录页地址。 */
    fun loginUrl(challenge: String): String =
        "https://app-api.pixiv.net/web/v1/login" +
            "?code_challenge_method=S256&client=pixiv-android&code_challenge=$challenge"
}
