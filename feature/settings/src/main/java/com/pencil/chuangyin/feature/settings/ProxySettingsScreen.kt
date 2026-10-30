package com.pencil.chuangyin.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.proxy.data.ProxyState
import com.pencil.chuangyin.core.proxy.engine.ProxyLogEntry
import com.pencil.chuangyin.core.proxy.engine.ProxyLogKind
import com.pencil.chuangyin.core.proxy.model.ClashConfig
import com.pencil.chuangyin.core.proxy.model.ProxyConfigEntry
import com.pencil.chuangyin.core.proxy.model.ProxyGroupType
import com.pencil.chuangyin.core.proxy.model.ProxyMode
import com.pencil.chuangyin.core.proxy.model.ProxyNode
import com.pencil.chuangyin.core.proxy.model.ProxyPreferences
import com.pencil.chuangyin.core.proxy.model.ProxyScope
import com.pencil.chuangyin.core.proxy.model.ProxySourceType
import com.pencil.chuangyin.core.proxy.route.Outbound
import com.pencil.chuangyin.core.proxy.route.RouteDecision
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.ui.components.AppAlertDialog
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppSwitch
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.GlassPanel
import com.pencil.chuangyin.core.ui.components.GlassTopAppBar
import com.pencil.chuangyin.core.ui.components.LiquidGlassLayerScope
import com.pencil.chuangyin.core.ui.motion.pressScale
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.resources.R as ResR
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 仪表盘日志最多展示条数：引擎自身保留了更多，界面只取最近的一段。 */
private const val MAX_VISIBLE_LOGS = 8

/** 仪表盘 / 配置页里警告文本最多展示条数（完整提示仍保存在配置对象中）。 */
private const val MAX_VISIBLE_WARNINGS = 4

/**
 * 代理页的全部用户操作。
 *
 * 用 [Stable] 数据类收敛回调，避免 Screen 签名膨胀到二十多个参数；实例在路由层
 * `remember(viewModel)` 创建一次，重组时不会失效。
 */
@Stable
class ProxySettingsActions(
    val setEnabled: (Boolean) -> Unit,
    val setScope: (ProxyScope) -> Unit,
    val setMode: (ProxyMode) -> Unit,
    val selectGroupNode: (String, String) -> Unit,
    val setGlobalSelection: (String) -> Unit,
    val measure: (String) -> Unit,
    val measureAll: () -> Unit,
    val clearLogs: () -> Unit,
    val importManual: (String, String?) -> Unit,
    val importLink: (String, String?) -> Unit,
    val importFile: (Uri) -> Unit,
    val importQr: (Uri) -> Unit,
    val setActiveConfig: (String) -> Unit,
    val renameConfig: (String, String) -> Unit,
    val removeConfig: (String) -> Unit,
    val refreshSubscription: (ProxyConfigEntry) -> Unit,
)

@Composable
fun ProxySettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProxySettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val latency by viewModel.latency.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        // 先消费再展示：旋转屏幕或进入后台再回来时不会重复弹同一条提示。
        viewModel.consumeMessage()
        snackbarHostState.showSnackbar(current.text)
    }

    val actions = remember(viewModel) {
        ProxySettingsActions(
            setEnabled = viewModel::setEnabled,
            setScope = viewModel::setScope,
            setMode = viewModel::setMode,
            selectGroupNode = viewModel::selectGroupNode,
            setGlobalSelection = viewModel::setGlobalSelection,
            measure = viewModel::measure,
            measureAll = viewModel::measureAll,
            clearLogs = viewModel::clearLogs,
            importManual = viewModel::importFromText,
            importLink = viewModel::importFromLink,
            importFile = viewModel::importFromFile,
            importQr = viewModel::importFromQrCode,
            setActiveConfig = viewModel::setActiveConfig,
            renameConfig = viewModel::renameConfig,
            removeConfig = viewModel::removeConfig,
            refreshSubscription = viewModel::refreshSubscription,
        )
    }

    // 仪表盘的「当前出口」预览随偏好 / 配置 / 延迟表变化重算。
    val preview = remember(state, latency) { viewModel.previewRoute() }

    ProxySettingsScreen(
        state = state,
        logs = logs,
        latency = latency,
        busy = busy,
        preview = preview,
        snackbarHostState = snackbarHostState,
        actions = actions,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    state: ProxyState,
    logs: List<ProxyLogEntry>,
    latency: Map<String, Long>,
    busy: Boolean,
    preview: RouteDecision,
    snackbarHostState: SnackbarHostState,
    actions: ProxySettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentTab = ProxyTab.entries[tabIndex.coerceIn(0, ProxyTab.entries.lastIndex)]

    var showLinkImport by rememberSaveable { mutableStateOf(false) }
    var showManualImport by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProxyConfigEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<ProxyConfigEntry?>(null) }

    // 二维码：从相册选图（Photo Picker，免存储权限）。
    val pickQrImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Photo Picker 的 Uri 通常不可持久化，失败无妨：本次读取权限已足够完成导入。
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        actions.importQr(uri)
    }
    // 配置文件：SAF 文档选择器，覆盖 .yaml/.yml/.txt 及无 MIME 的机场文件。
    val pickConfigFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) actions.importFile(uri)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            GlassTopAppBar(
                title = { AppText(text = stringResource(R.string.settings_proxy_title)) },
                navigationIcon = { BackIcon(onBack) },
                actions = {
                    if (busy) {
                        Box(
                            modifier = Modifier.padding(
                                end = dimensionResource(ResR.dimen.dimen_global_padding_m),
                            ),
                        ) {
                            AppLoadingIndicator(
                                size = 24.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ProxyTabBar(current = currentTab, onSelect = { tabIndex = it.ordinal })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when (currentTab) {
                ProxyTab.Dashboard -> DashboardTab(
                    state = state,
                    logs = logs,
                    latency = latency,
                    preview = preview,
                    actions = actions,
                    onOpenConfig = { tabIndex = ProxyTab.Config.ordinal },
                )
                ProxyTab.Proxy -> ProxyNodesTab(
                    state = state,
                    latency = latency,
                    actions = actions,
                )
                ProxyTab.Config -> ConfigTab(
                    state = state,
                    actions = actions,
                    onPickQr = {
                        pickQrImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onPickFile = { pickConfigFile.launch(arrayOf("*/*")) },
                    onShowLink = { showLinkImport = true },
                    onShowManual = { showManualImport = true },
                    onRename = { renameTarget = it },
                    onDelete = { deleteTarget = it },
                )
            }
        }
    }

    if (showManualImport) {
        ProxyTextImportDialog(
            title = stringResource(R.string.settings_proxy_manual_title),
            hint = stringResource(R.string.settings_proxy_manual_hint),
            fieldLabel = stringResource(R.string.settings_proxy_manual_field),
            confirmLabel = stringResource(R.string.settings_proxy_confirm),
            singleLine = false,
            onConfirm = { text, name ->
                actions.importManual(text, name)
                showManualImport = false
            },
            onDismiss = { showManualImport = false },
        )
    }

    if (showLinkImport) {
        ProxyTextImportDialog(
            title = stringResource(R.string.settings_proxy_link_title),
            hint = stringResource(R.string.settings_proxy_link_hint),
            fieldLabel = stringResource(R.string.settings_proxy_link_label),
            confirmLabel = stringResource(R.string.settings_proxy_confirm),
            singleLine = true,
            onConfirm = { text, name ->
                actions.importLink(text, name)
                showLinkImport = false
            },
            onDismiss = { showLinkImport = false },
        )
    }

    renameTarget?.let { target ->
        ProxyRenameDialog(
            initialName = target.name,
            onConfirm = { name ->
                actions.renameConfig(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        ProxyConfirmDialog(
            title = stringResource(R.string.settings_proxy_remove_confirm_title),
            message = stringResource(R.string.settings_proxy_remove_confirm_message, target.name),
            confirmLabel = stringResource(R.string.settings_proxy_remove),
            onConfirm = {
                actions.removeConfig(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

// ===================== 底部三标签 =====================

private enum class ProxyTab(
    val labelRes: Int,
    val icon: ImageVector,
) {
    Dashboard(R.string.settings_proxy_tab_dashboard, Icons.Filled.Speed),
    Proxy(R.string.settings_proxy_tab_proxy, Icons.Filled.Router),
    Config(R.string.settings_proxy_tab_config, Icons.Filled.Description),
}

@Composable
private fun ProxyTabBar(
    current: ProxyTab,
    onSelect: (ProxyTab) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
    ) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_l)),
            fallbackColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
                horizontalArrangement = Arrangement.spacedBy(
                    dimensionResource(ResR.dimen.dimen_global_padding_s),
                ),
            ) {
                ProxyTab.entries.forEach { tab ->
                    ProxyTabItem(
                        tab = tab,
                        selected = tab == current,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProxyTabItem(
    tab: ProxyTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = rememberAppMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motion.effectSpec(),
        label = "proxyTabContentColor",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = motion.effectSpec(),
        label = "proxyTabContainerColor",
    )
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        AppText(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

// ===================== 仪表盘 =====================

@Composable
private fun DashboardTab(
    state: ProxyState,
    logs: List<ProxyLogEntry>,
    latency: Map<String, Long>,
    preview: RouteDecision,
    actions: ProxySettingsActions,
    onOpenConfig: () -> Unit,
) {
    val prefs = state.preferences
    val active = state.activeConfig
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m))
            .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_xl)),
    ) {
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        ProxyStatusPanel(prefs = prefs, actions = actions)

        SettingsGroup(
            label = stringResource(R.string.settings_proxy_current_config),
            visible = true,
            delayMillis = 60,
        ) {
            if (active == null) {
                ProxyNoteRow(
                    icon = Icons.Filled.Description,
                    text = stringResource(R.string.settings_proxy_no_config_hint),
                )
            } else {
                SettingsEntry(
                    icon = Icons.Filled.Description,
                    title = active.name,
                    desc = stringResource(
                        R.string.settings_proxy_config_summary,
                        active.nodeCount,
                        active.groupCount,
                        active.ruleCount,
                    ),
                    onClick = onOpenConfig,
                )
                if (active.warningCount > 0) {
                    SettingsEntryDivider()
                    ProxyNoteRow(
                        icon = Icons.Filled.Warning,
                        text = stringResource(
                            R.string.settings_proxy_config_warnings,
                            active.warningCount,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        SettingsGroup(
            label = stringResource(R.string.settings_proxy_current_outbound),
            visible = true,
            delayMillis = 120,
        ) {
            ProxyOutboundRow(
                decision = preview,
                latency = latency[preview.policy],
                onMeasure = { actions.measure(preview.policy.ifBlank { ClashConfig.POLICY_DIRECT }) },
            )
        }

        SettingsGroup(
            label = stringResource(R.string.settings_proxy_tools_logs),
            visible = true,
            delayMillis = 180,
        ) {
            if (logs.isEmpty()) {
                ProxyNoteRow(
                    icon = Icons.Filled.Description,
                    text = stringResource(R.string.settings_proxy_tools_logs_empty),
                )
            } else {
                logs.take(MAX_VISIBLE_LOGS).forEachIndexed { index, entry ->
                    if (index > 0) SettingsEntryDivider()
                    ProxyLogRow(entry = entry, clock = clock)
                }
                SettingsEntryDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AppTextButton(
                        text = stringResource(R.string.settings_proxy_tools_clear_logs),
                        onClick = actions.clearLogs,
                        enabled = logs.isNotEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProxyStatusPanel(
    prefs: ProxyPreferences,
    actions: ProxySettingsActions,
) {
    ProxyPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = stringResource(R.string.settings_proxy_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                AppText(
                    text = stringResource(
                        if (prefs.enabled) {
                            R.string.settings_proxy_status_on
                        } else {
                            R.string.settings_proxy_status_off
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (prefs.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            AppSwitch(checked = prefs.enabled, onCheckedChange = actions.setEnabled)
        }
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        AppText(
            text = stringResource(
                if (prefs.enabled) {
                    R.string.settings_proxy_status_on_desc
                } else {
                    R.string.settings_proxy_status_off_desc
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        ProxyFieldLabel(stringResource(R.string.settings_proxy_scope))
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        AppSegmentedRow(
            options = listOf(
                stringResource(R.string.settings_proxy_scope_pixiv),
                stringResource(R.string.settings_proxy_scope_global),
            ),
            selectedIndex = if (prefs.scope == ProxyScope.Global) 1 else 0,
            onSelect = { index ->
                actions.setScope(if (index == 0) ProxyScope.PixivOnly else ProxyScope.Global)
            },
        )
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        AppText(
            text = stringResource(
                if (prefs.scope == ProxyScope.Global) {
                    R.string.settings_proxy_scope_hint_global
                } else {
                    R.string.settings_proxy_scope_hint_pixiv
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        ProxyFieldLabel(stringResource(R.string.settings_proxy_mode))
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        AppSegmentedRow(
            options = listOf(
                stringResource(R.string.settings_proxy_mode_rule),
                stringResource(R.string.settings_proxy_mode_global),
                stringResource(R.string.settings_proxy_mode_direct),
            ),
            selectedIndex = ProxyMode.entries.indexOf(prefs.mode),
            onSelect = { index ->
                ProxyMode.entries.getOrNull(index)?.let(actions.setMode)
            },
        )
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        AppText(
            text = stringResource(modeHintRes(prefs.mode)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProxyOutboundRow(
    decision: RouteDecision,
    latency: Long?,
    onMeasure: () -> Unit,
) {
    val titleColor = when (decision.outbound) {
        Outbound.Direct -> MaterialTheme.colorScheme.onSurface
        is Outbound.Node -> MaterialTheme.colorScheme.primary
        Outbound.Reject, is Outbound.Unavailable -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            dimensionResource(ResR.dimen.dimen_global_padding_m),
        ),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = outboundTitle(decision),
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
            )
            AppText(
                text = decision.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            AppText(
                text = latencyLabel(latency),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppTextButton(
                text = stringResource(R.string.settings_proxy_measure),
                onClick = onMeasure,
            )
        }
    }
}

// ===================== 代理（分组 / 节点） =====================

@Composable
private fun ProxyNodesTab(
    state: ProxyState,
    latency: Map<String, Long>,
    actions: ProxySettingsActions,
) {
    val config = state.activeConfig?.config
    val prefs = state.preferences
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m))
            .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_xl)),
    ) {
        if (config == null) {
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            SettingsGroup(
                label = stringResource(R.string.settings_proxy_tab_proxy),
                visible = true,
                delayMillis = 0,
            ) {
                ProxyNoteRow(
                    icon = Icons.Filled.Description,
                    text = stringResource(R.string.settings_proxy_no_config_hint),
                )
            }
            return@Column
        }

        config.groups.forEachIndexed { groupIndex, group ->
            SettingsGroup(
                label = "${group.name} · ${groupTypeText(group.type)}",
                visible = true,
                delayMillis = (groupIndex * 40).coerceAtMost(200),
            ) {
                if (group.members.isEmpty()) {
                    ProxyNoteRow(
                        icon = Icons.Filled.Warning,
                        text = stringResource(R.string.settings_proxy_group_empty),
                    )
                } else {
                    val selection = prefs.selectionOf(group.name)
                    group.members.forEachIndexed { memberIndex, member ->
                        if (memberIndex > 0) SettingsEntryDivider()
                        ProxyMemberRow(
                            config = config,
                            member = member,
                            selected = selection == member,
                            latency = latency[member],
                            onClick = { actions.selectGroupNode(group.name, member) },
                        )
                    }
                }
            }
        }

        val primaryGroup = config.primaryGroup
        SettingsGroup(
            label = stringResource(R.string.settings_proxy_nodes),
            visible = true,
            delayMillis = (config.groups.size * 40).coerceAtMost(200),
        ) {
            if (config.nodes.isEmpty()) {
                ProxyNoteRow(
                    icon = Icons.Filled.Warning,
                    text = stringResource(R.string.settings_proxy_nodes_empty),
                )
            } else {
                val currentSelection = primaryGroup?.let { prefs.selectionOf(it.name) }
                    ?: prefs.globalSelection
                config.nodes.forEachIndexed { index, node ->
                    if (index > 0) SettingsEntryDivider()
                    ProxyNodeRow(
                        node = node,
                        selected = currentSelection == node.name,
                        latency = latency[node.name],
                        onClick = {
                            if (primaryGroup != null) {
                                actions.selectGroupNode(primaryGroup.name, node.name)
                            } else {
                                actions.setGlobalSelection(node.name)
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        AppOutlinedButton(
            text = stringResource(R.string.settings_proxy_measure_all),
            onClick = actions.measureAll,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = Icons.Filled.NetworkCheck,
        )
    }
}

@Composable
private fun ProxyMemberRow(
    config: ClashConfig,
    member: String,
    selected: Boolean,
    latency: Long?,
    onClick: () -> Unit,
) {
    val node = config.node(member)
    val group = config.group(member)
    val isDirect = member.equals(ClashConfig.POLICY_DIRECT, ignoreCase = true)
    val isReject = member.equals(ClashConfig.POLICY_REJECT, ignoreCase = true)
    val supported = when {
        node != null -> node.isSupported
        group != null -> group.members.isNotEmpty()
        isDirect -> true
        else -> false
    }
    val subtitle = when {
        node != null -> "${node.protocol.token.uppercase()} · ${node.address}"
        group != null -> groupTypeText(group.type)
        isDirect -> stringResource(R.string.settings_proxy_direct)
        isReject -> stringResource(R.string.settings_proxy_reject)
        else -> stringResource(R.string.settings_proxy_member_missing)
    }
    val contentAlpha = if (supported) 1f else 0.38f
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource, supported)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = supported,
                onClick = onClick,
            )
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            dimensionResource(ResR.dimen.dimen_global_padding_m),
        ),
    ) {
        Icon(
            imageVector = if (selected) {
                Icons.Filled.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = member,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (node != null && !node.isSupported) {
            ProxyBadge(
                text = stringResource(R.string.settings_proxy_unsupported),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (latency != null) {
            AppText(
                text = latencyLabel(latency),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProxyNodeRow(
    node: ProxyNode,
    selected: Boolean,
    latency: Long?,
    onClick: () -> Unit,
) {
    val supported = node.isSupported
    val contentAlpha = if (supported) 1f else 0.38f
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource, supported)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = supported,
                onClick = onClick,
            )
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            dimensionResource(ResR.dimen.dimen_global_padding_m),
        ),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = node.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = "${node.protocol.token.uppercase()} · ${node.address}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
        if (!supported) {
            ProxyBadge(
                text = stringResource(R.string.settings_proxy_unsupported),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (latency != null) {
            AppText(
                text = latencyLabel(latency),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ===================== 配置 =====================

@Composable
private fun ConfigTab(
    state: ProxyState,
    actions: ProxySettingsActions,
    onPickQr: () -> Unit,
    onPickFile: () -> Unit,
    onShowLink: () -> Unit,
    onShowManual: () -> Unit,
    onRename: (ProxyConfigEntry) -> Unit,
    onDelete: (ProxyConfigEntry) -> Unit,
) {
    val activeId = state.activeConfig?.id
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m))
            .padding(bottom = dimensionResource(ResR.dimen.dimen_global_padding_xl)),
    ) {
        Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        if (state.configs.isEmpty()) {
            SettingsGroup(
                label = stringResource(R.string.settings_proxy_configs),
                visible = true,
                delayMillis = 0,
            ) {
                ProxyNoteRow(
                    icon = Icons.Filled.Description,
                    text = stringResource(R.string.settings_proxy_config_empty_hint),
                )
            }
        } else {
            SettingsGroup(
                label = stringResource(R.string.settings_proxy_configs),
                visible = true,
                delayMillis = 0,
            ) {
                state.configs.forEachIndexed { index, entry ->
                    if (index > 0) SettingsEntryDivider()
                    ProxyConfigRow(
                        entry = entry,
                        active = entry.id == activeId,
                        onActivate = { actions.setActiveConfig(entry.id) },
                        onRefresh = { actions.refreshSubscription(entry) },
                        onRename = { onRename(entry) },
                        onDelete = { onDelete(entry) },
                    )
                }
            }
            val active = state.activeConfig
            val warnings = active?.config?.warnings.orEmpty()
            if (warnings.isNotEmpty()) {
                SettingsGroupNote(
                    text = warnings.take(MAX_VISIBLE_WARNINGS).joinToString("\n"),
                )
            }
        }

        AddConfigSection(
            onPickQr = onPickQr,
            onPickFile = onPickFile,
            onShowLink = onShowLink,
            onShowManual = onShowManual,
            delayMillis = 60,
        )
    }
}

@Composable
private fun ProxyConfigRow(
    entry: ProxyConfigEntry,
    active: Boolean,
    onActivate: () -> Unit,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource, !active)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = !active,
                    onClick = onActivate,
                )
                .padding(
                    horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                    vertical = dimensionResource(ResR.dimen.dimen_global_padding_m),
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        ) {
            Box(
                modifier = Modifier
                    .size(dimensionResource(ResR.dimen.dimen_global_touch_target) - 8.dp)
                    .clip(RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_m)))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (active) {
                        Spacer(Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                        ProxyBadge(
                            text = stringResource(R.string.settings_proxy_active),
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                AppText(
                    text = stringResource(
                        R.string.settings_proxy_config_source_summary,
                        sourceText(entry.source),
                        entry.nodeCount,
                        entry.groupCount,
                        entry.ruleCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!active) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = dimensionResource(ResR.dimen.dimen_global_padding_m) +
                        dimensionResource(ResR.dimen.dimen_global_touch_target) - 8.dp +
                        dimensionResource(ResR.dimen.dimen_global_padding_m),
                    end = dimensionResource(ResR.dimen.dimen_global_padding_s),
                    bottom = dimensionResource(ResR.dimen.dimen_global_padding_s),
                ),
            horizontalArrangement = Arrangement.spacedBy(
                dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
        ) {
            if (!entry.subscriptionUrl.isNullOrBlank()) {
                AppTextButton(
                    text = stringResource(R.string.settings_proxy_refresh),
                    onClick = onRefresh,
                )
            }
            AppTextButton(
                text = stringResource(R.string.settings_proxy_rename),
                onClick = onRename,
            )
            AppTextButton(
                text = stringResource(R.string.settings_proxy_remove),
                onClick = onDelete,
                contentColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AddConfigSection(
    onPickQr: () -> Unit,
    onPickFile: () -> Unit,
    onShowLink: () -> Unit,
    onShowManual: () -> Unit,
    delayMillis: Int,
) {
    SettingsGroup(
        label = stringResource(R.string.settings_proxy_add),
        visible = true,
        delayMillis = delayMillis,
    ) {
        SettingsEntry(
            icon = Icons.Filled.QrCodeScanner,
            title = stringResource(R.string.settings_proxy_add_qr),
            desc = stringResource(R.string.settings_proxy_add_qr_desc),
            onClick = onPickQr,
        )
        SettingsEntryDivider()
        SettingsEntry(
            icon = Icons.Filled.UploadFile,
            title = stringResource(R.string.settings_proxy_add_file),
            desc = stringResource(R.string.settings_proxy_add_file_desc),
            onClick = onPickFile,
        )
        SettingsEntryDivider()
        SettingsEntry(
            icon = Icons.Filled.Link,
            title = stringResource(R.string.settings_proxy_add_link),
            desc = stringResource(R.string.settings_proxy_add_link_desc),
            onClick = onShowLink,
        )
        SettingsEntryDivider()
        SettingsEntry(
            icon = Icons.Filled.Create,
            title = stringResource(R.string.settings_proxy_add_manual),
            desc = stringResource(R.string.settings_proxy_add_manual_desc),
            onClick = onShowManual,
        )
    }
}

// ===================== 连接日志 =====================

@Composable
private fun ProxyLogRow(
    entry: ProxyLogEntry,
    clock: SimpleDateFormat,
) {
    val dotColor = when (entry.kind) {
        ProxyLogKind.Proxied -> MaterialTheme.colorScheme.primary
        ProxyLogKind.Direct -> MaterialTheme.colorScheme.outline
        ProxyLogKind.Rejected, ProxyLogKind.Failed -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                vertical = dimensionResource(ResR.dimen.dimen_global_padding_s),
            ),
        horizontalArrangement = Arrangement.spacedBy(
            dimensionResource(ResR.dimen.dimen_global_padding_m),
        ),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = entry.host,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AppText(
                    text = clock.format(Date(entry.timeMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppText(
                text = stringResource(
                    R.string.settings_proxy_log_line,
                    stringResource(logKindTextRes(entry.kind)),
                    entry.policy,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.detail.isNotBlank()) {
                AppText(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ===================== 弹窗 =====================

@Composable
private fun ProxyTextImportDialog(
    title: String,
    hint: String,
    fieldLabel: String,
    confirmLabel: String,
    singleLine: Boolean,
    onConfirm: (text: String, name: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
        AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { AppText(text = title) },
            text = {
                Column {
                    AppText(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
                    AppTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.settings_proxy_optional_name),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                    AppTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = fieldLabel,
                        singleLine = singleLine,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                AppTextButton(
                    text = confirmLabel,
                    enabled = text.isNotBlank(),
                    onClick = {
                        onConfirm(text, name.trim().takeIf { it.isNotEmpty() })
                    },
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(ResR.string.action_cancel),
                    onClick = onDismiss,
                )
            },
            glass = false,
        )
    }
}

@Composable
private fun ProxyRenameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
        AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { AppText(text = stringResource(R.string.settings_proxy_rename_title)) },
            text = {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.settings_proxy_rename_label),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                AppTextButton(
                    text = stringResource(R.string.settings_proxy_save),
                    enabled = name.isNotBlank(),
                    onClick = { onConfirm(name) },
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(ResR.string.action_cancel),
                    onClick = onDismiss,
                )
            },
            glass = false,
        )
    }
}

@Composable
private fun ProxyConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    LiquidGlassLayerScope(LiquidGlassLayer.MODAL) {
        AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { AppText(text = title) },
            text = {
                AppText(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                AppTextButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    contentColor = MaterialTheme.colorScheme.error,
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(ResR.string.action_cancel),
                    onClick = onDismiss,
                )
            },
            glass = false,
        )
    }
}

// ===================== 复用小组件 =====================

/** 代理页通用玻璃面板：与设置分组同一形状与回退底色，保证观感一致。 */
@Composable
private fun ProxyPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_l)),
        fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
            content = content,
        )
    }
}

@Composable
private fun ProxyFieldLabel(text: String) {
    AppText(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProxyNoteRow(
    icon: ImageVector,
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            dimensionResource(ResR.dimen.dimen_global_padding_m),
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        AppText(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProxyBadge(
    text: String,
    container: Color,
    content: Color,
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        AppText(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
        )
    }
}

// ===================== 文案映射 =====================

@Composable
private fun outboundTitle(decision: RouteDecision): String = when (decision.outbound) {
    Outbound.Direct -> stringResource(R.string.settings_proxy_direct)
    Outbound.Reject -> stringResource(R.string.settings_proxy_reject)
    is Outbound.Node -> decision.policy
    is Outbound.Unavailable -> stringResource(R.string.settings_proxy_outbound_unavailable)
}

@Composable
private fun groupTypeText(type: ProxyGroupType): String = stringResource(
    when (type) {
        ProxyGroupType.Select -> R.string.settings_proxy_group_select
        ProxyGroupType.UrlTest -> R.string.settings_proxy_group_urltest
        ProxyGroupType.Fallback -> R.string.settings_proxy_group_fallback
        ProxyGroupType.LoadBalance -> R.string.settings_proxy_group_loadbalance
        ProxyGroupType.Relay -> R.string.settings_proxy_group_relay
        ProxyGroupType.Unknown -> R.string.settings_proxy_group_unknown
    },
)

@Composable
private fun sourceText(source: ProxySourceType): String = stringResource(
    when (source) {
        ProxySourceType.Manual -> R.string.settings_proxy_source_manual
        ProxySourceType.Link -> R.string.settings_proxy_source_link
        ProxySourceType.File -> R.string.settings_proxy_source_file
        ProxySourceType.QrCode -> R.string.settings_proxy_source_qr
    },
)

@Composable
private fun latencyLabel(latency: Long?): String = if (latency == null) {
    stringResource(R.string.settings_proxy_latency_none)
} else {
    stringResource(R.string.settings_proxy_latency_value, latency)
}

private fun modeHintRes(mode: ProxyMode): Int = when (mode) {
    ProxyMode.Rule -> R.string.settings_proxy_mode_hint_rule
    ProxyMode.Global -> R.string.settings_proxy_mode_hint_global
    ProxyMode.Direct -> R.string.settings_proxy_mode_hint_direct
}

private fun logKindTextRes(kind: ProxyLogKind): Int = when (kind) {
    ProxyLogKind.Proxied -> R.string.settings_proxy_log_proxied
    ProxyLogKind.Direct -> R.string.settings_proxy_log_direct
    ProxyLogKind.Rejected -> R.string.settings_proxy_log_rejected
    ProxyLogKind.Failed -> R.string.settings_proxy_log_failed
}
