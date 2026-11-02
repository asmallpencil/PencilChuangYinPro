package com.pencil.chuangyin.feature.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.pencil.chuangyin.core.ui.editor.PageLayerTransform
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 把「画布 + 单图片图层」的编辑结果合成为最终打印位图。
 *
 * 与预览使用同一套参数，保证所见即所得：
 * - 画布宽度固定为打印头宽度（384 点），高度随图层内容自动延长；
 * - 图层默认按画布宽度等比铺满（100%），可放大到 200%；
 * - 超出画布宽度的部分直接裁掉（屏幕上以灰色半透明区域提示）；
 * - 平移量是相对画布宽/高的倍数，跨设备一致。
 *
 * 后续创意工坊要支持多图层时，只需把这里扩展成图层列表，[PageLayerTransform] 的字段可直接复用。
 */
object ImagePageComposer {

    /** 打印头宽度（与核心打印管线一致）。 */
    const val PAGE_WIDTH_PX = 384

    /**
     * 合成一页。未做任何调整时原样返回 [source]，避免无谓的位图复制。
     */
    fun compose(source: Bitmap, transform: PageLayerTransform): Bitmap {
        val adjusted = transform.layerScale != 1f ||
            transform.offsetX != 0f ||
            transform.offsetY != 0f
        if (!adjusted) return source

        val baseRatio = PAGE_WIDTH_PX.toFloat() / source.width.toFloat().coerceAtLeast(1f)
        val drawWidth = source.width * baseRatio * transform.layerScale
        val drawHeight = source.height * baseRatio * transform.layerScale
        // 画布高度随内容延长：向下平移时画布一起变长，不会把内容裁在页尾；
        // 向上平移时画布保持内容高度，超出页首的部分被裁掉。
        val extraHeight = (drawHeight * transform.offsetY).coerceAtLeast(0f)
        val pageHeight = max(drawHeight + extraHeight, 1f).roundToInt()

        val page = Bitmap.createBitmap(PAGE_WIDTH_PX, pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

        val matrix = Matrix().apply {
            // 先缩放再平移：平移量按图层自身的显示高度计算，与画布延长量无关。
            setScale(drawWidth / source.width, drawHeight / source.height)
            postTranslate(
                (PAGE_WIDTH_PX - drawWidth) / 2f + transform.offsetX * PAGE_WIDTH_PX,
                transform.offsetY * drawHeight,
            )
        }
        canvas.drawBitmap(source, matrix, paint)
        return page
    }
}
