package com.pencil.chuangyin.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 MaterialKolor 从单一种子色派生完整 Material 3 动态色板。
 * 该实现是上层唯一接触 material-kolor 的地方，符合「接口驱动、屏蔽第三方」的宪法。
 *
 * 风格选择（决定「调色盘所见」能否落到「界面所得」）：
 * - material-kolor 默认的 [PaletteStyle.TonalSpot] 会把主色板固定为 `hue = 种子, chroma = 36`，
 *   即无论用户选什么，饱和度都被强行改写，且无彩色种子（白 / 灰 / 黑）的 HCT 色相只是浮点噪声，
 *   于是「白色」会被映射成随机彩色（通常是红/粉），表现为白色永远调不出来、设色与调色盘不符。
 * - 因此这里按种子的 HSV 相对饱和度分流：
 *   - 近无彩色（白 / 灰 / 黑）→ [PaletteStyle.Monochrome]：全中性灰阶，白种子得到真正的白灰主题；
 *   - 有彩色 → [PaletteStyle.Fidelity]：保留种子自身的色相与彩度（不做 chroma 重映射），
 *     最接近「所见即所得」。
 */
@Singleton
class MaterialKolorColorSchemeProvider @Inject constructor() : ColorSchemeProvider {

    override fun colorScheme(seedArgb: Long, darkTheme: Boolean): ColorScheme =
        dynamicColorScheme(
            seedColor = Color(seedArgb),
            isDark = darkTheme,
            style = styleFor(seedArgb),
        )

    /** 按种子色的相对饱和度（(max - min) / max）判定风格，尺度无关，深浅种子都适用。 */
    private fun styleFor(seedArgb: Long): PaletteStyle {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(seedArgb.toInt(), hsv)
        return if (hsv[1] < ACHROMATIC_SEED_SATURATION) {
            PaletteStyle.Monochrome
        } else {
            PaletteStyle.Fidelity
        }
    }

    private companion object {
        /** 相对饱和度低于此值视为「无彩色」：白 / 灰 / 黑及其近邻。 */
        const val ACHROMATIC_SEED_SATURATION = 0.05f
    }
}
