package com.pencil.chuangyin.feature.pixiv.core.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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

/** 磁盘存储结构（显式声明，避免 data class 内部字段改名导致旧文件失效）。 */
@Serializable
private data class StoredAccount(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: Int = 0,
    val name: String = "",
    val account: String = "",
    val avatarUrl: String = "",
)

/**
 * 账号持久化：桌面端以 JSON 文件替代 Android 的 DataStore。
 * 位置：%APPDATA%\PencilChuangYinPro\pixiv_account.json（回退到 user.home）。
 */
@Singleton
class PixivAccountStore @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val file: File by lazy {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: File(System.getProperty("user.home"), ".config")
        File(File(base, "PencilChuangYinPro"), "pixiv_account.json")
    }

    private val _accountFlow = MutableStateFlow(loadFromDisk())

    val accountFlow: Flow<PixivAccount?> = _accountFlow

    fun currentOrNull(): PixivAccount? = _accountFlow.value

    suspend fun save(account: PixivAccount) {
        _accountFlow.value = account
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(
                json.encodeToString(
                    StoredAccount.serializer(),
                    StoredAccount(
                        accessToken = account.accessToken,
                        refreshToken = account.refreshToken,
                        userId = account.userId,
                        name = account.name,
                        account = account.account,
                        avatarUrl = account.avatarUrl,
                    ),
                ),
            )
        }
    }

    suspend fun clear() {
        _accountFlow.value = null
        runCatching { if (file.exists()) file.delete() }
    }

    private fun loadFromDisk(): PixivAccount? = runCatching {
        if (!file.exists()) return null
        val stored = json.decodeFromString(StoredAccount.serializer(), file.readText())
        if (stored.accessToken.isBlank() || stored.refreshToken.isBlank()) {
            null
        } else {
            PixivAccount(
                accessToken = stored.accessToken,
                refreshToken = stored.refreshToken,
                userId = stored.userId,
                name = stored.name,
                account = stored.account,
                avatarUrl = stored.avatarUrl,
            )
        }
    }.getOrNull()
}

/** 供 UI 直接观察登录态。 */
fun PixivAccountStore.stateFlow(): StateFlow<PixivAccount?> = accountFlow as StateFlow<PixivAccount?>
