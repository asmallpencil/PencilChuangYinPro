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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.pencil.chuangyin.core.theme.CornerStyle
import com.pencil.chuangyin.core.theme.MotionPreset
import com.pencil.chuangyin.core.theme.OUTLINE_WIDTH_MAX_DP
import com.pencil.chuangyin.core.theme.OUTLINE_WIDTH_MIN_DP
import com.pencil.chuangyin.core.theme.PARTICLE_SPEED_MAX
import com.pencil.chuangyin.core.theme.PARTICLE_SPEED_MIN
import com.pencil.chuangyin.core.theme.ParticleType
import com.pencil.chuangyin.core.theme.SURFACE_ALPHA_MIN
import com.pencil.chuangyin.core.theme.SeedOption
import com.pencil.chuangyin.core.theme.ThemeState
import com.pencil.chuangyin.core.theme.cornerShape
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppSegmentedRow
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppSwitch
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextButton
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.feature.pixiv.core.network.PixivConfig
import com.pencil.chuangyin.feature.pixiv.ui.login.PixivLoginUiState
import com.pencil.chuangyin.feature.pixiv.ui.login.PixivLoginViewModel
import com.pencil.chuangyin.resources.R as ResR
import kotlin.math.abs

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
        onOutlineWidthChange = viewModel::setOutlineWidth,
        onSurfaceAlphaChange = viewModel::setSurfaceAlpha,
        onSnowfallChange = viewModel::setSnowfallEnabled,
        onParticleTypeChange = viewModel::setParticleType,
        onParticleSpeedChange = viewModel::setParticleSpeed,
        onPixivAuthCode = pixivLoginViewModel::onAuthCode,
        modifier = modifier,
    )
}

/**
 * 首次启动引导（重做版）：沉浸式全屏 + 缓慢流动的渐变光斑背景 + 胶囊步骤指示器，
 * 内容卡片随 Pager 位移做缩放/淡入，每步内容错峰浮现。
 *
 * 步骤：欢迎 → 功能 → 权限 → 主题色 → 形状与动效 → 界面细节 → 氛围粒子 → P 站登录 → 完成。
 * 所有设置实时写入主题仓库，进入应用即刻生效。
 */
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
    onOutlineWidthChange: (Float) -> Unit,
    onSurfaceAlphaChange: (Float) -> Unit,
    onSnowfallChange: (Boolean) -> Unit,
    onParticleTypeChange: (ParticleType) -> Unit,
    onParticleSpeedChange: (Float) -> Unit,
    onPixivAuthCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { state.totalSteps })
    val motion = rememberAppMotion()

    // ViewModel 的 step 是唯一事实来源：下一步/上一步改它，这里把 Pager 吸附到对应页。
    LaunchedEffect(state.step) {
        if (pagerState.currentPage != state.step) {
            pagerState.animateScrollToPage(state.step)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        OnboardingBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            // 顶部：返回 / 胶囊进度 / 跳过
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.step > 0) {
                    AppIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(ResR.string.label_back),
                    )
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    StepIndicator(
                        current = state.step,
                        total = state.totalSteps,
                        onSelect = { page -> if (page != state.step) onSelectStep(page, onNext, onPrevious, state.step) },
                    )
                }
                AppTextButton(
                    text = stringResource(ResR.string.onboarding_skip),
                    onClick = onSkip,
                )
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                val pageOffset = (
                    pagerState.currentPage - page + pagerState.currentPageOffsetFraction
                    ).let { abs(it) }.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .graphicsLayer {
                            alpha = 1f - 0.4f * pageOffset
                            scaleX = 1f - 0.05f * pageOffset
                            scaleY = 1f - 0.05f * pageOffset
                            translationY = pageOffset * 36f
                        },
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        tonalElevation = 2.dp,
                        shadowElevation = 6.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
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
                                5 -> InterfaceContent(
                                    darkTheme = state.themeState.darkTheme,
                                    outlineEnabled = state.themeState.outlineEnabled,
                                    outlineWidthDp = state.themeState.outlineWidthDp,
                                    surfaceAlpha = state.themeState.surfaceAlpha,
                                    onDarkChange = onDarkChange,
                                    onOutlineChange = onOutlineChange,
                                    onOutlineWidthChange = onOutlineWidthChange,
                                    onSurfaceAlphaChange = onSurfaceAlphaChange,
                                )
                                6 -> AtmosphereContent(
                                    snowfallEnabled = state.themeState.snowfallEnabled,
                                    particleType = state.themeState.particleType,
                                    particleSpeed = state.themeState.particleSpeed,
                                    onSnowfallChange = onSnowfallChange,
                                    onParticleTypeChange = onParticleTypeChange,
                                    onParticleSpeedChange = onParticleSpeedChange,
                                )
                                7 -> PixivLoginContent(
                                    isLoggedIn = state.isPixivLoggedIn,
                                    userName = state.pixivUserName,
                                    loginState = pixivLoginState,
                                    onAuthCode = onPixivAuthCode,
                                )
                                else -> FinishContent(
                                    themeState = state.themeState,
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
                }
            }

            // 底部操作：上一步 + 下一步/开始使用
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.step > 0) {
                    AppTextButton(
                        text = stringResource(ResR.string.label_previous),
                        onClick = onPrevious,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.width(168.dp)) {
                    val label = if (state.isLastStep) {
                        stringResource(ResR.string.onboarding_action_start)
                    } else {
                        stringResource(ResR.string.label_next)
                    }
                    AppPrimaryButton(
                        text = label,
                        onClick = if (state.isLastStep) onFinished else onNext,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/** 点击胶囊指示器跳步：用 ViewModel 的 next/previous 逐页走到目标，避免绕过业务状态。 */
private fun onSelectStep(target: Int, onNext: () -> Unit, onPrevious: () -> Unit, current: Int) {
    repeat(abs(target - current)) {
        if (target > current) onNext() else onPrevious()
    }
}

/** 缓慢漂移的渐变光斑背景：两个主题色径向光晕 + 上下渐变底色。 */
@Composable
private fun OnboardingBackdrop() {
    val seed = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    // Canvas 的 DrawScope 不是 @Composable 上下文，取色必须提前到组合期。
    val surfaceTop = MaterialTheme.colorScheme.surface
    val surfaceBottom = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val transition = rememberInfiniteTransition(label = "onboardingBackdrop")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "onboardingBackdropPhase",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = GradientBrush.verticalGradient(
                listOf(surfaceTop, surfaceBottom),
            ),
        )
        drawCircle(
            brush = GradientBrush.radialGradient(
                colors = listOf(seed.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * (0.10f + 0.08f * t)),
                radius = size.minDimension * 0.95f,
            ),
            radius = size.minDimension * 0.95f,
            center = Offset(size.width * 0.88f, size.height * (0.10f + 0.08f * t)),
        )
        drawCircle(
            brush = GradientBrush.radialGradient(
                colors = listOf(tertiary.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.08f, size.height * (0.62f - 0.10f * t)),
                radius = size.minDimension * 0.8f,
            ),
            radius = size.minDimension * 0.8f,
            center = Offset(size.width * 0.08f, size.height * (0.62f - 0.10f * t)),
        )
    }
}

/** 胶囊步骤指示器：当前步拉长并着色，切换时用弹性动画。 */
@Composable
private fun StepIndicator(current: Int, total: Int, onSelect: (Int) -> Unit) {
    val motion = rememberAppMotion()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) 26.dp else 8.dp,
                animationSpec = motion.bouncySpec(),
                label = "stepIndicatorWidth",
            )
            val color by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                animationSpec = motion.effectSpec(),
                label = "stepIndicatorColor",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onSelect(index) },
            )
        }
    }
}

/** 每步统一头部：图标徽标 + 标题 + 说明。 */
@Composable
private fun StepHeader(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            AppText(text = title, style = MaterialTheme.typography.headlineSmall)
            AppText(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun WelcomeContent() {
    val transition = rememberInfiniteTransition(label = "welcomeGlow")
    val glow by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "welcomeGlowScale",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = glow; scaleY = glow }
                .clip(CircleShape)
                .background(
                    GradientBrush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Print,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        AppText(
            text = stringResource(ResR.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppText(
            text = stringResource(ResR.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        AppText(
            text = stringResource(ResR.string.onboarding_welcome_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WelcomePill(Icons.Filled.Lock, "本地处理")
            WelcomePill(Icons.Filled.Bolt, "即连即印")
            WelcomePill(Icons.Filled.AutoAwesome, "自由定制")
        }
    }
}

@Composable
private fun WelcomePill(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        AppText(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
    StepHeader(
        icon = Icons.Filled.AutoAwesome,
        title = stringResource(ResR.string.onboarding_features_title),
        desc = stringResource(ResR.string.onboarding_features_desc),
    )
    items.forEachIndexed { index, item ->
        OverviewRow(item = item, index = index)
    }
}

/** 功能条目：图标徽标 + 标题/说明，按 index 错峰入场。 */
@Composable
private fun OverviewRow(item: OverviewItem, index: Int) {
    val motion = rememberAppMotion()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val reveal by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = motion.staggerSpec(index, stepMs = 70),
        label = "overviewReveal",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .graphicsLayer {
                alpha = reveal
                translationX = (1f - reveal) * 28f
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = stringResource(item.labelRes),
                style = MaterialTheme.typography.titleMedium,
            )
            AppText(
                text = stringResource(item.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
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

    StepHeader(
        icon = Icons.Filled.Lock,
        title = stringResource(ResR.string.onboarding_permissions_title),
        desc = stringResource(ResR.string.onboarding_permissions_desc),
    )
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
    Spacer(modifier = Modifier.height(16.dp))
    if (allGranted) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppText(
                text = stringResource(ResR.string.onboarding_permission_all_granted),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        AppPrimaryButton(
            text = stringResource(ResR.string.onboarding_permission_grant),
            onClick = { launcher.launch(allPerms.toTypedArray()) },
        )
    }
}

@Composable
private fun PermissionRow(entry: PermissionEntry, granted: Boolean) {
    val motion = rememberAppMotion()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = stringResource(entry.labelRes),
                style = MaterialTheme.typography.titleMedium,
            )
            AppText(
                text = stringResource(entry.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val statusColor by animateColorAsState(
            targetValue = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            animationSpec = motion.effectSpec(),
            label = "permissionStatusColor",
        )
        AppText(
            text = stringResource(
                if (granted) {
                    ResR.string.onboarding_permission_granted
                } else {
                    ResR.string.onboarding_permission_not_granted
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
        )
    }
}

@Composable
private fun SeedColorContent(
    seedArgb: Long,
    onSeedSelected: (SeedOption) -> Unit,
) {
    val motion = rememberAppMotion()
    StepHeader(
        icon = Icons.Filled.Palette,
        title = stringResource(ResR.string.onboarding_seed_title),
        desc = stringResource(ResR.string.onboarding_seed_desc),
    )
    val swatchSize = 64.dp
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SeedOption.entries.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { option ->
                    val selected = seedArgb == option.color
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.08f else 1f,
                        animationSpec = motion.bouncySpec(),
                        label = "seedScale",
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(swatchSize)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(CircleShape)
                                .background(Color(option.color))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
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
                        Spacer(modifier = Modifier.height(6.dp))
                        AppText(
                            text = stringResource(seedName(option)),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleContent(
    cornerStyle: CornerStyle,
    cornerStrength: Float,
    motion: MotionPreset,
    onCornerStyleSelected: (CornerStyle) -> Unit,
    onCornerStrengthChange: (Float) -> Unit,
    onMotionSelected: (MotionPreset) -> Unit,
) {
    StepHeader(
        icon = Icons.Filled.Brush,
        title = stringResource(ResR.string.onboarding_style_title),
        desc = stringResource(ResR.string.onboarding_style_desc),
    )
    AppText(
        text = stringResource(ResR.string.onboarding_label_shape),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(10.dp))
    val previewSize = 56.dp
    val previewBase = 18.dp
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            width = if (isSelected) 2.dp else 1.dp,
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
    Spacer(modifier = Modifier.height(20.dp))
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
    Spacer(modifier = Modifier.height(16.dp))
    AppText(
        text = stringResource(ResR.string.onboarding_label_motion),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(8.dp))
    AppSegmentedRow(
        options = MotionPreset.entries.map { stringResource(motionLabel(it)) },
        selectedIndex = MotionPreset.entries.indexOf(motion),
        onSelect = { onMotionSelected(MotionPreset.entries[it]) },
    )
}

@Composable
private fun InterfaceContent(
    darkTheme: Boolean,
    outlineEnabled: Boolean,
    outlineWidthDp: Float,
    surfaceAlpha: Float,
    onDarkChange: (Boolean) -> Unit,
    onOutlineChange: (Boolean) -> Unit,
    onOutlineWidthChange: (Float) -> Unit,
    onSurfaceAlphaChange: (Float) -> Unit,
) {
    StepHeader(
        icon = Icons.Filled.Tune,
        title = stringResource(ResR.string.onboarding_interface_title),
        desc = stringResource(ResR.string.onboarding_interface_desc),
    )
    SettingsSwitchRow(
        icon = Icons.Filled.DarkMode,
        label = stringResource(ResR.string.onboarding_label_dark_theme),
        checked = darkTheme,
        onCheckedChange = onDarkChange,
    )
    SettingsSwitchRow(
        icon = Icons.Filled.Brush,
        label = stringResource(ResR.string.onboarding_label_outline),
        checked = outlineEnabled,
        onCheckedChange = onOutlineChange,
    )
    if (outlineEnabled) {
        LabeledSlider(
            label = stringResource(ResR.string.onboarding_label_outline_width),
            valueLabel = "%.1f dp".format(outlineWidthDp),
            value = outlineWidthDp,
            valueRange = OUTLINE_WIDTH_MIN_DP..OUTLINE_WIDTH_MAX_DP,
            onValueChange = onOutlineWidthChange,
        )
    }
    LabeledSlider(
        label = stringResource(ResR.string.onboarding_label_surface_alpha),
        valueLabel = "${(surfaceAlpha * 100).toInt()}%",
        value = surfaceAlpha,
        valueRange = SURFACE_ALPHA_MIN..1f,
        onValueChange = onSurfaceAlphaChange,
    )
}

@Composable
private fun AtmosphereContent(
    snowfallEnabled: Boolean,
    particleType: ParticleType,
    particleSpeed: Float,
    onSnowfallChange: (Boolean) -> Unit,
    onParticleTypeChange: (ParticleType) -> Unit,
    onParticleSpeedChange: (Float) -> Unit,
) {
    StepHeader(
        icon = Icons.Filled.AutoAwesome,
        title = stringResource(ResR.string.onboarding_atmosphere_title),
        desc = stringResource(ResR.string.onboarding_atmosphere_desc),
    )
    SettingsSwitchRow(
        icon = Icons.Filled.AutoAwesome,
        label = stringResource(ResR.string.onboarding_label_snowfall),
        checked = snowfallEnabled,
        onCheckedChange = onSnowfallChange,
    )
    if (snowfallEnabled) {
        Spacer(modifier = Modifier.height(8.dp))
        AppText(
            text = stringResource(ResR.string.onboarding_label_particle_type),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppSegmentedRow(
            options = ParticleType.entries.map { stringResource(particleLabel(it)) },
            selectedIndex = ParticleType.entries.indexOf(particleType),
            onSelect = { onParticleTypeChange(ParticleType.entries[it]) },
        )
        LabeledSlider(
            label = stringResource(ResR.string.onboarding_label_particle_speed),
            valueLabel = "%.1fx".format(particleSpeed),
            value = particleSpeed,
            valueRange = PARTICLE_SPEED_MIN..PARTICLE_SPEED_MAX,
            onValueChange = onParticleSpeedChange,
        )
    } else {
        Spacer(modifier = Modifier.height(8.dp))
        AppText(
            text = stringResource(ResR.string.onboarding_atmosphere_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        AppText(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            AppText(text = label, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            AppText(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
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
    StepHeader(
        icon = Icons.Filled.Store,
        title = stringResource(ResR.string.onboarding_pixiv_title),
        desc = stringResource(ResR.string.onboarding_pixiv_desc),
    )
    if (isLoggedIn) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(ResR.string.onboarding_pixiv_logged_in),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppText(
                text = stringResource(ResR.string.onboarding_pixiv_logged_in_as, userName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        AppText(
            text = stringResource(ResR.string.onboarding_pixiv_not_logged),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(12.dp))
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
            Spacer(modifier = Modifier.height(12.dp))
            AppText(
                text = stringResource(ResR.string.pixiv_login_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    AppText(
        text = stringResource(ResR.string.onboarding_pixiv_optional),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FinishContent(
    themeState: ThemeState,
    darkTheme: Boolean,
    outlineEnabled: Boolean,
    snowfallEnabled: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onOutlineChange: (Boolean) -> Unit,
    onSnowfallChange: (Boolean) -> Unit,
) {
    StepHeader(
        icon = Icons.Filled.Check,
        title = stringResource(ResR.string.onboarding_finish_title),
        desc = stringResource(ResR.string.onboarding_finish_desc),
    )
    SummaryRow(
        label = stringResource(ResR.string.onboarding_seed_title),
        value = stringResource(seedName(seedOptionOf(themeState.seedArgb))),
        color = Color(themeState.seedArgb),
    )
    SummaryRow(
        label = stringResource(ResR.string.onboarding_label_dark_theme),
        value = if (darkTheme) "开启" else "关闭",
    )
    SummaryRow(
        label = stringResource(ResR.string.onboarding_label_shape),
        value = stringResource(cornerStyleName(themeState.cornerStyle)),
    )
    SummaryRow(
        label = stringResource(ResR.string.onboarding_label_motion),
        value = stringResource(motionLabel(themeState.motionPreset)),
    )
    SummaryRow(
        label = stringResource(ResR.string.onboarding_label_outline),
        value = if (outlineEnabled) "开启" else "关闭",
    )
    SummaryRow(
        label = stringResource(ResR.string.onboarding_label_snowfall),
        value = if (snowfallEnabled) "开启" else "关闭",
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(modifier = Modifier.width(10.dp))
        AppText(
            text = stringResource(ResR.string.onboarding_finish_tutorial_hint),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color != null) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        AppText(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        AppText(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
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
        onOutlineWidthChange = {},
        onSurfaceAlphaChange = {},
        onSnowfallChange = {},
        onParticleTypeChange = {},
        onParticleSpeedChange = {},
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

private fun seedOptionOf(argb: Long): SeedOption =
    SeedOption.entries.firstOrNull { it.color == argb } ?: SeedOption.Orange

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

private fun particleLabel(type: ParticleType): Int = when (type) {
    ParticleType.Snow -> ResR.string.onboarding_particle_snow
    ParticleType.Rain -> ResR.string.onboarding_particle_rain
    ParticleType.Leaf -> ResR.string.onboarding_particle_leaf
    ParticleType.Petal -> ResR.string.onboarding_particle_petal
}

private fun isLight(argb: Long): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    return luminance > 0.6
}
