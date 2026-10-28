package com.pencil.chuangyin.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppSlider
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.printer.PAPER_WIDTH_MM_MAX
import com.pencil.chuangyin.printer.PAPER_WIDTH_MM_MIN
import com.pencil.chuangyin.resources.R as ResR

@Composable
fun SizeSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SizeSettingsScreen(
        paperWidthMm = state.paperWidthMm,
        onPaperWidthChange = viewModel::setPaperWidthMm,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeSettingsScreen(
    paperWidthMm: Int,
    onPaperWidthChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassActive = rememberLiquidGlassActive()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.settings_size_title)) },
                navigationIcon = {
                    AppIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(ResR.string.label_back),
                        // 设置子页返回按钮与主设置页一致：保持实心，不做玻璃。
                        glass = false,
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
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
        ) {
            AppText(
                text = stringResource(R.string.settings_label_paper_width),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(dimensionResource(ResR.dimen.dimen_global_padding_s)))
            AppText(
                text = stringResource(R.string.settings_paper_width_value, paperWidthMm),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            AppSlider(
                value = paperWidthMm.toFloat(),
                onValueChange = { onPaperWidthChange(it.toInt()) },
                valueRange = PAPER_WIDTH_MM_MIN.toFloat()..PAPER_WIDTH_MM_MAX.toFloat(),
                steps = PAPER_WIDTH_MM_MAX - PAPER_WIDTH_MM_MIN - 1,
                modifier = Modifier.fillMaxWidth(),
            )
            AppText(
                text = stringResource(R.string.settings_paper_width_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
