package top.yogiczy.mytv

import java.io.File

/**
 * 应用全局变量
 */
object AppGlobal {
    /**
     * 缓存目录
     */
    lateinit var cacheDir: File
    
    /**
     * 从直播源中提取的 User-Agent（用于播放器智能降级）
     */
    var extractedUserAgent: String? = null
    
    /**
     * 从直播源 #EXT-X-APP 提取的 APP 名称（用于 EPG 请求时的 User-Agent）
     */
    var extractedAppName: String? = null
}