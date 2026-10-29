package com.pencil.chuangyin.core.proxy.data

import com.pencil.chuangyin.core.proxy.model.ProxyConfigEntry
import com.pencil.chuangyin.core.proxy.model.ProxyImportResult
import com.pencil.chuangyin.core.proxy.model.ProxyPreferences
import com.pencil.chuangyin.core.proxy.model.ProxySourceType
import kotlinx.coroutines.flow.StateFlow

/** 代理的完整快照：偏好 + 全部配置 + 当前生效配置。 */
data class ProxyState(
    val preferences: ProxyPreferences = ProxyPreferences(),
    val configs: List<ProxyConfigEntry> = emptyList(),
    val activeConfig: ProxyConfigEntry? = null,
    /** 首次磁盘加载是否完成（完成前界面展示占位，避免默认值闪烁）。 */
    val ready: Boolean = false,
)

/**
 * 代理配置仓库。
 *
 * 所有写操作都先更新内存 StateFlow（界面立即响应）再落盘；解析永远发生在写入前，
 * 因此磁盘里不会出现无法解析的配置正文以外的状态。
 */
interface ProxySettingsRepository {

    val state: StateFlow<ProxyState>

    val preferences: StateFlow<ProxyPreferences>

    val configs: StateFlow<List<ProxyConfigEntry>>

    suspend fun updatePreferences(transform: (ProxyPreferences) -> ProxyPreferences)

    /** 新增配置；[name] 为空时自动生成。 */
    suspend fun addConfig(
        rawText: String,
        name: String?,
        source: ProxySourceType,
        subscriptionUrl: String? = null,
    ): ProxyImportResult

    /** 用新正文覆盖已有配置（订阅刷新 / 手动编辑）。 */
    suspend fun replaceConfig(id: String, rawText: String): ProxyImportResult

    suspend fun removeConfig(id: String)

    suspend fun renameConfig(id: String, name: String)

    suspend fun setActiveConfig(id: String)

    /** 选择某个组的出口。 */
    suspend fun selectGroupNode(groupName: String, nodeName: String)

    /** 选择全局出口。 */
    suspend fun setGlobalSelection(policy: String)

    suspend fun clearAllConfigs()
}
