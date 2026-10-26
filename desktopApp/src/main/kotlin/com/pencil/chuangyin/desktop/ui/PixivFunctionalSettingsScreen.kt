package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.pixiv.PixivGraph
import com.pencil.chuangyin.desktop.pixiv.PixivMediaType
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView

/**
 * P 站功能设置（对齐安卓端 PixivSettingsScreen）：
 * 默认内容模式 + 三个开关 + 「相关设置」入口。
 */
@Composable
fun PixivFunctionalSettingsScreen(
    onBack: () -> Unit,
    onOpenBlockedTags: () -> Unit,
) {
    val prefs = PixivGraph.prefsStore
    val defaultMode by prefs.defaultMediaMode.collectAsState()
    val historyEnabled by prefs.historyEnabled.collectAsState()
    val hiRes by prefs.hiResImages.collectAsState()
    val r18Filter by prefs.r18FilterEnabled.collectAsState()

    PixivSubScaffold(title = "功能设置", onBack = onBack) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsBlockTitle("默认内容模式")

            AppSegmentedRow(
                options = listOf("插画", "漫画", "小说"),
                selectedIndex = when (defaultMode) {
                    PixivMediaType.Manga -> 1
                    PixivMediaType.Novel -> 2
                    else -> 0
                },
                onSelected = { index ->
                    prefs.setDefaultMediaMode(
                        when (index) {
                            2 -> PixivMediaType.Novel
                            1 -> PixivMediaType.Manga
                            else -> PixivMediaType.Illust
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            SettingsToggleRow(
                title = "记录浏览历史",
                desc = "关闭后浏览作品不再写入历史记录",
                checked = historyEnabled,
                onCheckedChange = prefs::setHistoryEnabled,
            )
            SettingsToggleRow(
                title = "加载高清大图",
                desc = "开启后优先加载大分辨率图片，更耗流量",
                checked = hiRes,
                onCheckedChange = prefs::setHiResImages,
            )
            SettingsToggleRow(
                title = "R18 内容过滤",
                desc = "开启后自动过滤 R-18 / R-18G 作品（推荐与排行榜、搜索结果同步生效）",
                checked = r18Filter,
                onCheckedChange = prefs::setR18FilterEnabled,
            )

            Text(
                text = "相关设置",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
            )
            NavigationRow(onClick = onOpenBlockedTags)
        }
    }
}

@Composable
private fun SettingsBlockTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NavigationRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(
            icon = AppIcon.Block,
            size = 24.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "屏蔽标签",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        )
        AppIconView(
            icon = AppIcon.ChevronRight,
            size = 24.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}
