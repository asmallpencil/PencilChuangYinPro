package com.pencil.chuangyin.feature.pixiv.core.network

/**
 * P 站官方 Android App 接口常量。
 * 桌面端无法读取 android.os.Build，这里用固定的官方客户端标识（接口只校验格式与授权头）。
 */
object PixivConfig {
    const val API_BASE_URL = "https://app-api.pixiv.net/"
    const val OAUTH_BASE_URL = "https://oauth.secure.pixiv.net/"

    const val CLIENT_ID = "MOBrBDS8blbauoSck0ZfDbtuzpyT"
    const val CLIENT_SECRET = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj"
    const val REDIRECT_URI = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback"

    private const val APP_OS = "Android"
    private const val APP_OS_VERSION = "13"
    private const val DEVICE_MODEL = "SM-G991B"
    private const val APP_VER = "7.13.3"

    /** 与官方客户端一致的 UA。 */
    val USER_AGENT: String =
        "PixivAndroidApp/$APP_VER ($APP_OS $APP_OS_VERSION; $DEVICE_MODEL)"

    /** X-Client-Hash 计算盐（官方约定）。 */
    const val HASH_SALT =
        "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"

    /** 图片防盗链：访问 i.pximg.net 必须携带。 */
    const val IMAGE_REFERER = "https://app-api.pixiv.net/"
    const val WEB_REFERER = "https://www.pixiv.net/"

    const val APP_OS_HEADER = APP_OS
    const val APP_OS_VERSION_HEADER = APP_OS_VERSION
    const val APP_VERSION_HEADER = APP_VER
}
