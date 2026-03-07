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
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.SineShopClient
import me.voltual.pyrolysis.KtorClient.AppItem
import me.voltual.pyrolysis.KtorClient.Comment
import me.voltual.pyrolysis.KtorClient.AppComment
import me.voltual.pyrolysis.KtorClient.AppDetail
import me.voltual.pyrolysis.KtorClient.UserInformationData
import me.voltual.pyrolysis.SineShopClient.SineShopApp
import me.voltual.pyrolysis.SineShopClient.SineShopUserInfoLite
import me.voltual.pyrolysis.SineShopClient.SineShopComment
import me.voltual.pyrolysis.SineShopClient.SineShopAppDetail
import me.voltual.pyrolysis.SineShopClient.AppTag
import me.voltual.pyrolysis.SineShopClient.SineShopDownloadSource
import me.voltual.pyrolysis.SineShopClient.SineShopUserInfo
import me.voltual.pyrolysis.LingMarketClient
import me.voltual.pyrolysis.AbiFlag
import me.voltual.pyrolysis.FavouriteState

// --- KtorClient (小趣空间) Mappers ---

fun KtorClient.AppItem.toUnifiedAppItem(): UnifiedAppItem {
    return UnifiedAppItem(
        uniqueId = "${AppStore.XIAOQU_SPACE}-${this.id}-${this.apps_version_id}",
        navigationId = this.id.toString(),
        navigationVersionId = this.apps_version_id,
        store = AppStore.XIAOQU_SPACE,
        name = this.appname,
        iconUrl = this.app_icon,
        versionName = ""
    )
}

fun createUnifiedUserFromKtor(id: Long, name: String, avatar: String): UnifiedUser {
    return UnifiedUser(
        id = id.toString(),
        displayName = name,
        avatarUrl = avatar
    )
}

fun KtorClient.Comment.toUnifiedComment(): UnifiedComment {
    return UnifiedComment(
        id = this.id.toString(),
        content = this.content,
        sendTime = this.time.toLongOrNull() ?: 0L,
        sender = createUnifiedUserFromKtor(this.userid, this.nickname, this.usertx),
        childCount = this.sub_comments_count,
        // KtorClient.Comment 没有直接包含子评论列表，设为空
        childComments = emptyList(), 
        fatherReply = null,
        raw = this
    )
}

fun KtorClient.AppComment.toUnifiedComment(): UnifiedComment {
    val father = if (this.parentid != null && this.parentid != 0L) {
        UnifiedComment(
            id = this.parentid.toString(),
            content = this.parentcontent ?: "",
            sendTime = 0L,
            sender = createUnifiedUserFromKtor(0L, this.parentnickname ?: "未知用户", ""),
            childCount = 0,
            fatherReply = null,
            raw = this
        )
    } else null

    return UnifiedComment(
        id = this.id.toString(),
        content = this.content,
        sendTime = this.time.toLongOrNull() ?: 0L,
        sender = createUnifiedUserFromKtor(this.userid, this.nickname, this.usertx),
        childCount = 0,
        fatherReply = father,
        raw = this
    )
}

fun KtorClient.AppDetail.toUnifiedAppDetail(): UnifiedAppDetail {
    return UnifiedAppDetail(
        id = this.id.toString(),
        store = AppStore.XIAOQU_SPACE,
        packageName = "", 
        name = this.appname,
        versionCode = 0L,
        versionName = this.version,
        iconUrl = this.app_icon,
        type = this.category_name,
        previews = this.app_introduction_image_array,
        description = this.app_introduce,
        updateLog = this.app_explain,
        developer = null,
        size = this.app_size,
        uploadTime = this.create_time.toLongOrNull() ?: 0L,
        user = createUnifiedUserFromKtor(this.userid, this.nickname, this.usertx),
        tags = listOf(this.category_name, this.sub_category_name),
        downloadCount = this.download_count,
        isFavorite = false,
        favoriteCount = 0,
        reviewCount = this.comment_count,
        downloadUrl = if (this.is_pay == 0 || this.is_user_pay) this.download else null,
        raw = this
    )
}

// --- SineShopClient (弦应用商店) Mappers ---

fun SineShopClient.SineShopApp.toUnifiedAppItem(): UnifiedAppItem {
    // 解析 ABI 标签
    val abiLabel = AbiFlag.parseAbi(this.app_abi)
    
    // 拼接 info 字符串：类型 + 版本类型 + 版本名 + ABI
    // 示例结果: "工具 正式版 1.0.0 ARM64"
    val infoString = "${this.app_type} ${this.app_version_type} ${this.version_name} $abiLabel"

    return UnifiedAppItem(
        uniqueId = "${AppStore.SIENE_SHOP}-${this.id}",
        navigationId = this.id.toString(),
        navigationVersionId = 0L,
        store = AppStore.SIENE_SHOP,
        name = this.app_name,
        iconUrl = this.app_icon,
        versionName = this.version_name,
        info = infoString 
    )
}

fun SineShopClient.SineShopUserInfoLite.toUnifiedUser(): UnifiedUser {
    return UnifiedUser(
        id = this.id.toString(),
        displayName = this.displayName,
        avatarUrl = this.userAvatar
    )
}

fun SineShopClient.SineShopComment.toUnifiedComment(): UnifiedComment {
    return UnifiedComment(
        id = this.id.toString(),
        content = this.content,
        sendTime = this.send_time,
        sender = this.sender.toUnifiedUser(),
        childCount = this.child_count,
        fatherReply = this.father_reply?.toUnifiedComment(),
        raw = this,
        appId = if (this.app_id == -1) null else this.app_id.toString(),
        versionId = null
    )
}

fun SineShopClient.SineShopAppDetail.toUnifiedAppDetail(): UnifiedAppDetail {
    return UnifiedAppDetail(
        id = this.id.toString(),
        store = AppStore.SIENE_SHOP,
        packageName = this.package_name,
        name = this.app_name,
        versionCode = this.version_code.toLong(),
        versionName = this.version_name,
        iconUrl = this.app_icon,
        type = this.app_type,
        previews = this.app_previews,
        description = this.app_describe,
        updateLog = this.app_update_log,
        developer = this.app_developer,
        size = this.download_size,
        uploadTime = this.upload_time,
        user = this.user.toUnifiedUser(),
        tags = this.tags?.map { it.name },
        downloadCount = this.download_count,
        // 使用枚举类进行转换，消除魔法数字
        isFavorite = FavouriteState.isFavourite(this.is_favourite),
        favoriteCount = this.favourite_count,
        reviewCount = this.review_count,
        downloadUrl = null,
        raw = this
    )
}

fun SineShopClient.AppTag.toUnifiedCategory(): UnifiedCategory {
    return UnifiedCategory(
        id = this.id.toString(),
        name = this.name,
        icon = this.icon
    )
}

fun SineShopClient.SineShopDownloadSource.toUnifiedDownloadSource(): UnifiedDownloadSource {
    return UnifiedDownloadSource(
        name = this.name,
        url = this.url,
        isOfficial = this.isExtra == 1
    )
}

fun SineShopClient.SineShopUserInfo.toUnifiedUser(): UnifiedUser {
    return UnifiedUser(
        id = this.id.toString(),
        displayName = this.displayName,
        avatarUrl = this.userAvatar
    )
}

/**
 * 将小趣空间用户数据转换为统一用户详情
 * 
 * 注意：数据字段映射已修正（2026年1月9日18点）
 * - followerscount → followersCount: 用户关注的人数（该用户关注了多少人）
 * - fanscount → fansCount: 用户的粉丝数（有多少人关注了该用户）
 * 
 * 此前版本因映射错误导致UI显示相反，现已修复
 */

fun KtorClient.UserInformationData.toUnifiedUserDetail(): UnifiedUserDetail {
// 解析关注状态（注意：响应中的 follow_status 是 String 类型）
    val followStatusValue = this.follow_status.toIntOrNull() ?: 3
    val followStatus = FollowStatus.fromValue(followStatusValue)
    return UnifiedUserDetail(
        id = this.id,
        username = this.username,
        displayName = this.nickname,
        avatarUrl = this.usertx,
        description = null, // 小趣空间无此字段
        hierarchy = this.hierarchy,
        followersCount = this.followerscount,
        fansCount = this.fanscount,
        followStatus = followStatus,  
        postCount = this.postcount,
        likeCount = this.likecount,
        money = this.money,
        commentCount = this.commentcount?.toIntOrNull(), // 从 String? 转换为 Int?
        seriesDays = this.series_days,
        lastActivityTime = this.last_activity_time,
        store = AppStore.XIAOQU_SPACE,
        raw = this
    )
}

fun SineShopClient.SineShopUserInfo.toUnifiedUserDetail(): UnifiedUserDetail {
    return UnifiedUserDetail(
        id = this.id.toLong(),
        username = this.username,
        displayName = this.displayName,
        avatarUrl = this.userAvatar,
        description = this.userDescribe,
        userOfficial = this.userOfficial,
        userBadge = this.userBadge,
        userStatus = this.userStatus,
        userStatusReason = this.userStatusReason,
        banTime = this.banTime?.toLong(),
        joinTime = this.joinTime,
        userPermission = this.userPermission,
        bindQq = this.bindQq,
        bindEmail = this.bindEmail,
        bindBilibili = this.bindBilibili,
        verifyEmail = this.verifyEmail,
        lastLoginDevice = this.lastLoginDevice,
        lastOnlineTime = this.lastOnlineTime,
        uploadCount = this.uploadCount,
        replyCount = this.replyCount,
        store = AppStore.SIENE_SHOP,
        raw = this
    )
}

fun SineShopClient.SineShopReview.toUnifiedReview(): UnifiedComment {
    return UnifiedComment(
        id = this.id.toString(),
        content = this.content,
        sendTime = this.createTime,
        sender = this.user.toUnifiedUser(),
        childCount = 0, 
        fatherReply = null,
        raw = this,
        appId = this.appId.toString(),       
        versionId = this.appVersion.toLongOrNull(), 
        rating = this.rating,
        isCountedInRating = this.isCountedInRating
    )
}

// --- LingMarketClient (灵应用商店) Mappers ---

private fun buildLingMarketIconUrl(iconKey: String): String {
    // 专门为灵应用商店构建图标URL
    val baseUrl = LingMarketClient.LINGMARKET_ICON_BASE_URL.removeSuffix("/")
    val cleanIconKey = iconKey.removePrefix("/")
    return "$baseUrl/$cleanIconKey"
}
fun LingMarketClient.LingMarketUploader.toUnifiedUser(): UnifiedUser {
    return UnifiedUser(
        id = this.id,
        displayName = this.nickname ?: this.username ?: "未知用户", // 确保非空
        avatarUrl = null
    )
}

fun LingMarketClient.LingMarketCategory.toUnifiedCategory(): UnifiedCategory {
    return UnifiedCategory(
        id = this.name,           // 使用 name 字段作为 id因为弦和灵的id含义不同
        name = this.displayName,  // 使用 displayName 作为显示名称
        icon = this.icon
    )
}

fun LingMarketClient.LingMarketUser.toUnifiedUser(): UnifiedUser {
    return UnifiedUser(
        id = this.id,
        displayName = this.nickname ?: this.username ?: "未知用户", // 确保非空
        avatarUrl = this.avatarUrl
    )
}

fun LingMarketClient.LingMarketUserLite.toUnifiedUser(): UnifiedUser {
    return UnifiedUser(
        id = this.id,
        displayName = this.nickname ?: this.username ?: "未知用户", // 确保非空
        avatarUrl = this.avatarUrl
    )
}

fun LingMarketClient.LingMarketAppMinimal.toUnifiedAppItem(): UnifiedAppItem {
    return UnifiedAppItem(
        uniqueId = "${AppStore.LING_MARKET}-${this.id}-${this.versionCode}",
        navigationId = this.id,
        navigationVersionId = this.versionCode.toLong(),
        store = AppStore.LING_MARKET,
        name = this.name,
        iconUrl = buildLingMarketIconUrl(this.iconKey),
        versionName = this.versionName
    )
}

fun LingMarketClient.LingMarketApp.toUnifiedAppDetail(): UnifiedAppDetail {
    // 格式化大小
    val formattedSize = if (this.size > 0) {
        when {
            this.size >= 1024 * 1024 -> String.format("%.1f MB", this.size / (1024.0 * 1024.0))
            this.size >= 1024 -> String.format("%.1f KB", this.size / 1024.0)
            else -> "${this.size} B"
        }
    } else {
        null
    }

    return UnifiedAppDetail(
        id = this.id,
        store = AppStore.LING_MARKET,
        packageName = this.packageName,
        name = this.name,
        versionCode = this.versionCode.toLong(),
        versionName = this.versionName,
        iconUrl = buildLingMarketIconUrl(this.iconKey),
        type = this.category,
        previews = this.screenshotKeys,
        description = this.description,
        updateLog = this.changelog ?: "", // 使用可选值
        developer = null, // 灵应用商店没有单独的开发者字段
        size = formattedSize, // 使用格式化后的字符串
        uploadTime = this.createdAt.toLongOrNull() ?: 0L,
        user = this.uploader.toUnifiedUser(),
        tags = this.tags,
        downloadCount = this.downloads,
        isFavorite = false,
        favoriteCount = -1,
        reviewCount = this.ratingCount,
        downloadUrl = null, // 留空，由 ViewModel 处理下载
        raw = this
    )
}

fun LingMarketClient.LingMarketUser.toUnifiedUserDetail(): UnifiedUserDetail {
    return UnifiedUserDetail(
        id = this.id.toLongOrNull() ?: 0L,
        username = this.username,
        displayName = this.nickname,
        avatarUrl = this.avatarUrl,
        description = this.bio,
        store = AppStore.LING_MARKET,
        raw = this
    )
}