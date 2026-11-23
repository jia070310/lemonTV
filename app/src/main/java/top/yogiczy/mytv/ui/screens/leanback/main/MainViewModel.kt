package top.yogiczy.mytv.ui.screens.leanback.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvList
import top.yogiczy.mytv.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvSourceFailKind
import top.yogiczy.mytv.data.repositories.iptv.iptvFailKind
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.utils.HttpServer
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.Logger

class LeanbackMainViewModel : ViewModel() {
    companion object {
        /** 清理缓存弹窗至少停留这么久，方便看清提示，也给重新拉取留出时间 */
        private const val CACHE_CLEAR_DIALOG_MS = 1800L
    }

    private val log = Logger.create(javaClass.simpleName)
    private val iptvRepository = IptvRepository()
    private val epgRepository = EpgRepository()

    private val _uiState = MutableStateFlow<LeanbackMainUiState>(LeanbackMainUiState.Loading(isInitial = true))
    val uiState: StateFlow<LeanbackMainUiState> = _uiState.asStateFlow()

    /** 播放失败后已经强制重拉过的源，避免同一份旧缓存反复重试 */
    private val playbackRefetchedSources = mutableSetOf<String>()
    /** 清理弹窗期间先拉好的直播源，弹窗结束后再进入播放 */
    private var pendingIptvGroupList: IptvGroupList? = null
    /** 重拉之后仍然无法播放（或拉取失败）的源 */
    private val playbackDeadSources = mutableSetOf<String>()
    /** m3u 已经拉取成功过的源；之后频道仍失败则视为直播地址过期 */
    private val playbackFetchedOk = mutableSetOf<String>()
    private var failoverJob: Job? = null
    private var lastLoadFailKind = IptvSourceFailKind.FetchFailed
    /** 由界面注入，保证换源时勾选与 SP 同步 */
    private var sourceApplicator: ((String) -> Unit)? = null
    private var started = false

    fun bindSourceApplicator(apply: (String) -> Unit) {
        sourceApplicator = apply
    }

    /** 等界面绑好勾选同步后再启动，避免背后切源时仍显示旧勾选 */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            // 启动优先用本地缓存；缓存无效/过期再拉远端，播不通再走换源
            if (refreshIptv(forceRefresh = false, publishError = false)) {
                refreshEpg()
            } else {
                failoverFromCurrent(lastLoadFailKind)
            }
        }
    }

    /**
     * 公开的刷新方法，用于在设置变更后重新加载直播源和节目单
     */
    fun refresh() {
        log.i("开始刷新直播源和节目单，当前源: ${SP.iptvSourceUrl}")
        playbackRefetchedSources.clear()
        playbackDeadSources.clear()
        playbackFetchedOk.clear()
        failoverJob?.cancel()
        _uiState.value = LeanbackMainUiState.Loading("正在切换直播源...")
        
        viewModelScope.launch {
            try {
                if (reloadCurrentSource(publishError = false)) {
                    log.i("直播源刷新完成")
                } else {
                    failoverFromCurrent(lastLoadFailKind)
                }
            } catch (e: Exception) {
                log.e("刷新失败", e)
            }
        }
    }

    /**
     * m3u 已拉取成功，但连续多个频道播不通：判定直播源链接失效，先报错再换源。
     */
    fun handleUnplayableChannels(applySourceUrl: (String) -> Unit) {
        failoverFromCurrent(IptvSourceFailKind.Expired, applySourceUrl)
    }

    /**
     * 空文件 / 非 m3u / 链接超时等：不测频道，先报错再换源。
     */
    fun rejectCurrentSource(
        kind: IptvSourceFailKind,
        applySourceUrl: ((String) -> Unit)? = null,
    ) {
        failoverFromCurrent(kind, applySourceUrl)
    }

    fun clearCurrentSourceFailure() {
        SP.setIptvSourceFailReason(SP.iptvSourceUrl, null)
    }

    /**
     * 统一换源：
     * 1) 先弹出当前源的失败原因（用户能看清）
     * 2) 再写入下一个源为当前选中源
     * 3) 提示“正在切换”，并拉取新源
     * 4) 没有可切源时进入二维码错误页
     */
    private fun failoverFromCurrent(
        kind: IptvSourceFailKind,
        applySourceUrl: ((String) -> Unit)? = null,
    ) {
        if (failoverJob?.isActive == true) return
        failoverJob = viewModelScope.launch {
            var failedUrl = SP.iptvSourceUrl
            var failedKind = kind
            SP.setIptvSourceFailReason(failedUrl, failedKind.name)
            playbackDeadSources += failedUrl
            playbackRefetchedSources += failedUrl

            // 先报错，停一下，避免背后偷偷切源
            showSourceErrorDialog(sourceTitle(failedUrl), failedKind)
            delay(CACHE_CLEAR_DIALOG_MS)

            val sources = (listOf(failedUrl) + SP.getIptvSourceList()).distinct()
            val start = sources.indexOf(failedUrl).coerceAtLeast(0)
            for (step in 1 until sources.size) {
                val candidate = sources[(start + step) % sources.size]
                if (candidate.isBlank() || candidate == failedUrl || candidate in playbackDeadSources) continue

                // 先改选中源，界面勾选与真实源保持一致
                val apply = applySourceUrl ?: sourceApplicator
                if (apply != null) apply(candidate) else SP.iptvSourceUrl = candidate
                playbackRefetchedSources += candidate

                showSourceSwitchDialog(
                    fromSource = sourceTitle(failedUrl),
                    toSource = sourceTitle(candidate),
                    failKind = failedKind,
                    toFailKind = null,
                )
                delay(900L)

                val loaded = reloadCurrentSource(
                    publishError = false,
                    dialogMessage = null,
                    publishWhenDone = false,
                    keepDialog = true,
                )
                if (loaded) {
                    val pending = pendingIptvGroupList
                    pendingIptvGroupList = null
                    if (pending != null) {
                        playbackFetchedOk += candidate
                        SP.setIptvSourceFailReason(candidate, null)
                        _uiState.value = LeanbackMainUiState.Ready(iptvGroupList = pending)
                        refreshEpg()
                        return@launch
                    }
                }

                pendingIptvGroupList = null
                playbackDeadSources += candidate
                failedUrl = candidate
                failedKind = lastLoadFailKind
                SP.setIptvSourceFailReason(failedUrl, failedKind.name)
                showSourceErrorDialog(sourceTitle(failedUrl), failedKind)
                delay(CACHE_CLEAR_DIALOG_MS)
            }

            log.w("所有直播源均无法播放")
            _uiState.value = LeanbackMainUiState.Error("所有直播源均无法播放，请扫描二维码添加新的直播源")
        }
    }

    private fun showSourceErrorDialog(sourceName: String, kind: IptvSourceFailKind) {
        _uiState.value = LeanbackMainUiState.Loading(
            asDialog = true,
            errorNotice = true,
            fromSource = sourceName,
            failKind = kind,
            message = when (kind) {
                IptvSourceFailKind.Timeout -> "源链接连接超时"
                IptvSourceFailKind.Empty -> "源文件错误，不是可用的 m3u"
                IptvSourceFailKind.Expired -> "直播源链接已失效"
                IptvSourceFailKind.FetchFailed -> "源链接无效"
            },
        )
    }

    private fun showSourceSwitchDialog(
        fromSource: String,
        toSource: String,
        failKind: IptvSourceFailKind,
        toFailKind: IptvSourceFailKind?,
    ) {
        _uiState.value = LeanbackMainUiState.Loading(
            asDialog = true,
            switchNotice = true,
            fromSource = fromSource,
            toSource = toSource,
            failKind = failKind,
            toFailKind = toFailKind,
            message = "正在切换到 $toSource",
        )
    }

    private fun sourceTitle(url: String): String {
        val named = SP.iptvSourceNameMap[url]?.trim().orEmpty()
        if (named.isNotBlank()) return named
        return when {
            url.contains("migu", ignoreCase = true) -> "咪咕"
            url.contains("lemonTV", ignoreCase = true) || url.contains("iptv-fe.m3u") -> "默认源"
            else -> url.substringAfter("://").substringBefore("/").ifBlank { "直播源" }
        }
    }

    private suspend fun reloadCurrentSource(
        publishError: Boolean,
        dialogMessage: String? = null,
        publishWhenDone: Boolean = true,
        keepDialog: Boolean = false,
    ): Boolean {
        val startedAt = System.currentTimeMillis()
        if (dialogMessage != null) {
            _uiState.value = LeanbackMainUiState.Loading(dialogMessage, asDialog = true)
        }
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            iptvRepository.clearCache()
            epgRepository.clearCache()
        }
        delay(100)
        pendingIptvGroupList = null
        val ok = refreshIptv(
            forceRefresh = true,
            publishError = publishError,
            asDialog = dialogMessage != null || keepDialog,
            publishReady = false,
        )
        if (dialogMessage != null && !keepDialog) {
            val remain = CACHE_CLEAR_DIALOG_MS - (System.currentTimeMillis() - startedAt)
            if (remain > 0) delay(remain)
        }
        val pending = pendingIptvGroupList
        if (ok && pending != null) {
            if (publishWhenDone) {
                pendingIptvGroupList = null
                _uiState.value = LeanbackMainUiState.Ready(iptvGroupList = pending)
                refreshEpg()
            }
            return true
        }
        pendingIptvGroupList = null
        return false
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

    private suspend fun refreshIptv(
        forceRefresh: Boolean = false,
        publishError: Boolean = true,
        asDialog: Boolean = false,
        publishReady: Boolean = true,
    ): Boolean {
        var success = false
        flow {
            emit(
                iptvRepository.getIptvGroupList(
                    sourceUrl = SP.iptvSourceUrl,
                    cacheTime = SP.iptvSourceCacheTime,
                    simplify = SP.iptvSourceSimplify,
                    forceRefresh = forceRefresh,
                )
            )
        }
            .retryWhen { cause, attempt ->
                val kind = cause.iptvFailKind()
                if (kind == IptvSourceFailKind.Timeout || kind == IptvSourceFailKind.Empty) {
                    return@retryWhen false
                }
                // 启动阶段少重试，避免卡在闪屏近一分钟
                val maxRetry = minOf(3L, Constants.HTTP_RETRY_COUNT)
                if (attempt >= maxRetry) return@retryWhen false

                _uiState.value = LeanbackMainUiState.Loading(
                    "获取远程直播源(${attempt + 1}/$maxRetry)...",
                    asDialog = asDialog,
                )
                delay(1000L)
                true
            }
            .catch { ex ->
                log.e("获取直播源失败", ex)
                lastLoadFailKind = ex.iptvFailKind()
                SP.setIptvSourceFailReason(SP.iptvSourceUrl, lastLoadFailKind.name)
                val baseMessage = ex.message ?: "获取远程直播源失败"
                val message = if (baseMessage.contains("加速器")) {
                    "$baseMessage：${HttpServer.serverUrl}"
                } else {
                    baseMessage
                }
                // 统一换源会自行弹报错/二维码，这里避免抢先进错误页
                if (publishError) {
                    _uiState.value = LeanbackMainUiState.Error(message)
                    if (baseMessage.contains("加速器")) {
                        LeanbackToastState.I.showToast(message)
                    }
                }
                SP.iptvSourceUrlHistoryList -= SP.iptvSourceUrl
                success = false
            }
            .collect { iptvGroupList ->
                // 只有成功时才会执行到这里
                success = true
                SP.setIptvSourceFailReason(SP.iptvSourceUrl, null)
                if (publishReady) {
                    _uiState.value = LeanbackMainUiState.Ready(iptvGroupList = iptvGroupList)
                } else {
                    pendingIptvGroupList = iptvGroupList
                }
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
        return success
    }

    private suspend fun refreshEpg() {
        if (_uiState.value is LeanbackMainUiState.Ready) {
            val iptvGroupList = (_uiState.value as LeanbackMainUiState.Ready).iptvGroupList

            flow {
                if (!SP.epgEnable) {
                    log.i("节目单已关闭，跳过加载")
                    emit(EpgList())
                    return@flow
                }

                // EPG 地址降级：
                // 1. 用户自定义节目单（非默认）优先
                // 2. 直播源 m3u 内提取的 EPG 地址（按顺序）
                // 3. 默认节目单（GitHub，走加速器）
                val extractedUrls = iptvRepository.extractedEpgUrls
                val configuredUrl = SP.epgXmlUrl
                val isCustom = configuredUrl.isNotBlank() && configuredUrl != Constants.EPG_XML_URL

                log.i(
                    "从直播源提取的 EPG 地址数量: ${extractedUrls.size}, " +
                        "地址列表: ${extractedUrls.joinToString(", ")}"
                )

                val urlsToTry = buildList {
                    if (isCustom) add(configuredUrl)
                    addAll(extractedUrls)
                    add(Constants.EPG_XML_URL)
                }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

                log.i("EPG 降级地址列表（共 ${urlsToTry.size} 个）: ${urlsToTry.joinToString(", ")}")

                val primaryUrl = urlsToTry.first()
                val fallbackUrls = urlsToTry.drop(1)

                val (epgList, usedUrl) = epgRepository.getEpgListWithFallback(
                    primaryUrl = primaryUrl,
                    fallbackUrls = fallbackUrls,
                    filteredChannels = iptvGroupList.iptvList.map { it.channelName },
                    refreshTimeThreshold = SP.epgRefreshTimeThreshold,
                    userAgent = iptvRepository.extractedAppName,
                )

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
    data class Loading(
        val message: String? = null,
        val isInitial: Boolean = false,
        val asDialog: Boolean = false,
        /** 先展示当前源失败原因，再切源 */
        val errorNotice: Boolean = false,
        val switchNotice: Boolean = false,
        val fromSource: String? = null,
        val toSource: String? = null,
        val failKind: IptvSourceFailKind? = null,
        val toFailKind: IptvSourceFailKind? = null,
    ) : LeanbackMainUiState
    data class Error(val message: String? = null) : LeanbackMainUiState
    data class Ready(
        val iptvGroupList: IptvGroupList = IptvGroupList(),
        val epgList: EpgList = EpgList(),
    ) : LeanbackMainUiState
}