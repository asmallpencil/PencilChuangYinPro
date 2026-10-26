package com.pencil.chuangyin.desktop.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 桌面端动态配色 —— 对齐安卓端 MaterialKolorProvider 的语义：
 *
 * - 种子无彩色（HSV 饱和度 < 0.05）→ 纯中性灰阶（Monochrome），保证「白色」等低饱和种子
 *   不会因 Chroma 回填而偏色，这正是安卓端修复「白色永远调不出」的关键。
 * - 有彩色种子 → Fidelity 风格：保留种子色相与彩度生成 tonal palette。
 *
 * 桌面没有 materialkolor 制品，这里用 OKLab / OKLCH 手写实现（与 Material 3 官方
 * 色调算法同一色彩空间），tone → OKLab L 采用经验映射 L = (tone/100)^0.7。
 */
object OkLab {

    private fun srgbToLinear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun linearToSrgb(c: Double): Double =
        if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055

    /** sRGB(0..255) → OKLab(L, a, b) */
    fun fromArgb(argb: Long): DoubleArray {
        val r = srgbToLinear(((argb shr 16) and 0xFF) / 255.0)
        val g = srgbToLinear(((argb shr 8) and 0xFF) / 255.0)
        val b = srgbToLinear((argb and 0xFF) / 255.0)

        val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

        val l_ = cbrt(l)
        val m_ = cbrt(m)
        val s_ = cbrt(s)

        return doubleArrayOf(
            0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
            1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
            0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_,
        )
    }

    /** OKLab → 线性 sRGB（分量可能越界，返回值用于色域判断） */
    private fun toLinear(L: Double, a: Double, b: Double): DoubleArray {
        val l_ = L + 0.3963377774 * a + 0.2158037573 * b
        val m_ = L - 0.1055613458 * a - 0.0638541728 * b
        val s_ = L - 0.0894841775 * a - 1.2914855480 * b

        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_

        return doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    /**
     * OKLCH → 0xFFRRGGBB。超出 sRGB 色域时迭代降低彩度（保持明度与色相），
     * 与 Material 的 gamut mapping 行为一致。
     */
    fun oklchToArgb(L: Double, chroma: Double, hueRad: Double): Long {
        var c = chroma.coerceAtLeast(0.0)
        val l = L.coerceIn(0.0, 1.0)
        repeat(48) {
            val a = c * kotlin.math.cos(hueRad)
            val b = c * kotlin.math.sin(hueRad)
            val lin = toLinear(l, a, b)
            val inGamut = lin.all { it >= -0.0005 && it <= 1.0005 }
            if (inGamut) {
                val r = (linearToSrgb(lin[0].coerceIn(0.0, 1.0)) * 255.0).toInt().coerceIn(0, 255)
                val g = (linearToSrgb(lin[1].coerceIn(0.0, 1.0)) * 255.0).toInt().coerceIn(0, 255)
                val bl = (linearToSrgb(lin[2].coerceIn(0.0, 1.0)) * 255.0).toInt().coerceIn(0, 255)
                return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or bl.toLong()
            }
            c *= 0.96
            if (c < 1e-4) {
                // 纯灰
                val v = (linearToSrgb(l.coerceIn(0.0, 1.0)) * 255.0).toInt().coerceIn(0, 255)
                return 0xFF000000L or (v.toLong() shl 16) or (v.toLong() shl 8) or v.toLong()
            }
        }
        val v = (linearToSrgb(l) * 255.0).toInt().coerceIn(0, 255)
        return 0xFF000000L or (v.toLong() shl 16) or (v.toLong() shl 8) or v.toLong()
    }
}

/** tone(0..100) → OKLab 明度 */
private fun toneToL(tone: Double): Double = (tone / 100.0).pow(0.7)

/**
 * 单条色调板。与 Material 的 tonal palette 概念一致：固定色相与彩度，按 tone 取色。
 */
private class TonalPalette(val hueRad: Double, val chroma: Double) {
    fun at(tone: Double): Color = Color(OkLab.oklchToArgb(toneToL(tone), chroma, hueRad))
}

/** 种子色解析结果 */
private data class SeedSpec(val hueRad: Double, val chroma: Double, val monochrome: Boolean)

private fun resolveSeed(seedArgb: Long): SeedSpec {
    val lab = OkLab.fromArgb(seedArgb)
    val chroma = hypot(lab[1], lab[2])
    val hue = kotlin.math.atan2(lab[2], lab[1])
    // 与安卓端 MaterialKolorProvider 完全一致：亮度饱和度而非 OKLCH 彩度做判定，
    // 保证「接近白色的种子」走中性分支。
    val r = ((seedArgb shr 16) and 0xFF) / 255f
    val g = ((seedArgb shr 8) and 0xFF) / 255f
    val b = (seedArgb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val hsvSat = if (max <= 0f) 0f else (max - min) / max
    val monochrome = hsvSat < 0.05f
    return SeedSpec(
        hueRad = if (abs(lab[1]) < 1e-6 && abs(lab[2]) < 1e-6) 0.0 else hue,
        chroma = if (monochrome) 0.0 else chroma.coerceAtLeast(0.02),
        monochrome = monochrome,
    )
}

/**
 * 生成桌面端 ColorScheme。
 * @param seedArgb 0xAARRGGBB 种子色
 * @param darkTheme 是否深色
 */
fun generateColorScheme(seedArgb: Long, darkTheme: Boolean): ColorScheme {
    val seed = resolveSeed(seedArgb)

    val primaryPalette = TonalPalette(seed.hueRad, seed.chroma)
    val secondaryPalette = TonalPalette(seed.hueRad, seed.chroma * 0.36)
    val tertiaryPalette = TonalPalette(seed.hueRad + Math.toRadians(60.0), seed.chroma * 0.56)
    val neutralPalette = TonalPalette(seed.hueRad, seed.chroma * 0.04)
    val neutralVariantPalette = TonalPalette(seed.hueRad, seed.chroma * 0.12)
    // 错误色固定色相（OKLCH 约 27°），与 M3 baseline 一致
    val errorPalette = TonalPalette(Math.toRadians(27.0), 0.19)

    fun c(p: TonalPalette, tone: Double) = p.at(tone)

    return if (!darkTheme) {
        lightColorScheme(
            primary = c(primaryPalette, 40.0),
            onPrimary = c(primaryPalette, 100.0),
            primaryContainer = c(primaryPalette, 90.0),
            onPrimaryContainer = c(primaryPalette, 10.0),
            inversePrimary = c(primaryPalette, 80.0),
            secondary = c(secondaryPalette, 40.0),
            onSecondary = c(secondaryPalette, 100.0),
            secondaryContainer = c(secondaryPalette, 90.0),
            onSecondaryContainer = c(secondaryPalette, 10.0),
            tertiary = c(tertiaryPalette, 40.0),
            onTertiary = c(tertiaryPalette, 100.0),
            tertiaryContainer = c(tertiaryPalette, 90.0),
            onTertiaryContainer = c(tertiaryPalette, 10.0),
            background = c(neutralPalette, 98.0),
            onBackground = c(neutralPalette, 10.0),
            surface = c(neutralPalette, 98.0),
            onSurface = c(neutralPalette, 10.0),
            surfaceVariant = c(neutralVariantPalette, 90.0),
            onSurfaceVariant = c(neutralVariantPalette, 30.0),
            surfaceTint = c(primaryPalette, 40.0),
            inverseSurface = c(neutralPalette, 20.0),
            inverseOnSurface = c(neutralPalette, 95.0),
            error = c(errorPalette, 40.0),
            onError = c(errorPalette, 100.0),
            errorContainer = c(errorPalette, 90.0),
            onErrorContainer = c(errorPalette, 10.0),
            outline = c(neutralVariantPalette, 50.0),
            outlineVariant = c(neutralVariantPalette, 80.0),
            scrim = Color(0xFF000000),
            surfaceBright = c(neutralPalette, 98.0),
            surfaceDim = c(neutralPalette, 87.0),
            surfaceContainerLowest = c(neutralPalette, 100.0),
            surfaceContainerLow = c(neutralPalette, 96.0),
            surfaceContainer = c(neutralPalette, 94.0),
            surfaceContainerHigh = c(neutralPalette, 92.0),
            surfaceContainerHighest = c(neutralPalette, 90.0),
        )
    } else {
        darkColorScheme(
            primary = c(primaryPalette, 80.0),
            onPrimary = c(primaryPalette, 20.0),
            primaryContainer = c(primaryPalette, 30.0),
            onPrimaryContainer = c(primaryPalette, 90.0),
            inversePrimary = c(primaryPalette, 40.0),
            secondary = c(secondaryPalette, 80.0),
            onSecondary = c(secondaryPalette, 20.0),
            secondaryContainer = c(secondaryPalette, 30.0),
            onSecondaryContainer = c(secondaryPalette, 90.0),
            tertiary = c(tertiaryPalette, 80.0),
            onTertiary = c(tertiaryPalette, 20.0),
            tertiaryContainer = c(tertiaryPalette, 30.0),
            onTertiaryContainer = c(tertiaryPalette, 90.0),
            background = c(neutralPalette, 6.0),
            onBackground = c(neutralPalette, 90.0),
            surface = c(neutralPalette, 6.0),
            onSurface = c(neutralPalette, 90.0),
            surfaceVariant = c(neutralVariantPalette, 30.0),
            onSurfaceVariant = c(neutralVariantPalette, 80.0),
            surfaceTint = c(primaryPalette, 80.0),
            inverseSurface = c(neutralPalette, 90.0),
            inverseOnSurface = c(neutralPalette, 20.0),
            error = c(errorPalette, 80.0),
            onError = c(errorPalette, 20.0),
            errorContainer = c(errorPalette, 30.0),
            onErrorContainer = c(errorPalette, 90.0),
            outline = c(neutralVariantPalette, 60.0),
            outlineVariant = c(neutralVariantPalette, 30.0),
            scrim = Color(0xFF000000),
            surfaceBright = c(neutralPalette, 24.0),
            surfaceDim = c(neutralPalette, 6.0),
            surfaceContainerLowest = c(neutralPalette, 4.0),
            surfaceContainerLow = c(neutralPalette, 10.0),
            surfaceContainer = c(neutralPalette, 12.0),
            surfaceContainerHigh = c(neutralPalette, 17.0),
            surfaceContainerHighest = c(neutralPalette, 22.0),
        )
    }
}

/** 近似 HSV 饱和度，供设置页展示/调试使用。 */
fun hsvSaturation(argb: Long): Float {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    return if (max <= 0f) 0f else (max - min) / max
}

/** 内部用：两点距离（保留以便后续做色差比对）。 */
internal fun colorDistance(c1: Color, c2: Color): Float {
    val a = OkLab.fromArgb(c1.value.toLong())
    val b = OkLab.fromArgb(c2.value.toLong())
    return sqrt((a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2)).toFloat()
}
