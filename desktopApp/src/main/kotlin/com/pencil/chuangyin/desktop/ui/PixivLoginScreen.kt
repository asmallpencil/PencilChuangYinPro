package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.glass.GlassCard
import com.pencil.chuangyin.desktop.pixiv.PixivGraph
import com.pencil.chuangyin.desktop.pixiv.Pkce
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlinx.coroutines.launch

/**
 * P 站登录（桌面端）——协议与安卓端一致：PKCE(S256) + pixiv-android client。
 *
 * 桌面无法接管 `app-api.pixiv.net/...callback` 的自定义 Scheme，因此流程是：
 * 浏览器完成授权后，把地址栏里的回调 URL 整段（或其中的 `code=` 值）粘回来。
 */
@Composable
fun PixivLoginScreen(onBack: () -> Unit, onLoggedIn: () -> Unit) {
    val client = PixivGraph.client
    val session = PixivGraph.session
    val scope = rememberCoroutineScope()

    val verifier = remember { Pkce.newVerifier() }
    val loginUrl = remember(verifier) { client.loginUrl(verifier) }

    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun openBrowser() {
        runCatching { Desktop.getDesktop().browse(URI(loginUrl)) }
            .onFailure { error = "无法打开浏览器，请手动复制链接" }
    }

    fun copyLink() {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(loginUrl), null)
        }.onFailure { error = "复制失败，请手动选中链接复制" }
    }

    fun submit() {
        val raw = input.trim()
        if (raw.isEmpty() || busy) return
        val code = extractCode(raw)
        if (code.isNullOrBlank()) {
            error = "没有找到 code 参数，请粘贴完整的回调地址"
            return
        }
        busy = true
        error = null
        scope.launch {
            val account = runCatching { client.exchangeCode(code, verifier) }.getOrNull()
            if (account == null) {
                error = "换取令牌失败，请重新授权后再试"
            } else {
                session.save(account)
                onLoggedIn()
            }
            busy = false
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = "登录 P 站",
                navigationIcon = {
                    AppIconButton(icon = AppIcon.Back, contentDescription = "返回", onClick = onBack)
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 文案对齐安卓 pixiv_login_browser_hint；桌面无内嵌 WebView，故补充两步操作说明。
                Text(
                    text = "在打开的页面中登录 P 站账号，完成授权后将自动返回",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "1. 用浏览器打开授权页并登录 P 站",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppPrimaryButton(text = "打开浏览器", onClick = { openBrowser() })
                        AppSecondaryButton(text = "复制链接", onClick = { copyLink() })
                    }

                    Text(
                        text = "2. 授权成功后页面会跳转到 app-api.pixiv.net 的回调地址，" +
                            "把地址栏里的整段链接复制回来",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 20.dp),
                    )

                    AppTextField(
                        value = input,
                        onValueChange = { input = it; error = null },
                        placeholder = "粘贴回调地址或 code 值",
                        singleLine = false,
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        onDone = { submit() },
                    )

                    if (error != null) {
                        Text(
                            text = error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppPrimaryButton(
                            text = if (busy) "正在登录…" else "完成登录",
                            enabled = !busy && input.isNotBlank(),
                            onClick = { submit() },
                            modifier = Modifier.weight(1f),
                        )
                        if (busy) {
                            Box(
                                modifier = Modifier.widthIn(min = 44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "令牌只保存在本机（java.util.prefs），不会上传到任何第三方服务",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 从粘贴内容里取出授权码：整段 URL 取 `code=`，否则当作裸 code。 */
private fun extractCode(raw: String): String? {
    val marker = "code="
    val index = raw.indexOf(marker)
    if (index < 0) return raw.takeIf { it.isNotBlank() && !it.contains("://") }
    val rest = raw.substring(index + marker.length)
    return rest.substringBefore('&').takeIf { it.isNotBlank() }
}
