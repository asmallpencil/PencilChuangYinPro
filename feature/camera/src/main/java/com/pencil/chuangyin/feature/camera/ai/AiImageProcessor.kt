package com.pencil.chuangyin.feature.camera.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.pencil.chuangyin.core.agent.aiimage.AiImageScene
import com.pencil.chuangyin.core.agent.aiimage.AiImageSettings
import com.pencil.chuangyin.core.agent.aiimage.AiImageSettingsRepository
import com.pencil.chuangyin.core.agent.config.AiConfigRepository
import com.pencil.chuangyin.core.agent.model.ChatMessage
import com.pencil.chuangyin.core.agent.model.ChatRequest
import com.pencil.chuangyin.core.agent.model.ChatRole
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.openai.OpenAiChatClient
import com.pencil.chuangyin.core.scanner.DocumentScanner
import com.pencil.chuangyin.core.scanner.ScanMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/** AI 对图片采取的增强策略（模型只决定"用哪种"，强度仍由用户设置控制）。 */
enum class AiEnhanceMode {
    /** 不改动像素。 */
    None,

    /** 文稿美白（灰度 + levels + 提亮）。 */
    Whiten,

    /** 智能混合增强（调色 + 锐化）。 */
    Mixed,
    ;

    companion object {
        /** 兼容模型可能返回的各种写法（英文 / 中文 / 下划线）。 */
        fun fromRaw(raw: String?): AiEnhanceMode {
            val normalized = raw?.trim()?.lowercase()?.replace('-', '_') ?: return Mixed
            return when {
                normalized.contains("none") || normalized.contains("keep") ||
                    raw.contains("不变") || raw.contains("原图") -> None
                normalized.contains("whiten") || normalized.contains("white") ||
                    normalized.contains("binar") || raw.contains("美白") ||
                    raw.contains("黑白") -> Whiten
                else -> Mixed
            }
        }
    }
}

/**
 * AI 决策出的处理方案。
 *
 * 模型只产出参数，所有像素操作都由 [ImageOps] / [DocumentScanner] 在本地执行，
 * 因而模型输出不可信也不会直接改动图片。
 */
data class AiProcessPlan(
    /** 纸张四角（归一化 0~1，TL/TR/BR/BL）；null 表示模型认为不该裁切。 */
    val quad: List<PointF>?,
    /** 是否建议做透视拉直。 */
    val warp: Boolean,
    /** 增强策略。 */
    val enhance: AiEnhanceMode,
    /** 亮度 / 对比度 / 饱和度建议值（-1~1）。 */
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    /** 模型给出的工具调用序列（仅用于处理日志展示）。 */
    val tools: List<String>,
    /** 模型给出的一句话理由。 */
    val notes: String,
)

/** 处理产物。 */
data class AiProcessResult(
    /** 最终位图（新位图，调用方持有所有权）。 */
    val bitmap: Bitmap,
    /** AI 方案；完全没走通模型时为 null。 */
    val plan: AiProcessPlan?,
    /** 是否由模型参与决策（false = 内置算法兜底）。 */
    val decidedByModel: Boolean,
)

/**
 * 拍照 AI 处理契约。
 *
 * 与「AI 打印」的 Agent 完全不同：这里没有会话、没有聊天记录、没有多轮工具调用，
 * 一轮"看图 → 给 JSON 方案 → 本地执行工具链"即结束。
 */
interface AiImageProcessor {

    /**
     * 处理一张拍摄/导入的原图。
     *
     * 必须在后台线程调用；[onLog] 会在处理过程中多次回调（可来自任意线程）。
     * 输入 [source] 不会被修改或回收；内部中间位图由实现自行回收。
     */
    suspend fun process(
        source: Bitmap,
        scene: AiImageScene,
        onLog: (String) -> Unit,
    ): AiProcessResult
}

/**
 * 默认实现：模型决策 + 本地工具链执行，失败按设置回退内置算法。
 *
 * 完整工具链：
 * 1. `crop_warp` —— 按 AI 给出的纸张四角做透视拉直（文稿）；
 * 2. 内置纸边检测 —— AI 未给出有效四边形时由 [DocumentScanner] 补位；
 * 3. `whiten` —— 文稿美白；
 * 4. `mixed_enhance` —— 调色 + 锐化的智能混合增强；
 * 5. `none` —— 保持原图。
 */
@Singleton
class DefaultAiImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: DocumentScanner,
    private val configRepository: AiConfigRepository,
    private val settingsRepository: AiImageSettingsRepository,
    private val chatClient: OpenAiChatClient,
) : AiImageProcessor {

    override suspend fun process(
        source: Bitmap,
        scene: AiImageScene,
        onLog: (String) -> Unit,
    ): AiProcessResult {
        val settings = settingsRepository.settings.value.sanitized()
        onLog("准备图片：${source.width}×${source.height}")
        val working = withContext(Dispatchers.Default) {
            ImageOps.fitMaxEdge(source, settings.outputMaxEdgePx)
        }
        var output: Bitmap? = null
        var plan: AiProcessPlan? = null
        var decidedByModel = false
        try {
            val config = configRepository.activeModel.value
            if (config == null || !config.isUsable) {
                onLog("未配置可用的 AI 模型，改用内置算法")
                output = executeFallback(working, scene, settings, onLog)
            } else {
                onLog("使用模型：${config.displayName.ifBlank { config.modelName }}")
                val temp = withContext(Dispatchers.IO) { encodeTemp(working) }
                if (temp == null) {
                    onLog("图片编码失败，改用内置算法")
                    output = executeFallback(working, scene, settings, onLog)
                } else {
                    try {
                        plan = requestPlan(config, temp, scene, settings, onLog)
                        decidedByModel = true
                        output = withContext(Dispatchers.Default) {
                            executePlan(working, scene, plan, settings, onLog)
                        }
                    } finally {
                        runCatching { if (temp.exists()) temp.delete() }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            onLog("AI 处理失败：${error.message ?: error.javaClass.simpleName}")
            if (!settings.fallbackToLocal) throw error
            onLog("已回退内置算法")
            output = executeFallback(working, scene, settings, onLog)
        } finally {
            if (output == null || output !== working) runCatching { working.recycle() }
        }
        val result = output ?: error("AI 图片处理未产出位图")
        onLog(
            if (decidedByModel) "处理完成（AI 决策：${plan?.enhance?.name ?: "?"}）"
            else "处理完成（内置算法）",
        )
        return AiProcessResult(
            bitmap = result,
            plan = plan,
            decidedByModel = decidedByModel,
        )
    }

    // ---------------- 模型交互 ----------------

    /** 把图缩到视觉模型的推荐边长后编码为 JPEG 临时文件。 */
    private fun encodeTemp(bitmap: Bitmap): File? = runCatching {
        val dir = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
        val file = File(dir, "ai_image_${System.nanoTime()}.jpg")
        val modelInput = ImageOps.fitMaxEdge(bitmap, MODEL_INPUT_MAX_EDGE_PX)
        FileOutputStream(file).use { stream ->
            modelInput.compress(Bitmap.CompressFormat.JPEG, MODEL_JPEG_QUALITY, stream)
        }
        if (modelInput !== bitmap) runCatching { modelInput.recycle() }
        file
    }.getOrNull()

    private suspend fun requestPlan(
        config: com.pencil.chuangyin.core.agent.config.AiModelConfig,
        imageFile: File,
        scene: AiImageScene,
        settings: AiImageSettings,
        onLog: (String) -> Unit,
    ): AiProcessPlan {
        onLog("请求 AI 分析画面…")
        val messages = listOf(
            ChatMessage(
                id = MESSAGE_ID_SYSTEM,
                role = ChatRole.System,
                content = SYSTEM_PROMPT,
            ),
            ChatMessage(
                id = MESSAGE_ID_USER,
                role = ChatRole.User,
                content = buildUserPrompt(scene, settings),
                attachments = listOf(
                    ToolArtifact(
                        id = ATTACHMENT_ID,
                        kind = ToolArtifactKind.IMAGE,
                        path = imageFile.absolutePath,
                        label = imageFile.name,
                    ),
                ),
            ),
        )
        val result = withTimeout(AI_TIMEOUT_MS) {
            chatClient.complete(
                config = config,
                request = ChatRequest(
                    model = config.modelName,
                    messages = messages,
                    temperature = AI_TEMPERATURE,
                    maxTokens = AI_MAX_TOKENS,
                ),
            )
        }
        val content = result.content?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(result.error?.message ?: "AI 未返回内容")
        val plan = parsePlan(content)
        onLog("AI 选择工具：${plan.tools.ifEmpty { listOf(plan.enhance.name.lowercase()) }.joinToString()}")
        if (plan.notes.isNotBlank()) onLog("AI 说明：${plan.notes}")
        return plan
    }

    // ---------------- 本地工具链 ----------------

    /** 按 AI 方案执行本地工具链；中间位图在本函数内回收，只把最终位图交出去。 */
    private fun executePlan(
        working: Bitmap,
        scene: AiImageScene,
        plan: AiProcessPlan,
        settings: AiImageSettings,
        onLog: (String) -> Unit,
    ): Bitmap {
        val created = mutableListOf<Bitmap>()
        var current = working

        if (scene == AiImageScene.Document) {
            val quad = plan.quad
            if (plan.warp && quad != null) {
                onLog("执行工具：crop_warp（AI 纸张四角）")
                current = scanner.cropWarp(working, quad).also { if (it !== working) created += it }
            } else {
                onLog("执行工具：detect_paper（内置纸边检测补位）")
                val scan = scanner.scan(working, ScanMode.Document)
                current = scan.output.also { if (it !== working) created += it }
                onLog(
                    if (scan.warped) "已按自动检测结果拉直纸面"
                    else "未检测到明确纸边，已按整图处理",
                )
            }
        }

        val enhanced = when (plan.enhance) {
            AiEnhanceMode.None -> {
                onLog("执行工具：none（不做增强）")
                ImageOps.tune(current, plan.brightness, plan.contrast, plan.saturation)
            }
            AiEnhanceMode.Whiten -> {
                onLog("执行工具：whiten（强度 ${(settings.whitenStrength * 100).toInt()}%）")
                ImageOps.whiten(current, settings.whitenStrength)
            }
            AiEnhanceMode.Mixed -> {
                onLog("执行工具：mixed_enhance（强度 ${(settings.enhanceStrength * 100).toInt()}%）")
                ImageOps.mixedEnhance(
                    current,
                    settings.enhanceStrength,
                    plan.brightness,
                    plan.contrast,
                    plan.saturation,
                )
            }
        }
        if (enhanced !== current && current !== working && created.none { it === current }) {
            created += current
        }
        created.forEach { bitmap ->
            if (bitmap !== enhanced && bitmap !== working) runCatching { bitmap.recycle() }
        }
        return enhanced
    }

    /** 内置算法兜底：文稿走本扫描管线，实景走混合增强。 */
    private fun executeFallback(
        working: Bitmap,
        scene: AiImageScene,
        settings: AiImageSettings,
        onLog: (String) -> Unit,
    ): Bitmap = when (scene) {
        AiImageScene.Document -> {
            onLog("内置算法：文档扫描（找边 + 拉直 + 文档化）")
            val scan = scanner.scan(working, ScanMode.Document)
            val output = scan.output
            if (!scan.warped) onLog("未检测到明确纸边，已按整图处理")
            output
        }
        AiImageScene.RealScene -> {
            onLog("内置算法：智能混合增强")
            ImageOps.mixedEnhance(working, settings.enhanceStrength, 0f, 0f, 0f)
        }
    }

    // ---------------- 方案解析 ----------------

    /**
     * 解析模型输出。
     *
     * 模型常带 Markdown 代码块或前后说明，因此只截取首个 `{` 到最后一个 `}`；
     * 任何字段缺失/越界都被收敛，只有完全无法构成 JSON 时才会抛错触发回退。
     */
    private fun parsePlan(raw: String): AiProcessPlan {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI 未返回 JSON 方案" }
        val json = runCatching { JSONObject(raw.substring(start, end + 1)) }
            .getOrElse { throw IllegalStateException("AI 返回的方案不是合法 JSON") }

        val quad = parseQuad(json.optJSONArray(FIELD_QUAD))
        val tools = json.optJSONArray(FIELD_TOOLS)?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index)?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }.orEmpty()
        return AiProcessPlan(
            quad = quad,
            warp = json.optBoolean(FIELD_WARP, quad != null),
            enhance = AiEnhanceMode.fromRaw(json.optString(FIELD_ENHANCE).takeIf { it.isNotBlank() }),
            brightness = clampUnit(json.optDouble(FIELD_BRIGHTNESS, 0.0)),
            contrast = clampUnit(json.optDouble(FIELD_CONTRAST, 0.0)),
            saturation = clampUnit(json.optDouble(FIELD_SATURATION, 0.0)),
            tools = tools,
            notes = json.optString(FIELD_NOTES, "").trim().take(NOTES_MAX_CHARS),
        )
    }

    /**
     * 解析四角：支持 `[[x,y],…]` 与 `[{x,y},…]` 两种写法；
     * 坐标体系兼容 0~1 与 0~1000（视觉模型常见输出），并按"左上起点 + 顺时针"重排。
     */
    private fun parseQuad(array: JSONArray?): List<PointF>? {
        if (array == null || array.length() != 4) return null
        val raw = mutableListOf<PointF>()
        var maxCoordinate = 0.0
        for (index in 0 until 4) {
            val point = array.optJSONArray(index)?.let { pair ->
                if (pair.length() < 2) null else PointF(
                    pair.optDouble(0, Double.NaN).toFloat(),
                    pair.optDouble(1, Double.NaN).toFloat(),
                )
            } ?: array.optJSONObject(index)?.let { objectPoint ->
                PointF(
                    objectPoint.optDouble(FIELD_X, Double.NaN).toFloat(),
                    objectPoint.optDouble(FIELD_Y, Double.NaN).toFloat(),
                )
            } ?: return null
            if (point.x.isNaN() || point.y.isNaN()) return null
            maxCoordinate = maxOf(maxCoordinate, abs(point.x.toDouble()), abs(point.y.toDouble()))
            raw += point
        }
        // 0~1000 坐标体系归一化到 0~1。
        val scale = if (maxCoordinate > COORDINATE_OVERSHOOT) 1000f else 1f
        val normalized = raw.map {
            PointF(
                (it.x / scale).coerceIn(0f, 1f),
                (it.y / scale).coerceIn(0f, 1f),
            )
        }
        val ordered = orderClockwise(normalized) ?: return null
        // 面积过小（模型在画面上乱指）时丢弃，交给内置检测补位。
        val area = polygonArea(ordered)
        return if (area < MIN_QUAD_AREA) null else ordered
    }

    /** 以质心排序角度（屏幕坐标 y 向下，角度递增即顺时针），再从左上角逐个取出。 */
    private fun orderClockwise(points: List<PointF>): List<PointF>? {
        if (points.size != 4) return null
        val centerX = points.sumOf { it.x.toDouble() } / 4.0
        val centerY = points.sumOf { it.y.toDouble() } / 4.0
        val sorted = points.sortedBy { atan2(it.y - centerY, it.x - centerX) }
        val startIndex = sorted.indices.minByOrNull { sorted[it].x + sorted[it].y } ?: return null
        return (0 until 4).map { sorted[(startIndex + it) % 4] }
    }

    /** 鞋带公式求归一化面积（0~1 相对整图）。 */
    private fun polygonArea(points: List<PointF>): Float {
        var sum = 0f
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            sum += current.x * next.y - next.x * current.y
        }
        return abs(sum) / 2f
    }

    private fun clampUnit(value: Double): Float {
        if (value.isNaN()) return 0f
        return value.coerceIn(-1.0, 1.0).toFloat()
    }

    // ---------------- 提示词 ----------------

    private fun buildUserPrompt(scene: AiImageScene, settings: AiImageSettings): String {
        val sceneName = if (scene == AiImageScene.Document) "文稿（纸面文档、书页、票据）" else "实景（人物、物品、场景）"
        return buildString {
            appendLine("被摄类型：$sceneName")
            appendLine("用户设定的输出最长边：${settings.outputMaxEdgePx}px；打印介质为黑白热敏纸。")
            if (scene == AiImageScene.Document) {
                appendLine("任务：先在图中定位最外层纸张边界并给出四项点（若纸面已平整铺满画面，可给整图四角）；")
                appendLine("然后用 whiten 或 mixed 让文字在热敏纸上清晰、底色干净。不要给 0 面积或自交的四边形。")
            } else {
                appendLine("任务：quad 必须为 null、warp 必须为 false；用 mixed 让画面在黑白热敏纸上层次真实、细节尽量保留。")
            }
            appendLine("只输出一个 JSON 对象。")
        }
    }

    private companion object {
        const val TEMP_DIR = "ai_image"
        const val MODEL_INPUT_MAX_EDGE_PX = 1568
        const val MODEL_JPEG_QUALITY = 88
        const val AI_TIMEOUT_MS = 120_000L
        const val AI_TEMPERATURE = 0.2
        const val AI_MAX_TOKENS = 800
        const val NOTES_MAX_CHARS = 60
        const val COORDINATE_OVERSHOOT = 1.5
        const val MIN_QUAD_AREA = 0.02f

        const val MESSAGE_ID_SYSTEM = "ai-image-system"
        const val MESSAGE_ID_USER = "ai-image-user"
        const val ATTACHMENT_ID = "ai-image-source"

        const val FIELD_QUAD = "quad"
        const val FIELD_WARP = "warp"
        const val FIELD_ENHANCE = "enhance"
        const val FIELD_BRIGHTNESS = "brightness"
        const val FIELD_CONTRAST = "contrast"
        const val FIELD_SATURATION = "saturation"
        const val FIELD_TOOLS = "tools"
        const val FIELD_NOTES = "notes"
        const val FIELD_X = "x"
        const val FIELD_Y = "y"

        val SYSTEM_PROMPT = """
            你是「铅笔创印 Pro」拍照打印的图片预处理决策器，服务对象是黑白热敏打印机。
            你不能直接改图，只能在下列本地工具中选择并给出参数，由应用在本地执行：
            1. crop_warp：按你给出的纸张四角做透视拉直，参数 quad（4 个点，顺序 左上、右上、右下、左下，坐标用 0~1000 的相对值）；
            2. detect_paper：纸边不明显时使用，把 quad 设为 null；
            3. whiten：文稿美白，去底色、提亮、增强文字对比；
            4. mixed_enhance：智能混合增强，按 brightness/contrast/saturation（-1~1）调色并锐化细节，目标是打印后真实、层次清晰；
            5. none：保持原图。
            只输出一个 JSON 对象，不要 Markdown、不要代码块、不要任何解释文字，格式：
            {"quad":[[x,y],[x,y],[x,y],[x,y]] 或 null,"warp":true/false,"enhance":"whiten"|"mixed"|"none","brightness":-1~1,"contrast":-1~1,"saturation":-1~1,"tools":["工具名"],"notes":"不超过40字的中文说明"}
            严格要求：quad 必须是 4 个互不重合的点且构成凸四边形；实景照片 quad 必须为 null、warp 必须为 false。
        """.trimIndent()
    }
}
