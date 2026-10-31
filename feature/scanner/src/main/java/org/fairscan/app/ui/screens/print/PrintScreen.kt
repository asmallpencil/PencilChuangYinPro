/*
 * Copyright 2025-2026 The FairScan authors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.fairscan.app.ui.screens.print

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.fairscan.app.R
import org.fairscan.app.THUMBNAIL_SIZE_DP
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.components.AppOverflowMenu
import org.fairscan.app.ui.components.BackButton
import org.fairscan.app.ui.components.MainActionButton
import org.fairscan.app.ui.components.NewDocumentDialog
import org.fairscan.app.ui.components.pageCountText
import org.fairscan.app.ui.state.DocumentUiModel

/**
 * 打印页（FairScan 导出页的替代）：缩略图 + 页数 + 一个「打印」主按钮。
 * 点击打印把各页 JPEG 交给图片打印页，并直接跳转过去。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintScreen(
    document: DocumentUiModel,
    isPreparing: Boolean,
    hasError: Boolean,
    navigation: Navigation,
    onPrint: () -> Unit,
    onCloseScan: () -> Unit,
) {
    BackHandler { navigation.back() }
    val showConfirmationDialog = remember { mutableStateOf(false) }
    val pageCount = document.pageCount()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.print)) },
                navigationIcon = { BackButton(navigation.back) },
                actions = { AppOverflowMenu(navigation) },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val thumbnail = document.thumbnail(0)
                    thumbnail?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .heightIn(max = THUMBNAIL_SIZE_DP.dp)
                                .widthIn(max = THUMBNAIL_SIZE_DP.dp)
                                .clickable { navigation.toDocumentScreen() },
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(pageCountText(pageCount))
                        if (isPreparing) {
                            Text(
                                text = stringResource(R.string.creating_export),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.padding(top = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        if (hasError) {
                            Text(
                                text = stringResource(R.string.error_occurred),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            MainActionButton(
                onClick = {
                    if (!isPreparing) onPrint()
                },
                enabled = pageCount > 0 && !isPreparing,
                text = stringResource(R.string.print),
                icon = Icons.Default.Print,
                modifier = Modifier.fillMaxWidth(),
            )

            MainActionButton(
                onClick = { showConfirmationDialog.value = true },
                enabled = !isPreparing,
                text = stringResource(R.string.scan_new),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showConfirmationDialog.value) {
            NewDocumentDialog(
                onConfirm = onCloseScan,
                showDialog = showConfirmationDialog,
                title = stringResource(R.string.scan_new),
            )
        }
    }
}
