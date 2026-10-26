package com.pencil.chuangyin.feature.pixiv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.LocalImageLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** 从 Hilt 图中取出带防盗链 Referer 的 P 站专用 Coil ImageLoader。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PixivImageLoaderEntryPoint {
    fun pixivImageLoader(): ImageLoader
}

@Composable
fun rememberPixivImageLoader(): ImageLoader {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors
            .fromApplication(context, PixivImageLoaderEntryPoint::class.java)
            .pixivImageLoader()
    }
}

/** 所有 P 站页面统一经此包裹，使内部 AsyncImage 使用带 Referer 的 ImageLoader。 */
@Composable
fun PixivImageProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalImageLoader provides rememberPixivImageLoader()) {
        content()
    }
}
