//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.feature.store.repository

import me.voltual.pyrolysis.SineShopClient
import me.voltual.pyrolysis.data.unified.*
import java.io.File
import me.voltual.pyrolysis.AuthManager
import kotlin.math.ceil
import org.koin.core.annotation.Single
import me.voltual.pyrolysis.BBQApplication
import kotlinx.coroutines.flow.first

@Single
class SineShopRepository : IAppStoreRepository {

    private fun calculateTotalPages(totalItems: Int, pageSize: Int = 10): Int {
        if (totalItems <= 0) return 1
        return ceil(totalItems.toDouble() / pageSize).toInt()
    }
    
    private suspend fun getToken(): String {
        return AuthManager.getSineMarketToken(BBQApplication.instance).first()
    }

    override suspend fun getCategories(): Result<List<UnifiedCategory>> = try {
        SineShopClient.getAppTagList().map { tagList ->
            val specialCategories = listOf(
                UnifiedCategory(id = "-1", name = "最新上传"),
                UnifiedCategory(id = "-2", name = "最多下载"),
                UnifiedCategory(id = "-3", name = "我的上传"),
                UnifiedCategory(id = "-4", name = "我的收藏"),
                UnifiedCategory(id = "-5", name = "历史足迹")
            )
            specialCategories + tagList.map { it.toUnifiedCategory() }
        }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun getApps(categoryId: String?, page: Int, userId: String?): Result<Pair<List<UnifiedAppItem>, Int>> = try {
        // 部分“我的”分类接口需要 Token
        val result = when {
            userId != null -> SineShopClient.getAppsList(userId = userId.toInt(), page = page)
            categoryId == "-1" -> SineShopClient.getLatestAppsList(page = page)
            categoryId == "-2" -> SineShopClient.getMostDownloadedAppsList(page = page)
            categoryId == "-3" -> SineShopClient.getMyUploadAppsList(page = page, token = getToken())
            categoryId == "-4" -> SineShopClient.getMyFavouriteAppsList(page = page, token = getToken())
            categoryId == "-5" -> SineShopClient.getMyHistoryAppsList(page = page, token = getToken())
            else -> {
                categoryId?.toIntOrNull()?.let { tagId ->
                    SineShopClient.getAppsList(tag = tagId, page = page)
                } ?: Result.success(SineShopClient.AppListData(0, emptyList()))
            }
        }
        result.map { appListData ->
            Pair(appListData.list.map { it.toUnifiedAppItem() }, calculateTotalPages(appListData.total))
        }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun searchApps(query: String, page: Int, userId: String?): Result<Pair<List<UnifiedAppItem>, Int>> = try {
        SineShopClient.getAppsList(keyword = query, page = page).map { 
            Pair(it.list.map { item -> item.toUnifiedAppItem() }, calculateTotalPages(it.total))
        }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun getAppDetail(appId: String, versionId: Long): Result<UnifiedAppDetail> = try {
        // 获取详情时传入 Token 可以获取该用户是否已收藏 (is_favourite)
        SineShopClient.getSineShopAppInfo(appId = appId.toInt(), token = getToken()).map { it.toUnifiedAppDetail() }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun getAppComments(appId: String, versionId: Long, page: Int): Result<Pair<List<UnifiedComment>, Int>> = try {
        SineShopClient.getSineShopAppComments(appId = appId.toInt(), page = page).map { 
            Pair(it.list.map { c -> c.toUnifiedComment() }, calculateTotalPages(it.total))
        }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun postComment(appId: String, versionId: Long, content: String, parentCommentId: String?, mentionUserId: String?): Result<Unit> = try {
        val token = getToken()
        val res = if (parentCommentId == null) {
            SineShopClient.postSineShopAppRootComment(appId = appId.toInt(), content = content, token = token)
        } else {
            if (mentionUserId != null) {
                SineShopClient.postSineShopAppReplyCommentWithMention(parentCommentId.toInt(), content, mentionUserId.toInt(), token = token)
            } else {
                SineShopClient.postSineShopAppReplyComment(parentCommentId.toInt(), content, token = token)
            }
        }
        res.map { Unit }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun deleteComment(commentId: String): Result<Unit> = try {
        SineShopClient.deleteSineShopComment(commentId = commentId.toInt(), token = getToken())
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun getMyComments(page: Int): Result<Pair<List<UnifiedComment>, Int>> = try {
        SineShopClient.getMyComments(page = page, token = getToken()).map { 
            Pair(it.list.map { c -> c.toUnifiedComment() }, calculateTotalPages(it.total))
        }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun getMyReviews(page: Int): Result<Pair<List<UnifiedComment>, Int>> = try {
        SineShopClient.getMyReviews(page = page, token = getToken()).map { 
            Pair(it.list.map { it.toUnifiedReview() }, calculateTotalPages(it.total))
        }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun deleteReview(reviewId: String): Result<Unit> = try {
        SineShopClient.deleteSineShopReview(reviewId = reviewId.toInt(), token = getToken())
    } catch (e: Exception) { Result.failure(e) }

    // ==========================================================
    // 用户与下载功能
    // ==========================================================

    override suspend fun getCurrentUserDetail(): Result<UnifiedUserDetail> = try {
        SineShopClient.getUserInfo(token = getToken()).map { it.toUnifiedUserDetail() }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun updateUserProfile(params: UpdateUserProfileParams): Result<Unit> = try {
        if (params.displayName.isNullOrEmpty() && params.description.isNullOrEmpty()) {
            Result.success(Unit)
        } else {
            SineShopClient.editUserInfo(
                params.displayName ?: "", 
                params.description ?: "", 
                token = getToken()
            ).map { 
                if (it) Unit else throw Exception("更新失败")
            }
        }
    } catch (e: Exception) { Result.failure(e) }
    
    override suspend fun getFavoriteState(appId: String): Result<UnifiedFavoriteState> = try {
        // 直接复用详情接口获取状态
        getAppDetail(appId, 0).map { detail ->
            UnifiedFavoriteState(
                isFavorite = detail.isFavorite,
                favoriteCount = detail.favoriteCount
            )
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    override suspend fun toggleFavorite(appId: String, isCurrentlyFavorite: Boolean): Result<Boolean> = try {
    val token = getToken()
    val result = if (isCurrentlyFavorite) {
        // 当前已收藏，执行取消收藏
        SineShopClient.dislikeApp(appId = appId.toInt(), token = token)
    } else {
        // 当前未收藏，执行收藏
        SineShopClient.likeApp(appId = appId.toInt(), token = token)
    }
    
    result.map { success ->
        if (success) {
            // 操作成功，返回新的收藏状态（取反）
            !isCurrentlyFavorite
        } else {
            // 操作失败，保持原状态
            isCurrentlyFavorite
        }
    }
} catch (e: Exception) {
    Result.failure(e)
}

    override suspend fun uploadAvatar(imageBytes: ByteArray, filename: String): Result<String> = try {
        SineShopClient.uploadAvatar(imageBytes, filename, token = getToken()).map { 
            if (it) "上传成功" else throw Exception("失败") 
        }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun getAppDownloadSources(appId: String, versionId: Long): Result<List<UnifiedDownloadSource>> = try {
        SineShopClient.getAppDownloadSources(appId.toInt(), token = getToken()).map { sources -> 
            sources.map { it.toUnifiedDownloadSource() }
        }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun getAppVersionsByPackageName(packageName: String, page: Int): Result<Pair<List<UnifiedAppItem>, Int>> = try {
        SineShopClient.getAppVersionsByPackageName(packageName = packageName, page = page).map { 
            Pair(it.list.map { item -> item.toUnifiedAppItem() }, calculateTotalPages(it.total))
        }
    } catch (e: Exception) { Result.failure(e) }
}