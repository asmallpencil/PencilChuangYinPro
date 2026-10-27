package com.pencil.chuangyin.feature.pixiv.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.pencil.chuangyin.core.ui.components.AppSkeletonBox

/**
 * P 站列表统一封面图：[rememberAsyncImagePainter] + 普通 [Image]。
 *
 * 替代各处滚动列表里的 SubcomposeAsyncImage——后者每张图都为 loading/error 槽
 * 执行 Subcompose，几十行同时出现时是进入列表/滚动时的主要掉帧源；
 * 本组件仅在 painter 状态翻转时重组一次。图片加载器经 LocalImageLoader 下发
 * （携带 pximg Referer），与整站其他图片一致。
 */
@Composable
fun PixivCoverImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /** 加载失败（或无图）时的占位图标；头像场景传 Person。 */
    errorIcon: ImageVector = Icons.Filled.BrokenImage,
) {
    val painter = rememberAsyncImagePainter(
        model = model,
        contentScale = contentScale,
    )
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
        when (painter.state) {
            // 图片占位不用转圈：整块 shimmer 更能表达"图片正在填充"，且不会在网格里
            // 几十个 spinner 同时转导致视觉噪音与掉帧。
            is AsyncImagePainter.State.Loading -> AppSkeletonBox(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
            )
            is AsyncImagePainter.State.Error,
            AsyncImagePainter.State.Empty -> Icon(
                imageVector = errorIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Unit
        }
    }
}

@Preview(showBackground = true, widthDp = 96, heightDp = 128)
@Composable
private fun PixivCoverImagePreview() {
    MaterialTheme {
        PixivCoverImage(model = null, contentDescription = null)
    }
}
