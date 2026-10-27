package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.navigation.DesktopNavigator
import com.pencil.chuangyin.desktop.pixiv.PixivGraph
import com.pencil.chuangyin.desktop.pixiv.PixivNovel
import com.pencil.chuangyin.desktop.support.rememberRemoteImage
import com.pencil.chuangyin.desktop.text.TextEditorStore
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.motion.DesktopLoadingIndicator
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.launch

/**
 * 小说行 —— 与安卓端 `NovelRow` 逐项对齐：
 * 96×128 封面 + 标题两行 + 作者 + 字数 + 最多 4 个 `#标签` 预览。
 */
@Composable
fun NovelRow(novel: PixivNovel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cover = rememberRemoteImage(novel.coverUrl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(128.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                androidx.compose.foundation.Image(
                    bitmap = cover,
                    contentDescription = novel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                text = novel.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = novel.authorName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "${novel.textLength} 字",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (novel.tags.isNotEmpty()) {
                Text(
                    text = novel.tags.take(4).joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * 小说阅读页 —— 与安卓端 `PixivNovelReaderRoute` / `NovelContent` 对齐：
 * 顶栏（返回 / 收藏 / 刷新 / 浏览器打开）+ 标题 + 作者 + 标签流 + 正文。
 */
@Composable
fun PixivNovelDetailScreen(
    novelId: Long,
    bottomBarSpace: Dp,
    onBack: () -> Unit,
) {
    val client = PixivGraph.client
    val session = PixivGraph.session
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var novel by remember { mutableStateOf<PixivNovel?>(null) }
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var bookmarkBusy by remember { mutableStateOf(false) }

    LaunchedEffect(novelId, reloadTick) {
        loading = true
        error = null
        text = ""
        if (session.accessToken == null) {
            loading = false
            error = "请先登录 P 站"
            return@LaunchedEffect
        }
        val detail = runCatching { client.novelDetail(novelId) }.getOrNull()
        if (detail == null) {
            loading = false
            error = "作品不存在或已被删除"
            return@LaunchedEffect
        }
        novel = detail
        val body = runCatching { client.novelText(novelId) }.getOrDefault("")
        text = body
        loading = false
        if (body.isBlank()) error = "正文加载失败，请重试"
    }

    fun openInBrowser() {
        runCatching {
            Desktop.getDesktop().browse(URI("https://www.pixiv.net/novel/show.php?id=$novelId"))
        }.onFailure {
            scope.launch { snackbarHost.showSnackbar("无法打开浏览器") }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            GlassTopAppBar(
                title = novel?.title ?: "小说",
                navigationIcon = {
                    AppIconButton(icon = AppIcon.Back, contentDescription = "返回", onClick = onBack)
                },
                actions = {
                    val current = novel
                    if (current != null) {
                        AppIconButton(
                            icon = AppIcon.Bookmark,
                            contentDescription = if (current.isBookmarked) "取消收藏" else "收藏",
                            tint = if (current.isBookmarked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            onClick = {
                                if (bookmarkBusy) return@AppIconButton
                                bookmarkBusy = true
                                val target = !current.isBookmarked
                                scope.launch {
                                    val ok = client.setNovelBookmark(current.id, target)
                                    if (ok) {
                                        val updated = current.copy(isBookmarked = target)
                                        novel = updated
                                        // 同步本地收藏快照（侧边栏「收藏 → 小说」读本地存储）。
                                        if (target) {
                                            PixivGraph.novelBookmarkStore.add(updated)
                                        } else {
                                            PixivGraph.novelBookmarkStore.remove(current.id)
                                        }
                                    } else {
                                        snackbarHost.showSnackbar("操作失败，请重试")
                                    }
                                    bookmarkBusy = false
                                }
                            },
                        )
                        AppIconButton(
                            icon = AppIcon.Download,
                            contentDescription = "下载小说",
                            onClick = {
                                if (bookmarkBusy) return@AppIconButton
                                scope.launch {
                                    val ok = runCatching {
                                        PixivGraph.downloadManager.saveNovel(current.id, current.title, text)
                                    }.isSuccess
                                    snackbarHost.showSnackbar(if (ok) "已保存到下载目录" else "保存失败，请重试")
                                }
                            },
                        )
                        AppIconButton(
                            icon = AppIcon.Printer,
                            contentDescription = "打印",
                            onClick = {
                                // 与安卓端一致：标题+正文交给文本打印页，复用全套排版/浓度/连接。
                                TextEditorStore.updateTitle(current.title)
                                TextEditorStore.updateContent(text)
                                DesktopNavigator.navigate(DesktopNavigator.Page.Text)
                            },
                        )
                        AppIconButton(
                            icon = AppIcon.Refresh,
                            contentDescription = "刷新",
                            onClick = { reloadTick++ },
                        )
                        AppIconButton(
                            icon = AppIcon.Language,
                            contentDescription = "浏览器打开",
                            onClick = { openInBrowser() },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val current = novel
            when {
                loading -> LoadingBox()

                current == null -> ErrorBox(
                    message = error ?: "作品不存在或已被删除",
                    onRetry = { reloadTick++ },
                )

                error != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .padding(bottom = bottomBarSpace),
                ) {
                    AppEmptyState(
                        icon = AppIcon.Refresh,
                        title = error.orEmpty(),
                        description = "可以重试，或用浏览器打开原文",
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppPrimaryButton(text = "重试", onClick = { reloadTick++ })
                                AppSecondaryButton(text = "浏览器打开", onClick = { openInBrowser() })
                            }
                        },
                    )
                }

                else -> NovelContent(
                    novel = current,
                    text = text,
                    bottomBarSpace = bottomBarSpace,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelContent(novel: PixivNovel, text: String, bottomBarSpace: Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = bottomBarSpace),
    ) {
        Text(
            text = novel.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = novel.authorName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (novel.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                novel.tags.forEach { tag ->
                    SectionChip(label = tag, selected = false, onClick = {})
                }
            }
        }
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                DesktopLoadingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    size = 26.dp,
                )
            }
        }
    }
}
