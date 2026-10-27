package com.pencil.chuangyin.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pencil.chuangyin.core.ui.components.AppIconButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.core.ui.components.AppTextField
import com.pencil.chuangyin.core.ui.components.nativeGlassSurface
import com.pencil.chuangyin.core.ui.components.rememberLiquidGlassActive
import com.pencil.chuangyin.core.ui.motion.animateItemMotion
import com.pencil.chuangyin.core.ui.motion.pressScale
import com.pencil.chuangyin.core.ui.motion.rememberAppMotion
import com.pencil.chuangyin.resources.R as ResR

@Composable
fun LicensesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LicensesScreen(onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val glassActive = rememberLiquidGlassActive()
    val motion = rememberAppMotion()
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(query) {
        if (query.isBlank()) {
            OPEN_SOURCE_PROJECTS
        } else {
            // 项目名 / 作者 / 许可证 / 贡献说明 都参与匹配，找起来不用猜字段。
            val keyword = query.trim()
            OPEN_SOURCE_PROJECTS.filter { project ->
                project.name.contains(keyword, ignoreCase = true) ||
                    project.author.contains(keyword, ignoreCase = true) ||
                    project.license.contains(keyword, ignoreCase = true) ||
                    project.contribution.contains(keyword, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { AppText(text = stringResource(R.string.settings_licenses_title)) },
                navigationIcon = {
                    AppIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(ResR.string.label_back),
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimensionResource(ResR.dimen.dimen_global_padding_m),
                end = dimensionResource(ResR.dimen.dimen_global_padding_m),
                bottom = dimensionResource(ResR.dimen.dimen_global_padding_xl),
            ),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(ResR.dimen.dimen_global_padding_s)),
        ) {
            item(key = "intro") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_l)),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    AppText(
                        text = stringResource(R.string.settings_licenses_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
                    )
                }
            }
            item(key = "search") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.settings_licenses_search),
                    )
                    AppText(
                        text = if (query.isBlank()) {
                            stringResource(
                                R.string.settings_licenses_count,
                                OPEN_SOURCE_PROJECTS.size,
                            )
                        } else {
                            stringResource(
                                R.string.settings_licenses_count_filtered,
                                filtered.size,
                                OPEN_SOURCE_PROJECTS.size,
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = dimensionResource(ResR.dimen.dimen_global_padding_s),
                            top = dimensionResource(ResR.dimen.dimen_global_padding_s),
                        ),
                    )
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimensionResource(ResR.dimen.dimen_global_padding_xl)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            dimensionResource(ResR.dimen.dimen_global_padding_s),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(dimensionResource(ResR.dimen.dimen_global_touch_target)),
                        )
                        AppText(
                            text = stringResource(R.string.settings_licenses_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            align = TextAlign.Center,
                        )
                    }
                }
            } else {
                items(items = filtered, key = { it.name }) { project ->
                    LicenseRow(
                        project = project,
                        modifier = animateItemMotion(motion),
                        onOpen = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(project.url)),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseRow(
    project: OpenSourceProject,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_l)),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onOpen,
                )
                .padding(dimensionResource(ResR.dimen.dimen_global_padding_m)),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(
                dimensionResource(ResR.dimen.dimen_global_padding_m),
            ),
        ) {
            Box(
                modifier = Modifier
                    .size(dimensionResource(ResR.dimen.dimen_global_touch_target) - 8.dp)
                    .clip(RoundedCornerShape(dimensionResource(ResR.dimen.dimen_shape_corner_m)))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = project.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                AppText(
                    text = "${project.author} · ${project.license}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                AppText(
                    text = project.contribution,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_s)),
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
