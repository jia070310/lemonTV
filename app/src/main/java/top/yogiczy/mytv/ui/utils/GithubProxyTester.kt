package top.yogiczy.mytv.ui.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.utils.Logger
import java.util.concurrent.TimeUnit

/**
 * GitHub 加速器测速（应用内 / 网页后台共用）
 */
object GithubProxyTester {
    private val log = Logger.create(javaClass.simpleName)

    /** 部分加速器会对 OkHttp 默认 UA 限速甚至失败，使用浏览器 UA 更稳定 */
    private const val TEST_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    data class Result(
        val proxy: String,
        val ok: Boolean,
        val ms: Long,
        val error: String? = null,
    )

    /**
     * 并行测速；按成功优先、耗时升序排序
     */
    suspend fun testProxies(proxies: List<String>): List<Result> = withContext(Dispatchers.IO) {
        val normalized = proxies
            .map(Constants::normalizeGithubProxy)
            .distinct()
        if (normalized.isEmpty()) return@withContext emptyList()

        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()

        coroutineScope {
            normalized.map { proxy ->
                async { testOne(client, proxy) }
            }.awaitAll()
        }.sortedWith(
            compareBy<Result> { !it.ok }
                .thenBy { if (it.ms >= 0) it.ms else Long.MAX_VALUE },
        )
    }

    private fun testOne(client: OkHttpClient, proxy: String): Result {
        val testUrl = Constants.withGithubProxy(
            Constants.GITHUB_PROXY_TEST_RAW_URL,
            proxy,
        )
        val started = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(testUrl)
                .get()
                .header("User-Agent", TEST_USER_AGENT)
                .header("Accept", "*/*")
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { resp ->
                val body = resp.body?.bytes() ?: ByteArray(0)
                val ms = System.currentTimeMillis() - started
                val ok = resp.isSuccessful && body.isNotEmpty()
                if (ok) {
                    log.i("加速器可用: $proxy ${ms}ms (${body.size}B)")
                    Result(proxy = proxy, ok = true, ms = ms)
                } else {
                    val err = when {
                        !resp.isSuccessful -> "HTTP ${resp.code}"
                        else -> "空响应"
                    }
                    log.w("加速器不可用: $proxy -> $err")
                    Result(proxy = proxy, ok = false, ms = -1, error = err)
                }
            }
        } catch (ex: Exception) {
            log.w("加速器测速异常: $proxy -> ${ex.message}")
            Result(
                proxy = proxy,
                ok = false,
                ms = -1,
                error = ex.message ?: ex.javaClass.simpleName,
            )
        }
    }
}
