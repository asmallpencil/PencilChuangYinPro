package com.pencil.chuangyin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.proxy.data.ProxySettingsRepository
import com.pencil.chuangyin.core.proxy.model.ProxyScope
import com.pencil.chuangyin.core.theme.CornerStyle
import com.pencil.chuangyin.core.theme.LiquidGlassRenderer
import com.pencil.chuangyin.core.theme.LoadingAnimationStyle
import com.pencil.chuangyin.core.theme.MotionPreset
import com.pencil.chuangyin.core.theme.OutlineColorMode
import com.pencil.chuangyin.core.theme.ParticleType
import com.pencil.chuangyin.core.theme.ThemeRepository
import com.pencil.chuangyin.core.theme.ThemeState
import com.pencil.chuangyin.core.ui.data.OutputPathRepository
import com.pencil.chuangyin.core.ui.data.OUTPUT_DIR_DEFAULT
import com.pencil.chuangyin.printer.PrinterSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeState: ThemeState = ThemeState.Default,
    val paperWidthMm: Int = com.pencil.chuangyin.printer.PAPER_WIDTH_MM_DEFAULT,
    val autoConnectEnabled: Boolean = true,
    val outputDirName: String = OUTPUT_DIR_DEFAULT,
    /** 用户通过系统目录选择器授权的输出目录展示名；为空表示未授权，使用默认目录。 */
    val outputTreeName: String = "",
    /** 主菜单代理入口的状态摘要。 */
    val proxyEnabled: Boolean = false,
    val proxyScope: ProxyScope = ProxyScope.PixivOnly,
    val proxyConfigCount: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val printerSettings: PrinterSettings,
    private val outputPathRepository: OutputPathRepository,
    proxySettingsRepository: ProxySettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            themeRepository.state.collect { themeState ->
                _state.update { it.copy(themeState = themeState) }
            }
        }
        viewModelScope.launch {
            printerSettings.paperWidthMm.collect { mm ->
                _state.update { it.copy(paperWidthMm = mm) }
            }
        }
        viewModelScope.launch {
            printerSettings.autoConnectEnabled.collect { enabled ->
                _state.update { it.copy(autoConnectEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            outputPathRepository.outputDirName.collect { name ->
                _state.update { it.copy(outputDirName = name) }
            }
        }
        viewModelScope.launch {
            outputPathRepository.outputTreeUri.collect { uri ->
                _state.update {
                    it.copy(
                        outputTreeName = if (uri.isNullOrBlank()) "" else outputPathRepository.outputTreeDisplayName(),
                    )
                }
            }
        }
        // 主菜单只显示代理状态摘要，真实开关/配置仍在独立代理页维护。
        viewModelScope.launch {
            proxySettingsRepository.state.collect { proxy ->
                _state.update {
                    it.copy(
                        proxyEnabled = proxy.preferences.enabled,
                        proxyScope = proxy.preferences.scope,
                        proxyConfigCount = proxy.configs.size,
                    )
                }
            }
        }
    }

    fun setOutputDirName(name: String) {
        viewModelScope.launch { outputPathRepository.setOutputDirName(name) }
    }

    /** 记录用户授权的产物输出目录（SAF 树 URI）。 */
    fun setOutputTree(uri: String) {
        viewModelScope.launch { outputPathRepository.setOutputTree(uri) }
    }

    /** 撤销输出目录授权，回到默认相册目录。 */
    fun clearOutputTree() {
        viewModelScope.launch { outputPathRepository.clearOutputTree() }
    }

    fun setSeedColor(argb: Long) {
        viewModelScope.launch { themeRepository.setSeedColor(argb) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setDarkTheme(enabled) }
    }

    fun setCornerStyle(style: CornerStyle) {
        viewModelScope.launch { themeRepository.setCornerStyle(style) }
    }

    fun setCornerStrength(strength: Float) {
        viewModelScope.launch { themeRepository.setCornerStrength(strength) }
    }

    fun setMotionPreset(preset: MotionPreset) {
        viewModelScope.launch { themeRepository.setMotionPreset(preset) }
    }

    fun setOutlineEnabled(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setOutlineEnabled(enabled) }
    }

    fun setOutlineWidth(widthDp: Float) {
        viewModelScope.launch { themeRepository.setOutlineWidth(widthDp) }
    }

    fun setOutlineColorMode(mode: OutlineColorMode) {
        viewModelScope.launch { themeRepository.setOutlineColorMode(mode) }
    }

    fun setOutlineArgb(argb: Long) {
        viewModelScope.launch { themeRepository.setOutlineArgb(argb) }
    }

    fun setOutlineAlpha(alpha: Float) {
        viewModelScope.launch { themeRepository.setOutlineAlpha(alpha) }
    }

    fun setSnowfallEnabled(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setSnowfallEnabled(enabled) }
    }

    fun setParticleType(type: ParticleType) {
        viewModelScope.launch { themeRepository.setParticleType(type) }
    }

    fun setParticleSpeed(speed: Float) {
        viewModelScope.launch { themeRepository.setParticleSpeed(speed) }
    }

    fun setBackgroundUri(uri: String?) {
        viewModelScope.launch { themeRepository.setBackgroundUri(uri) }
    }

    fun setBackgroundAlpha(alpha: Float) {
        viewModelScope.launch { themeRepository.setBackgroundAlpha(alpha) }
    }

    fun setSurfaceAlpha(alpha: Float) {
        viewModelScope.launch { themeRepository.setSurfaceAlpha(alpha) }
    }

    fun setBackgroundBlur(blurDp: Float) {
        viewModelScope.launch { themeRepository.setBackgroundBlur(blurDp) }
    }

    /** 切换液态玻璃实现方式（C++ 原生覆盖层 / Kotlin Compose）。 */
    fun setLiquidGlassRenderer(renderer: LiquidGlassRenderer) {
        viewModelScope.launch { themeRepository.setLiquidGlassRenderer(renderer) }
    }

    fun setLiquidGlassControlsEnabled(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setLiquidGlassControlsEnabled(enabled) }
    }

    fun setLiquidGlassNavbarEnabled(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setLiquidGlassNavbarEnabled(enabled) }
    }

    fun setLiquidGlassBlur(blurDp: Float) {
        viewModelScope.launch { themeRepository.setLiquidGlassBlur(blurDp) }
    }

    fun setLiquidGlassLensHeight(lensHeightDp: Float) {
        viewModelScope.launch { themeRepository.setLiquidGlassLensHeight(lensHeightDp) }
    }

    fun setLiquidGlassLensAmount(lensAmountDp: Float) {
        viewModelScope.launch { themeRepository.setLiquidGlassLensAmount(lensAmountDp) }
    }

    fun setLiquidGlassSpectral(spectral: Float) {
        viewModelScope.launch { themeRepository.setLiquidGlassSpectral(spectral) }
    }

    fun setLiquidGlassTintAlpha(alpha: Float) {
        viewModelScope.launch { themeRepository.setLiquidGlassTintAlpha(alpha) }
    }

    fun setLiquidGlassShadowOn(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setLiquidGlassShadowOn(enabled) }
    }

    fun setLiquidGlassGlassOn(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setLiquidGlassGlassOn(enabled) }
    }

    fun setLiquidGlassContourEdge(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setLiquidGlassContourEdge(enabled) }
    }

    fun setLoadingAnimationStyle(style: LoadingAnimationStyle) {
        viewModelScope.launch { themeRepository.setLoadingAnimationStyle(style) }
    }

    fun setLoadingParticleCount(count: Int) {
        viewModelScope.launch { themeRepository.setLoadingParticleCount(count) }
    }

    fun setLoadingTrailSpan(span: Float) {
        viewModelScope.launch { themeRepository.setLoadingTrailSpan(span) }
    }

    fun setLoadingDurationMs(durationMs: Int) {
        viewModelScope.launch { themeRepository.setLoadingDurationMs(durationMs) }
    }

    fun setLoadingPulseDurationMs(durationMs: Int) {
        viewModelScope.launch { themeRepository.setLoadingPulseDurationMs(durationMs) }
    }

    fun setLoadingRotationDurationMs(durationMs: Int) {
        viewModelScope.launch { themeRepository.setLoadingRotationDurationMs(durationMs) }
    }

    fun setLoadingStrokeWidth(width: Float) {
        viewModelScope.launch { themeRepository.setLoadingStrokeWidth(width) }
    }

    fun setPaperWidthMm(mm: Int) {
        viewModelScope.launch { printerSettings.setPaperWidthMm(mm) }
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        viewModelScope.launch { printerSettings.setAutoConnectEnabled(enabled) }
    }
}
