package top.yogiczy.mytv.ui.screens.leanback.settings.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text as TvText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.GithubProxyTester
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents
import top.yogiczy.mytv.utils.humanizeMs
import kotlin.math.max

@Composable
fun LeanbackSettingsCategoryHttp(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
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
                        Log.i("SettingsCategoryHttp", "已切换加速器: $proxy")
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

@Composable
private fun LeanbackSettingsGithubProxyDialog(
    modifier: Modifier = Modifier,
    showDialogProvider: () -> Boolean = { false },
    onDismissRequest: () -> Unit = {},
    proxyListProvider: () -> ImmutableList<String> = { persistentListOf() },
    currentProxyProvider: () -> String = { Constants.GITHUB_PROXY },
    onSelected: (String) -> Unit = {},
    onDeleted: (String) -> Unit = {},
    onApplyBest: (String) -> Unit = {},
) {
    var proxyList by remember { mutableStateOf(proxyListProvider()) }
    val currentProxy = currentProxyProvider()
    var testing by remember { mutableStateOf(false) }
    var testMap by remember { mutableStateOf<Map<String, GithubProxyTester.Result>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(showDialogProvider()) {
        if (showDialogProvider()) {
            while (true) {
                delay(1000)
                val latest = SP.getGithubProxyList().toImmutableList()
                if (latest != proxyList) {
                    proxyList = latest
                    Log.i(
                        "SettingsCategoryHttp",
                        "加速器列表已刷新，数量: ${latest.size}",
                    )
                }
            }
        } else {
            testing = false
            testMap = emptyMap()
        }
    }

    fun runSpeedTest() {
        if (testing) return
        scope.launch {
            testing = true
            LeanbackToastState.I.showToast("正在测速…")
            try {
                val results = GithubProxyTester.testProxies(proxyList)
                testMap = results.associateBy { it.proxy }
                val best = results.firstOrNull { it.ok }
                if (best != null) {
                    LeanbackToastState.I.showToast("最优 ${best.ms}ms：${best.proxy}")
                    onApplyBest(best.proxy)
                } else {
                    val err = results.firstOrNull()?.error
                    LeanbackToastState.I.showToast(
                        if (err.isNullOrBlank()) "全部测速失败，请检查网络"
                        else "全部测速失败：$err",
                    )
                }
            } catch (ex: Exception) {
                LeanbackToastState.I.showToast("测速失败：${ex.message ?: "未知错误"}")
                Log.e("SettingsCategoryHttp", "测速失败", ex)
            } finally {
                testing = false
            }
        }
    }

    if (showDialogProvider()) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = modifier,
            onDismissRequest = { if (!testing) onDismissRequest() },
            confirmButton = {
                Text(
                    text = if (testing) "测速中，请稍候…"
                    else "短按切换；顶部可测速；长按删除自定义项；后台可添加新的加速器地址",
                )
            },
            title = { Text("选择 GitHub 加速器") },
            text = {
                var hasFocused by remember { mutableStateOf(false) }
                val bestProxy = testMap.values
                    .filter { it.ok && it.ms >= 0 }
                    .minByOrNull { it.ms }
                    ?.proxy

                TvLazyColumn(
                    state = TvLazyListState(
                        max(0, proxyList.indexOf(currentProxy)),
                    ),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        val focusRequester = remember { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            if (!hasFocused) {
                                hasFocused = true
                                focusRequester.requestFocus()
                            }
                        }

                        CompositionLocalProvider(
                            LocalContentColor provides if (isFocused) Color.Black
                            else MaterialTheme.colorScheme.onBackground
                        ) {
                            androidx.tv.material3.ListItem(
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                    .handleLeanbackKeyEvents(
                                        onSelect = {
                                            if (isFocused) runSpeedTest()
                                            else focusRequester.requestFocus()
                                        },
                                    ),
                                colors = ListItemDefaults.colors(
                                    focusedContainerColor = Color(0xFFfddd0e),
                                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.5f
                                    ),
                                ),
                                selected = testing,
                                onClick = {},
                                headlineContent = {
                                    TvText(
                                        text = if (testing) "测速中…" else "一键测速（电视本机网络）",
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = "speed test",
                                        tint = if (isFocused) Color.Black
                                        else MaterialTheme.colorScheme.primary,
                                    )
                                },
                            )
                        }
                    }

                    items(proxyList) { proxy ->
                        val focusRequester = remember { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }
                        val isRecommended = proxy in Constants.GITHUB_PROXY_CANDIDATES
                        val result = testMap[proxy]
                        val trailingLabel = when {
                            result == null -> null
                            result.ok -> "${result.ms}ms" + if (proxy == bestProxy) " · 最优" else ""
                            else -> result.error?.take(18) ?: "失败"
                        }

                        CompositionLocalProvider(
                            LocalContentColor provides if (isFocused) Color.Black
                            else MaterialTheme.colorScheme.onBackground
                        ) {
                            androidx.tv.material3.ListItem(
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                    .handleLeanbackKeyEvents(
                                        onSelect = {
                                            if (isFocused) onSelected(proxy)
                                            else focusRequester.requestFocus()
                                        },
                                        onLongSelect = {
                                            if (isFocused && !isRecommended) {
                                                onDeleted(proxy)
                                                proxyList = SP.getGithubProxyList().toImmutableList()
                                            } else {
                                                focusRequester.requestFocus()
                                            }
                                        },
                                    ),
                                colors = ListItemDefaults.colors(
                                    focusedContainerColor = Color(0xFFfddd0e),
                                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.5f
                                    ),
                                ),
                                selected = proxy == currentProxy,
                                onClick = {},
                                headlineContent = {
                                    TvText(
                                        text = when {
                                            proxy == Constants.GITHUB_PROXY -> "默认 · $proxy"
                                            isRecommended -> "推荐 · $proxy"
                                            else -> "自定义 · $proxy"
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = if (isFocused) Int.MAX_VALUE else 2,
                                    )
                                },
                                trailingContent = {
                                    when {
                                        trailingLabel != null -> {
                                            TvText(text = trailingLabel)
                                        }
                                        proxy == currentProxy -> {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "checked",
                                                tint = if (isFocused) Color.Black
                                                else MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            },
        )
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryHttpPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryHttp(
            modifier = Modifier.padding(20.dp),
        )
    }
}
