package com.pencil.chuangyin.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.FolderOpen
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
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.ui.components.AppAlertDialog
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.LiquidGlassLayerScope
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.printer.PAPER_WIDTH_MM_MAX
import com.pencil.chuangyin.printer.PAPER_WIDTH_MM_MIN
import com.pencil.chuangyin.resources.R as ResR

/**
 * 「打印设置」独立页面：
 * - 热敏纸宽度（打印头像素换算依据）
 * - 自动连接开关与完整行为说明
 * - 产物输出目录（SAF 授权 / 目录名）
 */
@Composable
fun PrintSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    PrintSettingsScreen(
        paperWidthMm = state.paperWidthMm,
        onPaperWidthChange = viewModel::setPaperWidthMm,
        autoConnectEnabled = state.autoConnectEnabled,
        onAutoConnectChange = viewModel::setAutoConnectEnabled,
        outputDirName = state.outputDirName,
        outputTreeName = state.outputTreeName,
        onOutputDirNameChange = viewModel::setOutputDirName,
        onSetOutputTree = viewModel::setOutputTree,
        onClearOutputTree = viewModel::clearOutputTree,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSettingsScreen(
    paperWidthMm: Int,
    onPaperWidthChange: (Int) -> Unit,
    autoConnectEnabled: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    outputDirName: String,
    outputTreeName: String,
    onOutputDirNameChange: (String) -> Unit,
    onSetOutputTree: (String) -> Unit,
    onClearOutputTree: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassActive = rememberLiquidGlassActive()
    var showOutputDialog by remember { mutableStateOf(false) }
    val pickOutputTree = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) onSetOutputTree(uri.toString())
    }
    val outputDesc = if (outputTreeName.isNotBlank()) {
        outputTreeName
    } else {
        stringResource(R.string.settings_menu_output_default, outputDirName)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.settings_print_title)) },
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
                label = stringResource(R.string.settings_group_paper),
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
                label = stringResource(R.string.settings_group_connection),
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
            SettingsGroup(
                label = stringResource(R.string.settings_group_output_path),
                visible = appeared,
                delayMillis = 140,
            ) {
                SettingsEntry(
                    icon = Icons.Filled.FolderOpen,
                    title = stringResource(R.string.settings_menu_output),
                    desc = stringResource(R.string.settings_menu_output_desc, outputDesc),
                    onClick = { showOutputDialog = true },
                )
            }
        }
    }

    if (showOutputDialog) {
        OutputPathDialog(
            currentName = outputTreeName,
            dirName = outputDirName,
            onDirNameChange = onOutputDirNameChange,
            onPick = { pickOutputTree.launch(null) },
            onClear = {
                onClearOutputTree()
                showOutputDialog = false
            },
            onDismiss = { showOutputDialog = false },
        )
    }
}

@Composable
private fun OutputPathDialog(
    currentName: String,
    dirName: String,
    onDirNameChange: (String) -> Unit,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 目录名写入 DataStore 是异步的，输入框若直接绑 dirName 会逐字延迟/吞字，
    // 因此本地维护一份输入值，每次输入即同步回写设置。
    var nameInput by remember { mutableStateOf(dirName) }
    LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
        AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { AppText(text = stringResource(R.string.settings_output_dialog_title)) },
            text = {
                Column {
                    AppText(
                        text = stringResource(R.string.settings_output_dialog_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
                    // 目录名决定未授权 SAF 时的公共目录（Pictures/<目录名>/…）。
                    AppTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            onDirNameChange(it)
                        },
                        label = stringResource(R.string.settings_output_dialog_name_label),
                        placeholder = com.pencil.chuangyin.core.ui.data.OUTPUT_DIR_DEFAULT,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
                    AppText(
                        text = if (currentName.isBlank()) {
                            stringResource(R.string.settings_output_dialog_none)
                        } else {
                            stringResource(R.string.settings_output_dialog_current, currentName)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                AppTextButton(text = stringResource(R.string.settings_output_dialog_pick), onClick = onPick)
            },
            dismissButton = {
                Row {
                    if (currentName.isNotBlank()) {
                        AppTextButton(text = stringResource(R.string.settings_output_dialog_clear), onClick = onClear)
                    }
                    AppTextButton(text = stringResource(ResR.string.action_cancel), onClick = onDismiss)
                }
            },
            // 产物输出目录弹窗保持实心卡片，不做液态玻璃。
            glass = false,
        )
    }
}
