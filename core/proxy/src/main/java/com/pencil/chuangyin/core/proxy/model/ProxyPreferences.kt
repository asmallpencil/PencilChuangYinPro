package com.pencil.chuangyin.core.proxy.model

import kotlinx.serialization.Serializable

/**
 * 代理偏好。
 *
 * 默认值即产品要求：**关闭**、**仅对 P 站生效**、规则模式。
 * 所有字段都带默认值，新增字段不需要迁移脚本；反序列化失败时整体回落到默认值。
 */
@Serializable
data class ProxyPreferences(
    val enabled: Boolean = false,
    val scope: ProxyScope = ProxyScope.PixivOnly,
    val mode: ProxyMode = ProxyMode.Rule,
    /** 当前使用的配置 id；为空表示没有配置。 */
    val activeConfigId: String? = null,
    /** 全局模式下的出口（节点名 / 组名 / DIRECT）。 */
    val globalSelection: String? = null,
    /** 每个代理组各自的选择（Clash 的 group selection 语义）。 */
    val groupSelections: Map<String, String> = emptyMap(),
    /** 是否执行 REJECT 类规则（关闭后广告拦截规则失效，仅保留分流）。 */
    val applyRejectRules: Boolean = true,
    /** 全局模式下是否也代理内网 / 回环地址（默认否，避免把打印机地址发到远端）。 */
    val proxyPrivateAddresses: Boolean = false,
) {
    /** 取某个组的当前选择；未选择过时返回 null，由路由器回落到组内第一个可用成员。 */
    fun selectionOf(groupName: String?): String? {
        if (groupName.isNullOrBlank()) return null
        return groupSelections[groupName]?.takeIf { it.isNotBlank() }
    }
}
