package top.yogiczy.mytv.data.repositories.epg

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import top.yogiczy.mytv.data.entities.Epg
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.data.entities.EpgProgrammeList
import top.yogiczy.mytv.data.repositories.FileCacheRepository
import top.yogiczy.mytv.data.repositories.epg.fetcher.EpgFetcher
import top.yogiczy.mytv.utils.Logger
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 节目单获取
 */
class EpgRepository : FileCacheRepository("epg.json") {
    private val log = Logger.create(javaClass.simpleName)
    private val epgXmlRepository = EpgXmlRepository()

    /**
     * 解析节目单xml
     */
    private suspend fun parseFromXml(
        xmlString: String,
        filteredChannels: List<String> = emptyList(),
    ) = withContext(Dispatchers.Default) {
        log.i("开始解析节目单，XML长度: ${xmlString.length}, 过滤频道数: ${filteredChannels.size}")
        if (filteredChannels.isNotEmpty()) {
            log.i("过滤频道前5个: ${filteredChannels.take(5).joinToString(", ")}")
        }
        
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xmlString))

        val epgMap = mutableMapOf<String, Epg>()
        var totalChannels = 0
        var matchedChannels = 0
        var totalProgrammes = 0  // 统计总节目数

        var eventType = parser.eventType
        parseLoop@ while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "channel") {
                        val channelId = parser.getAttributeValue(null, "id")
                        parser.nextTag()
                        val channelName = parser.nextText()
                        totalChannels++

                        // 同时比较 channelId 和 channelName，因为 M3U 中的 tvg-name 可能匹配任一个
                        val shouldInclude = filteredChannels.isEmpty() || 
                            filteredChannels.contains(channelName) || 
                            filteredChannels.contains(channelId)
                        
                        if (shouldInclude) {
                            epgMap[channelId] = Epg(channelName, EpgProgrammeList())
                            matchedChannels++
                            if (matchedChannels <= 5) {
                                log.i("匹配频道: id=$channelId, name=$channelName")
                            }
                        }
                    } else if (parser.name == "programme") {
                        totalProgrammes++
                        val channelId = parser.getAttributeValue(null, "channel")
                        val startTime = parser.getAttributeValue(null, "start")
                        val stopTime = parser.getAttributeValue(null, "stop")
                        
                        // 调试：记录CCTV1的每个节目
                        if (channelId == "CCTV1综合") {
                            val currentCount = epgMap[channelId]?.programmes?.size ?: 0
                            if (currentCount < 5 || currentCount % 20 == 0) {
                                log.d("CCTV1节目#${currentCount + 1}: start=$startTime")
                            }
                        }
                        
                        // 更健壮的解析：寻找 title 标签
                        var title = ""
                        var depth = 0
                        var foundTitle = false
                        
                        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                            when (parser.eventType) {
                                XmlPullParser.START_TAG -> {
                                    if (parser.name == "title") {
                                        foundTitle = true
                                        title = parser.nextText()
                                        break
                                    }
                                    depth++
                                }
                                XmlPullParser.END_TAG -> {
                                    if (parser.name == "programme") break
                                    depth--
                                    if (depth < 0) break
                                }
                            }
                            parser.next()
                        }
                        
                        if (!foundTitle || title.isBlank()) {
                            continue@parseLoop
                        }

                        fun parseTime(time: String): Long {
                            if (time.length < 14) return 0

                            return SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault()).parse(
                                time
                            )?.time ?: 0
                        }
                        
                        // 调试：输出第一个节目的时间
                        val programmeCount = epgMap[channelId]?.programmes?.size ?: 0
                        if (programmeCount == 0 && matchedChannels <= 2) {
                            val startTs = parseTime(startTime)
                            val endTs = parseTime(stopTime)
                            val currentTs = System.currentTimeMillis()
                            log.i("调试节目时间 - 频道: $channelId, 节目: $title")
                            log.i("  开始: $startTime -> $startTs")
                            log.i("  结束: $stopTime -> $endTs")
                            log.i("  当前: $currentTs")
                            log.i("  是否直播: ${currentTs in startTs..<endTs}")
                        }

                        if (epgMap.containsKey(channelId)) {
                            epgMap[channelId] = epgMap[channelId]!!.copy(
                                programmes = EpgProgrammeList(
                                    epgMap[channelId]!!.programmes + listOf(
                                        EpgProgramme(
                                            startAt = parseTime(startTime),
                                            endAt = parseTime(stopTime),
                                            title = title,
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        log.i("觢析节目单完成，XML中总频道数: $totalChannels, 匹配频道数: $matchedChannels, 有节目表的频道数: ${epgMap.size}, 解析的总节目数: $totalProgrammes")
                
        // 调试：输出CCTV1的节目数量和当前直播
        val cctv1Epg = epgMap["CCTV1综合"]
        if (cctv1Epg != null) {
            log.i("CCTV1节目数：${cctv1Epg.programmes.size}")
            val currentTime = System.currentTimeMillis()
            val liveProgramme = cctv1Epg.programmes.firstOrNull { 
                currentTime in it.startAt..<it.endAt 
            }
            if (liveProgramme != null) {
                log.i("CCTV1当前直播：${liveProgramme.title}")
            } else {
                log.w("CCTV1没有找到当前直播节目！当前时间: $currentTime")
                // 输出前3个和后3个节目的时间
                val allProgrammes = cctv1Epg.programmes.sortedBy { it.startAt }
                val nearbyIndex = allProgrammes.indexOfFirst { it.startAt > currentTime }
                if (nearbyIndex >= 0) {
                    val start = (nearbyIndex - 2).coerceAtLeast(0)
                    val end = (nearbyIndex + 2).coerceAtMost(allProgrammes.size - 1)
                    for (i in start..end) {
                        val prog = allProgrammes[i]
                        log.i("  [$i] ${prog.title}: ${prog.startAt} - ${prog.endAt}")
                    }
                }
            }
        }
                
        return@withContext EpgList(epgMap.values.toList())
    }

    suspend fun getEpgList(
        xmlUrl: String,
        filteredChannels: List<String> = emptyList(),
        refreshTimeThreshold: Int,
        userAgent: String? = null,
    ) = withContext(Dispatchers.Default) {
        try {
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < refreshTimeThreshold) {
                log.d("未到时间点，不刷新节目单")
                return@withContext EpgList()
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            
            // 为每个 URL 生成独立的缓存策略
            val urlHash = xmlUrl.hashCode().toString(16)
            val cacheFile = File(top.yogiczy.mytv.AppGlobal.cacheDir, "epg_$urlHash.json")
            cacheFile.parentFile?.mkdirs()
            
            // 检查缓存是否过期（按日期）
            val needRefresh = if (cacheFile.exists()) {
                val lastModified = cacheFile.lastModified()
                dateFormat.format(System.currentTimeMillis()) != dateFormat.format(lastModified)
            } else {
                true
            }
            
            val xmlJson = if (needRefresh) {
                log.i("下载EPG: $xmlUrl${if (userAgent != null) " (UA: $userAgent)" else ""}")
                val xmlString = epgXmlRepository.getEpgXml(xmlUrl, userAgent)
                val parsed = parseFromXml(xmlString, filteredChannels)
                log.i("解析节目单成功，共 ${parsed.value.size} 个频道")
                val json = Json.encodeToString(parsed.value)
                
                // 保存缓存
                cacheFile.writeText(json)
                log.d("缓存已保存: ${cacheFile.absolutePath}")
                json
            } else {
                log.d("使用缓存数据: ${cacheFile.name}")
                cacheFile.readText()
            }

            val epgList = EpgList(Json.decodeFromString<List<Epg>>(xmlJson))
            log.i("最终返回节目单：共 ${epgList.value.size} 个频道")
            epgList
        } catch (ex: Exception) {
            log.e("获取节目单失败", ex)
            throw Exception(ex)
        }
    }
    
    /**
     * 智能降级获取节目单：按优先级尝试多个 EPG 地址
     * @param primaryUrl 主 EPG 地址（如从直播源中提取的）
     * @param fallbackUrls 降级 EPG 地址列表（按优先级排序）
     * @param filteredChannels 需要过滤的频道列表
     * @param refreshTimeThreshold 刷新时间阈值
     * @return EPG 列表和实际使用的 URL
     */
    suspend fun getEpgListWithFallback(
        primaryUrl: String?,
        fallbackUrls: List<String>,
        filteredChannels: List<String> = emptyList(),
        refreshTimeThreshold: Int,
        userAgent: String? = null,
    ): Pair<EpgList, String?> = withContext(Dispatchers.Default) {
        val urlsToTry = listOfNotNull(primaryUrl) + fallbackUrls
        
        log.i("开始智能降级加载节目单，共 ${urlsToTry.size} 个地址${if (userAgent != null) "，UA: $userAgent" else ""}")
        
        for ((index, url) in urlsToTry.withIndex()) {
            if (url.isBlank()) continue
            
            try {
                log.i("尝试加载节目单 [${index + 1}/${urlsToTry.size}]: $url")
                val epgList = getEpgList(url, filteredChannels, refreshTimeThreshold, userAgent)
                
                // 检查是否有足够多的频道有当前直播节目
                // 判断标准：如果超过20%的频道有当前节目，就认为是有效的EPG数据
                val currentTime = System.currentTimeMillis()
                val channelsWithLiveProgramme = epgList.value.count { epg ->
                    epg.programmes.any { programme ->
                        currentTime in programme.startAt..<programme.endAt
                    }
                }
                val totalChannels = epgList.value.size
                val liveRatio = if (totalChannels > 0) channelsWithLiveProgramme.toFloat() / totalChannels else 0f
                
                log.i("节目单统计: 总频道数=$totalChannels, 有当前节目的频道数=$channelsWithLiveProgramme, 比例=${String.format("%.1f%%", liveRatio * 100)}")
                
                if (epgList.isNotEmpty() && liveRatio >= 0.2f) {
                    log.i("成功加载节目单（${String.format("%.1f%%", liveRatio * 100)}频道有当前节目），使用地址: $url")
                    return@withContext Pair(epgList, url)
                } else if (epgList.isEmpty()) {
                    log.w("节目单为空，尝试下一个地址")
                } else {
                    log.w("节目单有效频道比例过低（${String.format("%.1f%%", liveRatio * 100)}），尝试下一个地址")
                }
            } catch (ex: Exception) {
                log.w("加载节目单失败 [$url]: ${ex.message}，尝试下一个地址")
            }
        }
        
        log.e("所有节目单地址均加载失败")
        return@withContext Pair(EpgList(), null)
    }
}

/**
 * 节目单xml获取
 */
private class EpgXmlRepository : FileCacheRepository("epg.xml") {
    private val log = Logger.create(javaClass.simpleName)

    /**
     * 获取远程xml
     * @param userAgent 可选的 User-Agent，来自直播源 #EXT-X-APP 参数
     */
    private suspend fun fetchXml(url: String, userAgent: String? = null): String = withContext(Dispatchers.IO) {
        log.d("获取远程节目单xml: $url${if (userAgent != null) ", UA: $userAgent" else ""}")

        val client = OkHttpClient()
        val requestBuilder = Request.Builder().url(url)
        
        if (!userAgent.isNullOrBlank()) {
            // 用 APP 名称构造请求头，模拟该 APP 的请求特征
            // 同时添加 Referer、Origin 等头，绕过服务器防盗链
            val host = try {
                android.net.Uri.parse(url).host ?: ""
            } catch (e: Exception) { "" }
            
            requestBuilder
                .header("User-Agent", userAgent)
                .header("X-App-Name", userAgent)
            
            // 针对 aptv.app 域名，添加其服务器期望的 Referer/Origin
            if (host.contains("aptv.app")) {
                requestBuilder
                    .header("Referer", "https://aptv.app/")
                    .header("Origin", "https://aptv.app")
            } else {
                // 通用策略：用 EPG 域名本身作为 Referer
                if (host.isNotBlank()) {
                    requestBuilder.header("Referer", "https://$host/")
                }
            }
            
            log.i("EPG请求头 - UA: $userAgent, Referer: ${if (host.contains("aptv.app")) "https://aptv.app/" else "https://$host/"}")
        }
        
        val request = requestBuilder.build()

        try {
            with(client.newCall(request).execute()) {
                if (!isSuccessful) {
                    throw Exception("获取远程节目单xml失败: $code")
                }

                val fetcher = EpgFetcher.instances.first { it.isSupport(url) }

                return@with fetcher.fetch(this)
            }
        } catch (ex: Exception) {
            throw Exception("获取远程节目单xml失败，请检查网络连接", ex)
        }
    }

    /**
     * 获取xml
     * @param userAgent 可选的 User-Agent，来自直播源 #EXT-X-APP 参数
     */
    suspend fun getEpgXml(url: String, userAgent: String? = null): String {
        return getOrRefresh(0) {
            fetchXml(url, userAgent)
        }
    }
}
