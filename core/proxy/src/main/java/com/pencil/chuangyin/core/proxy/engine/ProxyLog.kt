package com.pencil.chuangyin.core.proxy.engine

/** 连接日志类型，决定界面上的颜色与图标。 */
enum class ProxyLogKind {
    /** 走了代理节点。 */
    Proxied,

    /** 按规则直连（命中 DIRECT）。 */
    Direct,

    /** 被 REJECT 规则或「出口不可用」拒绝。 */
    Rejected,

    /** 连接失败（ProxySelector.connectFailed 回调）。 */
    Failed,
}

/** 一条连接日志。 */
data class ProxyLogEntry(
    val id: Long,
    val timeMillis: Long,
    val host: String,
    val policy: String,
    val detail: String,
    val kind: ProxyLogKind,
)
