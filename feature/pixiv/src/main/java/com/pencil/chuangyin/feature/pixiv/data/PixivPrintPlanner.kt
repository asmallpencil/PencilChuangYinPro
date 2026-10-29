package com.pencil.chuangyin.feature.pixiv.data

import android.content.Context
import com.pencil.chuangyin.resources.R as ResR
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P 站本地内容 → 打印管线的分流结果。
 *
 * - [Images]：可直接交给图片打印页的页文件，**顺序即打印顺序**；
 * - [Novels]：合并后的纯文本，交给文本打印页；
 * - [Mixed]：小说与图片混选——两条打印管线互不兼容，调用方应禁用打印按钮；
 * - [Empty]：没有有效内容。
 */
sealed interface PixivPrintPlan {
    data class Images(val files: List<File>) : PixivPrintPlan
    data class Novels(val title: String, val content: String) : PixivPrintPlan
    data object Mixed : PixivPrintPlan
    data object Empty : PixivPrintPlan
}

/**
 * 把「已保存内容」中的任意选择（单图 / 多图压缩包 / 散图聚合 / 小说）展开为打印批次。
 *
 * 顺序规则（与用户在多选模式里看到的序号一致）：
 * 1. 按选择先后逐个作品处理；
 * 2. 单个作品内部按页码升序展开（压缩包按条目名排序，散图按文件页号排序）；
 * 3. 展开后的页文件按全局序号重命名后复制到独立批次目录，交给图片打印页后
 *    不会因为压缩包临时目录回收而失效。
 *
 * 该 Planner 不持有任何 UI 状态，可被任意入口复用（图库页、打印页仓库入口、AI 工具）。
 */
@Singleton
class PixivPrintPlanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: PixivDownloadManager,
) {

    /** 当前选择是否可打印（非空且未混选小说与图片）。 */
    fun canPrint(items: List<PixivSavedImage>): Boolean {
        if (items.isEmpty()) return false
        val hasNovel = items.any { it.kind == PixivSavedKind.NOVEL }
        val hasImage = items.any { it.kind != PixivSavedKind.NOVEL }
        return !(hasNovel && hasImage)
    }

    /** 是否为纯小说选择（决定跳文本打印页还是图片打印页）。 */
    fun isNovelSelection(items: List<PixivSavedImage>): Boolean =
        items.isNotEmpty() && items.all { it.kind == PixivSavedKind.NOVEL }

    /** 展开选择为打印批次；IO 线程执行，失败时返回 [PixivPrintPlan.Empty]。 */
    suspend fun plan(items: List<PixivSavedImage>): PixivPrintPlan = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext PixivPrintPlan.Empty
        val hasNovel = items.any { it.kind == PixivSavedKind.NOVEL }
        val hasImage = items.any { it.kind != PixivSavedKind.NOVEL }
        when {
            hasNovel && hasImage -> PixivPrintPlan.Mixed
            hasNovel -> planNovels(items)
            else -> planImages(items)
        }
    }

    private suspend fun planImages(items: List<PixivSavedImage>): PixivPrintPlan {
        val batchDir = newBatchDir()
        val output = ArrayList<File>(items.sumOf { it.pageCount.coerceAtLeast(1) })
        items.forEach { item ->
            val pages = runCatching { downloadManager.openPages(item) }.getOrDefault(emptyList())
                .filter { it.isFile && it.length() > 0L }
            pages.forEach { page ->
                val extension = page.extension.ifBlank { "jpg" }
                val target = File(batchDir, "p%04d.%s".format(output.size, extension))
                runCatching { page.copyTo(target, overwrite = true) }
                    .onSuccess { if (it.length() > 0L) output += it }
            }
        }
        return if (output.isEmpty()) PixivPrintPlan.Empty else PixivPrintPlan.Images(output)
    }

    private fun planNovels(items: List<PixivSavedImage>): PixivPrintPlan {
        val bodies = items.mapNotNull { item ->
            val raw = runCatching { item.file.readText() }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            stripHeader(raw, item.label)
        }
        if (bodies.isEmpty()) return PixivPrintPlan.Empty
        val title = if (bodies.size == 1) {
            items.first().label?.takeIf { it.isNotBlank() }
                ?: context.getString(ResR.string.pixiv_gallery_novel_title_plain, items.first().illustId)
        } else {
            context.getString(ResR.string.pixiv_gallery_print_novel_merged_title, bodies.size)
        }
        val content = bodies.joinToString(separator = NOVEL_SEPARATOR)
        return PixivPrintPlan.Novels(title = title.trim(), content = content)
    }

    /**
     * 去掉本地小说文件头部的标题行（保存格式为「标题\n\n正文」）。
     * 只在文件确实以标题开头时剥离，避免把正文首行误当标题删掉。
     */
    private fun stripHeader(raw: String, label: String?): String {
        if (label.isNullOrBlank()) return raw
        if (!raw.startsWith(label)) return raw
        return raw.removePrefix(label).trimStart('\n')
    }

    /** 每批独立目录：多次打印不会互相覆盖，旧批次按时间清理。 */
    private fun newBatchDir(): File {
        val root = File(context.cacheDir, PREP_DIR)
        root.mkdirs()
        cleanupOldBatches(root)
        return File(root, "batch_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    private fun cleanupOldBatches(root: File) {
        val deadline = System.currentTimeMillis() - BATCH_TTL_MS
        root.listFiles().orEmpty()
            .filter { it.isDirectory && it.lastModified() < deadline }
            .forEach { runCatching { it.deleteRecursively() } }
    }

    private companion object {
        const val PREP_DIR = "pixiv_print_prep"
        const val BATCH_TTL_MS = 24L * 60 * 60 * 1000
        const val NOVEL_SEPARATOR = "\n\n——\n\n"
    }
}
