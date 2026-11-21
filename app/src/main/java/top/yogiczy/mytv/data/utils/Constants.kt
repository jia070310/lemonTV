package top.yogiczy.mytv.data.utils

/**
 * 常量
 */
object Constants {
    /**
     * 应用 标题
     */
    const val APP_TITLE = "柠檬直播"

    /**
     * 应用 代码仓库
     */
    const val APP_REPO = "https://github.com/jia070310/lemonTV"

    /**
     * IPTV源地址（默认直播源，走默认 GitHub 加速器）
     */
    val IPTV_SOURCE_URL: String
        get() = withGithubProxy(
            "https://raw.githubusercontent.com/jia070310/lemonTV/main/iptv-fe.m3u",
            GITHUB_PROXY,
        )

    /**
     * 默认IPTV源列表（用于快速切换；含加速器地址的项会随自定义加速器迁移）
     */
    val IPTV_SOURCE_DEFAULT_LIST: List<String>
        get() = listOf(
            IPTV_SOURCE_URL,
            withGithubProxy(
                "https://raw.githubusercontent.com/ioptu/migu-video/main/migu.m3u",
                GITHUB_PROXY,
            ),
            "https://fastly.jsdelivr.net/gh/jia070310/lemonTV@main/iptv-fe.m3u",
            "https://fastly.jsdelivr.net/gh/ioptu/migu-video@main/migu.m3u",
        )

    /**
     * 推荐加速器列表（应用内 / 后台均可切换）
     * @see <a href="https://ghfast.top/">ghfast.top</a>
     */
    val GITHUB_PROXY_CANDIDATES = listOf(
        "https://ghproxy.net/",
        "https://ghfast.top/",
    )

    /** 已知的 GitHub 加速域前缀（含历史失效域），用于统一迁移到当前加速器 */
    val KNOWN_GITHUB_PROXY_PREFIXES = listOf(
        "https://ghproxy.net/",
        "http://ghproxy.net/",
        "https://ghfast.top/",
        "http://ghfast.top/",
        "https://gh-proxy.org/",
        "http://gh-proxy.org/",
        "https://hk.gh-proxy.org/",
        "http://hk.gh-proxy.org/",
        "https://mirror.ghproxy.com/",
        "http://mirror.ghproxy.com/",
        "https://ghproxy.com/",
        "http://ghproxy.com/",
        "https://gh.ddlc.top/",
        "https://gitclone.com/",
    )

    /** 测速用的小文件（raw） */
    const val GITHUB_PROXY_TEST_RAW_URL =
        "https://raw.githubusercontent.com/jia070310/lemonTV/main/versions.json"

    /**
     * 规范化加速器地址（保证以 http(s) 开头并以 / 结尾）
     */
    fun normalizeGithubProxy(proxy: String): String {
        var out = proxy.trim()
        if (out.isBlank()) return GITHUB_PROXY
        if (!out.startsWith("http://") && !out.startsWith("https://")) {
            out = "https://$out"
        }
        if (!out.endsWith("/")) out += "/"
        return out
    }

    /**
     * 将 URL 中的旧/其它 GitHub 加速域替换为指定加速器
     * @param previousProxy 额外替换的上一次自定义加速器前缀
     */
    fun migrateGithubProxyUrl(
        url: String,
        proxy: String = GITHUB_PROXY,
        previousProxy: String? = null,
    ): String {
        val target = normalizeGithubProxy(proxy)
        var out = url.trim()
        val replaceList = buildList {
            addAll(KNOWN_GITHUB_PROXY_PREFIXES)
            if (!previousProxy.isNullOrBlank()) add(normalizeGithubProxy(previousProxy))
        }.distinctBy { it.lowercase() }
        for (old in replaceList) {
            if (old.equals(target, ignoreCase = true)) continue
            out = out.replace(old, target, ignoreCase = true)
        }
        return out
    }

    /**
     * 给 GitHub 原始地址加上加速器前缀（已带加速器则迁移到指定加速器）
     */
    fun withGithubProxy(rawUrl: String, proxy: String = GITHUB_PROXY): String {
        val target = normalizeGithubProxy(proxy)
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return trimmed
        if (KNOWN_GITHUB_PROXY_PREFIXES.any { trimmed.startsWith(it, ignoreCase = true) }
            || trimmed.startsWith(target, ignoreCase = true)
        ) {
            return migrateGithubProxyUrl(trimmed, target)
        }
        return target + trimmed.removePrefix("/")
    }

    /**
     * 判断 URL 是否走了 GitHub 加速器
     */
    fun usesGithubProxy(url: String, proxy: String = GITHUB_PROXY): Boolean {
        val target = normalizeGithubProxy(proxy)
        if (url.startsWith(target, ignoreCase = true)) return true
        return KNOWN_GITHUB_PROXY_PREFIXES.any { url.startsWith(it, ignoreCase = true) }
    }

    /**
     * 同一直播源的备用拉取地址（按优先级）
     */
    fun iptvSourceFallbackUrls(sourceUrl: String, proxy: String = GITHUB_PROXY): List<String> {
        val u = migrateGithubProxyUrl(sourceUrl, proxy)
        return when {
            // jsDelivr 通常比加速器更稳，优先作为备用，加速器超时后能快速恢复
            u.contains("jia070310/lemonTV") && u.contains("iptv-fe.m3u") -> listOf(
                "https://fastly.jsdelivr.net/gh/jia070310/lemonTV@main/iptv-fe.m3u",
                withGithubProxy(
                    "https://raw.githubusercontent.com/jia070310/lemonTV/main/iptv-fe.m3u",
                    proxy,
                ),
            )
            u.contains("ioptu/migu-video") || u.contains("/migu.m3u") -> listOf(
                "https://fastly.jsdelivr.net/gh/ioptu/migu-video@main/migu.m3u",
                withGithubProxy(
                    "https://raw.githubusercontent.com/ioptu/migu-video/main/migu.m3u",
                    proxy,
                ),
            )
            else -> emptyList()
        }.let { fallbacks -> (listOf(u) + fallbacks).distinct() }
    }

    /**
     * IPTV源缓存时间（毫秒）
     */
    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60 * 24L // 24小时

    /**
     * 节目单XML地址（主）
     */
    const val EPG_XML_URL = "http://epg.51zmt.top:8000/e1.xml.gz"

    /**
     * 节目单XML备用地址
     */
    const val EPG_XML_URL_BACKUP = "https://epg.zsdc.eu.org/t.xml.gz"

    /**
     * 节目单刷新时间阈值（小时）
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 2 // 不到2点不刷新

    /**
     * Git最新版本信息
     */
    const val GIT_RELEASE_LATEST_URL =
        "https://api.github.com/repos/jia070310/lemonTV/releases/latest"

    /**
     * GitHub 加速代理地址（默认可用值；可在后台设置中自定义）
     */
    const val GITHUB_PROXY = "https://ghproxy.net/"

    /**
     * HTTP请求重试次数
     */
    const val HTTP_RETRY_COUNT = 10L

    /**
     * HTTP请求重试间隔时间（毫秒）
     */
    const val HTTP_RETRY_INTERVAL = 3000L

    /**
     * 播放器 userAgent
     */
    const val VIDEO_PLAYER_USER_AGENT = "ExoPlayer"

    /**
     * APTV 专用 User-Agent
     * 业务规则：当解析/请求的链接域名包含 `aptv.app` 时使用该 UA。
     */
    const val VIDEO_PLAYER_USER_AGENT_APTV =
        "AptvPlayer/1.4.25 (iPhone; CPU iPhone OS 26.3.1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"

    /**
     * 日志历史最大保留条数
     */
    const val LOG_HISTORY_MAX_SIZE = 50

    /**
     * 播放器加载超时
     */
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 15 // 15秒

    /**
     * 界面 超时未操作自动关闭界面
     */
    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15 // 15秒

    /**
     * 界面 时间显示前后范围
     */
    const val UI_TIME_SHOW_RANGE = 1000L * 30 // 前后30秒

    /**
     * 界面 临时面板界面显示时间
     */
    const val UI_TEMP_PANEL_SCREEN_SHOW_DURATION = 1500L // 1.5秒
}