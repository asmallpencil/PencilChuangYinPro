package com.pencil.chuangyin.feature.ai.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImagePainter
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.feature.ai.R
import com.pencil.chuangyin.resources.R as ResR
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 聊天产物可用的打开方式。 */
internal enum class AiMediaKind { Image, Video, Audio, File }

/**
 * 按扩展名判定产物类型。
 *
 * 工具上报的 `kind` 只有 image / file 两种，视频、音频要靠后缀补齐，
 * 否则视频会被当成普通文件直接甩给外部应用。
 */
internal fun AiUiArtifact.mediaKind(): AiMediaKind {
    if (isImage) return AiMediaKind.Image
    return when (path.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "ts", "flv", "wmv" -> AiMediaKind.Video
        "mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "amr", "mid" -> AiMediaKind.Audio
        else -> AiMediaKind.File
    }
}

/** 产物解析结果：加载中 / 可读本地路径 / 不可读。 */
private sealed interface MediaSource {
    data object Loading : MediaSource
    data class Ready(val path: String) : MediaSource
    data object Unavailable : MediaSource
}

/**
 * 聊天产物查看器：图片与视频在应用内直接播放，其余格式交给系统默认应用；
 * 系统里没有可打开的应用时转到系统文件管理器。
 *
 * 图片渲染复用 panpf/zoomimage（与 P 站全屏查看器同一套双指缩放 / 双击放大 / 子采样），
 * 视频用系统 [VideoView] 播放，不额外引入播放器内核。
 */
@Composable
internal fun AiMediaViewer(
    artifact: AiUiArtifact,
    resolveFile: (suspend (AiUiArtifact) -> String?)?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val kind = remember(artifact.id, artifact.path) { artifact.mediaKind() }
    val displayName = artifact.label ?: artifact.path.substringAfterLast('/')
    val source by produceState<MediaSource>(initialValue = MediaSource.Loading, key1 = artifact.id, key2 = artifact.path) {
        value = withContext(Dispatchers.IO) {
            val resolved = if (resolveFile != null) {
                runCatching { resolveFile.invoke(artifact) }.getOrNull()
            } else {
                artifact.path.takeIf { File(it).isFile }
            }
            if (resolved != null) MediaSource.Ready(resolved) else MediaSource.Unavailable
        }
    }
    // 音频与其它格式无法在应用内呈现，解析完成后立刻交给系统默认应用；没有可用应用则留在兜底页。
    var externalAttempted by remember(artifact.id) { mutableStateOf(false) }
    val sourcePath = (source as? MediaSource.Ready)?.path
    LaunchedEffect(sourcePath, kind) {
        val path = sourcePath ?: return@LaunchedEffect
        if (externalAttempted) return@LaunchedEffect
        if (kind != AiMediaKind.Audio && kind != AiMediaKind.File) return@LaunchedEffect
        externalAttempted = true
        if (openExternally(context, File(path))) {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize().background(VIEWER_BACKGROUND)) {
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
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = stringResource(kindLabelRes(kind)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                if (sourcePath != null) {
                    AppIconButton(
                        onClick = {
                            if (!openExternally(context, File(sourcePath))) {
                                fallbackToFileManager(context)
                            }
                        },
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.ai_media_open_external),
                        tint = Color.White,
                    )
                }
                AppIconButton(
                    onClick = onDismiss,
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.ai_media_close),
                    tint = Color.White,
                )
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when (val current = source) {
                    MediaSource.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppLoadingIndicator(color = Color.White)
                        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                        AppText(
                            text = stringResource(R.string.ai_media_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }

                    MediaSource.Unavailable -> AiMediaFallback(
                        icon = fallbackIcon(kind),
                        title = displayName,
                        subtitle = stringResource(R.string.ai_media_unavailable),
                        message = null,
                        onOpenExternal = null,
                        onOpenManager = { fallbackToFileManager(context) },
                    )

                    is MediaSource.Ready -> when (kind) {
                        AiMediaKind.Image -> AiZoomableImage(
                            path = current.path,
                            contentDescription = displayName,
                            modifier = Modifier.fillMaxSize(),
                        )

                        AiMediaKind.Video -> AiVideoPlayer(
                            path = current.path,
                            displayName = displayName,
                            modifier = Modifier.fillMaxSize(),
                        )

                        AiMediaKind.Audio, AiMediaKind.File -> AiMediaFallback(
                            icon = fallbackIcon(kind),
                            title = displayName,
                            subtitle = sizeLabel(current.path),
                            message = stringResource(
                                if (externalAttempted) R.string.ai_media_no_app else R.string.ai_media_unsupported,
                            ),
                            onOpenExternal = {
                                if (!openExternally(context, File(current.path))) {
                                    fallbackToFileManager(context)
                                }
                            },
                            onOpenManager = { fallbackToFileManager(context) },
                        )
                    }
                }
            }
        }
    }
}

/** 图片：ZoomImage 提供双指缩放、双击放大、缩放态拖拽与超大图子采样。 */
@Composable
private fun AiZoomableImage(
    path: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var state by remember(path) { mutableStateOf<AsyncImagePainter.State?>(null) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CoilZoomAsyncImage(
            model = File(path),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            scrollBar = null,
            onState = { state = it },
        )
        when (state) {
            is AsyncImagePainter.State.Loading -> AppLoadingIndicator(color = Color.White)
            is AsyncImagePainter.State.Error -> Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(48.dp),
            )
            else -> Unit
        }
    }
}

/** 视频：系统 VideoView + 传输控制条，按真实分辨率保持画面比例。 */
@Composable
private fun AiVideoPlayer(
    path: String,
    displayName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var videoError by remember(path) { mutableStateOf(false) }
    var ratio by remember(path) { mutableStateOf(16f / 9f) }
    if (videoError) {
        AiMediaFallback(
            icon = Icons.Filled.InsertDriveFile,
            title = displayName,
            subtitle = sizeLabel(path),
            message = stringResource(R.string.ai_media_unsupported),
            onOpenExternal = {
                if (!openExternally(context, File(path))) fallbackToFileManager(context)
            },
            onOpenManager = { fallbackToFileManager(context) },
            modifier = modifier,
        )
        return
    }
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).also { view ->
                view.setVideoPath(path)
                val controller = MediaController(ctx).apply { setAnchorView(view) }
                view.setMediaController(controller)
                view.setOnPreparedListener { player ->
                    if (player.videoWidth > 0 && player.videoHeight > 0) {
                        ratio = player.videoWidth.toFloat() / player.videoHeight
                    }
                    player.isLooping = false
                    view.start()
                }
                view.setOnErrorListener { _, _, _ ->
                    videoError = true
                    true
                }
            }
        },
        onRelease = { view -> runCatching { view.stopPlayback() } },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio),
    )
}

/** 无法内置预览时的兜底页：说明类型，并提供「用其他应用打开 / 文件管理器」出口。 */
@Composable
private fun AiMediaFallback(
    icon: ImageVector,
    title: String,
    subtitle: String,
    message: String?,
    onOpenExternal: (() -> Unit)?,
    onOpenManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_l)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        AppText(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            align = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            AppText(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                align = TextAlign.Center,
            )
        }
        if (message != null) {
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
            AppText(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                align = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        if (onOpenExternal != null) {
            AppPrimaryButton(
                text = stringResource(R.string.ai_media_open_external),
                onClick = onOpenExternal,
            )
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        }
        AppTextButton(
            text = stringResource(R.string.ai_media_open_manager),
            onClick = onOpenManager,
            contentColor = Color.White,
        )
    }
}

private fun fallbackIcon(kind: AiMediaKind): ImageVector = when (kind) {
    AiMediaKind.Audio -> Icons.Filled.MusicNote
    else -> Icons.Filled.InsertDriveFile
}

private fun kindLabelRes(kind: AiMediaKind): Int = when (kind) {
    AiMediaKind.Image -> R.string.ai_media_kind_image
    AiMediaKind.Video -> R.string.ai_media_kind_video
    AiMediaKind.Audio -> R.string.ai_media_kind_audio
    AiMediaKind.File -> R.string.ai_media_kind_file
}

/** 人类可读的文件大小；取不到时返回空串。 */
private fun sizeLabel(path: String): String {
    val bytes = runCatching { File(path).length() }.getOrDefault(0L)
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

/**
 * 用系统默认应用打开。走 FileProvider 授权，工作区外的文件先复制到可授权缓存目录。
 * 返回 false 表示系统里没有能处理该类型（或文件不可分享）的应用。
 */
private fun openExternally(context: Context, file: File): Boolean {
    if (!file.isFile) return false
    val uri = shareableUri(context, file) ?: return false
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeOf(file.name))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

/** 生成可跨进程读取的 content:// URI，失败时把文件落到缓存再授权。 */
private fun shareableUri(context: Context, file: File): Uri? {
    val authority = "${context.packageName}$AI_FILE_PROVIDER_SUFFIX"
    val direct = runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull()
    if (direct != null) return direct
    val sharedDir = File(context.cacheDir, SHARED_MEDIA_DIR).apply { mkdirs() }
    val staged = File(sharedDir, file.name)
    val copied = runCatching { file.copyTo(staged, overwrite = true) }.isSuccess
    if (!copied) return null
    return runCatching { FileProvider.getUriForFile(context, authority, staged) }.getOrNull()
}

/** 转到系统默认文件管理器；都不可用时给出提示，避免点了没反应。 */
private fun fallbackToFileManager(context: Context) {
    val candidates = listOf(
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse("content://com.android.externalstorage.documents/root/primary"),
                "vnd.android.document/root",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
    val opened = candidates.any { intent -> runCatching { context.startActivity(intent) }.isSuccess }
    if (!opened) {
        Toast.makeText(context, context.getString(R.string.ai_media_no_manager), Toast.LENGTH_SHORT).show()
    }
}

private fun mimeOf(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

private val VIEWER_BACKGROUND = Color(0xFF0B0B0D)

private const val AI_FILE_PROVIDER_SUFFIX = ".ai.fileprovider"
private const val SHARED_MEDIA_DIR = "ai_shared_media"
