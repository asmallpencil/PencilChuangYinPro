package com.pencil.chuangyin.feature.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.pencil.chuangyin.core.theme.CornerStyle
import com.pencil.chuangyin.core.theme.MotionPreset
import com.pencil.chuangyin.core.theme.SeedOption
import com.pencil.chuangyin.core.theme.ThemeState
import com.pencil.chuangyin.core.theme.cornerShape
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppOutlinedButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSecondaryButton
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppSwitch
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.pixiv.core.network.PixivConfig
import com.pencil.chuangyin.feature.pixiv.ui.login.PixivLoginUiState
import com.pencil.chuangyin.feature.pixiv.ui.login.PixivLoginViewModel
import com.pencil.chuangyin.resources.R as ResR

/** 引导功能单条：图标 + 标题 + 描述。 */
private data class OverviewItem(
    val icon: ImageVector,
    val labelRes: Int,
    val descRes: Int,
)

/** 待申请运行时权限条目：标题 + 用途说明 + 实际权限清单。 */
private data class PermissionEntry(val labelRes: Int, val descRes: Int)

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
    pixivLoginViewModel: PixivLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pixivLoginState by pixivLoginViewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        pixivLoginState = pixivLoginState,
        onFinished = onFinished,
        onBack = onBack,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onSkip = onFinished,
        onSeedSelected = viewModel::setSeedColor,
        onCornerStyleSelected = viewModel::setCornerStyle,
        onCornerStrengthChange = viewModel::setCornerStrength,
        onMotionSelected = viewModel::setMotionPreset,
        onDarkChange = viewModel::setDarkTheme,
        onOutlineChange = viewModel::setOutlineEnabled,
        onSnowfallChange = viewModel::setSnowfallEnabled,
        onPixivAuthCode = pixivLoginViewModel::onAuthCode,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    pixivLoginState: PixivLoginUiState,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onSeedSelected: (SeedOption) -> Unit,
    onCornerStyleSelected: (CornerStyle) -> Unit,
    onCornerStrengthChange: (Float) -> Unit,
    onMotionSelected: (MotionPreset) -> Unit,
    onDarkChange: (Boolean) -> Unit,
    onOutlineChange: (Boolean) -> Unit,
    onSnowfallChange: (Boolean) -> Unit,
    onPixivAuthCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { state.totalSteps })

    // ViewModel 的 step 是唯一事实来源：下一步/上一步改它，这里把 Pager 吸附到对应页。
    LaunchedEffect(state.step) {
        if (pagerState.currentPage != state.step) {
            pagerState.animateScrollToPage(state.step)
        }
    }
    val glassActive = rememberLiquidGlassActive()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    AppText(
                        text = stringResource(ResR.string.onboarding_step_x_of_y, state.step + 1, state.totalSteps),
                    )
                },
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
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            val stepProgress by animateFloatAsState(
                targetValue = (state.step + 1f) / state.totalSteps,
                animationSpec = rememberAppMotion().effectSpec(),
                label = "onboardingStepProgress",
            )
            LinearProgressIndicator(
                progress = { stepProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(ResR.dimen.dimen_shape_corner_s)),
            )

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_l)),
                ) {
                    when (page) {
                        0 -> WelcomeContent()
                        1 -> FeaturesContent()
                        2 -> PermissionContent()
                        3 -> SeedColorContent(
                            seedArgb = state.themeState.seedArgb,
                            onSeedSelected = onSeedSelected,
                        )
                        4 -> StyleContent(
                            cornerStyle = state.themeState.cornerStyle,
                            cornerStrength = state.themeState.cornerStrength,
                            motion = state.themeState.motionPreset,
                            onCornerStyleSelected = onCornerStyleSelected,
                            onCornerStrengthChange = onCornerStrengthChange,
                            onMotionSelected = onMotionSelected,
                        )
                        5 -> PixivLoginContent(
                            isLoggedIn = state.isPixivLoggedIn,
                            userName = state.pixivUserName,
                            loginState = pixivLoginState,
                            onAuthCode = onPixivAuthCode,
                        )
                        6 -> FinishContent(
                            darkTheme = state.themeState.darkTheme,
                            outlineEnabled = state.themeState.outlineEnabled,
                            snowfallEnabled = state.themeState.snowfallEnabled,
                            onDarkChange = onDarkChange,
                            onOutlineChange = onOutlineChange,
                            onSnowfallChange = onSnowfallChange,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
            ) {
                if (state.step > 0) {
                    AppSecondaryButton(
                        text = stringResource(ResR.string.label_previous),
                        onClick = onPrevious,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!state.isLastStep) {
                    AppOutlinedButton(
                        text = stringResource(ResR.string.onboarding_skip),
                        onClick = onSkip,
                    )
                }
                if (state.isLastStep) {
                    AppPrimaryButton(
                        text = stringResource(ResR.string.onboarding_action_start),
                        onClick = onFinished,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    AppPrimaryButton(
                        text = stringResource(ResR.string.label_next),
                        onClick = onNext,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeContent() {
    Column {
        AppText(
            text = stringResource(ResR.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        AppText(
            text = stringResource(ResR.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        AppText(
            text = stringResource(ResR.string.onboarding_welcome_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeaturesContent() {
    val items = listOf(
        OverviewItem(Icons.Filled.TextFields, ResR.string.onboarding_feature_text, ResR.string.onboarding_feature_text_desc),
        OverviewItem(Icons.Filled.Image, ResR.string.onboarding_feature_image, ResR.string.onboarding_feature_image_desc),
        OverviewItem(Icons.Filled.DocumentScanner, ResR.string.onboarding_feature_scan, ResR.string.onboarding_feature_scan_desc),
        OverviewItem(Icons.Filled.Brush, ResR.string.onboarding_feature_graphics, ResR.string.onboarding_feature_graphics_desc),
        OverviewItem(Icons.Filled.Store, ResR.string.onboarding_feature_pixiv, ResR.string.onboarding_feature_pixiv_desc),
    )
    Column {
        AppText(
            text = stringResource(ResR.string.onboarding_features_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        AppText(
            text = stringResource(ResR.string.onboarding_features_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        items.forEach { item ->
            OverviewRow(item = item)
        }
    }
}

@Composable
private fun OverviewRow(item: OverviewItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dimensionResource(ResR.dimen.dimen_global_touch_target))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = stringResource(item.labelRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s) / 2f))
            AppText(
                text = stringResource(item.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionContent() {
    val context = LocalContext.current
    val cameraPerms = remember { listOf(Manifest.permission.CAMERA) }
    val bluetoothPerms = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val notificationPerms = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
    }
    val storagePerms = remember {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            emptyList()
        }
    }
    val allPerms = remember { cameraPerms + bluetoothPerms + notificationPerms + storagePerms }

    fun grantedNow(perms: List<String>): Boolean =
        perms.isNotEmpty() && perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    // 请求结果驱动重组（本机权限状态同时真实反映到 grantedNow）。
    var grantedVersion by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantedVersion++ }

    val allGranted = allPerms.isNotEmpty() && allPerms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    Column {
        AppText(
            text = stringResource(ResR.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        AppText(
            text = stringResource(ResR.string.onboarding_permissions_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        PermissionRow(
            entry = PermissionEntry(
                ResR.string.onboarding_permission_camera,
                ResR.string.onboarding_permission_camera_desc,
            ),
            granted = grantedNow(cameraPerms),
        )
        PermissionRow(
            entry = PermissionEntry(
                ResR.string.onboarding_permission_bluetooth,
                ResR.string.onboarding_permission_bluetooth_desc,
            ),
            granted = grantedNow(bluetoothPerms),
        )
        PermissionRow(
            entry = PermissionEntry(
                ResR.string.onboarding_permission_notification,
                ResR.string.onboarding_permission_notification_desc,
            ),
            granted = grantedNow(notificationPerms),
        )
        if (storagePerms.isNotEmpty()) {
            PermissionRow(
                entry = PermissionEntry(
                    ResR.string.onboarding_permission_storage,
                    ResR.string.onboarding_permission_storage_desc,
                ),
                granted = grantedNow(storagePerms),
            )
        }
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        if (allGranted) {
            AppText(
                text = stringResource(ResR.string.onboarding_permission_all_granted),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            AppPrimaryButton(
                text = stringResource(ResR.string.onboarding_permission_grant),
                onClick = { launcher.launch(allPerms.toTypedArray()) },
            )
        }
    }
}

@Composable
private fun PermissionRow(entry: PermissionEntry, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_s)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = stringResource(entry.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (granted) {
                    Spacer(modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(ResR.string.onboarding_permission_granted),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                    )
                }
            }
            AppText(
                text = stringResource(entry.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeedColorContent(
    seedArgb: Long,
    onSeedSelected: (SeedOption) -> Unit,
) {
    Column {
        AppText(
            text = stringResource(ResR.string.onboarding_seed_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        AppText(
            text = stringResource(ResR.string.onboarding_seed_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        val swatchSize = dimensionResource(ResR.dimen.dimen_global_touch_target)
        Column(
            verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            SeedOption.entries.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                ) {
                    row.forEach { option ->
                        val selected = seedArgb == option.color
                        Box(
                            modifier = Modifier
                                .size(swatchSize)
                                .clip(CircleShape)
                                .background(Color(option.color))
                                .border(
                                    width = dimensionResource(ResR.dimen.dimen_shape_corner_s) / 4f,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { onSeedSelected(option) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(seedName(option)),
                                    tint = if (isLight(option.color)) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StyleContent(
    cornerStyle: CornerStyle,
    cornerStrength: Float,
    motion: MotionPreset,
    onCornerStyleSelected: (CornerStyle) -> Unit,
    onCornerStrengthChange: (Float) -> Unit,
    onMotionSelected: (MotionPreset) -> Unit,
) {
    Column {
        AppText(
            text = stringResource(ResR.string.onboarding_style_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        AppText(
            text = stringResource(ResR.string.onboarding_style_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        AppText(
            text = stringResource(ResR.string.onboarding_label_shape),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        val previewSize = dimensionResource(ResR.dimen.dimen_global_touch_target)
        val previewBase = dimensionResource(ResR.dimen.dimen_shape_corner_m)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            CornerStyle.entries.forEach { style ->
                val isSelected = style == cornerStyle
                val previewShape = cornerShape(style, cornerStrength, previewBase)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onCornerStyleSelected(style) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(previewSize)
                            .clip(previewShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .border(
                                width = dimensionResource(ResR.dimen.dimen_shape_corner_s) / 4f,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = previewShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    AppText(
                        text = stringResource(cornerStyleName(style)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        AppText(
            text = stringResource(ResR.string.onboarding_label_corner_strength),
            style = MaterialTheme.typography.titleMedium,
        )
        AppSlider(
            value = cornerStrength,
            onValueChange = onCornerStrengthChange,
            enabled = cornerStyle != CornerStyle.Square,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        AppText(
            text = stringResource(ResR.string.onboarding_label_motion),
            style = MaterialTheme.typography.titleMedium,
        )
        AppSegmentedRow(
            options = MotionPreset.entries.map { stringResource(motionLabel(it)) },
            selectedIndex = MotionPreset.entries.indexOf(motion),
            onSelect = { onMotionSelected(MotionPreset.entries[it]) },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PixivLoginContent(
    isLoggedIn: Boolean,
    userName: String,
    loginState: PixivLoginUiState,
    onAuthCode: (String) -> Unit,
) {
    Column {
        AppText(
            text = stringResource(ResR.string.onboarding_pixiv_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        AppText(
            text = stringResource(ResR.string.onboarding_pixiv_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isLoggedIn) {
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(ResR.string.onboarding_pixiv_logged_in),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_padding_s)))
                AppText(
                    text = stringResource(ResR.string.onboarding_pixiv_logged_in_as, userName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
            AppText(
                text = stringResource(ResR.string.onboarding_pixiv_not_logged),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(ResR.dimen.dimen_onboarding_login_webview_height))
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                key(loginState.loginUrl) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                configureOnboardingWebView(onAuthCode = onAuthCode)
                                loadUrl(loginState.loginUrl)
                            }
                        },
                    )
                }
            }
            if (loginState.loading) {
                Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
                AppText(
                    text = stringResource(ResR.string.pixiv_login_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        AppText(
            text = stringResource(ResR.string.onboarding_pixiv_optional),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FinishContent(
    darkTheme: Boolean,
    outlineEnabled: Boolean,
    snowfallEnabled: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onOutlineChange: (Boolean) -> Unit,
    onSnowfallChange: (Boolean) -> Unit,
) {
    Column {
        AppText(
            text = stringResource(ResR.string.onboarding_finish_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        AppText(
            text = stringResource(ResR.string.onboarding_finish_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        LabeledSwitchRow(
            label = stringResource(ResR.string.onboarding_label_dark_theme),
            checked = darkTheme,
            onCheckedChange = onDarkChange,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        LabeledSwitchRow(
            label = stringResource(ResR.string.onboarding_label_outline),
            checked = outlineEnabled,
            onCheckedChange = onOutlineChange,
        )
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
        LabeledSwitchRow(
            label = stringResource(ResR.string.onboarding_label_snowfall),
            checked = snowfallEnabled,
            onCheckedChange = onSnowfallChange,
        )
    }
}

@Composable
private fun LabeledSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── P 站登录 WebView 工具 ────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureOnboardingWebView(onAuthCode: (String) -> Unit) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        userAgentString = PixivConfig.USER_AGENT
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            return handlePixivCallback(url, onAuthCode)
        }

        @Deprecated("旧系统回调")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return handlePixivCallback(url ?: return false, onAuthCode)
        }
    }
}

private fun handlePixivCallback(url: String, onAuthCode: (String) -> Unit): Boolean {
    if (!url.startsWith(PixivConfig.REDIRECT_URI)) return false
    val code = Uri.parse(url).getQueryParameter("code")
    if (!code.isNullOrBlank()) onAuthCode(code)
    return true
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    OnboardingScreen(
        state = OnboardingUiState(),
        pixivLoginState = PixivLoginUiState(loginUrl = "https://app-api.pixiv.net/web/v1/login"),
        onFinished = {},
        onBack = {},
        onNext = {},
        onPrevious = {},
        onSkip = {},
        onSeedSelected = {},
        onCornerStyleSelected = {},
        onCornerStrengthChange = {},
        onMotionSelected = {},
        onDarkChange = {},
        onOutlineChange = {},
        onSnowfallChange = {},
        onPixivAuthCode = {},
    )
}

private fun seedName(option: SeedOption): Int = when (option) {
    SeedOption.Orange -> ResR.string.onboarding_seed_orange
    SeedOption.Blue -> ResR.string.onboarding_seed_blue
    SeedOption.Green -> ResR.string.onboarding_seed_green
    SeedOption.Purple -> ResR.string.onboarding_seed_purple
    SeedOption.Rose -> ResR.string.onboarding_seed_rose
    SeedOption.Teal -> ResR.string.onboarding_seed_teal
}

private fun cornerStyleName(style: CornerStyle): Int = when (style) {
    CornerStyle.Rounded -> ResR.string.onboarding_corner_rounded
    CornerStyle.Square -> ResR.string.onboarding_corner_square
    CornerStyle.Diamond -> ResR.string.onboarding_corner_diamond
    CornerStyle.Parallelogram -> ResR.string.onboarding_corner_parallelogram
}

private fun motionLabel(preset: MotionPreset): Int = when (preset) {
    MotionPreset.Full -> ResR.string.onboarding_motion_full
    MotionPreset.Expressive -> ResR.string.onboarding_motion_expressive
    MotionPreset.Reduced -> ResR.string.onboarding_motion_reduced
}

private fun isLight(argb: Long): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    return luminance > 0.6
}