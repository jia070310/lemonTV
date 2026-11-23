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
import top.yogiczy.mytv.data.repositories.iptv.parser.DefaultIptvParser
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
            val candidates = Constants.iptvSourceFallbackUrls(
                sourceUrl,
                SP.githubProxy,
                rewriteProxy = SP.githubProxyEnabled,
            )
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
            val failKinds = mutableListOf<IptvSourceFailKind>()
            for (candidate in candidates) {
                try {
                    val content = downloadSource(client, candidate, forceRefresh)
                    validatePlaylistContent(content)
                    log.i("下载完成($candidate)，内容长度: ${content.length} 字符，行数: ${content.lines().size}")
                    return@withContext content
                } catch (ex: Exception) {
                    lastError = ex
                    val kind = ex.iptvFailKind()
                    failKinds += kind
                    log.w("候选源失败: $candidate -> ${ex.message}")
                    // 已拉到正文但判定为源文件错误：不要再试其它镜像
                    if (kind == IptvSourceFailKind.Empty) {
                        throw if (ex is IptvSourceLoadException) {
                            ex
                        } else {
                            IptvSourceLoadException(kind, "源文件错误", ex)
                        }
                    }
                }
            }

            log.e("获取远程直播源失败", lastError)
            val kind = when {
                failKinds.any { it == IptvSourceFailKind.Empty } -> IptvSourceFailKind.Empty
                failKinds.isNotEmpty() && failKinds.all { it == IptvSourceFailKind.Timeout } ->
                    IptvSourceFailKind.Timeout
                else -> IptvSourceFailKind.FetchFailed
            }
            val hint = when (kind) {
                IptvSourceFailKind.Timeout -> "源链接连接超时"
                IptvSourceFailKind.Empty -> "源文件错误"
                else -> if (candidates.any { Constants.usesGithubProxy(it, SP.githubProxy) }) {
                    "获取远程直播源失败，加速器可能不可用，请在后台设置修改加速器"
                } else {
                    "获取远程直播源失败，请检查网络连接"
                }
            }
            throw IptvSourceLoadException(kind, hint, lastError)
        }

    private fun downloadSource(
        client: OkHttpClient,
        sourceUrl: String,
        forceRefresh: Boolean,
    ): String {
        // 强制刷新时加时间戳，尽量绕过加速器/CDN 对 raw 的旧缓存
        val requestUrl = if (forceRefresh) {
            val sep = if (sourceUrl.contains("?")) "&" else "?"
            "$sourceUrl${sep}_t=${System.currentTimeMillis()}"
        } else {
            sourceUrl
        }
        val requestBuilder = Request.Builder().url(requestUrl)

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
     * 校验拉取到的正文。
     * - 空内容 / 纯数字等垃圾：Empty（不要再试 CDN 旧镜像）
     * - 加速器返回的 HTML 错误页：FetchFailed（允许继续试备用地址）
     */
    private fun validatePlaylistContent(content: String) {
        val trimmed = content.trim()
        if (trimmed.isBlank() || trimmed.length < 16) {
            throw IptvSourceLoadException(IptvSourceFailKind.Empty, "直播源文件没有频道")
        }
        if (trimmed.startsWith("<") ||
            trimmed.contains("<html", ignoreCase = true) ||
            trimmed.contains("<!doctype", ignoreCase = true)
        ) {
            throw IptvSourceLoadException(IptvSourceFailKind.FetchFailed, "直播源返回了错误页面")
        }
        val looksLikeM3u = trimmed.startsWith("#EXTM3U") || trimmed.contains("#EXTINF")
        val looksLikeTvbox = trimmed.contains("#genre#")
        if (!looksLikeM3u && !looksLikeTvbox) {
            throw IptvSourceLoadException(IptvSourceFailKind.Empty, "直播源文件没有频道")
        }
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
                this.forceRefresh { fetchSource(sourceUrl, forceRefresh = true) }
            } else {
                // 本地缓存若已不是合法 m3u（例如远端已改成垃圾数据），丢掉重拉，
                // 避免「覆盖安装 / 未清数据」仍用旧 iptv.txt 假装能播。
                getOrRefresh(cacheTime) {
                    fetchSource(sourceUrl, forceRefresh = false)
                }.also { cached ->
                    try {
                        validatePlaylistContent(cached)
                    } catch (ex: IptvSourceLoadException) {
                        if (ex.kind == IptvSourceFailKind.Empty) {
                            log.w("本地缓存不是可用 m3u，清除后重新拉取")
                            clearCache()
                            return@getIptvGroupList getIptvGroupList(
                                sourceUrl = sourceUrl,
                                cacheTime = cacheTime,
                                simplify = simplify,
                                forceRefresh = true,
                            )
                        }
                        throw ex
                    }
                }
            }
            // 解析前再校验一次
            validatePlaylistContent(sourceData)

            val parser = IptvParser.instances.firstOrNull { it.isSupport(sourceUrl, sourceData) }
                ?: throw IptvSourceLoadException(IptvSourceFailKind.Empty, "直播源文件没有频道")
            if (parser is DefaultIptvParser) {
                throw IptvSourceLoadException(IptvSourceFailKind.Empty, "直播源文件没有频道")
            }
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
            
            val playableChannels = groupList.flatMap { it.iptvList }.filter { it.urlList.isNotEmpty() }
            log.i("解析直播源完成：${groupList.size}个分组，${playableChannels.size}个可播放频道")
            if (playableChannels.isEmpty()) {
                throw IptvSourceLoadException(IptvSourceFailKind.Empty, "直播源文件没有频道")
            }

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
        } catch (ex: IptvSourceLoadException) {
            log.e("获取直播源失败: ${ex.kind}", ex)
            throw ex
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw IptvSourceLoadException(ex.iptvFailKind(), ex.message ?: "获取直播源失败", ex)
        }
    }
}