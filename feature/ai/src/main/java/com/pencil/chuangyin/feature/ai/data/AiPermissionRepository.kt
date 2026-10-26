package com.pencil.chuangyin.feature.ai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pencil.chuangyin.core.agent.runtime.AgentPermissionMode
import com.pencil.chuangyin.core.agent.runtime.MutableAgentPermissionModeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 权限模式的持久化仓库。
 *
 * 引擎读取的是 [MutableAgentPermissionModeProvider] 的内存状态流；本仓库负责把用户选择
 * 落盘到 DataStore，并在启动时回填到该 provider，保证重启后仍沿用上次的模式。
 */
@Singleton
class AiPermissionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val provider: MutableAgentPermissionModeProvider,
) {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 当前模式的只读状态流（转发引擎所用的 provider）。 */
    val mode: StateFlow<AgentPermissionMode> get() = provider.mode

    init {
        // 同步取首个值做初始播种，保证界面首帧即为上次选择的模式。
        runCatching {
            val saved = runBlocking { context.dataStore.data.first()[KEY_MODE] }
            saved.toPermissionMode()?.let(provider::set)
        }
        scope.launch {
            context.dataStore.data.collect { prefs ->
                val mode = prefs[KEY_MODE].toPermissionMode() ?: return@collect
                if (provider.mode.value != mode) provider.set(mode)
            }
        }
    }

    /** 切换权限模式并落盘。 */
    fun set(mode: AgentPermissionMode) {
        provider.set(mode)
        scope.launch {
            context.dataStore.edit { prefs -> prefs[KEY_MODE] = mode.name }
        }
    }

    private fun String?.toPermissionMode(): AgentPermissionMode? =
        this?.let { raw -> runCatching { AgentPermissionMode.valueOf(raw) }.getOrNull() }

    private companion object {
        const val DATASTORE_NAME = "pencil_ai_permission"
        val KEY_MODE = stringPreferencesKey("permission_mode")
    }
}
