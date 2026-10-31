package com.pencil.chuangyin.core.proxy.core

import com.pencil.chuangyin.core.proxy.model.ProxyNode
import com.pencil.chuangyin.core.proxy.model.ProxyProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 把 Clash 节点翻译成 sing-box 出站配置。
 *
 * 设计取舍：**每个节点一个独立的本地 SOCKS 入口**。
 * 路由（DIRECT / REJECT / 规则 / 代理组选择）仍由本应用的 ProxyRouter 负责，
 * 内核只按「入口 → 出站」一一对应地建隧道。这样：
 * - 不需要和内核争抢「当前选中节点」的全局状态，多个域名并发走不同节点也不会串；
 * - 测速、切换节点、失败关闭沿用原有逻辑，一行都不用改；
 * - 关闭代理时整个内核进程直接停掉，不会有残留监听。
 *
 * 无法生成的节点（缺 uuid / 密码等）会被跳过且不分配端口，避免出现
 * 「有入口但路由落到 direct」的静默直连泄露。
 */
object SingBoxConfigBuilder {

    data class CoreConfig(
        val json: String,
        /** 节点名 → 本地 SOCKS 端口；只包含真正生成了出站的节点。 */
        val ports: Map<String, Int>,
    )

    private val json = Json { encodeDefaults = true }

    fun build(nodes: List<ProxyNode>, portStart: Int): CoreConfig {
        val inbounds = mutableListOf<JsonElement>()
        val outbounds = mutableListOf<JsonElement>()
        val rules = mutableListOf<JsonElement>()
        val ports = LinkedHashMap<String, Int>()
        var nextPort = portStart

        nodes.forEachIndexed { index, node ->
            if (nextPort !in 1..65535) return@forEachIndexed
            val outboundTag = "out-$index"
            val inboundTag = "in-$index"
            val outbound = outbound(node, outboundTag) ?: return@forEachIndexed
            val port = nextPort
            nextPort += 1
            ports[node.name] = port
            inbounds += buildJsonObject {
                put("type", "socks")
                put("tag", inboundTag)
                put("listen", LISTEN_ADDRESS)
                put("listen_port", port)
            }
            outbounds += outbound
            rules += buildJsonObject {
                put("inbound", JsonArray(listOf(JsonPrimitive(inboundTag))))
                put("action", "route")
                put("outbound", outboundTag)
            }
        }

        outbounds += buildJsonObject {
            put("type", "direct")
            put("tag", DIRECT_TAG)
        }

        val root = buildJsonObject {
            put("log", buildJsonObject { put("disabled", true) })
            put("inbounds", JsonArray(inbounds))
            put("outbounds", JsonArray(outbounds))
            put("route", buildJsonObject {
                put("rules", JsonArray(rules))
                put("final", DIRECT_TAG)
                put("auto_detect_interface", true)
            })
        }
        return CoreConfig(json.encodeToString(JsonObject.serializer(), root), ports)
    }

    // ---------------- 各协议 ----------------

    private fun outbound(node: ProxyNode, tag: String): JsonObject? = when (node.protocol) {
        ProxyProtocol.Shadowsocks -> shadowsocks(node, tag)
        ProxyProtocol.Vmess -> vmess(node, tag)
        ProxyProtocol.Vless -> vless(node, tag)
        ProxyProtocol.Trojan -> trojan(node, tag)
        ProxyProtocol.Hysteria -> hysteria(node, tag)
        ProxyProtocol.Hysteria2 -> hysteria2(node, tag)
        ProxyProtocol.Tuic -> tuic(node, tag)
        ProxyProtocol.AnyTls -> anyTls(node, tag)
        ProxyProtocol.Ssh -> ssh(node, tag)
        else -> null
    }

    private fun shadowsocks(node: ProxyNode, tag: String): JsonObject? {
        val extra = node.extra
        val method = node.cipher ?: extra["cipher"] ?: return null
        val password = node.password ?: extra["password"] ?: return null
        return buildJsonObject {
            put("type", "shadowsocks")
            put("tag", tag)
            put("server", node.server)
            put("server_port", node.port)
            put("method", method)
            put("password", password)
            plugin(extra)?.let {
                put("plugin", it.first)
                put("plugin_opts", it.second)
            }
        }
    }

    /** Clash 的 `plugin` + `plugin-opts` 翻译成 sip003 插件名与参数串。 */
    private fun plugin(extra: Map<String, String>): Pair<String, String>? {
        val name = extra["plugin"]?.takeIf { it.isNotBlank() } ?: return null
        val opts = mutableListOf<String>()
        extra["plugin-opts.mode"]?.let { opts += "mode=$it" }
        extra["plugin-opts.host"]?.let { opts += "host=$it" }
        extra["plugin-opts.path"]?.let { opts += "path=$it" }
        if (extra["plugin-opts.tls"]?.toBoolean() == true) opts += "tls"
        val target = when (name) {
            "obfs" -> "obfs-local"
            else -> name
        }
        return target to opts.joinToString(";")
    }

    private fun vmess(node: ProxyNode, tag: String): JsonObject? {
        val extra = node.extra
        val uuid = extra["uuid"] ?: node.username ?: return null
        return buildJsonObject {
            put("type", "vmess")
            put("tag", tag)
            put("server", node.server)
            put("server_port", node.port)
            put("uuid", uuid)
            put("alter_id", extra["alterId"]?.trim()?.toIntOrNull() ?: 0)
            put("security", extra["cipher"]?.takeIf { it.isNotBlank() } ?: "auto")
            transport(extra)?.let { put("transport", it) }
            tls(node, extra, alwaysOn = false)?.let { put("tls", it) }
        }
    }

    private fun vless(node: ProxyNode, tag: String): JsonObject? {
        val extra = node.extra
        val uuid = extra["uuid"] ?: node.username ?: return null
        return buildJsonObject {
            put("type", "vless")
            put("tag", tag)
            put("server", node.server)
            put("server_port", node.port)
            put("uuid", uuid)
            extra["flow"]?.takeIf { it.isNotBlank() }?.let { put("flow", it) }
            put("packet_encoding", extra["packet-encoding"]?.takeIf { it.isNotBlank() } ?: "xudp")
            transport(extra)?.let { put("transport", it) }
            tls(node, extra, alwaysOn = false)?.let { put("tls", it) }
        }
    }

    private fun trojan(node: ProxyNode, tag: String): JsonObject? {
        val extra = node.extra
        val password = node.password ?: extra["password"] ?: return null
        return buildJsonObject {
            put("type", "trojan")
            put("tag", tag)
            put("server", node.server)
            put("server_port", node.port)
            put("password", password)
            transport(extra)?.let { put("transport", it) }
            tls(node, extra, alwaysOn = true)?.let { put("tls", it) }
        }
    }

    private fun hysteria(node: ProxyNode, tag: String): JsonObject? {
        val extra = node.extra
        val auth = extra["auth-str"] ?: extra["auth"] ?: node.password ?: return null
        return buildJsonObject {
            put("type", "hysteria")
            put("tag", tag)
            put("server", node.server)
            put("server_port", node.port)
            put("auth_str", auth)
            // sing-box 要求 Hysteria v1 必须声明带宽上限，Clash 里通常是 up/down（Mbps）。
            put("up_mbps", extra["up"]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 100)
            put("down_mbps", extra["down"]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 100)
            extra["obfs"]?.takeIf { it.isNotBlank() }?.let { put("obfs", it) }
            tls(node, extra, alwaysOn = true)?.let { put("tls", it) }
        }
    }

    private fun hysteria2(node: ProxyNode, tag: String): JsonObject? {
        val extra = node.extra
        val password = extra["password"] ?: node.password ?: extra["auth"] ?: return null
        return buildJsonObject {
            put("type", "hysteria2")
            put("tag", tag)
            put("server", node.server)
            put("server_port", node.port)
            put("password", password)
            val obfsType = extra["obfs"]?.takeIf { it.isNotBlank() }
            if (obfsType != null) {
                put("obfs", buildJsonObject {
                    put("type", obfsType)
                    extra["obfs-password"]?.let { put("password", it) }
                })
            }
            tls(node, extra, alwaysOn = true)?.let { put("tls", it) }
        }
    }

    private fun tuic(node: ProxyNode, tag: String): JsonObject? {
        val extra = node.extra
        val uuid = extra["uuid"] ?: node.username ?: return null
        val password = extra["password"] ?: node.password ?: return null
        return buildJsonObject {
            put("type", "tuic")
            put("tag", tag)
            put("server", node.server)
            put("server_port", node.port)
            put("uuid", uuid)
            put("password", password)
            extra["congestion-controller"]?.takeIf { it.isNotBlank() }?.let {
                put("congestion_control", it)
            }
            extra["udp-relay-mode"]?.takeIf { it.isNotBlank() }?.let {
                put("udp_relay_mode", it)
            }
            tls(node, extra, alwaysOn = true)?.let { put("tls", it) }
        }
    }

    private fun anyTls(node: ProxyNode, tag: String): JsonObject? {
        val extra = node.extra
        val password = node.password ?: extra["password"] ?: return null
        return buildJsonObject {
            put("type", "anytls")
            put("tag", tag)
            put("server", node.server)
            put("server_port", node.port)
            put("password", password)
            tls(node, extra, alwaysOn = true)?.let { put("tls", it) }
        }
    }

    private fun ssh(node: ProxyNode, tag: String): JsonObject? {
        val extra = node.extra
        val user = extra["username"] ?: node.username ?: return null
        val password = node.password ?: extra["password"]
        val privateKey = extra["private-key"]
        if (password.isNullOrBlank() && privateKey.isNullOrBlank()) return null
        return buildJsonObject {
            put("type", "ssh")
            put("tag", tag)
            put("server", node.server)
            put("server_port", node.port)
            put("user", user)
            password?.takeIf { it.isNotBlank() }?.let { put("password", it) }
            privateKey?.takeIf { it.isNotBlank() }?.let { put("private_key", it) }
        }
    }

    // ---------------- 传输与 TLS -------------

    /** Clash 的 `network` + `*-opts` 翻译成 sing-box transport。 */
    private fun transport(extra: Map<String, String>): JsonObject? = when (
        extra["network"]?.trim()?.lowercase()
    ) {
        "ws" -> buildJsonObject {
            put("type", "ws")
            put("path", extra["ws-opts.path"] ?: "/")
            extra["ws-opts.headers.Host"]?.let {
                put("headers", buildJsonObject { put("Host", it) })
            }
        }
        "grpc" -> buildJsonObject {
            put("type", "grpc")
            extra["grpc-opts.grpc-service-name"]?.let { put("service_name", it) }
        }
        "h2", "http" -> buildJsonObject {
            put("type", "http")
            extra["h2-opts.path"]?.let { put("path", it) }
            val hosts = collectArray(extra, "h2-opts.host")
            if (hosts.isNotEmpty()) put("host", JsonArray(hosts.map { JsonPrimitive(it) }))
        }
        else -> null
    }

    private fun tls(
        node: ProxyNode,
        extra: Map<String, String>,
        alwaysOn: Boolean,
    ): JsonObject? {
        val publicKey = extra["reality-opts.public-key"]
        // REALITY 与 TLS 是同一层：有 public-key 就一定得开 TLS。
        val enabled = alwaysOn || node.tls || extra["tls"]?.toBoolean() == true ||
            !publicKey.isNullOrBlank()
        if (!enabled) return null
        val serverName = extra["servername"] ?: extra["sni"] ?: node.sni
        val fingerprint = extra["client-fingerprint"]
        return buildJsonObject {
            put("enabled", true)
            serverName?.takeIf { it.isNotBlank() }?.let { put("server_name", it) }
            if (node.skipCertVerify || extra["skip-cert-verify"]?.toBoolean() == true) {
                put("insecure", true)
            }
            val alpn = collectArray(extra, "alpn")
            if (alpn.isNotEmpty()) put("alpn", JsonArray(alpn.map { JsonPrimitive(it) }))
            if (!fingerprint.isNullOrBlank()) {
                put("utls", buildJsonObject {
                    put("enabled", true)
                    put("fingerprint", fingerprint)
                })
            }
            if (!publicKey.isNullOrBlank()) {
                put("reality", buildJsonObject {
                    put("enabled", true)
                    put("public_key", publicKey)
                    extra["reality-opts.short-id"]?.let { put("short_id", it) }
                })
                if (fingerprint.isNullOrBlank()) {
                    put("utls", buildJsonObject {
                        put("enabled", true)
                        put("fingerprint", "chrome")
                    })
                }
            }
        }
    }

    /** 读取 `key.0`、`key.1` … 形式的数组字段。 */
    private fun collectArray(extra: Map<String, String>, key: String): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (true) {
            val value = extra["$key.$index"]?.takeIf { it.isNotBlank() } ?: break
            result += value
            index += 1
        }
        return result
    }

    private const val LISTEN_ADDRESS = "127.0.0.1"
    private const val DIRECT_TAG = "direct"
}
