package top.yogiczy.mytv.ui.screens.leanback.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.ui.rememberLeanbackChildPadding
import top.yogiczy.mytv.ui.screens.leanback.settings.components.LeanbackSettingsCategoryContent
import top.yogiczy.mytv.ui.screens.leanback.settings.components.LeanbackSettingsCategoryList
import top.yogiczy.mytv.ui.theme.LeanbackTheme

@Composable
fun LeanbackSettingsScreen(
    modifier: Modifier = Modifier,
    onIptvSourceChanged: () -> Unit = {},
    hasUpdateProvider: () -> Boolean = { false },  // 新增：是否有更新
) {
    val childPadding = rememberLeanbackChildPadding()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    var focusedCategory by remember { mutableStateOf(LeanbackSettingsCategories.entries.first()) }
    
    // 优化：使用 derivedStateOf 减少重组
    val currentCategory by remember { derivedStateOf { focusedCategory } }
    
    // 优化：缓存渐变画刷，避免每次重组都创建新对象
    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1a1a1a),
                Color(0xFF0f0f0f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .background(brush = backgroundBrush)
            .padding(
                top = childPadding.top + 20.dp,
                bottom = childPadding.bottom,
                start = childPadding.start,
                end = childPadding.end,
            )
            .pointerInput(Unit) { detectTapGestures(onTap = { }) },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            LeanbackSettingsCategoryList(
                modifier = Modifier.width(200.dp),
                focusedCategoryProvider = { currentCategory },
                onFocused = { focusedCategory = it },
                hasUpdateProvider = hasUpdateProvider,  // 传递更新状态
            )

            LeanbackSettingsCategoryContent(
                focusedCategoryProvider = { currentCategory },
                onIptvSourceChanged = onIptvSourceChanged,
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackSettingsScreenPreview() {
    LeanbackTheme {
        LeanbackSettingsScreen()
    }
}
