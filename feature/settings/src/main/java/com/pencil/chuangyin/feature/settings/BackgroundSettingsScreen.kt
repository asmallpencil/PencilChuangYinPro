package com.pencil.chuangyin.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.theme.BACKGROUND_ALPHA_MIN
import com.pencil.chuangyin.core.theme.BACKGROUND_BLUR_MAX_DP
import com.pencil.chuangyin.core.theme.PARTICLE_SPEED_MAX
import com.pencil.chuangyin.core.theme.PARTICLE_SPEED_MIN
import com.pencil.chuangyin.core.theme.ParticleType
import com.pencil.chuangyin.core.theme.SURFACE_ALPHA_MIN
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.resources.R as ResR

/**
 * 「背景与装饰」独立页面：
 * - 自定义背景图与透明度/模糊；
 * - 全局粒子飘落装饰。
 */
@Composable
fun BackgroundSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackgroundSettingsScreen(
        state = state,
        onBack = onBack,
        onBackgroundUriChange = viewModel::setBackgroundUri,
        onBackgroundAlphaChange = viewModel::setBackgroundAlpha,
        onSurfaceAlphaChange = viewModel::setSurfaceAlpha,
        onBlurChange = viewModel::setBackgroundBlur,
        onSnowfallChange = viewModel::setSnowfallEnabled,
        onParticleTypeSelected = viewModel::setParticleType,
        onParticleSpeedChange = viewModel::setParticleSpeed,
        modifier = modifier,
    )
}

@Composable
fun BackgroundSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onBackgroundUriChange: (String?) -> Unit,
    onBackgroundAlphaChange: (Float) -> Unit,
    onSurfaceAlphaChange: (Float) -> Unit,
    onBlurChange: (Float) -> Unit,
    onSnowfallChange: (Boolean) -> Unit,
    onParticleTypeSelected: (ParticleType) -> Unit,
    onParticleSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = state.themeState
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    SettingsSubPage(
        title = stringResource(R.string.settings_menu_background),
        onBack = onBack,
        modifier = modifier,
    ) {
        SettingsGroup(
            label = stringResource(R.string.settings_label_background),
            visible = appeared,
            delayMillis = 0,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                    vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
                ),
            ) {
                BackgroundPicker(
                    backgroundUri = theme.backgroundUri,
                    onUriChange = onBackgroundUriChange,
                )
                SectionLabel(stringResource(R.string.settings_background_opacity))
                AppSlider(
                    value = theme.backgroundAlpha,
                    onValueChange = onBackgroundAlphaChange,
                    enabled = theme.backgroundUri != null,
                    valueRange = BACKGROUND_ALPHA_MIN..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLabel(stringResource(R.string.settings_surface_opacity))
                AppSlider(
                    value = theme.surfaceAlpha,
                    onValueChange = onSurfaceAlphaChange,
                    enabled = theme.backgroundUri != null,
                    valueRange = SURFACE_ALPHA_MIN..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLabel(stringResource(R.string.settings_background_blur))
                AppSlider(
                    value = theme.backgroundBlur,
                    onValueChange = onBlurChange,
                    enabled = theme.backgroundUri != null,
                    valueRange = 0f..BACKGROUND_BLUR_MAX_DP,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SettingsGroup(
            label = stringResource(R.string.settings_group_decor),
            visible = appeared,
            delayMillis = 70,
        ) {
            SettingSwitchRow(
                icon = Icons.Filled.Air,
                title = stringResource(R.string.settings_label_snowfall),
                desc = stringResource(R.string.settings_snowfall_desc),
                checked = theme.snowfallEnabled,
                onCheckedChange = onSnowfallChange,
            )
            Column(
                modifier = Modifier.padding(
                    horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                    vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
                ),
            ) {
                SectionLabel(stringResource(R.string.settings_label_particle_type))
                AppSegmentedRow(
                    options = ParticleType.entries.map { stringResource(particleTypeLabel(it)) },
                    selectedIndex = ParticleType.entries.indexOf(theme.particleType),
                    onSelect = { onParticleTypeSelected(ParticleType.entries[it]) },
                    enabled = theme.snowfallEnabled,
                )
                SectionLabel(stringResource(R.string.settings_label_particle_speed))
                AppSlider(
                    value = theme.particleSpeed,
                    onValueChange = onParticleSpeedChange,
                    enabled = theme.snowfallEnabled,
                    valueRange = PARTICLE_SPEED_MIN..PARTICLE_SPEED_MAX,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 系统相册选图（Photo Picker，免存储权限）+ 清除按钮。 */
@Composable
private fun BackgroundPicker(
    backgroundUri: String?,
    onUriChange: (String?) -> Unit,
) {
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onUriChange(uri.toString())
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppOutlinedButton(
            text = stringResource(R.string.settings_background_pick),
            onClick = {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
        if (backgroundUri != null) {
            Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            AppTextButton(
                text = stringResource(R.string.settings_background_clear),
                onClick = { onUriChange(null) },
                contentColor = MaterialTheme.colorScheme.error,
            )
        }
    }
    Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
}

private fun particleTypeLabel(type: ParticleType): Int = when (type) {
    ParticleType.Snow -> R.string.particle_snow
    ParticleType.Rain -> R.string.particle_rain
    ParticleType.Leaf -> R.string.particle_leaf
    ParticleType.Petal -> R.string.particle_petal
}
