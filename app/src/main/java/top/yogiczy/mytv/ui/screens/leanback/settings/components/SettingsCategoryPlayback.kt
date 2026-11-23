package top.yogiczy.mytv.ui.screens.leanback.settings.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import kotlinx.collections.immutable.toImmutableList
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.humanizeMs
import kotlin.math.max

/**
 * 播放器 + 网络（合并为一页）
 */
@Composable
fun LeanbackSettingsCategoryPlayback(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "全局画面比例",
                trailingContent = when (settingsViewModel.videoPlayerAspectRatio) {
                    SP.VideoPlayerAspectRatio.ORIGINAL -> "原始"
                    SP.VideoPlayerAspectRatio.SIXTEEN_NINE -> "16:9"
                    SP.VideoPlayerAspectRatio.FOUR_THREE -> "4:3"
                    SP.VideoPlayerAspectRatio.AUTO -> "自动拉伸"
                },
                onSelected = {
                    settingsViewModel.videoPlayerAspectRatio =
                        SP.VideoPlayerAspectRatio.entries.let {
                            it[(it.indexOf(settingsViewModel.videoPlayerAspectRatio) + 1) % it.size]
                        }
                },
            )
        }

        item {
            val min = 1000 * 5L
            val maxTimeout = 1000 * 30L
            val step = 1000 * 5L

            LeanbackSettingsCategoryListItem(
                headlineContent = "播放器加载超时",
                supportingContent = "影响超时换源、断线重连",
                trailingContent = settingsViewModel.videoPlayerLoadTimeout.humanizeMs(),
                onSelected = {
                    settingsViewModel.videoPlayerLoadTimeout =
                        max(min, (settingsViewModel.videoPlayerLoadTimeout + step) % (maxTimeout + step))
                },
            )
        }

        item {
            var showDialog by remember { mutableStateOf(false) }

            LeanbackSettingsCategoryListItem(
                headlineContent = "播放器自定义UA",
                supportingContent = if (settingsViewModel.videoPlayerUserAgent != Constants.VIDEO_PLAYER_USER_AGENT)
                    settingsViewModel.videoPlayerUserAgent else null,
                trailingContent = if (settingsViewModel.videoPlayerUserAgent != Constants.VIDEO_PLAYER_USER_AGENT)
                    "已启用" else "未启用",
                onSelected = {
                    settingsViewModel.videoPlayerUserAgentHistoryList = SP.videoPlayerUserAgentHistoryList
                    showDialog = true
                },
                remoteConfig = true,
            )

            LeanbackSettingsVideoPlayerUAHistoryDialog(
                showDialogProvider = { showDialog },
                onDismissRequest = { showDialog = false },
                uaHistoryProvider = {
                    settingsViewModel.videoPlayerUserAgentHistoryList.filter {
                        it != Constants.VIDEO_PLAYER_USER_AGENT &&
                            it != Constants.VIDEO_PLAYER_USER_AGENT_APTV
                    }.toImmutableList()
                },
                currentUAProvider = { settingsViewModel.videoPlayerUserAgent },
                onSelected = {
                    showDialog = false
                    if (settingsViewModel.videoPlayerUserAgent != it) {
                        settingsViewModel.videoPlayerUserAgent = it
                        Log.i("SettingsCategoryPlayback", "已切换UA: $it")
                    }
                },
                onDeleted = {
                    settingsViewModel.videoPlayerUserAgentHistoryList -= it
                }
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "启用 GitHub 加速器",
                supportingContent = if (settingsViewModel.githubProxyEnabled) {
                    "自动把直播源里的 GitHub 地址换成当前加速器"
                } else {
                    "已关闭，直播源按填写的地址原样拉取，保留链接自带的加速器"
                },
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.githubProxyEnabled,
                        onCheckedChange = null,
                    )
                },
                onSelected = {
                    settingsViewModel.githubProxyEnabled = !settingsViewModel.githubProxyEnabled
                },
            )
        }

        item {
            var showDialog by remember { mutableStateOf(false) }

            LeanbackSettingsCategoryListItem(
                headlineContent = "GitHub 加速器",
                supportingContent = "${settingsViewModel.githubProxy}\n后台可添加新的加速器地址",
                trailingContent = "点击切换 / 测速",
                onSelected = {
                    settingsViewModel.githubProxyHistoryList = SP.githubProxyHistoryList
                    showDialog = true
                },
            )

            LeanbackSettingsGithubProxyDialog(
                showDialogProvider = { showDialog },
                onDismissRequest = { showDialog = false },
                proxyListProvider = {
                    SP.getGithubProxyList().toImmutableList()
                },
                currentProxyProvider = { settingsViewModel.githubProxy },
                onSelected = { proxy ->
                    showDialog = false
                    if (settingsViewModel.githubProxy != proxy) {
                        settingsViewModel.githubProxy = proxy
                        LeanbackToastState.I.showToast("加速器已切换，建议重启应用生效")
                        Log.i("SettingsCategoryPlayback", "已切换加速器: $proxy")
                    }
                },
                onDeleted = { proxy ->
                    if (proxy !in Constants.GITHUB_PROXY_CANDIDATES) {
                        settingsViewModel.githubProxyHistoryList =
                            settingsViewModel.githubProxyHistoryList - proxy
                    }
                },
                onApplyBest = { proxy ->
                    if (settingsViewModel.githubProxy != proxy) {
                        settingsViewModel.githubProxy = proxy
                        LeanbackToastState.I.showToast("已选用最优加速器，建议重启应用生效")
                    } else {
                        LeanbackToastState.I.showToast("当前已是最优加速器")
                    }
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "HTTP请求重试次数",
                supportingContent = "影响直播源、节目单数据获取",
                trailingContent = Constants.HTTP_RETRY_COUNT.toString(),
                locK = true,
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "HTTP请求重试间隔时间",
                supportingContent = "影响直播源、节目单数据获取",
                trailingContent = Constants.HTTP_RETRY_INTERVAL.humanizeMs(),
                locK = true,
            )
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryPlaybackPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryPlayback(
            modifier = Modifier.padding(20.dp),
        )
    }
}
