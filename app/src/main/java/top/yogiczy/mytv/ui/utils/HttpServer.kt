package top.yogiczy.mytv.ui.utils

import android.content.Context
import android.widget.Toast
import com.koushikdutta.async.AsyncServer
import com.koushikdutta.async.http.body.JSONObjectBody
import com.koushikdutta.async.http.body.MultipartFormDataBody
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.R
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.utils.ApkInstaller
import top.yogiczy.mytv.utils.Loggable
import top.yogiczy.mytv.utils.Logger
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

object HttpServer : Loggable() {
    const val SERVER_PORT = 10481

    private val uploadedApkFile = File(AppGlobal.cacheDir, "uploaded_apk.apk").apply {
        deleteOnExit()
    }

    private var showToast: (String) -> Unit = { }
    private var appContext: Context? = null
    private var server: AsyncHttpServer? = null
    @Volatile
    private var started = false
    @Volatile
    private var starting = false

    val serverUrl: String
        get() = "http://${getLocalIpAddress()}:${SERVER_PORT}"

    /** 仅注册回调，不立刻开端口（进入二维码界面时再 startIfNeeded） */
    fun init(context: Context, showToast: (String) -> Unit) {
        appContext = context.applicationContext
        this.showToast = showToast
    }

    /** 进入后台二维码/设置页时按需启动；已启动则忽略 */
    fun startIfNeeded() {
        val context = appContext ?: return
        synchronized(this) {
            if (started || starting) return
            starting = true
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val httpServer = AsyncHttpServer()
                httpServer.listen(AsyncServer.getDefault(), SERVER_PORT)

                httpServer.get("/") { _, response ->
                    handleRawResource(response, context, "text/html", R.raw.index)
                }
                httpServer.get("/index_css.css") { _, response ->
                    handleRawResource(response, context, "text/css", R.raw.index_css)
                }
                httpServer.get("/index_js.js") { _, response ->
                    handleRawResource(response, context, "text/javascript", R.raw.index_js)
                }

                httpServer.get("/api/settings") { _, response ->
                    handleGetSettings(response)
                }

                httpServer.post("/api/settings") { request, response ->
                    handleSetSettings(request, response)
                }

                httpServer.post("/api/upload/apk") { request, response ->
                    handleUploadApk(request, response, context)
                }

                synchronized(this@HttpServer) {
                    server = httpServer
                    started = true
                    starting = false
                }
                log.i("设置服务已启动: 0.0.0.0:${SERVER_PORT}")
            } catch (ex: Exception) {
                synchronized(this@HttpServer) {
                    started = false
                    starting = false
                    server = null
                }
                log.e("服务启动失败: ${ex.message}", ex)
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "设置服务启动失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 应用退出时关闭端口 */
    fun stop() {
        synchronized(this) {
            try {
                server?.stop()
            } catch (ex: Exception) {
                log.e("关闭设置服务失败: ${ex.message}", ex)
            } finally {
                server = null
                started = false
                starting = false
            }
        }
        log.i("设置服务已关闭")
    }

    fun isLocalSettingsUrl(url: String): Boolean {
        return url.contains(":${SERVER_PORT}")
    }

    private fun wrapResponse(response: AsyncHttpServerResponse) = response.apply {
        headers.set(
            "Access-Control-Allow-Methods", "POST, GET, DELETE, PUT, OPTIONS"
        )
        headers.set("Access-Control-Allow-Origin", "*")
        headers.set(
            "Access-Control-Allow-Headers", "Origin, Content-Type, X-Auth-Token"
        )
    }

    private fun handleRawResource(
        response: AsyncHttpServerResponse,
        context: Context,
        contentType: String,
        id: Int,
    ) {
        wrapResponse(response).apply {
            setContentType(contentType)
            send(context.resources.openRawResource(id).readBytes().decodeToString())
        }
    }

    private fun handleGetSettings(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(
                Json.encodeToString(
                    AllSettings(
                        appTitle = Constants.APP_TITLE,
                        appRepo = Constants.APP_REPO,
                        iptvSourceUrl = SP.iptvSourceUrl,
                        iptvSourceName = SP.iptvSourceNameMap[SP.iptvSourceUrl] ?: "",
                        epgXmlUrl = SP.epgXmlUrl,
                        epgXmlUrlName = SP.iptvSourceNameMap[SP.epgXmlUrl] ?: "",
                        videoPlayerUserAgent = SP.videoPlayerUserAgent,
                        githubProxy = SP.githubProxy,
                        logHistory = Logger.history,
                    )
                )
            )
        }
    }

    private fun handleSetSettings(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val iptvSourceUrl = body.optString("iptvSourceUrl", "")
        val iptvSourceName = body.optString("iptvSourceName", "")
        val epgXmlUrl = body.optString("epgXmlUrl", "")
        val epgXmlUrlName = body.optString("epgXmlUrlName", "")
        val videoPlayerUserAgent = body.optString("videoPlayerUserAgent", "")
        val githubProxy = body.optString("githubProxy", "")

        // 自定义 GitHub 加速器：保存后迁移已有源地址
        if (body.has("githubProxy")) {
            val normalized = Constants.normalizeGithubProxy(
                githubProxy.ifBlank { Constants.GITHUB_PROXY }
            )
            if (normalized != SP.githubProxy) {
                SP.rewriteGithubProxyUrls(normalized)
                showToast("加速器已更新为 $normalized，请重启应用生效")
            } else {
                SP.githubProxy = normalized
            }
        }

        // 重要：网页推送只用于“新增/更新历史列表”，不改变当前已选中的直播源/节目单/UA。
        // 避免 UI 通过“当前值 == 列表项”计算 selected/勾选状态时自动选中新推送的数据。
        if (iptvSourceUrl.isNotBlank()) {
            val migratedUrl = Constants.migrateGithubProxyUrl(iptvSourceUrl, SP.githubProxy)
            SP.iptvSourceUrlHistoryList = SP.iptvSourceUrlHistoryList + migratedUrl
            SP.iptvSourceDeletedList = SP.iptvSourceDeletedList - migratedUrl
            // 保存名称映射（同时用于节目单列表展示）
            if (iptvSourceName.isNotBlank()) {
                SP.iptvSourceNameMap = SP.iptvSourceNameMap + (migratedUrl to iptvSourceName)
            }
        }

        if (epgXmlUrl.isNotBlank()) {
            SP.epgXmlUrlHistoryList = SP.epgXmlUrlHistoryList + epgXmlUrl
            // 保存名称映射
            if (epgXmlUrlName.isNotBlank()) {
                SP.iptvSourceNameMap = SP.iptvSourceNameMap + (epgXmlUrl to epgXmlUrlName)
            }
        }

        if (videoPlayerUserAgent.isNotBlank()) {
            SP.videoPlayerUserAgentHistoryList = SP.videoPlayerUserAgentHistoryList + videoPlayerUserAgent
        }

        wrapResponse(response).send("success")
    }

    private fun handleUploadApk(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
        context: Context,
    ) {
        val body = request.getBody<MultipartFormDataBody>()

        val os = uploadedApkFile.outputStream()
        val contentLength = request.headers["Content-Length"]?.toLong() ?: 1
        var hasReceived = 0L

        body.setMultipartCallback { part ->
            if (part.isFile) {
                body.setDataCallback { _, bb ->
                    val byteArray = bb.allByteArray
                    hasReceived += byteArray.size
                    showToast("正在接收文件: ${(hasReceived * 100f / contentLength).toInt()}%")
                    os.write(byteArray)
                }
            }
        }

        body.setEndCallback {
            showToast("文件接收完成")
            body.dataEmitter.close()
            os.flush()
            os.close()
            ApkInstaller.installApk(context, uploadedApkFile.path)
        }

        wrapResponse(response).send("success")
    }

    private fun getLocalIpAddress(): String {
        val defaultIp = "0.0.0.0"

        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress ?: defaultIp
                    }
                }
            }
            return defaultIp
        } catch (ex: SocketException) {
            log.e("IP Address: ${ex.message}", ex)
            return defaultIp
        }
    }
}

@Serializable
private data class AllSettings(
    val appTitle: String,
    val appRepo: String,
    val iptvSourceUrl: String,
    val iptvSourceName: String,
    val epgXmlUrl: String,
    val epgXmlUrlName: String,
    val videoPlayerUserAgent: String,
    val githubProxy: String,

    val logHistory: List<Logger.HistoryItem>,
)