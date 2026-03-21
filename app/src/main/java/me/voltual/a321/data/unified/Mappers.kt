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