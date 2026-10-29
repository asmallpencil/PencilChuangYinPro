package com.pencil.chuangyin.core.agent.aiimage

import kotlinx.serialization.Serializable

/**
 * 拍照 AI 处理的被摄类型。
 *
 * - [RealScene]：实景照片（人、物、风景），AI 走智能混合增强，目标是"真实且细节充足的打印图"；
 * - [Document]：文稿纸面，AI 先锁定纸张四角做透视拉直，再自行决定文稿美白或混合增强。
 */
enum class AiImageScene {
    RealScene,
    Document,
    ;

    companion object {
        /** 防御式反序列化：未知/空值回落到文稿（打印场景里文稿占比最高）。 */
        fun fromStored(raw: String?): AiImageScene =
            entries.firstOrNull { it.name == raw } ?: Document
    }
}

/**
 * AI 图片处理专属设置。
 *
 * 模型接口与「AI 打印」共用 [com.pencil.chuangyin.core.agent.config.AiConfigRepository]，
 * 这里只放图片处理管线自己的参数：默认场景、输出最长边、两种算法的强度与失败回退策略。
 */
@Serializable
data class AiImageSettings(
    /** 进入拍照页时默认选中的被摄类型。 */
    val defaultScene: AiImageScene = AiImageScene.Document,
    /** 处理结果的最长边像素（越大越细，但打印前会被打印机降采样）。 */
    val outputMaxEdgePx: Int = DEFAULT_OUTPUT_EDGE_PX,
    /** 文稿美白算法强度（0=几乎不改动，1=最强去底色）。 */
    val whitenStrength: Float = DEFAULT_WHITEN_STRENGTH,
    /** 智能混合增强强度（0=不增强，1=最强调色 + 锐化）。 */
    val enhanceStrength: Float = DEFAULT_ENHANCE_STRENGTH,
    /** AI 不可用/失败时是否自动回退到内置算法（关闭后直接报错，由用户决定）。 */
    val fallbackToLocal: Boolean = true,
) {
    /** 把任意来源（滑杆/旧数据/手改文件）的数值收敛到合法区间，避免管线拿到越界参数。 */
    fun sanitized(): AiImageSettings = copy(
        outputMaxEdgePx = outputMaxEdgePx.coerceIn(
            AiImageSettingsBounds.OUTPUT_EDGE_MIN_PX,
            AiImageSettingsBounds.OUTPUT_EDGE_MAX_PX,
        ),
        whitenStrength = whitenStrength.coerceIn(
            AiImageSettingsBounds.STRENGTH_MIN,
            AiImageSettingsBounds.STRENGTH_MAX,
        ),
        enhanceStrength = enhanceStrength.coerceIn(
            AiImageSettingsBounds.STRENGTH_MIN,
            AiImageSettingsBounds.STRENGTH_MAX,
        ),
    )

    companion object {
        const val DEFAULT_OUTPUT_EDGE_PX = 2000
        const val DEFAULT_WHITEN_STRENGTH = 0.7f
        const val DEFAULT_ENHANCE_STRENGTH = 0.6f
    }
}

/** 各参数的合法区间（设置页滑杆与管线收敛共用同一份定义）。 */
object AiImageSettingsBounds {
    const val OUTPUT_EDGE_MIN_PX = 800
    const val OUTPUT_EDGE_MAX_PX = 3000
    const val STRENGTH_MIN = 0f
    const val STRENGTH_MAX = 1f
}
