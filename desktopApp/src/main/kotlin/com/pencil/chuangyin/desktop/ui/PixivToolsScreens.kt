package com.pencil.chuangyin.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.pixiv.NovelBookmarkEntry
import com.pencil.chuangyin.desktop.pixiv.PixivCacheManager
import com.pencil.chuangyin.desktop.pixiv.PixivCacheSizes
import com.pencil.chuangyin.desktop.pixiv.PixivDownloadStatus
import com.pencil.chuangyin.desktop.pixiv.PixivGraph
import com.pencil.chuangyin.desktop.pixiv.PixivSavedCategory
import com.pencil.chuangyin.desktop.pixiv.PixivSavedImage
import com.pencil.chuangyin.desktop.pixiv.PixivSavedKind
import com.pencil.chuangyin.desktop.support.rememberLocalFileImage
import com.pencil.chuangyin.desktop.support.rememberRemoteImage
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import com.pencil.chuangyin.desktop.ui.motion.DesktopMotion
import com.pencil.chuangyin.desktop.ui.motion.animateGridItemMotion
import com.pencil.chuangyin.desktop.ui.motion.animateItemMotion
import java.awt.Desktop
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── 通用尺寸（对齐安卓端 dimens.xml）────────────────────────────────────────────

private val PaddingS = 8.dp
private val PaddingM = 16.dp
private val PaddingL = 24.dp
private val PaddingXl = 32.dp
private val DrawerAvatarSize = 72.dp
private const val GALLERY_COLUMNS = 3

/** 二级页骨架：玻璃顶栏（返回 + 标题 + 右侧动作）+ 内容区，对齐安卓端 PixivSubScaffold。 */
@Composable
fun PixivSubScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = title,
                navigationIcon = {
                    AppIconButton(icon = AppIcon.Back, contentDescription = "返回", onClick = onBack)
                },
                actions = actions,
            )
        },
    ) { innerPadding ->
        // 底部留出悬浮底栏占位（与 AppShell 的 FLOATING_NAV_* 同源），避免最后一行被遮挡。
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = FLOATING_NAV_HEIGHT + FLOATING_NAV_BOTTOM_GAP),
        ) {
            content(innerPadding)
        }
    }
}

/** 文件体积格式化（与安卓端 formatBytes 逐字对齐）。 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

/** 全屏空态文案（对齐安卓端 FullScreenEmpty：居中 onSurfaceVariant 文本）。 */
@Composable
fun FullScreenEmpty(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 确认弹窗（对齐安卓端 AppLiquidDialog 的文案结构；桌面端使用 M3 AlertDialog 玻璃底色）。 */
@Composable
fun AppLiquidDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = if (dismissLabel != null) {
            { TextButton(onClick = onDismissRequest) { Text(dismissLabel) } }
        } else {
            null
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/** 本地/网络封面小图（替代安卓端 PixivCoverImage 的方块缩略）。 */
@Composable
private fun ToolCover(url: String, modifier: Modifier = Modifier) {
    val bitmap = rememberRemoteImage(url.ifBlank { null })
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
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

// ── 账号管理 ───────────────────────────────────────────────────────────────────

@Composable
fun PixivAccountScreen(onBack: () -> Unit, onLogin: () -> Unit) {
    val account by PixivGraph.session.account.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }

    PixivSubScaffold(title = "账号管理", onBack = onBack) {
        val current = account
        if (current == null) {
            FullScreenEmpty("尚未登录")
            return@PixivSubScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(DrawerAvatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!current.avatarUrl.isNullOrBlank()) {
                    PixivAvatar(url = current.avatarUrl, size = DrawerAvatarSize)
                } else {
                    AppIconView(
                        icon = AppIcon.Person,
                        size = 32.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(PaddingM))
            Text(
                text = current.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            InfoRow(label = "P 站账号", value = current.account)
            InfoRow(label = "用户 ID", value = current.userId)
            Spacer(Modifier.height(24.dp))
            AppSecondaryButton(
                text = "重新登录",
                onClick = {
                    PixivGraph.session.clear()
                    onLogin()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AppPrimaryButton(
                text = "退出登录",
                onClick = { confirmLogout = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PaddingM),
            )
        }
    }

    if (confirmLogout) {
        AppLiquidDialog(
            onDismissRequest = { confirmLogout = false },
            title = "退出登录",
            message = "确定退出当前 P 站账号吗？本地登录信息将被清除。",
            confirmLabel = "确定",
            onConfirm = {
                confirmLogout = false
                PixivGraph.session.clear()
                onBack()
            },
            dismissLabel = "取消",
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PaddingM),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── 历史记录 ───────────────────────────────────────────────────────────────────

@Composable
fun PixivHistoryScreen(onBack: () -> Unit, onOpenDetail: (Long) -> Unit) {
    val store = PixivGraph.historyStore
    val history by store.history.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    PixivSubScaffold(
        title = "历史记录",
        onBack = onBack,
        actions = {
            if (history.isNotEmpty()) {
                AppIconButton(
                    icon = AppIcon.DeleteSweep,
                    contentDescription = "清空历史",
                    onClick = { confirmClear = true },
                )
            }
        },
    ) {
        if (history.isEmpty()) {
            FullScreenEmpty("还没有浏览记录")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(count = history.size, key = { index -> history[index].illustId }) { index ->
                    val entry = history[index]
                    Row(
                        modifier = animateItemMotion()
                            .fillMaxWidth()
                            .clickable { onOpenDetail(entry.illustId) }
                            .padding(PaddingM),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolCover(url = entry.coverUrl, modifier = Modifier.size(48.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = PaddingM),
                        ) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.userName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        AppIconButton(
                            icon = AppIcon.Close,
                            contentDescription = "删除",
                            onClick = { store.remove(entry.illustId) },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AppLiquidDialog(
            onDismissRequest = { confirmClear = false },
            title = "清空历史",
            message = "确定清空全部浏览历史吗？",
            confirmLabel = "确定",
            onConfirm = {
                confirmClear = false
                store.clear()
            },
            dismissLabel = "取消",
        )
    }
}

// ── 收藏 ───────────────────────────────────────────────────────────────────────

private enum class BookmarksTab { WORKS, NOVELS }

@Composable
fun PixivBookmarksScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
) {
    val workStore = PixivGraph.bookmarkStore
    val novelStore = PixivGraph.novelBookmarkStore
    val works by workStore.bookmarks.collectAsState()
    val novels by novelStore.bookmarks.collectAsState()
    var tab by remember { mutableStateOf(BookmarksTab.WORKS) }
    var confirmClear by remember { mutableStateOf(false) }

    val currentEmpty = when (tab) {
        BookmarksTab.WORKS -> works.isEmpty()
        BookmarksTab.NOVELS -> novels.isEmpty()
    }

    PixivSubScaffold(
        title = "收藏",
        onBack = onBack,
        actions = {
            if (!currentEmpty) {
                AppIconButton(
                    icon = AppIcon.DeleteSweep,
                    contentDescription = "清空收藏",
                    onClick = { confirmClear = true },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            AppSegmentedRow(
                options = listOf("作品", "小说"),
                selectedIndex = tab.ordinal,
                onSelected = { index -> tab = BookmarksTab.entries[index] },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PaddingL, vertical = PaddingS),
            )
            when (tab) {
                BookmarksTab.WORKS -> {
                    if (works.isEmpty()) {
                        FullScreenEmpty("还没有收藏作品，在作品详情页点爱心即可收藏")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(count = works.size, key = { index -> works[index].illustId }) { index ->
                                val entry = works[index]
                                Row(
                                    modifier = animateItemMotion()
                                        .fillMaxWidth()
                                        .clickable { onOpenDetail(entry.illustId) }
                                        .padding(PaddingM),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ToolCover(url = entry.coverUrl, modifier = Modifier.size(48.dp))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = PaddingM),
                                    ) {
                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = entry.userName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    AppIconButton(
                                        icon = AppIcon.Bookmark,
                                        contentDescription = "取消收藏",
                                        onClick = { workStore.remove(entry.illustId) },
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                BookmarksTab.NOVELS -> {
                    if (novels.isEmpty()) {
                        FullScreenEmpty("还没有收藏的小说，在小说阅读页点爱心即可收藏")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(count = novels.size, key = { index -> novels[index].novelId }) { index ->
                                val entry = novels[index]
                                NovelBookmarkRow(
                                    entry = entry,
                                    onClick = { onOpenNovel(entry.novelId) },
                                    onRemove = { novelStore.remove(entry.novelId) },
                                    modifier = animateItemMotion(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AppLiquidDialog(
            onDismissRequest = { confirmClear = false },
            title = "清空收藏",
            message = "确定清空全部本地收藏吗？（不影响 P 站服务端收藏）",
            confirmLabel = "确定",
            onConfirm = {
                confirmClear = false
                when (tab) {
                    BookmarksTab.WORKS -> workStore.clear()
                    BookmarksTab.NOVELS -> novelStore.clear()
                }
            },
            dismissLabel = "取消",
        )
    }
}

@Composable
private fun NovelBookmarkRow(
    entry: NovelBookmarkEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(PaddingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AppIconView(
                icon = AppIcon.Description,
                size = 24.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = PaddingM),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.userName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AppIconButton(
            icon = AppIcon.Bookmark,
            contentDescription = "取消小说收藏",
            onClick = onRemove,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

// ── 下载进度 ───────────────────────────────────────────────────────────────────

@Composable
fun PixivDownloadsScreen(onBack: () -> Unit) {
    val manager = PixivGraph.downloadManager
    val tasks by manager.tasks.collectAsState()

    PixivSubScaffold(title = "下载进度", onBack = onBack) {
        if (tasks.isEmpty()) {
            FullScreenEmpty("没有下载任务，可在作品详情页下载原图，或在小说阅读页下载小说")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = PaddingS),
            ) {
                items(count = tasks.size, key = { index -> tasks[index].illustId }) { index ->
                    val task = tasks[index]
                    val progressTarget = when (task.status) {
                        PixivDownloadStatus.RUNNING ->
                            if (task.total <= 0) 0f else task.done.toFloat() / task.total
                        PixivDownloadStatus.DONE -> 1f
                        PixivDownloadStatus.FAILED -> 0f
                    }
                    // 进度条用弹簧追赶，避免 1/200 → 2/200 这种一格一格硬跳。
                    val animatedProgress by animateFloatAsState(
                        targetValue = progressTarget,
                        animationSpec = DesktopMotion.spatial(),
                        label = "downloadTaskProgress",
                    )
                    Column(
                        modifier = animateItemMotion()
                            .fillMaxWidth()
                            .padding(horizontal = PaddingL, vertical = PaddingM),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            val statusText = when (task.status) {
                                PixivDownloadStatus.RUNNING -> "下载中 ${task.done}/${task.total}"
                                PixivDownloadStatus.DONE -> "已完成"
                                PixivDownloadStatus.FAILED -> task.message ?: "下载失败"
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (task.status == PixivDownloadStatus.FAILED) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            if (task.status != PixivDownloadStatus.RUNNING) {
                                AppIconButton(
                                    icon = AppIcon.Close,
                                    contentDescription = "移除记录",
                                    onClick = { manager.removeTask(task.illustId) },
                                )
                            }
                        }
                        Spacer(Modifier.height(PaddingS))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ── 已保存内容 ─────────────────────────────────────────────────────────────────

private enum class GalleryFilter { ALL, DOWNLOAD, PRINT_CACHE }

@Composable
fun PixivGalleryScreen(onBack: () -> Unit) {
    val manager = PixivGraph.downloadManager
    val images by manager.savedImages.collectAsState()
    var filter by remember { mutableStateOf(GalleryFilter.ALL) }
    var selected by remember { mutableStateOf<PixivSavedImage?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { manager.refreshSavedImages() }

    val filtered = remember(images, filter) {
        when (filter) {
            GalleryFilter.ALL -> images
            GalleryFilter.DOWNLOAD -> images.filter { it.category == PixivSavedCategory.DOWNLOAD }
            GalleryFilter.PRINT_CACHE -> images.filter { it.category == PixivSavedCategory.PRINT_CACHE }
        }
    }

    Box(Modifier.fillMaxSize()) {
        PixivSubScaffold(
            title = "已保存内容",
            onBack = onBack,
            actions = {
                if (images.isNotEmpty()) {
                    AppIconButton(
                        icon = AppIcon.DeleteSweep,
                        contentDescription = "全部删除",
                        onClick = { confirmDeleteAll = true },
                    )
                }
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                AppSegmentedRow(
                    options = listOf("全部", "手动下载", "打印缓存"),
                    selectedIndex = filter.ordinal,
                    onSelected = { index -> filter = GalleryFilter.entries[index] },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PaddingL, vertical = PaddingS),
                )
                when {
                    images.isEmpty() -> FullScreenEmpty("还没有保存的图片")
                    filtered.isEmpty() -> FullScreenEmpty("该分类暂无内容")
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(GALLERY_COLUMNS),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            count = filtered.size,
                            key = { index -> filtered[index].file.absolutePath },
                        ) { index ->
                            val image = filtered[index]
                            GalleryTile(
                                image = image,
                                onClick = { selected = image },
                                modifier = animateGridItemMotion(),
                            )
                        }
                    }
                }
            }
        }

        val current = selected
        if (current != null) {
            when (current.kind) {
                PixivSavedKind.ARCHIVE, PixivSavedKind.MULTI_IMAGE -> GalleryMultiPageOverlay(
                    image = current,
                    onDismiss = { selected = null },
                    onDelete = {
                        manager.deleteImage(current)
                        selected = null
                    },
                )

                PixivSavedKind.NOVEL -> GalleryNovelOverlay(
                    image = current,
                    onDismiss = { selected = null },
                    onDelete = {
                        manager.deleteImage(current)
                        selected = null
                    },
                )

                PixivSavedKind.IMAGE -> GalleryImageOverlay(
                    image = current,
                    onDismiss = { selected = null },
                    onShare = { openInFolder(current.file) },
                    onDelete = {
                        manager.deleteImage(current)
                        selected = null
                    },
                )
            }
        }
    }

    if (confirmDeleteAll) {
        AppLiquidDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = "全部删除",
            message = "确定删除全部已保存图片吗？",
            confirmLabel = "确定",
            onConfirm = {
                confirmDeleteAll = false
                manager.deleteAllImages()
            },
            dismissLabel = "取消",
        )
    }
}

@Composable
private fun GalleryTile(image: PixivSavedImage, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (image.kind) {
            PixivSavedKind.IMAGE -> {
                val bitmap = rememberLocalFileImage(image.file)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            PixivSavedKind.ARCHIVE, PixivSavedKind.MULTI_IMAGE -> {
                // 压缩包/多页散图：先取首页作为封面，再解码显示（与安卓端 produceState 一致）。
                val coverFile by produceState<File?>(initialValue = null, image.illustId, image.file.absolutePath) {
                    value = runCatching { PixivGraph.downloadManager.archiveFirstPageFile(image) }.getOrNull()
                }
                val bitmap = rememberLocalFileImage(coverFile)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AppIconView(
                        icon = AppIcon.Collections,
                        size = 32.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (image.pageCount > 0) {
                    Text(
                        text = "${image.pageCount} 页",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = PaddingS, vertical = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(PaddingS)
                        .size(22.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIconView(icon = AppIcon.Collections, size = 14.dp, tint = Color.White)
                }
            }

            PixivSavedKind.NOVEL -> {
                AppIconView(
                    icon = AppIcon.Description,
                    size = 32.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val label = image.label
                if (!label.isNullOrBlank()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = PaddingS, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** 全屏预览外壳（桌面端用同层级遮罩替代 Android Dialog）。 */
@Composable
private fun PreviewScaffold(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}

@Composable
private fun GalleryMultiPageOverlay(
    image: PixivSavedImage,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val manager = PixivGraph.downloadManager
    var pages by remember(image.illustId) { mutableStateOf<List<File>?>(null) }
    var failed by remember(image.illustId) { mutableStateOf(false) }

    LaunchedEffect(image.illustId) {
        failed = false
        runCatching { manager.openPages(image) }
            .onSuccess { pages = it }
            .onFailure { failed = true }
    }
    DisposableEffect(image.illustId) {
        onDispose { manager.clearViewCache(image.illustId) }
    }

    PreviewScaffold {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PaddingM, vertical = PaddingS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pageList = pages
                val title = when {
                    image.kind == PixivSavedKind.ARCHIVE ->
                        "作品 ${image.illustId} · 压缩包 ${pageList?.size ?: 0} 页 · ${formatBytes(image.sizeBytes)}"
                    else ->
                        "作品 ${image.illustId} · 共 ${pageList?.size ?: image.pageCount} 页 · ${formatBytes(image.sizeBytes)}"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AppIconButton(
                    icon = AppIcon.DeleteSweep,
                    contentDescription = "删除",
                    onClick = onDelete,
                )
                AppIconButton(
                    icon = AppIcon.Close,
                    contentDescription = "关闭预览",
                    onClick = onDismiss,
                )
            }

            val pageList = pages
            when {
                failed || (pageList != null && pageList.isEmpty()) -> {
                    FullScreenEmpty(
                        if (image.kind == PixivSavedKind.ARCHIVE) {
                            "压缩包解压失败，请稍后重试"
                        } else {
                            "打开失败，请稍后重试"
                        },
                    )
                }

                pageList == null -> {
                    FullScreenEmpty(
                        if (image.kind == PixivSavedKind.ARCHIVE) {
                            "正在解压压缩包…"
                        } else {
                            "正在加载…"
                        },
                    )
                }

                else -> {
                    val pagerState = rememberPagerState(initialPage = 0) { pageList.size }
                    Box(Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { index ->
                            val bitmap = rememberLocalFileImage(pageList[index])
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${pagerState.currentPage + 1}/${pageList.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(PaddingL)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = PaddingM, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryNovelOverlay(
    image: PixivSavedImage,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val content by produceState<String?>(initialValue = null, image.file.absolutePath) {
        value = runCatching {
            withContext(Dispatchers.IO) { image.file.readText() }
        }.getOrNull()
    }

    PreviewScaffold {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PaddingM, vertical = PaddingS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = image.label ?: "小说 ${image.illustId} · ${formatBytes(image.sizeBytes)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AppIconButton(
                    icon = AppIcon.DeleteSweep,
                    contentDescription = "删除",
                    onClick = onDelete,
                )
                AppIconButton(
                    icon = AppIcon.Close,
                    contentDescription = "关闭预览",
                    onClick = onDismiss,
                )
            }
            val text = content
            if (text == null) {
                FullScreenEmpty("正在加载小说内容…")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(PaddingL),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryImageOverlay(
    image: PixivSavedImage,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val bitmap = rememberLocalFileImage(image.file)

    PreviewScaffold {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "作品 ${image.illustId} · 第 ${image.pageIndex + 1} 页 · ${formatBytes(image.sizeBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(PaddingM),
            )
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(horizontal = PaddingM),
                )
            } else {
                FullScreenEmpty("正在加载…")
            }
            Spacer(Modifier.height(PaddingL))
            Row(horizontalArrangement = Arrangement.spacedBy(PaddingM)) {
                AppSecondaryButton(text = "打开所在文件夹", onClick = onShare)
                AppSecondaryButton(text = "删除", onClick = onDelete)
                AppPrimaryButton(text = "关闭", onClick = onDismiss)
            }
        }
    }
}

/** 打开文件所在目录（桌面端替代 Android 的 FileProvider 分享）。 */
private fun openInFolder(file: File) {
    runCatching {
        val dir = file.parentFile ?: return
        Desktop.getDesktop().open(dir)
    }
}

// ── 屏蔽标签 ───────────────────────────────────────────────────────────────────

@Composable
fun PixivBlockedTagsScreen(onBack: () -> Unit) {
    val store = PixivGraph.blockedTagsStore
    val tags by store.tags.collectAsState()
    var input by remember { mutableStateOf("") }

    fun submit() {
        val tag = input.trim()
        if (tag.isEmpty()) return
        store.add(tag)
        input = ""
    }

    PixivSubScaffold(title = "屏蔽标签", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "输入标签名（如：東方）",
                    onDone = { submit() },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(PaddingS))
                AppSecondaryButton(text = "添加屏蔽", onClick = { submit() })
            }
            Text(
                text = "带屏蔽标签的作品不会出现在推荐 / 榜单 / 搜索结果中",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = PaddingS),
            )
            if (tags.isEmpty()) {
                FullScreenEmpty("还没有屏蔽标签")
            } else {
                val list = remember(tags) { tags.sorted() }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(count = list.size, key = { index -> list[index] }) { index ->
                        val tag = list[index]
                        Row(
                            modifier = animateItemMotion()
                                .fillMaxWidth()
                                .padding(vertical = PaddingS),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            AppIconButton(
                                icon = AppIcon.Close,
                                contentDescription = "取消屏蔽",
                                onClick = { store.remove(tag) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 运行缓存 ───────────────────────────────────────────────────────────────────

@Composable
fun PixivCacheScreen(onBack: () -> Unit, onCacheCleared: () -> Unit = {}) {
    var sizes by remember { mutableStateOf(PixivCacheManager.snapshot()) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { sizes = PixivCacheManager.snapshot() }

    PixivSubScaffold(title = "运行缓存", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingL),
        ) {
            CacheRow(label = "打印原图缓存", value = formatBytes(sizes.printCacheBytes))
            CacheRow(label = "图片磁盘缓存", value = formatBytes(sizes.imageCacheBytes))
            CacheRow(
                label = "已下载原图（长期保存，不会被清理）",
                value = formatBytes(sizes.downloadsBytes),
            )
            Spacer(Modifier.height(PaddingM))
            CacheRow(
                label = "缓存合计",
                value = formatBytes(sizes.totalCacheBytes),
                emphasized = true,
            )
            AppPrimaryButton(
                text = "清除缓存",
                onClick = { confirmClear = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PaddingXl),
            )
        }
    }

    if (confirmClear) {
        AppLiquidDialog(
            onDismissRequest = { confirmClear = false },
            title = "清除缓存",
            message = "将清除打印原图与图片缓存（不影响已保存的下载图片），确定继续吗？",
            confirmLabel = "确定",
            onConfirm = {
                confirmClear = false
                PixivCacheManager.clearCache()
                sizes = PixivCacheManager.snapshot()
                onCacheCleared()
            },
            dismissLabel = "取消",
        )
    }
}

@Composable
private fun CacheRow(
    label: String,
    value: String?,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PaddingS),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (emphasized) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = if (emphasized) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(PaddingM))
        Text(
            text = value ?: "...",
            style = if (emphasized) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = if (emphasized) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.End,
        )
    }
}
