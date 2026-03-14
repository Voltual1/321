// Copyright (C) 2025 Voltual
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package me.voltual.a321

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.voltual.a321.utils.PanUtils
import java.io.IOException

object KtorClient {
    private const val BASE_URL = "https://www.123pan.com"
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY = 1000L

    // 协议版本常量
    private const val ANDROID_APP_VERSION = "61"
    private const val ANDROID_X_APP_VERSION = "3.1.3"

    // 运行时状态
    private var authToken: String? = null
    private val loginUuid = PanUtils.generateLoginUuid()
    private val deviceType = PanUtils.DEVICE_TYPES.random()
    private val osVersion = PanUtils.OS_VERSIONS.random()

    val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }

        defaultRequest {
            url(BASE_URL)
            // 模拟安卓客户端请求头
            header("User-Agent", "123pan/v$ANDROID_X_APP_VERSION($osVersion;Xiaomi)")
            header("Platform", "android")
            header("App-Version", ANDROID_APP_VERSION)
            header("X-App-Version", ANDROID_X_APP_VERSION)
            header("DeviceType", deviceType)
            header("LoginUuid", loginUuid)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            
            // 动态注入 Token
            authToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
    }

    // ===== 数据模型 =====

    @Serializable
    data class PanResponse<T>(
        val code: Int,
        val message: String,
        val data: T? = null
    ) {
        val isSuccess: Boolean get() = code == 0 || code == 200
    }

    @Serializable
    data class LoginData(val token: String)

    @Serializable
    data class UserInfo(
        val UID: Long,
        val Nickname: String,
        val SpaceUsed: Long,
        val SpacePermanent: Long
    )

    @Serializable
    data class FileListData(
        val Total: Int,
        val InfoList: List<FileInfo>
    )

    @Serializable
    data class FileInfo(
        val FileId: Long,
        val FileName: String,
        val Type: Int, // 1: 文件夹, 0: 文件
        val Size: Long,
        val Etag: String? = null,
        val S3KeyFlag: String? = null,
        val Category: Int? = null
    ) {
        val isDirectory: Boolean get() = Type == 1
    }

    @Serializable
    data class DownloadInfo(
        val DownloadUrl: String
    )

    @Serializable
    data class ShareData(
        val ShareKey: String
    )

    // ===== 核心 API 方法 =====

    /**
     * 登录
     */
    suspend fun login(passport: String, password: String): Result<String> {
        return safeApiCall<PanResponse<LoginData>> {
            httpClient.post("/b/api/user/sign_in") {
                setBody(mapOf(
                    "type" to 1,
                    "passport" to passport,
                    "password" to password
                ))
            }
        }.map { response ->
            if (response.isSuccess && response.data != null) {
                this.authToken = response.data.token
                response.data.token
            } else {
                throw IOException(response.message)
            }
        }
    }

    /**
     * 获取用户信息
     */
    suspend fun getUserInfo(): Result<UserInfo> {
        return safeApiCall<PanResponse<UserInfo>> {
            httpClient.get("/b/api/user/info")
        }.map { it.data ?: throw IOException("Empty user data") }
    }

    /**
     * 获取文件列表
     */
    suspend fun getFileList(
        parentFileId: Long = 0,
        page: Int = 1,
        limit: Int = 100
    ): Result<FileListData> {
        return safeApiCall<PanResponse<FileListData>> {
            httpClient.get("/api/file/list/new") {
                parameter("driveId", 0)
                parameter("parentFileId", parentFileId)
                parameter("limit", limit)
                parameter("page", page)
                parameter("orderBy", "file_id")
                parameter("orderDirection", "desc")
                parameter("trashed", false)
            }
        }.map { it.data ?: FileListData(0, emptyList()) }
    }

    /**
     * 获取下载直链
     * 参考 Python 原型：需要处理 302 或 HTML 提取
     */
    suspend fun getDownloadUrl(file: FileInfo): Result<String> {
        val endpoint = if (file.isDirectory) "/a/api/file/batch_download_info" else "/a/api/file/download_info"
        val payload = if (file.isDirectory) {
            mapOf("fileIdList" to listOf(mapOf("fileId" to file.FileId)))
        } else {
            mapOf(
                "fileId" to file.FileId,
                "etag" to file.Etag,
                "fileName" to file.FileName,
                "size" to file.Size,
                "type" to file.Type,
                "s3keyFlag" to file.S3KeyFlag,
                "driveId" to 0
            )
        }

        val initialRes = safeApiCall<PanResponse<DownloadInfo>> {
            httpClient.post(endpoint) { setBody(payload) }
        }

        return initialRes.mapCatching { response ->
            val rawUrl = response.data?.DownloadUrl ?: throw IOException("No download URL")
            
            // 执行一次 HEAD 或 GET 请求来跟随重定向获取最终直链
            val finalResponse = httpClient.get(rawUrl) {
                // 123盘直链获取有时需要关闭重定向自动处理来手动捕获 Location
                // 但 Ktor 默认处理重定向，这里我们直接取最终响应的 URL
            }
            finalResponse.request.url.toString()
        }
    }

    /**
     * 创建分享
     */
    suspend fun createShare(fileIds: List<Long>, pwd: String = ""): Result<String> {
        return safeApiCall<PanResponse<ShareData>> {
            httpClient.post("/a/api/share/create") {
                setBody(mapOf(
                    "driveId" to 0,
                    "fileIdList" to fileIds.joinToString(","),
                    "shareName" to "分享文件",
                    "sharePwd" to pwd,
                    "expiration" to "2099-12-12T08:00:00+08:00"
                ))
            }
        }.map { response ->
            if (response.isSuccess) {
                "$BASE_URL/s/${response.data?.ShareKey}"
            } else throw IOException(response.message)
        }
    }

    /**
     * 移动到回收站
     */
    suspend fun deleteFiles(fileIds: List<Long>): Result<Boolean> {
        val list = fileIds.map { mapOf("FileId" to it) }
        return safeApiCall<PanResponse<JsonElement>> {
            httpClient.post("/a/api/file/trash") {
                setBody(mapOf(
                    "driveId" to 0,
                    "fileTrashInfoList" to list,
                    "operation" to true
                ))
            }
        }.map { it.isSuccess }
    }

    /**
     * 创建文件夹
     */
    suspend fun mkdir(name: String, parentId: Long = 0): Result<Boolean> {
        return safeApiCall<PanResponse<JsonElement>> {
            httpClient.post("/b/api/file/upload_request") {
                setBody(mapOf(
                    "driveId" to 0,
                    "parentFileId" to parentId,
                    "fileName" to name,
                    "type" to 1, // 1 表示目录
                    "duplicate" to 1
                ))
            }
        }.map { it.isSuccess }
    }

    // ===== 通用辅助方法 =====

    @Suppress("RedundantSuspendModifier")
    private suspend inline fun <reified T> safeApiCall(block: suspend () -> HttpResponse): Result<T> {
        var attempts = 0
        while (attempts < MAX_RETRIES) {
            try {
                val response = block()
                if (!response.status.isSuccess()) {
                    throw IOException("HTTP Error: ${response.status}")
                }
                return Result.success(response.body())
            } catch (e: Exception) {
                attempts++
                if (attempts >= MAX_RETRIES) return Result.failure(e)
                delay(RETRY_DELAY)
            }
        }
        return Result.failure(IOException("Unknown error"))
    }

    /**
     * 设置外部传入的 Token (用于持久化加载)
     */
    fun setToken(token: String) {
        this.authToken = token
    }
}