package com.pencil.chuangyin.core.agent.config

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pencil.chuangyin.core.agent.AgentJson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/**
 * 基于 DataStore 的模型配置仓库。
 *
 * 全量配置以单条 JSON 字符串持久化（配置条目数量极少，整体读写比逐条字段更简单可靠）。
 */
@Singleton
class DataStoreAiConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AiConfigRepository {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    private val _models = MutableStateFlow<List<AiModelConfig>>(emptyList())
    override val models: StateFlow<List<AiModelConfig>> = _models.asStateFlow()

    private val _activeModelId = MutableStateFlow<String?>(null)
    override val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()

    private val _activeModel = MutableStateFlow<AiModelConfig?>(null)
    override val activeModel: StateFlow<AiModelConfig?> = _activeModel.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 同步取首个值做初始播种（DataStore 首读很快），保证界面首帧即有配置。
        runCatching {
            val snapshot = runBlocking {
                context.dataStore.data.map { it.toSnapshot() }.first()
            }
            publish(snapshot.models, snapshot.activeId)
        }
        // 之后持续订阅更新（非阻塞）。
        scope.launch {
            context.dataStore.data.map { it.toSnapshot() }.collect { publish(it.models, it.activeId) }
        }
    }

    override suspend fun upsert(model: AiModelConfig): AiModelConfig {
        val saved = if (model.id.isBlank()) model.copy(id = UUID.randomUUID().toString()) else model
        val updated = _models.value.toMutableList()
        val index = updated.indexOfFirst { it.id == saved.id }
        if (index >= 0) updated[index] = saved else updated.add(saved)
        // 首个配置自动成为当前选中项。
        val active = _activeModelId.value?.takeIf { id -> updated.any { it.id == id } } ?: saved.id
        publish(updated, active)
        persist(updated, active)
        return saved
    }

    override suspend fun remove(id: String) {
        val remaining = _models.value.filterNot { it.id == id }
        val active = _activeModelId.value?.takeIf { kept -> remaining.any { it.id == kept } }
            ?: remaining.firstOrNull()?.id
        publish(remaining, active)
        persist(remaining, active)
    }

    override suspend fun setActive(id: String?) {
        val models = _models.value
        val active = id?.takeIf { candidate -> models.any { it.id == candidate } }
        publish(models, active)
        persist(models, active)
    }

    /** 同步更新三路状态流，避免界面读到不一致的中间态。 */
    private fun publish(models: List<AiModelConfig>, activeId: String?) {
        val resolved = activeId?.takeIf { id -> models.any { it.id == id } }
            ?: models.firstOrNull()?.id
        _models.value = models
        _activeModelId.value = resolved
        _activeModel.value = models.firstOrNull { it.id == resolved }
    }

    private suspend fun persist(models: List<AiModelConfig>, activeId: String?) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MODELS] = AgentJson.encodeToString(MODELS_SERIALIZER, models)
            if (activeId == null) {
                prefs.remove(KEY_ACTIVE_ID)
            } else {
                prefs[KEY_ACTIVE_ID] = activeId
            }
        }
    }

    private fun Preferences.toSnapshot(): Snapshot {
        val models = this[KEY_MODELS]
            ?.let { raw -> runCatching { AgentJson.decodeFromString(MODELS_SERIALIZER, raw) }.getOrNull() }
            .orEmpty()
        return Snapshot(models, this[KEY_ACTIVE_ID])
    }

    private data class Snapshot(val models: List<AiModelConfig>, val activeId: String?)

    private companion object {
        const val DATASTORE_NAME = "pencil_ai_config"
        val KEY_MODELS = stringPreferencesKey("models")
        val KEY_ACTIVE_ID = stringPreferencesKey("activeModelId")
        val MODELS_SERIALIZER = ListSerializer(AiModelConfig.serializer())
    }
}
