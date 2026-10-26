package com.pencil.chuangyin.core.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 文档扫描算法合成图仪器测试（对应 spec AC-1 / AC-2 的可自动化部分）：
 * 3 张程序化合成的"纸面 + 内容"图（印刷线条/强透视小纸/手写块状），
 * 验证 找边→透视→去阴影→文字提取 全链路；纯灰图验证检测失败时整图降级不崩溃。
 *
 * 真机实拍识别率验收为 AC-8（10 张样张，人工量规）。
 *
 * 在连接的 ARM 设备上运行：
 *   gradle :core:scanner:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class DocumentScannerInstrumentedTest {

    private val scanner = OpenCvDocumentScanner()

    private val w = 1080
    private val h = 1920

    /** 合成场景：背景刷法 + 已知四角 TL/TR/BR/BL + 纸面内容画法。 */
    private data class Scene(
        val name: String,
        val quad: List<PointF>,
        val tolerancePx: Double,
        val paintBackground: Canvas.() -> Unit,
        val paintInk: Canvas.() -> Unit,
    )

    private fun scenePrintedLines(): Scene = Scene(
        name = "印刷线条-大纸",
        tolerancePx = 60.0,
        quad = listOf(
            PointF(235f, 430f), PointF(855f, 385f),
            PointF(895f, 1495f), PointF(195f, 1540f),
        ),
        paintBackground = { drawColor(Color.rgb(128, 128, 128)) },
        paintInk = {
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                strokeWidth = 14f
                strokeCap = Paint.Cap.ROUND
            }
            var y = 600f
            repeat(14) {
                drawLine(320f, y, 780f - (it % 3) * 120f, y, ink)
                y += 52f
            }
            drawRect(320f, 1380f, 560f, 1460f, Paint().apply { color = Color.BLACK })
        },
    )

    private fun sceneStrongPerspective(): Scene = Scene(
        name = "强透视-渐变桌面",
        tolerancePx = 80.0,
        quad = listOf(
            PointF(300f, 560f), PointF(760f, 500f),
            PointF(820f, 1320f), PointF(240f, 1380f),
        ),
        paintBackground = {
            val p = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    Color.rgb(96, 78, 60), Color.rgb(48, 40, 34),
                    Shader.TileMode.CLAMP,
                )
            }
            drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        },
        paintInk = {
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
            }
            var y = 700f
            repeat(10) {
                drawLine(380f, y, 720f - (it % 4) * 60f, y, ink)
                y += 48f
            }
        },
    )

    private fun sceneHandwriting(): Scene = Scene(
        name = "手写块状-微倾大纸",
        tolerancePx = 60.0,
        quad = listOf(
            PointF(180f, 300f), PointF(900f, 360f),
            PointF(860f, 1620f), PointF(140f, 1560f),
        ),
        paintBackground = { drawColor(Color.rgb(150, 150, 150)) },
        paintInk = {
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                strokeWidth = 22f
                strokeCap = Paint.Cap.ROUND
            }
            // 短斜线/勾选状笔画模拟手写字。
            val rows = listOf(520f, 660f, 800f, 940f, 1080f, 1220f)
            rows.forEachIndexed { row, y ->
                var x = 300f
                repeat(4) {
                    drawLine(x, y, x + 40f, y - 26f + (row + it) % 2 * 40f, ink)
                    x += 130f
                }
            }
            val block = Paint().apply { color = Color.BLACK }
            drawRect(300f, 1380f, 420f, 1500f, block)
            drawCircle(560f, 1440f, 55f, block)
        },
    )

    private fun render(scene: Scene): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        scene.paintBackground(canvas)

        val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val q = scene.quad
        val path = Path().apply {
            moveTo(q[0].x, q[0].y)
            lineTo(q[1].x, q[1].y)
            lineTo(q[2].x, q[2].y)
            lineTo(q[3].x, q[3].y)
            close()
        }
        canvas.drawPath(path, paper)
        canvas.save()
        canvas.clipPath(path)
        scene.paintInk(canvas)
        canvas.restore()
        return bmp
    }

    // ---- AC-1：三个场景各自检测四角 ----

    @Test
    fun detectQuad_locksCorners_onAllSyntheticScenes() {
        val scenes = listOf(scenePrintedLines(), sceneStrongPerspective(), sceneHandwriting())
        scenes.forEach { scene ->
            val quad = scanner.detectQuad(render(scene))
            assertNotNull("场景[${scene.name}]必须检测到四角", quad)
            quad!!
            assertEquals(4, quad.size)
            scene.quad.forEachIndexed { i, expected ->
                val d = hypot(
                    (quad[i].x - expected.x).toDouble(),
                    (quad[i].y - expected.y).toDouble(),
                )
                assertTrue(
                    "场景[${scene.name}]角点 $i 误差 $d px 超过 ${scene.tolerancePx} px",
                    d < scene.tolerancePx,
                )
            }
        }
    }

    // ---- AC-1：三场景扫描输出纯黑白、已透视校正 ----

    @Test
    fun scan_document_outputsPureBlackWhiteWarpedImage_onAllScenes() {
        val scenes = listOf(scenePrintedLines(), sceneStrongPerspective(), sceneHandwriting())
        scenes.forEach { scene ->
            val result = scanner.scan(render(scene), ScanMode.Document)
            assertNotNull("场景[${scene.name}] detectedQuad 不应为空", result.detectedQuad)
            assertTrue("场景[${scene.name}] 应执行透视校正", result.warped)

            val out = result.output
            var black = 0
            var sampled = 0
            var nonMono = 0
            var x = 0
            while (x < out.width) {
                var y = 0
                while (y < out.height) {
                    val pixel = out.getPixel(x, y)
                    val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    when {
                        gray < 40 -> black++
                        gray > 215 -> Unit
                        else -> nonMono++
                    }
                    sampled++
                    y += 17
                }
                x += 17
            }
            val blackRatio = black.toFloat() / sampled
            assertTrue(
                "场景[${scene.name}] 黑像素占比异常: $blackRatio",
                blackRatio in 0.005f..0.6f,
            )
            assertTrue(
                "场景[${scene.name}] 存在非黑白像素: $nonMono/$sampled",
                nonMono.toDouble() / sampled < 0.01,
            )
            assertTrue(maxOf(out.width, out.height) <= 2000)
        }
    }

    // ---- AC-2：无纸边（纯灰图）→ detectedQuad 为 null，降级去阴影灰度，不抛异常 ----

    @Test
    fun scan_textureOnlyImage_detectedQuadNull_andDegradesToEnhancedGray() {
        val bmp = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.rgb(140, 140, 140))

        // 检测 100 次必须稳定返回 null 且不崩溃。
        repeat(100) {
            assertNull(scanner.detectQuad(bmp))
        }
        // 整图扫描走灰度增强降级（不再整图二值化），输出非空且含中间灰度。
        repeat(10) {
            val result = scanner.scan(bmp, ScanMode.Document)
            assertNull(result.detectedQuad)
            val out = result.output
            assertTrue(out.width > 0 && out.height > 0)
            var midGray = 0
            var sampled = 0
            var x = 0
            while (x < out.width) {
                var y = 0
                while (y < out.height) {
                    val g = (Color.red(out.getPixel(x, y)) + Color.green(out.getPixel(x, y)) +
                        Color.blue(out.getPixel(x, y))) / 3
                    if (g in 40..215) midGray++
                    sampled++
                    y += 23
                }
                x += 23
            }
            assertTrue("降级输出应为灰度而非二值: midGray=$midGray/$sampled", midGray > sampled * 0.5)
        }
    }

    // ---- 出界纸张：纸延伸出图像边界（Canny 边缘不闭合，Mask 路径必须兜住） ----

    @Test
    fun detectQuad_paperPartiallyOutOfFrame_stillDetected() {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(70, 70, 70))
        // 白纸四分之三出画（左、上越界），可见角在图内。
        val paper = Paint().apply { color = Color.WHITE }
        canvas.drawRect(-300f, -200f, 820f, 1600f, paper)
        val ink = Paint().apply { color = Color.BLACK }
        var y = 300f
        repeat(10) {
            canvas.drawLine(150f, y, 650f, y, ink)
            y += 90f
        }

        val quad = scanner.detectQuad(bmp)
        assertNotNull("出界纸张必须仍被检测到", quad)
        // 可见部分应大致覆盖：右边界 ~820、下边界 ~1600（允许 minAreaRect 兜底误差）。
        quad!!
        val maxX = quad.maxOf { it.x }
        val maxY = quad.maxOf { it.y }
        assertTrue("检测框右界 $maxX 偏离可见纸边", maxX in 700f..940f)
        assertTrue("检测框下界 $maxY 偏离可见纸边", maxY in 1480f..1720f)
    }

    // ---- 多纸叠放：上层纸边缘与下层纸对比度低，应框住整叠可见区域 ----

    @Test
    fun detectQuad_stackedPapers_detected() {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(80, 80, 80))
        val paper = Paint().apply { color = Color.WHITE }
        // 下层纸（露出右侧与底部）。
        canvas.drawRect(260f, 340f, 980f, 1620f, paper)
        // 上层纸（略偏左上）。
        canvas.drawRect(140f, 260f, 860f, 1480f, paper)
        val ink = Paint().apply { color = Color.BLACK }
        var y = 500f
        repeat(8) {
            canvas.drawLine(260f, y, 720f, y, ink)
            y += 110f
        }

        val quad = scanner.detectQuad(bmp)
        assertNotNull("叠放纸张必须被检测到", quad)
        quad!!
        // 检测框应包住整叠的外接范围（Mask 是两纸并集）。
        val maxX = quad.maxOf { it.x }
        val maxY = quad.maxOf { it.y }
        assertTrue("检测框应覆盖叠纸右界: $maxX", maxX > 820f)
        assertTrue("检测框应覆盖叠纸下界: $maxY", maxY > 1400f)
    }

    // ---- documentize：对"只有纸面"的图输出纯黑白，且对黑白图幂等 ----

    @Test
    fun documentize_outputsPureBlackWhite_andIdempotent() {
        val paper = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(paper)
        canvas.drawColor(Color.WHITE)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 12f
        }
        var y = 300f
        repeat(12) {
            canvas.drawLine(180f, y, 900f - (it % 3) * 150f, y, ink)
            y += 70f
        }

        val doc = scanner.documentize(paper)
        assertPureBlackWhite(doc)

        // 幂等：对黑白输出再 documentize 仍为黑白。
        val twice = scanner.documentize(doc)
        assertPureBlackWhite(twice)
    }

    private fun assertPureBlackWhite(out: Bitmap) {
        var nonMono = 0
        var black = 0
        var sampled = 0
        var x = 0
        while (x < out.width) {
            var y = 0
            while (y < out.height) {
                val g = (Color.red(out.getPixel(x, y)) + Color.green(out.getPixel(x, y)) +
                    Color.blue(out.getPixel(x, y))) / 3
                when {
                    g < 40 -> black++
                    g > 215 -> Unit
                    else -> nonMono++
                }
                sampled++
                y += 17
            }
            x += 17
        }
        assertTrue("存在非黑白像素: $nonMono/$sampled", nonMono.toDouble() / sampled < 0.01)
        assertTrue("黑像素占比过低: $black/$sampled", black > sampled * 0.005)
    }

    // ---- 光照不均的书页：页面下半部分偏暗，全局阈值会丢，局部自适应必须罩住整页 ----

    @Test
    fun detectQuad_unevenLightingPage_fullPageCovered() {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(60, 60, 60))
        val paper = Paint().apply { color = Color.WHITE }
        // 整页纸（含下半暗端）。
        canvas.drawRect(150f, 300f, 930f, 1650f, paper)
        // 顶部亮、底部暗的垂直光照渐变叠加在纸面上（模拟手持遮挡/边缘光照衰减）。
        val shade = Paint().apply {
            shader = LinearGradient(
                0f, 300f, 0f, 1650f,
                Color.argb(0, 0, 0, 0),
                Color.argb(140, 0, 0, 0),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(150f, 300f, 930f, 1650f, shade)
        val ink = Paint().apply { color = Color.BLACK }
        var y = 480f
        repeat(9) {
            canvas.drawLine(260f, y, 820f, y, ink)
            y += 110f
        }

        val quad = scanner.detectQuad(bmp)
        assertNotNull("光照不均书页必须被检测到", quad)
        quad!!
        val maxY = quad.maxOf { it.y }
        val minY = quad.minOf { it.y }
        assertTrue("检测框必须罩住暗端纸底: maxY=$maxY", maxY > 1500f)
        assertTrue("检测框必须罩住纸顶: minY=$minY", minY < 450f)
    }

    @Test
    fun cropWarp_arbitraryQuad_returnsPositiveSize() {
        val src = render(scenePrintedLines())
        val out = scanner.cropWarp(
            src,
            listOf(
                PointF(250f, 450f), PointF(840f, 410f),
                PointF(880f, 1480f), PointF(210f, 1510f),
            ),
        )
        assertTrue(out.width > 100 && out.height > 100)
    }

    @Test
    fun normalMode_returnsImageUnprocessed() {
        val src = render(scenePrintedLines())
        val result = scanner.scan(src, ScanMode.Normal)
        assertEquals(src, result.output)
    }
}
