package com.pencil.chuangyin.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.theme.LiquidGlassLayer
import com.pencil.chuangyin.core.theme.LocalLiquidGlassLayer

/**
 * # 液态玻璃容器层适配框架
 *
 * 把「容器玻璃」从**三件套样板**收敛成**语义化容器**：新页面只要选一个容器，
 * 不必再手写
 *
 * ```kotlin
 * Modifier.nativeGlassSurface(
 *     shape = ...,
 *     enabled = rememberLiquidGlassActive(),
 *     fallbackColor = MaterialTheme.colorScheme.surface,
 * )
 * ```
 *
 * 并按约定给容器设 `containerColor = Color.Transparent`。
 *
 * 每个容器都保证以下四点，这也是新增页面最容易踩的坑：
 * 1. **自动判定可用性**：[enabled] 默认取 [rememberLiquidGlassActive]，独立弹窗窗口内自动
 *    回退到 Compose 绘制（原生覆盖层挂在 Activity 窗口上，覆盖不到独立窗口）；
 * 2. **回退底色必填**：玻璃关闭/不可用时铺 [fallbackColor]，避免出现「透明空洞」；
 * 3. **底色透明**：玻璃生效时容器自身不画底，否则会盖住玻璃面（各容器已内置处理）；
 * 4. **层级自动**：通过 [LocalLiquidGlassLayer] 继承父级层级，覆盖层（抽屉/弹窗）内的容器
 *    无需逐个调层级即可压住导航栏。
 *
 * 与控件级玻璃（`AppButton` 等）的关系：控件级走 `nativeGlassWidget`（会生成原生交互与
 * 命中），本文件的容器只画「玻璃底」（`LiquidGlassWidgetKind.Panel`，不参与命中），
 * 内容与交互仍由 Compose 负责——两者可任意嵌套。
 */

/**
 * 通用玻璃容器：一块圆角玻璃底 + 可选内边距。
 *
 * 适用于卡片、侧边栏、面板、预览区等任意容器。玻璃面覆盖**整个 [modifier] 尺寸**
 * （内边距只影响内容，不影响玻璃面），因此无需再额外套 `background()`。
 *
 * ```kotlin
 * GlassPanel(shape = MaterialTheme.shapes.large) {
 *     Text("内容")
 * }
 * ```
 *
 * @param shape 圆角形状；仅 [RoundedCornerShape] 能换算成原生圆角，其余形状按直角处理
 *        （见 `makePanelStyle`）。全宽栏请用 [GlassBarSurface]，它会强制直角。
 * @param enabled 是否允许使用液态玻璃，默认按全局开关与渲染器能力自动判定。
 * @param fallbackColor 玻璃不可用时的回退底色；默认 `surfaceContainer`（与 M3 卡片底一致）。
 * @param layer 原生层级下限；null = 继承父级（[LocalLiquidGlassLayer]）。
 *        需要让面板压住低层 UI 时传 [LiquidGlassLayer.OVERLAY] / [LiquidGlassLayer.MODAL]。
 * @param contentPadding 内容内边距。玻璃面本身不受影响。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    enabled: Boolean = rememberLiquidGlassActive(),
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    layer: Int? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    // 层级取「父级」与「显式要求」的较大者：容器只会被抬到指定层级之上，
    // 不会被内层容器意外降级（例如弹窗内再放一个默认面板，仍应处于 MODAL 层）。
    val inherited = LocalLiquidGlassLayer.current
    val effectiveLayer = if (layer == null) inherited else maxOf(inherited, layer)
    LiquidGlassLayerScope(effectiveLayer) {
        Box(
            modifier = modifier
                // 顺序关键：玻璃面必须先于 padding，否则玻璃只覆盖内容区、内边距处透空。
                .nativeGlassSurface(shape = shape, enabled = enabled, fallbackColor = fallbackColor)
                .padding(contentPadding),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

/**
 * 玻璃卡片：圆角 + 16dp 内边距 + 纵向排布的常用组合（等于
 * `GlassPanel(shape = large, contentPadding = 16.dp)` 的语义别名）。
 *
 * ```kotlin
 * GlassCard {
 *     AppText("标题")
 *     AppButton(...)
 * }
 * ```
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    enabled: Boolean = rememberLiquidGlassActive(),
    shape: Shape = MaterialTheme.shapes.large,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    layer: Int? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassPanel(
        modifier = modifier,
        shape = shape,
        enabled = enabled,
        fallbackColor = fallbackColor,
        layer = layer,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * 玻璃**栏**底：顶层栏 / 底部栏 / 子导航等**全宽**容器的底座。
 *
 * 与 [GlassPanel] 的差别只有一点，但很关键：**强制直角**（`RoundedCornerShape(0.dp)`）。
 * 原生层把「半径 0」当作直角矩形处理；若把全宽栏写成胶囊，两侧会出现半圆并触发透镜/色散
 * 扭曲（见 `Shape.cornerRadiusPx` 的注释）。
 *
 * 层级默认为「至少 [LiquidGlassLayer.BAR]」，使栏内的玻璃控件自动压在常规内容之上；
 * 位于抽屉/弹窗内时仍继承更高层级（取较大者）。
 *
 * @param enabled 默认取 [rememberLiquidGlassActive]；栏属于容器玻璃，若希望栏底不受
 *        「控件玻璃」开关影响，请显式传 [rememberLiquidGlassPanelActive]。
 */
@Composable
fun GlassBarSurface(
    modifier: Modifier = Modifier,
    enabled: Boolean = rememberLiquidGlassActive(),
    fallbackColor: Color = MaterialTheme.colorScheme.surface,
    layer: Int = LiquidGlassLayer.BAR,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(0.dp),
        enabled = enabled,
        fallbackColor = fallbackColor,
        layer = layer,
        content = content,
    )
}

/**
 * 玻璃顶栏：把「M3 [TopAppBar] + 透明容器色 + 直角玻璃底 + BAR 层级」这套固定组合一次配好。
 *
 * ```kotlin
 * GlassTopAppBar(
 *     title = { AppText("设置") },
 *     navigationIcon = { AppIconButton(onClick = onBack, imageVector = ..., ...) },
 *     actions = { ... },
 * )
 * ```
 *
 * `containerColor` 恒为 `Transparent`：玻璃生效时由原生层画底，关闭时由 [fallbackColor] 兜底，
 * 因此调用方**不要**再自行传 `colors`。
 *
 * @param enabled 默认取 [rememberLiquidGlassActive]。
 * @param fallbackColor 玻璃不可用时的容器色，默认 `surface`（与默认 `TopAppBar` 一致）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    enabled: Boolean = rememberLiquidGlassActive(),
    fallbackColor: Color = MaterialTheme.colorScheme.surface,
) {
    // 栏属于常驻 UI：至少 BAR 层，保证栏内控件压在内容之上；处于覆盖层时继承更高层级。
    val inherited = LocalLiquidGlassLayer.current
    LiquidGlassLayerScope(maxOf(inherited, LiquidGlassLayer.BAR)) {
        TopAppBar(
            title = title,
            modifier = modifier.nativeGlassSurface(
                shape = RoundedCornerShape(0.dp),
                enabled = enabled,
                fallbackColor = fallbackColor,
            ),
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            scrollBehavior = scrollBehavior,
        )
    }
}
