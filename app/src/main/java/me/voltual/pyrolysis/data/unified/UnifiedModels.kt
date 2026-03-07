//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.data.unified

import me.voltual.pyrolysis.AppStore
import java.io.File

/**
 * 统一的用户信息模型
 */
data class UnifiedUser(
    val id: String,
    val displayName: String,
    val avatarUrl: String?
)

/**
 * 统一的评论模型
 */
data class UnifiedComment(
    val id: String,
    val content: String,
    val sendTime: Long,
    val sender: UnifiedUser,
    val childCount: Int,
    val childComments: List<UnifiedComment> = emptyList(),
    val fatherReply: UnifiedComment? = null,
    val raw: Any,
    val appId: String? = null,
    val versionId: Long? = null,
    val rating: Int? = null,
    val isCountedInRating: Boolean? = null 
)

/**
 * 统一的更新用户资料参数
 */
data class UpdateUserProfileParams(
    // 小趣空间字段
    val nickname: String? = null,
    val qqNumber: String? = null,
    
    // 弦应用商店字段
    val displayName: String? = null,
    val description: String? = null,
    
    // 设备名称（本地存储）
    val deviceName: String? = null
)

/**
 * 统一的应用详情模型
 */

data class UnifiedAppDetail(
    val id: String,
    val store: AppStore,
    val packageName: String,
    val name: String,
    val versionCode: Long,
    val versionName: String,
    val iconUrl: String,
    val type: String,
    val previews: List<String>?,
    val description: String?,
    val updateLog: String?,
    val developer: String?,
    val size: String?,
    val uploadTime: Long,
    val user: UnifiedUser,
    val tags: List<String>?,
    val downloadCount: Int,
    val isFavorite: Boolean,
    val favoriteCount: Int,
    val reviewCount: Int,
    val downloadUrl: String?, // 直接可用的下载URL
    val raw: Any,
    // 微思应用商店专用字段
    val minsdkDisplay: String? = null,
    val targetsdkDisplay: String? = null,
    val cpuArchDisplay: String? = null,
    val osCompatibilityDisplay: String? = null,
    val displayCompatibilityDisplay: String? = null,
    val watchCount: Int? = null,
    val upnote: String? = null,
    val versionTypeDisplay: String? = null
)

/**
 * 统一的收藏/点赞状态数据类
 * 屏蔽不同商店 API 字段名（like, favorite, ratingCount）的差异
 */
data class UnifiedFavoriteState(
    val isFavorite: Boolean,
    val favoriteCount: Int = -1 // 默认值为 -1，表示不支持统计
)

/**
 * 统一的应用列表项模型
 */
data class UnifiedAppItem(
    val uniqueId: String,
    val navigationId: String,
    val navigationVersionId: Long,
    val store: AppStore,
    val name: String,
    val iconUrl: String,
    val versionName: String,
    // 新增：统一的描述性副标题/信息标签
    val info: String? = null
)

/**
 * 统一的应用分类模型
 */
data class UnifiedCategory(
    val id: String,
    val name: String,
    val icon: String? = null
)

/**
 * 统一的下载源模型
 */
data class UnifiedDownloadSource(
    val name: String,
    val url: String,
    val isOfficial: Boolean
)

/**
 * 统一的用户详情模型（支持小趣空间和弦应用商店）
 */
data class UnifiedUserDetail(
    val id: Long,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val description: String? = null,
    
    // 小趣空间特有字段
    val hierarchy: String? = null,  // 等级
    val followersCount: String? = null,
    val fansCount: String? = null,
    val postCount: String? = null,
    val likeCount: String? = null,
    val followStatus: FollowStatus? = null, // 新增：关注状态
    val money: Int? = null,
    val commentCount: Int? = null,
    val seriesDays: Int? = null,  // 连续签到天数
    val lastActivityTime: String? = null,
    
    // 弦应用商店特有字段
    val userOfficial: String? = null,
    val userBadge: String? = null,
    val userStatus: Int? = null,
    val userStatusReason: String? = null,
    val banTime: Long? = null,
    val joinTime: Long? = null,
    val userPermission: Int? = null,
    val bindQq: Long? = null,
    val bindEmail: String? = null,
    val bindBilibili: Int? = null,
    val verifyEmail: Int? = null,
    val lastLoginDevice: String? = null,
    val lastOnlineTime: Long? = null,
    val uploadCount: Int? = null,
    val replyCount: Int? = null,
    
    val store: AppStore,
    val raw: Any? = null
)
/**
 * 统一的发布应用参数
 * 包含两个平台所需的所有字段，使用可空类型处理差异
 */
data class UnifiedAppReleaseParams(
    val store: AppStore,
    // 通用字段
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sizeInMb: Double,
    val iconFile: File?, // 本地文件
    val iconUrl: String?, // 网络URL (小趣空间用)
    val apkFile: File?,   // 本地文件 (弦开放平台用)
    val apkUrl: String?,  // 网络URL (小趣空间用)
    
    // 小趣空间特有
    val introduce: String? = null, // 资源介绍
    val explain: String? = null,   // 适配说明
    val introImages: List<String>? = null, // 介绍图URL列表
    val categoryId: Int? = null,
    val subCategoryId: Int? = null,
    val isPay: Int? = null,
    val payMoney: String? = null,
    val isUpdate: Boolean = false,
    val appId: Long? = null, // 更新时需要
    val appVersionId: Long? = null, // 更新/删除时需要

    // 弦开放平台特有
    val appTypeId: Int? = null,
    val appVersionTypeId: Int? = null,
    val appTags: String? = null, // ID字符串
    val sdkMin: Int? = null,
    val sdkTarget: Int? = null,
    val developer: String? = null,
    val source: String? = null,
    val describe: String? = null, // 应用描述
    val updateLog: String? = null,
    val uploadMessage: String? = null,
    val keyword: String? = null,
    val isWearOs: Int? = null,
    val abi: Int? = null,
    val screenshots: List<File>? = null // 本地截图文件列表
)

// 小趣空间关注状态密封类
sealed class FollowStatus(val value: Int) {
    data object MutualFollow : FollowStatus(0)      // 已互关
    data object FollowedYou : FollowStatus(1)       // 关注了你
    data object YouFollowed : FollowStatus(2)       // 已关注
    data object NotFollowed : FollowStatus(3)       // 未关注
    
    companion object {
        fun fromValue(value: Int): FollowStatus {
            return when (value) {
                0 -> MutualFollow
                1 -> FollowedYou
                2 -> YouFollowed
                3 -> NotFollowed
                else -> NotFollowed
            }
        }
    }
}