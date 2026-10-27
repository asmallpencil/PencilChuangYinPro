package com.pencil.chuangyin.feature.ai.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.agent.runtime.ToolCallScope
import com.pencil.chuangyin.printer.ImagePrintOptions
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterController
import com.pencil.chuangyin.printer.PrinterSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** draw_image 支持的图形类型（官方生成器会把枚举值列表写进 Schema 的 enum）。 */
enum class DrawShapeType { RECT, ROUND_RECT, CIRCLE, ELLIPSE, LINE, POLYGON, POLYLINE, TEXT }

/** draw_image 的单个图形；未提供的字段保持 null，由渲染层按图形类型取用。points 为 "x1,y1 x2,y2" 空格分隔的点序列字符串（官方 Schema 对 data class 内嵌数组有循环依赖限制）。 */
data class DrawShape(
    val type: DrawShapeType?,
    val x: Double?,
    val y: Double?,
    val w: Double?,
    val h: Double?,
    val radius: Double?,
    val cx: Double?,
    val cy: Double?,
    val r: Double?,
    val rx: Double?,
    val ry: Double?,
    val x1: Double?,
    val y1: Double?,
    val x2: Double?,
    val y2: Double?,
    val points: String?,
    val text: String?,
    val size: Double?,
    val bold: Boolean?,
    val align: String?,
    val color: String?,
    val filled: Boolean?,
    val stroke_width: Double?,
)

/**
 * 绘图工具：把模型给出的图形清单渲染为 PNG 保存到工作区，并可选择直接打印。
 *
 * 渲染出的 PNG 会作为 [ToolArtifactKind.IMAGE] 产物回传，界面据此内联预览。
 */
@Singleton
class DrawTools @Inject constructor(
    private val workspace: Workspace,
    private val scope: ToolCallScope,
    private val printer: PrinterController,
    private val settings: PrinterSettings,
) {

    /** 根据图形清单绘制一张 PNG 图片并保存到工作区，可选直接打印。适合画表情、线条画、卡片、简单示意图。 */
    @Tool
    suspend fun draw_image(
        @Param(description = SHAPES_DESCRIPTION) shapes: List<DrawShape>,
        @Param(description = "画布宽度像素，默认等于打印头宽度。") width: Int?,
        @Param(description = "画布高度像素，默认 384。") height: Int?,
        @Param(description = "背景色，十六进制如 #FFFFFF，默认白色。") background: String?,
        @Param(description = "绘制完成后是否立即打印，默认 false。") print: Boolean?,
        @Param(description = "可选文件名（不含路径），默认自动命名，扩展名固定为 .png。") file_name: String?,
    ): ToolOutput {
        if (shapes.isEmpty()) {
            return ToolOutput("缺少参数 shapes，或图形清单为空。", isError = true)
        }

        val canvasWidth = (width ?: settings.printWidthPx()).coerceIn(ShapeRenderer.WIDTH_RANGE)
        val canvasHeight = (height ?: DEFAULT_HEIGHT).coerceIn(ShapeRenderer.HEIGHT_RANGE)
        val canvasBackground = ShapeRenderer.parseColor(background, COLOR_WHITE)

        val parseResult = ShapeRenderer.parse(JsonArray(shapes.map { drawShapeToJson(it) }), canvasBackground)
        if (parseResult.shapes.isEmpty()) {
            return ToolOutput("没有任何可渲染的图形。${parseResult.warnings.joinToString("；")}", isError = true)
        }

        scope.current?.reportProgress("正在绘制图片")
        val bitmap = withContext(Dispatchers.IO) {
            ShapeRenderer.render(canvasWidth, canvasHeight, canvasBackground, parseResult.shapes)
        }
        val target = resolveTargetFile(file_name)
        val saved = withContext(Dispatchers.IO) { savePng(bitmap, target) }
        bitmap.recycle()
        if (!saved) return ToolOutput("图片保存失败：${workspace.displayPath(target)}", isError = true)

        val builder = StringBuilder()
        builder.appendLine(
            "已绘制 ${parseResult.shapes.size} 个图形，保存为 ${workspace.displayPath(target)}（$canvasWidth×$canvasHeight）。",
        )
        if (parseResult.warnings.isNotEmpty()) {
            builder.appendLine("跳过：${parseResult.warnings.joinToString("；")}")
        }

        if (print == true) {
            builder.appendLine(printDrawn(target))
        }

        return ToolOutput(
            content = builder.toString().trim(),
            artifacts = listOf(
                ToolArtifact(
                    id = UUID.randomUUID().toString(),
                    kind = ToolArtifactKind.IMAGE,
                    path = target.absolutePath,
                    label = target.name,
                ),
            ),
        )
    }

    /** 把强类型 [DrawShape] 还原为渲染层可解析的 JSON 形态。 */
    private fun drawShapeToJson(shape: DrawShape): JsonObject = buildJsonObject {
        shape.type?.let { put("type", it.name.lowercase(Locale.US)) }
        shape.x?.let { put("x", it) }
        shape.y?.let { put("y", it) }
        shape.w?.let { put("w", it) }
        shape.h?.let { put("h", it) }
        shape.radius?.let { put("radius", it) }
        shape.cx?.let { put("cx", it) }
        shape.cy?.let { put("cy", it) }
        shape.r?.let { put("r", it) }
        shape.rx?.let { put("rx", it) }
        shape.ry?.let { put("ry", it) }
        shape.x1?.let { put("x1", it) }
        shape.y1?.let { put("y1", it) }
        shape.x2?.let { put("x2", it) }
        shape.y2?.let { put("y2", it) }
        shape.points?.let { put("points", it) }
        shape.text?.let { put("text", it) }
        shape.size?.let { put("size", it) }
        shape.bold?.let { put("bold", it) }
        shape.align?.let { put("align", it) }
        shape.color?.let { put("color", it) }
        shape.filled?.let { put("filled", it) }
        shape.stroke_width?.let { put("stroke_width", it) }
    }

    /** 打印刚绘制好的 PNG；未连接时返回说明而不是失败。 */
    private suspend fun printDrawn(file: File): String {
        if (!printer.isConnected) return "未打印：打印机未连接，请提示用户到首页连接后再试。"
        val printable = workspace.materialize(file) ?: return "打印失败：无法读取 ${file.name}。"
        scope.current?.reportProgress("正在打印绘制结果")
        val result = printer.printImagePages(
            pageCount = 1,
            options = ImagePrintOptions(),
            pageLoader = { BitmapFactory.decodeFile(printable.absolutePath) },
        )
        return when (result) {
            is PrintResult.Success -> "已发送到打印机。"
            is PrintResult.Error -> "打印失败：${result.code}${result.detail?.let { "（$it）" } ?: ""}"
        }
    }

    private fun resolveTargetFile(fileName: String?): File {
        val sanitized = fileName?.trim()?.let { sanitize(it) }?.takeIf { it.isNotEmpty() }
        val base = sanitized ?: (FILE_PREFIX + System.currentTimeMillis())
        return workspace.uniqueTarget("$base$FILE_SUFFIX")
    }

    /** 去掉路径分隔符与不合法字符，避免模型给出的名字逃逸出工作区。 */
    private fun sanitize(raw: String): String {
        val withoutExt = raw.removeSuffix(FILE_SUFFIX)
        return withoutExt.map { ch ->
            if (ch.isLetterOrDigit() || ch == '_' || ch == '-') ch else '_'
        }.joinToString("")
    }

    private fun savePng(bitmap: Bitmap, target: File): Boolean = runCatching {
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, buffer)
        workspace.writeBytes(target, buffer.toByteArray())
    }.getOrDefault(false)

    private companion object {
        const val DEFAULT_HEIGHT = 384
        const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        const val FILE_PREFIX = "draw_"
        const val FILE_SUFFIX = ".png"
        const val PNG_QUALITY = 100

        const val SHAPES_DESCRIPTION = "图形数组。每个元素必须提供 type，取值：" +
            "rect{x,y,w,h}、round_rect{x,y,w,h,radius}、circle{cx,cy,r}、ellipse{cx,cy,rx,ry}、" +
            "line{x1,y1,x2,y2}、polygon/polyline{points:\"x1,y1 x2,y2 ...\"（空格分隔的点序列字符串）}、" +
            "text{x,y,text,size,bold,align}。" +
            "公共可选字段：color（十六进制如 #000000）、filled（是否填充，默认 true）、stroke_width（线宽）。" +
            "text 的 y 为文字基线位置，align 取值 start/center/end。"
    }
}
