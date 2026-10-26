package com.pencil.chuangyin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.theme.CornerStyle
import com.pencil.chuangyin.core.theme.LiquidGlassRenderer
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
    val outputDirName: String = OUTPUT_DIR_DEFAULT,
    /** 用户自定义的完整产物输出路径；为空表示使用默认目录。 */
    val outputPath: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val printerSettings: PrinterSettings,
    private val outputPathRepository: OutputPathRepository,
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
            outputPathRepository.outputDirName.collect { name ->
                _state.update { it.copy(outputDirName = name) }
            }
        }
        viewModelScope.launch {
            outputPathRepository.outputPath.collect { path ->
                _state.update { it.copy(outputPath = path) }
            }
        }
    }

    fun setOutputDirName(name: String) {
        viewModelScope.launch { outputPathRepository.setOutputDirName(name) }
    }

    /** 设置自定义完整产物输出路径；清空表示回到默认目录。 */
    fun setOutputPath(path: String) {
        viewModelScope.launch { outputPathRepository.setOutputPath(path) }
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

    fun setPaperWidthMm(mm: Int) {
        viewModelScope.launch { printerSettings.setPaperWidthMm(mm) }
    }
}
