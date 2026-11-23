package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import kotlinx.collections.immutable.persistentListOf
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.utils.Logger
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun LeanbackSettingsCategoryDebug(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
    history: List<Logger.HistoryItem> = emptyList(),
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val historySorted = remember(history) {
        history.sortedByDescending { it.time }
    }

    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "显示FPS",
                supportingContent = "在屏幕左上角显示fps和柱状图",
                trailingContent = {
                    Switch(checked = settingsViewModel.debugShowFps, onCheckedChange = null)
                },
                onSelected = {
                    settingsViewModel.debugShowFps = !settingsViewModel.debugShowFps
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "显示播放器信息",
                supportingContent = "显示播放器详细信息（编码、解码器、采样率等）",
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.debugShowVideoPlayerMetadata,
                        onCheckedChange = null
                    )
                },
                onSelected = {
                    settingsViewModel.debugShowVideoPlayerMetadata =
                        !settingsViewModel.debugShowVideoPlayerMetadata
                },
            )
        }

        items(historySorted) {
            LeanbackSettingsCategoryListItem(
                headlineContent = "${it.level.toString()[0]} ${it.tag}",
                supportingContent = it.message,
                trailingContent = timeFormat.format(it.time),
            )
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryDebugPreview() {
    LeanbackTheme {
        LeanbackSettingsCategoryDebug(
            modifier = Modifier.padding(20.dp),
            history = persistentListOf(
                Logger.HistoryItem(
                    level = Logger.LevelType.ERROR,
                    tag = "Debug",
                    message = "This is a test message",
                    time = System.currentTimeMillis(),
                ),
            ),
        )
    }
}
