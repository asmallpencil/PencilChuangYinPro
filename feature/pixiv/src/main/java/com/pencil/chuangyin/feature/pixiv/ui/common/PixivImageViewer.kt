package com.pencil.chuangyin.feature.pixiv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.LocalImageLoader
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.resources.R as ResR

/**
 * 全屏图片查看器（开源项目：panpf/zoomimage）。
 *
 * 替代此前的自研查看器：双指捏合缩放、双击放大、缩放态单指拖拽、惯性滑动、
 * 超大图子采样（避免 OOM）全部由 ZoomImage 负责。缩放态下 ZoomImage 只在
 * 「内容可继续滚动」时接管拖动，未放大时水平拖动自然交给 [HorizontalPager]，
 * 因此左右翻页与缩放手势可以共存。
 *
 * @param models 待查看的图片（URL / File / ImageRequest 均可）。
 * @param title 顶栏标题。
 * @param actions 顶栏右侧的附加操作（分享 / 删除等），关闭按钮固定在最右。
 */
@Composable
fun PixivImageViewerDialog(
    models: List<Any?>,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    contentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (models.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, models.lastIndex),
    ) { models.size }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        horizontal = dimensionResource(ResR.dimen.dimen_global_padding_s),
                        vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                actions()
                AppIconButton(
                    onClick = onDismiss,
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(ResR.string.pixiv_gallery_close_preview),
                    tint = Color.White,
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    PixivZoomableImage(
                        model = models[page],
                        contentDescription = contentDescription,
                    )
                }
                if (models.size > 1) {
                    AppText(
                        text = stringResource(
                            ResR.string.pixiv_browser_find_result,
                            pagerState.currentPage + 1,
                            models.size,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        align = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_m))
                            .clip(MaterialTheme.shapes.small)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(
                                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                            ),
                    )
                }
            }
        }
    }
}

/** 单张可缩放图片（ZoomImage）：供全屏查看器与详情页全屏浏览共用。 */
@Composable
fun PixivZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalImageLoader.current,
) {
    var state by remember(model) { mutableStateOf<AsyncImagePainter.State?>(null) }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CoilZoomAsyncImage(
            model = model,
            contentDescription = contentDescription,
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            // 全屏单图不需要滚动条：翻页条由外层 Pager 的分页指示器承担
            scrollBar = null,
            onState = { state = it },
        )
        when (state) {
            // 纯黑底上的加载态：白色 morphing 指示器
            is AsyncImagePainter.State.Loading -> AppLoadingIndicator(color = Color.White)
            is AsyncImagePainter.State.Error -> Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
            )
            else -> Unit
        }
    }
}
