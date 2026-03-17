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
import androidx.compose.runtime.getValue
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
    focusedCategoryProvider: () -> LeanbackSettingsCategories = { LeanbackSettingsCategories.entries.first() },
    onIptvSourceChanged: () -> Unit = {},
) {
    val focusedCategory = focusedCategoryProvider()
    
    // 缓存已创建的分类内容，避免重复创建
    val categoryCache = remember { mutableStateMapOf<LeanbackSettingsCategories, @Composable () -> Unit>() }
    
    // 预加载策略：在切换分类后，异步预加载相邻分类
    LaunchedEffect(focusedCategory) {
        // 延迟 50ms 后预加载，避免阻塞当前渲染
        delay(50)
        
        val currentIndex = LeanbackSettingsCategories.entries.indexOf(focusedCategory)
        val categories = LeanbackSettingsCategories.entries
        
        // 预加载上一个分类
        if (currentIndex > 0) {
            val prevCategory = categories[currentIndex - 1]
            if (!categoryCache.containsKey(prevCategory)) {
                categoryCache[prevCategory] = { 
                    renderCategoryContent(prevCategory, onIptvSourceChanged)
                }
            }
        }
        
        // 预加载下一个分类
        if (currentIndex < categories.size - 1) {
            val nextCategory = categories[currentIndex + 1]
            if (!categoryCache.containsKey(nextCategory)) {
                categoryCache[nextCategory] = { 
                    renderCategoryContent(nextCategory, onIptvSourceChanged)
                }
            }
        }
    }
    
    // 清理不再需要的缓存（保留当前和相邻的）
    DisposableEffect(focusedCategory) {
        onDispose {
            val currentIndex = LeanbackSettingsCategories.entries.indexOf(focusedCategory)
            val categories = LeanbackSettingsCategories.entries
            val toKeep = setOfNotNull(
                focusedCategory,
                if (currentIndex > 0) categories[currentIndex - 1] else null,
                if (currentIndex < categories.size - 1) categories[currentIndex + 1] else null
            )
            categoryCache.keys.removeAll { it !in toKeep }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = focusedCategory.title, style = MaterialTheme.typography.headlineSmall)

        // 使用 Crossfade 实现平滑切换
        Crossfade(
            targetState = focusedCategory,
            animationSpec = tween(durationMillis = 100), // 极快切换
            label = "settings_category_content"
        ) { category ->
            // 优先使用缓存，如果没有则立即渲染
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
        LeanbackSettingsCategories.ABOUT -> LeanbackSettingsCategoryAbout()
        LeanbackSettingsCategories.APP -> LeanbackSettingsCategoryApp()
        LeanbackSettingsCategories.IPTV -> LeanbackSettingsCategoryIptv(
            onIptvSourceChanged = onIptvSourceChanged
        )
        LeanbackSettingsCategories.EPG -> LeanbackSettingsCategoryEpg(
            onEpgSourceChanged = onIptvSourceChanged
        )
        LeanbackSettingsCategories.UI -> LeanbackSettingsCategoryUI()
        LeanbackSettingsCategories.FAVORITE -> LeanbackSettingsCategoryFavorite()
        LeanbackSettingsCategories.UPDATE -> LeanbackSettingsCategoryUpdate()
        LeanbackSettingsCategories.VIDEO_PLAYER -> LeanbackSettingsCategoryVideoPlayer()
        LeanbackSettingsCategories.HTTP -> LeanbackSettingsCategoryHttp()
        LeanbackSettingsCategories.DEBUG -> LeanbackSettingsCategoryDebug()
        LeanbackSettingsCategories.LOG -> LeanbackSettingsCategoryLog(
            history = Logger.history,
        )
        LeanbackSettingsCategories.MORE -> LeanbackSettingsCategoryMore()
    }
}
