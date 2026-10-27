package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.pixiv.PixivIllust
import com.pencil.chuangyin.desktop.support.rememberRemoteImage
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import com.pencil.chuangyin.desktop.ui.motion.DesktopLoadingIndicator

/** 圆形头像（带占位）。 */
@Composable
fun PixivAvatar(url: String?, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val bitmap = rememberRemoteImage(url)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 作品卡片 —— 与安卓端 `IllustCard` 逐项对齐：
 * 封面比例由作品 width/height 计算并夹取到 0.62..1.6，卡片底色 surfaceVariant，
 * 收藏角标在右上、多页角标在右下，底部只保留两行标题。
 */
@Composable
fun IllustCard(
    item: PixivIllust,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val cover = rememberRemoteImage(item.coverUrl)
    val ratio = if (item.width > 0 && item.height > 0) {
        (item.width.toFloat() / item.height.toFloat()).coerceIn(0.62f, 1.6f)
    } else {
        0.72f
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                DesktopLoadingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    size = 28.dp,
                )
            }
            if (item.isBookmarked) {
                BadgeBox(Modifier.align(Alignment.TopEnd)) {
                    AppIconView(
                        icon = AppIcon.Bookmark,
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (item.pageCount > 1) {
                BadgeBox(Modifier.align(Alignment.BottomEnd)) {
                    AppIconView(icon = AppIcon.PhotoLibrary, size = 16.dp, tint = Color.White)
                }
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/** 封面角标底座（对齐安卓端 BadgeBox）。 */
@Composable
private fun BadgeBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 加载中占位。 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DesktopLoadingIndicator(
            color = MaterialTheme.colorScheme.primary,
            size = 36.dp,
        )
    }
}

/** 错误占位（含重试）。 */
@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AppEmptyState(
            icon = AppIcon.Refresh,
            title = "加载失败",
            description = message.take(160),
            action = { AppPrimaryButton(text = "重试", onClick = onRetry) },
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

/** 分隔线。 */
@Composable
fun HLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .size(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

internal val TransparentColor = Color.Transparent
