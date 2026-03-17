package top.yogiczy.mytv.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {
    private val log = Logger.create("ApkInstaller")

    @SuppressLint("SetWorldReadable")
    fun installApk(context: Context, filePath: String) {
        log.i("开始安装 APK: $filePath")
        val file = File(filePath)
        
        if (!file.exists()) {
            log.e("APK 文件不存在: $filePath")
            return
        }
        
        log.i("APK 文件大小: ${file.length()} 字节")

        try {
            val cacheDir = context.cacheDir
            val cachedApkFile = File(cacheDir, file.name).apply {
                log.i("复制 APK 到缓存目录: ${this.absolutePath}")
                writeBytes(file.readBytes())
                // 解决Android6 无法解析安装包
                setReadable(true, false)
            }

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                log.i("使用 FileProvider 获取 URI")
                FileProvider.getUriForFile(
                    context, 
                    context.packageName + ".FileProvider", 
                    cachedApkFile
                )
            } else {
                log.i("使用 File URI")
                Uri.fromFile(cachedApkFile)
            }

            log.i("生成 URI: $uri")

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                setDataAndType(uri, "application/vnd.android.package-archive")
            }

            log.i("启动安装 Intent")
            context.startActivity(installIntent)
            log.i("安装器已启动")
        } catch (e: Exception) {
            log.e("安装 APK 失败", e)
            throw e
        }
    }
}