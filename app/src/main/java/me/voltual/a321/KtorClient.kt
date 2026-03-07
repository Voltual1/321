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
    private const val BASE_URL = "https://example.com/"
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY = 1000L
    private const val REQUEST_TIMEOUT = 30000L
    private const val CONNECT_TIMEOUT = 30000L
    private const val SOCKET_TIMEOUT = 30000L

    // Ktor HttpClient 实例
val httpClient = HttpClient(OkHttp) {
    initConfig(this)
    defaultRequest {
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }
}

    private fun initConfig(client: HttpClientConfig<OkHttpConfig>) {
    // 默认请求配置
    client.defaultRequest {
        url(BASE_URL)
        header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
        header(HttpHeaders.Accept, ContentType.Application.Json.toString()) // 显式设置 Accept 头部
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

    interface ApiService {
    
    suspend fun getLatestRelease(): Result<UpdateInfo>
        
    }

    object ApiServiceImpl : ApiService {
        private const val GET_LATEST_RELEASE_URL = "https://gitee.com/api/v5/repos/Voltula/bbq/releases/latest"
        
          override suspend fun getLatestRelease(): Result<UpdateInfo> {
    return safeApiCall {
        httpClient.get(GET_LATEST_RELEASE_URL).body()
    }
    }        
        
    
        

    }

    /**
     * 关闭 HttpClient（在应用退出时调用）
     */
    fun close() {
        httpClient.close()
    }
}