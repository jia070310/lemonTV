package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsCategories
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsGroup
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LeanbackSettingsCategoryList(
    modifier: Modifier = Modifier,
    group: LeanbackSettingsGroup = LeanbackSettingsGroup.CHANNEL,
    focusedCategoryProvider: () -> LeanbackSettingsCategories = {
        LeanbackSettingsCategories.defaultOf(LeanbackSettingsGroup.CHANNEL)
    },
    onFocused: (LeanbackSettingsCategories) -> Unit = {},
    hasUpdateProvider: () -> Boolean = { false },
    /** 为 true 时切换大项会抢左侧第一项焦点；顶部大项切换时保持 false，避免抢走大项焦点 */
    autoFocusFirst: Boolean = false,
    /** 焦点在一级大项时为 false，二级不显示选中高亮 */
    highlightSelected: Boolean = true,
) {
    val categories = remember(group) { LeanbackSettingsCategories.ofGroup(group) }

    key(group) {
        TvLazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier.focusRestorer()
        ) {
            itemsIndexed(categories) { index, category ->
                val isCurrent by remember { derivedStateOf { focusedCategoryProvider() == category } }
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(group, autoFocusFirst) {
                    if (autoFocusFirst && index == 0) {
                        focusRequester.requestFocus()
                    }
                }

                LeanbackSettingsCategoryItem(
                    modifier = Modifier.focusRequester(focusRequester),
                    icon = category.icon,
                    title = category.title,
                    // 只有焦点在二级时才允许「当前项」发黄；一级有焦点时全部保持普通态
                    isSelectedProvider = { highlightSelected && isCurrent },
                    onFocused = { onFocused(category) },
                    showBadgeProvider = {
                        category == LeanbackSettingsCategories.ABOUT && hasUpdateProvider()
                    },
                )
            }
        }
    }
}

@Composable
private fun LeanbackSettingsCategoryItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    isSelectedProvider: () -> Boolean = { false },
    onFocused: () -> Unit = {},
    showBadgeProvider: () -> Boolean = { false },
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val isSelected = isSelectedProvider()

    Box {
        androidx.tv.material3.ListItem(
            selected = isSelected,
            onClick = { },
            leadingContent = {
                androidx.tv.material3.Icon(
                    icon,
                    title,
                    tint = if (isSelected)
                        androidx.compose.ui.graphics.Color.Black
                    else
                        androidx.compose.ui.graphics.Color.White
                )
            },
            headlineContent = {
                androidx.tv.material3.Text(
                    text = title,
                    color = if (isSelected)
                        androidx.compose.ui.graphics.Color.Black
                    else
                        androidx.compose.ui.graphics.Color.White
                )
            },
            colors = androidx.tv.material3.ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color(0xFF1f1f1f),
                focusedContainerColor = if (isSelected)
                    androidx.compose.ui.graphics.Color(0xFFfddd0e)
                else
                    androidx.compose.ui.graphics.Color(0xFF2a2a2a),
                selectedContainerColor = androidx.compose.ui.graphics.Color(0xFFfddd0e),
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
                focusedBorder = if (isSelected)
                    androidx.tv.material3.Border(
                        border = androidx.compose.foundation.BorderStroke(0.dp, androidx.compose.ui.graphics.Color.Transparent),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    )
                else
                    androidx.tv.material3.Border(
                        border = androidx.compose.foundation.BorderStroke(2.dp, androidx.compose.ui.graphics.Color(0xFFfddd0e)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    ),
                selectedBorder = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(0.dp, androidx.compose.ui.graphics.Color.Transparent),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                ),
            ),
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged {
                    val newFocused = it.isFocused || it.hasFocus
                    if (newFocused && !isFocused) {
                        isFocused = true
                        onFocused()
                    } else if (!newFocused) {
                        isFocused = false
                    }
                }
                .handleLeanbackKeyEvents(
                    onSelect = {
                        if (isFocused) focusManager.moveFocus(FocusDirection.Right)
                        else focusRequester.requestFocus()
                    }
                ),
        )

        if (showBadgeProvider()) {
            Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = androidx.compose.ui.graphics.Color(0xFFFF4444),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryItemPreview() {
    LeanbackTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LeanbackSettingsCategoryItem(
                icon = LeanbackSettingsCategories.ABOUT.icon,
                title = LeanbackSettingsCategories.ABOUT.title,
            )

            LeanbackSettingsCategoryItem(
                icon = LeanbackSettingsCategories.ABOUT.icon,
                title = LeanbackSettingsCategories.ABOUT.title,
                isSelectedProvider = { true },
            )
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryListPreview() {
    LeanbackTheme {
        LeanbackSettingsCategoryList()
    }
}
