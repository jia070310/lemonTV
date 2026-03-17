package top.yogiczy.mytv.ui.screens.leanback.settings.components

import android.util.Log
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ListItemDefaults
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents
import top.yogiczy.mytv.utils.humanizeMs
import kotlin.math.max

@Composable
fun LeanbackSettingsCategoryVideoPlayer(
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
            val max = 1000 * 30L
            val step = 1000 * 5L

            LeanbackSettingsCategoryListItem(
                headlineContent = "播放器加载超时",
                supportingContent = "影响超时换源、断线重连",
                trailingContent = settingsViewModel.videoPlayerLoadTimeout.humanizeMs(),
                onSelected = {
                    settingsViewModel.videoPlayerLoadTimeout =
                        max(min, (settingsViewModel.videoPlayerLoadTimeout + step) % (max + step))
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
                    // 打开对话框前，刷新历史记录列表
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
                        it != Constants.VIDEO_PLAYER_USER_AGENT
                    }.toImmutableList()
                },
                currentUAProvider = { settingsViewModel.videoPlayerUserAgent },
                onSelected = {
                    showDialog = false
                    if (settingsViewModel.videoPlayerUserAgent != it) {
                        settingsViewModel.videoPlayerUserAgent = it
                        Log.i("SettingsCategoryVideoPlayer", "已切换UA: $it")
                    }
                },
                onDeleted = {
                    settingsViewModel.videoPlayerUserAgentHistoryList -= it
                }
            )
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryHttpPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryVideoPlayer(
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun LeanbackSettingsVideoPlayerUAHistoryDialog(
    modifier: Modifier = Modifier,
    showDialogProvider: () -> Boolean = { false },
    onDismissRequest: () -> Unit = {},
    uaHistoryProvider: () -> ImmutableList<String> = { persistentListOf() },
    currentUAProvider: () -> String = { Constants.VIDEO_PLAYER_USER_AGENT },
    onSelected: (String) -> Unit = {},
    onDeleted: (String) -> Unit = {},
) {
    // 使用 State 来存储历史记录，以便可以动态更新
    var uaHistory by remember { mutableStateOf(listOf(Constants.VIDEO_PLAYER_USER_AGENT) + uaHistoryProvider()) }
    val currentUA = currentUAProvider()

    // 定期刷新历史记录，以便网页端推送后能自动显示
    LaunchedEffect(showDialogProvider()) {
        if (showDialogProvider()) {
            while (true) {
                delay(1000) // 每秒检查一次
                // 直接从 SP 读取最新数据
                val newHistory = listOf(Constants.VIDEO_PLAYER_USER_AGENT) + SP.videoPlayerUserAgentHistoryList.filter {
                    it != Constants.VIDEO_PLAYER_USER_AGENT
                }
                if (newHistory != uaHistory) {
                    uaHistory = newHistory
                    Log.i("SettingsCategoryVideoPlayer", "检测到UA历史记录更新，已刷新列表，当前数量: ${newHistory.size}")
                }
            }
        }
    }

    if (showDialogProvider()) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = modifier,
            onDismissRequest = onDismissRequest,
            confirmButton = { Text(text = "短按切换；长按删除历史记录") },
            title = { Text("历史UA") },
            text = {
                var hasFocused by remember { mutableStateOf(false) }

                TvLazyColumn(
                    state = TvLazyListState(
                        max(0, uaHistory.indexOf(currentUA) - 2),
                    ),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uaHistory) { ua ->
                        val focusRequester = remember { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            if (ua == currentUA && !hasFocused) {
                                hasFocused = true
                                focusRequester.requestFocus()
                            }
                        }

                        CompositionLocalProvider(
                            LocalContentColor provides if (isFocused) androidx.compose.ui.graphics.Color.Black
                            else MaterialTheme.colorScheme.onBackground
                        ) {
                            androidx.tv.material3.ListItem(
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                    .handleLeanbackKeyEvents(
                                        onSelect = {
                                            if (isFocused) onSelected(ua)
                                            else focusRequester.requestFocus()
                                        },
                                        onLongSelect = {
                                            if (isFocused) onDeleted(ua)
                                            else focusRequester.requestFocus()
                                        }
                                    ),
                                colors = ListItemDefaults.colors(
                                    focusedContainerColor = androidx.compose.ui.graphics.Color(0xFFfddd0e),
                                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                ),
                                selected = currentUA == ua,
                                onClick = { },
                                headlineContent = {
                                    androidx.tv.material3.Text(
                                        text = if (ua == Constants.VIDEO_PLAYER_USER_AGENT) "默认UA" else ua,
                                        maxLines = if (isFocused) Int.MAX_VALUE else 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                supportingContent = if (ua == Constants.VIDEO_PLAYER_USER_AGENT) {
                                    {
                                        androidx.tv.material3.Text(
                                            text = Constants.VIDEO_PLAYER_USER_AGENT,
                                            maxLines = if (isFocused) Int.MAX_VALUE else 1,
                                        )
                                    }
                                } else null,
                                trailingContent = if (currentUA == ua) {
                                    { 
                                        androidx.tv.material3.Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "checked",
                                            tint = if (isFocused) 
                                                androidx.compose.ui.graphics.Color.Black
                                            else
                                                androidx.compose.ui.graphics.Color(0xFFfddd0e)
                                        )
                                    }
                                } else null,
                            )
                        }
                    }
                }
            },
        )
    }
}
