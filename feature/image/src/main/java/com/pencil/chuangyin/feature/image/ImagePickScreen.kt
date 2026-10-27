package com.pencil.chuangyin.feature.image

import android.graphics.Bitmap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.ui.motion.animateGridItemMotion
import com.pencil.chuangyin.core.ui.motion.pressScale
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.resources.R as ResR

/**
 * 通用图片选择页：PDF 页面挑选与压缩包图片挑选共用。
 *
 * 接口驱动：条目用 [PickItem] 抽象（页码/路径由 UI 渲染文案），缩略图由调用方
 * 经 [onEnsureThumbnail] 懒加载回填；选择状态完全受控。后续拍照/其它来源的
 * 多选挑选也可复用本页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePickScreen(
    pick: PickUiState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onConfirm: () -> Unit,
    onEnsureThumbnail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allSelected = pick.items.isNotEmpty() && pick.items.all { it.key in pick.selected }
    val glassActive = rememberLiquidGlassActive()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    AppText(
                        text = stringResource(
                            when (pick.kind) {
                                PickKind.PDF -> R.string.image_pick_pdf_title
                                PickKind.ARCHIVE -> R.string.image_pick_archive_title
                                PickKind.TRANSFER -> R.string.image_pick_transfer_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    AppIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(ResR.string.label_back),
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
        bottomBar = {
            Surface(
                modifier = Modifier.nativeGlassSurface(
                    shape = RoundedCornerShape(0.dp),
                    enabled = glassActive,
                    fallbackColor = MaterialTheme.colorScheme.surface,
                ),
                color = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTextButton(
                        text = stringResource(
                            if (allSelected) R.string.image_pick_deselect_all else R.string.image_pick_select_all,
                        ),
                        onClick = onToggleSelectAll,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AppText(
                        text = stringResource(
                            R.string.image_pick_selected_count,
                            pick.selected.size,
                            pick.items.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AppOutlinedButton(
                        text = stringResource(R.string.image_pick_add),
                        onClick = onConfirm,
                        enabled = pick.selected.isNotEmpty(),
                    )
                }
            }
        },
    ) { innerPadding ->
        val motion = rememberAppMotion()
        LazyVerticalGrid(
            columns = GridCells.Fixed(PICK_GRID_COLUMNS),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(dimensionResource(ResR.dimen.dimen_global_padding_m)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
        ) {
            items(items = pick.items, key = PickItem::key) { item ->
                PickCell(
                    item = item,
                    selected = item.key in pick.selected,
                    thumbnail = pick.thumbnails[item.key],
                    onToggle = { onToggle(item.key) },
                    onEnsureThumbnail = { onEnsureThumbnail(item.key) },
                    modifier = animateGridItemMotion(motion),
                )
            }
        }
    }
}

@Composable
private fun PickCell(
    item: PickItem,
    selected: Boolean,
    thumbnail: Bitmap?,
    onToggle: () -> Unit,
    onEnsureThumbnail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(item.key) { onEnsureThumbnail() }
    val motion = rememberAppMotion()
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_s)))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .pressScale(interactionSource)
                .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AppLoadingIndicator(
                    size = dimensionResource(ResR.dimen.dimen_global_touch_target) * 0.6f,
                )
            }
            // 选中角标（截图同款：右上角紫底白勾）；勾选时从小弹到完整大小。
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = scaleIn(initialScale = 0.4f, animationSpec = motion.bouncySpec()) +
                    fadeIn(animationSpec = motion.effectFastSpec()),
                exit = scaleOut(targetScale = 0.6f, animationSpec = motion.spatialFastSpec()) +
                    fadeOut(animationSpec = motion.effectFastSpec()),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
            ) {
                Box(
                    modifier = Modifier
                        .size(dimensionResource(ResR.dimen.dimen_global_touch_target) * 0.6f)
                        .clip(RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_s)))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.image_pick_content_desc_selected),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        AppText(
            text = pickItemLabel(item),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s) / 2),
        )
    }
}

@Composable
private fun pickItemLabel(item: PickItem): String = when (item) {
    is PickItem.PdfPage -> stringResource(R.string.image_pick_page_label, item.pageIndex + 1)
    is PickItem.ArchiveImage -> item.path
    is PickItem.Transferred -> stringResource(R.string.image_pick_page_label, item.fileIndex + 1)
}

private const val PICK_GRID_COLUMNS = 3

@Preview(showBackground = true)
@Composable
private fun ImagePickScreenPreview() {
    ImagePickScreen(
        pick = PickUiState(
            kind = PickKind.PDF,
            items = (0 until 6).map(PickItem::PdfPage),
            selected = setOf("pdf_0", "pdf_1"),
        ),
        onBack = {},
        onToggle = {},
        onToggleSelectAll = {},
        onConfirm = {},
        onEnsureThumbnail = {},
    )
}
