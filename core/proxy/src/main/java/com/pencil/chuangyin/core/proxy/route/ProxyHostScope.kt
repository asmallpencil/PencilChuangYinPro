package com.pencil.chuangyin.core.proxy.route

/** 域名归属判断：P 站作用域与内网/回环判定。 */
object ProxyHostScope {

    /** P 站生态域名后缀（含图片 CDN、FANBOX、BOOTH 与 P 站帮助中心）。 */
    private val PIXIV_SUFFIXES = listOf(
        "pixiv.net",
        "pximg.net",
        "pixiv.org",
        "pixiv.co.jp",
        "pixiv.cc",
        "pixivision.net",
        "pixivision.com",
        "fanbox.cc",
        "booth.pm",
        "pixiv.help",
        "pixiv.re",
    )

    fun isPixivHost(host: String): Boolean {
        val normalized = host.trim().lowercase().trimEnd('.')
        if (normalized.isEmpty()) return false
        return PIXIV_SUFFIXES.any { normalized == it || normalized.endsWith(".$it") }
    }

    fun isLoopbackHost(host: String): Boolean {
        val normalized = host.trim().lowercase().trimEnd('.')
        return normalized == "localhost" ||
            normalized == "::1" ||
            normalized == "[::1]" ||
            normalized.startsWith("127.")
    }

    /** 内网 / 链路本地 / 回环地址（供 GEOIP,LAN 与「不代理内网」共用）。 */
    fun isPrivateHost(host: String): Boolean {
        val normalized = host.trim().lowercase().removeSurrounding("[", "]").trimEnd('.')
        if (isLoopbackHost(normalized)) return true
        val parts = normalized.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        return when {
            octets[0] == 10 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 169 && octets[1] == 254 -> true
            else -> false
        }
    }
}
