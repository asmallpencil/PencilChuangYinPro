package com.pencil.chuangyin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.agent.aiimage.AiImageScene
import com.pencil.chuangyin.core.agent.aiimage.AiImageSettings
import com.pencil.chuangyin.core.agent.aiimage.AiImageSettingsBounds
import com.pencil.chuangyin.core.agent.aiimage.AiImageSettingsRepository
import com.pencil.chuangyin.core.agent.config.AiConfigRepository
import com.pencil.chuangyin.core.agent.config.AiModelConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「AI 图片处理设置」页的状态。
 *
 * 参数本身由 [AiImageSettingsRepository] 持久化，本 ViewModel 只做：
 * - 把仓库的 Flow 原样暴露给 UI（单一数据源，不做本地副本，避免双写不一致）；
 * - 把 UI 的输入收敛到区间内再落库（防御性开发，非法值不进入仓库）；
 * - 暴露当前 AI 打印所用的模型配置，供页面展示「当前模型」。
 */
@HiltViewModel
class AiImageSettingsViewModel @Inject constructor(
    private val settingsRepository: AiImageSettingsRepository,
    aiConfigRepository: AiConfigRepository,
) : ViewModel() {

    /** AI 图片处理参数；仓库是唯一数据源。 */
    val settings: StateFlow<AiImageSettings> = settingsRepository.settings

    private val _activeModel = MutableStateFlow<AiModelConfig?>(null)

    /** 当前选中的模型配置（与 AI 打印共用），未配置时为 null。 */
    val activeModel: StateFlow<AiModelConfig?> = _activeModel.asStateFlow()

    init {
        viewModelScope.launch {
            aiConfigRepository.activeModel.collect { model ->
                _activeModel.value = model
            }
        }
    }

    fun setDefaultScene(scene: AiImageScene) {
        update { it.copy(defaultScene = scene) }
    }

    fun setOutputMaxEdgePx(px: Int) {
        update {
            it.copy(
                outputMaxEdgePx = px.coerceIn(
                    AiImageSettingsBounds.OUTPUT_EDGE_MIN_PX,
                    AiImageSettingsBounds.OUTPUT_EDGE_MAX_PX,
                ),
            )
        }
    }

    fun setWhitenStrength(strength: Float) {
        update { it.copy(whitenStrength = clampStrength(strength)) }
    }

    fun setEnhanceStrength(strength: Float) {
        update { it.copy(enhanceStrength = clampStrength(strength)) }
    }

    fun setFallbackToLocal(enabled: Boolean) {
        update { it.copy(fallbackToLocal = enabled) }
    }

    private fun clampStrength(value: Float): Float = if (value.isNaN()) {
        AiImageSettingsBounds.STRENGTH_MIN
    } else {
        value.coerceIn(AiImageSettingsBounds.STRENGTH_MIN, AiImageSettingsBounds.STRENGTH_MAX)
    }

    private fun update(transform: (AiImageSettings) -> AiImageSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
