package com.pencil.chuangyin.core.scanner

import android.graphics.Bitmap
import android.graphics.PointF

/** 拍摄模式：常规拍摄原图直出；文档扫描走四步算法。 */
enum class ScanMode { Normal, Document }

/**
 * 文档扫描结果。
 *
 * @property output 最终可展示/打印的位图（常规模式=原图；扫描模式=白底纯黑图）。
 * @property detectedQuad 检测到的纸张四角（TL/TR/BR/BL，坐标系基于 [DocumentScanner.scan] 输入位图）；
 *   null 表示未检测到纸边，已降级为整图处理。
 * @property warped 是否执行了透视校正。
 * @property confidence 纸边检测置信度 0..1（检测失败为 0）。
 */
data class ScanResult(
    val output: Bitmap,
    val detectedQuad: List<PointF>?,
    val warped: Boolean,
    val confidence: Float,
)

/**
 * 本地文档扫描算法契约（纯本地，无网络）。
 *
 * 四步管线：纸张四角检测 → 四点透视校正 → 非均匀光照校正 → 形态学文字提取。
 * 实现不修改也不回收输入 [src]；输出新位图，调用方负责回收。
 * 必须在后台线程调用（含 OpenCV 重计算）。
 */
interface DocumentScanner {

    /**
     * 完整扫描。
     *
     * @param src 原始照片位图（ARGB）。
     * @param mode [ScanMode.Normal] 原样返回（不做任何处理）；[ScanMode.Document] 执行完整算法。
     */
    fun scan(src: Bitmap, mode: ScanMode): ScanResult

    /**
     * 仅检测纸张四角（供取景器实时叠加，轻量调用）。
     * 返回 TL/TR/BR/BL 四点；检测不到返回 null。坐标基于输入位图。
     */
    fun detectQuad(src: Bitmap): List<PointF>?

    /**
     * 按指定四角做透视裁剪校正（结果页手动裁剪复用）。
     * 输出四角 [quad] 映射到轴对齐矩形后的新位图（框外填白）。
     */
    fun cropWarp(src: Bitmap, quad: List<PointF>): Bitmap

    /**
     * 对"画面基本只有纸面"的图直接执行 光照校正 → 文字提取（不检测、不裁剪）。
     * 用于手动裁剪确认后的文档化收尾；对已是黑白的图幂等。
     */
    fun documentize(src: Bitmap): Bitmap
}
