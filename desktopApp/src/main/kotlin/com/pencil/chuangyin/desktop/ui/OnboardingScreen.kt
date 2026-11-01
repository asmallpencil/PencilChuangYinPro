package com.pencil.chuangyin.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pencil.chuangyin.desktop.theme.CornerStyle
import com.pencil.chuangyin.desktop.theme.DesktopThemeStore
import com.pencil.chuangyin.desktop.theme.MotionPreset
import com.pencil.chuangyin.desktop.theme.OUTLINE_WIDTH_MAX_DP
import com.pencil.chuangyin.desktop.theme.OUTLINE_WIDTH_MIN_DP
import com.pencil.chuangyin.desktop.theme.SURFACE_ALPHA_MIN
import com.pencil.chuangyin.desktop.theme.SeedOption
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView
import kotlinx.coroutines.launch

/** 引导总步数（欢迎 / 功能 / 主题色 / 形状与动效 / 界面细节 / 完成）。 */
private const val ONBOARDING_STEPS = 6

/**
 * 首次启动引导（桌面端）——与安卓端 feature/onboarding 同构：
 * 全屏渐变光斑背景 + 胶囊步骤指示器 + 左右切换的配置页 + 完成页。
 * 所有改动实时写入 [DesktopThemeStore]，与设置页共享同一份主题状态。
 */
@Composable
fun DesktopOnboarding(onFinished: () -> Unit) {
    val state by DesktopThemeStore.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_STEPS })
    val scope = rememberCoroutineScope()

    fun go(delta: Int) {
        scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + delta).coerceIn(0, ONBOARDING_STEPS - 1)) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        OnboardingBackdrop()
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconView(icon = AppIcon.Printer, size = 24.dp, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "铅笔创印Pro",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                StepDots(current = pagerState.currentPage, total = ONBOARDING_STEPS) { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "跳过",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onFinished)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f).widthIn(max = 960.dp),
                key = { it },
            ) { page ->
                val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            alpha = (1f - 0.35f * kotlin.math.abs(offset)).coerceIn(0f, 1f)
                            translationY = offset * 56f
                            scaleX = 1f - 0.04f * kotlin.math.abs(offset)
                            scaleY = 1f - 0.04f * kotlin.math.abs(offset)
                        },
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(32.dp),
                    ) {
                        when (page) {
                            0 -> WelcomeStep()
                            1 -> FeaturesStep()
                            2 -> SeedColorStep(state.seedArgb) { color ->
                                DesktopThemeStore.update { it.copy(seedArgb = color) }
                            }
                            3 -> StyleStep(
                                cornerStyle = state.cornerStyle,
                                motionPreset = state.motionPreset,
                                onCornerChange = { style ->
                                    DesktopThemeStore.update { it.copy(cornerStyle = style) }
                                },
                                onMotionChange = { preset ->
                                    DesktopThemeStore.update { it.copy(motionPreset = preset) }
                                },
                            )
                            4 -> InterfaceStep(
                                dark = state.darkTheme,
                                outlineEnabled = state.outlineEnabled,
                                outlineWidth = state.outlineWidthDp,
                                surfaceAlpha = state.surfaceAlpha,
                                onDarkChange = { on -> DesktopThemeStore.update { it.copy(darkTheme = on) } },
                                onOutlineChange = { on -> DesktopThemeStore.update { it.copy(outlineEnabled = on) } },
                                onOutlineWidthChange = { v -> DesktopThemeStore.update { it.copy(outlineWidthDp = v) } },
                                onSurfaceAlphaChange = { v -> DesktopThemeStore.update { it.copy(surfaceAlpha = v) } },
                            )
                            else -> FinishStep(state, onFinished)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    Text(
                        text = "上一步",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { go(-1) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    modifier = Modifier
                        .width(168.dp)
                        .height(46.dp)
                        .clickable {
                            if (pagerState.currentPage == ONBOARDING_STEPS - 1) onFinished() else go(1)
                        },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (pagerState.currentPage == ONBOARDING_STEPS - 1) "开始使用" else "下一步",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

 /** 缓慢漂移的主题色光斑背景。 */
@Composable
private fun OnboardingBackdrop() {
    val seed = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
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
    Canvas(Modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(listOf(surfaceTop, surfaceBottom)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(seed.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * (0.10f + 0.08f * t)),
                radius = size.minDimension * 0.95f,
            ),
            radius = size.minDimension * 0.95f,
            center = Offset(size.width * 0.88f, size.height * (0.10f + 0.08f * t)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tertiary.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.08f, size.height * (0.62f - 0.10f * t)),
                radius = size.minDimension * 0.8f,
            ),
            radius = size.minDimension * 0.8f,
            center = Offset(size.width * 0.08f, size.height * (0.62f - 0.10f * t)),
        )
    }
}

@Composable
private fun StepDots(current: Int, total: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            val active = index == current
            val width by animateFloatAsState(
                targetValue = if (active) 26f else 8f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 620f),
                label = "stepDotWidth",
            )
            val color by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                },
                animationSpec = tween(260),
                label = "stepDotColor",
            )
            Box(
                modifier = Modifier
                    .width(width.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onSelect(index) },
            )
        }
    }
}

@Composable
private fun StepTitle(title: String, desc: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = desc,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun WelcomeStep() {
    val pulse = rememberInfiniteTransition(label = "welcomePulse")
    val glow by pulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "welcomeGlow",
    )
    Box(
        modifier = Modifier
            .size(108.dp)
            .background(
                Brush.radialGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f * glow),
                        Color.Transparent,
                    )
                ),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                AppIconView(icon = AppIcon.Printer, size = 36.dp, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(
        text = "欢迎使用铅笔创印Pro",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "一台蓝牙热敏打印机，一个能排版文字、处理图片、连接 P 站的创作台。接下来用几步把界面调成你喜欢的样子。",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FeaturePill("本地处理", "图片与文稿都在本机完成")
        FeaturePill("即连即印", "连上打印机直接出纸")
        FeaturePill("自由定制", "主题、形状、动效全可调")
    }
}

@Composable
private fun FeaturePill(title: String, desc: String) {
    Column(
        modifier = Modifier
            .widthIn(max = 220.dp)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            desc,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun FeaturesStep() {
    StepTitle("这些能力都在本地完成", "桌面端不做任何云端上传：图片二值化、文稿排版、P 站下载都保存在你自己的电脑上。")
    FeatureRow(AppIcon.TextTool, "文本打印", "标题/正文排版、字体导入、打印预览与批量份数。")
    FeatureRow(AppIcon.ImageTool, "图片打印", "相册多图、PDF、压缩包导入，抖动算法与逐页参数。")
    FeatureRow(AppIcon.AiTool, "AI 打印", "让 AI 生成内容或表格，直接进入打印流程。")
    FeatureRow(AppIcon.Store, "P 站图库", "浏览、下载已保存的图片，一键转入图片打印。")
}

@Composable
private fun FeatureRow(icon: AppIcon, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(icon = icon, size = 24.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SeedColorStep(seedArgb: Long, onSelect: (Long) -> Unit) {
    StepTitle("选一个主色", "主色会应用到按钮、图标、描边与高亮，随时可以在设置里更改。")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SeedOption.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { option ->
                    val selected = seedArgb == option.color
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.06f else 1f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
                        label = "seedScale",
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .background(
                                if (selected) {
                                    Color(option.color).copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                RoundedCornerShape(18.dp),
                            )
                            .clickable { onSelect(option.color) }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(option.color)))
                        Spacer(Modifier.width(10.dp))
                        Text(option.label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleStep(
    cornerStyle: CornerStyle,
    motionPreset: MotionPreset,
    onCornerChange: (CornerStyle) -> Unit,
    onMotionChange: (MotionPreset) -> Unit,
) {
    StepTitle("决定界面的形状与节奏", "形状影响卡片与按钮的轮廓，动效预设决定动画的速度与幅度。")
    Text("边角形状", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    OptionGrid(
        options = CornerStyle.entries.map { it.name to it.label() },
        selected = cornerStyle.name,
        onSelect = { index -> onCornerChange(CornerStyle.entries[index]) },
    )
    Spacer(Modifier.height(20.dp))
    Text("动效预设", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    OptionGrid(
        options = MotionPreset.entries.map { it.name to it.label() },
        selected = motionPreset.name,
        onSelect = { index -> onMotionChange(MotionPreset.entries[index]) },
    )
}

private fun CornerStyle.label(): String = when (this) {
    CornerStyle.Rounded -> "圆角"
    CornerStyle.Square -> "直角"
    CornerStyle.Diamond -> "切角"
    CornerStyle.Parallelogram -> "斜切"
}

private fun MotionPreset.label(): String = when (this) {
    MotionPreset.Full -> "完整"
    MotionPreset.Expressive -> "表现力"
    MotionPreset.Reduced -> "克制"
}

@Composable
private fun OptionGrid(options: List<Pair<String, String>>, selected: String, onSelect: (Int) -> Unit) {
    options.chunked(2).forEachIndexed { rowIndex, row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEachIndexed { colIndex, (key, label) ->
                val index = rowIndex * 2 + colIndex
                val active = key == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            RoundedCornerShape(16.dp),
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InterfaceStep(
    dark: Boolean,
    outlineEnabled: Boolean,
    outlineWidth: Float,
    surfaceAlpha: Float,
    onDarkChange: (Boolean) -> Unit,
    onOutlineChange: (Boolean) -> Unit,
    onOutlineWidthChange: (Float) -> Unit,
    onSurfaceAlphaChange: (Float) -> Unit,
) {
    StepTitle("一些细节偏好", "深色模式、控件描边与面板透明度都会立刻生效，之后还能在设置里微调。")
    ToggleLine("深色模式", "夜间或暗环境下更舒服的配色", dark, onDarkChange)
    ToggleLine("控件描边", "给卡片与按钮加一圈主题色细边", outlineEnabled, onOutlineChange)
    if (outlineEnabled) {
        SliderLine(
            label = "描边粗细",
            value = outlineWidth,
            range = OUTLINE_WIDTH_MIN_DP..OUTLINE_WIDTH_MAX_DP,
            valueText = "%.1f dp".format(outlineWidth),
            onChange = onOutlineWidthChange,
        )
    }
    SliderLine(
        label = "面板不透明度",
        value = surfaceAlpha,
        range = SURFACE_ALPHA_MIN..1f,
        valueText = "%.2f".format(surfaceAlpha),
        onChange = onSurfaceAlphaChange,
    )
}

@Composable
private fun ToggleLine(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderLine(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun FinishStep(
    state: com.pencil.chuangyin.desktop.theme.ThemeState,
    onFinished: () -> Unit,
) {
    StepTitle("已经就绪", "最后还会有一层新手引导，带你在真实的首页、图片打印与文本打印页里认一遍按钮。")
    SummaryLine("主题色", SeedOption.entries.firstOrNull { it.color == state.seedArgb }?.label ?: "自定义")
    SummaryLine("外观", if (state.darkTheme) "深色" else "浅色")
    SummaryLine("边角", state.cornerStyle.label())
    SummaryLine("动效", state.motionPreset.label())
    SummaryLine("描边", if (state.outlineEnabled) "开启" else "关闭")
    Spacer(Modifier.height(20.dp))
    Text(
        text = "点击右下角「开始使用」进入首页。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Surface(
        modifier = Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onFinished),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "开始使用",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
