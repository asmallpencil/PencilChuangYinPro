package com.pencil.chuangyin.core.proxy.parse

import com.pencil.chuangyin.core.proxy.model.ClashConfig
import com.pencil.chuangyin.core.proxy.model.ProxyGroup
import com.pencil.chuangyin.core.proxy.model.ProxyGroupType
import com.pencil.chuangyin.core.proxy.model.ProxyNode
import com.pencil.chuangyin.core.proxy.model.ProxyProtocol
import com.pencil.chuangyin.core.proxy.model.ProxyRule
import com.pencil.chuangyin.core.proxy.model.ProxyRuleType

/** 配置无法解析时抛出，消息可直接展示给用户。 */
class ClashParseException(message: String, val details: List<String> = emptyList()) : Exception(message)

/**
 * Clash 配置解析器：把 YAML 文本转成路由所需的 [ClashConfig]。
 *
 * 防御策略：
 * - 单个节点/组/规则解析失败不会中断整体解析，只记录 warning；
 * - 完全无法识别的文本（例如订阅返回的 HTML 错误页）抛出 [ClashParseException]，
 *   由上层提示用户，而不是静默生成空配置；
 * - 所有 warning 去重并截断，避免订阅里的上千条异常规则把界面撑爆。
 */
class ClashConfigParser {

    fun parse(rawText: String): ClashConfig {
        val text = rawText.trim()
        if (text.isEmpty()) throw ClashParseException("配置内容为空")
        if (looksLikeUrl(text) && !text.contains('\n')) {
            throw ClashParseException("这是一条链接，请使用「从链接导入」；当前不支持把链接当配置正文解析")
        }

        val root = try {
            YamlLite.parse(text)
        } catch (t: Throwable) {
            throw ClashParseException("YAML 解析失败：${t.message ?: "未知错误"}")
        }
        val obj = root.asObject()
            ?: throw ClashParseException("配置顶层不是键值结构，可能不是 Clash 配置或订阅返回了错误页")
        val warnings = Warnings()
        val nodes = parseNodes(obj["proxies"], warnings)
        val groups = parseGroups(obj["proxy-groups"], nodes, warnings)
        val rules = parseRules(obj["rules"], warnings)

        if (nodes.isEmpty() && groups.isEmpty()) {
            throw ClashParseException(
                "配置里没有可用的 proxies / proxy-groups",
                listOf("请确认导入的是 Clash / Clash Meta 订阅或配置，而不是纯规则集。"),
            )
        }
        if (obj.containsKey("proxy-providers")) {
            warnings.add("配置包含 proxy-providers：当前不读取远程节点提供者，对应的节点不会出现")
        }
        return ClashConfig(
            nodes = nodes,
            groups = groups,
            rules = rules,
            warnings = warnings.snapshot(),
        )
    }

    // ---------------- 节点 ----------------

    private fun parseNodes(value: YamlValue?, warnings: Warnings): List<ProxyNode> {
        val array = value?.asArray() ?: return emptyList()
        val result = mutableListOf<ProxyNode>()
        val names = mutableSetOf<String>()
        array.forEachIndexed { index, item ->
            val map = item.asObject() ?: return@forEachIndexed
            val name = map["name"].asString()?.trim().orEmpty()
            if (name.isBlank()) {
                warnings.add("第 ${index + 1} 个节点缺少 name，已跳过")
                return@forEachIndexed
            }
            if (!names.add(name)) {
                warnings.add("节点名重复「$name」，后出现的已被忽略")
                return@forEachIndexed
            }
            val protocol = ProxyProtocol.fromToken(map["type"].asString())
            val server = (map["server"].asString() ?: map["servername"].asString())?.trim()
                ?.removeSurrounding("[", "]").orEmpty()
            val port = map["port"].asInt() ?: map["port"].asString()?.toIntOrNull()
            if (server.isBlank() || port == null || port !in 1..65535) {
                warnings.add("节点「$name」缺少合法 server/port，已跳过")
                return@forEachIndexed
            }
            if (protocol == ProxyProtocol.Unknown) {
                warnings.add("节点「$name」的协议「${map["type"].asString().orEmpty()}」无法识别")
            }
            result += ProxyNode(
                name = name,
                protocol = protocol,
                server = server,
                port = port,
                username = map["username"].asString() ?: map["uuid"].asString(),
                password = map["password"].asString(),
                cipher = map["cipher"].asString(),
                tls = map["tls"].asBoolean() ?: false,
                sni = map["sni"].asString() ?: map["servername"].asString(),
                udp = map["udp"].asBoolean() ?: false,
                skipCertVerify = map["skip-cert-verify"].asBoolean() ?: false,
            )
        }
        return result
    }

    // ---------------- 代理组 ----------------

    private fun parseGroups(
        value: YamlValue?,
        nodes: List<ProxyNode>,
        warnings: Warnings,
    ): List<ProxyGroup> {
        val array = value?.asArray() ?: return emptyList()
        val result = mutableListOf<ProxyGroup>()
        val names = mutableSetOf<String>()
        array.forEachIndexed { index, item ->
            val map = item.asObject() ?: return@forEachIndexed
            val name = map["name"].asString()?.trim().orEmpty()
            if (name.isBlank()) {
                warnings.add("第 ${index + 1} 个代理组缺少 name，已跳过")
                return@forEachIndexed
            }
            if (!names.add(name)) {
                warnings.add("代理组名重复「$name」，后出现的已被忽略")
                return@forEachIndexed
            }
            if (map.containsKey("use")) {
                warnings.add("代理组「$name」使用了 proxy-providers，远程节点不会出现")
            }
            val explicit = map["proxies"].asArray()
                ?.mapNotNull { it.asString()?.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            val includeAll = map["include-all"].asBoolean() == true ||
                map["include-all-proxies"].asBoolean() == true
            val filter = map["filter"].asString()?.trim().orEmpty()
            val excludeFilter = map["exclude-filter"].asString()?.trim().orEmpty()
            val included = if (includeAll) {
                filterNodes(nodes, filter, excludeFilter).map { it.name }
            } else {
                emptyList()
            }
            val members = (explicit + included).distinct()
            if (members.isEmpty()) {
                warnings.add("代理组「$name」没有任何成员")
            }
            result += ProxyGroup(
                name = name,
                type = ProxyGroupType.fromToken(map["type"].asString()),
                members = members,
                testUrl = map["url"].asString()?.takeIf { it.isNotBlank() },
                intervalSeconds = map["interval"].asInt()?.takeIf { it > 0 },
                toleranceMs = map["tolerance"].asInt()?.takeIf { it >= 0 },
            )
        }
        return result
    }

    private fun filterNodes(nodes: List<ProxyNode>, filter: String, excludeFilter: String): List<ProxyNode> {
        val include = filter.toSafeRegex()
        val exclude = excludeFilter.toSafeRegex()
        return nodes.filter { node ->
            val included = include == null || include.containsMatchIn(node.name)
            val excluded = exclude != null && exclude.containsMatchIn(node.name)
            included && !excluded
        }
    }

    private fun String.toSafeRegex(): Regex? {
        if (isBlank()) return null
        return runCatching { Regex(this, RegexOption.IGNORE_CASE) }.getOrNull()
    }

    // ---------------- 规则 ----------------

    private fun parseRules(value: YamlValue?, warnings: Warnings): List<ProxyRule> {
        val array = value?.asArray() ?: return emptyList()
        val result = mutableListOf<ProxyRule>()
        array.forEach { item ->
            val raw = item.asString()?.trim().orEmpty()
            if (raw.isEmpty()) return@forEach
            result += parseRule(raw, warnings)
        }
        return result.filter { it.type != ProxyRuleType.Unsupported || it.unsupportedReason != null }
    }

    private fun parseRule(raw: String, warnings: Warnings): ProxyRule {
        val firstComma = raw.indexOf(',')
        val typeToken = (if (firstComma < 0) raw else raw.substring(0, firstComma)).trim().uppercase()
        val type = ProxyRuleType.fromToken(typeToken)
        if (type == ProxyRuleType.Unsupported) {
            warnings.add("不支持的规则类型「$typeToken」，已忽略：$raw")
            return ProxyRule(
                type = ProxyRuleType.Unsupported,
                value = raw,
                policy = "",
                noResolve = false,
                raw = raw,
                unsupportedReason = "不支持的规则类型 $typeToken",
            )
        }
        if (type == ProxyRuleType.Match) {
            val policy = raw.substringAfter(',', "").trim()
            if (policy.isEmpty()) {
                warnings.add("MATCH 规则缺少策略：$raw")
                return ProxyRule(type, "", "", false, raw, "MATCH 规则缺少策略")
            }
            return ProxyRule(type, "", policy, false, raw)
        }
        if (type == ProxyRuleType.Inline || type == ProxyRuleType.RuleSet || type == ProxyRuleType.Script) {
            warnings.add("规则集类型「$typeToken」需要外部资源，当前已跳过：$raw")
            return ProxyRule(type, raw.substringAfter(',').substringBeforeLast(',').trim(), "", false, raw,
                "$typeToken 规则当前不支持")
        }
        val parts = raw.split(',').map { it.trim() }
        val noResolve = parts.lastOrNull()?.equals("no-resolve", ignoreCase = true) == true
        val effective = if (noResolve) parts.dropLast(1) else parts
        if (effective.size < 3) {
            warnings.add("规则格式不完整，已忽略：$raw")
            return ProxyRule(type, "", "", noResolve, raw, "规则缺少策略")
        }
        val policy = effective.last()
        val ruleValue = effective.subList(1, effective.size - 1).joinToString(",")
        val unsupported = when (type) {
            ProxyRuleType.GeoSite -> "GEOSITE 需要 geosite 数据库，当前按不命中处理"
            ProxyRuleType.ProcessName -> "PROCESS-NAME 无法在应用内获取进程名，当前按不命中处理"
            ProxyRuleType.SrcIpCidr -> "SRC-IP-CIDR 在本应用中没有源地址语义，当前按不命中处理"
            else -> null
        }
        if (unsupported != null) warnings.add("$unsupported：$raw")
        return ProxyRule(
            type = type,
            value = ruleValue,
            policy = policy,
            noResolve = noResolve,
            raw = raw,
            unsupportedReason = unsupported,
        )
    }

    // ---------------- 工具 ----------------

    fun looksLikeUrl(text: String): Boolean {
        val trimmed = text.trim()
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
            !trimmed.contains('\n') && !trimmed.contains(' ')
    }

    /** 警告收集器：去重、限量，避免订阅异常时刷屏。 */
    private class Warnings {
        private val items = LinkedHashSet<String>()

        fun add(message: String) {
            if (items.size >= MAX_WARNINGS) return
            items += message
        }

        fun snapshot(): List<String> = items.toList()

        private companion object {
            const val MAX_WARNINGS = 24
        }
    }
}
