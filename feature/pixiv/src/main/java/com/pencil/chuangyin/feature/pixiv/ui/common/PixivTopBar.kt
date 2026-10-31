package com.pencil.chuangyin.feature.pixiv.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.motion.pressScale
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.resources.R as ResR

/**
 * P 站顶部导航栏（主页面 / 搜索页共用的一套视觉语言）。
 *
 * 设计要点：
 * - 顶栏不铺玻璃容器，直接浮在页面背景上：左侧菜单或返回、标题、右侧操作图标；
 * - 筛选芯片为胶囊形：未选中为低透明度深色底 + 细描边，选中为强调色描边 + 强调色文字；
 * - 不同筛选组之间用 1dp 竖线分隔，整行可横向滚动（组内保持紧凑，组间留白）。
 */
data class PixivChipOption(val key: String, val label: String)

data class PixivChipGroup(val selectedKey: String, val options: List<PixivChipOption>)

/** 主页面顶栏：菜单 + 标题 + 搜索/刷新。 */
@Composable
fun PixivBrowseTopBar(
    title: String,
    showActions: Boolean,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    menuContentDescription: String,
    searchContentDescription: String,
    refreshContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(TOP_BAR_HEIGHT)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showActions) {
            AppIconButton(
                onClick = onMenu,
                imageVector = Icons.Filled.Menu,
                contentDescription = menuContentDescription,
                glass = false,
            )
        }
        AppText(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(start = if (showActions) 0.dp else 16.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showActions) {
            AppIconButton(
                onClick = onSearch,
                imageVector = Icons.Filled.Search,
                contentDescription = searchContentDescription,
                size = ACTION_ICON_SIZE,
                glass = false,
            )
            AppIconButton(
                onClick = onRefresh,
                imageVector = Icons.Filled.Refresh,
                contentDescription = refreshContentDescription,
                size = ACTION_ICON_SIZE,
                glass = false,
            )
        }
    }
}

/** 搜索页顶栏：返回 + 搜索输入框 + 圆形的搜索按钮。 */
@Composable
fun PixivSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    placeholder: String,
    backContentDescription: String,
    searchContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            onClick = onBack,
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = backContentDescription,
            size = ACTION_ICON_SIZE,
            glass = false,
        )
        PixivSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = placeholder,
            onSearch = onSearch,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        PixivSearchActionButton(
            onClick = onSearch,
            contentDescription = searchContentDescription,
            modifier = Modifier.size(ACTION_ICON_SIZE),
        )
    }
}

/** 分组筛选芯片行：组内互斥单选，组间竖线分隔，整体横向滚动。 */
@Composable
fun PixivFilterChipRow(
    groups: List<PixivChipGroup>,
    onSelect: (groupIndex: Int, key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipSpacing = dimensionResource(ResR.dimen.dimen_pixiv_chip_spacing)
    val screenPadding = dimensionResource(ResR.dimen.dimen_pixiv_grid_padding)
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = screenPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(chipSpacing),
    ) {
        itemsIndexed(groups, key = { index, _ -> "pixiv_chip_group_$index" }) { index, group ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(chipSpacing),
            ) {
                group.options.forEach { option ->
                    PixivFilterChip(
                        selected = option.key == group.selectedKey,
                        label = option.label,
                        onClick = { onSelect(index, option.key) },
                    )
                }
                if (index != groups.lastIndex) {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PixivFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = rememberAppMotion()
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        },
        animationSpec = motion.effectSpec(),
        label = "pixivChipContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
        },
        animationSpec = motion.effectSpec(),
        label = "pixivChipBorder",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motion.effectSpec(),
        label = "pixivChipLabel",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(CHIP_HEIGHT)
            .clip(CircleShape)
            .background(containerColor)
            .border(dimensionResource(ResR.dimen.dimen_global_border), borderColor, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .pressScale(interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun PixivSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SEARCH_FIELD_RADIUS)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(SEARCH_FIELD_HEIGHT),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
                    .border(
                        dimensionResource(ResR.dimen.dimen_global_border),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        shape,
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    AppText(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun PixivSearchActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .border(
                dimensionResource(ResR.dimen.dimen_global_border),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                CircleShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .pressScale(interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

private val TOP_BAR_HEIGHT = 56.dp
private val ACTION_ICON_SIZE = 44.dp
private val CHIP_HEIGHT = 40.dp
private val SEARCH_FIELD_HEIGHT = 48.dp
private val SEARCH_FIELD_RADIUS = 24.dp
