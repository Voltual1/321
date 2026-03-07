//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.feature.store.repository

import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.BBQApplication
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.data.unified.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.flow.first
import me.voltual.pyrolysis.AppStore
import io.ktor.client.call.*
import java.io.File
import org.koin.core.annotation.Single

/**
 * 小趣空间数据仓库实现。
 */
 @Single
class XiaoQuRepository(private val apiClient: KtorClient.ApiService) : IAppStoreRepository {

    // 辅助方法：获取 Token
    private suspend fun getToken(): String {
        return AuthManager.getCredentials(BBQApplication.instance).first()?.token ?: ""
    }
    
    // 获取当前用户详情
    override suspend fun getCurrentUserDetail(): Result<UnifiedUserDetail> {
        return try {
            val token = getToken()
            if (token.isEmpty()) {
                return Result.failure(Exception("未登录"))
            }
            
            val result = apiClient.getUserInfo(token = token)
            result.map { response ->
                if (response.code == 1) {
                    UnifiedUserDetail(
                        id = response.data.id,
                        username = response.data.username,
                        displayName = response.data.nickname,
                        avatarUrl = response.data.usertx,
                        hierarchy = response.data.hierarchy,
                        money = response.data.money,
                        followersCount = response.data.followerscount,
                        fansCount = response.data.fanscount,
                        postCount = response.data.postcount,
                        likeCount = response.data.likecount,
                        store = AppStore.XIAOQU_SPACE,
                        raw = response.data
                    )
                } else {
                    throw Exception("获取用户信息失败: ${response.msg}")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // 更新用户资料
    override suspend fun updateUserProfile(params: UpdateUserProfileParams): Result<Unit> {
        return try {
            val token = getToken()
            if (token.isEmpty()) {
                return Result.failure(Exception("未登录"))
            }
            
            // 更新昵称
            if (!params.nickname.isNullOrEmpty()) {
                val nicknameResult = apiClient.modifyUserInfo(
                    token = token,
                    nickname = params.nickname,
                    qq = null
                )
                nicknameResult.getOrThrow().let { res ->
                    if (res.code != 1) throw Exception("昵称修改失败")
                }
            }
            
            // 更新QQ号
            if (!params.qqNumber.isNullOrEmpty()) {
                val qqResult = apiClient.modifyUserInfo(
                    token = token,
                    nickname = null,
                    qq = params.qqNumber
                )
                qqResult.getOrThrow().let { res ->
                    if (res.code != 1) throw Exception("QQ号修改失败")
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // 上传头像
    override suspend fun uploadAvatar(imageBytes: ByteArray, filename: String): Result<String> {
        return try {
            val token = getToken()
            if (token.isEmpty()) {
                return Result.failure(Exception("未登录"))
            }
            
            val result = apiClient.uploadAvatar(
                appid = 1,
                token = token,
                file = imageBytes,
                filename = filename
            )
            
            result.map { response ->
                if (response.code == 1) {
                    "上传成功"
                } else {
                    throw Exception("头像上传失败: ${response.msg}")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCategories(): Result<List<UnifiedCategory>> {
        val categories = listOf(
            UnifiedCategory("null_null", "最新分享"),
            UnifiedCategory("45_47", "影音阅读"),
            UnifiedCategory("45_55", "音乐听歌"),
            UnifiedCategory("45_61", "休闲娱乐"),
            UnifiedCategory("45_58", "文件管理"),
            UnifiedCategory("45_59", "图像摄影"),
            UnifiedCategory("45_53", "输入方式"),
            UnifiedCategory("45_54", "生活出行"),
            UnifiedCategory("45_50", "社交通讯"),
            UnifiedCategory("45_56", "上网浏览"),
            UnifiedCategory("45_60", "其他类型"),
            UnifiedCategory("45_62", "跑酷竞技")
        )
        return Result.success(categories)
    }

    private fun parseCategory(id: String?): Pair<Int?, Int?> {
        if (id == null || id == "null_null") return null to null
        val parts = id.split("_")
        val cat = parts.getOrNull(0)?.toIntOrNull()
        val sub = parts.getOrNull(1)?.toIntOrNull()
        return cat to sub
    }

    override suspend fun getApps(categoryId: String?, page: Int, userId: String?): Result<Pair<List<UnifiedAppItem>, Int>> {
        return try {
            val (catId, subCatId) = parseCategory(categoryId)
            val limit = if (userId != null) 12 else 9
            
            val result = apiClient.getAppsList(
                limit = limit,
                page = page,
                sortOrder = "desc",
                categoryId = catId,
                subCategoryId = subCatId,
                appName = null,
                userId = userId?.toLongOrNull()
            )

            result.map { response ->
                if (response.code == 1) {
                    val unifiedItems = response.data.list.map { it.toUnifiedAppItem() }
                    val totalPages = if (response.data.pagecount > 0) response.data.pagecount else 1
                    Pair(unifiedItems, totalPages)
                } else {
                    throw Exception("API Error: ${response.msg}")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchApps(query: String, page: Int, userId: String?): Result<Pair<List<UnifiedAppItem>, Int>> {
         return try {
            val result = apiClient.getAppsList(
                limit = 20,
                page = page,
                appName = query,
                sortOrder = "desc",
                userId = userId?.toLongOrNull()
            )
            result.map { response ->
                if (response.code == 1) {
                    val unifiedItems = response.data.list.map { it.toUnifiedAppItem() }
                    val totalPages = if (response.data.pagecount > 0) response.data.pagecount else 1
                    Pair(unifiedItems, totalPages)
                } else {
                    throw Exception("Search failed: ${response.msg}")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAppDetail(appId: String, versionId: Long): Result<UnifiedAppDetail> {
        return try {
            val token = getToken()
            val result = apiClient.getAppsInformation(
                token = token,
                appsId = appId.toLong(),
                appsVersionId = versionId
            )
            result.map { response ->
                if (response.code == 1) {
                    response.data.toUnifiedAppDetail()
                } else {
                    throw Exception("API Error: ${response.msg}")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAppComments(appId: String, versionId: Long, page: Int): Result<Pair<List<UnifiedComment>, Int>> {
        return try {
            val result = apiClient.getAppsCommentList(
                appsId = appId.toLong(),
                appsVersionId = versionId,
                limit = 20,
                page = page,
                sortOrder = "desc"
            )
            result.map { response ->
                if (response.code == 1) {
                    val unifiedComments = response.data.list.map { it.toUnifiedComment() }
                    val totalPages = if (response.data.pagecount > 0) response.data.pagecount else 1
                    Pair(unifiedComments, totalPages)
                } else {
                    throw Exception("API Error: ${response.msg}")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun postComment(appId: String, versionId: Long, content: String, parentCommentId: String?, mentionUserId: String?): Result<Unit> {
        return try {
            val token = getToken()
            // 修正：如果 parentCommentId 为 null，则传 0 (根评论)
            val parentId = parentCommentId?.toLongOrNull() ?: 0L

            val result = apiClient.postAppComment(
                token = token,
                content = content,
                appsId = appId.toLong(),
                appsVersionId = versionId, 
                parentId = parentId,
                imageUrl = null
            )
            result.map { response ->
                if (response.code == 1) {
                    Unit
                } else {
                    throw Exception("API Error: ${response.msg}")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteComment(commentId: String): Result<Unit> {
        return try {
            val token = getToken()
            val result = apiClient.deleteAppComment(token = token, commentId = commentId.toLong())
             result.map { response ->
                if (response.code == 1) {
                    Unit
                } else {
                    throw Exception("API Error: ${response.msg}")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteApp(appId: String, versionId: Long): Result<Unit> {
        return try {
            val token = getToken()
            if (token.isEmpty()) {
                throw Exception("未登录")
            }
            val result = apiClient.deleteApp(
                usertoken = token,
                apps_id = appId.toLong(),
                app_version_id = versionId
            )
            result.map { response ->
                if (response.code == 1) Unit else throw Exception(response.msg)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun releaseApp(params: UnifiedAppReleaseParams): Result<Unit> {
        return try {
            val token = getToken()
            if (token.isEmpty()) throw Exception("未登录")

            val introImagesString = params.introImages?.joinToString(",") ?: ""

            val result = if (params.isUpdate) {
                apiClient.updateApp(
                    usertoken = token,
                    apps_id = params.appId ?: 0L,
                    appname = params.appName,
                    icon = params.iconUrl,
                    app_size = params.sizeInMb.toString(),
                    app_introduce = params.introduce ?: "",
                    app_introduction_image = introImagesString,
                    file = params.apkUrl ?: "",
                    app_explain = params.explain,
                    app_version = params.versionName,
                    is_pay = params.isPay ?: 0,
                    pay_money = params.payMoney,
                    category_id = params.categoryId ?: 0,
                    sub_category_id = params.subCategoryId ?: 0
                )
            } else {
                apiClient.releaseApp(
                    usertoken = token,
                    appname = params.appName,
                    icon = params.iconUrl,
                    app_size = params.sizeInMb.toString(),
                    app_introduce = params.introduce ?: "",
                    app_introduction_image = introImagesString,
                    file = params.apkUrl ?: "",
                    app_explain = params.explain,
                    app_version = params.versionName,
                    is_pay = params.isPay ?: 0,
                    pay_money = params.payMoney,
                    category_id = params.categoryId ?: 0,
                    sub_category_id = params.subCategoryId ?: 0
                )
            }

            result.map { response ->
                if (response.code == 1) Unit else throw Exception(response.msg)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadImage(file: File, type: String): Result<String> {
        // 复用 KtorClient 中的上传逻辑
        return try {
            val response = KtorClient.uploadHttpClient.submitFormWithBinaryData(
                url = "api.php",
                formData = formData {
                    append("file", InputProvider { file.inputStream().asInput() }, Headers.build {
                        append(HttpHeaders.ContentType, "image/*")
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    })
                }
            )
            
            if (response.status.isSuccess()) {
                val responseBody: KtorClient.UploadResponse = response.body()
                if (responseBody.code == 0 && !responseBody.downurl.isNullOrBlank()) {
                    Result.success(responseBody.downurl)
                } else {
                    Result.failure(Exception(responseBody.msg))
                }
            } else {
                Result.failure(Exception("网络错误 ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadApk(file: File, serviceType: String): Result<String> {
        return try {
            when (serviceType) {
                "KEYUN" -> uploadToKeyun(file)
                "WANYUEYUN" -> uploadToWanyueyun(file)
                else -> Result.failure(Exception("不支持的上传服务类型: $serviceType"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun uploadToKeyun(file: File): Result<String> {
        try {
            val response = KtorClient.uploadHttpClient.submitFormWithBinaryData(
                url = "api.php",
                formData = formData {
                    append("file", InputProvider { file.inputStream().asInput() }, Headers.build {
                        append(HttpHeaders.ContentType, "application/octet-stream")
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    })
                }
            )

            if (response.status.isSuccess()) {
                val responseBody: KtorClient.UploadResponse = response.body()
                if (responseBody.code == 0 && !responseBody.downurl.isNullOrBlank()) {
                    return Result.success(responseBody.downurl)
                } else {
                    return Result.failure(Exception(responseBody.msg))
                }
            } else {
                return Result.failure(Exception("网络错误 ${response.status}"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private suspend fun uploadToWanyueyun(file: File): Result<String> {
        try {
            val response = KtorClient.wanyueyunUploadHttpClient.submitFormWithBinaryData(
                url = "upload",
                formData = formData {
                    append("Api", "小趣API")
                    append("file", InputProvider { file.inputStream().asInput() }, Headers.build {
                        append(HttpHeaders.ContentType, "application/vnd.android.package-archive")
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    })
                }
            )

            if (response.status.isSuccess()) {
                val responseBody: KtorClient.WanyueyunUploadResponse = response.body()
                if (responseBody.code == 200 && !responseBody.data.isNullOrBlank()) {
                    return Result.success(responseBody.data)
                } else {
                    return Result.failure(Exception(responseBody.msg))
                }
            } else {
                return Result.failure(Exception("网络错误 ${response.status}"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    override suspend fun deleteReview(reviewId: String): Result<Unit> {
    return Result.failure(Exception("小趣空间暂不支持评价功能。"))
}

override suspend fun getMyReviews(page: Int): Result<Pair<List<UnifiedComment>, Int>> {
        return Result.failure(Exception("小趣空间暂不支持获取我的评价功能。"))
    }    

override suspend fun deleteComment(appId: String, commentId: String): Result<Unit> {
    // 对于小趣空间，appId 参数不是必需的，但为了接口一致性，我们实现它
    return deleteComment(commentId)
}
    
override suspend fun getMyComments(page: Int): Result<Pair<List<UnifiedComment>, Int>> {
    return Result.failure(NotImplementedError("小趣空间不支持获取我的评论"))
}

    override suspend fun getAppDownloadSources(appId: String, versionId: Long): Result<List<UnifiedDownloadSource>> {
        return getAppDetail(appId, versionId).map { detail ->
            if (detail.downloadUrl != null) {
                listOf(UnifiedDownloadSource(name = "默认下载", url = detail.downloadUrl, isOfficial = true))
            } else {
                emptyList()
            }
        }
    }
}