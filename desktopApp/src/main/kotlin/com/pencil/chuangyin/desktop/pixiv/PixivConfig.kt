package com.pencil.chuangyin.desktop.pixiv

/**
 * P 站协议常量 —— 与安卓端 core/network（PixivConfig）逐字段一致。
 * 任何端口值变更必须两端同步修改。
 */
object PixivConfig {
    const val API_BASE_URL = "https://app-api.pixiv.net/"
    const val OAUTH_BASE_URL = "https://oauth.secure.pixiv.net/"
    const val OAUTH_TOKEN_URL = OAUTH_BASE_URL + "auth/token"
    const val CLIENT_ID = "MOBrBDS8blbauoSck0ZfDbtuzpyT"
    const val CLIENT_SECRET = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj"
    const val REDIRECT_URI = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback"
    const val APP_VER = "7.13.3"
    const val HASH_SALT = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"
    const val IMAGE_REFERER = "https://app-api.pixiv.net/"
    const val USER_AGENT = "PixivAndroidApp/$APP_VER (Android 13; PencilDesktop)"

    fun loginUrl(codeChallenge: String): String =
        "https://app-api.pixiv.net/web/v1/login" +
            "?code_challenge_method=S256&client=pixiv-android&code_challenge=$codeChallenge"
}

/** PKCE（S256）工具，与安卓端 Pkce.kt 行为一致。 */
object Pkce {
    private const val VERIFIER_LENGTH = 32

    fun newVerifier(): String {
        val bytes = ByteArray(VERIFIER_LENGTH)
        java.security.SecureRandom().nextBytes(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun challenge(verifier: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}

/** 分区定义（与安卓端 BrowseSections 一致）。 */
object BrowseSections {
    const val RECOMMEND = "recommend"

    val illustSections = listOf(
        RECOMMEND to "推荐",
        "day" to "日榜",
        "week" to "周榜",
        "month" to "月榜",
        "week_original" to "周榜原创",
        "week_rookie" to "新人榜",
        "day_male" to "受男性欢迎",
        "day_female" to "受女性欢迎",
    )

    val mangaSections = listOf(
        RECOMMEND to "推荐",
        "day_manga" to "漫画日榜",
    )

    /** 小说只有榜单（安卓端 PixivApi 无小说推荐接口，也没有 month 模式）。 */
    val novelSections = listOf(
        "day" to "日榜",
        "week" to "周榜",
        "day_male" to "受男性欢迎",
        "day_female" to "受女性欢迎",
    )

    fun sectionsOf(media: PixivMediaType): List<Pair<String, String>> = when (media) {
        PixivMediaType.Illust -> illustSections
        PixivMediaType.Manga -> mangaSections
        PixivMediaType.Novel -> novelSections
    }
}

/** 媒体类型分段（插画 / 漫画 / 小说）。 */
enum class PixivMediaType(val label: String) {
    Illust("插画"),
    Manga("漫画"),
    Novel("小说"),
}
