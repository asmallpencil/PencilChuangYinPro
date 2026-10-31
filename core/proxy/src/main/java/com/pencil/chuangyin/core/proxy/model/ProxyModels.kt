package com.pencil.chuangyin.core.proxy.model

import kotlinx.serialization.Serializable

/** 代理生效范围：默认仅 P 站，可切换为应用全局。 */
@Serializable
enum class ProxyScope {
    /** 只有 P 站相关域名走代理，其余请求直连。 */
    PixivOnly,

    /** 应用内所有 HTTP(S) 请求都按出口策略走代理（仍不影响系统与其他应用）。 */
    Global,
    ;

    companion object {
        fun fromStored(raw: String?): ProxyScope =
            entries.firstOrNull { it.name == raw } ?: PixivOnly
    }
}

/** 出站模式，与 Clash 的 mode 语义一致。 */
@Serializable
enum class ProxyMode {
    /** 按配置里的 rules 逐条匹配，未命中则直连。 */
    Rule,

    /** 忽略 rules，所有流量走所选出口。 */
    Global,

    /** 忽略 rules 与出口，全部直连（相当于临时停用代理）。 */
    Direct,
    ;

    companion object {
        fun fromStored(raw: String?): ProxyMode =
            entries.firstOrNull { it.name == raw } ?: Rule
    }
}

/** 配置来源，仅用于展示与订阅刷新。 */
@Serializable
enum class ProxySourceType {
    Manual,
    Link,
    File,
    QrCode,
    ;

    companion object {
        fun fromStored(raw: String?): ProxySourceType =
            entries.firstOrNull { it.name == raw } ?: Manual
    }
}

/**
 * Clash 配置中的节点协议。
 *
 * [Http] / [Socks5] 可直接用 [java.net.Proxy] 承载；[Shadowsocks] / [Trojan]
 * 由进程内本地桥接 + 自研协议栈承载（同样只影响本应用）。其余协议仍会完整解析并展示，
 * 但标记为「不支持」，选中时按不可用处理（不会静默直连）。
 */
enum class ProxyProtocol(val token: String) {
    Http("http"),
    Https("https"),
    Socks5("socks5"),
    Shadowsocks("ss"),
    Ssr("ssr"),
    Vmess("vmess"),
    Vless("vless"),
    Trojan("trojan"),
    Hysteria("hysteria"),
    Hysteria2("hysteria2"),
    Tuic("tuic"),
    AnyTls("anytls"),
    WireGuard("wireguard"),
    Snell("snell"),
    Ssh("ssh"),
    Unknown("unknown"),
    ;

    /**
     * 当前可真正承载流量的协议集合。
     *
     * HTTP / SOCKS5 直接用系统 [java.net.Proxy]；其余协议由内置 sing-box 内核承载
     * （每节点一个本地 SOCKS 入口）。SSR / Snell / WireGuard 暂不支持：
     * 前两种上游已淘汰，WireGuard 在 sing-box 中属于 endpoint 而非 outbound，无法按节点直连。
     */
    val isSupported: Boolean
        get() = this == Http || this == Socks5 || handledByCore

    /** 是否交给内置内核（sing-box）建立隧道。 */
    val handledByCore: Boolean
        get() = when (this) {
            Shadowsocks, Vmess, Vless, Trojan, Hysteria, Hysteria2, Tuic, AnyTls, Ssh -> true
            else -> false
        }

    /**
     * 是否属于手写协议栈（SS AEAD / Trojan）——内核不可用时（例如 x86 模拟器）
     * 仍能靠本地 SOCKS5 桥按老路径连通，作为降级回退。
     */
    val requiresBridge: Boolean
        get() = this == Shadowsocks || this == Trojan

    companion object {
        fun fromToken(raw: String?): ProxyProtocol {
            val token = raw?.trim()?.lowercase().orEmpty()
            return when (token) {
                // 部分订阅把 SOCKS 写成 socks / socks4，统按 SOCKS5 处理。
                "socks", "socks5" -> Socks5
                "hy2" -> Hysteria2
                else -> entries.firstOrNull { it.token == token } ?: Unknown
            }
        }
    }
}

/** 一个 Clash 节点。字段按订阅里常见的键名解析，缺失时留空。 */
data class ProxyNode(
    val name: String,
    val protocol: ProxyProtocol,
    val server: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val cipher: String? = null,
    val tls: Boolean = false,
    val sni: String? = null,
    val udp: Boolean = false,
    val skipCertVerify: Boolean = false,
    /**
     * 节点的完整原始字段（YAML 扁平化，例如 `ws-opts.path` / `reality-opts.public-key`）。
     * 交给内置内核生成配置时使用，避免为每种协议的每个参数都定义专用字段。
     */
    val extra: Map<String, String> = emptyMap(),
) {
    /** 是否可用：协议被支持且地址/端口合法。 */
    val isSupported: Boolean
        get() = protocol.isSupported && server.isNotBlank() && port in 1..65535

    val address: String
        get() = "$server:$port"
}

/** 代理组类型。 */
enum class ProxyGroupType {
    Select,
    UrlTest,
    Fallback,
    LoadBalance,
    Relay,
    Unknown,
    ;

    companion object {
        fun fromToken(raw: String?): ProxyGroupType = when (raw?.trim()?.lowercase()) {
            "select" -> Select
            "url-test", "urltest" -> UrlTest
            "fallback" -> Fallback
            "load-balance", "loadbalance" -> LoadBalance
            "relay" -> Relay
            else -> Unknown
        }
    }
}

/**
 * 代理组。[members] 已经过 `include-all` / `filter` 展开，元素可以是节点名、
 * 其他组名，或策略名 `DIRECT` / `REJECT`。
 */
data class ProxyGroup(
    val name: String,
    val type: ProxyGroupType,
    val members: List<String>,
    val testUrl: String? = null,
    val intervalSeconds: Int? = null,
    val toleranceMs: Int? = null,
)

/** 规则类型。 */
enum class ProxyRuleType(val token: String) {
    Domain("DOMAIN"),
    DomainSuffix("DOMAIN-SUFFIX"),
    DomainKeyword("DOMAIN-KEYWORD"),
    DomainRegex("DOMAIN-REGEX"),
    DomainWildcard("DOMAIN-WILDCARD"),
    IpCidr("IP-CIDR"),
    IpCidr6("IP-CIDR6"),
    SrcIpCidr("SRC-IP-CIDR"),
    GeoIp("GEOIP"),
    GeoSite("GEOSITE"),
    ProcessName("PROCESS-NAME"),
    DstPort("DST-PORT"),
    Network("NETWORK"),
    Inline("INLINE"),
    RuleSet("RULE-SET"),
    Script("SCRIPT"),
    Match("MATCH"),
    Unsupported(""),
    ;

    companion object {
        fun fromToken(raw: String): ProxyRuleType {
            val token = raw.trim().uppercase()
            if (token == "FINAL") return Match
            return entries.firstOrNull { it.token == token } ?: Unsupported
        }
    }
}

/**
 * 一条路由规则。
 *
 * [unsupportedReason] 非空表示该规则被解析出来但当前无法求值（例如 GEOSITE / RULE-SET），
 * 需要跳过而不是误判。跳过情况会汇总到 [ClashConfig.warnings]。
 */
data class ProxyRule(
    val type: ProxyRuleType,
    val value: String,
    val policy: String,
    val noResolve: Boolean,
    val raw: String,
    val unsupportedReason: String? = null,
)

/** 解析完成的 Clash 配置。 */
data class ClashConfig(
    val nodes: List<ProxyNode> = emptyList(),
    val groups: List<ProxyGroup> = emptyList(),
    val rules: List<ProxyRule> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    private val nodesByName: Map<String, ProxyNode> by lazy { nodes.associateBy { it.name } }
    private val groupsByName: Map<String, ProxyGroup> by lazy { groups.associateBy { it.name } }

    fun node(name: String): ProxyNode? = nodesByName[name]

    fun group(name: String): ProxyGroup? = groupsByName[name]

    /** 仪表盘默认展示的组：优先 select 组（订阅的主选择组通常就是它）。 */
    val primaryGroup: ProxyGroup?
        get() = groups.firstOrNull { it.type == ProxyGroupType.Select } ?: groups.firstOrNull()

    /** 所有可被选为出口的候选策略名（组 + 节点 + 内置策略）。 */
    fun policyCandidates(): List<String> {
        val result = LinkedHashSet<String>()
        result.add(POLICY_DIRECT)
        groups.forEach { result.add(it.name) }
        nodes.forEach { result.add(it.name) }
        return result.toList()
    }

    companion object {
        const val POLICY_DIRECT = "DIRECT"
        const val POLICY_REJECT = "REJECT"
    }
}

/** 配置条目：原始文本 + 解析结果 + 元信息。 */
data class ProxyConfigEntry(
    val id: String,
    val name: String,
    val source: ProxySourceType,
    val rawText: String,
    /** 订阅链接；仅 [ProxySourceType.Link] 有值，用于「刷新订阅」。 */
    val subscriptionUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val config: ClashConfig,
) {
    val nodeCount: Int get() = config.nodes.size
    val groupCount: Int get() = config.groups.size
    val ruleCount: Int get() = config.rules.count { it.unsupportedReason == null }
    val warningCount: Int get() = config.warnings.size
}

/** 导入结果：失败时携带可展示的原因，成功时返回新条目。 */
sealed interface ProxyImportResult {
    data class Success(val entry: ProxyConfigEntry) : ProxyImportResult
    data class Failure(val message: String, val details: List<String> = emptyList()) : ProxyImportResult
}
