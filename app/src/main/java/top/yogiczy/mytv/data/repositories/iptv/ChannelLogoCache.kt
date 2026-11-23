package top.yogiczy.mytv.data.repositories.iptv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import top.yogiczy.mytv.AppGlobal
import java.io.File

/**
 * 台标本地缓存：下载成功后写入磁盘，换台优先读缓存。
 * 清理 App 缓存目录（快捷面板「清除缓存」）时一并删除。
 */
object ChannelLogoCache {
    private const val DIR_NAME = "channel_logos"

    private val memory = object : LruCache<String, Bitmap>(24) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private fun dir(): File =
        File(AppGlobal.cacheDir, DIR_NAME).also { it.mkdirs() }

    private fun safeFileName(name: String): String =
        name.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")

    private fun cacheFile(fileName: String): File =
        File(dir(), "${safeFileName(fileName)}.png")

    fun get(fileName: String): Bitmap? {
        memory.get(fileName)?.let { return it }
        val file = cacheFile(fileName)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()?.also { memory.put(fileName, it) }
    }

    fun put(fileName: String, bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        val file = cacheFile(fileName)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
        val bitmap = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() ?: return null
        memory.put(fileName, bitmap)
        return bitmap
    }

    fun clear() {
        memory.evictAll()
        runCatching { dir().deleteRecursively() }
    }
}
