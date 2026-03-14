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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.voltual.a321.utils.Pan123Utils
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

object KtorClient {
    private const val BASE_URL = "https://www.123pan.com"
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY = 1000L

    val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 60000
        }

        install(Logging) {
            level = LogLevel.HEADERS
            logger = Logger.DEFAULT
        }

        defaultRequest {
            url(BASE_URL)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            // 预设 Android 协议 Header
            header("platform", "android")
            header("app-version", Pan123Utils.ANDROID_APP_VERSION)
            header("x-app-version", Pan123Utils.ANDROID_X_APP_VERSION)
            header("LoginUuid", Pan123Utils.generateLoginUuid())
            header("devicename", Pan123Utils.ANDROID_DEVICE_BRAND)
        }
    }

    // ===== 数据模型 =====

    @Serializable
    data class PanResponse<T>(
        val code: Int,
        val message: String? = null,
        val data: T? = null
    ) {
        val isSuccess: Boolean get() = code == 0 || code == 200
    }

    @Serializable
    data class LoginData(val token: String)

    @Serializable
    data class UserInfo(
        @SerialName("Nickname") val nickname: String,
        @SerialName("UID") val uid: Long,
        @SerialName("SpaceUsed") val spaceUsed: Long,
        @SerialName("SpacePermanent") val spaceTotal: Long
    )

    @Serializable
    data class FileListData(
        val InfoList: List<FileInfo>,
        val Total: Int,
        val Next: Int = 0
    )

    @Serializable
    data class FileInfo(
        val FileId: Long,
        val FileName: String,
        val Type: Int, // 1: 文件夹, 0: 文件
        val Size: Long,
        val Etag: String? = null,
        val S3KeyFlag: String? = null,
        val Category: Int = 0
    ) {
        val isDirectory: Boolean get() = Type == 1
    }

    @Serializable
    data class DownloadData(
        val DownloadUrl: String
    )

    @Serializable
    data class UploadRequestData(
        val Reuse: Boolean,
        val FileId: String? = null,
        val UploadId: String? = null,
        val Bucket: String? = null,
        val Key: String? = null,
        val StorageNode: String? = null
    )

    @Serializable
    data class PreSignedUrls(
        val presignedUrls: Map<String, String>
    )

    // ===== API 接口定义 =====

    interface ApiService {
        suspend fun login(passport: String, password: String): Result<String>
        suspend fun getUserInfo(token: String): Result<UserInfo>
        suspend fun getFileList(token: String, parentId: Long, page: Int): Result<FileListData>
        suspend fun getDownloadUrl(token: String, file: FileInfo): Result<String>
        suspend fun uploadFile(token: String, file: File, parentId: Long, onProgress: (Float) -> Unit): Result<Boolean>
    }

    object ApiServiceImpl : ApiService {

        override suspend fun login(passport: String, password: String): Result<String> {
            val osVersion = Pan123Utils.getRandomOsVersion()
            return safeApiCall<PanResponse<LoginData>> {
                httpClient.post("/b/api/user/sign_in") {
                    header(HttpHeaders.UserAgent, Pan123Utils.getUserAgent(osVersion))
                    setBody(mapOf("type" to 1, "passport" to passport, "password" to password))
                }
            }.map { 
                if (it.isSuccess) it.data?.token ?: "" 
                else throw IOException(it.message ?: "Login Failed")
            }
        }

        override suspend fun getUserInfo(token: String): Result<UserInfo> {
            return safeApiCall<PanResponse<UserInfo>> {
                httpClient.get("/b/api/user/info") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }.map { it.data ?: throw IOException("User info empty") }
        }

        override suspend fun getFileList(token: String, parentId: Long, page: Int): Result<FileListData> {
            return safeApiCall<PanResponse<FileListData>> {
                httpClient.get("/api/file/list/new") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    parameter("driveId", 0)
                    parameter("limit", 100)
                    parameter("parentFileId", parentId)
                    parameter("Page", page)
                    parameter("trashed", false)
                    parameter("orderBy", "file_id")
                    parameter("orderDirection", "desc")
                }
            }.map { it.data ?: FileListData(emptyList(), 0) }
        }

        override suspend fun getDownloadUrl(token: String, file: FileInfo): Result<String> {
            val path = if (file.isDirectory) "/a/api/file/batch_download_info" else "/a/api/file/download_info"
            val body = if (file.isDirectory) {
                mapOf("fileIdList" to listOf(mapOf("fileId" to file.FileId)))
            } else {
                mapOf(
                    "fileId" to file.FileId,
                    "etag" to file.Etag,
                    "s3keyFlag" to file.S3KeyFlag,
                    "size" to file.Size,
                    "type" to file.Type,
                    "fileName" to file.FileName
                )
            }

            return safeApiCall<PanResponse<DownloadData>> {
                httpClient.post(path) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    setBody(body)
                }
            }.map { resp ->
                val rawUrl = resp.data?.DownloadUrl ?: throw IOException("No download URL")
                // 跟随 302 重定向获取直链
                val finalResp = httpClient.get(rawUrl) { followRedirects = false }
                finalResp.headers[HttpHeaders.Location] ?: rawUrl
            }
        }

        /**
         * 上传文件流程：请求上传 -> (秒传成功 ? 结束 : 分块上传 -> 合并 -> 完成)
         */
        override suspend fun uploadFile(
            token: String,
            file: File,
            parentId: Long,
            onProgress: (Float) -> Unit
        ): Result<Boolean> {
            val md5 = Pan123Utils.calculateMd5(file)
            val size = file.length()

            // 1. 请求上传 (检查秒传)
            val reqResp = safeApiCall<PanResponse<UploadRequestData>> {
                httpClient.post("/b/api/file/upload_request") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    setBody(mapOf(
                        "driveId" to 0,
                        "etag" to md5,
                        "fileName" to file.name,
                        "parentFileId" to parentId,
                        "size" to size,
                        "type" to 0
                    ))
                }
            }.getOrElse { return Result.failure(it) }

            val uploadInfo = reqResp.data ?: return Result.failure(IOException("Upload request failed"))
            if (uploadInfo.Reuse) return Result.success(true) // 秒传成功

            // 2. 分块上传逻辑 (简化版：假设单块或循环上传)
            val chunkSize = 5 * 1024 * 1024L // 5MB
            val totalChunks = ((size + chunkSize - 1) / chunkSize).toInt()
            
            val raf = RandomAccessFile(file, "r")
            for (i in 1..totalChunks) {
                // 获取预签名 URL
                val urlResp = safeApiCall<PanResponse<PreSignedUrls>> {
                    httpClient.post("/b/api/file/s3_repare_upload_parts_batch") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        setBody(mapOf(
                            "bucket" to uploadInfo.Bucket,
                            "key" to uploadInfo.Key,
                            "uploadId" to uploadInfo.UploadId,
                            "partNumberStart" to i,
                            "partNumberEnd" to i,
                            "StorageNode" to uploadInfo.StorageNode
                        ))
                    }
                }.getOrElse { return Result.failure(it) }

                val uploadUrl = urlResp.data?.presignedUrls?.get(i.toString()) ?: return Result.failure(IOException("Get S3 URL failed"))

                // 读取分块并 PUT
                val buffer = ByteArray(if (i == totalChunks) (size - (i - 1) * chunkSize).toInt() else chunkSize.toInt())
                raf.seek((i - 1) * chunkSize)
                raf.readFully(buffer)

                val putStatus = httpClient.put(uploadUrl) {
                    setBody(buffer)
                }.status
                
                if (!putStatus.isSuccess()) return Result.failure(IOException("Chunk $i upload failed"))
                onProgress(i.toFloat() / totalChunks)
            }
            raf.close()

            // 3. 合并分块
            safeApiCall<PanResponse<Unit>> {
                httpClient.post("/b/api/file/s3_complete_multipart_upload") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    setBody(mapOf(
                        "bucket" to uploadInfo.Bucket,
                        "key" to uploadInfo.Key,
                        "uploadId" to uploadInfo.UploadId,
                        "StorageNode" to uploadInfo.StorageNode
                    ))
                }
            }

            // 4. 确认完成
            return safeApiCall<PanResponse<Unit>> {
                httpClient.post("/b/api/file/upload_complete") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    setBody(mapOf("fileId" to uploadInfo.FileId))
                }
            }.map { it.isSuccess }
        }
    }

    /**
     * 通用请求封装
     */
    private suspend inline fun <reified T> safeApiCall(block: suspend () -> HttpResponse): Result<T> {
        var attempts = 0
        while (attempts < MAX_RETRIES) {
            try {
                val response = block()
                if (response.status.value == 401) return Result.failure(IOException("Unauthorized"))
                if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value}")
                return Result.success(response.body())
            } catch (e: Exception) {
                attempts++
                if (attempts >= MAX_RETRIES) return Result.failure(e)
                delay(RETRY_DELAY)
            }
        }
        return Result.failure(IOException("Unknown error"))
    }
}