package com.pencil.chuangyin.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import kotlinx.coroutines.flow.Flow

/**
 * 主题状态：由 [ThemeRepository] 以 StateFlow 驱动 Compose 重组。
 *
 * 宪法约束：主题切换的唯一通道是通过 StateFlow 驱动重组，
 * 禁止 recreate() 或重启 Activity 来换肤。本数据类为幂等单位。
 */
data class ThemeState(
    val seedArgb: Long,
    val darkTheme: Boolean,
    val cornerStyle: CornerStyle,
    val cornerStrength: Float,
    val motionPreset: MotionPreset,
    /** 控件描边总开关（默认关闭，保持 M3 原生观感）。 */
    val outlineEnabled: Boolean = OUTLINE_ENABLED_DEFAULT,
    /** 控件描边粗细（dp），范围见 [OUTLINE_WIDTH_MIN_DP]..[OUTLINE_WIDTH_MAX_DP]。 */
    val outlineWidthDp: Float = OUTLINE_WIDTH_DEFAULT_DP,
    /** 控件描边配色模式：跟随主题种子色 / 自定义 RGB / RGB 彩虹渐变。 */
    val outlineColorMode: OutlineColorMode = OutlineColorMode.Seed,
    /** 自定义描边色（[OutlineColorMode.Rgb] 时生效），ARGB。 */
    val outlineArgb: Long = SeedOption.Orange.color,
    /** 描边不透明度，范围 0..1（独立于深色/浅色主题，描边颜色不受主题切换影响）。 */
    val outlineAlpha: Float = OUTLINE_ALPHA_DEFAULT,
    /** 全局粒子飘落效果开关（默认关闭）。 */
    val snowfallEnabled: Boolean = SNOWFALL_ENABLED_DEFAULT,
    /** 粒子飘落类型：雪花 / 雨滴 / 落叶 / 花瓣。 */
    val particleType: ParticleType = ParticleType.Snow,
    /** 粒子飘落速度倍率，范围 [PARTICLE_SPEED_MIN]..[PARTICLE_SPEED_MAX]（1=默认）。 */
    val particleSpeed: Float = PARTICLE_SPEED_DEFAULT,
    /** 自定义背景图片 content URI（null = 无自定义背景）。 */
    val backgroundUri: String? = null,
    /** 自定义背景不透明度，范围 [BACKGROUND_ALPHA_MIN]..1。 */
    val backgroundAlpha: Float = BACKGROUND_ALPHA_DEFAULT,
    /** 控件不透明度：surface 系颜色整体透明度，范围 [SURFACE_ALPHA_MIN]..1。 */
    val surfaceAlpha: Float = SURFACE_ALPHA_DEFAULT,
    /** 背景图模糊半径（dp），范围 0..[BACKGROUND_BLUR_MAX_DP]。 */
    val backgroundBlur: Float = BACKGROUND_BLUR_DEFAULT,
    /** 液态玻璃实现方式：C++ 原生覆盖层 / Kotlin Compose（默认 C++）。 */
    val liquidGlassRenderer: LiquidGlassRenderer = LIQUID_GLASS_RENDERER_DEFAULT,
    /** 液态玻璃控件开关（默认关闭，不含导航栏）。开启后按钮/开关/滑条/分段等由原生液态玻璃渲染。 */
    val liquidGlassControlsEnabled: Boolean = LIQUID_GLASS_CONTROLS_ENABLED_DEFAULT,
    /** 液态玻璃导航栏开关（默认关闭）。开启后底部导航栏改由原生液态玻璃渲染。 */
    val liquidGlassNavbarEnabled: Boolean = LIQUID_GLASS_NAVBAR_ENABLED_DEFAULT,
    /** 液态玻璃：背景模糊半径（dp）。 */
    val liquidGlassBlurDp: Float = LIQUID_GLASS_BLUR_DEFAULT,
    /** 液态玻璃：透镜高度（dp），越大折射/放大越强。 */
    val liquidGlassLensHeightDp: Float = LIQUID_GLASS_LENS_HEIGHT_DEFAULT,
    /** 液态玻璃：透镜位移量（dp）。 */
    val liquidGlassLensAmountDp: Float = LIQUID_GLASS_LENS_AMOUNT_DEFAULT,
    /** 液态玻璃：光谱色散强度（0~2）。 */
    val liquidGlassSpectral: Float = LIQUID_GLASS_SPECTRAL_DEFAULT,
    /** 液态玻璃：控件整体不透明度（0~1）。 */
    val liquidGlassTintAlpha: Float = LIQUID_GLASS_TINT_ALPHA_DEFAULT,
    /** 液态玻璃：是否投射阴影。 */
    val liquidGlassShadowOn: Boolean = LIQUID_GLASS_SHADOW_DEFAULT,
    /** 液态玻璃：是否启用玻璃材质（关闭退化为纯色面）。 */
    val liquidGlassGlassOn: Boolean = LIQUID_GLASS_GLASS_DEFAULT,
    /** 液态玻璃：是否绘制轮廓高光边缘。 */
    val liquidGlassContourEdge: Boolean = LIQUID_GLASS_CONTOUR_DEFAULT,
) {
    companion object {
        val Default = ThemeState(
            seedArgb = SeedOption.Orange.color,
            darkTheme = false,
            cornerStyle = CornerStyle.Rounded,
            cornerStrength = 0.5f,
            motionPreset = MotionPreset.Expressive,
        )
    }
}

/** 粒子飘落效果开关默认值。 */
const val SNOWFALL_ENABLED_DEFAULT = false

/** 自定义背景不透明度默认值（偏低以保证内容可读）。 */
const val BACKGROUND_ALPHA_DEFAULT = 0.3f

/** 自定义背景不透明度下限（避免滑到完全不可见）。 */
const val BACKGROUND_ALPHA_MIN = 0.05f

/** 控件不透明度默认值（完全不透明）。 */
const val SURFACE_ALPHA_DEFAULT = 1f

/** 控件不透明度下限（保留内容可读性）。 */
const val SURFACE_ALPHA_MIN = 0.1f

/** 背景模糊默认值（不模糊）。 */
const val BACKGROUND_BLUR_DEFAULT = 0f

/** 背景模糊上限（dp）。 */
const val BACKGROUND_BLUR_MAX_DP = 24f

/**
 * 粒子飘落类型：渲染由 core:ui 的 ParticleFallOverlay 按类型绘制，
 * 展示文案由 UI 层经 stringResource 提供。新增类型只需在此扩展枚举 + 覆盖层绘制分支。
 */
enum class ParticleType(val id: String) {
    Snow("particle_snow"),
    Rain("particle_rain"),
    Leaf("particle_leaf"),
    Petal("particle_petal"),
}

/** 描边开关默认值。 */
const val OUTLINE_ENABLED_DEFAULT = false

/** 描边粗细下限（dp）。 */
const val OUTLINE_WIDTH_MIN_DP = 0.5f

/** 描边粗细上限（dp）。 */
const val OUTLINE_WIDTH_MAX_DP = 4f

/** 描边粗细默认值（dp）。 */
const val OUTLINE_WIDTH_DEFAULT_DP = 1.5f

/** 描边不透明度默认值（完全不透明）。 */
const val OUTLINE_ALPHA_DEFAULT = 1f

/** 描边不透明度下限（避免完全不可见）。 */
const val OUTLINE_ALPHA_MIN = 0.05f

/** 粒子飘落速度倍率默认值。 */
const val PARTICLE_SPEED_DEFAULT = 1f

/** 粒子飘落速度倍率下限。 */
const val PARTICLE_SPEED_MIN = 0.2f

/** 粒子飘落速度倍率上限。 */
const val PARTICLE_SPEED_MAX = 3f

/** 液态玻璃控件开关默认值（关闭，保持 M3 原生观感）。 */
const val LIQUID_GLASS_CONTROLS_ENABLED_DEFAULT = false

/** 液态玻璃导航栏开关默认值（关闭，保持 M3 原生观感）。 */
const val LIQUID_GLASS_NAVBAR_ENABLED_DEFAULT = false

/** 液态玻璃实现方式默认值。 */
val LIQUID_GLASS_RENDERER_DEFAULT = LiquidGlassRenderer.Cpp

/**
 * 液态玻璃实现方式：
 * - [Cpp]：C++ 原生覆盖层（LiquidGlass-Cpp，`liblgm.so` + GLSurfaceView），控件由原生层绘制与交互；
 * - [Kyant]：Kotlin/Compose 实现（Kyant0/AndroidLiquidGlass 的 backdrop 折射/模糊，见 `:core:backdrop`）。
 *
 * 两种实现共用同一套设置开关与样式参数，仅在渲染路径上二选一，避免叠加绘制。
 */
enum class LiquidGlassRenderer(val id: String) {
    Cpp("lg_renderer_cpp"),
    Kyant("lg_renderer_kyant"),
}

/**
 * 控件描边配色模式：
 * - [Seed]：跟随主题种子色（取 ColorScheme.primary，换主题自动联动）；
 * - [Rgb]：使用用户在 RGB 调色盘中选定的自定义颜色；
 * - [Rainbow]：RGB 彩虹水平渐变描边。
 */
enum class OutlineColorMode(val id: String) {
    Seed("outline_seed"),
    Rgb("outline_rgb"),
    Rainbow("outline_rainbow"),
}

/** 种子色预设：颜色本体在代码层，展示文案由 UI 层经 stringResource 提供。 */
enum class SeedOption(val id: String, val color: Long) {
    Orange("seed_orange", 0xFFF6B626),
    Blue("seed_blue", 0xFF0277BD),
    Green("seed_green", 0xFF2E7D32),
    Purple("seed_purple", 0xFF7B1FA2),
    Rose("seed_rose", 0xFFC2185B),
    Teal("seed_teal", 0xFF00695C),
}

/**
 * 边角类型偏好：4 种边。实际 Shapes 由 [ShapeResolver] 结合强度与 dimens 生成。
 * 强度语义：圆边=弧度、方边=无强度、菱形/平行四边形=切角大小（强度增益 4 倍）。
 */
enum class CornerStyle(val id: String) {
    Rounded("corner_rounded"),
    Square("corner_square"),
    Diamond("corner_diamond"),
    Parallelogram("corner_parallelogram"),
}

/** 动效偏好。full=保守流畅，Expressive=MD3 Expressive 全局动效，Reduced=无障碍减弱。 */
enum class MotionPreset(val id: String) {
    Full("motion_full"),
    Expressive("motion_expressive"),
    Reduced("motion_reduced"),
}

/** 根据种子色生成浅色/深色 [ColorScheme]（core:theme 内部实现，供 AppTheme 使用）。 */
fun interface ColorSchemeProvider {
    fun colorScheme(seedArgb: Long, darkTheme: Boolean): ColorScheme
}

/** 根据边角类型 + 强度（0..1）解析 M3 [Shapes]（core:theme 内部实现，供 AppTheme 使用）。 */
fun interface ShapeResolver {
    fun shapes(style: CornerStyle, strength: Float): Shapes
}

/**
 * 主题仓库接口：Core/Feature 层只依赖本接口读写主题，具体实现（DataStore）由 Hilt 绑定。
 */
interface ThemeRepository {

    /** 主题状态流：UI 用 collectAsStateWithLifecycle 订阅驱动重组。 */
    val state: Flow<ThemeState>

    suspend fun setSeedColor(argb: Long)

    suspend fun setDarkTheme(enabled: Boolean)

    suspend fun setCornerStyle(style: CornerStyle)

    suspend fun setCornerStrength(strength: Float)

    suspend fun setMotionPreset(preset: MotionPreset)

    suspend fun setOutlineEnabled(enabled: Boolean)

    suspend fun setOutlineWidth(widthDp: Float)

    suspend fun setOutlineColorMode(mode: OutlineColorMode)

    suspend fun setOutlineArgb(argb: Long)

    /** 设置描边不透明度（0..1）。 */
    suspend fun setOutlineAlpha(alpha: Float)

    suspend fun setSnowfallEnabled(enabled: Boolean)

    suspend fun setParticleType(type: ParticleType)

    /** 设置粒子飘落速度倍率。 */
    suspend fun setParticleSpeed(speed: Float)

    /** 设置/清除自定义背景（传 null 清除）。 */
    suspend fun setBackgroundUri(uri: String?)

    suspend fun setBackgroundAlpha(alpha: Float)

    /** 设置控件不透明度（surface 系整体透明度）。 */
    suspend fun setSurfaceAlpha(alpha: Float)

    /** 设置背景图模糊半径（dp）。 */
    suspend fun setBackgroundBlur(blurDp: Float)

    /** 设置液态玻璃实现方式（C++ 原生覆盖层 / Kotlin Compose）。 */
    suspend fun setLiquidGlassRenderer(renderer: LiquidGlassRenderer)

    /** 设置液态玻璃控件开关。 */
    suspend fun setLiquidGlassControlsEnabled(enabled: Boolean)

    /** 设置液态玻璃导航栏开关。 */
    suspend fun setLiquidGlassNavbarEnabled(enabled: Boolean)

    /** 设置液态玻璃背景模糊半径（dp）。 */
    suspend fun setLiquidGlassBlur(blurDp: Float)

    /** 设置液态玻璃透镜高度（dp）。 */
    suspend fun setLiquidGlassLensHeight(lensHeightDp: Float)

    /** 设置液态玻璃透镜位移量（dp）。 */
    suspend fun setLiquidGlassLensAmount(lensAmountDp: Float)

    /** 设置液态玻璃光谱色散强度。 */
    suspend fun setLiquidGlassSpectral(spectral: Float)

    /** 设置液态玻璃控件不透明度（0..1）。 */
    suspend fun setLiquidGlassTintAlpha(alpha: Float)

    /** 设置液态玻璃阴影开关。 */
    suspend fun setLiquidGlassShadowOn(enabled: Boolean)

    /** 设置液态玻璃玻璃材质开关。 */
    suspend fun setLiquidGlassGlassOn(enabled: Boolean)

    /** 设置液态玻璃轮廓高光开关。 */
    suspend fun setLiquidGlassContourEdge(enabled: Boolean)
}
