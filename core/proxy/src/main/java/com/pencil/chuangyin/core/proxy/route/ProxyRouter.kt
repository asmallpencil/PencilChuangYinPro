package com.pencil.chuangyin.core.proxy.route

import com.pencil.chuangyin.core.proxy.model.ClashConfig
import com.pencil.chuangyin.core.proxy.model.ProxyGroup
import com.pencil.chuangyin.core.proxy.model.ProxyGroupType
import com.pencil.chuangyin.core.proxy.model.ProxyMode
import com.pencil.chuangyin.core.proxy.model.ProxyNode
import com.pencil.chuangyin.core.proxy.model.ProxyPreferences
import com.pencil.chuangyin.core.proxy.model.ProxyRule
import com.pencil.chuangyin.core.proxy.model.ProxyScope

/** 一次请求最终的出口。 */
sealed interface Outbound {

    data object Direct : Outbound

    /** 主动拒绝（REJECT 规则 / 出口不可用时失败关闭，避免悄悄直连泄露真实地址）。 */
    data object Reject : Outbound

    data class Node(val node: ProxyNode) : Outbound

    /** 策略存在但无法使用（节点不支持、组为空、名称找不到）。 */
    data class Unavailable(val policy: String, val reason: String) : Outbound
}

/**
 * 路由结果。
 *
 * @param policy 实际生效的策略名（DIRECT / REJECT / 组名 / 节点名）。
 * @param rule 命中的规则，直连或全局模式为 null。
 * @param groupName 若经过代理组，记录最外层组名（用于界面展示与日志）。
 * @param detail 人类可读的原因，直接进连接日志。
 */
data class RouteDecision(
    val outbound: Outbound,
    val policy: String,
    val rule: ProxyRule? = null,
    val groupName: String? = null,
    val detail: String = "",
)

/**
 * 出站路由：把「域名 + 偏好 + 配置」解析成具体出口。
 *
 * 规则优先级与 Clash 一致：自上而下第一条命中即止；未命中即直连（MATCH 规则可兜底）。
 */
class ProxyRouter(private val matcher: RuleMatcher = RuleMatcher()) {

    fun resolve(
        preferences: ProxyPreferences,
        config: ClashConfig?,
        host: String,
        port: Int? = null,
        latencyOf: (String) -> Long? = { null },
    ): RouteDecision {
        val normalizedHost = host.trim().lowercase().trimEnd('.')
        if (!preferences.enabled) {
            return RouteDecision(Outbound.Direct, ClashConfig.POLICY_DIRECT, detail = "代理已关闭")
        }
        if (preferences.scope == ProxyScope.PixivOnly &&
            !ProxyHostScope.isPixivHost(normalizedHost)
        ) {
            return RouteDecision(
                Outbound.Direct,
                ClashConfig.POLICY_DIRECT,
                detail = "仅代理 P 站：$normalizedHost 直连",
            )
        }
        if (ProxyHostScope.isPrivateHost(normalizedHost) && !preferences.proxyPrivateAddresses) {
            return RouteDecision(
                Outbound.Direct,
                ClashConfig.POLICY_DIRECT,
                detail = "内网 / 回环地址直连：$normalizedHost",
            )
        }

        return when (preferences.mode) {
            ProxyMode.Direct ->
                RouteDecision(Outbound.Direct, ClashConfig.POLICY_DIRECT, detail = "直连模式")
            ProxyMode.Global -> {
                val policy = preferences.globalSelection?.takeIf { it.isNotBlank() }
                    ?: config?.primaryGroup?.name
                    ?: config?.nodes?.firstOrNull()?.name
                if (policy.isNullOrBlank()) {
                    RouteDecision(
                        Outbound.Unavailable(policy = "", reason = "尚未导入可用的代理配置"),
                        policy = "",
                        detail = "全局模式缺少出口配置",
                    )
                } else {
                    val decision = resolvePolicy(policy, config, preferences, latencyOf, mutableSetOf())
                    decision.copy(detail = "全局模式 → ${decision.policy}")
                }
            }
            ProxyMode.Rule -> {
                if (config == null) {
                    return RouteDecision(
                        Outbound.Unavailable(policy = "", reason = "尚未导入可用的代理配置"),
                        policy = "",
                        detail = "规则模式缺少配置",
                    )
                }
                val context = RuleMatchContext(
                    host = normalizedHost,
                    port = port,
                    network = "tcp",
                    isPrivateAddress = ProxyHostScope.isPrivateHost(normalizedHost),
                )
                val rule = matcher.firstMatch(config.rules, context)
                    ?: return RouteDecision(
                        Outbound.Direct,
                        ClashConfig.POLICY_DIRECT,
                        detail = "无规则命中：$normalizedHost 直连",
                    )
                when (rule.policy.uppercase()) {
                    ClashConfig.POLICY_DIRECT ->
                        RouteDecision(Outbound.Direct, ClashConfig.POLICY_DIRECT, rule, detail = "规则 ${rule.raw} → DIRECT")
                    ClashConfig.POLICY_REJECT, "REJECT-DROP" -> {
                        if (!preferences.applyRejectRules) {
                            RouteDecision(Outbound.Direct, ClashConfig.POLICY_DIRECT, rule,
                                detail = "规则 ${rule.raw} → REJECT（已关闭拒绝规则，直连）")
                        } else {
                            RouteDecision(Outbound.Reject, ClashConfig.POLICY_REJECT, rule,
                                detail = "规则 ${rule.raw} → REJECT")
                        }
                    }
                    else -> resolvePolicy(rule.policy, config, preferences, latencyOf, mutableSetOf())
                        .copy(rule = rule, detail = "规则 ${rule.raw} → ${rule.policy}")
                }
            }
        }
    }

    private fun resolvePolicy(
        policy: String,
        config: ClashConfig?,
        preferences: ProxyPreferences,
        latencyOf: (String) -> Long?,
        visited: MutableSet<String>,
    ): RouteDecision {
        val name = policy.trim()
        if (name.equals(ClashConfig.POLICY_DIRECT, ignoreCase = true)) {
            return RouteDecision(Outbound.Direct, ClashConfig.POLICY_DIRECT, detail = "策略 DIRECT")
        }
        if (name.equals(ClashConfig.POLICY_REJECT, ignoreCase = true) ||
            name.equals("REJECT-DROP", ignoreCase = true)
        ) {
            return RouteDecision(Outbound.Reject, ClashConfig.POLICY_REJECT, detail = "策略 REJECT")
        }
        if (config == null) {
            return RouteDecision(Outbound.Unavailable(name, "尚未导入配置"), name, detail = "缺少配置")
        }
        if (!visited.add(name)) {
            return RouteDecision(Outbound.Unavailable(name, "代理组存在循环引用"), name, detail = "循环引用：$name")
        }
        try {
            config.node(name)?.let { node ->
                return if (node.isSupported) {
                    RouteDecision(Outbound.Node(node), node.name, detail = "节点 ${node.name}（${node.protocol.token}）")
                } else {
                    RouteDecision(
                        Outbound.Unavailable(name, "协议 ${node.protocol.token} 暂不支持"),
                        name,
                        detail = "节点 ${node.name} 协议不被支持",
                    )
                }
            }
            config.group(name)?.let { group ->
                return resolveGroup(group, config, preferences, latencyOf, visited)
            }
        } finally {
            visited.remove(name)
        }
        return RouteDecision(Outbound.Unavailable(name, "找不到名为「$name」的节点或代理组"), name,
            detail = "未知策略：$name")
    }

    private fun resolveGroup(
        group: ProxyGroup,
        config: ClashConfig,
        preferences: ProxyPreferences,
        latencyOf: (String) -> Long?,
        visited: MutableSet<String>,
    ): RouteDecision {
        val selected = preferences.selectionOf(group.name)
        val members = group.members.filter { it.isNotBlank() }.distinct()
        if (members.isEmpty()) {
            return RouteDecision(Outbound.Unavailable(group.name, "代理组为空"), group.name,
                detail = "组 ${group.name} 没有成员")
        }
        return when (group.type) {
            ProxyGroupType.Select -> {
                if (selected != null && (selected in members || selected.equals(ClashConfig.POLICY_DIRECT, true) ||
                        selected.equals(ClashConfig.POLICY_REJECT, true))
                ) {
                    // select 组严格使用用户选择：选择不可用时失败关闭，不偷偷换成别的节点。
                    resolveMember(selected, group, config, preferences, latencyOf, visited)
                        .copy(groupName = group.name)
                } else {
                    firstResolvable(members, group, config, preferences, latencyOf, visited)
                }
            }
            ProxyGroupType.UrlTest,
            ProxyGroupType.Fallback,
            ProxyGroupType.LoadBalance,
            -> {
                // 有测速结果时按延迟升序；未测速的排在最后，仍能作为兜底。
                val sorted = members.sortedBy { latencyOf(it) ?: Long.MAX_VALUE }
                firstResolvable(sorted, group, config, preferences, latencyOf, visited)
            }
            ProxyGroupType.Relay -> firstResolvable(members.take(1), group, config, preferences, latencyOf, visited)
            ProxyGroupType.Unknown -> firstResolvable(members, group, config, preferences, latencyOf, visited)
        }
    }

    private fun firstResolvable(
        members: List<String>,
        group: ProxyGroup,
        config: ClashConfig,
        preferences: ProxyPreferences,
        latencyOf: (String) -> Long?,
        visited: MutableSet<String>,
    ): RouteDecision {
        var last: RouteDecision? = null
        for (member in members) {
            val decision = resolveMember(member, group, config, preferences, latencyOf, visited)
            if (decision.outbound !is Outbound.Unavailable) {
                return decision.copy(groupName = group.name)
            }
            last = decision
        }
        return (last ?: RouteDecision(
            Outbound.Unavailable(group.name, "组内没有可用节点"),
            group.name,
            detail = "组 ${group.name} 无可用成员",
        )).copy(groupName = group.name)
    }

    private fun resolveMember(
        member: String,
        group: ProxyGroup,
        config: ClashConfig,
        preferences: ProxyPreferences,
        latencyOf: (String) -> Long?,
        visited: MutableSet<String>,
    ): RouteDecision {
        val decision = resolvePolicy(member, config, preferences, latencyOf, visited)
        return if (decision.groupName == null) decision.copy(groupName = group.name) else decision
    }
}
