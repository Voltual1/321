package me.voltual.a321.data.unified

import me.voltual.a321.KtorClient

fun KtorClient.FileInfo.toUnifiedModel(): PanFile {
  return PanFile(
    id = this.FileId.toString(),
    name = this.FileName,
    size = this.Size,
    isDirectory = this.isDirectory,
    updateTime = this.UpdateAt,
    category = this.Category,
    isAbnormal = this.isAbnormal,
    etag = this.Etag,
    s3KeyFlag = this.S3KeyFlag,
    rawDownloadUrl = this.DownloadUrl,
  )
}

fun List<KtorClient.FileInfo>.toUnifiedList(): List<PanFile> {
  return this.map { it.toUnifiedModel() }
}

fun KtorClient.UserInfo.toUnifiedQuota(): PanUserQuota {
  return PanUserQuota(
    userId = this.UID.toString(),
    nickname = this.Nickname,
    avatarUrl = this.HeadImage.takeIf { it.isNotBlank() },
    usedBytes = this.SpaceUsed,
    totalBytes = this.SpacePermanent + this.SpaceTemp,
    isVip = false,
    expireTime = this.SpaceTempExpr.takeIf { it.isNotBlank() },
  )
}

fun KtorClient.FolderDetailsData.toUnifiedModel(): PanFile {
  return PanFile(
    id = this.FileId.toString(),
    name = this.FileName,
    size = this.Size ?: 0L,
    isDirectory = true,
    updateTime = "",
    rawDownloadUrl = "",
    etag = "",
    category = 1,
  )
}

fun KtorClient.FileListData.toPageResult(): PanPageResult {
  return PanPageResult(
    files = this.InfoList.toUnifiedList(),
    totalCount = this.Total,
    hasMore = this.InfoList.isNotEmpty(),
  )
}

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
    },
  )
}

// 映射 123 云盘分享条目 (修复 shareUrl 赋值)
fun KtorClient.ShareInfo.toUnifiedModel(): PanFile {
  val primaryLink = this.shareLinkList.list.firstOrNull()
    ?: this.ShareUrl.takeIf { it.isNotBlank() }
    ?: "https://www.123pan.com/s/${this.ShareKey}"

  return PanFile(
    id = this.ShareId.toString(),
    name = this.ShareName,
    size = this.bytesTotal,
    isDirectory = false,
    updateTime = this.UpdateAt,
    category = 10,
    isAbnormal = this.isViolation == 1,
    etag = "",
    s3KeyFlag = null,
    rawDownloadUrl = primaryLink,
    extension = "",
    shareKey = this.ShareKey,
    sharePwd = this.SharePwd,
    expiration = this.Expiration,
    shareUrl = primaryLink,
  )
}

fun KtorClient.ShareListData.toPageResult(): PanPageResult {
  return PanPageResult(
    files = this.InfoList.map { it.toUnifiedModel() },
    totalCount = this.Total,
    hasMore = this.Next != "-1" && this.Next.toIntOrNull() != -1,
    nextMarker = if (this.Next != "-1" && this.Next != "0") this.Next else null,
  )
}

fun KtorClient.ShareGetResponseData.toPageResult(): PanPageResult {
  return PanPageResult(
    files = this.InfoList.toUnifiedList(),
    totalCount = this.Len,
    hasMore = this.Next != "-1" && this.Len > 0,
    nextMarker = if (this.Next != "-1") this.Next else null,
  )
}

fun PanFile.toCopyFileInfo(targetParentId: String = "0"): KtorClient.CopyFileInfo {
  return KtorClient.CopyFileInfo(
    driveId = 0,
    duplicate = 2,
    etag = this.etag,
    fileId = this.id.toLongOrNull() ?: 0L,
    fileName = this.name,
    parentFileId = targetParentId.toLongOrNull() ?: 0L,
    size = this.size,
    type = if (this.isDirectory) 1 else 0,
  )
}