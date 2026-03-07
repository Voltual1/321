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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.voltual.pyrolysis.data.UpdateInfo
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
    private const val BASE_URL = "http://apk.xiaoqu.online/"
    private const val UPLOAD_BASE_URL = "https://file.bz6.top/"
    private const val WANYUEYUN_UPLOAD_BASE_URL = "http://wanyueyun-x.xbjstd.cn:9812/"
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
    // 上传专用客户端
    val uploadHttpClient = HttpClient(OkHttp) {
        defaultRequest {
            url(UPLOAD_BASE_URL)
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
        }
    }

    // 挽悦云上传客户端
    val wanyueyunUploadHttpClient = HttpClient(OkHttp) {
        defaultRequest {
            url(WANYUEYUN_UPLOAD_BASE_URL)
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
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

    // ===== 模型类定义 =====

    // 基础响应模型
    @kotlinx.serialization.Serializable
    data class BaseResponse(
        val code: Int,
        val msg: String,
        val data: JsonElement? = null, // 改为 JsonElement? 来支持任何 JSON 类型
        val timestamp: Long
    ) {
        // 辅助方法：从 data 字段获取下载链接
    fun getDownloadUrl(): String? {
        return if (data is JsonObject) {
            data["download"]?.toString()?.removeSurrounding("\"")
        } else {
            null
        }
    }
}

    // 帖子详情响应模型
    @kotlinx.serialization.Serializable
    data class PostDetailResponse(
        val code: Int,
        val msg: String,
        val data: PostDetail,
        val timestamp: Long
    )

    @kotlinx.serialization.Serializable
    data class PostDetail(
        val id: Long,
        val title: String,
        val content: String,
        val userid: Long,
        val create_time: String,
        val update_time: String,
        val username: String,
        val nickname: String,
        val usertx: String,
        val hierarchy: String,
        val section_name: String,
        val sub_section_name: String,
        val view: String,
        val thumbs: String,
        val comment: String,
        val img_url: List<String>? = null,
        val video_url: String? = null,
        val ip_address: String,
        val create_time_ago: String,
        val is_thumbs: Int,
        val is_collection: Int
    )

    // 评论列表响应模型
    @kotlinx.serialization.Serializable
    data class CommentListResponse(
        val code: Int,
        val msg: String,
        val data: CommentListData,
        val timestamp: Long
    )

    @kotlinx.serialization.Serializable
    data class CommentListData(
        val list: List<Comment>,
        val pagecount: Int,
        val current_number: Int
    )

    @kotlinx.serialization.Serializable
    data class Comment(
        val id: Long,
        val content: String,
        val userid: Long,
        val time: String,
        val username: String,
        val nickname: String,
        val usertx: String,
        val hierarchy: String,
        val parentid: Long? = null,
        val parentnickname: String? = null,
        val parentcontent: String? = null,
        val image_path: List<String>? = null,
        val sub_comments_count: Int
    )

    // 帖子列表响应模型
    @kotlinx.serialization.Serializable
    data class PostListResponse(
        val code: Int,
        val msg: String,
        val data: PostListData,
        val timestamp: Long
    )

    @kotlinx.serialization.Serializable
    data class PostListData(
        val list: List<Post>,
        val pagecount: Int,
        val current_number: Int
    )

    @kotlinx.serialization.Serializable
    data class Post(
        val postid: Long,
        val title: String,
        val content: String,
        val userid: Long,
        val create_time: String,
        val update_time: String,
        val username: String,
        val nickname: String,
        val usertx: String,
        val hierarchy: String,
        val section_name: String,
        val sub_section_name: String,
        val view: String,
        val thumbs: String,
        val comment: String,
        val img_url: List<String>? = null
    )

    @kotlinx.serialization.Serializable
    data class LoginResponse(
        val code: Int,
        val msg: String,
        val data: LoginData? = null,
        val timestamp: Long
    )

    @kotlinx.serialization.Serializable
    data class LoginData(
        val usertoken: String,
        val id: Long,
        val username: String,
        val is_section_moderator: Int = 0
    )

    @kotlinx.serialization.Serializable
    data class HeartbeatResponse(
        val code: Int,
        val msg: String,
        val timestamp: Long
    )

    @kotlinx.serialization.Serializable
    data class UserInfoResponse(
        val code: Int,
        val msg: String,
        val data: UserData,
        val timestamp: Long
    )

@kotlinx.serialization.Serializable
data class UserData(
    val id: Long,
    val username: String,
    val usertx: String,
    val nickname: String,
    val hierarchy: String,
    val money: Int,
    val followerscount: String,
    val fanscount: String,
    val postcount: String,
    val likecount: String,
    val exp: Int,
    val create_time: String = "",
    val signlasttime: String = "",
    val series_days: Int = 0,
    val sign_today: Boolean = false  
)

    // 应用列表模型
    @kotlinx.serialization.Serializable
    data class AppListResponse(
        val code: Int,
        val msg: String,
        val data: AppListData,
        val timestamp: Long
    )

    @kotlinx.serialization.Serializable
    data class AppListData(
        val list: List<AppItem>,
        val pagecount: Int,
        val current_number: Int
    )

    @kotlinx.serialization.Serializable
    data class AppItem(
        val id: Long,
        val appname: String,
        val app_icon: String,
        val app_size: String,
        val download_count: Int,
        val create_time: String,
        val nickname: String,
        val apps_version_id: Long
    )

    // 应用详情响应模型
    @kotlinx.serialization.Serializable
    data class AppDetailResponse(
        val code: Int,
        val msg: String,
        val data: AppDetail,
        val timestamp: Long
    )

    // 消息通知响应
    @kotlinx.serialization.Serializable
    data class MessageNotificationResponse(
        val code: Int,
        val msg: String,
        val data: MessageNotificationData,
        val timestamp: Long
    )

    @kotlinx.serialization.Serializable
    data class MessageNotificationData(
        val list: List<MessageNotification>,
        val pagecount: Int,
        val current_number: Int
    )

    @kotlinx.serialization.Serializable
    data class MessageNotification(
        val id: Long,
        val title: String,
        val content: String,
        val send_to: Long,
        val appid: Int,
        val type: Int,
        val time: String,
        val postid: Long?,
        val pic_url: String?,
        val user_id: Long,
        val status: Int,
        val is_admin: Int
    )

    @kotlinx.serialization.Serializable
    data class AppDetail(
        val id: Long,
        val appname: String,
        val app_icon: String,
        val app_size: String,
        val app_explain: String?,
        val app_introduce: String?,
        val app_introduction_image: String?,
        val is_pay: Int,
        val pay_money: Int,
        val download: String?,
        val create_time: String,
        val update_time: String,
        val userid: Long,
        val appid: Int,
        val category_id: Int,
        val sub_category_id: Int,
        val category_name: String,
        val category_icon: String,
        val sub_category_name: String,
        val username: String,
        val nickname: String,
        val usertx: String,
        val sex: Int,
        val signature: String,
        val exp: Int,
        val version: String,
        val apps_version_id: Long,
        val version_create_time: String,
        val app_introduction_image_array: List<String>?,
        val ip_address: String,
        val sexName: String,
        val badge: List<JsonObject>,
        val vip: Boolean,
        val hierarchy: String,
        val is_user_pay: Boolean,
        val download_count: Int,
        val comment_count: Int,
        val user_pay_count: Int,
        val reward_count: Int,
        val posturl: String?
    )

    @kotlinx.serialization.Serializable
    data class BrowseHistoryResponse(
        val code: Int,
        val msg: String,
        val data: BrowseHistoryData,
        val timestamp: Long
    )

    @kotlinx.serialization.Serializable
    data class BrowseHistoryData(
        val list: List<BrowseHistoryItem>,
        val pagecount: Int,
        val current_number: Int
    )

    @kotlinx.serialization.Serializable
    data class BrowseHistoryItem(
        val postid: Long,
        val title: String,
        val content: String,
        val userid: Long,
        val create_time: String,
        val update_time: String,
        val username: String,
        val nickname: String,
        val usertx: String,
        val hierarchy: String,
        val section_name: String,
        val sub_section_name: String,
        val view: String,
        val thumbs: String,
        val comment: String,
        val img_url: List<String>? = null
    )

    // 应用评论列表响应模型
    @kotlinx.serialization.Serializable
    data class AppCommentListResponse(
        val code: Int,
        val msg: String,
        val data: AppCommentListData,
        val timestamp: Long
    )

    @kotlinx.serialization.Serializable
    data class AppCommentListData(
        val list: List<AppComment>,
        val pagecount: Int,
        val current_number: Int
    )

    @kotlinx.serialization.Serializable
    data class AppComment(
        val id: Long,
        val content: String,
        val userid: Long,
        val time: String,
        val username: String,
        val nickname: String,
        val usertx: String,
        val hierarchy: String,
        val parentid: Long? = null,
        val parentnickname: String? = null,
        val parentcontent: String? = null,
        val image_path: List<String>? = null
    )

    // 用户列表响应模型
    @kotlinx.serialization.Serializable
    data class UserListResponse(
        val code: Int,
        val msg: String,
        val data: UserListData,
        val timestamp: Long
    )

    @kotlinx.serialization.Serializable
    data class UserListData(
        val list: List<UserItem>,
        val pagecount: Int,
        val current_number: Int
    )

    @kotlinx.serialization.Serializable
    data class UserItem(
        val id: Long,
        val username: String,
        val nickname: String,
        val usertx: String,
        val sex: Int,
        val signature: String,
        val title: List<String>,
        val badge: List<JsonObject>,
        val vip: Boolean,
        val hierarchy: String,
        val status: Int,
        val sexName: String
    )

    @kotlinx.serialization.Serializable
    data class UserInformationResponse(
        val code: Int,
        val msg: String,
        val data: UserInformationData,
        val timestamp: Long
    )

    @kotlinx.serialization.Serializable
    data class UserInformationData(
        val id: Long,
        val follow_status: String,
        val username: String,
        val usertx: String,
        val nickname: String,
        val money: Int,
        val exp: Int,
        val followerscount: String,
        val fanscount: String,
        val postcount: String,
        val likecount: String,
        val hierarchy: String,
        val last_activity_time: String?,
        val series_days: Int?,
        val commentcount: String?
    )

    @kotlinx.serialization.Serializable
    data class BillingResponse(
        val code: Int,
        val msg: String,
        val data: BillingData,
        val timestamp: Long
    )

    @kotlinx.serialization.Serializable
    data class BillingData(
        val list: List<BillingItem>,
        val pagecount: Int,
        val current_number: Int
    )

    @kotlinx.serialization.Serializable
    data class BillingItem(
        val id: Long,
        val transaction_type: Int,
        val transaction_date: String,
        val transaction_amount: String,
        val remark: String,
        val type: Int
    )
    

    @kotlinx.serialization.Serializable
    data class RankingUser(
        val id: Long,
        val username: String,
        val nickname: String,
        val usertx: String,
    val money: Int? = null,  // 改为可选
    val exp: Int? = null,    // 改为可选
        val title: List<String>?,
        val badge: List<JsonObject>?
    )

    @kotlinx.serialization.Serializable
    data class RankingListResponse(
        val code: Int,
        val msg: String,
        val data: List<RankingUser>,
        val timestamp: Long
    )
    
    @kotlinx.serialization.Serializable
data class AppCategory(
    val categoryId: Int?,
    val subCategoryId: Int?,
    val categoryName: String
)

    // 上传响应模型
    @kotlinx.serialization.Serializable
    data class UploadResponse(
        val code: Int,
        val msg: String,
        val exists: Int? = null,
        val downurl: String?,
        val viewurl: String?
    )
    
    @kotlinx.serialization.Serializable
data class WanyueyunUploadResponse(
    val code: Int,
    val msg: String,
    val data: String?
)
    
    // **NEW**: Helper object to handle JSON conversion
    object JsonConverter {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        fun toJson(appDetail: AppDetail): String {
            return json.encodeToString(AppDetail.serializer(), appDetail)
        }

        fun fromJson(jsonString: String): AppDetail? {
            return try {
                json.decodeFromString(AppDetail.serializer(), jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                null
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

    interface ApiService {
        suspend fun login(
             appid: Int = 1,
             username: String,
             password: String,
             device: String
        ): Result<LoginResponse>

        suspend fun heartbeat(
             appid: Int = 1,
             token: String
        ): Result<HeartbeatResponse>

        suspend fun getPostsList(
             appid: Int = 1,
             limit: Int,
             page: Int,
             sort: String = "create_time",
             sortOrder: String = "desc",
             sectionId: Int? = null,
             userId: Long? = null
        ): Result<PostListResponse>

        suspend fun getUserInfo(
             appid: Int = 1,
             token: String
        ): Result<UserInfoResponse>

        suspend fun getPostDetail(
             appid: Int = 1,
             token: String,
             postId: Long
        ): Result<PostDetailResponse>

        suspend fun getPostComments(
             appid: Int = 1,
             postId: Long,
             limit: Int,
             page: Int,
             sort: String = "time",
             sortOrder: String = "desc"
        ): Result<CommentListResponse>

        suspend fun createPost(
             appid: Int = 1,
             token: String,
             title: String,
             content: String,
             sectionId: Int,
             imageUrls: String? = null,
             paidReading: Int = 0,
             downloadMethod: Int = 0
        ): Result<BaseResponse>

        suspend fun getAppsList(
             appid: Int = 1,
             limit: Int,
             page: Int,
             sort: String = "update_time",
             sortOrder: String = "desc",
             categoryId: Int? = null,
             subCategoryId: Int? = null,
             appName: String? = null,
             userId: Long? = null
        ): Result<AppListResponse>

        suspend fun getAppsInformation(
             appid: Int = 1,
             token: String,
             appsId: Long,
             appsVersionId: Long
        ): Result<AppDetailResponse>

        suspend fun getAppsCommentList(
             appid: Int = 1,
             appsId: Long,
             appsVersionId: Long,
             limit: Int,
             page: Int,
             sortOrder: String = "asc"
        ): Result<AppCommentListResponse>

        suspend fun postComment(
             appid: Int = 1,
             token: String,
             content: String,
             postId: Long? = null,
             parentId: Long? = null,
             imageUrl: String? = null
        ): Result<BaseResponse>

        suspend fun getMessageNotifications(
             appid: Int = 1,
             token: String,
             limit: Int,
             page: Int
        ): Result<MessageNotificationResponse>

        suspend fun getBrowseHistory(
             appid: Int = 1,
             token: String,
             limit: Int = 10,
             page: Int
        ): Result<BrowseHistoryResponse>

        suspend fun getLikesRecords(
             appid: Int = 1,
             token: String,
             limit: Int = 10,
             page: Int
        ): Result<PostListResponse>

        suspend fun searchPosts(
             appid: Int = 1,
             query: String,
             limit: Int = 10,
             page: Int,
             userId: Long? = null  // 新增可选的 userId 参数
        ): Result<PostListResponse>

        suspend fun getHotPostsList(
             appid: Int = 1,
             limit: Int,
             page: Int,
             sortOrder: String = "desc",
             sort: String = "score"
        ): Result<PostListResponse>

        suspend fun getMyFollowingPosts(
             appid: Int = 1,
             token: String,
             limit: Int,
             page: Int
        ): Result<PostListResponse>

        suspend fun likePost(
             appid: Int = 1,
             token: String,
             postId: Long
        ): Result<BaseResponse>

        suspend fun deletePost(
             appid: Int = 1,
             token: String,
             postId: Long
        ): Result<BaseResponse>

        suspend fun getFanList(
             appid: Int = 1,
             token: String,
             limit: Int = 10,
             page: Int
        ): Result<UserListResponse>

        suspend fun getFollowList(
             appid: Int = 1,
             token: String,
             limit: Int = 10,
             page: Int
        ): Result<UserListResponse>

        suspend fun getUserInformation(
             appid: Int = 1,
             userId: Long,
             token: String
        ): Result<UserInformationResponse>

        suspend fun deleteComment(
             appid: Int = 1,
             token: String,
             commentId: Long
        ): Result<BaseResponse>

        suspend fun getUserBilling(
             appid: Int = 1,
             token: String,
             limit: Int,
             page: Int
        ): Result<BillingResponse>

        suspend fun payForApp(
             appid: Int = 1,
             token: String,
             appsId: Long,
             appsVersionId: Long,
             money: Int,
             type: Int = 0
        ): Result<BaseResponse>

        suspend fun rewardPost(
             appid: Int = 1,
             token: String,
             postId: Long,
             money: Int,
             payment: Int = 0
        ): Result<BaseResponse>

        suspend fun postAppComment(
             appid: Int = 1,
             token: String,
             content: String,
             appsId: Long,
             appsVersionId: Long,
             parentId: Long? = null,
             imageUrl: String? = null
        ): Result<BaseResponse>

        suspend fun deleteAppComment(
             appid: Int = 1,
             token: String,
             commentId: Long
        ): Result<BaseResponse>

        suspend fun userSignIn(
             appid: Int = 1,
             token: String
        ): Result<BaseResponse>

        suspend fun register(
             appid: Int = 1,
             username: String,
             password: String,
             email: String,
             device: String,
             captcha: String
        ): Result<BaseResponse>

        suspend fun modifyUserInfo(
             appid: Int = 1,
             token: String,
             nickname: String? = null,
             qq: String? = null
        ): Result<BaseResponse>

        suspend fun releaseApp(
             appid: Int = 1,
             usertoken: String,
             appname: String,
             icon: String?,
             app_size: String,
             app_introduce: String,
             app_introduction_image: String?,
             file: String,
             app_explain: String?,
             app_version: String,
             is_pay: Int,
             pay_money: String?,
             category_id: Int,
             sub_category_id: Int
        ): Result<BaseResponse>

        suspend fun updateApp(
             appid: Int = 1,
             usertoken: String,
             apps_id: Long,
             appname: String,
             icon: String?,
             app_size: String,
             app_introduce: String,
             app_introduction_image: String,
             file: String,
             app_explain: String?,
             app_version: String,
             is_pay: Int,
             pay_money: String?,
             category_id: Int,
             sub_category_id: Int
        ): Result<BaseResponse>

        suspend fun deleteApp(
             appid: Int = 1,
             usertoken: String,
             apps_id: Long,
             app_version_id: Long
        ): Result<BaseResponse>

        suspend fun getRankingList(
             appid: Int = 1,
             sort: String = "money",
             sortOrder: String = "desc",
             limit: Int = 15,
             page: Int
        ): Result<RankingListResponse>

        suspend fun followUser(
             appid: Int = 1,
             token: String,
             followedId: Long
        ): Result<BaseResponse>

        suspend fun uploadAvatar(
             appid: Int,
             token: String,
             file: ByteArray,
             filename: String
        ): Result<BaseResponse>
        
        suspend fun getLatestRelease(): Result<UpdateInfo>

        //suspend fun getImageVerificationCode(
        //    appid: Int = 1,
        //    type: Int = 2
        //): Result<ResponseBody>
    }

    object ApiServiceImpl : ApiService {
        private const val LOGIN_URL = "api/login"
        private const val HEARTBEAT_URL = "api/user_heartbeat"
        private const val GET_POSTS_LIST_URL = "api/get_posts_list"
        private const val GET_USER_INFO_URL = "api/get_user_other_information"
        private const val GET_POST_DETAIL_URL = "api/get_post_information"
        private const val GET_POST_COMMENTS_URL = "api/get_list_comments"
        private const val CREATE_POST_URL = "api/post"
        private const val GET_APPS_LIST_URL = "api/get_apps_list"
        private const val GET_APPS_INFORMATION_URL = "api/get_apps_information"
        private const val GET_APPS_COMMENT_LIST_URL = "api/get_apps_comment_list"
        private const val POST_COMMENT_URL = "api/post_comment"
        private const val GET_MESSAGE_NOTIFICATIONS_URL = "api/get_message_notifications"
        private const val GET_BROWSE_HISTORY_URL = "api/browse_history"
        private const val GET_LIKES_RECORDS_URL = "api/get_likes_records"
        private const val SEARCH_POSTS_URL = "api/get_posts_list" // Use get_posts_list for search
        private const val GET_HOT_POSTS_LIST_URL = "api/get_posts_list" // Use get_posts_list, sort by score
        private const val GET_MY_FOLLOWING_POSTS_URL = "api/get_my_following_posts"
        private const val LIKE_POST_URL = "api/like_posts"
        private const val DELETE_POST_URL = "api/delete_post"
        private const val GET_FAN_LIST_URL = "api/get_fan_list"
        private const val GET_FOLLOW_LIST_URL = "api/get_follow_list"
        private const val GET_USER_INFORMATION_URL = "api/get_user_information"
        private const val DELETE_COMMENT_URL = "api/delete_comment"
        private const val GET_USER_BILLING_URL = "api/get_user_billing"
        private const val PAY_FOR_APP_URL = "api/pay_for_apps"
        private const val REWARD_POST_URL = "api/reward_posts"
        private const val POST_APP_COMMENT_URL = "api/apps_add_comment"
        private const val DELETE_APP_COMMENT_URL = "api/delete_apps_comment"
        private const val USER_SIGN_IN_URL = "api/user_sign_in"
        private const val REGISTER_URL = "api/register"
        private const val MODIFY_USER_INFO_URL = "api/modify_user_information"
        private const val RELEASE_APPS_URL = "api/release_apps"
        private const val RELEASE_NEW_VERSION_URL = "api/release_new_version"
        private const val DELETE_APPS_URL = "api/delete_apps"
        private const val RANKING_LIST_URL = "api/ranking_list"
        private const val FOLLOW_USERS_URL = "api/follow_users"
        private const val UPLOAD_AVATAR_URL = "api/upload_avatar"
        //private const val GET_IMAGE_VERIFICATION_CODE_URL = "api/get_image_verification_code"
        private const val GET_LATEST_RELEASE_URL = "https://gitee.com/api/v5/repos/Voltula/bbq/releases/latest"

        override suspend fun login(
            appid: Int,
            username: String,
            password: String,
            device: String
        ): Result<LoginResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("username", username)
                append("password", password)
                append("device", device)
            }
            return request(LOGIN_URL, parameters = parameters)
        }

        override suspend fun heartbeat(
            appid: Int,
            token: String
        ): Result<HeartbeatResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
            }
            return request(HEARTBEAT_URL, parameters = parameters)
        }

        override suspend fun getPostsList(
            appid: Int,
            limit: Int,
            page: Int,
            sort: String,
            sortOrder: String,
            sectionId: Int?,
            userId: Long?
        ): Result<PostListResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("limit", limit.toString())
                append("page", page.toString())
                append("sort", sort)
                append("sortOrder", sortOrder)
                sectionId?.let { append("sectionid", it.toString()) }
                userId?.let { append("userid", it.toString()) }
            }
            return request(GET_POSTS_LIST_URL, parameters = parameters)
        }

        override suspend fun getUserInfo(
            appid: Int,
            token: String
        ): Result<UserInfoResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
            }
            return request(GET_USER_INFO_URL, parameters = parameters)
        }

        override suspend fun getPostDetail(
            appid: Int,
            token: String,
            postId: Long
        ): Result<PostDetailResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("postid", postId.toString())
            }
            return request(GET_POST_DETAIL_URL, parameters = parameters)
        }

        override suspend fun getPostComments(
            appid: Int,
            postId: Long,
            limit: Int,
            page: Int,
            sort: String,
            sortOrder: String
        ): Result<CommentListResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("postid", postId.toString())
                append("limit", limit.toString())
                append("page", page.toString())
                append("sort", sort)
                append("sortOrder", sortOrder)
            }
            return request(GET_POST_COMMENTS_URL, parameters = parameters)
        }

        override suspend fun createPost(
            appid: Int,
            token: String,
            title: String,
            content: String,
            sectionId: Int,
            imageUrls: String?,
            paidReading: Int,
            downloadMethod: Int
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                                append("usertoken", token)
                append("title", title)
                append("content", content)
                append("subsectionid", sectionId.toString())
                imageUrls?.let { append("network_picture", it) }
                append("paid_reading", paidReading.toString())
                append("file_download_method", downloadMethod.toString())
            }
            return request(CREATE_POST_URL, parameters = parameters)
        }

        override suspend fun getAppsList(
            appid: Int,
            limit: Int,
            page: Int,
            sort: String,
            sortOrder: String,
            categoryId: Int?,
            subCategoryId: Int?,
            appName: String?,
            userId: Long?
        ): Result<AppListResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("limit", limit.toString())
                append("page", page.toString())
                append("sort", sort)
                append("sortOrder", sortOrder)
                categoryId?.let { append("category_id", it.toString()) }
                subCategoryId?.let { append("sub_category_id", it.toString()) }
                appName?.let { append("appname", it) }
                userId?.let { append("userid", it.toString()) }
            }
            return request(GET_APPS_LIST_URL, parameters = parameters)
        }

        override suspend fun getAppsInformation(
            appid: Int,
            token: String,
            appsId: Long,
            appsVersionId: Long
        ): Result<AppDetailResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("apps_id", appsId.toString())
                append("apps_version_id", appsVersionId.toString())
            }
            return request(GET_APPS_INFORMATION_URL, parameters = parameters)
        }

        override suspend fun getAppsCommentList(
            appid: Int,
            appsId: Long,
            appsVersionId: Long,
            limit: Int,
            page: Int,
            sortOrder: String
        ): Result<AppCommentListResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("apps_id", appsId.toString())
                append("apps_version_id", appsVersionId.toString())
                append("limit", limit.toString())
                append("page", page.toString())
                append("sortOrder", sortOrder)
            }
            return request(GET_APPS_COMMENT_LIST_URL, parameters = parameters)
        }

        override suspend fun postComment(
            appid: Int,
            token: String,
            content: String,
            postId: Long?,
            parentId: Long?,
            imageUrl: String?
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("content", content)
                postId?.let { append("postid", it.toString()) }
                parentId?.let { append("parentid", it.toString()) }
                imageUrl?.let { append("img", it) }
            }
            return request(POST_COMMENT_URL, parameters = parameters)
        }

        override suspend fun getMessageNotifications(
            appid: Int,
            token: String,
            limit: Int,
            page: Int
        ): Result<MessageNotificationResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("limit", limit.toString())
                append("page", page.toString())
            }
            return request(GET_MESSAGE_NOTIFICATIONS_URL, parameters = parameters)
        }

        override suspend fun getBrowseHistory(
            appid: Int,
            token: String,
            limit: Int,
            page: Int
        ): Result<BrowseHistoryResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("limit", limit.toString())
                append("page", page.toString())
            }
            return request(GET_BROWSE_HISTORY_URL, parameters = parameters)
        }

        override suspend fun getLikesRecords(
            appid: Int,
            token: String,
            limit: Int,
            page: Int
        ): Result<PostListResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("limit", limit.toString())
                append("page", page.toString())
            }
            return request(GET_LIKES_RECORDS_URL, parameters = parameters)
        }

        override suspend fun searchPosts(
    appid: Int,
    query: String,
    limit: Int,
    page: Int,
    userId: Long?
): Result<PostListResponse> {
    val parameters = Parameters.build {
        append("appid", appid.toString())
        append("keyword", query)
        append("limit", limit.toString())
        append("page", page.toString())
        userId?.let { append("userid", it.toString()) }  // 只在有 userId 时添加
    }
    return request(SEARCH_POSTS_URL, parameters = parameters)
}

        override suspend fun getHotPostsList(
            appid: Int,
            limit: Int,
            page: Int,
            sortOrder: String,
            sort: String
        ): Result<PostListResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("limit", limit.toString())
                append("page", page.toString())
                append("sortOrder", sortOrder)
                append("sort", sort)
            }
            return request(GET_HOT_POSTS_LIST_URL, parameters = parameters)
        }

        override suspend fun getMyFollowingPosts(
            appid: Int,
            token: String,
            limit: Int,
            page: Int
        ): Result<PostListResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("limit", limit.toString())
                append("page", page.toString())
            }
            return request(GET_MY_FOLLOWING_POSTS_URL, parameters = parameters)
        }

        override suspend fun likePost(
            appid: Int,
            token: String,
            postId: Long
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("postid", postId.toString())
            }
            return request(LIKE_POST_URL, parameters = parameters)
        }

        override suspend fun deletePost(
            appid: Int,
            token: String,
            postId: Long
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("postid", postId.toString())
            }
            return request(DELETE_POST_URL, parameters = parameters)
        }

        override suspend fun getFanList(
            appid: Int,
            token: String,
            limit: Int,
            page: Int
        ): Result<UserListResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("limit", limit.toString())
                append("page", page.toString())
            }
            return request(GET_FAN_LIST_URL, parameters = parameters)
        }

        override suspend fun getFollowList(
            appid: Int,
            token: String,
            limit: Int,
            page: Int
        ): Result<UserListResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("limit", limit.toString())
                append("page", page.toString())
            }
            return request(GET_FOLLOW_LIST_URL, parameters = parameters)
        }

        override suspend fun getUserInformation(
            appid: Int,
            userId: Long,
            token: String
        ): Result<UserInformationResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("userid", userId.toString())
                append("usertoken", token)
            }
            return request(GET_USER_INFORMATION_URL, parameters = parameters)
        }

        override suspend fun deleteComment(
            appid: Int,
            token: String,
            commentId: Long
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("commentid", commentId.toString())
            }
            return request(DELETE_COMMENT_URL, parameters = parameters)
        }

        override suspend fun getUserBilling(
            appid: Int,
            token: String,
            limit: Int,
            page: Int
        ): Result<BillingResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("limit", limit.toString())
                append("page", page.toString())
            }
            return request(GET_USER_BILLING_URL, parameters = parameters)
        }

        override suspend fun payForApp(
            appid: Int,
            token: String,
            appsId: Long,
            appsVersionId: Long,
            money: Int,
            type: Int
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("apps_id", appsId.toString())
                append("apps_version_id", appsVersionId.toString())
                append("money", money.toString())
                append("type", type.toString())
            }
            return request(PAY_FOR_APP_URL, parameters = parameters)
        }

        override suspend fun rewardPost(
            appid: Int,
            token: String,
            postId: Long,
            money: Int,
            payment: Int
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("postid", postId.toString())
                append("money", money.toString())
                append("payment", payment.toString())
            }
            return request(REWARD_POST_URL, parameters = parameters)
        }

        override suspend fun postAppComment(
            appid: Int,
            token: String,
            content: String,
            appsId: Long,
            appsVersionId: Long,
            parentId: Long?,
            imageUrl: String?
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("content", content)
                append("apps_id", appsId.toString())
                append("apps_version_id", appsVersionId.toString())
                parentId?.let { append("parentid", it.toString()) }
                imageUrl?.let { append("img", it) }
            }
            return request(POST_APP_COMMENT_URL, parameters = parameters)
        }

        override suspend fun deleteAppComment(
            appid: Int,
            token: String,
            commentId: Long
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("comment_id", commentId.toString())
            }
            return request(DELETE_APP_COMMENT_URL, parameters = parameters)
        }

        override suspend fun userSignIn(
            appid: Int,
            token: String
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
            }
            return request(USER_SIGN_IN_URL, parameters = parameters)
        }

        override suspend fun register(
            appid: Int,
            username: String,
            password: String,
            email: String,
            device: String,
            captcha: String
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("username", username)
                append("password", password)
                append("email", email)
                append("device", device)
                append("captcha", captcha)
            }
            return request(REGISTER_URL, parameters = parameters)
        }

        override suspend fun modifyUserInfo(
            appid: Int,
            token: String,
            nickname: String?,
            qq: String?
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                nickname?.let { append("nickname", it) }
                qq?.let { append("qq", it) }
            }
            return request(MODIFY_USER_INFO_URL, parameters = parameters)
        }

        override suspend fun releaseApp(
            appid: Int,
            usertoken: String,
            appname: String,
            icon: String?,
            app_size: String,
            app_introduce: String,
            app_introduction_image: String?,
            file: String,
            app_explain: String?,
            app_version: String,
            is_pay: Int,
            pay_money: String?,
            category_id: Int,
            sub_category_id: Int
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", usertoken)
                append("appname", appname)
                icon?.let { append("icon", it) }
                append("app_size", app_size)
                append("app_introduce", app_introduce)
                app_introduction_image?.let { append("app_introduction_image", it) }
                append("file", file)
                app_explain?.let { append("app_explain", it) }
                append("app_version", app_version)
                append("is_pay", is_pay.toString())
                pay_money?.let { append("pay_money", it) }
                append("category_id", category_id.toString())
                append("sub_category_id", sub_category_id.toString())
            }
            return request(RELEASE_APPS_URL, parameters = parameters)
        }

        override suspend fun updateApp(
            appid: Int,
            usertoken: String,
            apps_id: Long,
            appname: String,
            icon: String?,
            app_size: String,
            app_introduce: String,
            app_introduction_image: String,
            file: String,
            app_explain: String?,
            app_version: String,
            is_pay: Int,
            pay_money: String?,
            category_id: Int,
            sub_category_id: Int
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", usertoken)
                append("apps_id", apps_id.toString())
                append("appname", appname)
                icon?.let { append("icon", it) }
                append("app_size", app_size)
                append("app_introduce", app_introduce)
                append("app_introduction_image", app_introduction_image)
                append("file", file)
                app_explain?.let { append("app_explain", it) }
                append("app_version", app_version)
                append("is_pay", is_pay.toString())
                pay_money?.let { append("pay_money", it) }
                append("category_id", category_id.toString())
                append("sub_category_id", sub_category_id.toString())
            }
            return request(RELEASE_NEW_VERSION_URL, parameters = parameters)
        }


        override suspend fun deleteApp(
            appid: Int,
            usertoken: String,
            apps_id: Long,
            app_version_id: Long
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", usertoken)
                append("apps_id", apps_id.toString())
                append("app_version_id", app_version_id.toString())
            }
            return request(DELETE_APPS_URL, parameters = parameters)
        }

        override suspend fun getRankingList(
            appid: Int,
            sort: String,
            sortOrder: String,
            limit: Int,
            page: Int
        ): Result<RankingListResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("sort", sort)
                append("sortOrder", sortOrder)
                append("limit", limit.toString())
                append("page", page.toString())
            }
            return request(RANKING_LIST_URL, parameters = parameters)
        }
        
        override suspend fun getLatestRelease(): Result<UpdateInfo> {
    return safeApiCall {
        httpClient.get(GET_LATEST_RELEASE_URL).body()
    }
}

        override suspend fun followUser(
            appid: Int,
            token: String,
            followedId: Long
        ): Result<BaseResponse> {
            val parameters = Parameters.build {
                append("appid", appid.toString())
                append("usertoken", token)
                append("followedid", followedId.toString())
            }
            return request(FOLLOW_USERS_URL, parameters = parameters)
        }

override suspend fun uploadAvatar(
    appid: Int,
    token: String,
    file: ByteArray,
    filename: String
): Result<BaseResponse> {
    try {
        val response: HttpResponse = httpClient.post(UPLOAD_AVATAR_URL) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("appid", appid.toString())
                        append("usertoken", token)
                        append("file", file, Headers.build {
                            append(HttpHeaders.ContentType, "image/png")
                            append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                        })
                    }
                )
            )
        }

        // 检查状态码是否成功
        if (!response.status.isSuccess()) {
            return Result.failure(IOException("Request failed with status ${response.status.value}"))
        }

        // 使用 Ktor 的 body<T>() 函数解析 JSON 响应
        val baseResponse: BaseResponse = response.body()

        return Result.success(baseResponse)

    } catch (e: Exception) {
        return Result.failure(e)
    }
}
    }

    /**
     * 关闭 HttpClient（在应用退出时调用）
     */
    fun close() {
        httpClient.close()
        uploadHttpClient.close()
        wanyueyunUploadHttpClient.close()
    }
}
