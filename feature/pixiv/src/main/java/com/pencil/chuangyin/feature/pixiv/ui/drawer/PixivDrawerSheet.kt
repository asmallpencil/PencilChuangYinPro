package com.pencil.chuangyin.feature.pixiv.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.theme.LocalLiquidGlassControlsEnabled
import com.pencil.chuangyin.core.ui.components.AppDivider
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.feature.pixiv.core.auth.PixivAccount
import com.pencil.chuangyin.feature.pixiv.ui.common.PixivCoverImage
import com.pencil.chuangyin.resources.R as ResR

/**
 * 侧边栏可跳转目标（清除缓存统一收敛到「运行缓存」页处理，不在此处做即时动作）。
 * 小说已并入浏览页的第三内容模式（插画/漫画/小说同级），不再单独占侧边栏一项；
 * 应用设置/主题设置已移除，改为 P 站自己的「功能设置」（SETTINGS）。
 */
enum class PixivDrawerDestination {
    ACCOUNT,
    WALKTHROUGH,
    BROWSER,
    SOURCES,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
    GALLERY,
    BLOCKED_TAGS,
    CACHE,
    SETTINGS,
}

private data class DrawerItem(
    val destination: PixivDrawerDestination,
    val icon: ImageVector,
    val label: String,
)

/**
 * P 站侧边栏：头部用户区 + 主功能 + 分隔 + 本地管理（图片管理/屏蔽标签/运行缓存）。
 *
 * @param sheetWidth 抽屉宽度（调用方按屏幕宽度 * 0.75 传入，最多占 75%，REQ5）。
 * @param onClose 头部折叠按钮回调（REQ3）。
 * @param bottomBarSpace 底部悬浮导航栏占用的总高度，用于给最下方条目留白，
 *                       避免被悬浮栏遮挡（REQ4）。
 */
@Composable
fun PixivDrawerSheet(
    account: PixivAccount?,
    onDestination: (PixivDrawerDestination) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    sheetWidth: Dp = Dp.Unspecified,
    bottomBarSpace: Dp = 0.dp,
) {
    // 不使用 ModalDrawerSheet：其固定宽度在部分大屏设备几乎占满整屏。
    // 宽度显式由调用方约束为屏幕的 75%；内容整体可纵向滚动，保证短屏底部条目可达。
    // 液态玻璃开启时，整块侧边栏的**底色由 C++ 原生玻璃层绘制**（不是 Kotlin 伪玻璃），
    // 因此内容不再套不透明 Surface，否则会盖住原生玻璃。
    val glassActive = LocalLiquidGlassControlsEnabled.current
    val sheetShape = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .fillMaxHeight()
            .then(if (sheetWidth != Dp.Unspecified) Modifier.width(sheetWidth) else Modifier.fillMaxWidth())
            .nativeGlassSurface(
                shape = sheetShape,
                enabled = glassActive,
                fallbackColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            DrawerHeader(account = account, onClose = onClose)

            val primaryItems = listOf(
                DrawerItem(
                    PixivDrawerDestination.ACCOUNT,
                    Icons.Filled.ManageAccounts,
                    stringResource(ResR.string.pixiv_drawer_account),
                ),
                DrawerItem(
                    PixivDrawerDestination.WALKTHROUGH,
                    Icons.Filled.Slideshow,
                    stringResource(ResR.string.pixiv_drawer_walkthrough),
                ),
                DrawerItem(
                    PixivDrawerDestination.BROWSER,
                    Icons.Filled.Language,
                    stringResource(ResR.string.pixiv_drawer_browser),
                ),
                DrawerItem(
                    PixivDrawerDestination.SOURCES,
                    Icons.Filled.ImageSearch,
                    stringResource(ResR.string.pixiv_drawer_sources),
                ),
                DrawerItem(
                    PixivDrawerDestination.BOOKMARKS,
                    Icons.Filled.Bookmark,
                    stringResource(ResR.string.pixiv_drawer_bookmarks),
                ),
                DrawerItem(
                    PixivDrawerDestination.HISTORY,
                    Icons.Filled.History,
                    stringResource(ResR.string.pixiv_drawer_history),
                ),
                DrawerItem(
                    PixivDrawerDestination.DOWNLOADS,
                    Icons.Filled.Download,
                    stringResource(ResR.string.pixiv_drawer_downloads),
                ),
                DrawerItem(
                    // 应用设置 / 主题设置已移除，P 站独立的功能设置（仿原版可落地项）。
                    PixivDrawerDestination.SETTINGS,
                    Icons.Filled.Settings,
                    stringResource(ResR.string.pixiv_drawer_functional_settings),
                ),
            )
            primaryItems.forEach { item ->
                DrawerRow(icon = item.icon, label = item.label) { onDestination(item.destination) }
            }

            AppDivider(
                modifier = Modifier.padding(
                    horizontal = dimensionResource(ResR.dimen.dimen_global_padding_m),
                ),
            )

            val secondaryItems = listOf(
                DrawerItem(
                    PixivDrawerDestination.GALLERY,
                    Icons.Filled.Collections,
                    stringResource(ResR.string.pixiv_drawer_gallery),
                ),
                DrawerItem(
                    PixivDrawerDestination.BLOCKED_TAGS,
                    Icons.Filled.Block,
                    stringResource(ResR.string.pixiv_drawer_blocked_tags),
                ),
                DrawerItem(
                    PixivDrawerDestination.CACHE,
                    Icons.Filled.Cached,
                    stringResource(ResR.string.pixiv_drawer_cache),
                ),
            )
            secondaryItems.forEach { item ->
                DrawerRow(icon = item.icon, label = item.label) { onDestination(item.destination) }
            }
            // REQ4：底部条目之上留出悬浮导航栏高度 + 底边距，避免最下方两项被遮挡/点不到。
            Spacer(
                modifier = Modifier.height(
                    bottomBarSpace + dimensionResource(ResR.dimen.dimen_global_padding_s),
                ),
            )
        }
    }
}

@Composable
private fun DrawerHeader(account: PixivAccount?, onClose: () -> Unit) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(statusBarPadding)
            .padding(
                start = dimensionResource(ResR.dimen.dimen_global_padding_l),
                end = dimensionResource(ResR.dimen.dimen_global_padding_s),
                top = dimensionResource(ResR.dimen.dimen_global_padding_s),
                bottom = dimensionResource(ResR.dimen.dimen_global_padding_l),
            ),
    ) {
        // REQ3：显式折叠按钮（点击右侧空白遮罩也可关闭，手势在浏览页处理）。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            AppIconButton(
                onClick = onClose,
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = stringResource(ResR.string.pixiv_drawer_close),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .size(dimensionResource(ResR.dimen.dimen_pixiv_drawer_avatar_size))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (!account?.avatarUrl.isNullOrBlank()) {
                PixivCoverImage(
                    model = account?.avatarUrl,
                    contentDescription = account?.name,
                    errorIcon = Icons.Filled.Person,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AccountAvatarFallback()
            }
        }
        Spacer(modifier = Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_m)))
        AppText(
            text = account?.name.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = account?.account.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun AccountAvatarFallback() {
    Icon(
        imageVector = Icons.Filled.Person,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_pixiv_drawer_icon_size)),
    )
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(ResR.dimen.dimen_pixiv_drawer_item_height))
            .clickable(onClick = onClick)
            .padding(horizontal = dimensionResource(ResR.dimen.dimen_global_padding_l)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_pixiv_drawer_icon_size)),
        )
        Spacer(modifier = Modifier.width(dimensionResource(ResR.dimen.dimen_global_padding_l)))
        AppText(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
