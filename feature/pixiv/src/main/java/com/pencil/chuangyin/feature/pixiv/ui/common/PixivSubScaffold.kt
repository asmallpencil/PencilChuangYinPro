package com.pencil.chuangyin.feature.pixiv.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.GlassTopAppBar
import com.pencil.chuangyin.resources.R as ResR

/** P 站各二级页统一脚手架：返回箭头 + 标题 + 可选右上动作，内容吃满内边距。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixivSubScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    navigationIcon: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            // 玻璃底 / 透明容器色 / BAR 层级由 GlassTopAppBar 统一处理。
            GlassTopAppBar(
                title = { AppText(title) },
                navigationIcon = {
                    if (navigationIcon != null) {
                        navigationIcon()
                    } else {
                        AppIconButton(
                            onClick = onBack,
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(ResR.string.label_back),
                            glass = false,
                        )
                    }
                },
                actions = { actions() },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            content(innerPadding)
        }
    }
}

/** 字节体积人类可读格式（B/KB/MB/GB），缓存/图片大小统一入口。 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= SIZE_STEP && unitIndex < units.lastIndex) {
        value /= SIZE_STEP
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${bytes} B"
    } else {
        String.format(java.util.Locale.US, "%.1f %s", value, units[unitIndex])
    }
}

private const val SIZE_STEP = 1024.0
