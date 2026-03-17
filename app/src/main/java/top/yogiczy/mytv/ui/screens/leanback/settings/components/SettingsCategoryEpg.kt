package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.components.LeanbackQrcodeDialog
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.HttpServer
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents
import kotlin.math.max

@Composable
fun LeanbackSettingsCategoryEpg(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
    onEpgSourceChanged: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "节目单启用",
                supportingContent = "首次加载时可能会有跳帧风险",
                trailingContent = {
                    Switch(checked = settingsViewModel.epgEnable, onCheckedChange = null)
                },
                onSelected = {
                    settingsViewModel.epgEnable = !settingsViewModel.epgEnable
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "节目单刷新时间阈值",
                supportingContent = "短按增加1小时，长按设为0小时；时间不到${settingsViewModel.epgRefreshTimeThreshold}:00节目单将不会刷新",
                trailingContent = "${settingsViewModel.epgRefreshTimeThreshold}小时",
                onSelected = {
                    settingsViewModel.epgRefreshTimeThreshold =
                        (settingsViewModel.epgRefreshTimeThreshold + 1) % 12
                },
                onLongSelected = {
                    settingsViewModel.epgRefreshTimeThreshold = 0
                },
            )
        }

        item {
            var showDialog by remember { mutableStateOf(false) }

            LeanbackSettingsCategoryListItem(
                headlineContent = "自定义节目单",
                supportingContent = if (settingsViewModel.epgXmlUrl != Constants.EPG_XML_URL)
                    settingsViewModel.epgXmlUrl else null,
                trailingContent = if (settingsViewModel.epgXmlUrl != Constants.EPG_XML_URL) "已启用" else "未启用",
                onSelected = { 
                    // 打开对话框前，刷新历史记录列表
                    settingsViewModel.epgXmlUrlHistoryList = SP.epgXmlUrlHistoryList
                    showDialog = true 
                },
                remoteConfig = true,
            )

            LeanbackSettingsEpgSourceHistoryDialog(
                showDialogProvider = { showDialog },
                onDismissRequest = { showDialog = false },
                epgXmlUrlHistoryProvider = {
                    settingsViewModel.epgXmlUrlHistoryList.filter {
                        it != Constants.EPG_XML_URL
                    }.toImmutableList()
                },
                currentEpgXmlUrlProvider = { settingsViewModel.epgXmlUrl },
                onSelected = {
                    showDialog = false
                    if (settingsViewModel.epgXmlUrl != it) {
                        settingsViewModel.epgXmlUrl = it
                        // 通知主界面刷新节目单（refresh 中会自动清除缓存）
                        onEpgSourceChanged()
                    }
                },
                onDeleted = {
                    settingsViewModel.epgXmlUrlHistoryList -= it
                }
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "清除缓存",
                supportingContent = "短按清除节目单缓存文件",
                onSelected = {
                    coroutineScope.launch { EpgRepository().clearCache() }
                    LeanbackToastState.I.showToast("清除缓存成功")
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "刷新节目单",
                supportingContent = "清除缓存并重新加载节目单数据",
                onSelected = {
                    coroutineScope.launch { 
                        EpgRepository().clearCache()
                        LeanbackToastState.I.showToast("已清除缓存，请稍候...")
                    }
                    // 通知主界面刷新节目单
                    onEpgSourceChanged()
                },
            )
        }
    }
}

@Composable
private fun LeanbackSettingsEpgSourceHistoryDialog(
    modifier: Modifier = Modifier,
    showDialogProvider: () -> Boolean = { false },
    onDismissRequest: () -> Unit = {},
    epgXmlUrlHistoryProvider: () -> ImmutableList<String> = { persistentListOf() },
    currentEpgXmlUrlProvider: () -> String = { Constants.EPG_XML_URL },
    onSelected: (String) -> Unit = {},
    onDeleted: (String) -> Unit = {},
) {
    // 预设地址列表（始终显示在最前面，不可删除）
    val presetUrls = listOf(Constants.EPG_XML_URL, Constants.EPG_XML_URL_BACKUP)

    // 使用 State 来存储历史记录，以便可以动态更新
    var epgXmlUrlHistory by remember {
        mutableStateOf(
            presetUrls + epgXmlUrlHistoryProvider().filter { it !in presetUrls }
        )
    }
    var nameMap by remember { mutableStateOf(SP.iptvSourceNameMap) }
    val currentEpgXmlUrl = currentEpgXmlUrlProvider()

    // 定期刷新历史记录，以便网页端推送后能自动显示
    LaunchedEffect(showDialogProvider()) {
        if (showDialogProvider()) {
            while (true) {
                delay(1000) // 每秒检查一次
                // 直接从 SP 读取最新数据，预设地址始终在最前面
                val newHistory = presetUrls + SP.epgXmlUrlHistoryList.filter { it !in presetUrls }
                val newNameMap = SP.iptvSourceNameMap
                if (newHistory != epgXmlUrlHistory || newNameMap != nameMap) {
                    epgXmlUrlHistory = newHistory
                    nameMap = newNameMap
                    android.util.Log.i("SettingsCategoryEpg", "检测到节目单历史记录更新，已刷新列表，当前数量: ${newHistory.size}")
                }
            }
        }
    }

    if (showDialogProvider()) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = modifier,
            onDismissRequest = onDismissRequest,
            containerColor = androidx.compose.ui.graphics.Color(0xFF2a2a2a),
            confirmButton = { 
                Text(
                    text = "短按切换；长按删除历史记录",
                    color = androidx.compose.ui.graphics.Color(0xFFfddd0e)
                ) 
            },
            title = { Text("历史节目单") },
            text = {
                var hasFocused by remember { mutableStateOf(false) }

                TvLazyColumn(
                    state = TvLazyListState(
                        max(0, epgXmlUrlHistory.indexOf(currentEpgXmlUrl) - 2),
                    ),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(epgXmlUrlHistory) { url ->
                        val focusRequester = remember { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }
                        val isSelected = currentEpgXmlUrl == url

                        LaunchedEffect(Unit) {
                            if (url == currentEpgXmlUrl && !hasFocused) {
                                hasFocused = true
                                focusRequester.requestFocus()
                            }
                        }

                        androidx.tv.material3.ListItem(
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                .handleLeanbackKeyEvents(
                                    onSelect = {
                                        if (isFocused) onSelected(url)
                                        else focusRequester.requestFocus()
                                    },
                                    onLongSelect = {
                                        // 预设地址不允许删除
                                        if (isFocused && url !in presetUrls) onDeleted(url)
                                        else focusRequester.requestFocus()
                                    }
                                ),
                            selected = isSelected,
                            onClick = { },
                            colors = androidx.tv.material3.ListItemDefaults.colors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF1f1f1f),
                                focusedContainerColor = androidx.compose.ui.graphics.Color(0xFFfddd0e),
                                selectedContainerColor = androidx.compose.ui.graphics.Color(0xFF1f1f1f),
                                contentColor = androidx.compose.ui.graphics.Color.White,
                                focusedContentColor = androidx.compose.ui.graphics.Color.Black,
                                selectedContentColor = androidx.compose.ui.graphics.Color.White,
                            ),
                            shape = androidx.tv.material3.ListItemDefaults.shape(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                focusedShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                selectedShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            ),
                            border = androidx.tv.material3.ListItemDefaults.border(
                                border = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x0DFFFFFF)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                ),
                                focusedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(2.dp, androidx.compose.ui.graphics.Color(0xFFfddd0e)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                ),
                                selectedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x0DFFFFFF)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                ),
                            ),
                            headlineContent = {
                                androidx.tv.material3.Text(
                                    text = when (url) {
                                        Constants.EPG_XML_URL -> "默认节目单"
                                        Constants.EPG_XML_URL_BACKUP -> "备用节目单"
                                        else -> nameMap[url] ?: url
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = if (isFocused) Int.MAX_VALUE else 2,
                                )
                            },
                            supportingContent = if (url in presetUrls || nameMap.containsKey(url)) {
                                {
                                    androidx.tv.material3.Text(
                                        text = url,
                                        maxLines = if (isFocused) Int.MAX_VALUE else 1,
                                    )
                                }
                            } else null,
                            trailingContent = {
                                if (currentEpgXmlUrl == url) {
                                    androidx.tv.material3.Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "checked",
                                        tint = if (isFocused) 
                                            androidx.compose.ui.graphics.Color.Black
                                        else
                                            androidx.compose.ui.graphics.Color(0xFFfddd0e)
                                    )
                                }
                            },
                        )
                    }

                    item {
                        val focusRequester = remember { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }
                        var showDialog by remember { mutableStateOf(false) }

                        androidx.tv.material3.ListItem(
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                .handleLeanbackKeyEvents(
                                    onSelect = {
                                        if (isFocused) showDialog = true
                                        else focusRequester.requestFocus()
                                    },
                                ),
                            selected = false,
                            onClick = {},
                            colors = androidx.tv.material3.ListItemDefaults.colors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF1f1f1f),
                                focusedContainerColor = androidx.compose.ui.graphics.Color(0xFFfddd0e),
                                selectedContainerColor = androidx.compose.ui.graphics.Color(0xFF1f1f1f),
                                contentColor = androidx.compose.ui.graphics.Color.White,
                                focusedContentColor = androidx.compose.ui.graphics.Color.Black,
                                selectedContentColor = androidx.compose.ui.graphics.Color.White,
                            ),
                            shape = androidx.tv.material3.ListItemDefaults.shape(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                focusedShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                selectedShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            ),
                            border = androidx.tv.material3.ListItemDefaults.border(
                                border = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x0DFFFFFF)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                ),
                                focusedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(2.dp, androidx.compose.ui.graphics.Color(0xFFfddd0e)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                ),
                                selectedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x0DFFFFFF)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                ),
                            ),
                            headlineContent = {
                                androidx.tv.material3.Text("添加其他节目单")
                            },
                        )

                        LeanbackQrcodeDialog(
                            text = HttpServer.serverUrl,
                            description = "扫码前往设置页面",
                            showDialogProvider = { showDialog },
                            onDismissRequest = { showDialog = false },
                        )
                    }
                }
            }
        )
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryEpgPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryEpg(
            modifier = Modifier.padding(20.dp),
            settingsViewModel = LeanbackSettingsViewModel().apply {
                epgXmlUrl = "https://iptv-org.github.io/epg.xml"
                epgXmlUrlHistoryList = setOf(
                    "https://iptv-org.github.io/epg.xml",
                    "https://iptv-org.github.io/epg2.xml",
                    "https://iptv-org.github.io/epg3.xml",
                )
            }
        )
    }
}