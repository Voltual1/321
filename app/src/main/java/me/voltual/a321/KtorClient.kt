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
import io.ktor.client.request.forms.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.voltual.a321.data.UpdateInfo
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import java.io.ByteArrayInputStream
import java.io.InputStream
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import io.ktor.utils.io.*
import io.ktor.http.content.*

object KtorClient {
    private const val BASE_URL = "https://www.123pan.com/"
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
        
        defaultRequest {
            url(BASE_URL)
            // 抓包中的固定 Header
            header("user-agent", "123pan/v3.1.3(Android_9;vivo)")
            header("platform", "android")
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
        
        install(Logging) {
            level = LogLevel.INFO
        }
    }
    
    @Serializable
data class PanResponse<T>(
    val code: Int,
    val message: String,
    val data: T
)

@Serializable
data class FileListData(
    val Next: String,
    val Len: Int,
    val IsFirst: Boolean,
    val Total: Int,
    val InfoList: List<FileInfo>
)

@Serializable
data class FileInfo(
    val FileId: Long,
    val FileName: String,
    val Type: Int, // 1 是文件夹, 0 是文件
    val Size: Long,
    val UpdateAt: String,
    val DownloadUrl: String? = null,
    val Category: Int
)

    // 将 ApiService 提取出来，方便 Repository 调用
    interface ApiService {
        suspend fun getLatestRelease(url: String): Result<UpdateInfo>
        suspend fun getFileList(token: String, page: Int): Result<PanResponse<FileListData>>
    }

    object ApiServiceImpl : ApiService {
        private const val FILE_LIST_PATH = "api/file/list/new"

        override suspend fun getLatestRelease(url: String): Result<UpdateInfo> {
            return safeApiCall { httpClient.get(url) }
        }

        override suspend fun getFileList(token: String, page: Int): Result<PanResponse<FileListData>> {
            return safeApiCall {
                httpClient.get(FILE_LIST_PATH) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    url {
                        parameters.append("driveId", "0")
                        parameters.append("limit", "100")
                        parameters.append("page", page.toString())
                        parameters.append("orderBy", "update_at")
                        parameters.append("orderDirection", "desc")
                        parameters.append("parentFileId", "0")
                        parameters.append("trashed", "false")
                        parameters.append("SearchData", "")
                        parameters.append("OnlyLookAbnormalFile", "0")
                    }
                }
            }
        }
    }
    
    /**
 * 安全地执行 Ktor 请求，并处理异常和重试
 */
 @Suppress("RedundantSuspendModifier")
private suspend inline fun <reified T> safeApiCall(block: suspend () -> HttpResponse): Result<T> {
    var attempts = 0
    while (attempts < MAX_RETRIES) {
        try {
            val response = block()
            if (!response.status.isSuccess()) {
                println("Request failed with status: ${response.status}")
                throw IOException("Request failed with status: ${response.status}")
            }
            val responseBody: T = try {
                response.body()
            } catch (e: Exception) {
                println("Failed to deserialize response body: ${e.message}")
                throw e
            }
            return Result.success(responseBody)
        } catch (e: IOException) {
            attempts++
            println("Request failed, retrying in $RETRY_DELAY ms... (Attempt $attempts/$MAX_RETRIES)")
            delay(RETRY_DELAY)
        } catch (e: Exception) {
            println("Request failed: ${e.message}")
            return Result.failure(e)
        }
    }
    println("Request failed after $MAX_RETRIES attempts.")
    return Result.failure(IOException("Request failed after $MAX_RETRIES attempts."))
}

    /**
     * 发起 Ktor 请求
     */
    private suspend inline fun <reified T> request(
        url: String,
        method: HttpMethod = HttpMethod.Post,
        parameters: Parameters = Parameters.Empty
    ): Result<T> {
        return safeApiCall {
            httpClient.request(url) {
                this.method = method
                this.setBody(FormDataContent(parameters))
            }
        }
    }
}