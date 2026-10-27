package com.pencil.chuangyin.feature.ai.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.os.Build
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.agent.runtime.ToolCallScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 视频元信息（时长/分辨率/旋转/码率/音轨等）。 */
internal data class VideoMeta(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val bitrateKbps: Long,
    val mimeType: String,
    val hasAudio: Boolean,
    val frameCount: Int,
    val frameRate: Double,
)

/** 已抽出的视频帧（文件 + 对应时间点）。 */
internal data class VideoFrame(val file: File, val timeMs: Long)

/**
 * 视频读取：基于系统 [MediaMetadataRetriever]，无需第三方库。
 *
 * 视频无法像文本一样直接给模型「看」，因此拆成两件事：
 * - [probe]：读出时长、分辨率、码率等元信息，供模型判断后续如何处理；
 * - [frames]：按时间点抽帧成 PNG，作为图片产物交给多模态理解（也可直接 print_image 打印）。
 */
internal object VideoReader {

    val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "mov", "avi", "wmv", "flv", "webm", "3gp", "3gpp",
        "mpeg", "mpg", "ts", "m2ts", "rmvb", "rm",
    )

    fun isVideoExtension(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.US) in VIDEO_EXTENSIONS

    /** 读取元信息；文件不是可解码视频时抛 [IllegalArgumentException]。 */
    fun probe(file: File): VideoMeta {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val width = retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
            val height = retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
            if (width <= 0 || height <= 0) {
                throw IllegalArgumentException("不是可解码的视频文件，或缺少视频轨")
            }
            val frameCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT).toInt()
            } else {
                0
            }
            val fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toDoubleOrNull() ?: 0.0
            return VideoMeta(
                durationMs = duration,
                width = width,
                height = height,
                rotation = retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt(),
                bitrateKbps = retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_BITRATE) / 1000L,
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty(),
                hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                frameCount = frameCount,
                frameRate = fps,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * 在 [startMs]..[endMs] 区间内均匀抽 [count] 帧，落盘到 [outputDir]。
     *
     * 使用 `OPTION_CLOSEST_SYNC` 取最近的同步帧，避免对长 GOP 视频逐帧解码导致超时；
     * 每帧最长边压到 [maxEdge]，既够模型看清画面，也不至于拖垮手机。
     */
    fun frames(
        file: File,
        count: Int,
        startMs: Long,
        endMs: Long,
        maxEdge: Int,
        outputDir: File,
    ): List<VideoFrame> {
        val meta = probe(file)
        if (meta.durationMs <= 0) throw IllegalArgumentException("视频时长为 0，无法抽帧")
        val total = count.coerceIn(1, MAX_FRAMES)
        val from = startMs.coerceIn(0L, meta.durationMs)
        val to = (if (endMs > from) endMs else meta.durationMs).coerceIn(from, meta.durationMs)
        val stamps = buildList {
            if (total == 1) {
                add((from + to) / 2)
            } else {
                val step = (to - from).toDouble() / (total - 1)
                repeat(total) { index -> add((from + step * index).toLong()) }
            }
        }

        outputDir.mkdirs()
        val result = ArrayList<VideoFrame>(stamps.size)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            stamps.forEachIndexed { index, timeMs ->
                val raw = runCatching {
                    retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }.getOrNull() ?: return@forEachIndexed
                val oriented = orient(raw, meta)
                val scaled = scaleDown(oriented, maxEdge)
                if (scaled !== oriented) oriented.recycle()
                val out = File(outputDir, "frame_${index + 1}_${System.currentTimeMillis()}.png")
                val saved = runCatching {
                    out.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }.isSuccess
                scaled.recycle()
                if (saved && out.isFile) result += VideoFrame(out, timeMs)
            }
        } finally {
            runCatching { retriever.release() }
        }
        return result
    }

    /** 部分系统返回的帧未应用旋转元数据：仅在帧尺寸等于原始尺寸时按 rotation 校正。 */
    private fun orient(bitmap: Bitmap, meta: VideoMeta): Bitmap {
        val degrees = ((meta.rotation % 360) + 360) % 360
        if (degrees == 0 || degrees % 180 == 0) return bitmap
        // 已经被系统旋转过的帧，其宽高相对原始元数据是交换的，避免重复旋转。
        if (bitmap.width == meta.height && bitmap.height == meta.width) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge || longest <= 0) return bitmap
        val ratio = maxEdge.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(bitmap, width, height, true) }.getOrDefault(bitmap)
    }

    private fun MediaMetadataRetriever.metadataLong(key: Int): Long =
        extractMetadata(key)?.toLongOrNull() ?: 0L

    const val MAX_FRAMES = 16
}

/** 把 [VideoMeta] 渲染成便于模型阅读的文本。 */
internal fun VideoMeta.describe(display: String): String = buildString {
    appendLine("文件 $display 是视频：")
    appendLine("- 时长：${formatDuration(durationMs)}（$durationMs 毫秒）")
    appendLine("- 分辨率：$width×$height" + if (rotation % 180 != 0) "（旋转 $rotation°）" else "")
    appendLine("- 码率：${bitrateKbps} kbps · 容器/编码：${mimeType.ifBlank { "未知" }}")
    appendLine("- 音轨：" + if (hasAudio) "有" else "无")
    if (frameCount > 0) appendLine("- 总帧数：约 $frameCount" + if (frameRate > 0) "（${"%.2f".format(Locale.US, frameRate)} fps）" else "")
    append("查看画面内容请用 extract_video_frames 抽帧（抽出的图片可直接打印）。")
}.trim()

/** 毫秒转 `mm:ss(.ms)`。 */
internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = ms % 1000
    return "%02d:%02d.%03d".format(Locale.US, minutes, seconds, millis)
}

/**
 * 视频工具：读元信息、按时间点抽帧。
 *
 * 抽出的帧既作为 IMAGE 产物交给多模态理解，也会复制进工作区，方便随后 print_image 打印。
 */
@Singleton
class VideoTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workspace: Workspace,
    private val scope: ToolCallScope,
) {

    /** 读取视频元信息（时长、分辨率、码率、音轨等），不产生副作用。 */
    @Tool
    suspend fun video_info(
        @Param(description = "视频文件路径；相对路径以工作区为基准，也可用可读范围内的绝对路径。") path: String,
    ): ToolOutput {
        val source = runCatching { workspace.resolveForRead(path.trim()) }
            .getOrElse { return ToolOutput(it.message ?: "视频路径无效", isError = true) }
        if (source.isDirectory) return ToolOutput("${workspace.displayPath(source)} 是目录，不是视频文件。", isError = true)
        val readable = workspace.materialize(source)
            ?: return ToolOutput("读取失败：无法打开 ${workspace.displayPath(source)}。", isError = true)
        val display = workspace.displayPath(source)
        return withContext(Dispatchers.IO) {
            var error: Throwable? = null
            val meta = runCatching { VideoReader.probe(readable) }
                .onFailure { error = it }
                .getOrNull()
                ?: return@withContext ToolOutput(
                    "无法解析视频：${error?.message ?: "编码不受支持或缺少视频轨"}",
                    isError = true,
                )
            ToolOutput(meta.describe(display))
        }
    }

    /**
     * 按时间点均匀抽帧，返回图片产物与工作区内的落盘路径（可直接 print_image 打印）。
     */
    @Tool
    suspend fun extract_video_frames(
        @Param(description = "视频文件路径；相对路径以工作区为基准。") path: String,
        @Param(description = "抽几帧，默认 6，上限 ${VideoReader.MAX_FRAMES}。") count: Int?,
        @Param(description = "起始时间（毫秒），默认 0。") start_ms: Int?,
        @Param(description = "结束时间（毫秒），默认视频末尾。") end_ms: Int?,
        @Param(description = "每帧最长边像素，默认 1280，范围 320-2048。") max_edge: Int?,
    ): ToolOutput {
        val source = runCatching { workspace.resolveForRead(path.trim()) }
            .getOrElse { return ToolOutput(it.message ?: "视频路径无效", isError = true) }
        if (source.isDirectory) return ToolOutput("${workspace.displayPath(source)} 是目录，不是视频文件。", isError = true)
        val readable = workspace.materialize(source)
            ?: return ToolOutput("读取失败：无法打开 ${workspace.displayPath(source)}。", isError = true)

        val display = workspace.displayPath(source)
        val frameCount = (count ?: DEFAULT_FRAMES).coerceIn(1, VideoReader.MAX_FRAMES)
        val edge = (max_edge ?: DEFAULT_MAX_EDGE).coerceIn(MIN_EDGE, MAX_EDGE)
        scope.current?.reportProgress("正在抽取视频画面")

        var error: Throwable? = null
        val frames = withContext(Dispatchers.IO) {
            runCatching {
                VideoReader.frames(
                    file = readable,
                    count = frameCount,
                    startMs = start_ms?.toLong() ?: 0L,
                    endMs = end_ms?.toLong() ?: 0L,
                    maxEdge = edge,
                    outputDir = File(context.cacheDir, "video_frames"),
                )
            }.onFailure { error = it }.getOrNull()
        } ?: return ToolOutput("抽帧失败：${error?.message ?: "无法解码该视频"}", isError = true)

        if (frames.isEmpty()) {
            return ToolOutput("未能从 $display 抽出画面（可能是编码不受支持或区间内没有同步帧）。", isError = true)
        }
        val builder = StringBuilder()
        builder.appendLine("已从 $display 抽出 ${frames.size} 帧（按时间顺序，画面已随本次结果提供，可直接查看）：")
        val artifacts = ArrayList<ToolArtifact>(frames.size)
        frames.forEachIndexed { index, frame ->
            // 产物留在缓存供多模态读取，同时复制一份进工作区便于用户打印/长期保留。
            val stored = workspace.uniqueTarget("${source.nameWithoutExtension}_frame_${index + 1}.png")
            val copied = workspace.copyInto(frame.file, stored)
            val shownPath = if (copied) workspace.displayPath(stored) else frame.file.absolutePath
            builder.appendLine("${index + 1}. ${formatDuration(frame.timeMs)} → $shownPath")
            artifacts += ToolArtifact(
                id = UUID.randomUUID().toString(),
                kind = ToolArtifactKind.IMAGE,
                path = frame.file.absolutePath,
                label = "画面 ${index + 1} · ${formatDuration(frame.timeMs)}",
            )
        }
        builder.append("需要打印某一帧时，用 print_image 传入对应路径。")
        return ToolOutput(builder.toString().trim(), artifacts = artifacts)
    }

    private companion object {
        const val DEFAULT_FRAMES = 6
        const val DEFAULT_MAX_EDGE = 1280
        const val MIN_EDGE = 320
        const val MAX_EDGE = 2048
    }
}
