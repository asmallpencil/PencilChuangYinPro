package com.pencil.chuangyin.feature.pixiv.ui.tools

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppLiquidDialog
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.reader.AppReader
import com.pencil.chuangyin.core.ui.components.reader.ReaderMode
import com.pencil.chuangyin.core.ui.motion.animateGridItemMotion
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.pixiv.data.PixivSavedCategory
import com.pencil.chuangyin.feature.pixiv.data.PixivSavedImage
import com.pencil.chuangyin.feature.pixiv.data.PixivSavedKind
import com.pencil.chuangyin.feature.pixiv.ui.PixivImageProvider
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenEmpty
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivCoverImage
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivImageViewerDialog
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivSubScaffold
import com.pencil.chuangyin.feature.pixiv.ui.common.formatBytes
import com.pencil.chuangyin.feature.pixiv.ui.drawer.PixivPrintTarget
import com.pencil.chuangyin.feature.pixiv.ui.drawer.PixivToolsViewModel
import com.pencil.chuangyin.resources.R as ResR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 图片管理保存类型筛选（手动下载 / 打印缓存）。 */
private enum class GalleryFilter { ALL, DOWNLOAD, PRINT_CACHE }

/** 内容分类筛选（与保存类型正交）。 */
private enum class GalleryCategory { ALL, NOVEL, IMAGE }

/** 打开「已保存内容」的入口：决定默认分类与可打印范围。 */
enum class PixivGalleryEntry { ALL, NOVEL, IMAGE }

/** 打印被拒绝的原因（混选 / 内容无法展开），用于区分提示文案。 */
private enum class PrintBlockReason { MIXED, EMPTY }

/** 入口对应的默认分类：文本打印入口落小说、图片打印入口落图片、侧边栏入口保持全部。 */
private val PixivGalleryEntry.defaultCategory: GalleryCategory
    get() = when (this) {
        PixivGalleryEntry.ALL -> GalleryCategory.ALL
        PixivGalleryEntry.NOVEL -> GalleryCategory.NOVEL
        PixivGalleryEntry.IMAGE -> GalleryCategory.IMAGE
    }

/** 当前入口下该卡片是否可打印：文本入口仅小说，图片入口仅图片（含压缩包/多图）。 */
private fun PixivSavedImage.printableUnder(entry: PixivGalleryEntry): Boolean = when (entry) {
    PixivGalleryEntry.ALL -> true
    PixivGalleryEntry.NOVEL -> kind == PixivSavedKind.NOVEL
    PixivGalleryEntry.IMAGE -> kind != PixivSavedKind.NOVEL
}

private fun PixivSavedImage.matchesCategory(category: GalleryCategory): Boolean = when (category) {
    GalleryCategory.ALL -> true
    GalleryCategory.NOVEL -> kind == PixivSavedKind.NOVEL
    GalleryCategory.IMAGE -> kind != PixivSavedKind.NOVEL
}

/**
 * 「已保存内容」页。
 *
 * 交互模型：
 * - 普通态：点击卡片进入查看页；**长按任意卡片进入多选模式**并选中该卡片；
 * - 多选态：点击卡片切换选中，左上角显示选择序号（1、2、3…，序号即打印顺序），
 *   顶栏左侧为「退出多选 / 一键全选」，右侧为「打印 / 删除所选」；
 * - 打印：图片（含压缩包、散图聚合）按序号展开全部页后交给图片打印页；
 *   纯小说合并正文后交给文本打印页；**图文混选时打印按钮禁用**。
 *
 * 筛选：上排「保存类型」（全部 / 手动下载 / 打印缓存）+ 下排紧凑「分类」（全部 / 小说 / 图片）。
 * 入口（[entry]）决定默认分类与可打印范围：从文本打印进入默认小说且图片卡片置灰，
 * 从图片打印进入默认图片且小说卡片置灰，侧边栏进入不限。
 *
 * 查看页（图片 / 多图 / 小说）不再提供删除，改为「分享 + 打印」，打印走同一条正常打印流程。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PixivGalleryRoute(
    onBack: () -> Unit,
    onOpenPrintImages: () -> Unit,
    onOpenPrintText: () -> Unit,
    entry: PixivGalleryEntry = PixivGalleryEntry.ALL,
    viewModel: PixivToolsViewModel = hiltViewModel(),
) {
    val images by viewModel.savedImages.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(GalleryFilter.ALL) }
    var category by remember(entry) { mutableStateOf(entry.defaultCategory) }
    var selected by remember { mutableStateOf<PixivSavedImage?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var printing by remember { mutableStateOf(false) }
    var printBlocked by remember { mutableStateOf<PrintBlockReason?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 每次进入重新扫描本地文件（含打印缓存），保证列表实时。
    LaunchedEffect(Unit) { viewModel.refreshSavedImages() }

    val filtered = remember(images, filter, category) {
        val target = when (filter) {
            GalleryFilter.ALL -> null
            GalleryFilter.DOWNLOAD -> PixivSavedCategory.DOWNLOAD
            GalleryFilter.PRINT_CACHE -> PixivSavedCategory.PRINT_CACHE
        }
        images.filter { item ->
            (target == null || item.category == target) && item.matchesCategory(category)
        }
    }

    // 当前入口允许打印的可见卡片（下拉「分类」只影响展示，入口约束决定能否选中）。
    val printableVisible = remember(filtered, entry) {
        filtered.filter { it.printableUnder(entry) }
    }

    // 选择键 = 文件绝对路径（同一作品的分页会有各自路径，天然唯一）。
    // 用有序列表保存，序号即用户选择先后，也就是打印顺序。
    val selectedItems = remember(images, selection) {
        val byKey = images.associateBy { it.file.absolutePath }
        selection.mapNotNull { byKey[it] }
    }
    val canPrint = selectedItems.isNotEmpty() &&
        selectedItems.all { it.printableUnder(entry) } &&
        viewModel.canPrintSelection(selectedItems)

    fun exitSelection() {
        selectionMode = false
        selection = emptyList()
    }

    fun toggle(item: PixivSavedImage) {
        if (!item.printableUnder(entry)) return
        val key = item.file.absolutePath
        selection = if (key in selection) selection - key else selection + key
    }

    fun printItems(items: List<PixivSavedImage>) {
        if (printing || items.isEmpty()) return
        if (!viewModel.canPrintSelection(items)) {
            printBlocked = PrintBlockReason.MIXED
            return
        }
        printing = true
        scope.launch {
            val target = runCatching { viewModel.preparePrint(items) }
                .getOrDefault(PixivPrintTarget.NONE)
            printing = false
            when (target) {
                PixivPrintTarget.IMAGE -> onOpenPrintImages()
                PixivPrintTarget.TEXT -> onOpenPrintText()
                PixivPrintTarget.NONE -> printBlocked = PrintBlockReason.EMPTY
            }
        }
    }

    PixivImageProvider {
        PixivSubScaffold(
            title = if (selectionMode) {
                stringResource(ResR.string.pixiv_gallery_selected_count, selectedItems.size)
            } else {
                stringResource(ResR.string.pixiv_gallery_title)
            },
            onBack = onBack,
            navigationIcon = if (selectionMode) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconButton(
                            onClick = { exitSelection() },
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(ResR.string.pixiv_gallery_exit_selection),
                            glass = false,
                        )
                        AppIconButton(
                            onClick = {
                                val visibleKeys = printableVisible.map { it.file.absolutePath }
                                selection = if (visibleKeys.isNotEmpty() &&
                                    selection.toSet() == visibleKeys.toSet()
                                ) {
                                    emptyList()
                                } else {
                                    visibleKeys
                                }
                            },
                            imageVector = Icons.Filled.SelectAll,
                            contentDescription = stringResource(
                                if (printableVisible.isNotEmpty() &&
                                    selection.toSet() == printableVisible.map { it.file.absolutePath }.toSet()
                                ) {
                                    ResR.string.pixiv_gallery_select_none
                                } else {
                                    ResR.string.pixiv_gallery_select_all
                                },
                            ),
                            enabled = printableVisible.isNotEmpty(),
                            glass = false,
                        )
                    }
                }
            } else {
                null
            },
            actions = {
                if (selectionMode) {
                    AppIconButton(
                        onClick = { printItems(selectedItems) },
                        imageVector = Icons.Filled.Print,
                        contentDescription = stringResource(
                            if (canPrint) ResR.string.pixiv_gallery_print_selected
                            else ResR.string.pixiv_gallery_print_mixed_disabled,
                        ),
                        enabled = canPrint,
                    )
                    AppIconButton(
                        onClick = { confirmDelete = true },
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = stringResource(ResR.string.pixiv_gallery_delete_selected),
                        enabled = selectedItems.isNotEmpty(),
                    )
                }
            },
        ) {
            if (images.isEmpty()) {
                FullScreenEmpty(message = stringResource(ResR.string.pixiv_gallery_empty))
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l),
                            vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                        ),
                    ) {
                        // 上排：保存类型（全部 / 手动下载 / 打印缓存）
                        AppText(
                            text = stringResource(ResR.string.pixiv_gallery_save_type),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                        AppSegmentedRow(
                            options = listOf(
                                stringResource(ResR.string.pixiv_gallery_filter_all),
                                stringResource(ResR.string.pixiv_gallery_filter_download),
                                stringResource(ResR.string.pixiv_gallery_filter_print_cache),
                            ),
                            selectedIndex = filter.ordinal,
                            onSelect = { index ->
                                filter = when (index) {
                                    1 -> GalleryFilter.DOWNLOAD
                                    2 -> GalleryFilter.PRINT_CACHE
                                    else -> GalleryFilter.ALL
                                }
                            },
                        )
                        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                        // 下排：分类（全部 / 小说 / 图片），紧凑尺寸的次级筛选
                        AppText(
                            text = stringResource(ResR.string.pixiv_gallery_category_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                        AppSegmentedRow(
                            options = listOf(
                                stringResource(ResR.string.pixiv_gallery_category_all),
                                stringResource(ResR.string.pixiv_gallery_category_novel),
                                stringResource(ResR.string.pixiv_gallery_category_image),
                            ),
                            selectedIndex = category.ordinal,
                            onSelect = { index ->
                                category = when (index) {
                                    1 -> GalleryCategory.NOVEL
                                    2 -> GalleryCategory.IMAGE
                                    else -> GalleryCategory.ALL
                                }
                            },
                            compact = true,
                        )
                    }
                    if (filtered.isEmpty()) {
                        FullScreenEmpty(message = stringResource(ResR.string.pixiv_gallery_empty_filtered))
                    } else {
                        val motion = rememberAppMotion()
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(GALLERY_COLUMNS),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                dimensionResource(ResR.dimen.dimen_pixiv_grid_padding),
                            ),
                            horizontalArrangement = Arrangement.spacedBy(
                                dimensionResource(ResR.dimen.dimen_pixiv_grid_spacing),
                            ),
                            verticalArrangement = Arrangement.spacedBy(
                                dimensionResource(ResR.dimen.dimen_pixiv_grid_spacing),
                            ),
                        ) {
                            items(filtered.size, key = { filtered[it].file.absolutePath }) { index ->
                                val item = filtered[index]
                                val key = item.file.absolutePath
                                val enabled = item.printableUnder(entry)
                                GalleryTile(
                                    image = item,
                                    selectionMode = selectionMode,
                                    selectionIndex = selection.indexOf(key).takeIf { it >= 0 },
                                    enabled = enabled,
                                    onClick = {
                                        if (selectionMode) toggle(item) else selected = item
                                    },
                                    onLongClick = {
                                        if (selectionMode) {
                                            toggle(item)
                                        } else {
                                            selectionMode = true
                                            selection = listOf(key)
                                        }
                                    },
                                    loadCover = viewModel::archiveCover,
                                    modifier = animateGridItemMotion(motion),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val detail = selected
    if (detail != null) {
        val dismiss = { selected = null }
        when (detail.kind) {
            PixivSavedKind.ARCHIVE, PixivSavedKind.MULTI_IMAGE -> GalleryMultiPageDialog(
                image = detail,
                onDismiss = dismiss,
                onSharePage = { file -> shareSavedFile(context, file) },
                onPrint = {
                    selected = null
                    printItems(listOf(detail))
                },
                viewModel = viewModel,
            )
            PixivSavedKind.NOVEL -> GalleryNovelDialog(
                image = detail,
                onDismiss = dismiss,
                onShare = { shareSavedFile(context, detail.file) },
                onPrint = {
                    selected = null
                    printItems(listOf(detail))
                },
                viewModel = viewModel,
            )
            PixivSavedKind.IMAGE -> GalleryImageDialog(
                image = detail,
                onDismiss = dismiss,
                onShare = { shareSavedFile(context, detail.file) },
                onPrint = {
                    selected = null
                    printItems(listOf(detail))
                },
                viewModel = viewModel,
            )
        }
    }

    if (confirmDelete) {
        AppLiquidDialog(
            onDismissRequest = { confirmDelete = false },
            title = stringResource(ResR.string.pixiv_gallery_delete_selected),
            message = stringResource(
                ResR.string.pixiv_gallery_delete_selected_confirm,
                selectedItems.size,
            ),
            confirmLabel = stringResource(ResR.string.action_confirm),
            onConfirm = {
                confirmDelete = false
                val removing = selectedItems
                exitSelection()
                viewModel.deleteImages(removing)
            },
            dismissLabel = stringResource(ResR.string.action_cancel),
        )
    }

    val blocked = printBlocked
    if (blocked != null) {
        AppLiquidDialog(
            onDismissRequest = { printBlocked = null },
            title = stringResource(ResR.string.pixiv_gallery_print_action),
            message = stringResource(
                when (blocked) {
                    PrintBlockReason.MIXED -> ResR.string.pixiv_gallery_print_mixed_disabled
                    PrintBlockReason.EMPTY -> ResR.string.pixiv_gallery_print_empty
                },
            ),
            confirmLabel = stringResource(ResR.string.action_confirm),
            onConfirm = { printBlocked = null },
        )
    }

    if (printing) {
        // 准备打印内容（解压 / 复制 / 合并）期间封锁交互，避免重复触发。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppLoadingIndicator(color = Color.White)
                AppText(
                    text = stringResource(ResR.string.pixiv_gallery_print_preparing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_m)),
                )
            }
        }
    }
}

/** 图片管理网格 tile：按内容类型（图片 / 压缩包 / 小说）区分展示，多选态叠加序号徽标。 */
@Composable
private fun GalleryTile(
    image: PixivSavedImage,
    selectionMode: Boolean,
    selectionIndex: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    loadCover: suspend (PixivSavedImage) -> File?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(dimensionResource(ResR.dimen.dimen_pixiv_related_card_width))
            .alpha(if (enabled) 1f else DISABLED_TILE_ALPHA)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        when (image.kind) {
            PixivSavedKind.IMAGE -> PixivCoverImage(
                model = image.file,
                contentDescription = image.file.name,
                modifier = Modifier.fillMaxSize(),
            )
            PixivSavedKind.ARCHIVE, PixivSavedKind.MULTI_IMAGE -> {
                // 多图以首张图为封面，右下角展示「多图」角标（点击后左右滑切换预览）。
                val cover by produceState<File?>(initialValue = null, image.illustId) {
                    value = loadCover(image)
                }
                val coverFile = cover
                if (coverFile != null) {
                    PixivCoverImage(
                        model = coverFile,
                        contentDescription = image.file.name,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Collections,
                            contentDescription = stringResource(ResR.string.pixiv_gallery_archive),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_pixiv_drawer_icon_size)),
                        )
                    }
                }
                AppText(
                    text = stringResource(ResR.string.pixiv_gallery_archive_pages, image.pageCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    align = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                )
                // 右下角「多图」角标：提示点击后可左右滑查看全部页。
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimensionResource(ResR.dimen.dimen_global_padding_s))
                        .size(dimensionResource(ResR.dimen.dimen_pixiv_gallery_badge_size))
                        .clip(RoundedCornerShape(dimensionResource(ResR.dimen.dimen_pixiv_gallery_badge_corner)))
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Collections,
                        contentDescription = stringResource(ResR.string.pixiv_gallery_archive_badge),
                        tint = Color.White,
                        modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_pixiv_drawer_icon_size)),
                    )
                }
            }
            PixivSavedKind.NOVEL -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_pixiv_drawer_icon_size)),
                    )
                }
                val label = image.label
                if (label != null) {
                    AppText(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        align = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                    )
                }
            }
        }

        if (selectionMode) {
            val selected = selectionIndex != null
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(dimensionResource(ResR.dimen.dimen_global_padding_s))
                    .size(dimensionResource(ResR.dimen.dimen_pixiv_gallery_badge_size))
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.35f),
                    )
                    .border(
                        width = dimensionResource(ResR.dimen.dimen_pixiv_selection_ring),
                        color = Color.White,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    AppText(
                        text = (selectionIndex + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        align = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * 多图预览：压缩包走临时解压，多页散图聚合直接用页文件列表，左右滑查看全部页。
 * 顶栏操作为「分享当前页 + 打印」（删除已移至多选模式）。
 */
@Composable
private fun GalleryMultiPageDialog(
    image: PixivSavedImage,
    onDismiss: () -> Unit,
    onSharePage: (File) -> Unit,
    onPrint: () -> Unit,
    viewModel: PixivToolsViewModel,
) {
    var pages by remember(image.illustId) { mutableStateOf<List<File>?>(null) }
    var failed by remember(image.illustId) { mutableStateOf(false) }
    var currentPage by remember(image.illustId) { mutableStateOf(0) }
    // 打印会把弹窗连同解压目录一起销毁；若此时先销毁再准备打印，解压目录会被
    // closeArchive 删掉，与打印侧重新解压并发竞争，导致输出为空。
    // 故标记「本次关闭是为了打印」，跳过回收，目录交给下次打开/打印批次自行覆盖。
    var printRequested by remember(image.illustId) { mutableStateOf(false) }
    val isArchive = image.kind == PixivSavedKind.ARCHIVE
    LaunchedEffect(image.illustId) {
        val result = runCatching { viewModel.openPages(image) }.getOrNull()
        if (result.isNullOrEmpty()) failed = true else pages = result
    }
    DisposableEffect(image.illustId) {
        onDispose { if (!printRequested) viewModel.closeArchive(image.illustId) }
    }
    val pageList = pages
    val title = if (isArchive) {
        stringResource(
            ResR.string.pixiv_gallery_archive_title,
            image.illustId,
            image.pageCount,
            formatBytes(image.sizeBytes),
        )
    } else {
        stringResource(
            ResR.string.pixiv_gallery_multi_title,
            image.illustId,
            image.pageCount,
            formatBytes(image.sizeBytes),
        )
    }
    if (pageList.isNullOrEmpty()) {
        // 解压 / 读取失败与进行中：保留全屏空态提示
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(
                            horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                            vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    AppIconButton(
                        onClick = onDismiss,
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(ResR.string.pixiv_gallery_close_preview),
                    )
                }
                FullScreenEmpty(
                    message = stringResource(
                        when {
                            failed -> if (isArchive) ResR.string.pixiv_gallery_archive_open_failed
                            else ResR.string.pixiv_gallery_open_failed
                            else -> if (isArchive) ResR.string.pixiv_gallery_archive_loading
                            else ResR.string.pixiv_gallery_loading
                        },
                    ),
                )
            }
        }
        return
    }
    PixivImageViewerDialog(
        models = pageList,
        title = title,
        onDismiss = onDismiss,
        contentDescription = image.label ?: image.illustId.toString(),
        onPageChanged = { currentPage = it },
        // 编辑后的单页直接导出到软件输出目录（相册），不覆盖原始下载文件。
        onExportEdited = { index, bitmap ->
            viewModel.exportEditedImage(bitmap, "pixiv_${image.illustId}_edit_${index + 1}")
        },
        actions = {
            AppIconButton(
                onClick = { pageList.getOrNull(currentPage)?.let(onSharePage) },
                imageVector = Icons.Filled.Share,
                contentDescription = stringResource(ResR.string.action_share),
                tint = Color.White,
            )
            AppIconButton(
                onClick = {
                    printRequested = true
                    onPrint()
                },
                imageVector = Icons.Filled.Print,
                contentDescription = stringResource(ResR.string.pixiv_gallery_print_action),
                tint = Color.White,
            )
        },
    )
}

/**
 * 小说正文查看：本地 txt 走统一阅读器（默认小说分页模式，可在右上角切换文本 / 小说）。
 *
 * - 本地内容可直接快捷修改，保存后写回 txt；
 * - 顶栏保留「分享 + 打印」，删除只在多选模式提供。
 */
@Composable
private fun GalleryNovelDialog(
    image: PixivSavedImage,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    viewModel: PixivToolsViewModel,
) {
    var content by remember(image.illustId) { mutableStateOf<String?>(null) }
    var failed by remember(image.illustId) { mutableStateOf(false) }
    var saveFailed by remember(image.illustId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(image.illustId) {
        val text = runCatching { withContext(Dispatchers.IO) { image.file.readText() } }.getOrNull()
        if (text == null) failed = true else content = text
    }
    // 保存失败提示 2 秒后自动消失。
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            kotlinx.coroutines.delay(2000L)
            saveFailed = false
        }
    }
    val title = image.label ?: stringResource(
        ResR.string.pixiv_gallery_novel_title_plain,
        image.illustId,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val text = content
        if (text == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(
                            horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                            vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    AppIconButton(
                        onClick = onDismiss,
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(ResR.string.pixiv_gallery_close_preview),
                    )
                }
                FullScreenEmpty(
                    message = stringResource(
                        if (failed) ResR.string.pixiv_gallery_novel_open_failed
                        else ResR.string.pixiv_gallery_novel_loading,
                    ),
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                AppReader(
                    title = title,
                    content = text,
                    onBack = onDismiss,
                    defaultMode = ReaderMode.NOVEL,
                    editable = true,
                    onContentChange = { updated ->
                        val previous = content
                        content = updated
                        scope.launch {
                            if (!viewModel.saveNovelContent(image.file, updated)) {
                                // 写盘失败时回滚到修改前的内容，避免界面与磁盘不一致。
                                content = previous
                                saveFailed = true
                            }
                        }
                    },
                    actions = {
                        AppIconButton(
                            onClick = onShare,
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(ResR.string.action_share),
                        )
                        AppIconButton(
                            onClick = onPrint,
                            imageVector = Icons.Filled.Print,
                            contentDescription = stringResource(ResR.string.pixiv_gallery_print_action),
                        )
                    },
                )
                if (saveFailed) {
                    AppText(
                        text = stringResource(ResR.string.reader_edit_save_failed),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        align = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_xl))
                            .clip(RoundedCornerShape(50))
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

/** 单图查看：支持缩放与编辑，顶栏为「分享 + 打印」。 */
@Composable
private fun GalleryImageDialog(
    image: PixivSavedImage,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    viewModel: PixivToolsViewModel,
) {
    PixivImageViewerDialog(
        models = listOf(image.file),
        title = stringResource(
            ResR.string.pixiv_gallery_caption,
            image.illustId,
            image.pageIndex + 1,
            formatBytes(image.sizeBytes),
        ),
        onDismiss = onDismiss,
        contentDescription = image.file.name,
        onExportEdited = { index, bitmap ->
            viewModel.exportEditedImage(bitmap, "pixiv_${image.illustId}_edit_${index + 1}")
        },
        actions = {
            AppIconButton(
                onClick = onShare,
                imageVector = Icons.Filled.Share,
                contentDescription = stringResource(ResR.string.action_share),
                tint = Color.White,
            )
            AppIconButton(
                onClick = onPrint,
                imageVector = Icons.Filled.Print,
                contentDescription = stringResource(ResR.string.pixiv_gallery_print_action),
                tint = Color.White,
            )
        },
    )
}

/** 经 FileProvider 授权分享本地文件（图片走相册/聊天，小说走文本目标）。 */
private fun shareSavedFile(context: Context, file: File) {
    if (!file.isFile) return
    val authority = "${context.packageName}$FILE_PROVIDER_SUFFIX"
    val uri = runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = when (file.extension.lowercase()) {
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "image/*"
        }
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
private const val GALLERY_COLUMNS = 3

/** 当前入口不可打印的卡片（置灰）透明度。 */
private const val DISABLED_TILE_ALPHA = 0.4f
