package com.pencil.chuangyin.core.proxy.route

import com.pencil.chuangyin.core.proxy.model.ProxyRule
import com.pencil.chuangyin.core.proxy.model.ProxyRuleType
import java.net.InetAddress

/** 一次规则匹配所需的连接上下文。 */
data class RuleMatchContext(
    val host: String,
    /** 目的端口；未知时传 null（DST-PORT 规则会跳过）。 */
    val port: Int? = null,
    /** tcp / udp；由传输层决定，Http 请求固定 tcp。 */
    val network: String? = "tcp",
    /** host 本身是否是内网 / 回环地址（GEOIP,LAN 用）。 */
    val isPrivateAddress: Boolean = false,
)

/**
 * Clash 规则匹配器。
 *
 * 只做「第一条命中」判定，不做策略解析——策略解析在 [ProxyRouter] 里完成。
 * IP 规则只对 host 本身是字面量 IP 时生效，不对域名做 DNS 解析：
 * 在进程内 ProxySelector 中做同步 DNS 会阻塞路由线程，且会绕过代理泄露 DNS。
 */
class RuleMatcher {

    fun firstMatch(rules: List<ProxyRule>, context: RuleMatchContext): ProxyRule? =
        rules.firstOrNull { matches(it, context) }

    fun matches(rule: ProxyRule, context: RuleMatchContext): Boolean {
        if (rule.unsupportedReason != null) return false
        val host = normalizeHost(context.host)
        return when (rule.type) {
            ProxyRuleType.Domain -> host == normalizeHost(rule.value)
            ProxyRuleType.DomainSuffix -> {
                val suffix = normalizeHost(rule.value)
                host == suffix || host.endsWith(".$suffix")
            }
            ProxyRuleType.DomainKeyword -> host.contains(rule.value.trim().lowercase())
            ProxyRuleType.DomainRegex -> safeRegex(rule.value)?.matches(host) ?: false
            ProxyRuleType.DomainWildcard -> wildcardMatches(rule.value, host)
            ProxyRuleType.IpCidr, ProxyRuleType.IpCidr6 ->
                isLiteralIp(host) && cidrMatches(rule.value, host)
            ProxyRuleType.SrcIpCidr -> false
            ProxyRuleType.GeoIp -> geoIpMatches(rule.value, host, context.isPrivateAddress)
            ProxyRuleType.GeoSite -> false
            ProxyRuleType.ProcessName -> false
            ProxyRuleType.DstPort -> context.port != null && rule.value.trim().toIntOrNull() == context.port
            ProxyRuleType.Network -> context.network?.equals(rule.value.trim(), ignoreCase = true) == true
            ProxyRuleType.Match -> true
            ProxyRuleType.Inline, ProxyRuleType.RuleSet, ProxyRuleType.Script,
            ProxyRuleType.Unsupported,
            -> false
        }
    }

    private fun normalizeHost(value: String): String = value.trim().lowercase().trimEnd('.')

    private fun safeRegex(pattern: String): Regex? =
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()

    /** `*` → `.*`，`?` → `.`，其余字符转义，整串全匹配。 */
    private fun wildcardMatches(pattern: String, host: String): Boolean {
        val regex = buildString {
            append('^')
            pattern.trim().forEach { c ->
                when (c) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(c.toString()))
                }
            }
            append('$')
        }
        return runCatching { Regex(regex, RegexOption.IGNORE_CASE).matches(host) }.getOrDefault(false)
    }

    /**
     * GEOIP 规则：仅支持 LAN/PRIVATE 语义（不内嵌 GeoIP 数据库）。
     * 其它值（CN、JP…）按不命中处理，配置解析阶段已经给出 warning。
     */
    private fun geoIpMatches(value: String, host: String, isPrivate: Boolean): Boolean {
        val token = value.trim().uppercase()
        if (token != "LAN" && token != "PRIVATE") return false
        return isPrivate || isPrivateLiteral(host)
    }

    private fun isLiteralIp(host: String): Boolean = parseIp(host) != null

    private fun isPrivateLiteral(host: String): Boolean {
        val address = parseIp(host) ?: return false
        return isPrivateAddress(address)
    }

    private fun parseIp(host: String): InetAddress? {
        val looksLikeIpv4 = IPV4_REGEX.matches(host)
        val looksLikeIpv6 = host.contains(':') && IPV6_CHARS_ONLY.matches(host)
        if (!looksLikeIpv4 && !looksLikeIpv6) return null
        return runCatching { InetAddress.getByName(host) }.getOrNull()
    }

    private fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isLinkLocalAddress || address.isSiteLocalAddress || address.isLoopbackAddress) return true
        val bytes = address.address
        if (bytes.size == 16) {
            // fc00::/7（唯一本地地址）
            if ((bytes[0].toInt() and 0xFE) == 0xFC) return true
            // fe80::/10（链路本地）
            if ((bytes[0].toInt() and 0xFF) == 0xFE && (bytes[1].toInt() and 0xC0) == 0x80) return true
        }
        return false
    }

    /** CIDR 匹配，支持 IPv4 / IPv6；网络段写错时按不命中处理。 */
    private fun cidrMatches(cidr: String, host: String): Boolean {
        val slash = cidr.lastIndexOf('/')
        val networkPart = if (slash < 0) cidr else cidr.substring(0, slash)
        val prefix = if (slash < 0) null else cidr.substring(slash + 1).trim().toIntOrNull()
        val hostAddress = parseIp(host) ?: return false
        val networkAddress = runCatching { InetAddress.getByName(networkPart.trim()) }.getOrNull() ?: return false
        val hostBytes = hostAddress.address
        val networkBytes = networkAddress.address
        if (hostBytes.size != networkBytes.size) return false
        val totalBits = hostBytes.size * 8
        val bits = (prefix ?: totalBits).coerceIn(0, totalBits)
        if (bits == 0) return true
        val fullBytes = bits / 8
        val remainder = bits % 8
        for (i in 0 until fullBytes) {
            if (hostBytes[i] != networkBytes[i]) return false
        }
        if (remainder == 0) return true
        val mask = (0xFF shl (8 - remainder)) and 0xFF
        return (hostBytes[fullBytes].toInt() and mask) == (networkBytes[fullBytes].toInt() and mask)
    }

    private companion object {
        val IPV4_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        val IPV6_CHARS_ONLY = Regex("""^[0-9a-fA-F:.%]+$""")
    }
}
