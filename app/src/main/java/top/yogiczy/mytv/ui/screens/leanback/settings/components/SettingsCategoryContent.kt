package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsCategories
import top.yogiczy.mytv.utils.Logger

@Composable
fun LeanbackSettingsCategoryContent(
    modifier: Modifier = Modifier,
    focusedCategoryProvider: () -> LeanbackSettingsCategories = {
        LeanbackSettingsCategories.IPTV
    },
    onIptvSourceChanged: () -> Unit = {},
) {
    val focusedCategory = focusedCategoryProvider()
    val categoryCache = remember { mutableStateMapOf<LeanbackSettingsCategories, @Composable () -> Unit>() }
    val groupCategories = remember(focusedCategory.group) {
        LeanbackSettingsCategories.ofGroup(focusedCategory.group)
    }

    LaunchedEffect(focusedCategory) {
        delay(50)
        val currentIndex = groupCategories.indexOf(focusedCategory)
        if (currentIndex > 0) {
            val prevCategory = groupCategories[currentIndex - 1]
            if (!categoryCache.containsKey(prevCategory)) {
                categoryCache[prevCategory] = {
                    renderCategoryContent(prevCategory, onIptvSourceChanged)
                }
            }
        }
        if (currentIndex in 0 until groupCategories.lastIndex) {
            val nextCategory = groupCategories[currentIndex + 1]
            if (!categoryCache.containsKey(nextCategory)) {
                categoryCache[nextCategory] = {
                    renderCategoryContent(nextCategory, onIptvSourceChanged)
                }
            }
        }
    }

    DisposableEffect(focusedCategory) {
        onDispose {
            val currentIndex = groupCategories.indexOf(focusedCategory)
            val toKeep = setOfNotNull(
                focusedCategory,
                groupCategories.getOrNull(currentIndex - 1),
                groupCategories.getOrNull(currentIndex + 1),
            )
            categoryCache.keys.removeAll { it !in toKeep }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = focusedCategory.title, style = MaterialTheme.typography.headlineSmall)

        Crossfade(
            targetState = focusedCategory,
            animationSpec = tween(durationMillis = 100),
            label = "settings_category_content"
        ) { category ->
            val cachedContent = categoryCache[category]
            if (cachedContent != null) {
                cachedContent()
            } else {
                renderCategoryContent(category, onIptvSourceChanged)
            }
        }
    }
}

@Composable
private fun renderCategoryContent(
    category: LeanbackSettingsCategories,
    onIptvSourceChanged: () -> Unit
) {
    when (category) {
        LeanbackSettingsCategories.IPTV -> LeanbackSettingsCategoryIptv(
            onIptvSourceChanged = onIptvSourceChanged
        )
        LeanbackSettingsCategories.EPG -> LeanbackSettingsCategoryEpg(
            onEpgSourceChanged = onIptvSourceChanged
        )
        LeanbackSettingsCategories.FAVORITE -> LeanbackSettingsCategoryFavorite()
        LeanbackSettingsCategories.PLAYBACK -> LeanbackSettingsCategoryPlayback()
        LeanbackSettingsCategories.REMOTE -> LeanbackSettingsCategoryMore()
        LeanbackSettingsCategories.ABOUT -> LeanbackSettingsCategoryAbout()
        LeanbackSettingsCategories.APP -> LeanbackSettingsCategoryApp()
        LeanbackSettingsCategories.UI -> LeanbackSettingsCategoryUI()
        LeanbackSettingsCategories.DEBUG -> LeanbackSettingsCategoryDebug(
            history = Logger.history,
        )
    }
}
