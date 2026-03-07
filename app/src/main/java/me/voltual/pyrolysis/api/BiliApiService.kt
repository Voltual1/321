//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package me.voltual.pyrolysis.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

object BiliApiManager {
    private const val BILI_API_BASE_URL = "https://api.bilibili.com/"
    private const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // Ktor HttpClient for Bilibili API
    private val httpClient = HttpClient(OkHttp) {
        defaultRequest {
            url(BILI_API_BASE_URL)
            header(HttpHeaders.UserAgent, USER_AGENT_WEB)
            header(HttpHeaders.Referrer, "https://www.bilibili.com/")
            header(HttpHeaders.Accept, "application/json")
        }

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30000L
            connectTimeoutMillis = 30000L
            socketTimeoutMillis = 30000L
        }
    }

    val instance: BiliApiService = BiliApiServiceImpl()

    // 模型类使用 kotlinx.serialization
    object models {
        // 视频信息响应 (对应 /x/web-interface/view)
        @Serializable
        data class BiliVideoInfoResponse(
            val code: Int,
            val message: String,
            val data: VideoData? = null
        )

        @Serializable
        data class VideoData(
            val bvid: String,
            val aid: Long,
            val title: String,
            val pic: String,
            val duration: Int,
            val owner: Owner,
            val pages: List<VideoPage>
        )

        @Serializable
        data class Owner(
            val mid: Long,
            val name: String,
            val face: String
        )

        @Serializable
        data class VideoPage(
            val cid: Long,
            val part: String,
            val duration: Int
        )

        // 视频播放地址响应 (对应 /x/player/playurl)
        @Serializable
        data class BiliPlayUrlResponse(
            val code: Int,
            val message: String,
            val data: PlayUrlData? = null
        )

        @Serializable
        data class PlayUrlData(
            val durl: List<DashUrl>? = null
        )

        @Serializable
        data class DashUrl(
            val url: String,
            val size: Long,
            val backup_url: List<String>? = null
        )
    }

    interface BiliApiService {
        // 获取视频详细信息（最重要的是cid）
        suspend fun getVideoInfo(bvid: String): Result<models.BiliVideoInfoResponse>

        // 获取视频播放地址
        suspend fun getPlayUrl(
            bvid: String,
            cid: Long,
            quality: Int = 80, // 80=1080P, 64=720P, 32=480P, 16=360P
            fnval: Int = 1 // 1=flv, 16=DASH
        ): Result<models.BiliPlayUrlResponse>

        // 获取弹幕，返回原始字节数据
        suspend fun getDanmaku(cid: Long): Result<ByteArray>
    }

    private class BiliApiServiceImpl : BiliApiService {
        companion object {
            const val MAX_RETRIES = 3
            const val RETRY_DELAY = 1000L
        }

        /**
         * 安全地执行 Ktor 请求，并处理异常和重试
         */
          @Suppress("RedundantSuspendModifier")
        private suspend inline fun <reified T> safeApiCall(block: suspend () -> T): Result<T> {
            var attempts = 0
            while (attempts < MAX_RETRIES) {
                try {
                    val result = block()
                    return Result.success(result)
                } catch (e: IOException) {
                    attempts++
                    if (attempts >= MAX_RETRIES) {
                        return Result.failure(IOException("Request failed after $MAX_RETRIES attempts: ${e.message}"))
                    }
                    kotlinx.coroutines.delay(RETRY_DELAY)
                } catch (e: Exception) {
                    return Result.failure(e)
                }
            }
            return Result.failure(IOException("Request failed after $MAX_RETRIES attempts"))
        }

        override suspend fun getVideoInfo(bvid: String): Result<models.BiliVideoInfoResponse> {
            return safeApiCall {
                httpClient.get("x/web-interface/view") {
                    parameter("bvid", bvid)
                }.body()
            }
        }

        override suspend fun getPlayUrl(
            bvid: String,
            cid: Long,
            quality: Int,
            fnval: Int
        ): Result<models.BiliPlayUrlResponse> {
            return safeApiCall {
                httpClient.get("x/player/playurl") {
                    parameter("bvid", bvid)
                    parameter("cid", cid)
                    parameter("qn", quality)
                    parameter("fnval", fnval)
                }.body()
            }
        }

        override suspend fun getDanmaku(cid: Long): Result<ByteArray> {
            return safeApiCall {
                // 弹幕接口返回的是 XML 格式的压缩数据，需要解压
                httpClient.get("x/v1/dm/list.so") {
                    parameter("oid", cid)
                }.body<ByteArray>()
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
