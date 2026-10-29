package com.pencil.chuangyin.core.proxy.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pencil.chuangyin.core.proxy.model.ClashConfig
import com.pencil.chuangyin.core.proxy.model.ProxyConfigEntry
import com.pencil.chuangyin.core.proxy.model.ProxyImportResult
import com.pencil.chuangyin.core.proxy.model.ProxyPreferences
import com.pencil.chuangyin.core.proxy.model.ProxySourceType
import com.pencil.chuangyin.core.proxy.parse.ClashConfigParser
import com.pencil.chuangyin.core.proxy.parse.ClashParseException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 基于 DataStore 的代理配置仓库。
 *
 * 存储形态：偏好是一份 JSON；配置列表是「元信息 + 原始正文」的 JSON 数组。
 * 每次加载都重新解析正文，因此解析器升级后旧配置会自动获得新能力（不需要迁移）。
 */
@Singleton
class DataStoreProxySettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProxySettingsRepository {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val parser = ClashConfigParser()

    private val writeMutex = Mutex()

    private val _preferences = MutableStateFlow(ProxyPreferences())
    private val _configs = MutableStateFlow<List<ProxyConfigEntry>>(emptyList())
    private val _state = MutableStateFlow(ProxyState())

    override val preferences: StateFlow<ProxyPreferences> = _preferences.asStateFlow()
    override val configs: StateFlow<List<ProxyConfigEntry>> = _configs.asStateFlow()
    override val state: StateFlow<ProxyState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 首帧同步播种，避免界面先展示默认值再跳变。
        runCatching {
            val snapshot = runBlocking { context.dataStore.data.first() }
            applySnapshot(snapshot)
        }
        publish(ready = true)
        scope.launch {
            context.dataStore.data.collect { snapshot ->
                applySnapshot(snapshot)
                publish(ready = true)
            }
        }
    }

    // ---------------- 读 ----------------

    private fun applySnapshot(snapshot: Preferences) {
        val prefs = decodePreferences(snapshot[KEY_PREFERENCES])
        val entries = decodeConfigs(snapshot[KEY_CONFIGS])
        _preferences.value = prefs
        _configs.value = entries
    }

    private fun publish(ready: Boolean) {
        val prefs = _preferences.value
        val entries = _configs.value
        val active = entries.firstOrNull { it.id == prefs.activeConfigId } ?: entries.firstOrNull()
        _state.value = ProxyState(
            preferences = prefs,
            configs = entries,
            activeConfig = active,
            ready = ready,
        )
    }

    private fun decodePreferences(raw: String?): ProxyPreferences {
        if (raw.isNullOrBlank()) return ProxyPreferences()
        return runCatching {
            json.decodeFromString(ProxyPreferences.serializer(), raw)
        }.getOrElse { ProxyPreferences() }
    }

    private fun decodeConfigs(raw: String?): List<ProxyConfigEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val document = runCatching {
            json.decodeFromString(StoredDocument.serializer(), raw)
        }.getOrElse { StoredDocument() }
        return document.configs.map { stored ->
            val config = runCatching { parser.parse(stored.raw) }.getOrElse { error ->
                ClashConfig(warnings = listOf("配置无法解析：${error.message ?: "未知错误"}"))
            }
            ProxyConfigEntry(
                id = stored.id,
                name = stored.name,
                source = stored.source,
                rawText = stored.raw,
                subscriptionUrl = stored.subscriptionUrl,
                createdAt = stored.createdAt,
                updatedAt = stored.updatedAt,
                config = config,
            )
        }
    }

    // ---------------- 写 ----------------

    override suspend fun updatePreferences(transform: (ProxyPreferences) -> ProxyPreferences) {
        writeMutex.withLock {
            val next = transform(_preferences.value)
            if (next == _preferences.value) return
            _preferences.value = next
            persist()
        }
    }

    override suspend fun addConfig(
        rawText: String,
        name: String?,
        source: ProxySourceType,
        subscriptionUrl: String?,
    ): ProxyImportResult = writeMutex.withLock {
        val config = tryParse(rawText) ?: return@withLock lastParseFailure
        val now = System.currentTimeMillis()
        val entry = ProxyConfigEntry(
            id = UUID.randomUUID().toString(),
            name = name?.takeIf { it.isNotBlank() } ?: defaultName(source),
            source = source,
            rawText = rawText,
            subscriptionUrl = subscriptionUrl,
            createdAt = now,
            updatedAt = now,
            config = config,
        )
        _configs.value = _configs.value + entry
        if (_preferences.value.activeConfigId == null) {
            _preferences.value = _preferences.value.copy(activeConfigId = entry.id)
        }
        persist()
        ProxyImportResult.Success(entry)
    }

    override suspend fun replaceConfig(id: String, rawText: String): ProxyImportResult =
        writeMutex.withLock {
            val existing = _configs.value.firstOrNull { it.id == id }
                ?: return@withLock ProxyImportResult.Failure("配置已不存在，请刷新后重试")
            val config = tryParse(rawText) ?: return@withLock lastParseFailure
            val updated = existing.copy(
                rawText = rawText,
                updatedAt = System.currentTimeMillis(),
                config = config,
            )
            _configs.value = _configs.value.map { if (it.id == id) updated else it }
            persist()
            ProxyImportResult.Success(updated)
        }

    override suspend fun removeConfig(id: String) {
        writeMutex.withLock {
            if (_configs.value.none { it.id == id }) return
            _configs.value = _configs.value.filterNot { it.id == id }
            val prefs = _preferences.value
            if (prefs.activeConfigId == id) {
                _preferences.value = prefs.copy(
                    activeConfigId = _configs.value.firstOrNull()?.id,
                    globalSelection = null,
                )
            }
            persist()
        }
    }

    override suspend fun renameConfig(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        writeMutex.withLock {
            if (_configs.value.none { it.id == id }) return
            _configs.value = _configs.value.map {
                if (it.id == id) it.copy(name = trimmed, updatedAt = System.currentTimeMillis()) else it
            }
            persist()
        }
    }

    override suspend fun setActiveConfig(id: String) {
        writeMutex.withLock {
            val target = _configs.value.firstOrNull { it.id == id } ?: return
            _preferences.value = _preferences.value.copy(
                activeConfigId = target.id,
                globalSelection = null,
            )
            persist()
        }
    }

    override suspend fun selectGroupNode(groupName: String, nodeName: String) {
        if (groupName.isBlank() || nodeName.isBlank()) return
        writeMutex.withLock {
            val prefs = _preferences.value
            val next = prefs.groupSelections.toMutableMap().apply { put(groupName, nodeName) }
            _preferences.value = prefs.copy(groupSelections = next)
            persist()
        }
    }

    override suspend fun setGlobalSelection(policy: String) {
        if (policy.isBlank()) return
        writeMutex.withLock {
            _preferences.value = _preferences.value.copy(globalSelection = policy)
            persist()
        }
    }

    override suspend fun clearAllConfigs() {
        writeMutex.withLock {
            _configs.value = emptyList()
            _preferences.value = _preferences.value.copy(
                activeConfigId = null,
                globalSelection = null,
                groupSelections = emptyMap(),
            )
            persist()
        }
    }

    private suspend fun persist() {
        val prefs = _preferences.value
        val entries = _configs.value
        publish(ready = true)
        context.dataStore.edit { p ->
            p[KEY_PREFERENCES] = json.encodeToString(ProxyPreferences.serializer(), prefs)
            p[KEY_CONFIGS] = json.encodeToString(
                StoredDocument.serializer(),
                StoredDocument(entries.map { it.toStored() }),
            )
        }
    }

    // ---------------- 解析 ----------------

    @Volatile
    private var lastParseFailure: ProxyImportResult.Failure =
        ProxyImportResult.Failure("配置解析失败")

    private fun tryParse(rawText: String): ClashConfig? {
        val trimmed = rawText.trim()
        return try {
            parser.parse(trimmed)
        } catch (e: ClashParseException) {
            lastParseFailure = ProxyImportResult.Failure(e.message ?: "配置解析失败", e.details)
            null
        } catch (t: Throwable) {
            lastParseFailure = ProxyImportResult.Failure(
                "配置解析失败：${t.message ?: t.javaClass.simpleName}",
            )
            null
        }
    }

    private fun defaultName(source: ProxySourceType): String {
        val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
        val prefix = when (source) {
            ProxySourceType.Link -> "订阅"
            ProxySourceType.File -> "文件配置"
            ProxySourceType.QrCode -> "二维码配置"
            ProxySourceType.Manual -> "手动配置"
        }
        return "$prefix $stamp"
    }

    private fun ProxyConfigEntry.toStored(): StoredConfig = StoredConfig(
        id = id,
        name = name,
        source = source,
        raw = rawText,
        subscriptionUrl = subscriptionUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    /** 磁盘结构：与运行期模型解耦，避免解析结果被序列化进磁盘。 */
    @Serializable
    private data class StoredConfig(
        val id: String,
        val name: String,
        val source: ProxySourceType,
        val raw: String,
        val subscriptionUrl: String? = null,
        val createdAt: Long = 0L,
        val updatedAt: Long = 0L,
    )

    @Serializable
    private data class StoredDocument(
        val configs: List<StoredConfig> = emptyList(),
    )

    private companion object {
        const val DATASTORE_NAME = "pencil_proxy"
        val KEY_PREFERENCES = stringPreferencesKey("preferences")
        val KEY_CONFIGS = stringPreferencesKey("configs")
    }
}
