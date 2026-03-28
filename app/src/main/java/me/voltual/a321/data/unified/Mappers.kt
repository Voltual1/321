package me.voltual.a321.data.unified

import me.voltual.a321.KtorClient

fun KtorClient.FileInfo.toUnifiedModel(): PanFile {
    return PanFile(
        id = this.FileId,
        name = this.FileName,
        size = this.Size,
        isDirectory = this.isDirectory,
        updateTime = this.UpdateAt,
        category = this.Category,
        isAbnormal = this.isAbnormal,
        etag = this.Etag,
        s3KeyFlag = this.S3KeyFlag,
        rawDownloadUrl = this.DownloadUrl // 映射列表自带的 URL
    )
}

fun List<KtorClient.FileInfo>.toUnifiedList(): List<PanFile> {
    return this.map { it.toUnifiedModel() }
}

/**
 * 将 123 网盘用户信息映射为统一配额模型
 */
fun KtorClient.UserInfo.toUnifiedQuota(): PanUserQuota {
    return PanUserQuota(
        userId = this.UID.toString(),
        nickname = this.Nickname,
        avatarUrl = this.HeadImage.takeIf { it.isNotBlank() },
        usedBytes = this.SpaceUsed,
        // 总空间 = 永久空间 + 临时空间
        totalBytes = this.SpacePermanent + this.SpaceTemp,
        isVip = false, // 123网盘API中若有等级字段可在此映射
        expireTime = this.SpaceTempExpr.takeIf { it.isNotBlank() }
    )
}

/**
 * 将文件夹详情映射为 PanFile
 * 注意：文件夹在统一模型中 size 通常为 0 或总大小
 */
fun KtorClient.FolderDetailsData.toUnifiedModel(): PanFile {
    return PanFile(
        id = this.FileId,
        name = this.FileName,
        size = this.Size ?: 0L,
        isDirectory = true,
        updateTime = "", // 详情接口有时不返回更新时间，保持空或默认
        category = 1
    )
}

/**
 * 将网盘列表响应包装为统一分页结果
 */
fun KtorClient.FileListData.toPageResult(): PanPageResult {
    return PanPageResult(
        files = this.InfoList.toUnifiedList(),
        totalCount = this.Total,
        // 123 网盘目前靠 Page 偏移，若 files 数量达到 Total 或当前页不满 limit 则无更多
        hasMore = this.InfoList.isNotEmpty() 
    )
}

/**
 * 通用映射器：将 API 的 Result 转换为统一的 Action 结果
 */
fun <T> Result<KtorClient.PanResponse<T>>.toActionResult(): PanActionResult {
    return this.fold(
        onSuccess = { response ->
            if (response.isSuccess) {
                PanActionResult.Success
            } else {
                PanActionResult.Error(response.code, response.message)
            }
        },
        onFailure = { throwable ->
            PanActionResult.Error(-1, throwable.message ?: "Unknown Network Error")
        }
    )
}

/**
 * 将分享信息转换为统一的文件模型，以便在文件列表中显示
 */
fun KtorClient.ShareInfo.toUnifiedModel(): PanFile {
    return PanFile(
        id = this.ShareId,
        name = this.ShareName,
        size = this.bytesTotal,               // 分享文件总大小
        isDirectory = false,                  // 分享视为文件项
        updateTime = this.UpdateAt,
        category = 10,                        // 自定义分类，表示分享
        isAbnormal = false,
        etag = null,
        s3KeyFlag = null,
        rawDownloadUrl = this.shareLinkList.list.firstOrNull(), // 取第一个分享链接作为原始 URL
        extension = ""                        // 无扩展名
    )
}

/**
 * 将分享列表响应转换为统一的分页结果
 */
fun KtorClient.ShareListData.toPageResult(): PanPageResult {
    return PanPageResult(
        files = this.InfoList.map { it.toUnifiedModel() },
        totalCount = this.Total,
        // Next 为 "-1" 表示没有更多数据，否则继续
        hasMore = this.Next != "-1" && this.Next.toIntOrNull() != -1,
        nextMarker = if (this.Next != "-1" && this.Next != "0") this.Next else null
    )
}

fun KtorClient.ShareGetResponseData.toPageResult(): PanPageResult {
    return PanPageResult(
        files = this.InfoList.toUnifiedList(),
        totalCount = this.Len,               // Len 是当前页返回的数量，非总数（接口未提供总数）
        hasMore = this.Next != "-1" && this.Len > 0,
        nextMarker = if (this.Next != "-1") this.Next else null
    )
}