package com.pencil.chuangyin.core.proxy.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 配置导入的 I/O 层：订阅下载与本地文件读取。
 *
 * 订阅下载固定直连（`.proxy(Proxy.NO_PROXY)`）：代理配置本身必须先能拿到，
 * 否则会出现「开启代理才能下载代理配置」的循环依赖。
 */
@Singleton
class ProxyConfigFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** 输入是否为订阅链接（仅 http/https）。 */
    fun isSubscriptionUrl(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    /** 下载订阅正文；失败时返回可展示的原因。 */
    suspend fun fetch(url: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!isSubscriptionUrl(trimmed)) {
            return@withContext Result.failure(IllegalArgumentException("链接必须以 http:// 或 https:// 开头"))
        }
        runCatching {
            val request = Request.Builder()
                .url(trimmed)
                // 部分机场按 UA 返回 Clash 配置格式，显式声明避免拿到 HTML 页面。
                .header("User-Agent", "clash-verge/v2.0 PencilChuangYin")
                .header("Accept", "text/yaml, application/yaml, text/plain, */*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("订阅返回 HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) throw IllegalStateException("订阅内容为空")
                if (body.length > MAX_TEXT_LENGTH) {
                    throw IllegalStateException("订阅内容过大（${body.length} 字符），已拒绝导入")
                }
                normalize(body)
            }
        }
    }

    /** 读取用户选择的本地文件（SAF）。 */
    suspend fun readText(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("无法打开所选文件")
            stream.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_BYTES) throw IllegalStateException("文件超过 8 MB，已拒绝导入")
                    buffer.write(chunk, 0, read)
                }
                normalize(buffer.toString(Charsets.UTF_8))
            }
        }
    }

    /**
     * 兼容部分机场把 Clash 配置 Base64 编码后返回的订阅格式。
     * 只在「正文没有 YAML 特征且像 Base64」时才尝试解码，避免误伤正常配置。
     */
    private fun normalize(raw: String): String {
        val text = raw.removePrefix("\uFEFF").trim()
        if (looksLikeClashYaml(text)) return text
        if (text.length < MIN_BASE64_LENGTH || !BASE64_REGEX.matches(text)) return text
        val decoded = runCatching {
            Base64.decode(text, Base64.DEFAULT).toString(Charsets.UTF_8)
        }.getOrNull() ?: return text
        return if (looksLikeClashYaml(decoded)) decoded else text
    }

    private fun looksLikeClashYaml(text: String): Boolean =
        text.contains("proxies") || text.contains("proxy-groups") || text.contains("rules")

    private companion object {
        const val TIMEOUT_SECONDS = 20L
        const val MAX_BYTES = 8 * 1024 * 1024
        const val MAX_TEXT_LENGTH = 8 * 1024 * 1024
        const val MIN_BASE64_LENGTH = 64
        val BASE64_REGEX = Regex("""^[A-Za-z0-9+/=\s]+$""")
    }
}
