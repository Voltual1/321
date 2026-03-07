package me.voltual.pyrolysis.feature.store.repository

import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.LingMarketClient
import me.voltual.pyrolysis.data.unified.*
import java.io.File
import org.koin.core.annotation.Single
import kotlinx.coroutines.runBlocking

@Single
class LingMarketRepository : IAppStoreRepository {

    // ==========================================================
    // 核心查询逻辑
    // ==========================================================

    override suspend fun getCategories(): Result<List<UnifiedCategory>> = try {
        LingMarketClient.getCategories(includeInactive = false).map { categories ->
            val special = listOf(UnifiedCategory("-1", "最近更新"),UnifiedCategory("-4", "我的收藏"))
            special + categories.map { it.toUnifiedCategory() }
        }.recover {
            // API 失败时的 Fallback
            listOf(
                UnifiedCategory("-1", "最近更新"), UnifiedCategory("browser", "浏览器"),
                UnifiedCategory("-4", "我的收藏"),
                UnifiedCategory("Games", "游戏"), UnifiedCategory("tools", "实用工具"),
                UnifiedCategory("Apps", "应用商店"), UnifiedCategory("video", "视频播放"),
                UnifiedCategory("teach", "教育学习"), UnifiedCategory("read", "图文阅读"),
                UnifiedCategory("system", "系统优化"), UnifiedCategory("file", "文件管理"),
                UnifiedCategory("watchfaces", "表盘（wearOS4+）"), UnifiedCategory("watchfacess", "表盘（wearOS4-）"),
                UnifiedCategory("pay", "数字消费"), UnifiedCategory("music", "音乐播放"),
                UnifiedCategory("talk", "社交通讯"), UnifiedCategory("walk", "便利出行"),
                UnifiedCategory("tab", "输入法"), UnifiedCategory("desktop", "桌面/启动器"),
                UnifiedCategory("hahaha", "整活搞怪"), UnifiedCategory("xposed", "xposed 模块"),
                UnifiedCategory("Uncategorized", "未分类")
            )
        }.getOrThrow()
        .let { Result.success(it) }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun getApps(categoryId: String?, page: Int, userId: String?): Result<Pair<List<UnifiedAppItem>, Int>> = try {
        // 使用 when 替代不完整的 if-else
        val callResult = when {
            categoryId == "-1" || categoryId == null -> {
                LingMarketClient.getRecentlyUpdatedApps(page, 20)
            }
            categoryId == "-4" -> {
                // 我的收藏分类：获取收藏列表并转换为统一格式
                LingMarketClient.getFavorites(page, 20).map { response ->
                    LingMarketClient.LingMarketAppListResponse(
                        apps = response.favorites.map { it.app },
                        pagination = response.pagination
                    )
                }
            }
            else -> {
                // 确保 categoryId 不为 null 时再调用，或者提供默认值
                LingMarketClient.getAppsByCategory(categoryId, page, 20)
            }
        }

        callResult.map { res -> 
            Pair(res.apps.map { it.toUnifiedAppItem() }, res.pagination.pages) 
        }.getOrThrow().let { Result.success(it) }
        
    } catch (e: Exception) { 
        Result.failure(e) 
    }

    override suspend fun searchApps(query: String, page: Int, userId: String?): Result<Pair<List<UnifiedAppItem>, Int>> = try {
        LingMarketClient.searchApps(query, page, 20).map { res ->
            Pair(res.apps.map { it.toUnifiedAppItem() }, res.pagination.pages)
        }.getOrThrow()
            .let { Result.success(it) }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun getAppDetail(appId: String, versionId: Long): Result<UnifiedAppDetail> = try {
        LingMarketClient.getAppDetail(appId).map { detail ->
            val unified = detail.toUnifiedAppDetail()
            // 灵商店特有逻辑：通过 apkKey 换取真实的下载 URL
            val directUrlResult = LingMarketClient.getFileDownloadUrl(detail.apkKey)
            if (directUrlResult.isSuccess) {
                val directUrl = directUrlResult.getOrNull()?.url
                if (directUrl != null) unified.copy(downloadUrl = directUrl) else unified
            } else {
                unified
            }
        }.getOrThrow()
            .let { Result.success(it) }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun getAppComments(appId: String, versionId: Long, page: Int): Result<Pair<List<UnifiedComment>, Int>> = try {
        LingMarketClient.getAppComments(appId, page, 20).map { res ->
            val unified = res.comments.map { comment ->
                UnifiedComment(
                    id = comment.id, content = comment.content,
                    sendTime = comment.createdAt.toLongOrNull() ?: 0L,
                    sender = comment.user.toUnifiedUser(),
                    childCount = comment.replyCount, raw = comment
                )
            }
            Pair(unified, res.pagination.pages)
        }.getOrThrow()
            .let { Result.success(it) }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun postComment(appId: String, versionId: Long, content: String, parentCommentId: String?, mentionUserId: String?): Result<Unit> = try {
        val res = if (parentCommentId == null) {
            LingMarketClient.postAppComment(appId, content)
        } else {
            LingMarketClient.postCommentReply(appId, parentCommentId, content)
        }
        res.getOrThrow()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun deleteComment(appId: String, commentId: String): Result<Unit> = try {
        val res = LingMarketClient.deleteComment(appId, commentId)
        res.getOrThrow()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
    
        override suspend fun getFavoriteState(appId: String): Result<UnifiedFavoriteState> = try {
        LingMarketClient.checkFavoriteStatus(appId).map { res ->
            UnifiedFavoriteState(
                isFavorite = res.isFavorited,
                favoriteCount = -1 //  灵应用商店的接口不返回总数，传 -1
            )
        }.getOrThrow().let { Result.success(it) }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    override suspend fun toggleFavorite(appId: String, isCurrentlyFavorite: Boolean): Result<Boolean> = try {
    val result = if (isCurrentlyFavorite) {
        LingMarketClient.removeFromFavorites(appId)
    } else {
        LingMarketClient.addToFavorites(appId)
    }
    
    result.map { 
        // 操作成功，返回新的收藏状态（取反）
        !isCurrentlyFavorite
    }.getOrThrow().let { Result.success(it) }
} catch (e: Exception) {
    Result.failure(e)
}

    override suspend fun getCurrentUserDetail(): Result<UnifiedUserDetail> = try {
        LingMarketClient.getUserProfile().map { it.toUnifiedUserDetail() }.getOrThrow()
        .let { Result.success(it) }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun updateUserProfile(params: UpdateUserProfileParams): Result<Unit> = try {
        val nickname = params.nickname ?: params.displayName ?: throw Exception("昵称不能为空")
        val res = LingMarketClient.updateUserProfile(nickname, params.description)
        res.getOrThrow()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun uploadAvatar(imageBytes: ByteArray, filename: String): Result<String> = try {
        LingMarketClient.uploadAvatar(imageBytes, filename).map { res ->
            if (res.isSuccess) res.avatarUrl ?: "上传成功" else throw Exception(res.msg)
        }.getOrThrow()
            .let { Result.success(it) }
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun getAppDownloadSources(appId: String, versionId: Long): Result<List<UnifiedDownloadSource>> =
    getAppDetail(appId, versionId).map { detail ->
        detail.downloadUrl?.let { 
            listOf(UnifiedDownloadSource("默认下载源", it, true)) 
        } ?: emptyList()
    }

    // 其余不支持的方法（toggleFavorite, deleteApp, releaseApp, uploadApk 等）
    // 均自动继承接口的默认 failure 实现。
}