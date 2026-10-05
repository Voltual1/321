package me.voltual.a321.data.service

import android.content.Context
import android.net.Uri
import me.voltual.a321.data.unified.*

interface PanService {
  val platform: PanPlatform

  suspend fun getFiles(parentId: String = "0", page: Int = 1): Result<List<PanFile>> =
    Result.failure(UnsupportedOperationException("${platform.displayName} 暂不支持此功能"))

  suspend fun getFilesWithTotal(parentId: String = "0", page: Int = 1): Result<PanPageResult> =
    Result.failure(UnsupportedOperationException("${platform.displayName} 暂不支持此功能"))

  suspend fun uploadFile(
    context: Context,
    uri: Uri,
    fileName: String,
    fileSize: Long,
    parentId: String,
    onProgress: (Float) -> Unit,
  ): Result<String> =
    Result.failure(UnsupportedOperationException("${platform.displayName} 暂不支持上传功能"))

  suspend fun getDownloadUrl(file: PanFile): Result<String> =
    Result.failure(UnsupportedOperationException("${platform.displayName} 暂不支持直链获取"))

  suspend fun getUserQuota(): Result<PanUserQuota> =
    Result.failure(UnsupportedOperationException("${platform.displayName} 暂不支持用户容量查询"))

  suspend fun deleteFiles(fileIds: List<String>): PanActionResult =
    PanActionResult.Error(-1, "${platform.displayName} 暂不支持删除文件")

  suspend fun createFolder(name: String, parentId: String): PanActionResult =
    PanActionResult.Error(-1, "${platform.displayName} 暂不支持新建目录")

  suspend fun moveFiles(fileIds: List<String>, targetParentId: String): PanActionResult =
    PanActionResult.Error(-1, "${platform.displayName} 暂不支持移动文件")

  suspend fun renameFile(fileId: String, newName: String): PanActionResult =
    PanActionResult.Error(-1, "${platform.displayName} 暂不支持重命名")

  suspend fun shareFiles(
    fileIds: List<String>,
    password: String = "",
    expiration: String = "",
  ): Result<String> =
    Result.failure(UnsupportedOperationException("${platform.displayName} 暂不支持创建分享"))

  suspend fun getRecycleBinFiles(page: Int = 1): Result<PanPageResult> =
    Result.failure(UnsupportedOperationException("${platform.displayName} 暂不支持回收站"))

  suspend fun restoreFiles(fileIds: List<String>): PanActionResult =
    PanActionResult.Error(-1, "${platform.displayName} 暂不支持文件恢复")

  suspend fun deleteFilesPermanently(fileIds: List<String>): PanActionResult =
    PanActionResult.Error(-1, "${platform.displayName} 暂不支持彻底删除")

  suspend fun searchFiles(
    keyword: String,
    page: Int = 1,
    parentId: String = "0",
    limit: Int = 100,
  ): Result<PanPageResult> =
    Result.failure(UnsupportedOperationException("${platform.displayName} 暂不支持搜索文件"))

  suspend fun getShareList(next: String? = null, limit: Int = 100): Result<PanPageResult> =
    Result.failure(UnsupportedOperationException("${platform.displayName} 暂不支持分享列表"))

  suspend fun getShareInfo(
    shareKey: String,
    next: String? = "1",
    parentId: String = "0",
    page: Int = 1,
    sharePwd: String? = null,
  ): Result<PanPageResult> =
    Result.failure(UnsupportedOperationException("${platform.displayName} 暂不支持查看外部分享"))

  suspend fun deleteShare(shareId: String): PanActionResult =
    PanActionResult.Error(-1, "${platform.displayName} 暂不支持取消分享")

  suspend fun copyShareFiles(
    shareKey: String,
    sharePwd: String,
    targetParentId: String,
    files: List<PanFile>,
  ): PanActionResult =
    PanActionResult.Error(-1, "${platform.displayName} 暂不支持转存分享文件")
}