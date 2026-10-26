package com.pencil.chuangyin.desktop.theme

import java.util.prefs.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 主题持久化 —— 对齐安卓端 DataStore "pencil_print_theme" 的单一真相源语义。
 * 桌面用 java.util.prefs（Windows 注册表 HKCU\Software\JavaSoft\Prefs）替代 DataStore，
 * 不引入任何额外依赖。
 */
@Serializable
private data class StoredTheme(
    val seedArgb: Long = ThemeState.Default.seedArgb,
    val darkTheme: Boolean = ThemeState.Default.darkTheme,
    val cornerStyle: String = ThemeState.Default.cornerStyle.name,
    val cornerStrength: Float = ThemeState.Default.cornerStrength,
    val motionPreset: String = ThemeState.Default.motionPreset.name,
    val outlineEnabled: Boolean = OUTLINE_ENABLED_DEFAULT,
    val outlineWidthDp: Float = OUTLINE_WIDTH_DEFAULT_DP,
    val outlineColorMode: String = OutlineColorMode.Seed.name,
    val outlineArgb: Long = SeedOption.Orange.color,
    val outlineAlpha: Float = OUTLINE_ALPHA_DEFAULT,
    val backgroundPath: String? = null,
    val backgroundAlpha: Float = BACKGROUND_ALPHA_DEFAULT,
    val surfaceAlpha: Float = SURFACE_ALPHA_DEFAULT,
    val backgroundBlur: Float = BACKGROUND_BLUR_DEFAULT,
    val liquidGlassControlsEnabled: Boolean = LIQUID_GLASS_CONTROLS_ENABLED_DEFAULT,
    val liquidGlassNavbarEnabled: Boolean = LIQUID_GLASS_NAVBAR_ENABLED_DEFAULT,
    val liquidGlassBlurDp: Float = LIQUID_GLASS_BLUR_DEFAULT,
    val liquidGlassLensHeightDp: Float = LIQUID_GLASS_LENS_HEIGHT_DEFAULT,
    val liquidGlassLensAmountDp: Float = LIQUID_GLASS_LENS_AMOUNT_DEFAULT,
    val liquidGlassSpectral: Float = LIQUID_GLASS_SPECTRAL_DEFAULT,
    val liquidGlassTintAlpha: Float = LIQUID_GLASS_TINT_ALPHA_DEFAULT,
    val liquidGlassShadowOn: Boolean = LIQUID_GLASS_SHADOW_DEFAULT,
    val liquidGlassGlassOn: Boolean = LIQUID_GLASS_GLASS_DEFAULT,
    val liquidGlassContourEdge: Boolean = LIQUID_GLASS_CONTOUR_DEFAULT,
)

private fun ThemeState.toStored() = StoredTheme(
    seedArgb = seedArgb,
    darkTheme = darkTheme,
    cornerStyle = cornerStyle.name,
    cornerStrength = cornerStrength,
    motionPreset = motionPreset.name,
    outlineEnabled = outlineEnabled,
    outlineWidthDp = outlineWidthDp,
    outlineColorMode = outlineColorMode.name,
    outlineArgb = outlineArgb,
    outlineAlpha = outlineAlpha,
    backgroundPath = backgroundPath,
    backgroundAlpha = backgroundAlpha,
    surfaceAlpha = surfaceAlpha,
    backgroundBlur = backgroundBlur,
    liquidGlassControlsEnabled = liquidGlassControlsEnabled,
    liquidGlassNavbarEnabled = liquidGlassNavbarEnabled,
    liquidGlassBlurDp = liquidGlassBlurDp,
    liquidGlassLensHeightDp = liquidGlassLensHeightDp,
    liquidGlassLensAmountDp = liquidGlassLensAmountDp,
    liquidGlassSpectral = liquidGlassSpectral,
    liquidGlassTintAlpha = liquidGlassTintAlpha,
    liquidGlassShadowOn = liquidGlassShadowOn,
    liquidGlassGlassOn = liquidGlassGlassOn,
    liquidGlassContourEdge = liquidGlassContourEdge,
)

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, def: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(def)

private fun StoredTheme.toState() = ThemeState(
    seedArgb = seedArgb,
    darkTheme = darkTheme,
    cornerStyle = enumOrDefault(cornerStyle, CornerStyle.Rounded),
    cornerStrength = cornerStrength.coerceIn(0f, 1f),
    motionPreset = enumOrDefault(motionPreset, MotionPreset.Expressive),
    outlineEnabled = outlineEnabled,
    outlineWidthDp = outlineWidthDp.coerceIn(OUTLINE_WIDTH_MIN_DP, OUTLINE_WIDTH_MAX_DP),
    outlineColorMode = enumOrDefault(outlineColorMode, OutlineColorMode.Seed),
    outlineArgb = outlineArgb,
    outlineAlpha = outlineAlpha.coerceIn(OUTLINE_ALPHA_MIN, 1f),
    backgroundPath = backgroundPath,
    backgroundAlpha = backgroundAlpha.coerceIn(BACKGROUND_ALPHA_MIN, 1f),
    surfaceAlpha = surfaceAlpha.coerceIn(SURFACE_ALPHA_MIN, 1f),
    backgroundBlur = backgroundBlur.coerceIn(0f, BACKGROUND_BLUR_MAX_DP),
    liquidGlassControlsEnabled = liquidGlassControlsEnabled,
    liquidGlassNavbarEnabled = liquidGlassNavbarEnabled,
    liquidGlassBlurDp = liquidGlassBlurDp.coerceIn(0f, LIQUID_GLASS_BLUR_MAX_DP),
    liquidGlassLensHeightDp = liquidGlassLensHeightDp.coerceIn(0f, LIQUID_GLASS_LENS_HEIGHT_MAX_DP),
    liquidGlassLensAmountDp = liquidGlassLensAmountDp.coerceIn(0f, LIQUID_GLASS_LENS_AMOUNT_MAX_DP),
    liquidGlassSpectral = liquidGlassSpectral.coerceIn(0f, LIQUID_GLASS_SPECTRAL_MAX),
    liquidGlassTintAlpha = liquidGlassTintAlpha.coerceIn(LIQUID_GLASS_TINT_ALPHA_MIN, 1f),
    liquidGlassShadowOn = liquidGlassShadowOn,
    liquidGlassGlassOn = liquidGlassGlassOn,
    liquidGlassContourEdge = liquidGlassContourEdge,
)

object DesktopThemeStore {

    private const val KEY_PAYLOAD = "theme_payload"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: Preferences by lazy {
        Preferences.userRoot().node("com/pencil/chuangyin/pro")
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ThemeState> = _state.asStateFlow()

    val current: ThemeState get() = _state.value

    fun update(transform: (ThemeState) -> ThemeState) {
        val next = transform(_state.value)
        _state.value = next
        save(next)
    }

    private fun load(): ThemeState {
        val raw = runCatching { prefs.get(KEY_PAYLOAD, null) }.getOrNull()
        if (raw.isNullOrBlank()) return ThemeState.Default
        return runCatching {
            json.decodeFromString(StoredTheme.serializer(), raw).toState()
        }.getOrDefault(ThemeState.Default)
    }

    private fun save(state: ThemeState) {
        runCatching {
            prefs.put(KEY_PAYLOAD, json.encodeToString(StoredTheme.serializer(), state.toStored()))
            prefs.flush()
        }
    }
}
