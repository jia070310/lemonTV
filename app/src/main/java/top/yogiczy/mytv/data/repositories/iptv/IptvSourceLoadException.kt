package top.yogiczy.mytv.data.repositories.iptv

import java.net.SocketTimeoutException

enum class IptvSourceFailKind {
    /** m3u 文件下载失败，但不是超时 */
    FetchFailed,
    /** m3u 链接连接超时 */
    Timeout,
    /** 文件拉下来了，但里面没有频道 */
    Empty,
    /** m3u 拉取成功，但里面的直播地址不能播放 */
    Expired,
}

fun IptvSourceFailKind.label(): String = when (this) {
    IptvSourceFailKind.FetchFailed -> "源链接无效"
    IptvSourceFailKind.Timeout -> "连接超时"
    IptvSourceFailKind.Empty -> "源文件错误"
    IptvSourceFailKind.Expired -> "直播源链接失效"
}

class IptvSourceLoadException(
    val kind: IptvSourceFailKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

fun Throwable.iptvFailKind(): IptvSourceFailKind {
    var current: Throwable? = this
    while (current != null) {
        if (current is IptvSourceLoadException) return current.kind
        if (current is SocketTimeoutException) return IptvSourceFailKind.Timeout
        val msg = current.message?.lowercase().orEmpty()
        if (msg.contains("timeout") || msg.contains("timed out")) return IptvSourceFailKind.Timeout
        current = current.cause
    }
    return IptvSourceFailKind.FetchFailed
}
