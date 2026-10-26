package com.pencil.chuangyin.feature.pixiv.data

import android.content.Context
import com.pencil.chuangyin.feature.pixiv.di.PixivImageClient
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.resources.R as ResR
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把作品原图下载到本地缓存文件，供打印管线（ShotTransferBus）消费。
 * 直接落盘原始字节（不做 Bitmap 解码，避免超大图 OOM），扩展名沿用 URL 后缀。
 */
@Singleton
class PixivImageDownloader @Inject constructor(
    @PixivImageClient private val imageClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {

    private val dir: File
        get() = File(context.cacheDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    suspend fun downloadAllPages(
        illust: PixivIllust,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<File> = downloadPages(illust, illust.pages.indices.toList(), onProgress)

    /**
     * 下载指定页（0 基页码，文件名沿用真实页码），供 Agent 工具「只下第 N 页」使用。
     *
     * 若按 [downloadAllPages] 单页子集下载，页码会退化为 0 而与首页缓存互相覆盖，
     * 故这里显式传入真实页码，保证不同页各自落盘、不串图。
     */
    suspend fun downloadPages(
        illust: PixivIllust,
        indices: List<Int>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<File> = withContext(Dispatchers.IO) {
        val total = indices.size
        indices.mapIndexed { position, index ->
            val page = illust.pages.getOrNull(index)
                ?: error("页码越界：$index（该作品共 ${illust.pages.size} 页）")
            val file = downloadOne(illust.id, index, page.original)
            onProgress(position + 1, total)
            file
        }
    }

    private fun downloadOne(illustId: Int, index: Int, url: String): File {
        val extension = url.substringAfterLast('.', "jpg").take(4).lowercase()
        val target = File(dir, "pixiv_${illustId}_p$index.$extension")
        if (target.exists() && target.length() > 0L) return target

        val response = imageClient.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            require(it.isSuccessful) { context.getString(ResR.string.pixiv_download_http_error, it.code) }
            val body = it.body ?: error(context.getString(ResR.string.pixiv_download_empty_body))
            target.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        return target
    }

    private companion object {
        const val DIR_NAME = "pixiv_print"
    }
}
