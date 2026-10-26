package com.pencil.chuangyin.desktop.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 桌面端图标集。
 *
 * 说明：Compose Desktop 的已解析依赖里没有 material-icons 制品，
 * 为避免新增依赖导致构建失败，这里用 Canvas 图元按 24×24 网格手绘，
 * 线宽/端点/圆角与 Material Symbols 的默认形态保持一致（2dp、圆头、圆角连接）。
 */
enum class AppIcon {
    Home,
    Creative,
    Store,
    Settings,
    Search,
    Refresh,
    Menu,
    Back,
    ChevronRight,
    Close,
    Heart,
    Comment,
    Send,
    Printer,
    Login,
    TextTool,
    ImageTool,
    CameraTool,
    AiTool,
    TagTool,
    Paper,
    Ink,
    Thermometer,
    Bluetooth,
    Battery,
    Check,
    Bookmark,
    PhotoLibrary,
    Person,
    History,
    Download,
    Gallery,
    Block,
    Cache,
    Slideshow,
    Language,
    ChevronLeft,
    Fullscreen,
    DeleteSweep,
    Add,
    Description,
    Collections,
    ManageAccounts,
    ImageSearch,
    Cached,
    Info,
    SwapVert,
    SwapHoriz,
    ArrowDropUp,
    ArrowDropDown,
    FolderZip,
    TextSnippet,
    PicturePdf,
    FilterOne,
    DeleteOutline,
}

@Composable
fun AppIconView(
    icon: AppIcon,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(width = 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawGlyph(icon, unit, tint, stroke)
    }
}

private fun DrawScope.dot(x: Float, y: Float, unit: Float) = Offset(x * unit, y * unit)

private fun DrawScope.line(
    x1: Float, y1: Float, x2: Float, y2: Float, unit: Float, color: Color, stroke: Stroke,
) = drawLine(color, dot(x1, y1, unit), dot(x2, y2, unit), strokeWidth = stroke.width, cap = StrokeCap.Round)

private fun DrawScope.path(
    unit: Float, color: Color, stroke: Stroke, fill: Boolean = false, build: Path.(Float) -> Unit,
) {
    val p = Path()
    p.build(unit)
    if (fill) drawPath(p, color) else drawPath(p, color, style = stroke)
}

private fun DrawScope.roundRect(
    l: Float, t: Float, r: Float, b: Float, radius: Float, unit: Float, color: Color, stroke: Stroke,
) = drawRoundRect(
    color = color,
    topLeft = dot(l, t, unit),
    size = Size((r - l) * unit, (b - t) * unit),
    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * unit),
    style = stroke,
)

@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun DrawScope.drawGlyph(icon: AppIcon, unit: Float, tint: Color, stroke: Stroke) {
    when (icon) {
        AppIcon.Home -> {
            path(unit, tint, stroke) { u ->
                moveTo(3 * u, 10.5f * u)
                lineTo(12 * u, 3.5f * u)
                lineTo(21 * u, 10.5f * u)
                lineTo(21 * u, 20.5f * u)
                lineTo(14.5f * u, 20.5f * u)
                lineTo(14.5f * u, 14f * u)
                lineTo(9.5f * u, 14f * u)
                lineTo(9.5f * u, 20.5f * u)
                lineTo(3 * u, 20.5f * u)
                close()
            }
        }

        AppIcon.Creative, AppIcon.AiTool -> {
            path(unit, tint, stroke, fill = true) { u ->
                moveTo(12f * u, 2.5f * u)
                lineTo(14.2f * u, 9.8f * u)
                lineTo(21.5f * u, 12f * u)
                lineTo(14.2f * u, 14.2f * u)
                lineTo(12f * u, 21.5f * u)
                lineTo(9.8f * u, 14.2f * u)
                lineTo(2.5f * u, 12f * u)
                lineTo(9.8f * u, 9.8f * u)
                close()
            }
        }

        AppIcon.Battery -> {
            roundRect(3.5f, 7f, 17.5f, 17f, 2.5f, unit, tint, stroke)
            roundRect(18.6f, 10f, 20.4f, 14f, 1.2f, unit, tint, stroke)
            drawRoundRect(
                color = tint,
                topLeft = dot(6f, 9.5f, unit),
                size = Size(9f * unit, 5f * unit),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * unit),
            )
        }

        AppIcon.Store -> {
            path(unit, tint, stroke) { u ->
                moveTo(4f * u, 9f * u)
                lineTo(20f * u, 9f * u)
                lineTo(20f * u, 20.5f * u)
                lineTo(4f * u, 20.5f * u)
                close()
            }
            line(4f, 9f, 4.8f, 4f, unit, tint, stroke)
            line(20f, 9f, 19.2f, 4f, unit, tint, stroke)
            line(4.8f, 4f, 19.2f, 4f, unit, tint, stroke)
            line(9f, 4f, 9f, 9f, unit, tint, stroke)
            line(15f, 4f, 15f, 9f, unit, tint, stroke)
            line(10f, 20.5f, 10f, 14f, unit, tint, stroke)
            line(10f, 14f, 14f, 14f, unit, tint, stroke)
            line(14f, 14f, 14f, 20.5f, unit, tint, stroke)
        }

        AppIcon.Settings -> {
            drawCircle(tint, radius = 3.1f * unit, center = dot(12f, 12f, unit), style = stroke)
            for (i in 0 until 8) {
                val a = Math.toRadians((i * 45).toDouble())
                val c = kotlin.math.cos(a).toFloat()
                val s = kotlin.math.sin(a).toFloat()
                drawLine(
                    color = tint,
                    start = dot(12f + c * 6.2f, 12f + s * 6.2f, unit),
                    end = dot(12f + c * 9.4f, 12f + s * 9.4f, unit),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
        }

        AppIcon.Search -> {
            drawCircle(tint, radius = 6.2f * unit, center = dot(10.5f, 10.5f, unit), style = stroke)
            line(15f, 15f, 20f, 20f, unit, tint, stroke)
        }

        AppIcon.Refresh -> {
            drawArc(
                color = tint,
                startAngle = -50f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = dot(3.5f, 3.5f, unit),
                size = Size(17f * unit, 17f * unit),
                style = stroke,
            )
            path(unit, tint, stroke, fill = true) { u ->
                moveTo(18.4f * u, 2.6f * u)
                lineTo(20.2f * u, 8.4f * u)
                lineTo(14.6f * u, 6.6f * u)
                close()
            }
        }

        AppIcon.Menu -> {
            line(3.5f, 7f, 20.5f, 7f, unit, tint, stroke)
            line(3.5f, 12f, 20.5f, 12f, unit, tint, stroke)
            line(3.5f, 17f, 20.5f, 17f, unit, tint, stroke)
        }

        AppIcon.Back -> {
            path(unit, tint, stroke) { u ->
                moveTo(14.5f * u, 5f * u)
                lineTo(7.5f * u, 12f * u)
                lineTo(14.5f * u, 19f * u)
            }
        }

        AppIcon.ChevronRight -> {
            path(unit, tint, stroke) { u ->
                moveTo(9.5f * u, 5f * u)
                lineTo(16.5f * u, 12f * u)
                lineTo(9.5f * u, 19f * u)
            }
        }

        AppIcon.Close -> {
            line(6f, 6f, 18f, 18f, unit, tint, stroke)
            line(18f, 6f, 6f, 18f, unit, tint, stroke)
        }

        AppIcon.Heart -> {
            path(unit, tint, stroke) { u ->
                moveTo(12f * u, 20.5f * u)
                cubicTo(6f * u, 16.5f * u, 3.2f * u, 13.2f * u, 3.2f * u, 9.8f * u)
                cubicTo(3.2f * u, 7f * u, 5.6f * u, 4.8f * u, 8.2f * u, 4.8f * u)
                cubicTo(10f * u, 4.8f * u, 11.4f * u, 5.8f * u, 12f * u, 7.2f * u)
                cubicTo(12.6f * u, 5.8f * u, 14f * u, 4.8f * u, 15.8f * u, 4.8f * u)
                cubicTo(18.4f * u, 4.8f * u, 20.8f * u, 7f * u, 20.8f * u, 9.8f * u)
                cubicTo(20.8f * u, 13.2f * u, 18f * u, 16.5f * u, 12f * u, 20.5f * u)
                close()
            }
        }

        AppIcon.Comment -> {
            path(unit, tint, stroke) { u ->
                moveTo(8f * u, 5f * u)
                lineTo(18f * u, 5f * u)
                cubicTo(19.7f * u, 5f * u, 21f * u, 6.3f * u, 21f * u, 8f * u)
                lineTo(21f * u, 14f * u)
                cubicTo(21f * u, 15.7f * u, 19.7f * u, 17f * u, 18f * u, 17f * u)
                lineTo(12f * u, 17f * u)
                lineTo(7f * u, 21f * u)
                lineTo(7f * u, 17f * u)
                lineTo(6f * u, 17f * u)
                cubicTo(4.3f * u, 17f * u, 3f * u, 15.7f * u, 3f * u, 14f * u)
                lineTo(3f * u, 8f * u)
                cubicTo(3f * u, 6.3f * u, 4.3f * u, 5f * u, 6f * u, 5f * u)
                close()
            }
        }

        AppIcon.Send -> {
            path(unit, tint, stroke) { u ->
                moveTo(3.2f * u, 20.8f * u)
                lineTo(21f * u, 12f * u)
                lineTo(3.2f * u, 3.2f * u)
                lineTo(6.4f * u, 12f * u)
                close()
            }
            line(6.4f, 12f, 21f, 12f, unit, tint, stroke)
        }

        AppIcon.Printer -> {
            roundRect(4.5f, 8f, 19.5f, 17f, 2f, unit, tint, stroke)
            roundRect(7f, 3f, 17f, 8f, 1.2f, unit, tint, stroke)
            roundRect(7f, 17f, 17f, 21f, 1.2f, unit, tint, stroke)
            drawCircle(tint, radius = 1.1f * unit, center = dot(17.2f, 11.2f, unit))
        }

        AppIcon.Login -> {
            path(unit, tint, stroke) { u ->
                moveTo(13.5f * u, 4.5f * u)
                lineTo(19.5f * u, 4.5f * u)
                lineTo(19.5f * u, 19.5f * u)
                lineTo(13.5f * u, 19.5f * u)
            }
            line(3.5f, 12f, 14.5f, 12f, unit, tint, stroke)
            path(unit, tint, stroke) { u ->
                moveTo(10.5f * u, 8.2f * u)
                lineTo(14.4f * u, 12f * u)
                lineTo(10.5f * u, 15.8f * u)
            }
        }

        AppIcon.TextTool -> {
            line(6f, 5f, 18f, 5f, unit, tint, stroke)
            line(12f, 5f, 12f, 19.5f, unit, tint, stroke)
            line(9f, 19.5f, 15f, 19.5f, unit, tint, stroke)
        }

        AppIcon.ImageTool -> {
            roundRect(3.2f, 4.5f, 20.8f, 19.5f, 2.6f, unit, tint, stroke)
            drawCircle(tint, radius = 1.7f * unit, center = dot(8.6f, 9.6f, unit), style = stroke)
            path(unit, tint, stroke) { u ->
                moveTo(4.4f * u, 17.4f * u)
                lineTo(9.6f * u, 12.4f * u)
                lineTo(13.4f * u, 16f * u)
                lineTo(16.2f * u, 13.4f * u)
                lineTo(20.4f * u, 17.4f * u)
            }
        }

        AppIcon.CameraTool -> {
            roundRect(3.2f, 6.6f, 20.8f, 20.2f, 2.6f, unit, tint, stroke)
            path(unit, tint, stroke) { u ->
                moveTo(8.6f * u, 6.6f * u)
                lineTo(10f * u, 3.6f * u)
                lineTo(14f * u, 3.6f * u)
                lineTo(15.4f * u, 6.6f * u)
            }
            drawCircle(tint, radius = 4f * unit, center = dot(12f, 13.4f, unit), style = stroke)
        }

        AppIcon.TagTool -> {
            path(unit, tint, stroke) { u ->
                moveTo(11.4f * u, 3.2f * u)
                lineTo(20.8f * u, 12.6f * u)
                lineTo(12.6f * u, 20.8f * u)
                lineTo(3.2f * u, 11.4f * u)
                close()
            }
            drawCircle(tint, radius = 1.4f * unit, center = dot(8.4f, 8.4f, unit), style = stroke)
        }

        AppIcon.Paper -> {
            path(unit, tint, stroke) { u ->
                moveTo(6f * u, 3.2f * u)
                lineTo(14.5f * u, 3.2f * u)
                lineTo(18.6f * u, 7.4f * u)
                lineTo(18.6f * u, 20.8f * u)
                lineTo(6f * u, 20.8f * u)
                close()
            }
            line(14.2f, 3.4f, 14.2f, 7.6f, unit, tint, stroke)
            line(14.2f, 7.6f, 18.4f, 7.6f, unit, tint, stroke)
            line(8.6f, 12.4f, 16f, 12.4f, unit, tint, stroke)
            line(8.6f, 16.4f, 16f, 16.4f, unit, tint, stroke)
        }

        AppIcon.Ink -> {
            path(unit, tint, stroke) { u ->
                moveTo(12f * u, 3.2f * u)
                cubicTo(12f * u, 3.2f * u, 6.4f * u, 10.2f * u, 6.4f * u, 14.2f * u)
                cubicTo(6.4f * u, 17.4f * u, 8.9f * u, 20.2f * u, 12f * u, 20.2f * u)
                cubicTo(15.1f * u, 20.2f * u, 17.6f * u, 17.4f * u, 17.6f * u, 14.2f * u)
                cubicTo(17.6f * u, 10.2f * u, 12f * u, 3.2f * u, 12f * u, 3.2f * u)
                close()
            }
        }

        AppIcon.Thermometer -> {
            drawCircle(tint, radius = 3.1f * unit, center = dot(12f, 17.4f, unit), style = stroke)
            line(12f, 4.2f, 12f, 14.6f, unit, tint, stroke)
            line(14.8f, 7.2f, 17.4f, 7.2f, unit, tint, stroke)
            line(14.8f, 11f, 17.4f, 11f, unit, tint, stroke)
        }

        AppIcon.Bluetooth -> {
            path(unit, tint, stroke) { u ->
                moveTo(8f * u, 7.2f * u)
                lineTo(16f * u, 16.8f * u)
                lineTo(12f * u, 20.4f * u)
                lineTo(12f * u, 3.6f * u)
                lineTo(16f * u, 7.2f * u)
                lineTo(8f * u, 16.8f * u)
            }
        }

        AppIcon.Check -> {
            path(unit, tint, stroke) { u ->
                moveTo(4.8f * u, 12.6f * u)
                lineTo(9.8f * u, 17.6f * u)
                lineTo(19.2f * u, 6.6f * u)
            }
        }

        AppIcon.Bookmark -> {
            path(unit, tint, stroke) { u ->
                moveTo(6f * u, 3.6f * u)
                lineTo(18f * u, 3.6f * u)
                lineTo(18f * u, 20.6f * u)
                lineTo(12f * u, 16.4f * u)
                lineTo(6f * u, 20.6f * u)
                close()
            }
        }

        AppIcon.PhotoLibrary -> {
            roundRect(7.4f, 3.4f, 20.6f, 16.6f, 2f, unit, tint, stroke)
            path(unit, tint, stroke) { u ->
                moveTo(3.4f * u, 7.4f * u)
                lineTo(3.4f * u, 18.4f * u)
                cubicTo(3.4f * u, 19.7f * u, 4.4f * u, 20.6f * u, 5.6f * u, 20.6f * u)
                lineTo(16.6f * u, 20.6f * u)
            }
            drawCircle(tint, radius = 1.5f * unit, center = dot(12.2f, 9f, unit), style = stroke)
            path(unit, tint, stroke) { u ->
                moveTo(8f * u, 15.4f * u)
                lineTo(11.6f * u, 11.8f * u)
                lineTo(14.4f * u, 14.4f * u)
                lineTo(16.4f * u, 12.6f * u)
                lineTo(19.8f * u, 15.6f * u)
            }
        }

        AppIcon.Person -> {
            drawCircle(tint, radius = 4f * unit, center = dot(12f, 8f, unit), style = stroke)
            path(unit, tint, stroke) { u ->
                moveTo(4.4f * u, 20.4f * u)
                cubicTo(4.4f * u, 16.2f * u, 7.8f * u, 13.6f * u, 12f * u, 13.6f * u)
                cubicTo(16.2f * u, 13.6f * u, 19.6f * u, 16.2f * u, 19.6f * u, 20.4f * u)
            }
        }

        AppIcon.History -> {
            drawArc(
                color = tint,
                startAngle = 300f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = dot(3.6f, 3.6f, unit),
                size = Size(16.8f * unit, 16.8f * unit),
                style = stroke,
            )
            line(12f, 7.6f, 12f, 12.4f, unit, tint, stroke)
            line(12f, 12.4f, 15.6f, 14.6f, unit, tint, stroke)
            path(unit, tint, stroke, fill = true) { u ->
                moveTo(4.6f * u, 2.8f * u)
                lineTo(8.6f * u, 4.6f * u)
                lineTo(4.2f * u, 7.2f * u)
                close()
            }
        }

        AppIcon.Download -> {
            line(12f, 3.6f, 12f, 15f, unit, tint, stroke)
            path(unit, tint, stroke) { u ->
                moveTo(7.4f * u, 10.6f * u)
                lineTo(12f * u, 15.4f * u)
                lineTo(16.6f * u, 10.6f * u)
            }
            path(unit, tint, stroke) { u ->
                moveTo(4.2f * u, 16.6f * u)
                lineTo(4.2f * u, 20.4f * u)
                lineTo(19.8f * u, 20.4f * u)
                lineTo(19.8f * u, 16.6f * u)
            }
        }

        AppIcon.Gallery -> {
            roundRect(3.4f, 5.4f, 20.6f, 20.4f, 2.2f, unit, tint, stroke)
            path(unit, tint, stroke) { u ->
                moveTo(9.6f * u, 5.4f * u)
                lineTo(10.8f * u, 2.8f * u)
                lineTo(13.2f * u, 2.8f * u)
                lineTo(14.4f * u, 5.4f * u)
            }
            drawCircle(tint, radius = 1.6f * unit, center = dot(8.6f, 10.4f, unit), style = stroke)
            path(unit, tint, stroke) { u ->
                moveTo(4.6f * u, 18.4f * u)
                lineTo(9.4f * u, 13.6f * u)
                lineTo(13f * u, 17.4f * u)
                lineTo(15.2f * u, 15.2f * u)
                lineTo(19.6f * u, 19.6f * u)
            }
        }

        AppIcon.Block -> {
            drawCircle(tint, radius = 8.2f * unit, center = dot(12f, 12f, unit), style = stroke)
            line(6.2f, 6.2f, 17.8f, 17.8f, unit, tint, stroke)
        }

        AppIcon.Cache -> {
            roundRect(4f, 4.2f, 20f, 9.6f, 1.8f, unit, tint, stroke)
            roundRect(4f, 9.8f, 20f, 15.2f, 1.8f, unit, tint, stroke)
            roundRect(4f, 15.4f, 20f, 20.8f, 1.8f, unit, tint, stroke)
            drawCircle(tint, radius = 1.1f * unit, center = dot(17.2f, 6.9f, unit))
            drawCircle(tint, radius = 1.1f * unit, center = dot(17.2f, 12.5f, unit))
            drawCircle(tint, radius = 1.1f * unit, center = dot(17.2f, 18.1f, unit))
        }

        AppIcon.Slideshow -> {
            roundRect(3.4f, 4.4f, 20.6f, 19.6f, 2.2f, unit, tint, stroke)
            path(unit, tint, stroke, fill = true) { u ->
                moveTo(10.2f * u, 8.4f * u)
                lineTo(15.8f * u, 12f * u)
                lineTo(10.2f * u, 15.6f * u)
                close()
            }
        }

        AppIcon.Language -> {
            drawCircle(tint, radius = 8.2f * unit, center = dot(12f, 12f, unit), style = stroke)
            line(3.8f, 12f, 20.2f, 12f, unit, tint, stroke)
            path(unit, tint, stroke) { u ->
                moveTo(12f * u, 3.8f * u)
                cubicTo(8.4f * u, 7.4f * u, 8.4f * u, 16.6f * u, 12f * u, 20.2f * u)
                cubicTo(15.6f * u, 16.6f * u, 15.6f * u, 7.4f * u, 12f * u, 3.8f * u)
                close()
            }
        }

        AppIcon.ChevronLeft -> {
            path(unit, tint, stroke) { u ->
                moveTo(14.5f * u, 5f * u)
                lineTo(7.5f * u, 12f * u)
                lineTo(14.5f * u, 19f * u)
            }
        }

        AppIcon.Fullscreen -> {
            path(unit, tint, stroke) { u ->
                moveTo(4f * u, 9.2f * u)
                lineTo(4f * u, 4f * u)
                lineTo(9.2f * u, 4f * u)
            }
            path(unit, tint, stroke) { u ->
                moveTo(14.8f * u, 4f * u)
                lineTo(20f * u, 4f * u)
                lineTo(20f * u, 9.2f * u)
            }
            path(unit, tint, stroke) { u ->
                moveTo(20f * u, 14.8f * u)
                lineTo(20f * u, 20f * u)
                lineTo(14.8f * u, 20f * u)
            }
            path(unit, tint, stroke) { u ->
                moveTo(9.2f * u, 20f * u)
                lineTo(4f * u, 20f * u)
                lineTo(4f * u, 14.8f * u)
            }
        }

        AppIcon.DeleteSweep, AppIcon.DeleteOutline -> {
            // 垃圾箱：盖子 + 提手 + 桶身 + 两条内部竖线（Material DeleteSweep 的简体形态）。
            line(5.6f, 6f, 18.4f, 6f, unit, tint, stroke)
            line(9.6f, 6f, 9.6f, 3.6f, unit, tint, stroke)
            line(9.6f, 3.6f, 14.4f, 3.6f, unit, tint, stroke)
            line(14.4f, 3.6f, 14.4f, 6f, unit, tint, stroke)
            path(unit, tint, stroke) { u ->
                moveTo(7.2f * u, 6f * u)
                lineTo(8.1f * u, 20.4f * u)
                lineTo(15.9f * u, 20.4f * u)
                lineTo(16.8f * u, 6f * u)
            }
            line(10.6f, 9.6f, 10.9f, 16.8f, unit, tint, stroke)
            line(13.4f, 9.6f, 13.1f, 16.8f, unit, tint, stroke)
        }

        AppIcon.Add -> {
            line(12f, 5f, 12f, 19f, unit, tint, stroke)
            line(5f, 12f, 19f, 12f, unit, tint, stroke)
        }

        AppIcon.Description -> {
            path(unit, tint, stroke) { u ->
                moveTo(6f * u, 3.6f * u)
                lineTo(14f * u, 3.6f * u)
                lineTo(18.6f * u, 8.2f * u)
                lineTo(18.6f * u, 20.4f * u)
                lineTo(6f * u, 20.4f * u)
                close()
            }
            line(14f, 3.6f, 14f, 8.2f, unit, tint, stroke)
            line(14f, 8.2f, 18.6f, 8.2f, unit, tint, stroke)
            line(9f, 12.2f, 15.6f, 12.2f, unit, tint, stroke)
            line(9f, 15.8f, 15.6f, 15.8f, unit, tint, stroke)
        }

        AppIcon.Collections -> {
            roundRect(8f, 3.4f, 20.6f, 12.4f, 2f, unit, tint, stroke)
            roundRect(3.4f, 8f, 16f, 20.6f, 2f, unit, tint, stroke)
        }

        AppIcon.ManageAccounts -> {
            drawCircle(tint, radius = 3.3f * unit, center = dot(8.4f, 8f, unit), style = stroke)
            path(unit, tint, stroke) { u ->
                moveTo(3.6f * u, 19.6f * u)
                lineTo(3.6f * u, 17.8f * u)
                cubicTo(3.6f * u, 14.8f * u, 6.1f * u, 13.1f * u, 8.4f * u, 13.1f * u)
                cubicTo(10.7f * u, 13.1f * u, 13.2f * u, 14.8f * u, 13.2f * u, 17.8f * u)
                lineTo(13.2f * u, 19.6f * u)
            }
            line(16.2f, 8.6f, 21f, 8.6f, unit, tint, stroke)
            line(16.2f, 12.4f, 21f, 12.4f, unit, tint, stroke)
            line(16.2f, 16.2f, 19.2f, 16.2f, unit, tint, stroke)
        }

        AppIcon.ImageSearch -> {
            roundRect(3f, 3.4f, 17.4f, 17.4f, 2f, unit, tint, stroke)
            drawCircle(tint, radius = 1.3f * unit, center = dot(7.2f, 7.6f, unit), style = stroke)
            path(unit, tint, stroke) { u ->
                moveTo(3.4f * u, 15.4f * u)
                lineTo(7.6f * u, 11f * u)
                lineTo(10.8f * u, 14.4f * u)
                lineTo(12.8f * u, 12.4f * u)
                lineTo(17f * u, 16.6f * u)
            }
            drawCircle(tint, radius = 3.2f * unit, center = dot(16.8f, 16.8f, unit), style = stroke)
            line(19.2f, 19.2f, 21.2f, 21.2f, unit, tint, stroke)
        }

        AppIcon.Cached -> {
            drawArc(
                color = tint,
                startAngle = 200f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = dot(3.6f, 3.6f, unit),
                size = Size(16.8f * unit, 16.8f * unit),
                style = stroke,
            )
            drawArc(
                color = tint,
                startAngle = 20f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = dot(3.6f, 3.6f, unit),
                size = Size(16.8f * unit, 16.8f * unit),
                style = stroke,
            )
            path(unit, tint, stroke, fill = true) { u ->
                moveTo(3.1f * u, 8.2f * u)
                lineTo(4.6f * u, 3.4f * u)
                lineTo(7.6f * u, 7.4f * u)
                close()
            }
            path(unit, tint, stroke, fill = true) { u ->
                moveTo(20.9f * u, 15.8f * u)
                lineTo(19.4f * u, 20.6f * u)
                lineTo(16.4f * u, 16.6f * u)
                close()
            }
        }

        AppIcon.Info -> {
            drawCircle(tint, radius = 8.2f * unit, center = dot(12f, 12f, unit), style = stroke)
            drawCircle(tint, radius = 1f * unit, center = dot(12f, 7.6f, unit))
            line(12f, 10.6f, 12f, 16.6f, unit, tint, stroke)
        }

        AppIcon.SwapVert -> {
            line(8.4f, 4.2f, 8.4f, 19.2f, unit, tint, stroke)
            path(unit, tint, stroke) { u ->
                moveTo(4.6f * u, 15.4f * u)
                lineTo(8.4f * u, 19.6f * u)
                lineTo(12.2f * u, 15.4f * u)
            }
            line(15.6f, 19.8f, 15.6f, 4.8f, unit, tint, stroke)
            path(unit, tint, stroke) { u ->
                moveTo(11.8f * u, 8.6f * u)
                lineTo(15.6f * u, 4.4f * u)
                lineTo(19.4f * u, 8.6f * u)
            }
        }

        AppIcon.SwapHoriz -> {
            line(4.2f, 8.4f, 19.2f, 8.4f, unit, tint, stroke)
            path(unit, tint, stroke) { u ->
                moveTo(15.4f * u, 4.6f * u)
                lineTo(19.6f * u, 8.4f * u)
                lineTo(15.4f * u, 12.2f * u)
            }
            line(19.8f, 15.6f, 4.8f, 15.6f, unit, tint, stroke)
            path(unit, tint, stroke) { u ->
                moveTo(8.6f * u, 11.8f * u)
                lineTo(4.4f * u, 15.6f * u)
                lineTo(8.6f * u, 19.4f * u)
            }
        }

        // 实心三角（对齐 Material Icons 的 ArrowDropUp / ArrowDropDown）。
        AppIcon.ArrowDropUp -> path(unit, tint, stroke, fill = true) { u ->
            moveTo(12f * u, 7.2f * u)
            lineTo(17.6f * u, 14.8f * u)
            lineTo(6.4f * u, 14.8f * u)
            close()
        }

        AppIcon.ArrowDropDown -> path(unit, tint, stroke, fill = true) { u ->
            moveTo(12f * u, 16.8f * u)
            lineTo(6.4f * u, 9.2f * u)
            lineTo(17.6f * u, 9.2f * u)
            close()
        }

        // 压缩包：文件夹外形 + 右侧拉链条带。
        AppIcon.FolderZip -> {
            path(unit, tint, stroke) { u ->
                moveTo(3f * u, 18.2f * u)
                lineTo(3f * u, 6.4f * u)
                lineTo(9.2f * u, 6.4f * u)
                lineTo(10.9f * u, 8.4f * u)
                lineTo(21f * u, 8.4f * u)
                lineTo(21f * u, 18.2f * u)
                close()
            }
            line(15.4f, 8.4f, 15.4f, 18.2f, unit, tint, stroke)
            line(15.4f, 11.4f, 17.8f, 11.4f, unit, tint, stroke)
            line(15.4f, 14.4f, 17.8f, 14.4f, unit, tint, stroke)
        }

        // 文本片段：带折角的文档 + 三条文字。
        AppIcon.TextSnippet -> {
            path(unit, tint, stroke) { u ->
                moveTo(5.4f * u, 2.8f * u)
                lineTo(14.4f * u, 2.8f * u)
                lineTo(18.8f * u, 7.2f * u)
                lineTo(18.8f * u, 21.2f * u)
                lineTo(5.4f * u, 21.2f * u)
                close()
            }
            path(unit, tint, stroke) { u ->
                moveTo(14.2f * u, 3f * u)
                lineTo(14.2f * u, 7.4f * u)
                lineTo(18.6f * u, 7.4f * u)
            }
            line(8.4f, 11.2f, 15.8f, 11.2f, unit, tint, stroke)
            line(8.4f, 14.6f, 15.8f, 14.6f, unit, tint, stroke)
            line(8.4f, 18f, 13f, 18f, unit, tint, stroke)
        }

        // PDF 图片：带折角的文档 + 内部小图（太阳 + 山形）。
        AppIcon.PicturePdf -> {
            path(unit, tint, stroke) { u ->
                moveTo(4.8f * u, 2.8f * u)
                lineTo(14.4f * u, 2.8f * u)
                lineTo(19.2f * u, 7.6f * u)
                lineTo(19.2f * u, 21.2f * u)
                lineTo(4.8f * u, 21.2f * u)
                close()
            }
            path(unit, tint, stroke) { u ->
                moveTo(14.2f * u, 3f * u)
                lineTo(14.2f * u, 7.8f * u)
                lineTo(19f * u, 7.8f * u)
            }
            drawCircle(tint, radius = 1.1f * unit, center = dot(9.2f, 12.2f, unit), style = stroke)
            path(unit, tint, stroke) { u ->
                moveTo(6.6f * u, 18.4f * u)
                lineTo(10.4f * u, 14.4f * u)
                lineTo(12.8f * u, 16.8f * u)
                lineTo(14.6f * u, 15f * u)
                lineTo(17.4f * u, 18.4f * u)
            }
        }

        // 筛选 1：漏斗 + 数字 1。
        AppIcon.FilterOne -> {
            path(unit, tint, stroke) { u ->
                moveTo(3.4f * u, 5f * u)
                lineTo(20.6f * u, 5f * u)
                lineTo(13.9f * u, 12.6f * u)
                lineTo(13.9f * u, 19.6f * u)
                lineTo(10.1f * u, 17.2f * u)
                lineTo(10.1f * u, 12.6f * u)
                close()
            }
            line(11.2f, 8.6f, 12.4f, 7.6f, unit, tint, stroke)
            line(12.4f, 7.6f, 12.4f, 11.4f, unit, tint, stroke)
            line(11.2f, 11.4f, 13.6f, 11.4f, unit, tint, stroke)
        }
    }
}
