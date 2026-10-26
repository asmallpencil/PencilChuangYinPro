package com.pencil.chuangyin.feature.pixiv.ui.tools

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivSubScaffold
import com.pencil.chuangyin.resources.R as ResR

/** 以图搜源引擎（GET URL 均支持 url 参数预填图片地址）。 */
private enum class SourceEngine(
    val baseUrl: String,
    val withImageUrl: (String) -> String,
) {
    SAUCENAO(
        baseUrl = "https://saucenao.com/",
        withImageUrl = { image -> "https://saucenao.com/search.php?url=${Uri.encode(image)}" },
    ),
    ASCII2D(
        baseUrl = "https://ascii2d.net/",
        withImageUrl = { image -> "https://ascii2d.net/search/url/${Uri.encode(image)}" },
    ),
    GOOGLE_LENS(
        baseUrl = "https://lens.google/",
        withImageUrl = { image ->
            "https://lens.google.com/uploadbyurl?url=${Uri.encode(image)}"
        },
    ),
    TINEYE(
        baseUrl = "https://tineye.com/",
        withImageUrl = { image -> "https://tineye.com/search?url=${Uri.encode(image)}" },
    ),
}

/**
 * 以图搜源：从作品详情进入时 [imageUrl] 为当前图地址，自动预填；
 * 从侧边栏进入时为空，打开引擎首页。点击用系统浏览器外跳。
 */
@Composable
fun PixivSourcesRoute(
    onBack: () -> Unit,
    imageUrl: String? = null,
) {
    val context = LocalContext.current
    PixivSubScaffold(title = stringResource(ResR.string.pixiv_sources_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(com.pencil.chuangyin.resources.R.dimen.dimen_global_padding_l)),
        ) {
            AppText(
                text = stringResource(
                    if (imageUrl.isNullOrBlank()) {
                        ResR.string.pixiv_sources_desc_no_image
                    } else {
                        ResR.string.pixiv_sources_desc
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(dimensionResource(com.pencil.chuangyin.resources.R.dimen.dimen_global_padding_m)))
            SourceEngine.entries.forEach { engine ->
                val label = when (engine) {
                    SourceEngine.SAUCENAO -> stringResource(ResR.string.pixiv_source_saucenao)
                    SourceEngine.ASCII2D -> stringResource(ResR.string.pixiv_source_ascii2d)
                    SourceEngine.GOOGLE_LENS -> stringResource(ResR.string.pixiv_source_google_lens)
                    SourceEngine.TINEYE -> stringResource(ResR.string.pixiv_source_tineye)
                }
                SourceRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    label = label,
                    url = engine.baseUrl,
                    onClick = {
                        val target = imageUrl
                            ?.takeIf { it.isNotBlank() }
                            ?.let { engine.withImageUrl(it) }
                            ?: engine.baseUrl
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(target)),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    icon: ImageVector,
    label: String,
    url: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = dimensionResource(com.pencil.chuangyin.resources.R.dimen.dimen_global_padding_m)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(dimensionResource(com.pencil.chuangyin.resources.R.dimen.dimen_pixiv_drawer_icon_size)),
            )
            AppText(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = dimensionResource(com.pencil.chuangyin.resources.R.dimen.dimen_global_padding_m)),
            )
        }
        AppText(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = dimensionResource(com.pencil.chuangyin.resources.R.dimen.dimen_pixiv_drawer_icon_size) +
                    dimensionResource(com.pencil.chuangyin.resources.R.dimen.dimen_global_padding_m),
                top = dimensionResource(com.pencil.chuangyin.resources.R.dimen.dimen_global_padding_s),
            ),
        )
    }
}
