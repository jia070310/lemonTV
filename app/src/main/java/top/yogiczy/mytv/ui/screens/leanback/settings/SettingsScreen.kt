package top.yogiczy.mytv.ui.screens.leanback.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import top.yogiczy.mytv.ui.rememberLeanbackChildPadding
import top.yogiczy.mytv.ui.screens.leanback.settings.components.LeanbackSettingsCategoryContent
import top.yogiczy.mytv.ui.screens.leanback.settings.components.LeanbackSettingsCategoryList
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents

private val SettingsAccent = Color(0xFFfddd0e)
private val SettingsCardBg = Color(0xFF141414)
private val SettingsHeaderBg = Color(0xFF1c1c1c)
private val SettingsDivider = Color.White.copy(alpha = 0.08f)
private val SettingsTabIdle = Color(0xFF2a2a2a)

@Composable
fun LeanbackSettingsScreen(
    modifier: Modifier = Modifier,
    onIptvSourceChanged: () -> Unit = {},
    hasUpdateProvider: () -> Boolean = { false },
) {
    val childPadding = rememberLeanbackChildPadding()
    val firstTabFocusRequester = remember { FocusRequester() }

    var selectedGroup by remember { mutableStateOf(LeanbackSettingsGroup.CHANNEL) }
    var focusedCategory by remember {
        mutableStateOf(LeanbackSettingsCategories.defaultOf(LeanbackSettingsGroup.CHANNEL))
    }
    /** 焦点在一级页头时，二级不高亮 */
    var focusOnGroupTabs by remember { mutableStateOf(true) }

    val currentCategory by remember { derivedStateOf { focusedCategory } }

    LaunchedEffect(Unit) {
        firstTabFocusRequester.requestFocus()
    }

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1a1a1a), Color(0xFF0f0f0f))
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = backgroundBrush)
            .padding(
                top = childPadding.top + 12.dp,
                bottom = childPadding.bottom,
                start = childPadding.start,
                end = childPadding.end,
            )
            .pointerInput(Unit) { detectTapGestures(onTap = { }) },
    ) {
        // 整页一个卡片：页头 Tab + 内容，视觉上天然是一体
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SettingsCardBg, RoundedCornerShape(18.dp))
                .border(BorderStroke(1.dp, SettingsDivider), RoundedCornerShape(18.dp)),
        ) {
            // 页头
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        SettingsHeaderBg,
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                LeanbackSettingsGroupTabs(
                    selectedGroup = selectedGroup,
                    firstTabFocusRequester = firstTabFocusRequester,
                    onGroupFocused = { group ->
                        focusOnGroupTabs = true
                        if (selectedGroup != group) {
                            selectedGroup = group
                            focusedCategory = LeanbackSettingsCategories.defaultOf(group)
                        }
                    },
                    onTabsFocusChanged = { focused ->
                        if (focused) focusOnGroupTabs = true
                    },
                )
            }

            // 页头与内容的淡分隔线（不用通栏黄线）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SettingsDivider),
            )

            // 内容区
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .onFocusChanged {
                        if (it.hasFocus) focusOnGroupTabs = false
                    },
            ) {
                LeanbackSettingsCategoryList(
                    modifier = Modifier
                        .width(210.dp)
                        .fillMaxHeight(),
                    group = selectedGroup,
                    focusedCategoryProvider = { currentCategory },
                    onFocused = {
                        focusOnGroupTabs = false
                        focusedCategory = it
                    },
                    hasUpdateProvider = hasUpdateProvider,
                    autoFocusFirst = false,
                    highlightSelected = !focusOnGroupTabs,
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(SettingsDivider),
                )

                LeanbackSettingsCategoryContent(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    focusedCategoryProvider = { currentCategory },
                    onIptvSourceChanged = onIptvSourceChanged,
                )
            }
        }
    }
}

@Composable
private fun LeanbackSettingsGroupTabs(
    selectedGroup: LeanbackSettingsGroup,
    firstTabFocusRequester: FocusRequester,
    onGroupFocused: (LeanbackSettingsGroup) -> Unit,
    onTabsFocusChanged: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onTabsFocusChanged(it.hasFocus) },
    ) {
        LeanbackSettingsGroup.entries.forEachIndexed { index, group ->
            val selected = selectedGroup == group
            var isFocused by remember { mutableStateOf(false) }
            val focusRequester = remember { FocusRequester() }

            val container = when {
                isFocused -> SettingsAccent
                selected -> SettingsAccent.copy(alpha = 0.18f)
                else -> SettingsTabIdle
            }
            val contentColor = when {
                isFocused -> Color.Black
                selected -> SettingsAccent
                else -> Color.White.copy(alpha = 0.78f)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = { onGroupFocused(group) },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = container,
                        focusedContainerColor = SettingsAccent,
                        pressedContainerColor = SettingsAccent,
                    ),
                    shape = ClickableSurfaceDefaults.shape(
                        shape = RoundedCornerShape(10.dp),
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = androidx.tv.material3.Border(
                            border = BorderStroke(
                                1.dp,
                                if (selected && !isFocused) SettingsAccent.copy(alpha = 0.55f)
                                else Color.Transparent,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ),
                        focusedBorder = androidx.tv.material3.Border(
                            border = BorderStroke(0.dp, Color.Transparent),
                            shape = RoundedCornerShape(10.dp),
                        ),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    modifier = Modifier
                        .then(
                            if (index == 0) Modifier.focusRequester(firstTabFocusRequester)
                            else Modifier
                        )
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            val focused = it.isFocused || it.hasFocus
                            isFocused = focused
                            if (focused) onGroupFocused(group)
                        }
                        .handleLeanbackKeyEvents(
                            onSelect = {
                                if (isFocused) onGroupFocused(group)
                                else focusRequester.requestFocus()
                            },
                        ),
                ) {
                    Text(
                        text = group.title,
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }

                // 选中指示条：像页签贴在内容上
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(3.dp)
                        .background(
                            color = when {
                                isFocused || selected -> SettingsAccent
                                else -> Color.Transparent
                            },
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
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
