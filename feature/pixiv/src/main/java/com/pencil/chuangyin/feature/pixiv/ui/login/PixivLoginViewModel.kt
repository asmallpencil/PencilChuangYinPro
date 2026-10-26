package com.pencil.chuangyin.feature.pixiv.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.feature.pixiv.core.auth.PixivAuthRepository
import com.pencil.chuangyin.feature.pixiv.core.auth.Pkce
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PixivLoginUiState(
    val loginUrl: String,
    val loading: Boolean = false,
    val error: Boolean = false,
    val success: Boolean = false,
)

@HiltViewModel
class PixivLoginViewModel @Inject constructor(
    private val authRepository: PixivAuthRepository,
) : ViewModel() {

    private var verifier: String = Pkce.generateVerifier()

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<PixivLoginUiState> = _state.asStateFlow()

    private fun buildState(): PixivLoginUiState {
        val challenge = Pkce.challenge(verifier)
        return PixivLoginUiState(loginUrl = Pkce.loginUrl(challenge))
    }

    /** WebView 拦截到回调 code，换 token。 */
    fun onAuthCode(code: String) {
        if (_state.value.loading || _state.value.success) return
        _state.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            val result = runCatching { authRepository.loginWithCode(code = code, codeVerifier = verifier) }
            _state.update {
                if (result.isSuccess) {
                    it.copy(loading = false, success = true)
                } else {
                    // 失败后换新 PKCE，供 WebView 重新加载登录页。
                    verifier = Pkce.generateVerifier()
                    buildState().copy(error = true)
                }
            }
        }
    }

    fun errorShown() = _state.update { it.copy(error = false) }
}
