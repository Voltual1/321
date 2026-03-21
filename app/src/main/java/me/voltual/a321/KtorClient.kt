//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
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
                encodeDefaults = true // 关键修复：强制序列化默认值
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
    
    // ===== 请求模型定义 (用于 POST Body) =====

@Serializable
data class LoginRequest(
    val type: Int = 1,
    val passport: String,
    val password: String
)

@Serializable
data class UploadRequest(
    val driveId: Int = 0,
    val parentFileId: Long,
    val fileName: String,
    val size: Long,
    val etag: String,
    val type: Int = 0,
    val duplicate: Int = 2
)

@Serializable
data class S3PartUrlsRequest(
    val bucket: String,
    val key: String,
    val partNumberStart: Int,
    val partNumberEnd: Int,
    val uploadId: String,
    val StorageNode: String
)

@Serializable
data class CompleteS3UploadRequest(
    val bucket: String,
    val key: String,
    val uploadId: String,
    val StorageNode: String
)

@Serializable
data class ConfirmUploadRequest(
    val fileId: String
)

@Serializable
data class TrashRequest(
    val driveId: Int = 0,
    val fileTrashInfoList: List<TrashItem>,
    val operation: Boolean
)

@Serializable
data class TrashItem(
    val FileId: Long
)

@Serializable
data class CreateFolderRequest(
    val fileName: String,
    val parentFileId: Long,
    val type: Int = 1,
    val duplicate: Int = 1
)

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
data class UploadRequestData(
    val Reuse: Boolean, // 是否秒传成功
    val FileId: String? = null,
    val UploadId: String? = null,
    val Bucket: String? = null,
    val Key: String? = null,
    val StorageNode: String? = null
)

    @Serializable
data class S3PartUrlsData(
    val presignedUrls: Map<String, String> // PartNumber -> URL
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
    val DownloadUrl: String = "", 
    val Status: Int = 0,
    val UpdateAt: String = "" // 补全此字段
) {
    val isDirectory: Boolean get() = Type == 1
    // 补全此逻辑：123网盘 Status > 100 通常表示文件异常（被封禁或审核不通过）
    val isAbnormal: Boolean get() = Status > 100 
}

    @Serializable
    data class DownloadData(
        val DownloadUrl: String
    )

    @Serializable
    data class LoginData(
        val token: String
    )
    
    // ===== 下载相关的请求模型 =====

@Serializable
data class DownloadInfoRequest(
    val driveId: Int = 0,
    val fileId: Long,
    val etag: String?,
    val fileName: String,
    val size: Long,
    val s3keyFlag: String?,
    val type: Int = 0
)

@Serializable
data class BatchDownloadRequest(
    val fileIdList: List<BatchDownloadItem>
)

@Serializable
data class BatchDownloadItem(
    val fileId: Long
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
        // 1. 请求上传（含秒传校验）
    suspend fun requestUpload(token: String, parentId: Long, fileName: String, size: Long, md5: String): Result<PanResponse<UploadRequestData>>

    // 2. 获取分块上传的预签名 URL
    suspend fun getS3PartUrls(token: String, bucket: String, key: String, uploadId: String, storageNode: String, partNumber: Int): Result<PanResponse<S3PartUrlsData>>

    // 3. 合并 S3 分块
    suspend fun completeS3Upload(token: String, bucket: String, key: String, uploadId: String, storageNode: String): Result<PanResponse<Unit>>

    // 4. 最终确认上传完成
    suspend fun confirmUpload(token: String, fileId: String): Result<PanResponse<Unit>>
    }

    object ApiServiceImpl : ApiService {

        override suspend fun getLatestRelease(url: String): Result<UpdateInfo> {
            return safeApiCall { httpClient.get(url) }
        }

            override suspend fun login(passport: String, password: String) = safeApiCall<PanResponse<LoginData>> {
        httpClient.post("/b/api/user/sign_in") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(passport = passport, password = password))
        }
    }

    override suspend fun requestUpload(token: String, parentId: Long, fileName: String, size: Long, md5: String) = safeApiCall<PanResponse<UploadRequestData>> {
        httpClient.post("/b/api/file/upload_request") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UploadRequest(
                parentFileId = parentId,
                fileName = fileName,
                size = size,
                etag = md5
            ))
        }
    }

    override suspend fun getS3PartUrls(token: String, bucket: String, key: String, uploadId: String, storageNode: String, partNumber: Int) = safeApiCall<PanResponse<S3PartUrlsData>> {
        httpClient.post("/b/api/file/s3_repare_upload_parts_batch") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(S3PartUrlsRequest(
                bucket = bucket,
                key = key,
                partNumberStart = partNumber,
                // 关键修复：End 必须比 Start 大 1 才能获取到当前块的 URL
            partNumberEnd = partNumber + 1, 
                uploadId = uploadId,
                StorageNode = storageNode
            ))
        }
    }

    override suspend fun completeS3Upload(token: String, bucket: String, key: String, uploadId: String, storageNode: String) = safeApiCall<PanResponse<Unit>> {
        httpClient.post("/b/api/file/s3_complete_multipart_upload") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CompleteS3UploadRequest(
                bucket = bucket,
                key = key,
                uploadId = uploadId,
                StorageNode = storageNode
            ))
        }
    }

    override suspend fun confirmUpload(token: String, fileId: String) = safeApiCall<PanResponse<Unit>> {
        httpClient.post("/b/api/file/upload_complete") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ConfirmUploadRequest(fileId = fileId))
        }
    }

        override suspend fun getFileList(token: String, page: Int, parentId: Long): Result<PanResponse<FileListData>> {
            return safeApiCall {
                httpClient.get("/api/file/list/new") {
                    bearerAuth(token)
                    url {
                        parameters.append("driveId", "0")
                        parameters.append("limit", "4")
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
    
    // 使用具体的 Serializable 对象替代 mapOf
    val requestBody: Any = if (file.isDirectory) {
        BatchDownloadRequest(listOf(BatchDownloadItem(file.FileId)))
    } else {
        DownloadInfoRequest(
            fileId = file.FileId,
            etag = file.Etag,
            fileName = file.FileName,
            size = file.Size,
            s3keyFlag = file.S3KeyFlag
        )
    }

    val responseResult: Result<PanResponse<DownloadData>> = safeApiCall {
        httpClient.post(endpoint) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
    }

    return responseResult.mapCatching { response ->
        val rawUrl = response.data?.DownloadUrl ?: throw IOException("未获取到下载链接")
        
        val headResponse = httpClient.get(rawUrl)
        if (headResponse.status == HttpStatusCode.Found) {
            headResponse.headers[HttpHeaders.Location] ?: rawUrl
        } else {
            rawUrl
        }
    }
}

        override suspend fun createFolder(token: String, name: String, parentId: Long) = safeApiCall<PanResponse<Unit>> {
        httpClient.post("/b/api/file/upload_request") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CreateFolderRequest(fileName = name, parentFileId = parentId))
        }
    }

    override suspend fun deleteFiles(token: String, fileIds: List<Long>) = safeApiCall<PanResponse<Unit>> {
        httpClient.post("/a/api/file/trash") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(TrashRequest(
                fileTrashInfoList = fileIds.map { TrashItem(it) },
                operation = true
            ))
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