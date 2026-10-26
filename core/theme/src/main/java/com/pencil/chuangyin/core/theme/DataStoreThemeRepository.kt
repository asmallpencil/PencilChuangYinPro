package com.pencil.chuangyin.core.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * 基于 DataStore 的 [ThemeRepository] 实现：主题状态持久化，
 * 云端/本地/内存实现可后续按需替换，Feature 层不感知。
 *
 * 旧版形状 key（shapePreset: shape_sharp/shape_rounded/shape_extra_rounded）
 * 在首次读取状态流时一次性迁移到 cornerStyle/cornerStrength：
 * sharp→Square、rounded→Rounded(0.5)、extra_rounded→Rounded(1.0)，迁移后删除旧 key。
 */
@Singleton
class DataStoreThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ThemeRepository {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    private companion object {
        const val DATASTORE_NAME = "pencil_print_theme"
        val KEY_SEED = longPreferencesKey("seedArgb")
        val KEY_DARK = booleanPreferencesKey("darkTheme")
        val KEY_CORNER_STYLE = stringPreferencesKey("cornerStyle")
        val KEY_CORNER_STRENGTH = floatPreferencesKey("cornerStrength")
        val KEY_MOTION = stringPreferencesKey("motionPreset")

        val KEY_OUTLINE_ENABLED = booleanPreferencesKey("outlineEnabled")
        val KEY_OUTLINE_WIDTH = floatPreferencesKey("outlineWidthDp")
        val KEY_OUTLINE_COLOR_MODE = stringPreferencesKey("outlineColorMode")
        val KEY_OUTLINE_ARGB = longPreferencesKey("outlineArgb")
        val KEY_OUTLINE_ALPHA = floatPreferencesKey("outlineAlpha")
        val KEY_SNOWFALL_ENABLED = booleanPreferencesKey("snowfallEnabled")
        val KEY_PARTICLE_TYPE = stringPreferencesKey("particleType")
        val KEY_PARTICLE_SPEED = floatPreferencesKey("particleSpeed")
        val KEY_BACKGROUND_URI = stringPreferencesKey("backgroundUri")
        val KEY_BACKGROUND_ALPHA = floatPreferencesKey("backgroundAlpha")
        val KEY_SURFACE_ALPHA = floatPreferencesKey("surfaceAlpha")
        val KEY_BACKGROUND_BLUR = floatPreferencesKey("backgroundBlur")
        val KEY_LIQUID_GLASS_RENDERER = stringPreferencesKey("liquidGlassRenderer")
        val KEY_LIQUID_GLASS_CONTROLS = booleanPreferencesKey("liquidGlassControlsEnabled")
        val KEY_LIQUID_GLASS_NAVBAR = booleanPreferencesKey("liquidGlassNavbarEnabled")
        val KEY_LG_BLUR = floatPreferencesKey("liquidGlassBlurDp")
        val KEY_LG_LENS_HEIGHT = floatPreferencesKey("liquidGlassLensHeightDp")
        val KEY_LG_LENS_AMOUNT = floatPreferencesKey("liquidGlassLensAmountDp")
        val KEY_LG_SPECTRAL = floatPreferencesKey("liquidGlassSpectral")
        val KEY_LG_TINT_ALPHA = floatPreferencesKey("liquidGlassTintAlpha")
        val KEY_LG_SHADOW = booleanPreferencesKey("liquidGlassShadowOn")
        val KEY_LG_GLASS = booleanPreferencesKey("liquidGlassGlassOn")
        val KEY_LG_CONTOUR = booleanPreferencesKey("liquidGlassContourEdge")

        val KEY_SHAPE_LEGACY = stringPreferencesKey("shapePreset")
        const val LEGACY_SHAPE_SHARP = "shape_sharp"
        const val LEGACY_SHAPE_EXTRA_ROUNDED = "shape_extra_rounded"
    }

    private val defaults = ThemeState.Default

    override val state: Flow<ThemeState> = flow {
        migrateLegacyShape()
        emitAll(
            context.dataStore.data.map { prefs ->
                ThemeState(
                    seedArgb = prefs[KEY_SEED] ?: defaults.seedArgb,
                    darkTheme = prefs[KEY_DARK] ?: defaults.darkTheme,
                    cornerStyle = CornerStyle.entries.firstOrNull { it.id == prefs[KEY_CORNER_STYLE] }
                        ?: defaults.cornerStyle,
                    cornerStrength = (prefs[KEY_CORNER_STRENGTH] ?: defaults.cornerStrength)
                        .coerceIn(0f, 1f),
                    motionPreset = MotionPreset.entries.firstOrNull { it.id == prefs[KEY_MOTION] }
                        ?: defaults.motionPreset,
                    outlineEnabled = prefs[KEY_OUTLINE_ENABLED] ?: defaults.outlineEnabled,
                    outlineWidthDp = (prefs[KEY_OUTLINE_WIDTH] ?: defaults.outlineWidthDp)
                        .coerceIn(OUTLINE_WIDTH_MIN_DP, OUTLINE_WIDTH_MAX_DP),
                    outlineColorMode = OutlineColorMode.entries
                        .firstOrNull { it.id == prefs[KEY_OUTLINE_COLOR_MODE] }
                        ?: defaults.outlineColorMode,
                    outlineArgb = prefs[KEY_OUTLINE_ARGB] ?: defaults.outlineArgb,
                    outlineAlpha = (prefs[KEY_OUTLINE_ALPHA] ?: defaults.outlineAlpha)
                        .coerceIn(OUTLINE_ALPHA_MIN, 1f),
                    snowfallEnabled = prefs[KEY_SNOWFALL_ENABLED] ?: defaults.snowfallEnabled,
                    particleType = ParticleType.entries
                        .firstOrNull { it.id == prefs[KEY_PARTICLE_TYPE] }
                        ?: defaults.particleType,
                    particleSpeed = (prefs[KEY_PARTICLE_SPEED] ?: defaults.particleSpeed)
                        .coerceIn(PARTICLE_SPEED_MIN, PARTICLE_SPEED_MAX),
                    backgroundUri = prefs[KEY_BACKGROUND_URI],
                    backgroundAlpha = (prefs[KEY_BACKGROUND_ALPHA] ?: defaults.backgroundAlpha)
                        .coerceIn(BACKGROUND_ALPHA_MIN, 1f),
                    surfaceAlpha = (prefs[KEY_SURFACE_ALPHA] ?: defaults.surfaceAlpha)
                        .coerceIn(SURFACE_ALPHA_MIN, 1f),
                    backgroundBlur = (prefs[KEY_BACKGROUND_BLUR] ?: defaults.backgroundBlur)
                        .coerceIn(BACKGROUND_BLUR_DEFAULT, BACKGROUND_BLUR_MAX_DP),
                    liquidGlassRenderer = LiquidGlassRenderer.entries
                        .firstOrNull { it.id == prefs[KEY_LIQUID_GLASS_RENDERER] }
                        ?: defaults.liquidGlassRenderer,
                    liquidGlassControlsEnabled = prefs[KEY_LIQUID_GLASS_CONTROLS]
                        ?: defaults.liquidGlassControlsEnabled,
                    liquidGlassNavbarEnabled = prefs[KEY_LIQUID_GLASS_NAVBAR]
                        ?: defaults.liquidGlassNavbarEnabled,
                    liquidGlassBlurDp = (prefs[KEY_LG_BLUR] ?: defaults.liquidGlassBlurDp)
                        .coerceIn(0f, LIQUID_GLASS_BLUR_MAX_DP),
                    liquidGlassLensHeightDp = (prefs[KEY_LG_LENS_HEIGHT] ?: defaults.liquidGlassLensHeightDp)
                        .coerceIn(0f, LIQUID_GLASS_LENS_HEIGHT_MAX_DP),
                    liquidGlassLensAmountDp = (prefs[KEY_LG_LENS_AMOUNT] ?: defaults.liquidGlassLensAmountDp)
                        .coerceIn(0f, LIQUID_GLASS_LENS_AMOUNT_MAX_DP),
                    liquidGlassSpectral = (prefs[KEY_LG_SPECTRAL] ?: defaults.liquidGlassSpectral)
                        .coerceIn(0f, LIQUID_GLASS_SPECTRAL_MAX),
                    liquidGlassTintAlpha = (prefs[KEY_LG_TINT_ALPHA] ?: defaults.liquidGlassTintAlpha)
                        .coerceIn(LIQUID_GLASS_TINT_ALPHA_MIN, 1f),
                    liquidGlassShadowOn = prefs[KEY_LG_SHADOW] ?: defaults.liquidGlassShadowOn,
                    liquidGlassGlassOn = prefs[KEY_LG_GLASS] ?: defaults.liquidGlassGlassOn,
                    liquidGlassContourEdge = prefs[KEY_LG_CONTOUR] ?: defaults.liquidGlassContourEdge,
                )
            },
        )
    }

    override suspend fun setSeedColor(argb: Long) = editState { set(KEY_SEED, argb) }

    override suspend fun setDarkTheme(enabled: Boolean) = editState { set(KEY_DARK, enabled) }

    override suspend fun setCornerStyle(style: CornerStyle) = editState { set(KEY_CORNER_STYLE, style.id) }

    override suspend fun setCornerStrength(strength: Float) =
        editState { set(KEY_CORNER_STRENGTH, strength.coerceIn(0f, 1f)) }

    override suspend fun setMotionPreset(preset: MotionPreset) = editState { set(KEY_MOTION, preset.id) }

    override suspend fun setOutlineEnabled(enabled: Boolean) =
        editState { set(KEY_OUTLINE_ENABLED, enabled) }

    override suspend fun setOutlineWidth(widthDp: Float) =
        editState { set(KEY_OUTLINE_WIDTH, widthDp.coerceIn(OUTLINE_WIDTH_MIN_DP, OUTLINE_WIDTH_MAX_DP)) }

    override suspend fun setOutlineColorMode(mode: OutlineColorMode) =
        editState { set(KEY_OUTLINE_COLOR_MODE, mode.id) }

    override suspend fun setOutlineArgb(argb: Long) = editState { set(KEY_OUTLINE_ARGB, argb) }

    override suspend fun setOutlineAlpha(alpha: Float) =
        editState { set(KEY_OUTLINE_ALPHA, alpha.coerceIn(OUTLINE_ALPHA_MIN, 1f)) }

    override suspend fun setSnowfallEnabled(enabled: Boolean) =
        editState { set(KEY_SNOWFALL_ENABLED, enabled) }

    override suspend fun setParticleType(type: ParticleType) =
        editState { set(KEY_PARTICLE_TYPE, type.id) }

    override suspend fun setParticleSpeed(speed: Float) =
        editState { set(KEY_PARTICLE_SPEED, speed.coerceIn(PARTICLE_SPEED_MIN, PARTICLE_SPEED_MAX)) }

    override suspend fun setBackgroundUri(uri: String?) = editState {
        if (uri == null) remove(KEY_BACKGROUND_URI) else set(KEY_BACKGROUND_URI, uri)
    }

    override suspend fun setBackgroundAlpha(alpha: Float) =
        editState { set(KEY_BACKGROUND_ALPHA, alpha.coerceIn(BACKGROUND_ALPHA_MIN, 1f)) }

    override suspend fun setSurfaceAlpha(alpha: Float) =
        editState { set(KEY_SURFACE_ALPHA, alpha.coerceIn(SURFACE_ALPHA_MIN, 1f)) }

    override suspend fun setBackgroundBlur(blurDp: Float) =
        editState { set(KEY_BACKGROUND_BLUR, blurDp.coerceIn(BACKGROUND_BLUR_DEFAULT, BACKGROUND_BLUR_MAX_DP)) }

    override suspend fun setLiquidGlassRenderer(renderer: LiquidGlassRenderer) =
        editState { set(KEY_LIQUID_GLASS_RENDERER, renderer.id) }

    override suspend fun setLiquidGlassControlsEnabled(enabled: Boolean) =
        editState { set(KEY_LIQUID_GLASS_CONTROLS, enabled) }

    override suspend fun setLiquidGlassNavbarEnabled(enabled: Boolean) =
        editState { set(KEY_LIQUID_GLASS_NAVBAR, enabled) }

    override suspend fun setLiquidGlassBlur(blurDp: Float) =
        editState { set(KEY_LG_BLUR, blurDp.coerceIn(0f, LIQUID_GLASS_BLUR_MAX_DP)) }

    override suspend fun setLiquidGlassLensHeight(lensHeightDp: Float) =
        editState { set(KEY_LG_LENS_HEIGHT, lensHeightDp.coerceIn(0f, LIQUID_GLASS_LENS_HEIGHT_MAX_DP)) }

    override suspend fun setLiquidGlassLensAmount(lensAmountDp: Float) =
        editState { set(KEY_LG_LENS_AMOUNT, lensAmountDp.coerceIn(0f, LIQUID_GLASS_LENS_AMOUNT_MAX_DP)) }

    override suspend fun setLiquidGlassSpectral(spectral: Float) =
        editState { set(KEY_LG_SPECTRAL, spectral.coerceIn(0f, LIQUID_GLASS_SPECTRAL_MAX)) }

    override suspend fun setLiquidGlassTintAlpha(alpha: Float) =
        editState { set(KEY_LG_TINT_ALPHA, alpha.coerceIn(LIQUID_GLASS_TINT_ALPHA_MIN, 1f)) }

    override suspend fun setLiquidGlassShadowOn(enabled: Boolean) =
        editState { set(KEY_LG_SHADOW, enabled) }

    override suspend fun setLiquidGlassGlassOn(enabled: Boolean) =
        editState { set(KEY_LG_GLASS, enabled) }

    override suspend fun setLiquidGlassContourEdge(enabled: Boolean) =
        editState { set(KEY_LG_CONTOUR, enabled) }

    /** 旧形状预设 → 新边角类型的一次性迁移；迁移完成后删除旧 key。 */
    private suspend fun migrateLegacyShape() {
        context.dataStore.edit { prefs ->
            val legacy = prefs[KEY_SHAPE_LEGACY] ?: return@edit
            if (prefs[KEY_CORNER_STYLE] == null) {
                when (legacy) {
                    LEGACY_SHAPE_SHARP -> {
                        prefs[KEY_CORNER_STYLE] = CornerStyle.Square.id
                    }
                    LEGACY_SHAPE_EXTRA_ROUNDED -> {
                        prefs[KEY_CORNER_STYLE] = CornerStyle.Rounded.id
                        prefs[KEY_CORNER_STRENGTH] = 1f
                    }
                    else -> {
                        prefs[KEY_CORNER_STYLE] = CornerStyle.Rounded.id
                        prefs[KEY_CORNER_STRENGTH] = 0.5f
                    }
                }
            }
            prefs.remove(KEY_SHAPE_LEGACY)
        }
    }

    private suspend fun editState(block: androidx.datastore.preferences.core.MutablePreferences.() -> Unit) {
        context.dataStore.edit(block)
    }
}
