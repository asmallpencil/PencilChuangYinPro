package com.pencil.chuangyin.feature.pixiv.ui.tools

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.pencil.chuangyin.core.ui.components.AppLiquidDialog
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.motion.animateGridItemMotion
import com.pencil.chuangyin.core.ui.motion.animateItemMotion
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.pixiv.data.PixivDownloadStatus
import com.pencil.chuangyin.feature.pixiv.data.PixivSavedCategory
import com.pencil.chuangyin.feature.pixiv.data.PixivSavedImage
import com.pencil.chuangyin.feature.pixiv.data.PixivSavedKind
import com.pencil.chuangyin.feature.pixiv.data.local.NovelBookmarkEntry
import com.pencil.chuangyin.feature.pixiv.ui.PixivImageProvider
import com.pencil.chuangyin.feature.pixiv.ui.common.FullScreenEmpty
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivCoverImage
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivImageViewerDialog
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivSubScaffold
import com.pencil.chuangyin.feature.pixiv.ui.common.formatBytes
import com.pencil.chuangyin.feature.pixiv.ui.drawer.PixivToolsViewModel
import com.pencil.chuangyin.resources.R as ResR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ── 账号管理 ───────────────────────────────────────────────────────────────────

@Composable
fun PixivAccountRoute(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    viewModel: PixivToolsViewModel = hiltViewModel(),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    PixivImageProvider {
        PixivSubScaffold(title = stringResource(ResR.string.pixiv_account_title), onBack = onBack) {
            val current = account
            if (current == null) {
                FullScreenEmpty(message = stringResource(ResR.string.pixiv_not_login_title))
                return@PixivSubScaffold
            }
            var confirmLogout by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimensionResource(ResR.dimen.dimen_global_padding_l)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(dimensionResource(ResR.dimen.dimen_pixiv_drawer_avatar_size))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (current.avatarUrl.isNotBlank()) {
                        PixivCoverImage(
                            model = current.avatarUrl,
                            contentDescription = current.name,
                            errorIcon = Icons.Filled.Person,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AppText(
                    text = current.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_m)),
                )
                InfoRow(
                    label = stringResource(ResR.string.pixiv_account_id_label),
                    value = current.account,
                )
                InfoRow(
                    label = stringResource(ResR.string.pixiv_account_user_id_label),
                    value = current.userId.toString(),
                )
                Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
                AppOutlinedButton(
                    text = stringResource(ResR.string.pixiv_account_relogin),
                    onClick = {
                        viewModel.logout()
                        onLogin()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppPrimaryButton(
                    text = stringResource(ResR.string.pixiv_logout),
                    onClick = { confirmLogout = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_m)),
                )
            }
            if (confirmLogout) {
                AppLiquidDialog(
                    onDismissRequest = { confirmLogout = false },
                    title = stringResource(ResR.string.pixiv_logout),
                    message = stringResource(ResR.string.pixiv_account_logout_confirm),
                    confirmLabel = stringResource(ResR.string.action_confirm),
                    onConfirm = {
                        confirmLogout = false
                        viewModel.logout()
                        onBack()
                    },
                    dismissLabel = stringResource(ResR.string.action_cancel),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_m)),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppText(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppText(text = value)
    }
}

// ── 历史记录 ───────────────────────────────────────────────────────────────────

@Composable
fun PixivHistoryRoute(
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    viewModel: PixivToolsViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    PixivImageProvider {
        PixivSubScaffold(
            title = stringResource(ResR.string.pixiv_history_title),
            onBack = onBack,
            actions = {
                if (history.isNotEmpty()) {
                    AppIconButton(
                        onClick = { confirmClear = true },
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = stringResource(ResR.string.pixiv_history_clear),
                    )
                }
            },
        ) {
            if (history.isEmpty()) {
                FullScreenEmpty(message = stringResource(ResR.string.pixiv_history_empty))
            } else {
                val motion = rememberAppMotion()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(history, key = { it.illustId }) { entry ->
                        Row(
                            modifier = animateItemMotion(motion)
                                .fillMaxWidth()
                                .clickable { onOpenDetail(entry.illustId) }
                                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PixivCoverImage(
                                model = entry.coverUrl,
                                contentDescription = entry.title,
                                modifier = Modifier
                                    .size(dimensionResource(ResR.dimen.dimen_global_touch_target))
                                    .clip(MaterialTheme.shapes.small),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m)),
                            ) {
                                AppText(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                )
                                AppText(
                                    text = entry.userName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            AppIconButton(
                                onClick = { viewModel.removeHistory(entry.illustId) },
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(ResR.string.action_delete),
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AppLiquidDialog(
            onDismissRequest = { confirmClear = false },
            title = stringResource(ResR.string.pixiv_history_clear),
            message = stringResource(ResR.string.pixiv_history_clear_confirm),
            confirmLabel = stringResource(ResR.string.action_confirm),
            onConfirm = {
                confirmClear = false
                viewModel.clearHistory()
            },
            dismissLabel = stringResource(ResR.string.action_cancel),
        )
    }
}

// ── 收藏页（REQ5/REQ6） ────────────────────────────────────────────────────────

private enum class BookmarksTab { WORKS, NOVELS }

@Composable
fun PixivBookmarksRoute(
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onOpenNovel: (Int) -> Unit,
    viewModel: PixivToolsViewModel = hiltViewModel(),
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val novelBookmarks by viewModel.novelBookmarks.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(BookmarksTab.WORKS) }
    var confirmClear by remember { mutableStateOf(false) }
    PixivImageProvider {
        PixivSubScaffold(
            title = stringResource(ResR.string.pixiv_bookmarks_title),
            onBack = onBack,
            actions = {
                val currentNotEmpty = when (tab) {
                    BookmarksTab.WORKS -> bookmarks.isNotEmpty()
                    BookmarksTab.NOVELS -> novelBookmarks.isNotEmpty()
                }
                if (currentNotEmpty) {
                    AppIconButton(
                        onClick = { confirmClear = true },
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = stringResource(ResR.string.pixiv_bookmarks_clear),
                    )
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppSegmentedRow(
                    options = listOf(
                        stringResource(ResR.string.pixiv_bookmarks_tab_works),
                        stringResource(ResR.string.pixiv_bookmarks_tab_novels),
                    ),
                    selectedIndex = tab.ordinal,
                    onSelect = { index -> tab = BookmarksTab.values()[index] },
                    modifier = Modifier.padding(
                        horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l),
                        vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                    ),
                )
                when (tab) {
                    BookmarksTab.WORKS -> if (bookmarks.isEmpty()) {
                        FullScreenEmpty(message = stringResource(ResR.string.pixiv_bookmarks_empty))
                    } else {
                        val motion = rememberAppMotion()
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(bookmarks, key = { it.illustId }) { entry ->
                                Row(
                                    modifier = animateItemMotion(motion)
                                        .fillMaxWidth()
                                        .clickable { onOpenDetail(entry.illustId) }
                                        .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PixivCoverImage(
                                        model = entry.coverUrl,
                                        contentDescription = entry.title,
                                        modifier = Modifier
                                            .size(dimensionResource(ResR.dimen.dimen_global_touch_target))
                                            .clip(MaterialTheme.shapes.small),
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m)),
                                    ) {
                                        AppText(
                                            text = entry.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                        )
                                        AppText(
                                            text = entry.userName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                    AppIconButton(
                                        onClick = { viewModel.removeBookmark(entry.illustId) },
                                        imageVector = Icons.Filled.Bookmark,
                                        contentDescription = stringResource(ResR.string.pixiv_bookmarks_remove),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                    BookmarksTab.NOVELS -> if (novelBookmarks.isEmpty()) {
                        FullScreenEmpty(message = stringResource(ResR.string.pixiv_bookmarks_novel_empty))
                    } else {
                        val motion = rememberAppMotion()
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(novelBookmarks, key = { it.novelId }) { entry ->
                                NovelBookmarkRow(
                                    entry = entry,
                                    onClick = { onOpenNovel(entry.novelId) },
                                    onRemove = { viewModel.removeNovelBookmark(entry.novelId) },
                                    modifier = animateItemMotion(motion),
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
            title = stringResource(ResR.string.pixiv_bookmarks_clear),
            message = stringResource(ResR.string.pixiv_bookmarks_clear_confirm),
            confirmLabel = stringResource(ResR.string.action_confirm),
            onConfirm = {
                confirmClear = false
                when (tab) {
                    BookmarksTab.WORKS -> viewModel.clearBookmarks()
                    BookmarksTab.NOVELS -> viewModel.clearNovelBookmarks()
                }
            },
            dismissLabel = stringResource(ResR.string.action_cancel),
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
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dimensionResource(ResR.dimen.dimen_global_touch_target))
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_pixiv_drawer_icon_size)),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            AppText(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            AppText(
                text = entry.userName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        AppIconButton(
            onClick = onRemove,
            imageVector = Icons.Filled.Bookmark,
            contentDescription = stringResource(ResR.string.pixiv_bookmarks_novel_remove),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

// ── 下载进度 ───────────────────────────────────────────────────────────────────

@Composable
fun PixivDownloadsRoute(
    onBack: () -> Unit,
    viewModel: PixivToolsViewModel = hiltViewModel(),
) {
    val tasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    PixivSubScaffold(title = stringResource(ResR.string.pixiv_downloads_title), onBack = onBack) {
        if (tasks.isEmpty()) {
            FullScreenEmpty(message = stringResource(ResR.string.pixiv_downloads_empty))
        } else {
            val motion = rememberAppMotion()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
                ),
            ) {
                items(tasks, key = { it.illustId }) { task ->
                    Column(
                        modifier = animateItemMotion(motion)
                            .fillMaxWidth()
                            .padding(
                                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l),
                                vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
                            ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppText(
                                text = task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            val statusText = when (task.status) {
                                PixivDownloadStatus.RUNNING -> stringResource(
                                    ResR.string.pixiv_download_running,
                                    task.done,
                                    task.total,
                                )
                                PixivDownloadStatus.DONE -> stringResource(ResR.string.pixiv_download_done)
                                PixivDownloadStatus.FAILED ->
                                    task.message ?: stringResource(ResR.string.pixiv_download_failed)
                            }
                            AppText(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = when (task.status) {
                                    PixivDownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            if (task.status != PixivDownloadStatus.RUNNING) {
                                AppIconButton(
                                    onClick = { viewModel.removeDownloadTask(task.illustId) },
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(ResR.string.pixiv_download_remove),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                        if (task.status == PixivDownloadStatus.RUNNING) {
                            // 进度条用 spring 追进度，避免每一帧都"跳格"。
                            val fraction by animateFloatAsState(
                                targetValue = if (task.total == 0) 0f else task.done.toFloat() / task.total,
                                animationSpec = motion.effectSpec(),
                                label = "pixivDownloadProgress",
                            )
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { if (task.status == PixivDownloadStatus.DONE) 1f else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 屏蔽标签 ───────────────────────────────────────────────────────────────────

@Composable
fun PixivBlockedTagsRoute(
    onBack: () -> Unit,
    viewModel: PixivToolsViewModel = hiltViewModel(),
) {
    val tags by viewModel.blockedTags.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    PixivSubScaffold(title = stringResource(ResR.string.pixiv_blocked_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = stringResource(ResR.string.pixiv_blocked_add_hint),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppOutlinedButton(
                    text = stringResource(ResR.string.pixiv_blocked_add),
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.addBlockedTag(input)
                            input = ""
                        }
                    },
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }
            AppText(
                text = stringResource(ResR.string.pixiv_blocked_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
            )
            if (tags.isEmpty()) {
                FullScreenEmpty(message = stringResource(ResR.string.pixiv_blocked_empty))
            } else {
                val motion = rememberAppMotion()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tags.toList(), key = { it }) { tag ->
                        Row(
                            modifier = animateItemMotion(motion)
                                .fillMaxWidth()
                                .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = tag,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            AppIconButton(
                                onClick = { viewModel.removeBlockedTag(tag) },
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(ResR.string.pixiv_blocked_remove),
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
fun PixivCacheRoute(
    onBack: () -> Unit,
    onCacheCleared: () -> Unit = {},
    viewModel: PixivToolsViewModel = hiltViewModel(),
) {
    val sizes by viewModel.cacheSizes.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    PixivSubScaffold(title = stringResource(ResR.string.pixiv_cache_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_l)),
        ) {
            CacheRow(
                label = stringResource(ResR.string.pixiv_cache_print),
                value = sizes?.let { formatBytes(it.printCacheBytes) },
            )
            CacheRow(
                label = stringResource(ResR.string.pixiv_cache_images),
                value = sizes?.let { formatBytes(it.imageCacheBytes) },
            )
            CacheRow(
                label = stringResource(ResR.string.pixiv_cache_downloads),
                value = sizes?.let { formatBytes(it.downloadsBytes) },
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
            CacheRow(
                label = stringResource(ResR.string.pixiv_cache_total),
                value = sizes?.let { formatBytes(it.totalCacheBytes) },
                emphasized = true,
            )
            AppPrimaryButton(
                text = stringResource(ResR.string.pixiv_cache_clear),
                onClick = { confirmClear = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimensionResource(ResR.dimen.dimen_global_padding_xl)),
            )
        }
    }
    if (confirmClear) {
        AppLiquidDialog(
            onDismissRequest = { confirmClear = false },
            title = stringResource(ResR.string.pixiv_cache_clear),
            message = stringResource(ResR.string.pixiv_cache_clear_confirm),
            confirmLabel = stringResource(ResR.string.action_confirm),
            onConfirm = {
                confirmClear = false
                viewModel.clearCache()
                onCacheCleared()
            },
            dismissLabel = stringResource(ResR.string.action_cancel),
        )
    }
}

@Composable
private fun CacheRow(label: String, value: String?, emphasized: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppText(
            text = label,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = if (emphasized) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        AppText(
            text = value ?: "...",
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            align = TextAlign.End,
        )
    }
}
