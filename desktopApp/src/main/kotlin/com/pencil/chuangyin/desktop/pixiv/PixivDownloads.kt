package com.pencil.chuangyin.desktop.pixiv

import com.pencil.chuangyin.desktop.support.OutputPathStore
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
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

/** 下载任务运行状态。 */
enum class PixivDownloadStatus { RUNNING, DONE, FAILED }

/** 一个作品的下载任务（多页作品按页推进，完成后打包 zip）。 */
data class PixivDownloadTask(
    val illustId: Long,
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
    val illustId: Long,
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
 * P 站原图下载管理（侧边栏「下载进度」「已保存内容」的数据源）——对齐安卓端 PixivDownloadManager：
 * - 多图作品一律打包为 `pixiv_{id}.zip` 单文件，查看时临时解压；
 * - 单图作品落 `pixiv_{id}_p0.{ext}`；
 * - 小说落 `pixiv_novel_{id}.txt`；
 * - 打印缓存（pixiv_print）以 PRINT_CACHE 分类并入「已保存内容」，与手动下载分开展示。
 *
 * 与安卓端差异：没有 MediaStore，镜像到输出目录改为直接拷贝文件。
 */
class PixivDownloadManager {

    private val dir: File = PixivPaths.downloadsDir.apply { if (!exists()) mkdirs() }
    private val printCacheDir: File = PixivPaths.printCacheDir

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 图片下载专用客户端：pximg 需要 Referer，安卓端同样走独立 OkHttp 实例。 */
    private val imageClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", PixivConfig.USER_AGENT)
                .header("Referer", PixivConfig.IMAGE_REFERER)
                .build()
            chain.proceed(request)
        }
        .build()

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
                    total = illust.pageUrls.size,
                    done = 0,
                    status = PixivDownloadStatus.RUNNING,
                )
            }
        }
        if (shouldStart) scope.launch { runDownload(illust) }
    }

    private suspend fun runDownload(illust: PixivIllust) {
        var failureMessage: String? = null
        val total = illust.pageUrls.size
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
                    error(EMPTY_PAGES_MESSAGE)
                } else if (total > 1) {
                    downloadArchive(illust) { done -> updateDone(done) }
                } else {
                    val pageFile = downloadPageFile(illust.id, 0, illust.pageUrls[0])
                    mirrorImageToPublic(pageFile)
                    updateDone(1)
                }
            }
        }
        result.onFailure { failureMessage = it.message ?: DOWNLOAD_FAILED_MESSAGE }
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

    /** 多图作品：逐页下载到临时目录，全部完成后打包为单个 zip。 */
    private fun downloadArchive(illust: PixivIllust, onProgress: (Int) -> Unit): File {
        val zip = File(dir, "pixiv_${illust.id}.zip")
        if (zip.exists() && zip.length() > 0L) return zip

        val tempDir = PixivPaths.zipTempDirFor(illust.id).apply { mkdirs() }
        try {
            val pageFiles = illust.pageUrls.mapIndexed { index, url ->
                val pageFile = downloadPageFile(illust.id, index, url, tempDir)
                mirrorImageToPublic(pageFile)
                onProgress(index + 1)
                pageFile
            }
            zip.parentFile?.mkdirs()
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
    private fun downloadPageFile(illustId: Long, index: Int, url: String, targetDir: File = dir): File {
        val extension = url.substringAfterLast('.', "jpg").take(4).lowercase()
        val target = File(targetDir, "pixiv_${illustId}_p$index.$extension")
        if (target.exists() && target.length() > 0L) return target

        val response = imageClient.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            require(it.isSuccessful) { "HTTP ${it.code}" }
            val body = it.body ?: error("响应体为空")
            target.parentFile?.mkdirs()
            target.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        return target
    }

    /** 从任务列表移除（仅清记录，已下载文件保留）。 */
    fun removeTask(illustId: Long) {
        _tasks.update { list -> list.filterNot { it.illustId == illustId } }
    }

    /** 小说下载完成后登记一条已完成任务，使其在「下载进度」页可见。 */
    fun enqueueNovelDone(novelId: Long, title: String) {
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

    /**
     * 打印前下载 —— 对齐安卓端 `PixivImageDownloader.downloadAllPages`：
     * 把作品全部原图落进打印缓存目录（`pixiv_print`），文件名 `pixiv_{id}_p{index}.{ext}`，
     * 之后由图片打印页接管，复用同一套打印设置（与安卓端 ShotTransferBus 交接等价）。
     */
    suspend fun downloadForPrint(
        illust: PixivIllust,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<File> = withContext(Dispatchers.IO) {
        val urls = illust.pageUrls
        require(urls.isNotEmpty()) { EMPTY_PAGES_MESSAGE }
        printCacheDir.mkdirs()
        val total = urls.size
        val files = urls.mapIndexed { index, url ->
            val file = downloadPageFile(illust.id, index, url, printCacheDir)
            onProgress(index + 1, total)
            file
        }
        refreshSavedImages()
        files
    }

    /** 重新扫描已保存文件，手动下载与打印缓存分目录归类。 */
    @Synchronized
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
     * 使「已保存内容」像压缩包一样获得封面（第一页）+多图角标+左右滑预览；
     * 单页作品保持单张 IMAGE 记录。
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
                illustId = imageMatch.groupValues[1].toLongOrNull() ?: return null,
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
                illustId = zipMatch.groupValues[1].toLongOrNull() ?: return null,
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
                illustId = novelMatch.groupValues[1].toLongOrNull() ?: return null,
                pageIndex = -1,
                pageCount = 0,
                sizeBytes = file.length(),
                savedAt = file.lastModified(),
                kind = PixivSavedKind.NOVEL,
                category = PixivSavedCategory.DOWNLOAD,
                label = runCatching { file.useLines { it.firstOrNull() } }.getOrNull(),
            )
        }
        return null
    }

    private fun classifyPrintCache(file: File): PixivSavedImage? {
        val match = IMAGE_NAME_PATTERN.matchEntire(file.nameWithoutExtension) ?: return null
        return PixivSavedImage(
            file = file,
            illustId = match.groupValues[1].toLongOrNull() ?: return null,
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

    /** 已保存图片所在目录。 */
    fun downloadDir(): File = dir

    /** 保存小说正文为本地文本文件（小说下载）。重复保存时覆盖，返回保存文件。 */
    fun saveNovel(novelId: Long, title: String, text: String): File {
        val target = File(dir, "pixiv_novel_${novelId}.txt")
        target.parentFile?.mkdirs()
        target.writeText("$title\n\n$text")
        refreshSavedImages()
        enqueueNovelDone(novelId, title)
        return target
    }

    /** zip 建档快捷查看：临时解压到缓存目录并返回页文件列表（离开时调用 [clearViewCache] 回收）。 */
    suspend fun extractArchive(image: PixivSavedImage): List<File> = withContext(Dispatchers.IO) {
        val viewDir = PixivPaths.viewDirFor(image.illustId)
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
    fun clearViewCache(illustId: Long) {
        runCatching { PixivPaths.viewDirFor(illustId).deleteRecursively() }
    }

    /** 取出 zip 的首个页文件，作为「已保存内容」里的封面图。 */
    suspend fun archiveFirstPageFile(image: PixivSavedImage): File? = withContext(Dispatchers.IO) {
        if (image.kind == PixivSavedKind.MULTI_IMAGE) {
            return@withContext image.files.sortedBy { it.name }.firstOrNull()?.takeIf { it.isFile }
                ?: image.file
        }
        val coverDir = PixivPaths.coverDirFor(image.illustId)
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
     * 其余（单图/小说）返回自身文件。
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
     * 把下载成功的图片镜像到「产物输出路径」。
     * 桌面端为普通文件拷贝，失败不影响主流程（应用内部仍以 pixiv_downloads 为数据源）。
     */
    private fun mirrorImageToPublic(file: File): Boolean = runCatching {
        if (!file.exists() || !file.isFile) return false
        val outDir = OutputPathStore.ensureDir(OutputPathStore.OutputDomain.PIXIV) ?: return false
        file.copyTo(File(outDir, file.name), overwrite = true)
        true
    }.getOrDefault(false)

    private companion object {
        const val EMPTY_PAGES_MESSAGE = "该作品没有可下载的页面"
        const val DOWNLOAD_FAILED_MESSAGE = "下载失败"
        val IMAGE_NAME_PATTERN = Regex("""pixiv_(\d+)_p(\d+)""")
        val ZIP_NAME_PATTERN = Regex("""pixiv_(\d+)\.zip""")
        val NOVEL_NAME_PATTERN = Regex("""pixiv_novel_(\d+)""")
    }
}
