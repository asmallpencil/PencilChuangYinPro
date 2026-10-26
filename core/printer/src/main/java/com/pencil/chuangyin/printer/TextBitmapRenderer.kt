package com.pencil.chuangyin.printer

import android.graphics.Bitmap
import android.graphics.Typeface

/**
 * 文本 → 可打印 Bitmap 渲染器接口。
 *
 * 面向巨量文本设计：不提供“一次渲染整篇超高 Bitmap”的入口（17 万字会 OOM/卡顿），
 * 而是用 [renderPages] 按固定页高流式产出 384 点宽的位图，逐页交给打印管线，
 * 任意时刻内存中只有一页，保证不卡死、不溢出。
 */
interface TextBitmapRenderer {

    /**
     * 流式分页渲染：对 [doc] 完成换行排版后，按打印页高切成多张位图，
     * 逐张回调 [onPage]。回调返回后该页位图可能被回收，调用方如需保留请自行拷贝。
     *
     * 对齐（含不足一行的短行居中/靠右）在逐行绘制时按实测行宽显式计算，确定生效。
     *
     * @param onPage (pageBitmap, pageIndex, pageCount)
     */
    fun renderPages(
        doc: TextDoc,
        typeface: Typeface?,
        onPage: (page: Bitmap, pageIndex: Int, pageCount: Int) -> Unit,
    )

    /**
     * 仅渲染文档顶部 [maxHeightPx] 像素用于 WYSIWYG 预览。
     * 长文本预览有界、快速；超长（如超过一万字）时 UI 层可直接不调用本方法。
     */
    fun renderPreview(doc: TextDoc, typeface: Typeface?, maxHeightPx: Int): Bitmap
}
