package top.yogiczy.mytv.data.utils

/**
 * 常量
 */
object Constants {
    /**
     * 应用 标题
     */
    const val APP_TITLE = "柠檬TV"

    /**
     * 应用 代码仓库
     */
    const val APP_REPO = "https://github.com/jia070310/lemonTV"

    /**
     * IPTV源地址（使用 ghfast.top 代理，更稳定）
     */
    const val IPTV_SOURCE_URL = "https://ghfast.top/https://raw.githubusercontent.com/jia070310/lemonTV/refs/heads/main/iptv-fe.m3u"
    
    /**
     * 默认IPTV源列表（用于快速切换）
     */
    val IPTV_SOURCE_DEFAULT_LIST = listOf(
        IPTV_SOURCE_URL,
        "https://gh-proxy.org/github.com/ioptu/IPTV.txt2m3u.player/raw/refs/heads/main/migu.m3u",
        "https://gh-proxy.org/github.com/ioptu/IPTV.txt2m3u.player/raw/refs/heads/main/httop.m3u",
        "https://gh-proxy.org/github.com/ioptu/IPTV.txt2m3u.player/raw/refs/heads/main/httop_merged.m3u",
        "https://gh-proxy.org/github.com/ioptu/IPTV.txt2m3u.player/raw/refs/heads/main/iptv.m3u",
        "https://gh-proxy.org/github.com/ioptu/IPTV.txt2m3u.player/raw/refs/heads/main/iptv_merged.m3u",
    )

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
     * GitHub加速代理地址
     */
    const val GITHUB_PROXY = "https://hk.gh-proxy.org/"

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