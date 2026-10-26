package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.ai.AgentTool
import com.pencil.chuangyin.desktop.ai.ToolContext
import com.pencil.chuangyin.desktop.ai.ToolOutput
import com.pencil.chuangyin.desktop.text.PrinterCommand
import java.awt.Color
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * 绘图工具：把模型给出的图形清单渲染为 PNG 保存到工作区，可选直接打印。
 *
 * 渲染出的 PNG 会作为 IMAGE 产物回传，界面据此内联预览。
 */
class DrawTools(private val workspace: Workspace) {

    val tools: List<AgentTool> = listOf(
        agentTool(
            name = ToolNames.DRAW_IMAGE,
            description = "根据图形清单绘制一张 PNG 图片并保存到工作区，可立即打印。" +
                "适合画表情、线条画、卡片、简单示意图。",
            parameters = ToolSchema.parameters(
                required = listOf(ToolParams.SHAPES),
                properties = mapOf(
                    ToolParams.SHAPES to ToolSchema.objectArray(
                        description = SHAPES_DESCRIPTION,
                        properties = mapOf(
                            ToolParams.TYPE to ToolSchema.string(
                                "图形类型，必填。",
                                ToolParamValues.SHAPE_TYPES,
                            ),
                            ToolParams.X to ToolSchema.number("x 坐标（rect/round_rect/text 用）。"),
                            ToolParams.Y to ToolSchema.number("y 坐标（rect/round_rect/text 用；text 为基线）。"),
                            ToolParams.W to ToolSchema.number("宽度（rect/round_rect 用）。"),
                            ToolParams.H to ToolSchema.number("高度（rect/round_rect 用）。"),
                            ToolParams.RADIUS to ToolSchema.number("圆角半径（round_rect 用，默认 8）。"),
                            ToolParams.CX to ToolSchema.number("圆心 X（circle/ellipse 用）。"),
                            ToolParams.CY to ToolSchema.number("圆心 Y（circle/ellipse 用）。"),
                            ToolParams.R to ToolSchema.number("半径（circle 用）。"),
                            ToolParams.RX to ToolSchema.number("横向半径（ellipse 用）。"),
                            ToolParams.RY to ToolSchema.number("纵向半径（ellipse 用）。"),
                            ToolParams.X1 to ToolSchema.number("起点 X（line 用）。"),
                            ToolParams.Y1 to ToolSchema.number("起点 Y（line 用）。"),
                            ToolParams.X2 to ToolSchema.number("终点 X（line 用）。"),
                            ToolParams.Y2 to ToolSchema.number("终点 Y（line 用）。"),
                            ToolParams.POINTS to ToolSchema.string("点序列，形如 \"x1,y1 x2,y2\"（polygon/polyline 用）。"),
                            ToolParams.TEXT to ToolSchema.string("文字内容（text 用）。"),
                            ToolParams.SIZE to ToolSchema.number("字号（text 用，默认 24）。"),
                            ToolParams.BOLD to ToolSchema.boolean("是否加粗（text 用）。"),
                            ToolParams.ALIGN to ToolSchema.string(
                                "水平对齐（text 用），取值 start/center/end。",
                                ToolParamValues.ALIGN_VALUES,
                            ),
                            ToolParams.COLOR to ToolSchema.string("颜色，十六进制如 #000000。"),
                            ToolParams.FILLED to ToolSchema.boolean("是否填充，默认 true。"),
                            ToolParams.STROKE_WIDTH to ToolSchema.number("线宽（不填充时生效）。"),
                        ),
                        required = listOf(ToolParams.TYPE),
                    ),
                    ToolParams.WIDTH to ToolSchema.integer("画布宽度像素，默认等于打印头宽度（384）。"),
                    ToolParams.HEIGHT to ToolSchema.integer("画布高度像素，默认 384。"),
                    ToolParams.BACKGROUND to ToolSchema.string("背景色，十六进制如 #FFFFFF，默认白色。"),
                    ToolParams.PRINT_AFTER_DRAW to ToolSchema.boolean(
                        "绘制完成后是否立即打印（未连接打印机时仅保存文件），默认 false。",
                    ),
                    ToolParams.FILE_NAME to ToolSchema.string("可选文件名（不含路径），默认自动命名，扩展名固定为 .png。"),
                ),
            ),
        ) { args, context -> drawImage(args, context) },
    )

    private suspend fun drawImage(args: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val shapes = args.arrayOrNull(ToolParams.SHAPES)
        if (shapes == null || shapes.isEmpty()) {
            return@withContext failure("缺少参数 shapes，或图形清单为空。")
        }

        val canvasWidth = (args.intOrNull(ToolParams.WIDTH) ?: PrinterCommand.PRINT_WIDTH_PX)
            .coerceIn(ShapeRenderer.WIDTH_RANGE)
        val canvasHeight = (args.intOrNull(ToolParams.HEIGHT) ?: DEFAULT_HEIGHT)
            .coerceIn(ShapeRenderer.HEIGHT_RANGE)
        val canvasBackground = ShapeRenderer.parseColor(args.stringOrNull(ToolParams.BACKGROUND), COLOR_WHITE)

        val parseResult = ShapeRenderer.parse(shapes, canvasBackground)
        if (parseResult.shapes.isEmpty()) {
            return@withContext failure("没有任何可渲染的图形。${parseResult.warnings.joinToString("；")}")
        }

        context.reportProgress("正在绘制图片")
        val image = ShapeRenderer.render(canvasWidth, canvasHeight, canvasBackground, parseResult.shapes)
        val target = resolveTargetFile(args.stringOrNull(ToolParams.FILE_NAME))
        val saved = runCatching {
            target.parentFile?.let { if (!it.exists()) it.mkdirs() }
            ImageIO.write(image, "png", target)
        }.getOrDefault(false)
        if (!saved) return@withContext failure("图片保存失败：${workspace.displayPath(target)}")

        val builder = StringBuilder()
        builder.appendLine(
            "已绘制 ${parseResult.shapes.size} 个图形，保存为 ${workspace.displayPath(target)}（$canvasWidth×$canvasHeight）。",
        )
        if (parseResult.warnings.isNotEmpty()) {
            builder.appendLine("跳过：${parseResult.warnings.joinToString("；")}")
        }
        if (args.booleanOr(ToolParams.PRINT_AFTER_DRAW, false)) {
            builder.appendLine(PrintBridge.printImageFile(target, "绘图结果"))
        }

        ToolOutput(
            content = builder.toString().trim(),
            artifacts = listOf(imageArtifact(target)),
        )
    }

    private fun resolveTargetFile(fileName: String?): File {
        val root = workspace.writeRoot.apply { mkdirs() }
        val sanitized = fileName?.trim()?.let { sanitize(it) }?.takeIf { it.isNotEmpty() }
        if (sanitized != null) {
            val candidate = File(root, "$sanitized$FILE_SUFFIX")
            return if (candidate.exists()) File(root, "$sanitized-${System.currentTimeMillis().toString().takeLast(4)}$FILE_SUFFIX") else candidate
        }
        return PrintBridge.newOutputFile(FILE_PREFIX, "png").let { generated ->
            File(root, generated.name)
        }
    }

    /** 去掉路径分隔符与不合法字符，避免模型给出的名字逃逸出工作区。 */
    private fun sanitize(raw: String): String {
        val withoutExt = raw.removeSuffix(FILE_SUFFIX)
        return withoutExt.map { ch ->
            if (ch.isLetterOrDigit() || ch == '_' || ch == '-') ch else '_'
        }.joinToString("").lowercase(Locale.US)
    }

    private companion object {
        const val DEFAULT_HEIGHT = 384
        val COLOR_WHITE: Color = Color.WHITE
        const val FILE_PREFIX = "draw_"
        const val FILE_SUFFIX = ".png"

        const val SHAPES_DESCRIPTION = "图形数组。每个元素必须提供 type，取值：" +
            "rect{x,y,w,h}、round_rect{x,y,w,h,radius}、circle{cx,cy,r}、ellipse{cx,cy,rx,ry}、" +
            "line{x1,y1,x2,y2}、polygon/polyline{points:\"x1,y1 x2,y2 ...\"（空格分隔的点序列字符串）}、" +
            "text{x,y,text,size,bold,align}。" +
            "公共可选字段：color（十六进制如 #000000）、filled（是否填充，默认 true）、stroke_width（线宽）。" +
            "text 的 y 为文字基线位置，align 取值 start/center/end。"
    }
}
