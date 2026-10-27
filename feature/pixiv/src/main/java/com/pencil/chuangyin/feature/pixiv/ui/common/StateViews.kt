package com.pencil.chuangyin.feature.pixiv.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import com.pencil.chuangyin.core.ui.components.AppLoadingIndicator
import com.pencil.chuangyin.core.ui.components.AppPrimaryButton
import com.pencil.chuangyin.core.ui.components.AppText
import com.pencil.chuangyin.resources.R as ResR

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLoadingIndicator()
        AppText(
            text = " ",
            modifier = Modifier.padding(dimensionResource(ResR.dimen.dimen_global_padding_s)),
        )
    }
}

@Composable
fun FullScreenError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimensionResource(ResR.dimen.dimen_global_padding_l)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppText(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            align = TextAlign.Center,
        )
        AppPrimaryButton(
            text = stringRetry(),
            onClick = onRetry,
            modifier = Modifier.padding(top = dimensionResource(ResR.dimen.dimen_global_padding_m)),
        )
    }
}

@Composable
fun FullScreenEmpty(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppText(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            align = TextAlign.Center,
        )
    }
}

@Composable
private fun stringRetry(): String =
    androidx.compose.ui.res.stringResource(ResR.string.state_retry)
