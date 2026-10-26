package com.pencil.chuangyin.pro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.resources.R as ResR

/** 「创意」顶级页：创作工具入口已前置到主页，本页为灵感能力占位页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreativeScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBarSpace: Dp = Dp(0f),
) {
    val glassActive = rememberLiquidGlassActive()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(ResR.string.creative_title)) },
                actions = {
                    AppIconButton(
                        onClick = onOpenSettings,
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(ResR.string.label_settings),
                    )
                },
                // 顶部导航栏底部交给原生液态玻璃（与首页 / P 站同一套实现）；
                // 玻璃生效时 TopAppBar 自身容器必须透明，否则会遮住玻璃面。
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
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_l))
                .padding(bottom = bottomBarSpace),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_touch_target)),
            )
            AppText(
                text = stringResource(ResR.string.creative_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_m)),
            )
            AppText(
                text = stringResource(ResR.string.creative_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                align = TextAlign.Center,
                modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreativeScreenPreview() {
    CreativeScreen(onOpenSettings = {})
}
