package com.pencil.chuangyin.feature.ai.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 声明式矢量绘图：把模型给出的图形清单渲染成 [Bitmap]。
 *
 * 之所以采用「图形清单」而非让模型直接生成图片字节，是因为大模型无法可靠地输出像素，
 * 而描述几何图形则稳定、可校验、体积小；渲染在端上完成，热敏打印所需的分辨率也正好。
 */
internal object ShapeRenderer {

    /** 画布尺寸允许范围。 */
    val WIDTH_RANGE = 32..1024
    val HEIGHT_RANGE = 32..2048

    /** 默认线宽与文字字号。 */
    const val DEFAULT_STROKE_WIDTH = 2f
    const val DEFAULT_TEXT_SIZE = 24f

    /**
     * 解析图形清单。
     *
     * @return 可渲染图形与跳过原因（无法解析的项会被跳过而非整体失败）。
     */
    fun parse(
        shapes: JsonArray?,
        background: Int,
    ): ParseResult {
        val parsed = ArrayList<DrawnShape>()
        val warnings = ArrayList<String>()
        val canvasBackground = background
        shapes?.forEachIndexed { index, element ->
            val obj = element as? JsonObject
            if (obj == null) {
                warnings += "第 ${index + 1} 个图元不是对象，已跳过"
                return@forEachIndexed
            }
            val shape = parseShape(obj)
            if (shape == null) {
                warnings += "第 ${index + 1} 个图元无法识别，已跳过"
            } else {
                parsed += shape
            }
        }
        return ParseResult(parsed, warnings, canvasBackground)
    }

    /** 渲染为位图。 */
    fun render(width: Int, height: Int, background: Int, shapes: List<DrawnShape>): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        shapes.forEach { shape -> drawShape(canvas, paint, shape) }
        return bitmap
    }

    private fun drawShape(canvas: Canvas, paint: Paint, shape: DrawnShape) {
        when (shape) {
            is DrawnShape.Rect -> {
                applyPaint(paint, shape.paint)
                canvas.drawRect(shape.x, shape.y, shape.x + shape.w, shape.y + shape.h, paint)
            }

            is DrawnShape.RoundRect -> {
                applyPaint(paint, shape.paint)
                val rect = RectF(shape.x, shape.y, shape.x + shape.w, shape.y + shape.h)
                canvas.drawRoundRect(rect, shape.radius, shape.radius, paint)
            }

            is DrawnShape.Circle -> {
                applyPaint(paint, shape.paint)
                canvas.drawCircle(shape.cx, shape.cy, shape.r, paint)
            }

            is DrawnShape.Ellipse -> {
                applyPaint(paint, shape.paint)
                val rect = RectF(shape.cx - shape.rx, shape.cy - shape.ry, shape.cx + shape.rx, shape.cy + shape.ry)
                canvas.drawOval(rect, paint)
            }

            is DrawnShape.Line -> {
                applyPaint(paint, shape.paint.copy(filled = false))
                canvas.drawLine(shape.x1, shape.y1, shape.x2, shape.y2, paint)
            }

            is DrawnShape.Poly -> {
                applyPaint(paint, shape.paint)
                if (shape.points.size >= 2) {
                    val path = android.graphics.Path()
                    path.moveTo(shape.points.first().first, shape.points.first().second)
                    shape.points.drop(1).forEach { (x, y) -> path.lineTo(x, y) }
                    if (shape.closed) path.close()
                    canvas.drawPath(path, paint)
                }
            }

            is DrawnShape.Text -> {
                paint.reset()
                paint.isAntiAlias = true
                paint.color = shape.color
                paint.textSize = shape.size
                paint.typeface = if (shape.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                paint.textAlign = when (shape.align) {
                    ToolParamValues.ALIGN_CENTER -> Paint.Align.CENTER
                    ToolParamValues.ALIGN_END -> Paint.Align.RIGHT
                    else -> Paint.Align.LEFT
                }
                canvas.drawText(shape.text, shape.x, shape.y, paint)
            }
        }
    }

    private fun applyPaint(paint: Paint, config: ShapePaint) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = config.color
        paint.strokeWidth = config.strokeWidth
        paint.style = if (config.filled) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }

    private fun parseShape(obj: JsonObject): DrawnShape? {
        val paint = parsePaint(obj) ?: return null
        return when (obj.stringOrNull(FIELD_TYPE)?.trim()?.lowercase()) {
            TYPE_RECT -> DrawnShape.Rect(
                x = obj.numberOr(FIELD_X, 0f),
                y = obj.numberOr(FIELD_Y, 0f),
                w = obj.numberOr(FIELD_W, 0f),
                h = obj.numberOr(FIELD_H, 0f),
                paint = paint,
            )

            TYPE_ROUND_RECT -> DrawnShape.RoundRect(
                x = obj.numberOr(FIELD_X, 0f),
                y = obj.numberOr(FIELD_Y, 0f),
                w = obj.numberOr(FIELD_W, 0f),
                h = obj.numberOr(FIELD_H, 0f),
                radius = obj.numberOr(FIELD_RADIUS, DEFAULT_RADIUS),
                paint = paint,
            )

            TYPE_CIRCLE -> DrawnShape.Circle(
                cx = obj.numberOr(FIELD_CX, 0f),
                cy = obj.numberOr(FIELD_CY, 0f),
                r = obj.numberOr(FIELD_R, 0f),
                paint = paint,
            )

            TYPE_ELLIPSE -> DrawnShape.Ellipse(
                cx = obj.numberOr(FIELD_CX, 0f),
                cy = obj.numberOr(FIELD_CY, 0f),
                rx = obj.numberOr(FIELD_RX, 0f),
                ry = obj.numberOr(FIELD_RY, 0f),
                paint = paint,
            )

            TYPE_LINE -> DrawnShape.Line(
                x1 = obj.numberOr(FIELD_X1, 0f),
                y1 = obj.numberOr(FIELD_Y1, 0f),
                x2 = obj.numberOr(FIELD_X2, 0f),
                y2 = obj.numberOr(FIELD_Y2, 0f),
                paint = paint,
            )

            TYPE_POLYGON, TYPE_POLYLINE -> {
                val points = parsePoints(obj[FIELD_POINTS])
                if (points.isEmpty()) return null
                DrawnShape.Poly(
                    points = points,
                    closed = obj.stringOrNull(FIELD_TYPE)?.trim()?.lowercase() == TYPE_POLYGON,
                    paint = paint,
                )
            }

            TYPE_TEXT -> {
                val text = obj.stringOrNull(FIELD_TEXT) ?: return null
                DrawnShape.Text(
                    x = obj.numberOr(FIELD_X, 0f),
                    y = obj.numberOr(FIELD_Y, 0f),
                    text = text,
                    size = obj.numberOr(FIELD_SIZE, DEFAULT_TEXT_SIZE),
                    bold = obj.booleanOr(FIELD_BOLD, false),
                    align = obj.stringOr(FIELD_ALIGN, ToolParamValues.ALIGN_START),
                    color = paint.color,
                )
            }

            else -> null
        }
    }

    private fun parsePaint(obj: JsonObject): ShapePaint? {
        val color = parseColor(obj.stringOrNull(FIELD_COLOR), DEFAULT_COLOR)
        return ShapePaint(
            color = color,
            filled = obj.booleanOr(FIELD_FILLED, true),
            strokeWidth = obj.numberOr(FIELD_STROKE_WIDTH, DEFAULT_STROKE_WIDTH)
                .coerceAtLeast(MIN_STROKE_WIDTH),
        )
    }

    /** 支持 `[[x,y],...]`、`[{"x":..,"y":..},...]` 与 `"x1,y1 x2,y2"` 三种写法。 */
    private fun parsePoints(element: kotlinx.serialization.json.JsonElement?): List<Pair<Float, Float>> {
        val array = element as? JsonArray
        if (array != null) {
            return array.mapNotNull { item ->
                when (item) {
                    is JsonArray -> {
                        if (item.size < 2) return@mapNotNull null
                        val x = (item[0] as? JsonPrimitive)?.contentOrNullNumber()
                        val y = (item[1] as? JsonPrimitive)?.contentOrNullNumber()
                        if (x == null || y == null) null else x to y
                    }

                    is JsonObject -> {
                        val x = item.numberOrNull(FIELD_X)
                        val y = item.numberOrNull(FIELD_Y)
                        if (x == null || y == null) null else x to y
                    }

                    else -> null
                }
            }
        }
        val raw = (element as? JsonPrimitive)?.contentOrNullText()
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';', ' ')
            .mapNotNull { token ->
                val parts = token.split(',')
                if (parts.size < 2) return@mapNotNull null
                val x = parts[0].trim().toFloatOrNull()
                val y = parts[1].trim().toFloatOrNull()
                if (x == null || y == null) null else x to y
            }
    }

    /** 解析十六进制颜色，失败时回退 [fallback]。 */
    fun parseColor(raw: String?, fallback: Int): Int {
        if (raw.isNullOrBlank()) return fallback
        val normalized = raw.trim().let {
            if (it.startsWith("#")) it else "#$it"
        }
        return runCatching { android.graphics.Color.parseColor(normalized) }.getOrDefault(fallback)
    }

    private const val DEFAULT_COLOR = 0xFF000000.toInt()
    private const val MIN_STROKE_WIDTH = 0.5f
    private const val DEFAULT_RADIUS = 8f

    private const val FIELD_TYPE = "type"
    private const val FIELD_COLOR = "color"
    private const val FIELD_FILLED = "filled"
    private const val FIELD_STROKE_WIDTH = "stroke_width"
    private const val FIELD_X = "x"
    private const val FIELD_Y = "y"
    private const val FIELD_W = "w"
    private const val FIELD_H = "h"
    private const val FIELD_RADIUS = "radius"
    private const val FIELD_CX = "cx"
    private const val FIELD_CY = "cy"
    private const val FIELD_R = "r"
    private const val FIELD_RX = "rx"
    private const val FIELD_RY = "ry"
    private const val FIELD_X1 = "x1"
    private const val FIELD_Y1 = "y1"
    private const val FIELD_X2 = "x2"
    private const val FIELD_Y2 = "y2"
    private const val FIELD_POINTS = "points"
    private const val FIELD_TEXT = "text"
    private const val FIELD_SIZE = "size"
    private const val FIELD_BOLD = "bold"
    private const val FIELD_ALIGN = "align"

    private const val TYPE_RECT = "rect"
    private const val TYPE_ROUND_RECT = "round_rect"
    private const val TYPE_CIRCLE = "circle"
    private const val TYPE_ELLIPSE = "ellipse"
    private const val TYPE_LINE = "line"
    private const val TYPE_POLYGON = "polygon"
    private const val TYPE_POLYLINE = "polyline"
    private const val TYPE_TEXT = "text"

    /** 解析结果。 */
    data class ParseResult(
        val shapes: List<DrawnShape>,
        val warnings: List<String>,
        val background: Int,
    )
}

/** 已解析的图形（单位为画布像素）。 */
internal sealed interface DrawnShape {
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val paint: ShapePaint) : DrawnShape
    data class RoundRect(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val radius: Float,
        val paint: ShapePaint,
    ) : DrawnShape

    data class Circle(val cx: Float, val cy: Float, val r: Float, val paint: ShapePaint) : DrawnShape
    data class Ellipse(
        val cx: Float,
        val cy: Float,
        val rx: Float,
        val ry: Float,
        val paint: ShapePaint,
    ) : DrawnShape

    data class Line(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val paint: ShapePaint,
    ) : DrawnShape

    data class Poly(
        val points: List<Pair<Float, Float>>,
        val closed: Boolean,
        val paint: ShapePaint,
    ) : DrawnShape

    data class Text(
        val x: Float,
        val y: Float,
        val text: String,
        val size: Float,
        val bold: Boolean,
        val align: String,
        val color: Int,
    ) : DrawnShape
}

/** 描边/填充配置。 */
internal data class ShapePaint(
    val color: Int,
    val filled: Boolean,
    val strokeWidth: Float,
)

/** JSON 数值读取（兼容数字与字符串形式的数字）。 */
internal fun JsonObject.numberOrNull(key: String): Float? {
    val element = this[key] as? JsonPrimitive ?: return null
    return element.contentOrNullNumber()
}

internal fun JsonObject.numberOr(key: String, default: Float): Float = numberOrNull(key) ?: default

private fun JsonPrimitive.contentOrNullNumber(): Float? =
    runCatching { content.trim().toFloat() }.getOrNull()

private fun JsonPrimitive.contentOrNullText(): String? =
    runCatching { content }.getOrNull()
