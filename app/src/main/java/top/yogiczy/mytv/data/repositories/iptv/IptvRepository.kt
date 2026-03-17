package top.yogiczy.mytv.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvList
import top.yogiczy.mytv.data.repositories.FileCacheRepository
import top.yogiczy.mytv.data.repositories.iptv.parser.IptvParser
import top.yogiczy.mytv.data.repositories.iptv.parser.M3uIptvParser
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
    private suspend fun fetchSource(sourceUrl: String, forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        log.d("获取远程直播源: $sourceUrl, 强制刷新: $forceRefresh")

        // 创建禁用缓存的 OkHttpClient
        val client = if (forceRefresh) {
            OkHttpClient.Builder()
                .cache(null)  // 完全禁用缓存
                .build()
        } else {
            OkHttpClient()
        }
        
        val requestBuilder = Request.Builder().url(sourceUrl)
        
        // 强制刷新时，额外添加 HTTP 头绕过缓存
        if (forceRefresh) {
            requestBuilder
                .cacheControl(okhttp3.CacheControl.Builder()
                    .noCache()  // 不使用缓存
                    .noStore()  // 不存储缓存
                    .build())
                .addHeader("Pragma", "no-cache")  // HTTP/1.0 兼容
                .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")  // 额外确保
        }
        
        val request = requestBuilder.build()

        try {
            with(client.newCall(request).execute()) {
                if (!isSuccessful) {
                    throw Exception("获取远程直播源失败: $code")
                }

                val content = body!!.string()
                log.i("下载完成，内容长度: ${content.length} 字符，行数: ${content.lines().size}")
                return@with content
            }
        } catch (ex: Exception) {
            log.e("获取远程直播源失败", ex)
            throw Exception("获取远程直播源失败，请检查网络连接", ex)
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