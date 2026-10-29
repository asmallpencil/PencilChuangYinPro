package com.pencil.chuangyin.feature.pixiv.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil.ImageLoader
import coil.compose.LocalImageLoader
import com.pencil.chuangyin.core.ui.components.viewer.AppImageViewerDialog
import com.pencil.chuangyin.core.ui.components.viewer.AppZoomableImage

/**
 * 全屏图片查看器（P 站入口）。
 *
 * 实现已统一收敛到 `core:ui` 的 [AppImageViewerDialog]：
 * 缩放 / 双击 / 惯性拖动 / 超大图子采样沿用 ZoomImage，并叠加 M3 动效、液态玻璃工具条、
 * 悬浮翻页栏（屏幕下方 4/5 处）与「裁切 / 涂鸦 / 文字」编辑能力。
 * 此处仅保留 P 站原有调用签名，避免各入口重复接线。
 *
 * @param models 待查看的图片（URL / File / ImageRequest 均可）。
 * @param title 顶栏标题。
 * @param actions 顶栏右侧的附加操作（分享 / 打印等），关闭按钮固定在最右。
 * @param onPageChanged 当前页变化回调（分享当前页 / 页码指示等使用）。
 * @param onExportEdited 导出编辑后的图片；为空时保存按钮不显示。
 */
@Composable
fun PixivImageViewerDialog(
    models: List<Any?>,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    contentDescription: String? = null,
    onPageChanged: ((Int) -> Unit)? = null,
    onExportEdited: (suspend (index: Int, bitmap: Bitmap) -> Boolean)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    AppImageViewerDialog(
        models = models,
        title = title,
        onDismiss = onDismiss,
        modifier = modifier,
        initialPage = initialPage,
        contentDescription = contentDescription,
        onPageChanged = onPageChanged,
        onExportEdited = onExportEdited,
        actions = actions,
    )
}

/** 单张可缩放图片（ZoomImage）：供全屏查看器与详情页全屏浏览共用。 */
@Composable
fun PixivZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalImageLoader.current,
) {
    AppZoomableImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        imageLoader = imageLoader,
    )
}
