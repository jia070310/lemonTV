package top.yogiczy.mytv.ui.screens.leanback.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/** 设置页上方两大项 */
enum class LeanbackSettingsGroup(
    val title: String,
) {
    CHANNEL("直播设置"),
    SYSTEM("关于/其它"),
}

/** 左侧子项（随上方大项切换） */
enum class LeanbackSettingsCategories(
    val icon: ImageVector,
    val title: String,
    val group: LeanbackSettingsGroup,
) {
    IPTV(Icons.Default.LiveTv, "直播源", LeanbackSettingsGroup.CHANNEL),
    EPG(Icons.Default.Menu, "节目单", LeanbackSettingsGroup.CHANNEL),
    FAVORITE(Icons.Default.Star, "收藏", LeanbackSettingsGroup.CHANNEL),
    UI(Icons.Default.DisplaySettings, "界面", LeanbackSettingsGroup.CHANNEL),
    PLAYBACK(Icons.Default.SmartDisplay, "播放与网络", LeanbackSettingsGroup.CHANNEL),
    REMOTE(Icons.Default.MoreHoriz, "手机/电脑设置", LeanbackSettingsGroup.CHANNEL),

    ABOUT(Icons.Default.Info, "关于", LeanbackSettingsGroup.SYSTEM),
    APP(Icons.Default.Settings, "应用", LeanbackSettingsGroup.SYSTEM),
    DEBUG(Icons.Default.BugReport, "调试", LeanbackSettingsGroup.SYSTEM),
    ;

    companion object {
        fun ofGroup(group: LeanbackSettingsGroup): List<LeanbackSettingsCategories> =
            entries.filter { it.group == group }

        fun defaultOf(group: LeanbackSettingsGroup): LeanbackSettingsCategories =
            ofGroup(group).first()
    }
}
