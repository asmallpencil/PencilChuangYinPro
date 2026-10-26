package com.pencil.chuangyin.feature.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.agent.config.AiModelConfig
import com.pencil.chuangyin.core.ui.components.AppAlertDialog
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSecondaryButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.pseudoGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.feature.ai.R
import com.pencil.chuangyin.resources.R as ResR

@Composable
fun AiConfigRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AiConfigViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    AiConfigScreen(
        state = state,
        onBack = onBack,
        onCreate = viewModel::openCreate,
        onEdit = viewModel::openEdit,
        onSetActive = viewModel::setActive,
        onRequestDelete = viewModel::requestDelete,
        onDialogDismiss = viewModel::dismissDialog,
        onSave = viewModel::save,
        onTest = viewModel::testConnection,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigScreen(
    state: AiConfigUiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (AiModelConfig) -> Unit,
    onSetActive: (String) -> Unit,
    onRequestDelete: (AiModelConfig) -> Unit,
    onDialogDismiss: () -> Unit,
    onSave: (AiConfigForm) -> Unit,
    onTest: (AiConfigForm) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassActive = rememberLiquidGlassActive()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.ai_config_title)) },
                navigationIcon = {
                    AppIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(ResR.string.label_back),
                    )
                },
                modifier = Modifier.nativeGlassSurface(
                    shape = RoundedCornerShape(0.dp),
                    enabled = glassActive,
                    fallbackColor = MaterialTheme.colorScheme.surface,
                ),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            AppText(
                text = stringResource(R.string.ai_config_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
            AppText(
                text = stringResource(R.string.ai_config_models_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            if (state.isEmpty) {
                AppText(
                    text = stringResource(R.string.ai_config_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.models.forEach { model ->
                    AiConfigModelCard(
                        model = model,
                        active = model.id == state.activeModelId,
                        onEdit = { onEdit(model) },
                        onSetActive = { onSetActive(model.id) },
                        onDelete = { onRequestDelete(model) },
                    )
                    Spacer(Modifier.height(dimensionResource(R.dimen.ai_model_row_gap)))
                }
            }
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
            AppPrimaryButton(
                text = stringResource(R.string.ai_config_add),
                onClick = onCreate,
                leadingIcon = Icons.Filled.Add,
            )
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        }
    }

    state.dialog?.let { dialog ->
        AiConfigFormDialog(
            dialog = dialog,
            testStatus = state.testStatus,
            testDetail = state.testDetail,
            onDismiss = onDialogDismiss,
            onTest = onTest,
            onSave = onSave,
        )
    }

    state.pendingDelete?.let { model ->
        AppAlertDialog(
            onDismissRequest = onCancelDelete,
            title = { AppText(text = stringResource(R.string.ai_config_delete_title)) },
            text = {
                AppText(
                    text = stringResource(
                        R.string.ai_config_delete_message,
                        model.displayName.ifBlank { model.modelName },
                    ),
                )
            },
            confirmButton = {
                AppTextButton(
                    text = stringResource(ResR.string.action_delete),
                    onClick = onConfirmDelete,
                    contentColor = MaterialTheme.colorScheme.error,
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(ResR.string.action_cancel),
                    onClick = onCancelDelete,
                )
            },
        )
    }
}

/** 单份模型配置卡片：展示信息 + 设为当前 / 编辑 / 删除。 */
@Composable
private fun AiConfigModelCard(
    model: AiModelConfig,
    active: Boolean,
    onEdit: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 位于纵向滚动容器内、随内容移动：用伪玻璃同帧自绘，避免真玻璃采样滞后。
            .pseudoGlassSurface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                text = model.displayName.ifBlank { model.modelName },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (active) {
                AppText(
                    text = stringResource(R.string.ai_config_active),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        AppText(
            text = model.modelName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppText(
            text = model.baseUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s))) {
            if (!active) {
                AppTextButton(
                    text = stringResource(R.string.ai_config_set_active),
                    onClick = onSetActive,
                )
            }
            AppTextButton(
                text = stringResource(R.string.ai_config_edit),
                onClick = onEdit,
            )
            AppTextButton(
                text = stringResource(R.string.ai_config_delete),
                onClick = onDelete,
                contentColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * 新增/编辑模型弹窗：四个字段 + 必填校验 + 连通性检测。
 *
 * 四个输入框的内容由弹窗本地 [remember] 同步持有，输入即刻生效、不做异步回绕，
 * 避免受控输入框被上一帧旧值回写导致字符顺序错乱；保存 / 检测时把当前表单整体传出。
 */
@Composable
private fun AiConfigFormDialog(
    dialog: AiConfigDialog,
    testStatus: AiConfigTestStatus,
    testDetail: String?,
    onDismiss: () -> Unit,
    onTest: (AiConfigForm) -> Unit,
    onSave: (AiConfigForm) -> Unit,
) {
    // 以 dialog 为 key：打开新增/编辑时重置，检测状态变化不会重置输入内容。
    var form by remember(dialog) { mutableStateOf(dialog.form) }
    val testing = testStatus == AiConfigTestStatus.Testing
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppText(
                text = stringResource(
                    if (dialog.isNew) R.string.ai_config_dialog_new else R.string.ai_config_dialog_edit,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = FORM_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                AppTextField(
                    value = form.baseUrl,
                    onValueChange = { value -> form = form.copy(baseUrl = value) },
                    label = stringResource(R.string.ai_config_field_base_url),
                    placeholder = stringResource(R.string.ai_config_field_base_url_hint),
                )
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppTextField(
                    value = form.modelName,
                    onValueChange = { value -> form = form.copy(modelName = value) },
                    label = stringResource(R.string.ai_config_field_model),
                    placeholder = stringResource(R.string.ai_config_field_model_hint),
                )
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppTextField(
                    value = form.displayName,
                    onValueChange = { value -> form = form.copy(displayName = value) },
                    label = stringResource(R.string.ai_config_field_display),
                    placeholder = stringResource(R.string.ai_config_field_display_hint),
                )
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppTextField(
                    value = form.apiKey,
                    onValueChange = { value -> form = form.copy(apiKey = value) },
                    label = stringResource(R.string.ai_config_field_api_key),
                    placeholder = stringResource(R.string.ai_config_field_api_key_hint),
                )
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AiConfigTestHint(testStatus = testStatus, testDetail = testDetail)
                Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppSecondaryButton(
                    text = stringResource(
                        if (testing) R.string.ai_config_testing else R.string.ai_config_test,
                    ),
                    onClick = { onTest(form) },
                    enabled = form.isComplete && !testing,
                )
            }
        },
        confirmButton = {
            AppTextButton(
                text = stringResource(ResR.string.action_save),
                onClick = { onSave(form) },
                enabled = form.isComplete,
            )
        },
        dismissButton = {
            AppTextButton(
                text = stringResource(ResR.string.action_cancel),
                onClick = onDismiss,
            )
        },
    )
}

/** 检测结果提示：未检测不占位；失败时区分「字段不全」与「服务端错误」。 */
@Composable
private fun AiConfigTestHint(
    testStatus: AiConfigTestStatus,
    testDetail: String?,
    modifier: Modifier = Modifier,
) {
    when (testStatus) {
        AiConfigTestStatus.Idle -> Unit
        AiConfigTestStatus.Testing -> AppText(
            text = stringResource(R.string.ai_config_testing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )

        AiConfigTestStatus.Success -> AppText(
            text = stringResource(R.string.ai_config_test_success),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )

        AiConfigTestStatus.Failure -> AppText(
            text = testDetail?.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.ai_config_test_failed, it) }
                ?: stringResource(R.string.ai_config_required_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    }
}

/** 表单区域最大高度，超出后可滚动，避免小屏弹窗被裁切。 */
private val FORM_MAX_HEIGHT = 420.dp

@Preview(showBackground = true)
@Composable
private fun AiConfigScreenPreview() {
    AiConfigScreen(
        state = AiConfigUiState(
            models = listOf(
                AiModelConfig(
                    id = "1",
                    displayName = "我的模型",
                    baseUrl = "https://api.example.com/v1",
                    modelName = "gpt-4o-mini",
                    apiKey = "sk-xxx",
                ),
                AiModelConfig(
                    id = "2",
                    baseUrl = "https://api.deepseek.com/v1",
                    modelName = "deepseek-chat",
                ),
            ),
            activeModelId = "1",
        ),
        onBack = {},
        onCreate = {},
        onEdit = {},
        onSetActive = {},
        onRequestDelete = {},
        onDialogDismiss = {},
        onSave = {},
        onTest = {},
        onCancelDelete = {},
        onConfirmDelete = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun AiConfigFormDialogPreview() {
    AiConfigFormDialog(
        dialog = AiConfigDialog(
            isNew = true,
            form = AiConfigForm(baseUrl = "https://api.example.com/v1", modelName = "gpt-4o-mini"),
        ),
        testStatus = AiConfigTestStatus.Failure,
        testDetail = "401 Unauthorized",
        onDismiss = {},
        onTest = {},
        onSave = {},
    )
}
