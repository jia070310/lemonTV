package top.yogiczy.mytv.ui.screens.leanback.components

import androidx.compose.ui.graphics.Color
import top.yogiczy.mytv.data.repositories.iptv.IptvSourceFailKind

fun IptvSourceFailKind.hintColor(onLightBackground: Boolean = true): Color = when (this) {
    IptvSourceFailKind.Timeout,
    IptvSourceFailKind.FetchFailed -> if (onLightBackground) Color(0xFFB91C1C) else Color(0xFFF87171)
    IptvSourceFailKind.Empty -> if (onLightBackground) Color(0xFFB45309) else Color(0xFFFBBF24)
    IptvSourceFailKind.Expired -> if (onLightBackground) Color(0xFFC2410C) else Color(0xFFFB923C)
}
