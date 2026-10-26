package com.pencil.chuangyin.feature.pixiv.core.network

import com.pencil.chuangyin.feature.pixiv.core.auth.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 注入官方 App 约定请求头：UA / App-OS* / Host / X-Client-Time / X-Client-Hash，
 * 已登录时附带 Bearer token。X-Client-Hash = md5(ISO 时间 + 盐)。
 */
class PixivHeaderInterceptor(
    private val host: String,
    private val session: SessionManager?,
) : Interceptor {

    private val md5 = java.security.MessageDigest.getInstance("MD5")

    override fun intercept(chain: Interceptor.Chain): Response {
        // 与官方一致的 ISO8601（带时区偏移，如 +0800）。
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.US)
            .apply { timeZone = TimeZone.getDefault() }
        val clientTime = dateFormat.format(Date())
        val clientHash = md5.digest("$clientTime${PixivConfig.HASH_SALT}".toByteArray())
            .joinToString("") { "%02x".format(it) }

        val builder = chain.request().newBuilder()
            .header("User-Agent", PixivConfig.USER_AGENT)
            .header("App-OS", PixivConfig.APP_OS_HEADER)
            .header("App-OS-Version", android.os.Build.VERSION.RELEASE)
            .header("App-Version", PixivConfig.APP_VERSION_HEADER)
            .header("Accept-Language", "zh-CN")
            .header("Host", host)
            .header("X-Client-Time", clientTime)
            .header("X-Client-Hash", clientHash)

        val token = session?.current()?.accessToken
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }
}

/** 图片防盗链拦截器：i.pximg.net 必须带 Referer。 */
class ImageRefererInterceptor(
    private val referer: String = PixivConfig.IMAGE_REFERER,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", PixivConfig.USER_AGENT)
            .header("Referer", referer)
            .build()
        return chain.proceed(request)
    }
}

/**
 * 400/401 时用 refresh_token 静默续期并重放一次；refresh 也失败则清空会话，
 * 由 UI 根据登录态跳登录页。单飞由 [SessionManager.refresh] 的互斥保证。
 */
class TokenRefreshInterceptor(
    private val session: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 400 && response.code != 401) return response
        if (request.header(RETRIED_HEADER) != null) return response

        val newToken = try {
            kotlinx.coroutines.runBlocking { session.refresh() }
        } catch (e: Exception) {
            response.close()
            kotlinx.coroutines.runBlocking { session.clear() }
            // 已清空会话，返回原 401 响应，调用方据此进入登录引导。
            return chain.proceed(request)
        }

        response.close()
        val retried = request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .header(RETRIED_HEADER, "1")
            .build()
        return chain.proceed(retried)
    }

    private companion object {
        const val RETRIED_HEADER = "X-Pixiv-Retried"
    }
}
