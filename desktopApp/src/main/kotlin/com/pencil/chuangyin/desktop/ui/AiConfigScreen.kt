package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.glass.GlassPanel
import com.pencil.chuangyin.desktop.support.AiConfigForm
import com.pencil.chuangyin.desktop.support.AiConfigStore
import com.pencil.chuangyin.desktop.support.AiModelConfig
import com.pencil.chuangyin.desktop.support.AiTestResult
import com.pencil.chuangyin.desktop.support.ApiProtocol
import kotlinx.coroutines.launch

/** 正在编辑的模型表单；[isNew] 决定弹窗标题与保存语义。 */
private data class AiConfigDialog(
    val isNew: Boolean,
    val form: AiConfigForm,
)

/** 连通性检测状态。 */
private enum class AiConfigTestStatus { Idle, Testing, Success, Failure }

/** 表单区域最大高度，超出后可滚动，避免弹窗被裁切（对齐安卓端 FORM_MAX_HEIGHT）。 */
private val FORM_MAX_HEIGHT = 420.dp

/**
 * AI 配置设置 —— 1:1 对齐安卓端 feature/ai/ui/AiConfigScreen.kt：
 * 说明文案 → 「已配置模型」列表（空态 / 模型卡 + 设为当前·编辑·删除）→ 「添加模型」→ 表单弹窗。
 */
@Composable
fun AiConfigPage(onBack: () -> Unit) {
    val storeState by AiConfigStore.state.collectAsState()
    val scope = rememberCoroutineScope()

    var dialog by remember { mutableStateOf<AiConfigDialog?>(null) }
    var testStatus by remember { mutableStateOf(AiConfigTestStatus.Idle) }
    var testDetail by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<AiModelConfig?>(null) }

    fun closeDialog() {
        dialog = null
        testStatus = AiConfigTestStatus.Idle
        testDetail = null
    }

    fun openCreate() {
        dialog = AiConfigDialog(isNew = true, form = AiConfigForm())
        testStatus = AiConfigTestStatus.Idle
        testDetail = null
    }

    fun openEdit(model: AiModelConfig) {
        dialog = AiConfigDialog(isNew = false, form = AiConfigForm.of(model))
        testStatus = AiConfigTestStatus.Idle
        testDetail = null
    }

    SettingsPage(title = "AI 配置设置", onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "支持 OpenAI 兼容、Anthropic Claude 与 Google Gemini 三种协议。" +
                "选择协议后填写服务链接、模型名称与密钥即可接入，可保存多个模型并随时切换。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "已配置模型",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        if (storeState.isEmpty) {
            Text(
                text = "还没有配置模型，先添加一个吧",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            storeState.models.forEach { model ->
                AiConfigModelCard(
                    model = model,
                    active = model.id == storeState.activeModelId,
                    onEdit = { openEdit(model) },
                    onSetActive = { AiConfigStore.setActive(model.id) },
                    onDelete = { pendingDelete = model },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        AppPrimaryButton(
            text = "添加模型",
            onClick = { openCreate() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }

    dialog?.let { current ->
        AiConfigFormDialog(
            dialog = current,
            testStatus = testStatus,
            testDetail = testDetail,
            onDismiss = { closeDialog() },
            onTest = { form ->
                val config = form.toModel()
                if (!config.isUsable) {
                    testStatus = AiConfigTestStatus.Failure
                    testDetail = null
                } else {
                    testStatus = AiConfigTestStatus.Testing
                    testDetail = null
                    scope.launch {
                        when (val result = AiConfigStore.testConnection(config)) {
                            is AiTestResult.Success -> {
                                testStatus = AiConfigTestStatus.Success
                                testDetail = null
                            }

                            is AiTestResult.Failure -> {
                                testStatus = AiConfigTestStatus.Failure
                                testDetail = result.detail
                            }
                        }
                    }
                }
            },
            onSave = { form ->
                if (form.isComplete) {
                    AiConfigStore.upsert(form.toModel())
                    closeDialog()
                }
            },
        )
    }

    pendingDelete?.let { model ->
        AppLiquidDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除模型配置",
            message = "确定要删除「${model.displayName.ifBlank { model.modelName }}」吗？",
            confirmLabel = "删除",
            onConfirm = {
                AiConfigStore.remove(model.id)
                pendingDelete = null
            },
            dismissLabel = "取消",
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
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = 0f,
        fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.displayName.ifBlank { model.modelName },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (active) {
                    Text(
                        text = "当前使用",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${model.protocol.shortName} · ${model.modelName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = model.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!active) {
                    TextButton(onClick = onSetActive) { Text("设为当前") }
                }
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("删除")
                }
            }
        }
    }
}

/**
 * 新增/编辑模型弹窗：四个字段 + 必填校验 + 连通性检测。
 *
 * 四个输入框的内容由弹窗本地 remember 同步持有，输入即刻生效，
 * 保存 / 检测时把当前表单整体传出（与安卓端同一套做法）。
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
    // 各协议默认地址集合：切换协议时若仍是默认地址（或为空），自动替换为新协议默认地址。
    val protocolDefaults = remember { ApiProtocol.entries.map { it.defaultBaseUrl }.toSet() }
    val testing = testStatus == AiConfigTestStatus.Testing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (dialog.isNew) "新增模型" else "编辑模型",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = FORM_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                FieldLabel("接口协议")
                AppSegmentedRow(
                    options = ApiProtocol.entries.map { it.shortName },
                    selectedIndex = form.protocol.ordinal,
                    onSelected = { index ->
                        val protocol = ApiProtocol.entries[index]
                        form = form.copy(
                            protocol = protocol,
                            baseUrl = if (form.baseUrl.isBlank() || form.baseUrl.trim() in protocolDefaults) {
                                protocol.defaultBaseUrl
                            } else {
                                form.baseUrl
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "选错协议会直接连不上，请与密钥所属服务商保持一致",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FieldLabel("服务链接")
                AppTextField(
                    value = form.baseUrl,
                    onValueChange = { value -> form = form.copy(baseUrl = value) },
                    placeholder = "https://api.example.com/v1",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                FieldLabel("模型名称")
                AppTextField(
                    value = form.modelName,
                    onValueChange = { value -> form = form.copy(modelName = value) },
                    placeholder = "gpt-4o-mini",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                FieldLabel("模型显示名称")
                AppTextField(
                    value = form.displayName,
                    onValueChange = { value -> form = form.copy(displayName = value) },
                    placeholder = "我的模型（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                FieldLabel("密钥")
                AppTextField(
                    value = form.apiKey,
                    onValueChange = { value -> form = form.copy(apiKey = value) },
                    placeholder = "sk-…",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                AiConfigTestHint(testStatus = testStatus, testDetail = testDetail)
                Spacer(Modifier.height(8.dp))
                AppSecondaryButton(
                    text = if (testing) "正在测试连接…" else "测试连接",
                    onClick = { onTest(form) },
                    enabled = form.isComplete && !testing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(form) },
                enabled = form.isComplete,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
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

        AiConfigTestStatus.Testing -> Text(
            text = "正在测试连接…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )

        AiConfigTestStatus.Success -> Text(
            text = "连接成功，可以正常使用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )

        AiConfigTestStatus.Failure -> Text(
            text = testDetail?.takeIf { it.isNotBlank() }
                ?.let { "连接失败：$it" }
                ?: "「服务链接」与「模型名称」为必填项",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    }
}
