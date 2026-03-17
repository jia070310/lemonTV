package top.yogiczy.mytv.ui.screens.leanback.quickpanel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.rememberLeanbackChildPadding
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.utils.SP

@Composable
fun LeanbackQuickPanelIptvSourceDialog(
    modifier: Modifier = Modifier,
    showDialogProvider: () -> Boolean = { false },
    currentSourceUrlProvider: () -> String = { "" },
    onSourceSelected: (String) -> Unit = {},
    onDismissRequest: () -> Unit = {},
) {
    val childPadding = rememberLeanbackChildPadding()
    val coroutineScope = rememberCoroutineScope()
    var showQrcodeDialog by remember { mutableStateOf(false) }  // 新增：二维码对话框状态
    
    // 获取所有源地址列表（默认源 + 历史源 + 自定义源）
    var sourceList by remember { mutableStateOf<List<String>>(emptyList()) }
    var nameMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    // 定期刷新列表，以便网页端推送后能自动显示
    LaunchedEffect(showDialogProvider()) {
        if (showDialogProvider()) {
            while (true) {
                val allSources = (Constants.IPTV_SOURCE_DEFAULT_LIST + SP.iptvSourceUrlHistoryList)
                    .distinct()
                sourceList = allSources
                nameMap = SP.iptvSourceNameMap
                delay(1000)
            }
        }
    }

    if (showDialogProvider()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.5f)),  // 改为0.5f，与菜单遮罩透明度一致
            contentAlignment = Alignment.TopEnd,
        ) {
            Surface(
                modifier = Modifier
                    .width(320.dp)
                    .height(320.dp)
                    .padding(top = 50.dp, end = 50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = Color(0xB3000000),  // 70%不透明度，更明显的透明效果
                ),
            ) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 标题
                    Text(
                        text = "选择直播源",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    // 源列表
                    TvLazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        itemsIndexed(sourceList) { index, url ->
                            val focusRequester = remember { FocusRequester() }
                            var isFocused by remember { mutableStateOf(false) }
                            val isCurrentSource = url == currentSourceUrlProvider()
                            
                            // 第一个项目自动获取焦点
                            LaunchedEffect(Unit) {
                                if (index == 0) {
                                    focusRequester.requestFocus()
                                }
                            }

                            ListItem(
                                selected = isFocused,
                                onClick = {
                                    onSourceSelected(url)
                                    onDismissRequest()
                                },
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { isFocused = it.isFocused },
                                colors = androidx.tv.material3.ListItemDefaults.colors(
                                    focusedContainerColor = Color(0xFFfddd0e),
                                    focusedContentColor = Color.Black,
                                    selectedContainerColor = Color(0xFFfddd0e),
                                    selectedContentColor = Color.Black,
                                ),
                                scale = androidx.tv.material3.ListItemDefaults.scale(
                                    focusedScale = 1.02f,
                                ),
                                headlineContent = {
                                    Text(
                                        text = nameMap[url] ?: when {
                                            url == Constants.IPTV_SOURCE_URL -> "默认源"
                                            url.contains("migu.m3u") -> "咪咕源"
                                            url.contains("httop.m3u") && !url.contains("merged") -> "HTTOP源"
                                            url.contains("httop_merged") -> "HTTOP合并源"
                                            url.contains("iptv.m3u") && !url.contains("merged") -> "IPTV源"
                                            url.contains("iptv_merged") -> "IPTV合并源"
                                            else -> "自定义源"
                                        },
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                supportingContent = if (isFocused || isCurrentSource) {
                                    {
                                        Text(
                                            text = url,
                                            maxLines = if (isFocused) 2 else 1,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                } else null,
                                trailingContent = if (isCurrentSource) {
                                    {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "当前源",
                                            tint = if (isFocused) 
                                                Color.Black
                                            else
                                                Color(0xFFfddd0e)
                                        )
                                    }
                                } else null,
                            )
                        }
                        
                        // 添加自定义源按钮
                        item {
                            val focusRequester = remember { FocusRequester() }
                            var isFocused by remember { mutableStateOf(false) }
                            
                            ListItem(
                                selected = isFocused,
                                onClick = {
                                    showQrcodeDialog = true
                                },
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { isFocused = it.isFocused },
                                colors = androidx.tv.material3.ListItemDefaults.colors(
                                    focusedContainerColor = Color(0xFFfddd0e),
                                    focusedContentColor = Color.Black,
                                    selectedContainerColor = Color(0xFFfddd0e),
                                    selectedContentColor = Color.Black,
                                ),
                                scale = androidx.tv.material3.ListItemDefaults.scale(
                                    focusedScale = 1.02f,
                                ),
                                headlineContent = {
                                    Text(
                                        text = "＋ 添加自定义源",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                supportingContent = if (isFocused) {
                                    {
                                        Text(
                                            text = "请在浏览器访问本机IP地址:10481端口进行配置",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                } else null,
                            )
                        }
                    }
                }
            }
        }
        
        // 二维码对话框
        top.yogiczy.mytv.ui.screens.leanback.components.LeanbackQrcodeDialog(
            text = top.yogiczy.mytv.ui.utils.HttpServer.serverUrl,
            description = "扫码前往设置页面",
            showDialogProvider = { showQrcodeDialog },
            onDismissRequest = { showQrcodeDialog = false },
        )
    }
}
