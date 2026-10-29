package com.pencil.chuangyin.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.printer.PAPER_WIDTH_MM_MAX
import com.pencil.chuangyin.printer.PAPER_WIDTH_MM_MIN
import com.pencil.chuangyin.resources.R as ResR

/**
 * 「功能设定」独立页面：
 * - 热敏纸宽度（打印头像素换算依据）
 * - 自动连接开关与完整行为说明
 *
 * 主页只保留一个入口，避免这些功能项挤在设置主菜单里。
 */
@Composable
fun FunctionSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    FunctionSettingsScreen(
        paperWidthMm = state.paperWidthMm,
        onPaperWidthChange = viewModel::setPaperWidthMm,
        autoConnectEnabled = state.autoConnectEnabled,
        onAutoConnectChange = viewModel::setAutoConnectEnabled,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionSettingsScreen(
    paperWidthMm: Int,
    onPaperWidthChange: (Int) -> Unit,
    autoConnectEnabled: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassActive = rememberLiquidGlassActive()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.settings_size_title)) },
                navigationIcon = { BackIcon(onBack) },
                modifier = Modifier.nativeGlassSurface(
                    shape = RoundedCornerShape(0.dp),
                    enabled = glassActive,
                    fallbackColor = MaterialTheme.colorScheme.surface,
                ),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        var appeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appeared = true }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_xl)),
        ) {
            SettingsGroup(
                label = stringResource(R.string.settings_menu_size),
                visible = appeared,
                delayMillis = 0,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                        vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
                    ),
                ) {
                    AppText(
                        text = stringResource(R.string.settings_label_paper_width),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                    AppText(
                        text = stringResource(R.string.settings_paper_width_value, paperWidthMm),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    AppSlider(
                        value = paperWidthMm.toFloat(),
                        onValueChange = { onPaperWidthChange(it.toInt()) },
                        valueRange = PAPER_WIDTH_MM_MIN.toFloat()..PAPER_WIDTH_MM_MAX.toFloat(),
                        steps = PAPER_WIDTH_MM_MAX - PAPER_WIDTH_MM_MIN - 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppText(
                        text = stringResource(R.string.settings_paper_width_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingsGroup(
                label = stringResource(R.string.settings_menu_auto_connect),
                visible = appeared,
                delayMillis = 70,
            ) {
                SettingSwitchRow(
                    icon = Icons.Filled.Bluetooth,
                    title = stringResource(R.string.settings_menu_auto_connect),
                    desc = stringResource(R.string.settings_menu_auto_connect_desc),
                    checked = autoConnectEnabled,
                    onCheckedChange = onAutoConnectChange,
                )
                SettingsGroupNote(text = stringResource(R.string.settings_auto_connect_logic))
            }
        }
    }
}
