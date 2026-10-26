package com.pencil.chuangyin.feature.pixiv.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pixivAccountDataStore by preferencesDataStore(name = "pixiv_account")

/** 已登录账号快照（token + 用户基本信息）。 */
data class PixivAccount(
    val accessToken: String,
    val refreshToken: String,
    val userId: Int,
    val name: String,
    val account: String,
    val avatarUrl: String,
) {
    /** P 站接口要求的 Authorization 头值。 */
    val authorization: String get() = "Bearer $accessToken"
}

/** 账号持久化（DataStore）。替代 Pix-EzViewer 的 Room 用户表，单账号足够。 */
@Singleton
class PixivAccountStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val USER_ID = intPreferencesKey("user_id")
        val NAME = stringPreferencesKey("name")
        val ACCOUNT = stringPreferencesKey("account")
        val AVATAR = stringPreferencesKey("avatar")
    }

    val accountFlow: Flow<PixivAccount?> = context.pixivAccountDataStore.data.map { p ->
        val access = p[Keys.ACCESS]
        val refresh = p[Keys.REFRESH]
        if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
            null
        } else {
            PixivAccount(
                accessToken = access,
                refreshToken = refresh,
                userId = p[Keys.USER_ID] ?: 0,
                name = p[Keys.NAME].orEmpty(),
                account = p[Keys.ACCOUNT].orEmpty(),
                avatarUrl = p[Keys.AVATAR].orEmpty(),
            )
        }
    }

    suspend fun save(account: PixivAccount) {
        context.pixivAccountDataStore.edit { p ->
            p[Keys.ACCESS] = account.accessToken
            p[Keys.REFRESH] = account.refreshToken
            p[Keys.USER_ID] = account.userId
            p[Keys.NAME] = account.name
            p[Keys.ACCOUNT] = account.account
            p[Keys.AVATAR] = account.avatarUrl
        }
    }

    suspend fun clear() {
        context.pixivAccountDataStore.edit { it.clear() }
    }
}
