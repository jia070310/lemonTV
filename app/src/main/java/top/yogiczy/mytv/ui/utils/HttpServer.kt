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
import top.yogiczy.mytv.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.utils.ApkInstaller
import top.yogiczy.mytv.utils.Loggable
import top.yogiczy.mytv.utils.Logger
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

object HttpServer : Loggable() {
    private const val SERVER_PORT = 10481

    private val uploadedApkFile = File(AppGlobal.cacheDir, "uploaded_apk.apk").apply {
        deleteOnExit()
    }

    private var showToast: (String) -> Unit = { }

    val serverUrl: String by lazy {
        "http://${getLocalIpAddress()}:${SERVER_PORT}"
    }

    fun start(context: Context, showToast: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val server = AsyncHttpServer()
                server.listen(AsyncServer.getDefault(), SERVER_PORT)

                server.get("/") { _, response ->
                    handleRawResource(response, context, "text/html", R.raw.index)
                }
                server.get("/index_css.css") { _, response ->
                    handleRawResource(response, context, "text/css", R.raw.index_css)
                }
                server.get("/index_js.js") { _, response ->
                    handleRawResource(response, context, "text/javascript", R.raw.index_js)
                }

                server.get("/api/settings") { _, response ->
                    handleGetSettings(response)
                }

                server.post("/api/settings") { request, response ->
                    handleSetSettings(request, response)
                }

                server.post("/api/upload/apk") { request, response ->
                    handleUploadApk(request, response, context)
                }

                HttpServer.showToast = showToast
                log.i("服务已启动: 0.0.0.0:${SERVER_PORT}")
            } catch (ex: Exception) {
                log.e("服务启动失败: ${ex.message}", ex)
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "设置服务启动失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        val iptvSourceUrl = body.get("iptvSourceUrl").toString()
        val iptvSourceName = body.optString("iptvSourceName", "")
        val epgXmlUrl = body.get("epgXmlUrl").toString()
        val epgXmlUrlName = body.optString("epgXmlUrlName", "")
        val videoPlayerUserAgent = body.get("videoPlayerUserAgent").toString()

        if (SP.iptvSourceUrl != iptvSourceUrl) {
            SP.iptvSourceUrl = iptvSourceUrl
            // 添加到历史记录，这样电视端设置界面会自动显示
            if (iptvSourceUrl.isNotBlank()) {
                SP.iptvSourceUrlHistoryList = SP.iptvSourceUrlHistoryList + iptvSourceUrl
                // 保存名称映射
                if (iptvSourceName.isNotBlank()) {
                    SP.iptvSourceNameMap = SP.iptvSourceNameMap + (iptvSourceUrl to iptvSourceName)
                }
            }
            IptvRepository().clearCache()
        }

        if (SP.epgXmlUrl != epgXmlUrl) {
            SP.epgXmlUrl = epgXmlUrl
            // 添加到历史记录
            if (epgXmlUrl.isNotBlank()) {
                SP.epgXmlUrlHistoryList = SP.epgXmlUrlHistoryList + epgXmlUrl
                // 保存名称映射
                if (epgXmlUrlName.isNotBlank()) {
                    SP.iptvSourceNameMap = SP.iptvSourceNameMap + (epgXmlUrl to epgXmlUrlName)
                }
            }
            EpgRepository().clearCache()
        }

        if (SP.videoPlayerUserAgent != videoPlayerUserAgent) {
            SP.videoPlayerUserAgent = videoPlayerUserAgent
            // 添加到历史记录
            if (videoPlayerUserAgent.isNotBlank()) {
                SP.videoPlayerUserAgentHistoryList = SP.videoPlayerUserAgentHistoryList + videoPlayerUserAgent
            }
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

    val logHistory: List<Logger.HistoryItem>,
)