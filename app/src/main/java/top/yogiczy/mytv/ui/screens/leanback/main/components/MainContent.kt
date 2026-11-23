package top.yogiczy.mytv.ui.screens.leanback.main.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.EpgList.Companion.currentProgrammes
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvIdx
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvList
import top.yogiczy.mytv.ui.screens.leanback.main.LeanbackMainViewModel
import top.yogiczy.mytv.ui.screens.leanback.classicpanel.LeanbackClassicPanelScreen
import top.yogiczy.mytv.ui.screens.leanback.components.LeanbackVisible
import top.yogiczy.mytv.ui.screens.leanback.monitor.LeanbackMonitorScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.LeanbackPanelChannelNoSelectScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.LeanbackPanelDateTimeScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.LeanbackPanelScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.LeanbackPanelTempScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.rememberLeanbackPanelChannelNoSelectState
import top.yogiczy.mytv.ui.screens.leanback.quickpanel.LeanbackQuickPanelScreen
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsScreen
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.screens.leanback.update.LeanBackUpdateViewModel
import top.yogiczy.mytv.ui.screens.leanback.update.LeanbackUpdateScreen
import top.yogiczy.mytv.ui.screens.leanback.video.LeanbackVideoScreen
import top.yogiczy.mytv.ui.screens.leanback.video.rememberLeanbackVideoPlayerState
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.ui.utils.handleLeanbackDragGestures
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents

@Composable
fun LeanbackMainContent(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    iptvGroupList: IptvGroupList = IptvGroupList(),
    epgList: EpgList = EpgList(),
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
    updateViewModel: LeanBackUpdateViewModel = viewModel(),  // 新增：更新ViewModel
    mainViewModel: LeanbackMainViewModel? = null,
) {
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    val videoPlayerState = rememberLeanbackVideoPlayerState(
        defaultAspectRatioProvider = {
            when (settingsViewModel.videoPlayerAspectRatio) {
                SP.VideoPlayerAspectRatio.ORIGINAL -> null
                SP.VideoPlayerAspectRatio.SIXTEEN_NINE -> 16f / 9f
                SP.VideoPlayerAspectRatio.FOUR_THREE -> 4f / 3f
                SP.VideoPlayerAspectRatio.AUTO -> {
                    configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()
                }
            }
        }
    )
    
    val mainContentState = rememberLeanbackMainContentState(
        videoPlayerState = videoPlayerState,
        iptvGroupList = iptvGroupList,
        onChannelsUnplayable = {
            // 连续 3 个频道播不通：提示直播源链接失效，再切换源（勾选同步到新源）
            mainViewModel?.handleUnplayableChannels { nextSource ->
                settingsViewModel.iptvSourceUrl = nextSource
            }
        },
        onSourceRejected = {
            // 列表为空 / 非 m3u：提示源文件错误，再切换源
            mainViewModel?.rejectCurrentSource(
                top.yogiczy.mytv.data.repositories.iptv.IptvSourceFailKind.Empty,
            ) { nextSource ->
                settingsViewModel.iptvSourceUrl = nextSource
            }
        },
        onPlaybackSucceeded = {
            mainViewModel?.clearCurrentSourceFailure()
        },
    )
    val panelChannelNoSelectState = rememberLeanbackPanelChannelNoSelectState(
        onChannelNoConfirm = {
            val channelNo = it.toInt() - 1

            if (channelNo in iptvGroupList.iptvList.indices) {
                mainContentState.changeCurrentIptv(iptvGroupList.iptvList[channelNo])
            }
        }
    )

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 防止切换到其他界面时焦点丢失
        // TODO 换一个更好的解决方案
        while (true) {
            if (!mainContentState.isPanelVisible
                && !mainContentState.isSettingsVisible
                && !mainContentState.isQuickPanelVisible
            ) {
                focusRequester.requestFocus()
            }
            delay(100)
        }
    }

    LeanbackBackPressHandledArea(
        modifier = modifier,
        onBackPressed = {
            if (mainContentState.isPanelVisible) mainContentState.isPanelVisible = false
            else if (mainContentState.isSettingsVisible) mainContentState.isSettingsVisible = false
            else if (mainContentState.isQuickPanelVisible) mainContentState.isQuickPanelVisible =
                false
            else onBackPressed()
        },
    ) {
        LeanbackVideoScreen(
            state = videoPlayerState,
            showMetadataProvider = { settingsViewModel.debugShowVideoPlayerMetadata },
            retryStatusProvider = { mainContentState.retryStatus },
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusable()
                .handleLeanbackKeyEvents(
                    onUp = {
                        if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToNext()
                        else mainContentState.changeCurrentIptvToPrev()
                    },
                    onDown = {
                        if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToPrev()
                        else mainContentState.changeCurrentIptvToNext()
                    },
                    onLeft = {
                        if (mainContentState.currentIptv.urlList.size > 1) {
                            mainContentState.changeCurrentIptv(
                                iptv = mainContentState.currentIptv,
                                urlIdx = mainContentState.currentIptvUrlIdx - 1,
                            )
                        }
                    },
                    onRight = {
                        if (mainContentState.currentIptv.urlList.size > 1) {
                            mainContentState.changeCurrentIptv(
                                iptv = mainContentState.currentIptv,
                                urlIdx = mainContentState.currentIptvUrlIdx + 1,
                            )
                        }
                    },
                    onSelect = { mainContentState.isPanelVisible = true },
                    onLongSelect = { mainContentState.isQuickPanelVisible = true },
                    onSettings = { mainContentState.isQuickPanelVisible = true },
                    onNumber = {
                        if (settingsViewModel.iptvChannelNoSelectEnable) {
                            panelChannelNoSelectState.input(it)
                        }
                    },
                    onLongDown = { mainContentState.isQuickPanelVisible = true },
                )
                .handleLeanbackDragGestures(
                    onSwipeDown = {
                        if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToNext()
                        else mainContentState.changeCurrentIptvToPrev()
                    },
                    onSwipeUp = {
                        if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToPrev()
                        else mainContentState.changeCurrentIptvToNext()
                    },
                    onSwipeRight = {
                        if (mainContentState.currentIptv.urlList.size > 1) {
                            mainContentState.changeCurrentIptv(
                                iptv = mainContentState.currentIptv,
                                urlIdx = mainContentState.currentIptvUrlIdx - 1,
                            )
                        }
                    },
                    onSwipeLeft = {
                        if (mainContentState.currentIptv.urlList.size > 1) {
                            mainContentState.changeCurrentIptv(
                                iptv = mainContentState.currentIptv,
                                urlIdx = mainContentState.currentIptvUrlIdx + 1,
                            )
                        }
                    },
                ),
        )

        CompositionLocalProvider(
            LocalDensity provides Density(
                density = LocalDensity.current.density * settingsViewModel.uiDensityScaleRatio,
                fontScale = LocalDensity.current.fontScale * settingsViewModel.uiFontScaleRatio,
            )
        ) {
            LeanbackVisible({
                !mainContentState.isTempPanelVisible
                        && !mainContentState.isSettingsVisible
                        && !mainContentState.isPanelVisible
                        && !mainContentState.isQuickPanelVisible
                        && panelChannelNoSelectState.channelNo.isEmpty()
            }) {
                LeanbackPanelDateTimeScreen(
                    showModeProvider = { settingsViewModel.uiTimeShowMode }
                )
            }

            LeanbackPanelChannelNoSelectScreen(
                channelNoProvider = { panelChannelNoSelectState.channelNo }
            )

            LeanbackVisible({
                mainContentState.isTempPanelVisible
                        && !mainContentState.isSettingsVisible
                        && !mainContentState.isPanelVisible
                        && !mainContentState.isQuickPanelVisible
                        && panelChannelNoSelectState.channelNo.isEmpty()
            }) {
                LeanbackPanelTempScreen(
                    channelNoProvider = { iptvGroupList.iptvIdx(mainContentState.currentIptv) + 1 },
                    currentIptvProvider = { mainContentState.currentIptv },
                    currentIptvUrlIdxProvider = { mainContentState.currentIptvUrlIdx },
                    currentProgrammesProvider = { epgList.currentProgrammes(mainContentState.currentIptv) },
                    showProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
                )
            }

            LeanbackVisible({ !settingsViewModel.uiUseClassicPanelScreen && mainContentState.isPanelVisible }) {
                LeanbackPanelScreen(
                    iptvGroupListProvider = { iptvGroupList },
                    epgListProvider = { epgList },
                    currentIptvProvider = { mainContentState.currentIptv },
                    currentIptvUrlIdxProvider = { mainContentState.currentIptvUrlIdx },
                    videoPlayerMetadataProvider = { videoPlayerState.metadata },
                    showProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
                    onIptvSelected = { mainContentState.changeCurrentIptv(it) },
                    onIptvFavoriteToggle = {
                        if (!settingsViewModel.iptvChannelFavoriteEnable) return@LeanbackPanelScreen

                        if (settingsViewModel.iptvChannelFavoriteList.contains(it.channelName)) {
                            settingsViewModel.iptvChannelFavoriteList -= it.channelName
                            LeanbackToastState.I.showToast("取消收藏: ${it.channelName}")
                        } else {
                            settingsViewModel.iptvChannelFavoriteList += it.channelName
                            LeanbackToastState.I.showToast("已收藏: ${it.channelName}")
                        }
                    },
                    iptvFavoriteListProvider = { settingsViewModel.iptvChannelFavoriteList.toImmutableList() },
                    iptvFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
                    onIptvFavoriteListVisibleChange = {
                        settingsViewModel.iptvChannelFavoriteListVisible = it
                    },
                    onClose = { mainContentState.isPanelVisible = false },
                )
            }

            LeanbackVisible({ settingsViewModel.uiUseClassicPanelScreen && mainContentState.isPanelVisible }) {
                LeanbackClassicPanelScreen(
                    iptvGroupListProvider = { iptvGroupList },
                    epgListProvider = { epgList },
                    currentIptvProvider = { mainContentState.currentIptv },
                    showProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
                    onIptvSelected = { mainContentState.changeCurrentIptv(it) },
                    onIptvFavoriteToggle = {
                        if (!settingsViewModel.iptvChannelFavoriteEnable) return@LeanbackClassicPanelScreen

                        if (settingsViewModel.iptvChannelFavoriteList.contains(it.channelName)) {
                            settingsViewModel.iptvChannelFavoriteList -= it.channelName
                            LeanbackToastState.I.showToast("取消收藏: ${it.channelName}")
                        } else {
                            settingsViewModel.iptvChannelFavoriteList += it.channelName
                            LeanbackToastState.I.showToast("已收藏: ${it.channelName}")
                        }
                    },
                    iptvFavoriteListProvider = { settingsViewModel.iptvChannelFavoriteList.toImmutableList() },
                    iptvFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
                    onIptvFavoriteListVisibleChange = {
                        settingsViewModel.iptvChannelFavoriteListVisible = it
                    },
                    onClose = { mainContentState.isPanelVisible = false },
                    iptvFavoriteEnableProvider = { settingsViewModel.iptvChannelFavoriteEnable }
                )
            }
        }

        LeanbackVisible({ mainContentState.isQuickPanelVisible && !mainContentState.isSettingsVisible }) {
            LeanbackQuickPanelScreen(
                currentIptvProvider = { mainContentState.currentIptv },
                currentIptvUrlIdxProvider = { mainContentState.currentIptvUrlIdx },
                currentProgrammesProvider = { epgList.currentProgrammes(mainContentState.currentIptv) },
                currentIptvChannelNoProvider = {
                    (iptvGroupList.iptvIdx(mainContentState.currentIptv) + 1).toString()
                        .padStart(2, '0')
                },
                videoPlayerMetadataProvider = { videoPlayerState.metadata },
                videoPlayerAspectRatioProvider = { videoPlayerState.aspectRatio },
                onChangeVideoPlayerAspectRatio = { videoPlayerState.aspectRatio = it },
                onIptvUrlIdxChange = {
                    mainContentState.changeCurrentIptv(
                        iptv = mainContentState.currentIptv,
                        urlIdx = it,
                    )
                },
                onClearCache = {
                    settingsViewModel.iptvPlayableHostList = emptySet()
                    coroutineScope.launch {
                        top.yogiczy.mytv.data.repositories.iptv.ChannelLogoCache.clear()
                        AppGlobal.cacheDir.deleteRecursively()
                    }
                    LeanbackToastState.I.showToast("缓存已清除，请重启应用")
                },
                onRefreshSource = {
                    mainViewModel?.refreshCurrentSource()
                    LeanbackToastState.I.showToast("正在刷新当前直播源...")
                },
                onSwitchSource = { url ->
                    // 切换源：保存到SP并触发刷新
                    settingsViewModel.iptvSourceUrl = url
                    mainContentState.isQuickPanelVisible = false
                    LeanbackToastState.I.showToast("正在切换直播源...")
                    mainContentState.resetFailureTracking()
                    mainViewModel?.refresh()
                },
                onMoreSettings = { mainContentState.isSettingsVisible = true },
                onClose = { mainContentState.isQuickPanelVisible = false },
                hasUpdateProvider = { updateViewModel.isUpdateAvailable },  // 传递更新状态
            )
        }

        LeanbackVisible({ mainContentState.isSettingsVisible }) {
            LeanbackSettingsScreen(
                onIptvSourceChanged = {
                    android.util.Log.i("MainContent", "收到 onIptvSourceChanged 回调")
                    // 直播源或节目单变更后，关闭设置并刷新主界面
                    mainContentState.isSettingsVisible = false
                    android.util.Log.i("MainContent", "mainViewModel: $mainViewModel")
                    mainContentState.resetFailureTracking()
                    mainViewModel?.refresh()
                    android.util.Log.i("MainContent", "已调用 mainViewModel.refresh()")
                },
                hasUpdateProvider = { updateViewModel.isUpdateAvailable },  // 传递更新状态
            )
        }

        LeanbackVisible({ settingsViewModel.debugShowFps }) {
            LeanbackMonitorScreen()
        }

        LeanbackUpdateScreen()
    }
}

@Composable
fun LeanbackBackPressHandledArea(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = Box(
    modifier = Modifier
        .onPreviewKeyEvent {
            if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                onBackPressed()
                true
            } else {
                false
            }
        }
        .then(modifier),
    content = content,
)