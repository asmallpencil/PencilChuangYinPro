package com.pencil.chuangyin.core.agent.aiimage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

/**
 * AI 图片处理设置仓库。
 *
 * 读取方是拍照页（处理前取一次快照）与设置页（实时编辑），因此用单例 + StateFlow；
 * 写入永远落 [AiImageSettings.sanitized] 后再持久化，保证磁盘上不会出现越界参数。
 */
interface AiImageSettingsRepository {

    /** 当前设置（永远已收敛到合法区间）。 */
    val settings: StateFlow<AiImageSettings>

    /** 以不可变快照的方式更新设置；空操作也会安全返回。 */
    suspend fun update(transform: (AiImageSettings) -> AiImageSettings)
}

/**
 * 基于 DataStore 的实现：逐字段存储，字段缺失/损坏时用默认值兜底，
 * 版本升级新增字段不需要迁移脚本。
 */
@Singleton
class DataStoreAiImageSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AiImageSettingsRepository {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    private val _settings = MutableStateFlow(AiImageSettings())

    override val settings: StateFlow<AiImageSettings> = _settings.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 先同步播种首帧，避免拍照页首帧读到默认值后又跳变。
        runCatching {
            val snapshot = runBlocking { context.dataStore.data.map { it.toSettings() }.first() }
            _settings.value = snapshot
        }
        scope.launch {
            context.dataStore.data.map { it.toSettings() }.collect { _settings.value = it }
        }
    }

    override suspend fun update(transform: (AiImageSettings) -> AiImageSettings) {
        val next = transform(_settings.value).sanitized()
        if (next == _settings.value) return
        // 先更新内存状态（界面立即响应），再落盘。
        _settings.value = next
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_SCENE] = next.defaultScene.name
            prefs[KEY_OUTPUT_EDGE] = next.outputMaxEdgePx
            prefs[KEY_WHITEN] = next.whitenStrength
            prefs[KEY_ENHANCE] = next.enhanceStrength
            prefs[KEY_FALLBACK] = next.fallbackToLocal
        }
    }

    private fun Preferences.toSettings(): AiImageSettings {
        val defaults = AiImageSettings()
        return AiImageSettings(
            defaultScene = AiImageScene.fromStored(this[KEY_DEFAULT_SCENE]),
            outputMaxEdgePx = this[KEY_OUTPUT_EDGE] ?: defaults.outputMaxEdgePx,
            whitenStrength = this[KEY_WHITEN] ?: defaults.whitenStrength,
            enhanceStrength = this[KEY_ENHANCE] ?: defaults.enhanceStrength,
            fallbackToLocal = this[KEY_FALLBACK] ?: defaults.fallbackToLocal,
        ).sanitized()
    }

    private companion object {
        const val DATASTORE_NAME = "pencil_ai_image"
        val KEY_DEFAULT_SCENE = stringPreferencesKey("defaultScene")
        val KEY_OUTPUT_EDGE = intPreferencesKey("outputMaxEdgePx")
        val KEY_WHITEN = floatPreferencesKey("whitenStrength")
        val KEY_ENHANCE = floatPreferencesKey("enhanceStrength")
        val KEY_FALLBACK = booleanPreferencesKey("fallbackToLocal")
    }
}
