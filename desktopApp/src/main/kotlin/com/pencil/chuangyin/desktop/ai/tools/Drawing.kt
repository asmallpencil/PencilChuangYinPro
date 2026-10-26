package com.pencil.chuangyin.desktop.ai.tools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 声明式矢量绘图：把模型给出的图形清单渲染成 [BufferedImage]。
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
        background: Color,
    ): ParseResult {
        val parsed = ArrayList<DrawnShape>()
        val warnings = ArrayList<String>()
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
        return ParseResult(parsed, warnings, background)
    }

    /** 渲染为位图。 */
    fun render(width: Int, height: Int, background: Color, shapes: List<DrawnShape>): BufferedImage {
        val image = ImageOps.newCanvas(width, height, background)
        val g = ImageOps.graphicsOf(image)
        try {
            shapes.forEach { shape -> drawShape(g, shape) }
        } finally {
            g.dispose()
        }
        return image
    }

    private fun drawShape(
        g: java.awt.Graphics2D,
        shape: DrawnShape,
    ) {
        when (shape) {
            is DrawnShape.Rect -> {
                g.paint = shape.paint.color
                g.stroke = strokeOf(shape.paint)
                g.draw(Rectangle2D.Float(shape.x, shape.y, shape.w, shape.h))
            }

            is DrawnShape.RoundRect -> {
                g.paint = shape.paint.color
                g.stroke = strokeOf(shape.paint)
                g.draw(RoundRectangle2D.Float(shape.x, shape.y, shape.w, shape.h, shape.radius * 2f, shape.radius * 2f))
            }

            is DrawnShape.Circle -> {
                g.paint = shape.paint.color
                g.stroke = strokeOf(shape.paint)
                val d = shape.r * 2f
                g.draw(Ellipse2D.Float(shape.cx - shape.r, shape.cy - shape.r, d, d))
            }

            is DrawnShape.Ellipse -> {
                g.paint = shape.paint.color
                g.stroke = strokeOf(shape.paint)
                g.draw(Ellipse2D.Float(shape.cx - shape.rx, shape.cy - shape.ry, shape.rx * 2f, shape.ry * 2f))
            }

            is DrawnShape.Line -> {
                val paint = shape.paint.copy(filled = false)
                g.paint = paint.color
                g.stroke = strokeOf(paint)
                g.drawLine(shape.x1.toInt(), shape.y1.toInt(), shape.x2.toInt(), shape.y2.toInt())
            }

            is DrawnShape.Poly -> {
                if (shape.points.size >= 2) {
                    g.paint = shape.paint.color
                    g.stroke = strokeOf(shape.paint)
                    val path = Path2D.Float()
                    path.moveTo(shape.points.first().first, shape.points.first().second)
                    shape.points.drop(1).forEach { (x, y) -> path.lineTo(x, y) }
                    if (shape.closed) path.closePath()
                    g.draw(path)
                }
            }

            is DrawnShape.Text -> {
                g.paint = shape.color
                g.stroke = BasicStroke(1f)
                g.font = Font(FONT_FAMILY, if (shape.bold) Font.BOLD else Font.PLAIN, shape.size.toInt().coerceAtLeast(1))
                val metrics = g.fontMetrics
                val offset = when {
                    shape.align == ToolParamValues.ALIGN_CENTER -> -metrics.stringWidth(shape.text) / 2f
                    shape.align == ToolParamValues.ALIGN_END -> -metrics.stringWidth(shape.text).toFloat()
                    else -> 0f
                }
                g.drawString(shape.text, shape.x + offset, shape.y)
            }
        }
    }

    private fun strokeOf(paint: ShapePaint): BasicStroke = if (paint.filled) {
        BasicStroke(1f)
    } else {
        BasicStroke(paint.strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    }

    private fun parseShape(obj: JsonObject): DrawnShape? {
        val paint = parsePaint(obj) ?: return null
        return when (obj.stringOrNull(FIELD_TYPE)?.lowercase()) {
            TYPE_RECT -> DrawnShape.Rect(
                x = obj.numberOr(FIELD_X, 0.0).toFloat(),
                y = obj.numberOr(FIELD_Y, 0.0).toFloat(),
                w = obj.numberOr(FIELD_W, 0.0).toFloat(),
                h = obj.numberOr(FIELD_H, 0.0).toFloat(),
                paint = paint,
            )

            TYPE_ROUND_RECT -> DrawnShape.RoundRect(
                x = obj.numberOr(FIELD_X, 0.0).toFloat(),
                y = obj.numberOr(FIELD_Y, 0.0).toFloat(),
                w = obj.numberOr(FIELD_W, 0.0).toFloat(),
                h = obj.numberOr(FIELD_H, 0.0).toFloat(),
                radius = obj.numberOr(FIELD_RADIUS, DEFAULT_RADIUS.toDouble()).toFloat(),
                paint = paint,
            )

            TYPE_CIRCLE -> DrawnShape.Circle(
                cx = obj.numberOr(FIELD_CX, 0.0).toFloat(),
                cy = obj.numberOr(FIELD_CY, 0.0).toFloat(),
                r = obj.numberOr(FIELD_R, 0.0).toFloat(),
                paint = paint,
            )

            TYPE_ELLIPSE -> DrawnShape.Ellipse(
                cx = obj.numberOr(FIELD_CX, 0.0).toFloat(),
                cy = obj.numberOr(FIELD_CY, 0.0).toFloat(),
                rx = obj.numberOr(FIELD_RX, 0.0).toFloat(),
                ry = obj.numberOr(FIELD_RY, 0.0).toFloat(),
                paint = paint,
            )

            TYPE_LINE -> DrawnShape.Line(
                x1 = obj.numberOr(FIELD_X1, 0.0).toFloat(),
                y1 = obj.numberOr(FIELD_Y1, 0.0).toFloat(),
                x2 = obj.numberOr(FIELD_X2, 0.0).toFloat(),
                y2 = obj.numberOr(FIELD_Y2, 0.0).toFloat(),
                paint = paint,
            )

            TYPE_POLYGON, TYPE_POLYLINE -> {
                val points = parsePoints(obj[FIELD_POINTS])
                if (points.isEmpty()) return null
                DrawnShape.Poly(
                    points = points,
                    closed = obj.stringOrNull(FIELD_TYPE)?.lowercase() == TYPE_POLYGON,
                    paint = paint,
                )
            }

            TYPE_TEXT -> {
                val text = obj.stringOrNull(FIELD_TEXT) ?: return null
                DrawnShape.Text(
                    x = obj.numberOr(FIELD_X, 0.0).toFloat(),
                    y = obj.numberOr(FIELD_Y, 0.0).toFloat(),
                    text = text,
                    size = obj.numberOr(FIELD_SIZE, DEFAULT_TEXT_SIZE.toDouble()).toFloat(),
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
            strokeWidth = obj.numberOr(FIELD_STROKE_WIDTH, DEFAULT_STROKE_WIDTH.toDouble())
                .toFloat()
                .coerceAtLeast(MIN_STROKE_WIDTH),
        )
    }

    /** 支持 `[[x,y],...]`、`[{"x":..,"y":..},...]` 与 `"x1,y1 x2,y2"` 三种写法。 */
    private fun parsePoints(element: JsonElement?): List<Pair<Float, Float>> {
        val array = element as? JsonArray
        if (array != null) {
            return array.mapNotNull { item ->
                when (item) {
                    is JsonArray -> {
                        if (item.size < 2) return@mapNotNull null
                        val x = (item[0] as? JsonPrimitive)?.numberOrNull()
                        val y = (item[1] as? JsonPrimitive)?.numberOrNull()
                        if (x == null || y == null) null else x.toFloat() to y.toFloat()
                    }

                    is JsonObject -> {
                        val x = item.numberOrNull(FIELD_X)
                        val y = item.numberOrNull(FIELD_Y)
                        if (x == null || y == null) null else x.toFloat() to y.toFloat()
                    }

                    else -> null
                }
            }
        }
        val raw = (element as? JsonPrimitive)?.contentOrNull ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(';', ' ')
            .mapNotNull { token ->
                val parts = token.split(',')
                if (parts.size < 2) return@mapNotNull null
                val x = parts[0].trim().toFloatOrNull()
                val y = parts[1].trim().toFloatOrNull()
                if (x == null || y == null) null else x to y
            }
    }

    /** 解析十六进制颜色或颜色名，失败时回退 [fallback]。 */
    fun parseColor(raw: String?, fallback: Color): Color = ImageOps.parseColor(raw, fallback)

    private val DEFAULT_COLOR = Color.BLACK
    private const val MIN_STROKE_WIDTH = 0.5f
    private const val DEFAULT_RADIUS = 8f
    private const val FONT_FAMILY = "SansSerif"

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
        val background: Color,
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
        val color: Color,
    ) : DrawnShape
}

/** 描边/填充配置。 */
internal data class ShapePaint(
    val color: Color,
    val filled: Boolean,
    val strokeWidth: Float,
)
