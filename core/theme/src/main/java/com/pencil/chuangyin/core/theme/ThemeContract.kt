package com.pencil.chuangyin.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.compositionLocalOf
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
    /** 加载动画曲线样式（math-curve-loaders 曲线集 + 项目原生多形状）。 */
    val loadingAnimationStyle: LoadingAnimationStyle = LOADING_STYLE_DEFAULT,
    /** 加载动画粒子数，范围 [LOADING_PARTICLE_MIN]..[LOADING_PARTICLE_MAX]。 */
    val loadingParticleCount: Int = LOADING_PARTICLE_DEFAULT,
    /** 加载动画尾迹长度，范围 [LOADING_TRAIL_MIN]..[LOADING_TRAIL_MAX]。 */
    val loadingTrailSpan: Float = LOADING_TRAIL_DEFAULT,
    /** 加载动画循环时长（毫秒），范围 [LOADING_DURATION_MIN_MS]..[LOADING_DURATION_MAX_MS]。 */
    val loadingDurationMs: Int = LOADING_DURATION_DEFAULT_MS,
    /** 加载动画呼吸（细节缩放）时长（毫秒）。 */
    val loadingPulseDurationMs: Int = LOADING_PULSE_DEFAULT_MS,
    /** 加载动画整体旋转时长（毫秒）。 */
    val loadingRotationDurationMs: Int = LOADING_ROTATION_DEFAULT_MS,
    /** 加载动画轨迹线宽（0..100 视口单位），范围 [LOADING_STROKE_MIN]..[LOADING_STROKE_MAX]。 */
    val loadingStrokeWidth: Float = LOADING_STROKE_DEFAULT,
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

// ===================== 加载动画（math-curve-loaders） =====================

/** 加载动画粒子数下限。 */
const val LOADING_PARTICLE_MIN = 24

/** 加载动画粒子数上限。 */
const val LOADING_PARTICLE_MAX = 140

/** 加载动画粒子数默认值。 */
const val LOADING_PARTICLE_DEFAULT = 64

/** 加载动画尾迹长度下限。 */
const val LOADING_TRAIL_MIN = 0.12f

/** 加载动画尾迹长度上限。 */
const val LOADING_TRAIL_MAX = 0.68f

/** 加载动画尾迹长度默认值。 */
const val LOADING_TRAIL_DEFAULT = 0.38f

/** 加载动画循环时长下限（毫秒）。 */
const val LOADING_DURATION_MIN_MS = 2400

/** 加载动画循环时长上限（毫秒）。 */
const val LOADING_DURATION_MAX_MS = 12000

/** 加载动画循环时长默认值（毫秒）。 */
const val LOADING_DURATION_DEFAULT_MS = 4600

/** 加载动画呼吸时长下限（毫秒）。 */
const val LOADING_PULSE_MIN_MS = 1800

/** 加载动画呼吸时长上限（毫秒）。 */
const val LOADING_PULSE_MAX_MS = 10000

/** 加载动画呼吸时长默认值（毫秒）。 */
const val LOADING_PULSE_DEFAULT_MS = 4200

/** 加载动画旋转时长下限（毫秒）。 */
const val LOADING_ROTATION_MIN_MS = 6000

/** 加载动画旋转时长上限（毫秒）。 */
const val LOADING_ROTATION_MAX_MS = 60000

/** 加载动画旋转时长默认值（毫秒）。 */
const val LOADING_ROTATION_DEFAULT_MS = 28000

/** 加载动画轨迹线宽下限（0..100 视口单位）。 */
const val LOADING_STROKE_MIN = 2.5f

/** 加载动画轨迹线宽上限（0..100 视口单位）。 */
const val LOADING_STROKE_MAX = 7.5f

/** 加载动画轨迹线宽默认值（0..100 视口单位）。 */
const val LOADING_STROKE_DEFAULT = 5.5f

/**
 * 加载动画曲线样式：
 * - [Classic]：项目原有的 7 形状 morph 加载动画（graphics-shapes）；
 * - 其余为 math-curve-loaders 的 20 条数学曲线（粒子尾迹沿曲线流动）。
 *
 * 渲染实现见 core:ui 的 `MathCurveLoadingIndicator`；展示文案由 settings 模块经 stringResource 提供。
 */
enum class LoadingAnimationStyle(val id: String) {
    Classic("loading_classic"),
    ThinkingRose("loading_thinking_rose"),
    ThinkingFive("loading_thinking_five"),
    ThinkingNine("loading_thinking_nine"),
    RoseOrbit("loading_rose_orbit"),
    RoseTwo("loading_rose_two"),
    RoseThree("loading_rose_three"),
    RoseFour("loading_rose_four"),
    LissajousDrift("loading_lissajous_drift"),
    LemniscateBloom("loading_lemniscate_bloom"),
    HypotrochoidLoop("loading_hypotrochoid_loop"),
    SpiralThree("loading_spiral_three"),
    SpiralFour("loading_spiral_four"),
    SpiralFive("loading_spiral_five"),
    SpiralSix("loading_spiral_six"),
    ButterflyPhase("loading_butterfly_phase"),
    CardioidGlow("loading_cardioid_glow"),
    CardioidHeart("loading_cardioid_heart"),
    HeartWave("loading_heart_wave"),
    SpiralSearch("loading_spiral_search"),
    FourierFlow("loading_fourier_flow"),
}

/** 加载动画样式默认值（保持项目原生观感）。 */
val LOADING_STYLE_DEFAULT = LoadingAnimationStyle.Classic

/**
 * 加载动画运行时描述：由 [ThemeState] 派生，经 [LocalLoadingAnimation] 下发给 core:ui。
 *
 * 之所以单独抽一个不可变快照而不是直接把 [ThemeState] 塞进 CompositionLocal：
 * core:ui 只需要这 7 个字段，独立类型能让「加载动画」的依赖边界清晰，
 * 也避免 core:ui 因主题状态里其它字段变化而重组。
 */
data class LoadingAnimationSpec(
    val style: LoadingAnimationStyle = LOADING_STYLE_DEFAULT,
    val particleCount: Int = LOADING_PARTICLE_DEFAULT,
    val trailSpan: Float = LOADING_TRAIL_DEFAULT,
    val durationMs: Int = LOADING_DURATION_DEFAULT_MS,
    val pulseDurationMs: Int = LOADING_PULSE_DEFAULT_MS,
    val rotationDurationMs: Int = LOADING_ROTATION_DEFAULT_MS,
    val strokeWidth: Float = LOADING_STROKE_DEFAULT,
)

/** 把持久化的主题状态投影为加载动画运行时描述（含上下限兜底）。 */
fun ThemeState.toLoadingAnimationSpec(): LoadingAnimationSpec = LoadingAnimationSpec(
    style = loadingAnimationStyle,
    particleCount = loadingParticleCount.coerceIn(LOADING_PARTICLE_MIN, LOADING_PARTICLE_MAX),
    trailSpan = loadingTrailSpan.coerceIn(LOADING_TRAIL_MIN, LOADING_TRAIL_MAX),
    durationMs = loadingDurationMs.coerceIn(LOADING_DURATION_MIN_MS, LOADING_DURATION_MAX_MS),
    pulseDurationMs = loadingPulseDurationMs.coerceIn(LOADING_PULSE_MIN_MS, LOADING_PULSE_MAX_MS),
    rotationDurationMs = loadingRotationDurationMs.coerceIn(
        LOADING_ROTATION_MIN_MS,
        LOADING_ROTATION_MAX_MS,
    ),
    strokeWidth = loadingStrokeWidth.coerceIn(LOADING_STROKE_MIN, LOADING_STROKE_MAX),
)

/** 当前加载动画偏好的 CompositionLocal（由 [AppTheme] 下发）。 */
val LocalLoadingAnimation = compositionLocalOf { LoadingAnimationSpec() }

/**
 * 当前动效偏好的 CompositionLocal。
 *
 * 由 [AppTheme] 下发，core:ui 的动效基础设施（[com.pencil.chuangyin.core.ui.motion.AppMotion]）
 * 读取它决定 spring 参数：Expressive 允许轻微过冲，Full 用过冲为零的 spring，
 * Reduced 直接退化为短时长 tween（无障碍 / 低端机流畅优先）。
 *
 * 之所以放在 core:theme 而不是 core:ui：主题状态本身在 core:theme，AppTheme 是唯一注入点，
 * 这样 core:ui / feature:* 都能零成本读到同一份偏好，不需要各自注入。
 */
val LocalMotionPreset = compositionLocalOf { MotionPreset.Expressive }

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

    /** 设置加载动画曲线样式。 */
    suspend fun setLoadingAnimationStyle(style: LoadingAnimationStyle)

    /** 设置加载动画粒子数（24..140）。 */
    suspend fun setLoadingParticleCount(count: Int)

    /** 设置加载动画尾迹长度（0.12..0.68）。 */
    suspend fun setLoadingTrailSpan(span: Float)

    /** 设置加载动画循环时长（毫秒，2400..12000）。 */
    suspend fun setLoadingDurationMs(durationMs: Int)

    /** 设置加载动画呼吸时长（毫秒，1800..10000）。 */
    suspend fun setLoadingPulseDurationMs(durationMs: Int)

    /** 设置加载动画旋转时长（毫秒，6000..60000）。 */
    suspend fun setLoadingRotationDurationMs(durationMs: Int)

    /** 设置加载动画轨迹线宽（2.5..7.5）。 */
    suspend fun setLoadingStrokeWidth(width: Float)
}
