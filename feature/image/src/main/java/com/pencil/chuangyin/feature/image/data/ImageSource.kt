package com.pencil.chuangyin.feature.image.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.pencil.chuangyin.feature.image.R
import java.io.File

/**
 * 可打印图片来源：统一抽象相册 URI、压缩包解压文件、PDF 页面三种来源。
 *
 * [load] 每次返回一张经过降采样的新位图，由打印控制器在用完后回收；
 * 这样批量打印上百页时内存中始终只有当前页。实现不得缓存大图。
 */
interface ImageSource {
    /** 用于展示/保存命名的标签。 */
    val name: String

    /** 解码为工作分辨率位图（长边 ≤ [MAX_SOURCE_EDGE]），失败抛异常由上层兜底。 */
    fun load(): Bitmap

    /** 释放底层资源（PDF 句柄等）；普通文件/URI 为空操作。 */
    fun close() {}

    companion object {
        /** 源图解码长边上限：远超 384 点打印宽度，保留细节又不占内存。 */
        const val MAX_SOURCE_EDGE = 2_400

        /** PDF 页面栅格化的目标宽度（约 300dpi@58mm，再交给处理器缩到 384）。 */
        const val PDF_RENDER_WIDTH = 1_200
    }
}

/** 相册/文件选择器 URI 来源。 */
class UriImageSource(
    private val context: Context,
    private val uri: Uri,
    override val name: String,
) : ImageSource {

    override fun load(): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error(context.getString(R.string.image_decode_failed, name))
        return capEdge(decoded)
    }
}

/** 压缩包解压到缓存目录后的文件来源。 */
class FileImageSource(
    private val context: Context,
    private val file: File,
    override val name: String,
) : ImageSource {

    override fun load(): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error(context.getString(R.string.image_decode_failed, name))
        return capEdge(decoded)
    }
}

/** PDF 单页来源：共享同一个 [PdfRenderer]，渲染时按页打开/关闭 Page（加锁，PdfRenderer 非线程安全）。 */
class PdfPageSource(
    private val renderer: PdfRenderer,
    private val pageIndex: Int,
    override val name: String,
) : ImageSource {

    override fun load(): Bitmap = synchronized(renderer) {
        renderer.openPage(pageIndex).use { page ->
            val scale = ImageSource.PDF_RENDER_WIDTH.toFloat() / page.width.toFloat().coerceAtLeast(1f)
            val targetWidth = ImageSource.PDF_RENDER_WIDTH
            val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            // 白底（PDF 透明区域按白纸处理）。
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }
}

/** 渲染 PDF 页缩略图（长边 ≤ [edge]），供选择页网格使用；与打印渲染共用同一把锁。 */
fun renderPdfThumbnail(renderer: PdfRenderer, pageIndex: Int, edge: Int = PDF_THUMB_EDGE): Bitmap? =
    runCatching {
        synchronized(renderer) {
            renderer.openPage(pageIndex).use { page ->
                val scale = edge.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }.getOrNull()

private const val PDF_THUMB_EDGE = 320

/** 持有 PDF 文件描述符与渲染器，随屏幕生命周期统一关闭。 */
class PdfDocumentBundle private constructor(
    private val pfd: ParcelFileDescriptor,
    val renderer: PdfRenderer,
) {
    val pageCount: Int get() = renderer.pageCount

    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    companion object {
        fun open(context: Context, uri: Uri): PdfDocumentBundle? = runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val renderer = try {
                PdfRenderer(pfd)
            } catch (t: Throwable) {
                runCatching { pfd.close() }
                throw t
            }
            PdfDocumentBundle(pfd, renderer)
        }.getOrNull()
    }
}

/** 按源边限制计算 BitmapFactory 采样率（2 的幂）。 */
internal fun calculateInSampleSize(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    var longEdge = maxOf(width, height)
    while (longEdge / 2 >= ImageSource.MAX_SOURCE_EDGE) {
        longEdge /= 2
        sample *= 2
    }
    return sample
}

/** 采样后仍超上限时再精确缩放一次。 */
internal fun capEdge(bitmap: Bitmap): Bitmap {
    val longEdge = maxOf(bitmap.width, bitmap.height)
    if (longEdge <= ImageSource.MAX_SOURCE_EDGE) return bitmap
    val scale = ImageSource.MAX_SOURCE_EDGE.toFloat() / longEdge
    val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
    if (scaled != bitmap) bitmap.recycle()
    return scaled
}
