package com.pencil.chuangyin.feature.pixiv.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pencil.chuangyin.feature.pixiv.core.auth.SessionManager
import com.pencil.chuangyin.feature.pixiv.core.network.ImageRefererInterceptor
import com.pencil.chuangyin.feature.pixiv.core.network.PixivApi
import com.pencil.chuangyin.feature.pixiv.core.network.PixivConfig
import com.pencil.chuangyin.feature.pixiv.core.network.PixivHeaderInterceptor
import com.pencil.chuangyin.feature.pixiv.core.network.PixivJson
import com.pencil.chuangyin.feature.pixiv.core.network.PixivOAuthApi
import com.pencil.chuangyin.feature.pixiv.core.network.TokenRefreshInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PixivNetworkModule {

    private const val HOST_APP_API = "app-api.pixiv.net"
    private const val HOST_OAUTH = "oauth.secure.pixiv.net"

    @Provides
    @Singleton
    fun provideJson(): Json = PixivJson

    /** OAuth 客户端：只带基础头，不带 token / 刷新拦截，避免与刷新逻辑成环。 */
    @Provides
    @Singleton
    @PixivOAuthClient
    fun provideOAuthClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(PixivHeaderInterceptor(host = HOST_OAUTH, session = null))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideOAuthApi(@PixivOAuthClient oAuthClient: OkHttpClient, json: Json): PixivOAuthApi =
        Retrofit.Builder()
            .baseUrl(PixivConfig.OAUTH_BASE_URL)
            .client(oAuthClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()

    /** 业务 API 客户端：基础头 + Bearer + 401 自动续期重放。 */
    @Provides
    @Singleton
    @PixivApiClient
    fun provideApiClient(session: SessionManager): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(PixivHeaderInterceptor(host = HOST_APP_API, session = session))
        .addInterceptor(TokenRefreshInterceptor(session))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun providePixivApi(@PixivApiClient apiClient: OkHttpClient, json: Json): PixivApi =
        Retrofit.Builder()
            .baseUrl(PixivConfig.API_BASE_URL)
            .client(apiClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()

    /** 图片客户端：统一注入防盗链 Referer。 */
    @Provides
    @Singleton
    @PixivImageClient
    fun provideImageClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(ImageRefererInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun providePixivImageLoader(
        @ApplicationContext context: Context,
        @PixivImageClient imageClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(imageClient)
        .crossfade(true)
        // 显式缓存：固定磁盘缓存上限 250MB（沿用 image_cache 目录名），
        // 内存缓存 25%（默认仅 20%）提升网格滚动与二次进入的位图复用。
        .memoryCache {
            MemoryCache.Builder(context).maxSizePercent(PIXIV_IMAGE_MEM_CACHE_PERCENT).build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(PIXIV_IMAGE_DISK_CACHE_DIR))
                .maxSizeBytes(PIXIV_IMAGE_DISK_CACHE_BYTES)
                .build()
        }
        .build()

    private const val PIXIV_IMAGE_MEM_CACHE_PERCENT = 0.25
    // 沿用 Coil 默认目录名，侧边栏「运行缓存」的统计与清除（PixivCacheManager）持续生效。
    private const val PIXIV_IMAGE_DISK_CACHE_DIR = "image_cache"
    private const val PIXIV_IMAGE_DISK_CACHE_BYTES = 250L * 1024L * 1024L
}
