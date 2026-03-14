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
import me.voltual.a321.data.UpdateInfo
import me.voltual.a321.utils.PanUtils
import java.io.IOException

object KtorClient {
    private const val BASE_URL = "https://www.123pan.com"
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY = 1000L

    // 协议常量 (参考 Python 原型)
    private const val ANDROID_APP_VERSION = "61"
    private const val ANDROID_X_APP_VERSION = "2.4.0"

    val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
        }

        defaultRequest {
            url(BASE_URL)
            // 完整伪装 Android 协议 Header
            header("platform", "android")
            header("app-version", ANDROID_APP_VERSION)
            header("x-app-version", ANDROID_X_APP_VERSION)
            header("devicetype", PanUtils.getRandomDeviceType())
            header("devicename", "Xiaomi")
            header("osversion", "Android_13")
            header("LoginUuid", PanUtils.generateLoginUuid())
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
        }

        install(Logging) {
            level = LogLevel.INFO
        }
        
        // 处理重定向：获取下载直链时我们需要手动处理 302
        followRedirects = false 
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
        val Category: Int = 0,
        val Status: Int = 0
    ) {
        val isDirectory: Boolean get() = Type == 1
    }

    @Serializable
    data class DownloadData(
        val DownloadUrl: String
    )

    @Serializable
    data class LoginData(
        val token: String
    )

    // ===== API 接口定义 =====

    interface ApiService {
        // 兼容 UpdateChecker.kt
        suspend fun getLatestRelease(url: String): Result<UpdateInfo>
        
        // 兼容 PanRepository.kt
        suspend fun getFileList(token: String, page: Int, parentId: Long = 0): Result<PanResponse<FileListData>>

        // 新增功能
        suspend fun login(passport: String, password: String): Result<PanResponse<LoginData>>
        suspend fun getDownloadUrl(token: String, file: FileInfo): Result<String>
        suspend fun createFolder(token: String, name: String, parentId: Long): Result<PanResponse<Unit>>
        suspend fun deleteFiles(token: String, fileIds: List<Long>): Result<PanResponse<Unit>>
    }

    object ApiServiceImpl : ApiService {

        override suspend fun getLatestRelease(url: String): Result<UpdateInfo> {
            return safeApiCall { httpClient.get(url) }
        }

        override suspend fun login(passport: String, password: String): Result<PanResponse<LoginData>> {
            return safeApiCall {
                httpClient.post("/b/api/user/sign_in") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf(
                        "type" to 1,
                        "passport" to passport,
                        "password" to password
                    ))
                }
            }
        }

        override suspend fun getFileList(token: String, page: Int, parentId: Long): Result<PanResponse<FileListData>> {
            return safeApiCall {
                httpClient.get("/api/file/list/new") {
                    bearerAuth(token)
                    url {
                        parameters.append("driveId", "0")
                        parameters.append("limit", "100")
                        parameters.append("Page", page.toString())
                        parameters.append("parentFileId", parentId.toString())
                        parameters.append("orderBy", "file_id")
                        parameters.append("orderDirection", "desc")
                        parameters.append("trashed", "false")
                    }
                }
            }
        }

        /**
         * 获取下载直链
         * 参考 Python 原型：如果是文件夹走 batch_download_info，文件走 download_info
         */
        override suspend fun getDownloadUrl(token: String, file: FileInfo): Result<String> {
            val endpoint = if (file.isDirectory) "/a/api/file/batch_download_info" else "/a/api/file/download_info"
            
            val payload = if (file.isDirectory) {
                mapOf("fileIdList" to listOf(mapOf("fileId" to file.FileId)))
            } else {
                mapOf(
                    "fileId" to file.FileId,
                    "etag" to file.Etag,
                    "size" to file.Size,
                    "fileName" to file.FileName,
                    "s3keyFlag" to file.S3KeyFlag,
                    "type" to 0,
                    "driveId" to 0
                )
            }

            val responseResult: Result<PanResponse<DownloadData>> = safeApiCall {
                httpClient.post(endpoint) {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            }

            return responseResult.mapCatching { response ->
                val rawUrl = response.data?.DownloadUrl ?: throw IOException("未获取到下载链接")
                
                // 处理 302 重定向以获取真实直链 (Python 原型中的核心逻辑)
                val headResponse = httpClient.get(rawUrl)
                if (headResponse.status == HttpStatusCode.Found) {
                    headResponse.headers[HttpHeaders.Location] ?: rawUrl
                } else {
                    rawUrl
                }
            }
        }

        override suspend fun createFolder(token: String, name: String, parentId: Long): Result<PanResponse<Unit>> {
            return safeApiCall {
                httpClient.post("/b/api/file/upload_request") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(mapOf(
                        "fileName" to name,
                        "parentFileId" to parentId,
                        "type" to 1,
                        "duplicate" to 1
                    ))
                }
            }
        }

        override suspend fun deleteFiles(token: String, fileIds: List<Long>): Result<PanResponse<Unit>> {
            return safeApiCall {
                httpClient.post("/a/api/file/trash") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(mapOf(
                        "driveId" to 0,
                        "fileTrashInfoList" to fileIds.map { mapOf("FileId" to it) },
                        "operation" to true
                    ))
                }
            }
        }
    }

    /**
     * 安全地执行 Ktor 请求
     */
    private suspend inline fun <reified T> safeApiCall(block: suspend () -> HttpResponse): Result<T> {
        var attempts = 0
        while (attempts < MAX_RETRIES) {
            try {
                val response = block()
                if (response.status.value in 300..399 && T::class == String::class) {
                    // 特殊处理重定向返回
                    return Result.success(response as T)
                }
                if (!response.status.isSuccess() && response.status != HttpStatusCode.Found) {
                    throw IOException("HTTP Error: ${response.status}")
                }
                return Result.success(response.body())
            } catch (e: Exception) {
                attempts++
                if (attempts >= MAX_RETRIES) return Result.failure(e)
                delay(RETRY_DELAY)
            }
        }
        return Result.failure(IOException("Request failed after retries"))
    }

    private fun HttpRequestBuilder.bearerAuth(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    fun close() {
        httpClient.close()
    }
}