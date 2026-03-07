// Copyright (C) 2025 Voltual
package me.voltual.pyrolysis.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import me.voltual.pyrolysis.AppStore

/**
 * Navigation 3 的类型安全目的地契约。
 * 移除了所有冗余的 route 字符串定义，完全依赖 Kotlinx Serialization 进行类型匹配。
 */
sealed interface AppDestination : NavKey

// --- 核心导航 ---
@Serializable
data object Home : AppDestination

@Serializable
data object Login : AppDestination

@Serializable
data object About : AppDestination

@Serializable
data object LogViewer : AppDestination

@Serializable
data object ThemeCustomize : AppDestination

@Serializable
data object StoreManager : AppDestination

@Serializable
data object UpdateSettings : AppDestination

@Serializable
data object Update : AppDestination

// --- 社区与帖子 ---
@Serializable
data object Community : AppDestination

@Serializable
data object MyLikes : AppDestination

@Serializable
data object HotPosts : AppDestination

@Serializable
data object FollowingPosts : AppDestination

@Serializable
data object BrowseHistory : AppDestination

@Serializable
data object Download : AppDestination

/** 帖子详情 */
@Serializable
data class PostDetail(val postId: Long) : AppDestination

/** 创建帖子 */
@Serializable
data object CreatePost : AppDestination

/** 创建退币申请帖 */
@Serializable
data class CreateRefundPost(
    val appId: Long,
    val versionId: Long,
    val appName: String,
    val payMoney: Int
) : AppDestination

/** 图片预览 */
@Serializable
data class ImagePreview(val imageUrl: String) : AppDestination

// --- 用户相关 ---
/** 用户详情 */
@Serializable
data class UserDetail(
    val userId: Long,
    val store: AppStore = AppStore.XIAOQU_SPACE
) : AppDestination

/** 用户的帖子列表 */
@Serializable
data class MyPosts(
    val userId: Long,
    val nickname: String? = null
) : AppDestination

/** 搜索 */
@Serializable
data class Search(
    val userId: String? = null,
    val nickname: String? = null
) : AppDestination

@Serializable
data object MyComments : AppDestination

@Serializable
data object MyReviews : AppDestination

@Serializable
data object FollowList : AppDestination

@Serializable
data object FanList : AppDestination

/** 账号资料 */
@Serializable
data class AccountProfile(val store: AppStore = AppStore.XIAOQU_SPACE) : AppDestination

@Serializable
data object SignInSettings : AppDestination

// --- 资源广场与应用 ---
/** 资源广场 */
@Serializable
data class ResourcePlaza(
    val isMyResource: Boolean,
    val userId: Long = -1L,
    val mode: String = "public",
    val storeName: String = AppStore.XIAOQU_SPACE.name
) : AppDestination

@Serializable
data object Explore : AppDestination

@Serializable
data object SortFilterSheet : AppDestination

/** 应用详情 */
@Serializable
data class AppDetail(
    val appId: String,
    val versionId: Long,
    val storeName: String
) : AppDestination

//F-Droid应用详情
@Serializable
data class AppPage(
    val packageName :String
) : AppDestination

@Serializable
data object SearchPage : AppDestination

@Serializable
data object PrefsReposPage : AppDestination

@Serializable
data object CreateAppRelease : AppDestination

/** 更新应用发布 */
@Serializable
data class UpdateAppRelease(val appDetailJson: String) : AppDestination

// --- 消息、账单、支付 ---
@Serializable
data object MessageCenter : AppDestination

@Serializable
data object Billing : AppDestination

@Serializable
data object PaymentCenterAdvanced : AppDestination

/** 应用购买支付 */
@Serializable
data class PaymentForApp(
    val appId: Long,
    val appName: String,
    val versionId: Long,
    val price: Int,
    val iconUrl: String,
    val previewContent: String
) : AppDestination

/** 帖子打赏支付 */
@Serializable
data class PaymentForPost(
    val postId: Long,
    val postTitle: String,
    val previewContent: String,
    val authorName: String,
    val authorAvatar: String,
    val postTime: String
) : AppDestination

// --- 其他 ---
@Serializable
data object RankingList : AppDestination

/** 视频播放 */
@Serializable
data class Player(val bvid: String) : AppDestination