package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

/**
 * 以图搜源引擎（与安卓端 SourceEngine 完全一致：GET URL 均支持 url 参数预填图片地址）。
 * 桌面端改为用系统默认浏览器打开。
 */
private enum class SourceEngine(
    val label: String,
    val baseUrl: String,
    val withImageUrl: (String) -> String,
) {
    SAUCENAO(
        label = "SauceNAO（插画/P 站首选）",
        baseUrl = "https://saucenao.com/",
        withImageUrl = { image -> "https://saucenao.com/search.php?url=${encodeUrl(image)}" },
    ),
    ASCII2D(
        label = "ascii2d（二次元以图搜图）",
        baseUrl = "https://ascii2d.net/",
        withImageUrl = { image -> "https://ascii2d.net/search/url/${encodeUrl(image)}" },
    ),
    GOOGLE_LENS(
        label = "Google Lens",
        baseUrl = "https://lens.google/",
        withImageUrl = { image -> "https://lens.google.com/uploadbyurl?url=${encodeUrl(image)}" },
    ),
    TINEYE(
        label = "TinEye（全网反向搜索）",
        baseUrl = "https://tineye.com/",
        withImageUrl = { image -> "https://tineye.com/search?url=${encodeUrl(image)}" },
    ),
}

/** 对齐安卓端 Uri.encode：空格编码为 %20 而不是 +。 */
private fun encodeUrl(raw: String): String =
    runCatching { URLEncoder.encode(raw, "UTF-8").replace("+", "%20") }.getOrDefault(raw)

/** 用系统默认浏览器打开（与安卓端 ACTION_VIEW 行为对齐）。 */
private fun openExternal(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

/**
 * 以图搜源：从作品详情进入时 [imageUrl] 为当前图地址，自动预填；
 * 从侧边栏进入时为空，打开引擎首页。
 */
@Composable
fun PixivSourcesScreen(
    onBack: () -> Unit,
    imageUrl: String? = null,
) {
    PixivSubScaffold(title = "以图搜源", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                text = if (imageUrl.isNullOrBlank()) {
                    "选择搜索引擎，将用系统浏览器打开："
                } else {
                    "选择搜索引擎，将用系统浏览器打开并自动带入当前图片地址："
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            SourceEngine.entries.forEach { engine ->
                SourceRow(
                    label = engine.label,
                    url = engine.baseUrl,
                    onClick = {
                        val target = imageUrl
                            ?.takeIf { it.isNotBlank() }
                            ?.let { engine.withImageUrl(it) }
                            ?: engine.baseUrl
                        openExternal(target)
                    },
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    label: String,
    url: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconView(
                icon = AppIcon.Language,
                size = 24.dp,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            )
        }
        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 40.dp, top = 8.dp),
        )
    }
}
