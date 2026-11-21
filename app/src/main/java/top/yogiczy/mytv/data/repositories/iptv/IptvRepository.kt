package top.yogiczy.mytv.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.net.Uri
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvList
import top.yogiczy.mytv.data.repositories.FileCacheRepository
import top.yogiczy.mytv.data.repositories.iptv.parser.IptvParser
import top.yogiczy.mytv.data.repositories.iptv.parser.M3uIptvParser
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.Logger

/**
 * 直播源获取
 */
class IptvRepository : FileCacheRepository("iptv.txt") {
    private val log = Logger.create(javaClass.simpleName)
    
    // 从直播源文件中提取的元数据
    // 支持多个 EPG 地址
    var extractedEpgUrls: List<String> = emptyList()
        private set
    var extractedUserAgent: String? = null
        private set
    // 从 #EXT-X-APP 提取的 APP 名称，用于读取 EPG 时作为 User-Agent
    var extractedAppName: String? = null
        private set

    /**
     * 获取远程直播源数据
     * @param forceRefresh 是否强制刷新（绕过 HTTP 缓存）
     */
    private suspend fun fetchSource(sourceUrl: String, forceRefresh: Boolean = false) =
        withContext(Dispatchers.IO) {
            val candidates = Constants.iptvSourceFallbackUrls(sourceUrl, SP.githubProxy)
            log.d("获取远程直播源: $sourceUrl, 候选=${candidates.size}, 强制刷新: $forceRefresh")

            // 超时要短：加速器挂起时尽快切到备用镜像，避免闪屏卡近一分钟
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
            val client = if (forceRefresh) {
                clientBuilder.cache(null).build()
            } else {
                clientBuilder.build()
            }

            var lastError: Exception? = null
            for (candidate in candidates) {
                try {
                    val content = downloadSource(client, candidate, forceRefresh)
                    // 空壳 #EXTM3U（例如部分 jsDelivr 节点缓存失效）视为失败
                    if (content.trim().length < 64) {
                        throw Exception("直播源内容过短，可能镜像失效")
                    }
                    log.i("下载完成($candidate)，内容长度: ${content.length} 字符，行数: ${content.lines().size}")
                    return@withContext content
                } catch (ex: Exception) {
                    lastError = ex
                    log.w("候选源失败: $candidate -> ${ex.message}")
                }
            }

            log.e("获取远程直播源失败", lastError)
            val hint = if (candidates.any { Constants.usesGithubProxy(it, SP.githubProxy) }) {
                "获取远程直播源失败，加速器可能不可用，请在后台设置修改加速器"
            } else {
                "获取远程直播源失败，请检查网络连接"
            }
            throw Exception(hint, lastError)
        }

    private fun downloadSource(
        client: OkHttpClient,
        sourceUrl: String,
        forceRefresh: Boolean,
    ): String {
        val requestBuilder = Request.Builder().url(sourceUrl)

        val host = try {
            Uri.parse(sourceUrl).host ?: ""
        } catch (_: Exception) {
            ""
        }
        if (host.contains("aptv.app")) {
            requestBuilder
                .header("User-Agent", Constants.VIDEO_PLAYER_USER_AGENT_APTV)
                .header("X-App-Name", Constants.VIDEO_PLAYER_USER_AGENT_APTV)
        } else {
            // 部分 GitHub 加速器对 OkHttp 默认 UA 限速/失败
            requestBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            )
        }

        if (forceRefresh) {
            requestBuilder
                .cacheControl(
                    okhttp3.CacheControl.Builder()
                        .noCache()
                        .noStore()
                        .build()
                )
                .addHeader("Pragma", "no-cache")
                .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("获取远程直播源失败: ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    /**
     * 简化规则
     */
    private fun simplifyTest(group: IptvGroup, iptv: Iptv): Boolean {
        return iptv.name.lowercase().startsWith("cctv") || iptv.name.endsWith("卫视")
    }

    /**
     * 获取直播源分组列表
     */
    suspend fun getIptvGroupList(
        sourceUrl: String,
        cacheTime: Long,
        simplify: Boolean = false,
        forceRefresh: Boolean = false,  // 新增：是否强制刷新
    ): IptvGroupList {
        try {
            val sourceData = if (forceRefresh) {
                log.i("强制刷新直播源：$sourceUrl")
                // 调用父类的 forceRefresh 方法，并传入 forceRefresh=true 绕过 HTTP 缓存
                this.forceRefresh { fetchSource(sourceUrl, forceRefresh = true) }
            } else {
                getOrRefresh(cacheTime) {
                    fetchSource(sourceUrl, forceRefresh = false)
                }
            }

            val parser = IptvParser.instances.first { it.isSupport(sourceUrl, sourceData) }
            val groupList = parser.parse(sourceData)
            
            // 如果是 M3U 解析器，保存提取的元数据
            if (parser is M3uIptvParser) {
                extractedEpgUrls = parser.extractedEpgUrls
                extractedUserAgent = parser.extractedUserAgent
                extractedAppName = parser.extractedAppName
                
                if (extractedEpgUrls.isNotEmpty()) {
                    log.i("从直播源中提取到 ${extractedEpgUrls.size} 个 EPG 地址: ${extractedEpgUrls.joinToString(", ")}")
                }
                if (extractedUserAgent != null) {
                    log.i("从直播源中提取到 User-Agent: $extractedUserAgent")
                }
                if (extractedAppName != null) {
                    log.i("从直播源中提取到 APP 名称 (#EXT-X-APP): $extractedAppName")
                }
            }
            
            log.i("解析直播源完成：${groupList.size}个分组，${groupList.flatMap { it.iptvList }.size}个频道")

            if (simplify) {
                return IptvGroupList(groupList.map { group ->
                    IptvGroup(
                        name = group.name, iptvList = IptvList(group.iptvList.filter { iptv ->
                            simplifyTest(group, iptv)
                        })
                    )
                }.filter { it.iptvList.isNotEmpty() })
            }

            return groupList
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw Exception(ex)
        }
    }
}