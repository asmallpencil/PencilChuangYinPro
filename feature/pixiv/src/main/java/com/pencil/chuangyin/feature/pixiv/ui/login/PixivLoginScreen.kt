package com.pencil.chuangyin.feature.pixiv.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.feature.pixiv.core.network.PixivConfig
import com.pencil.chuangyin.feature.pixiv.ui.PixivImageProvider
import com.pencil.chuangyin.resources.R as ResR

/**
 * P 站登录页：WebView 打开官方 App 登录页（PKCE S256），
 * 拦截 redirect_uri 回调取 code，交由 ViewModel 换 token。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixivLoginRoute(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PixivLoginViewModel = hiltViewModel(),
) {
    PixivImageProvider {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val loginFailed = stringResource(ResR.string.pixiv_login_failed)
        val glassActive = rememberLiquidGlassActive()

        LaunchedEffect(state.success) {
            if (state.success) onSuccess()
        }
        LaunchedEffect(state.error) {
            if (state.error) {
                snackbarHostState.showSnackbar(loginFailed)
                viewModel.errorShown()
            }
        }

        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { AppText(stringResource(ResR.string.pixiv_login_web_title)) },
                    navigationIcon = {
                        AppIconButton(
                            onClick = onBack,
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(ResR.string.label_back),
                            glass = false,
                        )
                    },
                    modifier = Modifier.nativeGlassSurface(
                        shape = RoundedCornerShape(0.dp),
                        enabled = glassActive,
                        fallbackColor = MaterialTheme.colorScheme.surface,
                    ),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // 换 PKCE 后重建 WebView，重新加载登录页。
                key(state.loginUrl) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                configureWebView(
                                    onPageStarted = { /* 可在此扩展页面级加载态 */ },
                                    onAuthCode = viewModel::onAuthCode,
                                )
                                loadUrl(state.loginUrl)
                            }
                        },
                    )
                }

                if (state.loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 原来是「spinner + 文案」同塞进 Box：二者都是 Box 子节点，会重叠。
                        // 这里改为竖向 Column，并换成 M3 Expressive 的 morphing 指示器。
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppLoadingIndicator()
                            AppText(
                                text = stringResource(ResR.string.pixiv_login_loading),
                                modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_l)),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureWebView(
    onPageStarted: (String) -> Unit,
    onAuthCode: (String) -> Unit,
) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        userAgentString = PixivConfig.USER_AGENT
    }
    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            url?.let(onPageStarted)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            return handleCallback(url, onAuthCode)
        }

        @Deprecated("旧系统回调")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return handleCallback(url ?: return false, onAuthCode)
        }
    }
}

private fun handleCallback(url: String, onAuthCode: (String) -> Unit): Boolean {
    if (!url.startsWith(PixivConfig.REDIRECT_URI)) return false
    val code = Uri.parse(url).getQueryParameter("code")
    if (!code.isNullOrBlank()) onAuthCode(code)
    return true
}
