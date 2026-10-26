package com.pencil.chuangyin.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.desktop.ui.icons.AppIcon
import com.pencil.chuangyin.desktop.ui.icons.AppIconView

/**
 * 创意页 —— 与安卓端 app/ui/CreativeScreen.kt 完全一致的占位形态（文案取 core/resources）：
 * 顶部玻璃应用栏（标题「创意工坊」+ 设置），内容区居中一个创意图标与说明文案。
 */
@Composable
fun CreativeScreen(
    bottomBarSpace: Dp,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = "创意工坊",
                actions = {
                    AppIconButton(
                        icon = AppIcon.Settings,
                        contentDescription = "设置",
                        onClick = onOpenSettings,
                    )
                },
            )
        },
        containerColor = Color.Transparent,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(bottom = bottomBarSpace)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIconView(
                    icon = AppIcon.Creative,
                    size = 48.dp,
                    tint = MaterialTheme.colorScheme.primaryContainer,
                )
                Text(
                    text = "创意工坊",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "灵感功能筹备中，敬请期待",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
