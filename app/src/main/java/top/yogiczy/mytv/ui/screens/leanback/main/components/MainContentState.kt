package top.yogiczy.mytv.ui.screens.leanback.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvIdx
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvList
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.video.LeanbackVideoPlayerState
import top.yogiczy.mytv.ui.screens.leanback.video.rememberLeanbackVideoPlayerState
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.Loggable
import kotlin.math.max

@Stable
class LeanbackMainContentState(
    coroutineScope: CoroutineScope,
    private val videoPlayerState: LeanbackVideoPlayerState,
    private val iptvGroupList: IptvGroupList,
    private val onChannelsUnplayable: (() -> Unit)? = null,
    private val onSourceRejected: (() -> Unit)? = null,
    private val onPlaybackSucceeded: (() -> Unit)? = null,
) : Loggable() {
    private var _currentIptv by mutableStateOf(Iptv())
    val currentIptv get() = _currentIptv

    private var _currentIptvUrlIdx by mutableIntStateOf(0)
    val currentIptvUrlIdx get() = _currentIptvUrlIdx
    
    // 本轮已失败的频道下标，避免只有一两个频道时反复空转
    private val failedChannelIndices = mutableSetOf<Int>()
    // 当前频道已经试过的线路，失败后按顺序补试还没播过的链接
    private val triedUrlIndices = mutableSetOf<Int>()
    // 当前URL的重试次数
    private var retryCount = 0
    // 重试协程Job
    private var retryJob: Job? = null
    // 连续切频道失败次数
    private var consecutiveChannelFailures = 0
    // 已交给上层清理缓存 / 换源，避免错误回调重复触发
    private var sourceRecoveryTriggered = false
    // 重试状态描述（供UI显示）
    private var _retryStatus by mutableStateOf<String?>(null)
    val retryStatus get() = _retryStatus

    companion object {
        /** 单个URL最大重试次数 */
        private const val MAX_RETRY_COUNT = 3
        /** 重试间隔（毫秒） */
        private const val RETRY_INTERVAL_MS = 3000L
        /** 连续失败这么多个频道后，提示直播源链接失效并切换源 */
        private const val MAX_CHANNEL_FAILURES_BEFORE_RELOAD = 3
    }

    private var _isPanelVisible by mutableStateOf(false)
    var isPanelVisible
        get() = _isPanelVisible
        set(value) {
            _isPanelVisible = value
        }

    private var _isSettingsVisible by mutableStateOf(false)
    var isSettingsVisible
        get() = _isSettingsVisible
        set(value) {
            _isSettingsVisible = value
        }

    private var _isTempPanelVisible by mutableStateOf(false)
    var isTempPanelVisible
        get() = _isTempPanelVisible
        set(value) {
            _isTempPanelVisible = value
        }

    private var _isQuickPanelVisible by mutableStateOf(false)
    var isQuickPanelVisible
        get() = _isQuickPanelVisible
        set(value) {
            _isQuickPanelVisible = value
        }

    init {
        if (iptvGroupList.iptvList.isEmpty()) {
            log.w("直播源没有频道，直接切换源")
            onSourceRejected?.invoke()
        } else {
            changeCurrentIptv(iptvGroupList.iptvList.getOrElse(SP.iptvLastIptvIdx) {
                iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
            })
        }

        videoPlayerState.onReady {
            coroutineScope.launch {
                val name = _currentIptv.name
                val urlIdx = _currentIptvUrlIdx
                delay(Constants.UI_TEMP_PANEL_SCREEN_SHOW_DURATION)
                if (name == _currentIptv.name && urlIdx == _currentIptvUrlIdx) {
                    _isTempPanelVisible = false
                }
            }

            // 记忆可播放的域名
            if (_currentIptvUrlIdx in _currentIptv.urlList.indices) {
                SP.iptvPlayableHostList += getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])
            }

            // 成功播放，重置所有计数器
            retryCount = 0
            retryJob?.cancel()
            retryJob = null
            consecutiveChannelFailures = 0
            failedChannelIndices.clear()
            triedUrlIndices.clear()
            sourceRecoveryTriggered = false
            _retryStatus = null
            onPlaybackSucceeded?.invoke()
        }

        // 播放失败、加载超时、播出后中断，都走同一套：当前线路 3 次 → 下一条线路 → 下一个频道
        videoPlayerState.onError {
            if (sourceRecoveryTriggered) return@onError
            handlePlaybackFailure(coroutineScope)
        }

        videoPlayerState.onCutoff {
            if (sourceRecoveryTriggered) return@onCutoff
            log.w("直播中断，按播放失败处理：${_currentIptv.name}")
            handlePlaybackFailure(coroutineScope)
        }
    }

    /**
     * 当前线路失败：先重试 3 次，再换同频道下一条线路；没有下一条就换频道。
     * 连续 3 个频道都不通时，交给上层提示「直播源链接失效」再切换源。
     */
    private fun handlePlaybackFailure(coroutineScope: CoroutineScope) {
        retryJob?.cancel()

        if (_currentIptv.urlList.isNotEmpty() && _currentIptvUrlIdx in _currentIptv.urlList.indices) {
            SP.iptvPlayableHostList -= getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])
        }

        val lineNo = _currentIptvUrlIdx + 1
        val lineTotal = _currentIptv.urlList.size.coerceAtLeast(1)
        val where = failoverPlace(lineNo, lineTotal)

        if (_currentIptv.urlList.isNotEmpty() && retryCount < MAX_RETRY_COUNT) {
            retryCount++
            val countdown = RETRY_INTERVAL_MS / 1000
            log.w("播放失败，${countdown}秒后重试（$where，$retryCount/$MAX_RETRY_COUNT）：${_currentIptv.name}")
            val retryIptv = _currentIptv
            val retryUrlIdx = _currentIptvUrlIdx
            retryJob = coroutineScope.launch {
                for (remaining in countdown downTo 1) {
                    _retryStatus = "$where · 第 $retryCount/$MAX_RETRY_COUNT 次重试，${remaining}秒后重连"
                    delay(1000L)
                }
                if (_currentIptv == retryIptv && _currentIptvUrlIdx == retryUrlIdx &&
                    retryUrlIdx in retryIptv.urlList.indices
                ) {
                    _retryStatus = "$where · 第 $retryCount/$MAX_RETRY_COUNT 次重试"
                    videoPlayerState.prepare(
                        url = retryIptv.urlList[retryUrlIdx],
                        userAgent = retryIptv.userAgent,
                        referer = retryIptv.referer
                    )
                }
            }
            return
        }

        retryCount = 0
        _retryStatus = null
        log.w("$where 重试${MAX_RETRY_COUNT}次均失败：${_currentIptv.name}")

        val nextUrlIdx = _currentIptv.urlList.indices.firstOrNull { it !in triedUrlIndices }
        if (nextUrlIdx != null) {
            log.i("切换到下一个线路：${_currentIptv.name}（${nextUrlIdx + 1}/${_currentIptv.urlList.size}）")
            _retryStatus = "${failoverPlace(nextUrlIdx + 1, lineTotal)} · 正在切换线路"
            changeCurrentIptv(_currentIptv, nextUrlIdx, fromFailover = true)
            return
        }

        failCurrentChannel()
    }

    private fun failCurrentChannel() {
        retryCount = 0
        retryJob?.cancel()
        retryJob = null
        consecutiveChannelFailures++
        val currentIdx = iptvGroupList.iptvIdx(_currentIptv)
        if (currentIdx >= 0) failedChannelIndices.add(currentIdx)

        val next = getNextIptv()
        val nextIdx = iptvGroupList.iptvIdx(next)
        val noMoreChannel = nextIdx < 0 || nextIdx in failedChannelIndices
        log.w("频道 ${_currentIptv.name} 无法播放（$consecutiveChannelFailures/$MAX_CHANNEL_FAILURES_BEFORE_RELOAD）")

        if (consecutiveChannelFailures >= MAX_CHANNEL_FAILURES_BEFORE_RELOAD || noMoreChannel) {
            if (sourceRecoveryTriggered) return
            sourceRecoveryTriggered = true
            log.w("已测试 $consecutiveChannelFailures 个频道仍无法播放，判定直播源链接失效")
            _retryStatus = "第 $consecutiveChannelFailures/$MAX_CHANNEL_FAILURES_BEFORE_RELOAD 个频道失败，直播源链接失效"
            onChannelsUnplayable?.invoke()
        } else {
            val nextNo = (consecutiveChannelFailures + 1)
                .coerceAtMost(MAX_CHANNEL_FAILURES_BEFORE_RELOAD)
            log.i("自动切换到下一个频道：${next.name}")
            _retryStatus = "第 $nextNo/$MAX_CHANNEL_FAILURES_BEFORE_RELOAD 个频道 ${next.name} · 正在切换"
            changeCurrentIptv(next, urlIdx = 0, fromFailover = true)
        }
    }

    /** 第几个重试频道、频道名、第几条线路 */
    private fun failoverPlace(lineNo: Int, lineTotal: Int): String {
        val channelNo = (consecutiveChannelFailures + 1)
            .coerceIn(1, MAX_CHANNEL_FAILURES_BEFORE_RELOAD)
        val name = _currentIptv.name.ifBlank { "未命名频道" }
        return "第 $channelNo/$MAX_CHANNEL_FAILURES_BEFORE_RELOAD 个频道 $name · 线路 $lineNo/$lineTotal"
    }

    private fun getPrevIptv(): Iptv {
        val currentIndex = iptvGroupList.iptvIdx(_currentIptv)
        return iptvGroupList.iptvList.getOrElse(currentIndex - 1) {
            iptvGroupList.lastOrNull()?.iptvList?.lastOrNull() ?: Iptv()
        }
    }

    private fun getNextIptv(): Iptv {
        val currentIndex = iptvGroupList.iptvIdx(_currentIptv)
        return iptvGroupList.iptvList.getOrElse(currentIndex + 1) {
            iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
        }
    }

    fun changeCurrentIptv(iptv: Iptv, urlIdx: Int? = null, fromFailover: Boolean = false) {
        _isPanelVisible = false
        // 切换频道时取消当前重试任务，重置重试计数
        retryJob?.cancel()
        retryJob = null
        retryCount = 0
        _retryStatus = null

        if (iptv == _currentIptv && urlIdx == null) return

        // 用户手动换台时重新计数；自动换线路/换台不打断本轮失败统计
        if (!fromFailover && iptv != _currentIptv) {
            consecutiveChannelFailures = 0
            failedChannelIndices.clear()
            sourceRecoveryTriggered = false
        }
        if (iptv != _currentIptv || !fromFailover) {
            triedUrlIndices.clear()
        }

        if (iptv == _currentIptv && urlIdx != _currentIptvUrlIdx &&
            _currentIptv.urlList.isNotEmpty() && _currentIptvUrlIdx in _currentIptv.urlList.indices
        ) {
            SP.iptvPlayableHostList -= getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])
        }

        _isTempPanelVisible = true

        _currentIptv = iptv
        SP.iptvLastIptvIdx = iptvGroupList.iptvIdx(_currentIptv)

        if (iptv.urlList.isEmpty()) {
            log.w("频道 ${iptv.name} 的 URL 列表为空，切换下一个频道")
            failCurrentChannel()
            return
        }

        _currentIptvUrlIdx = if (urlIdx == null) {
            // 优先从记忆中选择可播放的域名
            max(0, _currentIptv.urlList.indexOfFirst {
                SP.iptvPlayableHostList.contains(getUrlHost(it))
            })
        } else {
            (urlIdx + _currentIptv.urlList.size) % _currentIptv.urlList.size
        }
        triedUrlIndices += _currentIptvUrlIdx

        val url = iptv.urlList[_currentIptvUrlIdx]
        log.d("播放${iptv.name}（${_currentIptvUrlIdx + 1}/${_currentIptv.urlList.size}）: $url")
        
        // 传入频道的 User-Agent 和 Referer
        videoPlayerState.prepare(
            url = url,
            userAgent = iptv.userAgent,
            referer = iptv.referer
        )
    }

    fun changeCurrentIptvToPrev() {
        changeCurrentIptv(getPrevIptv())
    }

    fun changeCurrentIptvToNext() {
        changeCurrentIptv(getNextIptv())
    }
    
    /**
     * 重置失败记录（手动切换源后调用）
     */
    fun resetFailureTracking() {
        failedChannelIndices.clear()
        triedUrlIndices.clear()
        retryCount = 0
        consecutiveChannelFailures = 0
        sourceRecoveryTriggered = false
        retryJob?.cancel()
        retryJob = null
        _retryStatus = null
        log.i("已重置失败跟踪记录")
    }
}

@Composable
fun rememberLeanbackMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: LeanbackVideoPlayerState = rememberLeanbackVideoPlayerState(),
    iptvGroupList: IptvGroupList = IptvGroupList(),
    onChannelsUnplayable: (() -> Unit)? = null,
    onSourceRejected: (() -> Unit)? = null,
    onPlaybackSucceeded: (() -> Unit)? = null,
) = remember {
    LeanbackMainContentState(
        coroutineScope = coroutineScope,
        videoPlayerState = videoPlayerState,
        iptvGroupList = iptvGroupList,
        onChannelsUnplayable = onChannelsUnplayable,
        onSourceRejected = onSourceRejected,
        onPlaybackSucceeded = onPlaybackSucceeded,
    )
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}