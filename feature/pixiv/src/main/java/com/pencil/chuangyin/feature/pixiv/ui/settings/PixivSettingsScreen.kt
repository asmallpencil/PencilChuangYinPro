package com.pencil.chuangyin.feature.pixiv.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppSwitch
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.feature.pixiv.data.local.PixivPrefsStore
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_ILLUST
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_MANGA
import com.pencil.chuangyin.feature.pixiv.ui.common.MEDIA_NOVEL
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivSubScaffold
import com.pencil.chuangyin.resources.R as ResR
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PixivSettingsViewModel @Inject constructor(
    private val prefsStore: PixivPrefsStore,
) : ViewModel() {

    val defaultMediaMode: StateFlow<String> = prefsStore.defaultMediaMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, PixivPrefsStore.DEFAULT_MEDIA_MODE)

    val historyEnabled: StateFlow<Boolean> = prefsStore.historyEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val hiResImages: StateFlow<Boolean> = prefsStore.hiResImages
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val r18FilterEnabled: StateFlow<Boolean> = prefsStore.r18FilterEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setDefaultMediaMode(mode: String) {
        viewModelScope.launch { prefsStore.setDefaultMediaMode(mode) }
    }

    fun setHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsStore.setHistoryEnabled(enabled) }
    }

    fun setHiResImages(enabled: Boolean) {
        viewModelScope.launch { prefsStore.setHiResImages(enabled) }
    }

    fun setR18FilterEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsStore.setR18FilterEnabled(enabled) }
    }
}

@Composable
fun PixivSettingsRoute(
    onBack: () -> Unit,
    onOpenBlockedTags: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PixivSettingsViewModel = hiltViewModel(),
) {
    val defaultMode by viewModel.defaultMediaMode.collectAsStateWithLifecycle()
    val historyEnabled by viewModel.historyEnabled.collectAsStateWithLifecycle()
    val hiRes by viewModel.hiResImages.collectAsStateWithLifecycle()
    val r18Filter by viewModel.r18FilterEnabled.collectAsStateWithLifecycle()

    PixivSubScaffold(
        modifier = modifier,
        title = stringResource(ResR.string.pixiv_settings_title),
        onBack = onBack,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsSectionTitle(stringResource(ResR.string.pixiv_settings_default_mode_title))

            AppSegmentedRow(
                options = listOf(
                    stringResource(ResR.string.pixiv_mode_illust),
                    stringResource(ResR.string.pixiv_mode_manga),
                    stringResource(ResR.string.pixiv_mode_novel),
                ),
                selectedIndex = when (defaultMode) {
                    MEDIA_MANGA -> 1
                    MEDIA_NOVEL -> 2
                    else -> 0
                },
                onSelect = { index ->
                    viewModel.setDefaultMediaMode(
                        when (index) {
                            2 -> MEDIA_NOVEL
                            1 -> MEDIA_MANGA
                            else -> MEDIA_ILLUST
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l)),
            )

            SettingsToggleRow(
                title = stringResource(ResR.string.pixiv_settings_history_label),
                desc = stringResource(ResR.string.pixiv_settings_history_desc),
                checked = historyEnabled,
                onCheckedChange = viewModel::setHistoryEnabled,
            )
            SettingsToggleRow(
                title = stringResource(ResR.string.pixiv_settings_hi_res_label),
                desc = stringResource(ResR.string.pixiv_settings_hi_res_desc),
                checked = hiRes,
                onCheckedChange = viewModel::setHiResImages,
            )
            SettingsToggleRow(
                title = stringResource(ResR.string.pixiv_settings_r18_label),
                desc = stringResource(ResR.string.pixiv_settings_r18_desc),
                checked = r18Filter,
                onCheckedChange = viewModel::setR18FilterEnabled,
            )

            AppText(
                text = stringResource(ResR.string.pixiv_settings_related),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = dimensionResource(ResR.dimen.dimen_global_padding_l),
                    top = dimensionResource(ResR.dimen.dimen_global_padding_l),
                    bottom = dimensionResource(ResR.dimen.dimen_global_padding_s),
                ),
            )
            NavigationRow(onClick = onOpenBlockedTags)
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    AppText(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = dimensionResource(ResR.dimen.dimen_global_padding_l),
            top = dimensionResource(ResR.dimen.dimen_global_padding_l),
            bottom = dimensionResource(ResR.dimen.dimen_global_padding_s),
        ),
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(text = title, style = MaterialTheme.typography.bodyLarge)
            AppText(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NavigationRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Block,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppText(
            text = stringResource(ResR.string.pixiv_drawer_blocked_tags),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m)),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun PixivSettingsPreview() {
    MaterialTheme {
        Column {
            AppSegmentedRow(
                options = listOf("插画", "漫画", "小说"),
                selectedIndex = 0,
                onSelect = {},
            )
        }
    }
}