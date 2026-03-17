package top.yogiczy.mytv.data.repositories.iptv.parser

import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvList

class M3uIptvParser : IptvParser {
    
    // 存储从 M3U 文件中提取的元数据
    // 支持多个 EPG 地址（用逗号分隔）
    var extractedEpgUrls: List<String> = emptyList()
        private set
    var extractedUserAgent: String? = null
        private set
    // 从 #EXT-X-APP 提取的 APP 名称，用于读取 EPG 时作为 User-Agent
    var extractedAppName: String? = null
        private set

    override fun isSupport(url: String, data: String): Boolean {
        return data.startsWith("#EXTM3U")
    }

    override suspend fun parse(data: String): IptvGroupList {
        try {
            // 使用 lines() 方法正确处理所有换行符（\n, \r\n, \r）
            val lines = data.lines()
            val iptvList = mutableListOf<IptvResponseItem>()
                
            // 提取 M3U 头部的元数据
            extractMetadata(lines)
        
            var currentUserAgent: String? = null
            var currentReferer: String? = null
                
            lines.forEachIndexed { index, line ->
                val trimmedLine = line.trim()
                    
                // 提取当前频道的 User-Agent
                if (trimmedLine.startsWith("#EXTVLCOPT:http-user-agent")) {
                    val uaMatch = Regex("""http-user-agent\\s*=\\s*(.+)""").find(trimmedLine)
                    if (uaMatch != null) {
                        currentUserAgent = uaMatch.groupValues[1].trim()
                    }
                }
                    
                // 提取当前频道的 Referer
                if (trimmedLine.startsWith("#EXTVLCOPT:http-referer") || 
                    trimmedLine.contains("http-referer=")) {
                    val refererMatch = Regex("""http-referer\\s*=\\s*(.+)""").find(trimmedLine)
                    if (refererMatch != null) {
                        currentReferer = refererMatch.groupValues[1].trim().removeSurrounding("\"", "\"")
                    }
                }
                    
                if (!trimmedLine.startsWith("#EXTINF")) return@forEachIndexed
                
                // 防止数组越界：检查下一行是否存在
                if (index + 1 >= lines.size) {
                    // 格式错误：#EXTINF 后没有 URL 行
                    return@forEachIndexed
                }
        
                val name = line.split(",").lastOrNull() ?: "未知频道"
                val channelName = Regex("""tvg-name=\"(.+?)\"""").find(line)?.groupValues?.get(1) ?: name
                val groupName = Regex("""group-title=\"(.+?)\"""").find(line)?.groupValues?.get(1) ?: "其他"
                    
                // 从 EXTINF 行中也可能有 user-agent 和 referer
                val inlineUserAgent = Regex("""http-user-agent=\"(.+?)\"""").find(line)?.groupValues?.get(1)
                val inlineReferer = Regex("""http-referer=\"(.+?)\"""").find(line)?.groupValues?.get(1)
                
                val url = lines[index + 1].trim()
                
                // 跳过空 URL
                if (url.isBlank() || url.startsWith("#")) {
                    return@forEachIndexed
                }
        
                iptvList.add(
                    IptvResponseItem(
                        name = name.trim(),
                        channelName = channelName.trim(),
                        groupName = groupName.trim(),
                        url = url,
                        userAgent = inlineUserAgent ?: currentUserAgent ?: extractedUserAgent,
                        referer = inlineReferer ?: currentReferer,
                    )
                )
                    
                // 不重置 currentUserAgent 和 currentReferer！
                // 它们应该在遇到新的 #EXTVLCOPT 时才更新，这样同一组频道可以共用 UA/Referer
            }
            
            // 检查是否解析到任何频道
            println("M3uIptvParser: 原始解析结果：${iptvList.size} 个频道项")
            if (iptvList.isEmpty()) {
                throw Exception("直播源格式错误：未找到任何有效频道，请更换直播源")
            }

            return IptvGroupList(iptvList.groupBy { it.groupName }.map { groupEntry ->
                IptvGroup(
                    name = groupEntry.key,
                    iptvList = IptvList(groupEntry.value.groupBy { it.name }.map { nameEntry ->
                        Iptv(
                            name = nameEntry.key,
                            channelName = nameEntry.value.first().channelName,
                            urlList = nameEntry.value.map { it.url },
                            userAgent = nameEntry.value.first().userAgent,
                            referer = nameEntry.value.first().referer,
                        )
                    })
                )
            })
        } catch (e: Exception) {
            // 捕获所有异常，重新抛出更友好的错误信息
            throw Exception("直播源解析失败：${e.message ?: "格式错误"}", e)
        }
    }
    
    /**
     * 从 M3U 文件头部提取元数据
     * 支持格式：
     * #EXTM3U x-tvg-url="http://epg.example.com/epg.xml"
     * #EXTM3U x-tvg-url="url1,url2,url3"  (支持多个地址用逗号分隔)
     * #EXTVLCOPT:http-user-agent=Mozilla/5.0
     */
    private fun extractMetadata(lines: List<String>) {
        // 重置
        extractedEpgUrls = emptyList()
        extractedUserAgent = null
        extractedAppName = null
        
        // 只检查前20行（通常元数据在文件开头）
        lines.take(20).forEach { line ->
            val trimmedLine = line.trim()
            
            // 提取 EPG 地址（支持多个地址）
            if (trimmedLine.startsWith("#EXTM3U")) {
                // 支持 x-tvg-url 和 url-tvg
                val epgMatch = Regex("""(?:x-tvg-url|url-tvg)\s*=\s*["']?([^"']+)["']?""").find(trimmedLine)
                if (epgMatch != null) {
                    val epgUrlsString = epgMatch.groupValues[1].trim()
                    // 按逗号分割，并清理每个地址
                    extractedEpgUrls = epgUrlsString.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
            }
            
            // 提取 User-Agent（#EXTVLCOPT 方式）
            if (trimmedLine.startsWith("#EXTVLCOPT:http-user-agent")) {
                val uaMatch = Regex("""http-user-agent\s*=\s*(.+)""").find(trimmedLine)
                if (uaMatch != null) {
                    extractedUserAgent = uaMatch.groupValues[1].trim()
                }
            }
            
            // 提取 #EXT-X-APP 参数，作为 EPG 请求时的 User-Agent 标识
            // 格式：#EXT-X-APP APTV
            if (trimmedLine.startsWith("#EXT-X-APP")) {
                val appName = trimmedLine.removePrefix("#EXT-X-APP").trim()
                if (appName.isNotBlank()) {
                    extractedAppName = appName
                }
            }
        }
    }

    private data class IptvResponseItem(
        val name: String,
        val channelName: String,
        val groupName: String,
        val url: String,
        val userAgent: String? = null,
        val referer: String? = null,
    )
}