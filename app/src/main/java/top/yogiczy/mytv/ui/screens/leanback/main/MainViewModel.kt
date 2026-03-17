package top.yogiczy.mytv.ui.screens.leanback.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvList
import top.yogiczy.mytv.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.Logger

class LeanbackMainViewModel : ViewModel() {
    private val log = Logger.create(javaClass.simpleName)
    private val iptvRepository = IptvRepository()
    private val epgRepository = EpgRepository()

    private val _uiState = MutableStateFlow<LeanbackMainUiState>(LeanbackMainUiState.Loading(isInitial = true))
    val uiState: StateFlow<LeanbackMainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 每次启动时清空缓存，确保使用最新数据
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                iptvRepository.clearCache()
                epgRepository.clearCache()
            }
            log.i("应用启动时已清除缓存")
            
            refreshIptv()
            refreshEpg()
        }
    }

    /**
     * 公开的刷新方法，用于在设置变更后重新加载直播源和节目单
     */
    fun refresh() {
        log.i("开始刷新直播源和节目单，当前源: ${SP.iptvSourceUrl}")
        // 先设置为 Loading 状态，触发 UI 重组
        _uiState.value = LeanbackMainUiState.Loading("正在切换直播源...")
        
        viewModelScope.launch {
            try {
                // 先清除缓存，确保获取最新数据
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    iptvRepository.clearCache()
                    epgRepository.clearCache()
                }
                log.i("已清除缓存")
                // 等待一小段时间确保文件已删除
                delay(100)
                // 刷新数据
                log.i("开始重新加载直播源数据")
                refreshIptv()
                refreshEpg()
            } catch (e: Exception) {
                log.e("刷新失败", e)
            }
        }
    }
    
    /**
     * 刷新当前直播源，用于手动更新缓存
     * 不改变 UI 状态，仅重新获取数据
     */
    fun refreshCurrentSource() {
        log.i("手动刷新当前直播源，强制重新加载")
        
        viewModelScope.launch {
            try {
                // 静默刷新，使用 forceRefresh 参数
                flow {
                    emit(
                        iptvRepository.getIptvGroupList(
                            sourceUrl = SP.iptvSourceUrl,
                            cacheTime = SP.iptvSourceCacheTime,
                            simplify = SP.iptvSourceSimplify,
                            forceRefresh = true,  // 强制刷新
                        )
                    )
                }
                    .retryWhen { _, attempt ->
                        if (attempt >= Constants.HTTP_RETRY_COUNT) return@retryWhen false
                        kotlinx.coroutines.delay(Constants.HTTP_RETRY_INTERVAL)
                        log.w("刷新直播源失败，第 ${attempt + 1} 次重试")
                        true
                    }
                    .catch { ex ->
                        log.e("刷新直播源失败", ex)
                        LeanbackToastState.I.showToast("刷新失败: ${ex.message}")
                    }
                    .collect { iptvGroupList ->
                        _uiState.update { state ->
                            if (state is LeanbackMainUiState.Ready) {
                                state.copy(
                                    iptvGroupList = iptvGroupList
                                )
                            } else {
                                state
                            }
                        }
                        LeanbackToastState.I.showToast("直播源刷新成功")
                        log.i("直播源刷新完成")
                    }
                
                // 同时刷新节目单
                refreshEpg()
            } catch (e: Exception) {
                log.e("刷新当前直播源失败", e)
            }
        }
    }

    private suspend fun refreshIptv() {
        flow {
            emit(
                iptvRepository.getIptvGroupList(
                    sourceUrl = SP.iptvSourceUrl,
                    cacheTime = SP.iptvSourceCacheTime,
                    simplify = SP.iptvSourceSimplify,
                )
            )
        }
            .retryWhen { _, attempt ->
                if (attempt >= Constants.HTTP_RETRY_COUNT) return@retryWhen false

                _uiState.value =
                    LeanbackMainUiState.Loading("获取远程直播源(${attempt + 1}/${Constants.HTTP_RETRY_COUNT})...")
                delay(Constants.HTTP_RETRY_INTERVAL)
                true
            }
            .catch { ex ->
                log.e("获取直播源失败", ex)
                _uiState.value = LeanbackMainUiState.Error(ex.message)
                SP.iptvSourceUrlHistoryList -= SP.iptvSourceUrl
                // 捕获异常后不再继续执行，直接返回
            }
            .collect { iptvGroupList ->
                // 只有成功时才会执行到这里
                _uiState.value = LeanbackMainUiState.Ready(iptvGroupList = iptvGroupList)
                SP.iptvSourceUrlHistoryList += SP.iptvSourceUrl
                
                // 保存提取的 User-Agent 到全局变量，供播放器使用
                AppGlobal.extractedUserAgent = iptvRepository.extractedUserAgent
                if (AppGlobal.extractedUserAgent != null) {
                    log.i("保存提取的 User-Agent: ${AppGlobal.extractedUserAgent}")
                }
                
                // 保存 #EXT-X-APP 到全局变量，供 EPG 请求使用
                AppGlobal.extractedAppName = iptvRepository.extractedAppName
                if (AppGlobal.extractedAppName != null) {
                    log.i("保存提取的 APP 名称 (#EXT-X-APP): ${AppGlobal.extractedAppName}")
                }
            }
    }

    private suspend fun refreshEpg() {
        if (_uiState.value is LeanbackMainUiState.Ready) {
            val iptvGroupList = (_uiState.value as LeanbackMainUiState.Ready).iptvGroupList

            flow {
                // EPG 地址降级策略：
                // 1. 优先使用直播源中提取的 EPG 地址列表（按顺序尝试）
                // 2. 如果都失败，使用默认主 EPG 地址
                // 3. 如果还失败，使用默认备用 EPG 地址
                
                val extractedUrls = iptvRepository.extractedEpgUrls
                log.i("从直播源提取的 EPG 地址数量: ${extractedUrls.size}, 地址列表: ${extractedUrls.joinToString(", ")}")
                
                // 默认地址：主 + 备用
                val defaultUrls = listOf(Constants.EPG_XML_URL, Constants.EPG_XML_URL_BACKUP)
                
                // 合并所有地址：提取的地址 + 默认地址
                val allUrls = extractedUrls + defaultUrls
                log.i("EPG 降级地址列表（共 ${allUrls.size} 个）: ${allUrls.joinToString(", ")}")
                
                val primaryUrl = extractedUrls.firstOrNull() ?: Constants.EPG_XML_URL
                val fallbackUrls = if (extractedUrls.isNotEmpty()) {
                    extractedUrls.drop(1) + defaultUrls
                } else {
                    listOf(Constants.EPG_XML_URL_BACKUP)
                }
                
                val (epgList, usedUrl) = epgRepository.getEpgListWithFallback(
                    primaryUrl = primaryUrl,
                    fallbackUrls = fallbackUrls,
                    filteredChannels = iptvGroupList.iptvList.map { it.channelName },
                    refreshTimeThreshold = SP.epgRefreshTimeThreshold,
                    userAgent = iptvRepository.extractedAppName,
                )
                
                // 如果使用了直播源中的 EPG 地址且成功，记录到历史
                if (usedUrl != null && usedUrl in extractedUrls) {
                    log.i("使用直播源中的 EPG 地址成功: $usedUrl")
                    SP.epgXmlUrlHistoryList += usedUrl
                }
                
                emit(epgList)
            }
                .retry(Constants.HTTP_RETRY_COUNT) { delay(Constants.HTTP_RETRY_INTERVAL); true }
                .catch {
                    emit(EpgList())
                    log.e("所有 EPG 地址均加载失败", it)
                }
                .map { epgList ->
                    _uiState.value =
                        (_uiState.value as LeanbackMainUiState.Ready).copy(epgList = epgList)
                    if (epgList.isNotEmpty()) {
                        SP.epgXmlUrlHistoryList += SP.epgXmlUrl
                    }
                }
                .collect()
        }
    }
}

sealed interface LeanbackMainUiState {
    data class Loading(val message: String? = null, val isInitial: Boolean = false) : LeanbackMainUiState
    data class Error(val message: String? = null) : LeanbackMainUiState
    data class Ready(
        val iptvGroupList: IptvGroupList = IptvGroupList(),
        val epgList: EpgList = EpgList(),
    ) : LeanbackMainUiState
}