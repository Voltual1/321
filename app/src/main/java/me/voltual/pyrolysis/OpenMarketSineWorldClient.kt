//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.

@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package me.voltual.pyrolysis

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

object OpenMarketSineWorldClient {
    private const val BASE_URL = "https://open.sineshop.xin"    
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY = 1000L
    private const val REQUEST_TIMEOUT = 60000L
    private const val CONNECT_TIMEOUT = 30000L
    private const val SOCKET_TIMEOUT = 60000L

    // 用户代理信息
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"

    // Ktor HttpClient 实例
    val httpClient = HttpClient(OkHttp) {
        initConfig(this)
    }

    private fun initConfig(client: HttpClientConfig<OkHttpConfig>) {
        // 默认请求配置
        client.defaultRequest {
            url(BASE_URL)
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json, text/plain, */*")
        }

        // JSON 序列化配置
        client.install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
        }

        // 日志配置
        client.install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS
        }

        // 超时配置
        client.install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT
            connectTimeoutMillis = CONNECT_TIMEOUT
            socketTimeoutMillis = SOCKET_TIMEOUT
        }
    }

    // ===== 数据模型 =====

    // 基础响应模型
    @Serializable
    data class OpenMarketBaseResponse<T>(
        val code: Int,
        val msg: String,
        val data: T? = null
    ) {
        val isSuccess: Boolean get() = code == 200
    }

    // 登录响应数据
    @Serializable
    data class LoginData(
        val token: String,
        val user: SineShopUserInfoLite // 复用 SineShopClient 中的简易用户信息模型
    )

    // 简单的用户信息模型
    @Serializable
    data class SineShopUserInfoLite(
        val id: Int,
        val username: String,
        @SerialName("display_name") val displayName: String,
        @SerialName("user_avatar") val userAvatar: String?
    )

    // 预上传响应数据
    @Serializable
    data class PreUploadData(
        @SerialName("app_id") val appId: Int,
        @SerialName("upload_token") val uploadToken: String
    )

    // APK上传响应数据
    @Serializable
    data class UploadApkResponse(
        val filename: String,
        val message: String,
        val path: String,
        val size: Long
    )

    // 预上传所需的参数封装类
    data class AppReleaseInfo(
        val appName: String,
        val packageName: String,
        val versionName: String,
        val versionCode: String,
        val appTypeId: Int,
        val appVersionTypeId: Int,
        val appTags: String, 
        val appSdkMin: Int,
        val appSdkTarget: Int,
        val appDeveloper: String,
        val appSource: String,
        val appDescribe: String,
        val appUpdateLog: String,
        val uploadMessage: String,
        val keyword: String,
        val appIsWearos: Int, // 0 或 1
        val appAbi: Int,
        val downloadSize: Long,
        val iconFile: File,
        val screenshotFiles: List<File>
    )

    // ===== API 方法 =====

    /**
     * 登录开放平台
     */
    suspend fun login(username: String, password: String): Result<LoginData> {
        val url = "/api/v1/auth/login"
        val requestBody = mapOf(
            "username" to username,
            "password" to password
        )
        
        return safeApiCall<OpenMarketBaseResponse<LoginData>> {
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
        }.map { response ->
            if (response.isSuccess && response.data != null) {
                response.data
            } else {
                throw IOException(response.msg)
            }
        }
    }

    /**
     * 预上传 (提交应用信息)
     * 返回 upload_token 用于后续上传 APK
     */
    suspend fun preUpload(info: AppReleaseInfo): Result<PreUploadData> {
        val url = "/api/v1/apps/pre-upload"
        val token = getToken()
        
        return safeApiCall<OpenMarketBaseResponse<PreUploadData>> {
            httpClient.submitFormWithBinaryData(
                url = url,
                formData = formData {
                    // 文本字段
                    append("app_name", info.appName)
                    append("package_name", info.packageName)
                    append("version_name", info.versionName)
                    append("version_code", info.versionCode)
                    append("app_type_id", info.appTypeId.toString())
                    append("app_version_type_id", info.appVersionTypeId.toString())
                    append("app_tags", info.appTags)
                    append("app_sdk_min", info.appSdkMin.toString())
                    append("app_sdk_target", info.appSdkTarget.toString())
                    append("app_developer", info.appDeveloper)
                    append("app_source", info.appSource)
                    append("app_describe", info.appDescribe)
                    append("app_update_log", info.appUpdateLog)
                    append("upload_message", info.uploadMessage)
                    append("keyword", info.keyword)
                    append("app_is_wearos", info.appIsWearos.toString())
                    append("app_abi", info.appAbi.toString())
                    append("download_size", info.downloadSize.toString())

                    // 图标文件
                    append("icon", InputProvider { info.iconFile.inputStream().asInput() }, Headers.build {
                        append(HttpHeaders.ContentType, "image/png") // 假设是PNG，或者根据文件扩展名判断
                        append(HttpHeaders.ContentDisposition, "filename=\"icon.png\"")
                    })

                    // 截图文件列表
                    info.screenshotFiles.forEachIndexed { index, file ->
                        val filename = "screenshot_$index.${file.extension}"
                        val contentType = if (file.extension.lowercase() in listOf("jpg", "jpeg")) "image/jpeg" else "image/png"
                        
                        append("screenshots", InputProvider { file.inputStream().asInput() }, Headers.build {
                            append(HttpHeaders.ContentType, contentType)
                            append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                        })
                    }
                }
            ) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }.map { response ->
            if (response.isSuccess && response.data != null) {
                response.data
            } else {
                throw IOException(response.msg)
            }
        }
    }

    /**
     * 上传 APK 文件
     * 使用 preUpload 返回的 uploadToken
     */
    suspend fun uploadApk(apkFile: File, uploadToken: String): Result<UploadApkResponse> {
        // 注意：上传接口路径没有 /v1/
        val url = "/api/upload" 
        
        return safeApiCall<UploadApkResponse> {
            httpClient.submitFormWithBinaryData(
                url = url,
                formData = formData {
                    append("file", InputProvider { apkFile.inputStream().asInput() }, Headers.build {
                        append(HttpHeaders.ContentType, "application/vnd.android.package-archive")
                        append(HttpHeaders.ContentDisposition, "filename=\"${apkFile.name}\"")
                    })
                }
            ) {
                // Token 作为 Query 参数传递
                parameter("token", uploadToken)
            }
        }.map { response ->
            // 这里的响应结构直接就是 UploadApkResponse，没有包裹在 BaseResponse 中 (根据抓包数据)
            // 但如果服务器返回错误，可能会有不同的结构，这里假设成功路径
            response 
        }
    }

    /**
     * 安全地执行 Ktor 请求
     */
    @Suppress("RedundantSuspendModifier")
    private suspend inline fun <reified T> safeApiCall(block: suspend () -> HttpResponse): Result<T> {
        var attempts = 0
        while (attempts < MAX_RETRIES) {
            try {
                val response = block()
                // 检查 HTTP 状态码
                if (!response.status.isSuccess()) {
                    throw IOException("Request failed with status: ${response.status}")
                }
                val responseBody: T = try {
                    response.body()
                } catch (e: Exception) {
                    println("OpenMarket Failed to deserialize response body: ${e.message}")
                    throw e
                }
                return Result.success(responseBody)
            } catch (e: IOException) {
                attempts++
                if (attempts < MAX_RETRIES) {
                    delay(RETRY_DELAY)
                }
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }
        return Result.failure(IOException("Request failed after $MAX_RETRIES attempts."))
    }

    /**
     * 获取 Open Market Token
     */
    private fun getToken(): String {
        return runBlocking {
            AuthManager.getSineOpenMarketToken(BBQApplication.instance).first()
        }
    }

    /**
     * 关闭客户端
     */
    fun close() {
        httpClient.close()
    }
}