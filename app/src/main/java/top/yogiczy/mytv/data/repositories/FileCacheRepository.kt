package top.yogiczy.mytv.data.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.utils.Logger
import java.io.File

/**
 * 用于将数据缓存至本地
 */
abstract class FileCacheRepository(
    private val fileName: String,
) {
    private val log = Logger.create("FileCacheRepository")
    
    private fun getCacheFile() = File(AppGlobal.cacheDir, fileName).also {
        // 确保缓存目录存在
        it.parentFile?.mkdirs()
    }

    private suspend fun getCacheData(): String? = withContext(Dispatchers.IO) {
        val file = getCacheFile()
        if (file.exists()) file.readText()
        else null
    }

    private suspend fun setCacheData(data: String) = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile()
            file.writeText(data)
            log.d("缓存数据已保存: ${file.absolutePath}, 大小: ${data.length} 字节")
        } catch (e: Exception) {
            log.e("保存缓存失败: $fileName", e)
            // 尝试再次创建目录并保存
            try {
                val file = getCacheFile()
                file.parentFile?.mkdirs()
                file.writeText(data)
                log.i("重试保存缓存成功")
            } catch (e2: Exception) {
                log.e("重试保存缓存仍然失败", e2)
                throw e2
            }
        }
    }

    protected suspend fun getOrRefresh(cacheTime: Long, refreshOp: suspend () -> String): String {
        return getOrRefresh(
            { lastModified, _ -> System.currentTimeMillis() - lastModified >= cacheTime },
            refreshOp,
        )
    }

    fun clearCache() {
        try {
            val file = getCacheFile()
            if (file.exists()) {
                file.delete()
                log.i("缓存文件已删除: ${file.absolutePath}")
            } else {
                log.d("缓存文件不存在: ${file.absolutePath}")
            }
        } catch (ex: Exception) {
            log.e("删除缓存文件失败", ex)
        }
    }
    
    /**
     * 强制刷新：删除缓存并重新获取
     */
    protected suspend fun forceRefresh(refreshOp: suspend () -> String): String {
        log.i("强制刷新: $fileName")
        clearCache()
        val data = refreshOp()
        setCacheData(data)
        return data
    }

    protected suspend fun getOrRefresh(
        isExpired: (lastModified: Long, cacheData: String?) -> Boolean,
        refreshOp: suspend () -> String,
    ): String {
        var data = getCacheData()
        val cacheFile = getCacheFile()

        if (isExpired(cacheFile.lastModified(), data)) {
            log.i("缓存已过期，删除缓存文件: ${cacheFile.absolutePath}")
            clearCache()  // 删除缓存文件
            data = null
        }

        if (data.isNullOrBlank()) {
            log.i("重新获取数据: $fileName")
            data = refreshOp()
            setCacheData(data)
        } else {
            log.d("使用缓存数据: $fileName")
        }

        return data
    }
}