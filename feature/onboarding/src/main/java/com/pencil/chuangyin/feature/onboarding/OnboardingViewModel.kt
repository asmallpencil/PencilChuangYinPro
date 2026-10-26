package com.pencil.chuangyin.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.theme.CornerStyle
import com.pencil.chuangyin.core.theme.MotionPreset
import com.pencil.chuangyin.core.theme.SeedOption
import com.pencil.chuangyin.core.theme.ThemeRepository
import com.pencil.chuangyin.core.theme.ThemeState
import com.pencil.chuangyin.feature.pixiv.core.auth.PixivAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val step: Int = 0,
    val totalSteps: Int = TOTAL_STEPS,
    val themeState: ThemeState = ThemeState.Default,
    /** P 站是否已登录（登录态持久化在 DataStore，重启免登录）。 */
    val isPixivLoggedIn: Boolean = false,
    /** 已登录的 P 站用户名（未登录为空串）。 */
    val pixivUserName: String = "",
) {
    val canGoNext: Boolean get() = step < totalSteps - 1
    val isLastStep: Boolean get() = step >= totalSteps - 1

    private companion object {
        const val TOTAL_STEPS = 7
    }
}

/**
 * 引导页 ViewModel：负责步骤推进、主题设置（写 DataStore）以及 P 站登录态订阅。
 * 登录 WebView 的授权码换 token 流程由 feature:pixiv 的 PixivLoginViewModel 负责，
 * 此处仅订阅 [PixivAuthRepository.account] 感知登录成功/已有登录态。
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    pixivAuthRepository: PixivAuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            themeRepository.state.collect { themeState ->
                _state.update { it.copy(themeState = themeState) }
            }
        }
        viewModelScope.launch {
            pixivAuthRepository.account.collect { account ->
                _state.update {
                    it.copy(
                        isPixivLoggedIn = account != null,
                        pixivUserName = account?.name.orEmpty(),
                    )
                }
            }
        }
    }

    fun next() {
        _state.update { if (it.canGoNext) it.copy(step = it.step + 1) else it }
    }

    fun previous() {
        _state.update { if (it.step > 0) it.copy(step = it.step - 1) else it }
    }

    fun setSeedColor(option: SeedOption) {
        viewModelScope.launch { themeRepository.setSeedColor(option.color) }
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

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setDarkTheme(enabled) }
    }

    fun setOutlineEnabled(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setOutlineEnabled(enabled) }
    }

    fun setSnowfallEnabled(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setSnowfallEnabled(enabled) }
    }
}