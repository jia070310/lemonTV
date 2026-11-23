package top.yogiczy.mytv.ui.screens.leanback.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.data.repositories.iptv.ChannelLogoCache
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.utils.SP
import java.util.concurrent.TimeUnit

private val logoHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
}

private fun fanmingmingLogoRawUrl(fileName: String): String {
    val encoded = Uri.encode(fileName.trim(), "+.-_")
    return "https://raw.githubusercontent.com/fanmingming/live/main/tv/$encoded.png"
}

private fun resolveLogoUrl(fileName: String): String {
    val raw = fanmingmingLogoRawUrl(fileName)
    return if (SP.githubProxyEnabled) {
        Constants.withGithubProxy(raw, SP.githubProxy)
    } else {
        raw
    }
}

fun channelLogoFileNameCandidates(name: String, channelName: String?): List<String> {
    val primary = name.trim()
    val channel = channelName?.trim().orEmpty()
    val suffixPattern = Regex(
        "(综合|财经|综艺|中文国际|体育赛事|体育|电影|国防军事|电视剧|纪录|戏曲|社会与法|新闻|少儿|音乐|农业农村|欧洲|美洲)$"
    )

    return buildList {
        fun addCandidate(value: String?) {
            val v = value?.trim().orEmpty()
            if (v.isNotBlank()) add(v)
        }

        addCandidate(primary)
        addCandidate(channel)
        addCandidate(primary.replace(suffixPattern, ""))
        addCandidate(channel.replace(suffixPattern, ""))

        Regex("""^(CCTV\d+\+?)""", RegexOption.IGNORE_CASE)
            .find(primary)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { addCandidate(it) }

        Regex("""^(CGTN[^\s]*)""", RegexOption.IGNORE_CASE)
            .find(primary)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { addCandidate(it) }
    }.distinct()
}

@Composable
fun LeanbackChannelLogo(
    name: String,
    channelName: String? = null,
    contentDescription: String = "$name 台标",
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
    showPlaceholder: Boolean = true,
) {
    val candidates = remember(name, channelName) {
        channelLogoFileNameCandidates(name, channelName)
    }
    if (candidates.isEmpty()) return

    var bitmap by remember(candidates) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(candidates, SP.githubProxyEnabled, SP.githubProxy) {
        val cached = withContext(Dispatchers.IO) {
            candidates.firstNotNullOfOrNull { ChannelLogoCache.get(it) }
        }
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }

        bitmap = withContext(Dispatchers.IO) {
            for (fileName in candidates) {
                ChannelLogoCache.get(fileName)?.let { return@withContext it }

                val url = resolveLogoUrl(fileName)
                val downloaded = runCatching {
                    val request = Request.Builder()
                        .url(url)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        )
                        .build()
                    logoHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching null
                        val bytes = response.body?.bytes() ?: return@runCatching null
                        ChannelLogoCache.put(fileName, bytes)
                    }
                }.getOrNull()
                if (downloaded != null) return@withContext downloaded
            }
            null
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size),
        )
    } else if (showPlaceholder) {
        Spacer(modifier = modifier.size(size))
    }
}
