package com.pencil.chuangyin.feature.pixiv.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.pencil.chuangyin.core.ui.data.OutputPathRepository
import com.pencil.chuangyin.feature.pixiv.di.PixivImageClient
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.resources.R as ResR
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** 下载任务运行状态。 */
enum class PixivDownloadStatus { RUNNING, DONE, FAILED }

/** 一个作品的下载任务（多页作品按页推进，完成后打包 zip）。 */
data class PixivDownloadTask(
    val illustId: Int,
    val title: String,
    val total: Int,
    val done: Int,
    val status: PixivDownloadStatus,
    val message: String? = null,
)

/** 本地保存文件的内容类型。 */
enum class PixivSavedKind {
    IMAGE,
    /** 多图 zip 打包（手动下载的长期保存）。 */
    ARCHIVE,
    /** 多页散图聚合（打印缓存同作品多页，非 zip，可在预览里左右滑）。 */
    MULTI_IMAGE,
    NOVEL,
}

/**
 * 本地保存文件的分类：
 * - [DOWNLOAD]：用户手动「下载全部原图」/「下载小说」产生的长期保存；
 * - [PRINT_CACHE]：打印流程自动落盘的缓存（随「清除缓存」一起回收）。
 */
enum class PixivSavedCategory { DOWNLOAD, PRINT_CACHE }

/** 图片管理页展示的一条本地保存记录。 */
data class PixivSavedImage(
    val file: File,
    val illustId: Int,
    /** 单图页码（0 起）；zip / 小说无页码时为 -1。 */
    val pageIndex: Int,
    /** 页数：单图 1；zip 为内含页数；小说 0。 */
    val pageCount: Int,
    val sizeBytes: Long,
    val savedAt: Long,
    val kind: PixivSavedKind = PixivSavedKind.IMAGE,
    val category: PixivSavedCategory = PixivSavedCategory.DOWNLOAD,
    /** 小说标题（NOVEL 时从文件首行解析），其余为空。 */
    val label: String? = null,
    /** 多页散图聚合时的全部页文件（[PixivSavedKind.MULTI_IMAGE]），zip/单图为空。 */
    val files: List<File> = emptyList(),
)

/**
 * P 站原图下载管理（侧边栏「下载进度」「图片管理」的数据源）。
 * 与 [PixivImageDownloader] 的区别：落 filesDir 长期保存（清缓存不会删），
 * 任务进度通过 StateFlow 实时推给 UI，已保存文件可在图片管理页查看/分享/删除。
 *
 * 保存规则：
 * - 多图作品（插图/漫画）一律打包为 `pixiv_{id}.zip` 单文件（REQ8），查看时临时解压；
 * - 单图作品落 `pixiv_{id}_p0.{ext}`；
 * - 小说落 `pixiv_novel_{id}.txt`；
 * - 打印流程产生的缓存文件（cacheDir/pixiv_print）以 PRINT_CACHE 分类并入图片管理（REQ6），
 *   与手动下载（DOWNLOAD）分开展示。
 *
 * 下载跟随进程生命周期，故使用应用级守护协程作用域。
 */
@Singleton
class PixivDownloadManager @Inject constructor(
    @PixivImageClient private val imageClient: OkHttpClient,
    @ApplicationContext private val context: Context,
    private val outputPathRepository: OutputPathRepository,
) {
    private val dir: File = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }
    private val printCacheDir: File = File(context.cacheDir, PRINT_CACHE_DIR)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<List<PixivDownloadTask>>(emptyList())
    val tasks: StateFlow<List<PixivDownloadTask>> = _tasks.asStateFlow()

    private val _savedImages = MutableStateFlow<List<PixivSavedImage>>(emptyList())
    val savedImages: StateFlow<List<PixivSavedImage>> = _savedImages.asStateFlow()

    init {
        refreshSavedImages()
    }

    /** 入队下载；同一作品正在下载或已下载完成时直接忽略，失败的可重新入队。 */
    fun enqueue(illust: PixivIllust) {
        var shouldStart = false
        _tasks.update { list ->
            val existing = list.firstOrNull { it.illustId == illust.id }
            if (existing != null && existing.status != PixivDownloadStatus.FAILED) {
                shouldStart = false
                list
            } else {
                shouldStart = true
                list.filterNot { it.illustId == illust.id } + PixivDownloadTask(
                    illustId = illust.id,
                    title = illust.title,
                    total = illust.pages.size,
                    done = 0,
                    status = PixivDownloadStatus.RUNNING,
                )
            }
        }
        if (shouldStart) appScope.launch { runDownload(illust) }
    }

    private suspend fun runDownload(illust: PixivIllust) {
        var failureMessage: String? = null
        val total = illust.pages.size
        fun updateDone(done: Int) {
            _tasks.update { list ->
                list.map { task ->
                    if (task.illustId == illust.id) task.copy(done = done) else task
                }
            }
        }
        val result = runCatching {
            withContext(Dispatchers.IO) {
                if (total <= 0) {
                    error(context.getString(ResR.string.pixiv_download_empty_pages))
                } else if (total > 1) {
                    downloadArchive(illust) { done -> updateDone(done) }
                } else {
                    val pageFile = downloadPageFile(illust.id, 0, illust.pages[0].original)
                    mirrorImageToPublic(pageFile)
                    updateDone(1)
                }
            }
        }
        result.onFailure { failureMessage = it.message ?: context.getString(ResR.string.pixiv_download_failed) }
        _tasks.update { list ->
            list.map { task ->
                if (task.illustId != illust.id) return@map task
                task.copy(
                    done = if (failureMessage == null) total else task.done,
                    status = if (failureMessage == null) PixivDownloadStatus.DONE else PixivDownloadStatus.FAILED,
                    message = failureMessage,
                )
            }
        }
        refreshSavedImages()
    }

    /** 多图作品：逐页下载到临时目录，全部完成后打包为单个 zip（REQ8）。 */
    private fun downloadArchive(illust: PixivIllust, onProgress: (Int) -> Unit): File {
        val zip = File(dir, "pixiv_${illust.id}.zip")
        if (zip.exists() && zip.length() > 0L) return zip

        val tempDir = File(context.cacheDir, "pixiv_zip_tmp_${illust.id}").apply { mkdirs() }
        try {
            val pageFiles = illust.pages.mapIndexed { index, page ->
                val pageFile = downloadPageFile(illust.id, index, page.original, tempDir)
                mirrorImageToPublic(pageFile)
                onProgress(index + 1)
                pageFile
            }
            zip.outputStream().use { fos ->
                ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
                    pageFiles.forEachIndexed { index, file ->
                        zos.putNextEntry(ZipEntry("p${index + 1}.${file.extension}"))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
        return zip
    }

    /** 下载单页原图到目标目录（默认下载根目录）。 */
    private fun downloadPageFile(illustId: Int, index: Int, url: String, targetDir: File = dir): File {
        val extension = url.substringAfterLast('.', "jpg").take(4).lowercase()
        val target = File(targetDir, "pixiv_${illustId}_p$index.$extension")
        if (target.exists() && target.length() > 0L) return target

        val response = imageClient.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            require(it.isSuccessful) { context.getString(ResR.string.pixiv_download_http_error, it.code) }
            val body = it.body ?: error(context.getString(ResR.string.pixiv_download_empty_body))
            target.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        return target
    }

    /** 从任务列表移除（仅清记录，已下载文件保留）。 */
    fun removeTask(illustId: Int) {
        _tasks.update { list -> list.filterNot { it.illustId == illustId } }
    }

    /** REQ5：小说下载完成后登记一条已完成任务，使其在「下载进度」页可见。 */
    fun enqueueNovelDone(novelId: Int, title: String) {
        _tasks.update { list ->
            list.filterNot { it.illustId == novelId } + PixivDownloadTask(
                illustId = novelId,
                title = title,
                total = 1,
                done = 1,
                status = PixivDownloadStatus.DONE,
            )
        }
    }

    /** 重新扫描已保存文件（启动/删除/打印后调用），手动下载与打印缓存分目录归类。 */
    fun refreshSavedImages() {
        runCatching {
            val downloads = dir.listFiles().orEmpty()
                .filter { it.isFile && it.length() > 0L }
                .mapNotNull { classifyDownload(it) }
            val printCache = aggregatePrintCache(
                printCacheDir.listFiles().orEmpty()
                    .filter { it.isFile && it.length() > 0L },
            )
            _savedImages.value = (downloads + printCache).sortedByDescending { it.savedAt }
        }
    }

    /**
     * 打印缓存按作品聚合并去重：同一 illustId 的多页散图聚合成单个「多图」记录，
     * 使图片管理页像压缩包一样获得封面（第一页）+多图角标+左右滑预览；
     * 单页作品保持单张 [PixivSavedKind.IMAGE] 记录。
     */
    private fun aggregatePrintCache(files: List<File>): List<PixivSavedImage> {
        return files
            .mapNotNull { classifyPrintCache(it) }
            .groupBy { it.illustId }
            .values
            .map { group ->
                val sorted = group.sortedBy { it.pageIndex }
                val pageFiles = sorted.map { it.file }
                if (sorted.size > 1) {
                    PixivSavedImage(
                        file = pageFiles.first(),
                        illustId = sorted.first().illustId,
                        pageIndex = sorted.first().pageIndex,
                        pageCount = pageFiles.size,
                        sizeBytes = pageFiles.sumOf { it.length() },
                        savedAt = pageFiles.maxOf { it.lastModified() },
                        kind = PixivSavedKind.MULTI_IMAGE,
                        category = PixivSavedCategory.PRINT_CACHE,
                        files = pageFiles,
                    )
                } else {
                    sorted.first()
                }
            }
    }

    private fun classifyDownload(file: File): PixivSavedImage? {
        val imageMatch = IMAGE_NAME_PATTERN.matchEntire(file.nameWithoutExtension)
        if (imageMatch != null && file.extension != "zip") {
            return PixivSavedImage(
                file = file,
                illustId = imageMatch.groupValues[1].toIntOrNull() ?: return null,
                pageIndex = imageMatch.groupValues[2].toIntOrNull() ?: 0,
                pageCount = 1,
                sizeBytes = file.length(),
                savedAt = file.lastModified(),
                kind = PixivSavedKind.IMAGE,
                category = PixivSavedCategory.DOWNLOAD,
            )
        }
        val zipMatch = ZIP_NAME_PATTERN.matchEntire(file.name)
        if (zipMatch != null) {
            return PixivSavedImage(
                file = file,
                illustId = zipMatch.groupValues[1].toIntOrNull() ?: return null,
                pageIndex = -1,
                pageCount = zipPageCount(file),
                sizeBytes = file.length(),
                savedAt = file.lastModified(),
                kind = PixivSavedKind.ARCHIVE,
                category = PixivSavedCategory.DOWNLOAD,
            )
        }
        val novelMatch = NOVEL_NAME_PATTERN.matchEntire(file.nameWithoutExtension)
        if (novelMatch != null) {
            return PixivSavedImage(
                file = file,
                illustId = novelMatch.groupValues[1].toIntOrNull() ?: return null,
                pageIndex = -1,
                pageCount = 0,
                sizeBytes = file.length(),
                savedAt = file.lastModified(),
                kind = PixivSavedKind.NOVEL,
                category = PixivSavedCategory.DOWNLOAD,
                label = file.useLines { it.firstOrNull() },
            )
        }
        return null
    }

    private fun classifyPrintCache(file: File): PixivSavedImage? {
        val match = IMAGE_NAME_PATTERN.matchEntire(file.nameWithoutExtension) ?: return null
        return PixivSavedImage(
            file = file,
            illustId = match.groupValues[1].toIntOrNull() ?: return null,
            pageIndex = match.groupValues[2].toIntOrNull() ?: 0,
            pageCount = 1,
            sizeBytes = file.length(),
            savedAt = file.lastModified(),
            kind = PixivSavedKind.IMAGE,
            category = PixivSavedCategory.PRINT_CACHE,
        )
    }

    private fun zipPageCount(zip: File): Int = runCatching {
        ZipFile(zip).use { it.size() }
    }.getOrDefault(0)

    fun deleteImage(image: PixivSavedImage) {
        runCatching {
            image.files.ifEmpty { listOf(image.file) }.forEach { runCatching { it.delete() } }
        }
        refreshSavedImages()
    }

    fun deleteAllImages() {
        dir.listFiles().orEmpty().forEach { runCatching { it.delete() } }
        printCacheDir.listFiles().orEmpty().forEach { runCatching { it.delete() } }
        refreshSavedImages()
    }

    /** 已保存图片所在目录（分享走 FileProvider）。 */
    fun downloadDir(): File = dir

    /** 保存小说正文为本地文本文件（小说下载）。重复保存时覆盖，返回保存文件。 */
    fun saveNovel(novelId: Int, title: String, text: String): File {
        val target = File(dir, "pixiv_novel_${novelId}.txt")
        target.writeText("$title\n\n$text")
        refreshSavedImages()
        enqueueNovelDone(novelId, title)
        return target
    }

    /** zip 建档快捷查看：临时解压到缓存目录并返回页文件列表（离开时调用 [clearViewCache] 回收）。 */
    suspend fun extractArchive(image: PixivSavedImage): List<File> = withContext(Dispatchers.IO) {
        val viewDir = viewDirFor(image.illustId)
        viewDir.deleteRecursively()
        viewDir.mkdirs()
        runCatching {
            ZipFile(image.file).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .sortedBy { it.name }
                    .map { entry ->
                        val out = File(viewDir, entry.name)
                        zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                        out
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    /** 清理某个 zip 的临时解压目录。 */
    fun clearViewCache(illustId: Int) {
        runCatching { viewDirFor(illustId).deleteRecursively() }
    }

    /** 取出 zip 的首个页文件，作为图片管理里的封面图（只解压首页，进入时才会完整解压）。 */
    suspend fun archiveFirstPageFile(image: PixivSavedImage): File? = withContext(Dispatchers.IO) {
        if (image.kind == PixivSavedKind.MULTI_IMAGE) {
            return@withContext image.files.sortedBy { it.name }.firstOrNull()?.takeIf { it.isFile }
                ?: image.file
        }
        val coverDir = coverDirFor(image.illustId)
        coverDir.listFiles().orEmpty().sortedBy { it.name }.firstOrNull()?.let { return@withContext it }
        runCatching {
            coverDir.mkdirs()
            ZipFile(image.file).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .sortedBy { it.name }
                    .firstOrNull()
                    ?.let { entry ->
                        val out = File(coverDir, entry.name)
                        zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                        out
                    }
            }
        }.getOrNull()
    }

    /**
     * 多图预览统一入口：压缩包解压全部页，多页散图聚合直接返回页文件列表，
     * 其余（单图/小说）返回自身文件。供图片管理页左右滑预览使用。
     */
    suspend fun openPages(image: PixivSavedImage): List<File> = withContext(Dispatchers.IO) {
        val pages = when (image.kind) {
            PixivSavedKind.ARCHIVE -> extractArchive(image)
            PixivSavedKind.MULTI_IMAGE -> image.files.sortedBy { it.name }
            else -> listOf(image.file)
        }
        pages.filter { it.isFile && it.length() > 0L }
    }

    /**
     * 把下载成功的单张图片镜像到配置的产物输出目录。
     * 若用户设置了完整绝对路径，则优先直接写入该真实目录（可下载到任意可写位置）；
     * 写入失败或未设置路径时，Android Q+ 走 MediaStore（默认 Pictures/PencilChuangyinPro），
     * API 29 及以下尝试拷贝到公共 Pictures 目录。
     * 失败不影响主流程（应用内部仍以 filesDir 为数据源）。
     */
    private fun mirrorImageToPublic(file: File): Boolean = runCatching {
        if (!file.exists() || !file.isFile) return false
        val displayName = file.name
        val customPath = outputPathRepository.outputPath.value
        var mirrored = false
        if (customPath.isNotBlank()) {
            mirrored = runCatching {
                val outFile = File(customPath, displayName)
                outFile.parentFile?.mkdirs()
                file.copyTo(outFile, overwrite = true)
                true
            }.getOrDefault(false)
        }
        if (mirrored) {
            true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relativePath = outputPathRepository.relativeMediaStorePath() + "/"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, imageMimeType(file))
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val outFile = outputPathRepository.activeOutputDirFile()
            outFile.parentFile?.mkdirs()
            val target = File(outFile, displayName)
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
            true
        }
    }.getOrDefault(false)

    private fun imageMimeType(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    private fun viewDirFor(illustId: Int): File = File(context.cacheDir, "${VIEW_TMP_DIR}_$illustId")

    private fun coverDirFor(illustId: Int): File = File(context.cacheDir, "${COVER_TMP_DIR}_$illustId")

    private companion object {
        const val DIR_NAME = "pixiv_downloads"
        const val PRINT_CACHE_DIR = "pixiv_print"
        const val VIEW_TMP_DIR = "pixiv_view"
        const val COVER_TMP_DIR = "pixiv_cover"
        val IMAGE_NAME_PATTERN = Regex("""pixiv_(\d+)_p(\d+)""")
        val ZIP_NAME_PATTERN = Regex("""pixiv_(\d+)\.zip""")
        val NOVEL_NAME_PATTERN = Regex("""pixiv_novel_(\d+)""")
    }
}