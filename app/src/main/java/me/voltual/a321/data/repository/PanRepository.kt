package me.voltual.a321.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import me.voltual.a321.AuthManager
import me.voltual.a321.data.service.Cloud139Service
import me.voltual.a321.data.service.Pan123Service
import me.voltual.a321.data.service.PanService
import me.voltual.a321.data.unified.*

const val LOGIN_REQUIRED = "你可能没有登录哦o_O，先点击左上角的按钮把侧边栏抽屉拉出来看看吧"

class PanRepository(private val context: Context) {

  private suspend fun getActiveService(): PanService {
    val credentials = AuthManager.getCredentials(context).first()
    val rawToken = credentials.token
    if (rawToken.isEmpty()) throw Exception(LOGIN_REQUIRED)

    return when {
      rawToken.startsWith("cloud139|") -> {
        Cloud139Service(rawToken.removePrefix("cloud139|"))
      }
      rawToken.startsWith("Basic ") -> {
        Cloud139Service(rawToken)
      }
      else -> {
        Pan123Service(rawToken.removePrefix("123pan|"))
      }
    }
  }

  suspend fun getFiles(parentId: Long = 0, page: Int = 1): Result<List<PanFile>> {
    return getActiveService().getFiles(parentId, page)
  }

  suspend fun getFilesWithTotal(parentId: Long = 0, page: Int = 1): Result<PanPageResult> {
    return getActiveService().getFilesWithTotal(parentId, page)
  }

  suspend fun uploadFile(
    uri: Uri,
    fileName: String,
    fileSize: Long,
    parentId: Long,
    onProgress: (Float) -> Unit,
  ): Result<String> {
    return getActiveService().uploadFile(context, uri, fileName, fileSize, parentId, onProgress)
  }

  suspend fun getDownloadUrl(file: PanFile): Result<String> {
    return getActiveService().getDownloadUrl(file)
  }

  suspend fun getUserQuota(): Result<PanUserQuota> {
    return getActiveService().getUserQuota()
  }

  suspend fun deleteFiles(fileIds: List<Long>): PanActionResult {
    return runCatching { getActiveService().deleteFiles(fileIds) }
      .getOrElse { PanActionResult.Error(-1, it.message ?: "删除失败") }
  }

  suspend fun createFolder(name: String, parentId: Long): PanActionResult {
    return runCatching { getActiveService().createFolder(name, parentId) }
      .getOrElse { PanActionResult.Error(-1, it.message ?: "新建文件夹失败") }
  }

  suspend fun moveFiles(fileIds: List<Long>, targetParentId: Long): PanActionResult {
    return runCatching { getActiveService().moveFiles(fileIds, targetParentId) }
      .getOrElse { PanActionResult.Error(-1, it.message ?: "移动失败") }
  }

  suspend fun renameFile(fileId: Long, newName: String): PanActionResult {
    return runCatching { getActiveService().renameFile(fileId, newName) }
      .getOrElse { PanActionResult.Error(-1, it.message ?: "重命名失败") }
  }

  suspend fun shareFiles(
    fileIds: List<Long>,
    password: String = "",
    expiration: String = "",
  ): Result<String> {
    return getActiveService().shareFiles(fileIds, password, expiration)
  }

  suspend fun getRecycleBinFiles(page: Int = 1): Result<PanPageResult> {
    return getActiveService().getRecycleBinFiles(page)
  }

  suspend fun restoreFiles(fileIds: List<Long>): PanActionResult {
    return runCatching { getActiveService().restoreFiles(fileIds) }
      .getOrElse { PanActionResult.Error(-1, it.message ?: "恢复失败") }
  }

  suspend fun deleteFilesPermanently(fileIds: List<Long>): PanActionResult {
    return runCatching { getActiveService().deleteFilesPermanently(fileIds) }
      .getOrElse { PanActionResult.Error(-1, it.message ?: "彻底删除失败") }
  }

  suspend fun searchFiles(
    keyword: String,
    page: Int = 1,
    parentId: Long = 0,
    limit: Int = 100,
  ): Result<PanPageResult> {
    return getActiveService().searchFiles(keyword, page, parentId, limit)
  }

  suspend fun getShareList(next: String? = null, limit: Int = 100): Result<PanPageResult> {
    return getActiveService().getShareList(next, limit)
  }

  suspend fun getShareInfo(
    shareKey: String,
    next: String? = "1",
    parentId: Long = 0,
    page: Int = 1,
    sharePwd: String? = null,
  ): Result<PanPageResult> {
    return getActiveService().getShareInfo(shareKey, next, parentId, page, sharePwd)
  }

  suspend fun deleteShare(shareId: Long): PanActionResult {
    return runCatching { getActiveService().deleteShare(shareId) }
      .getOrElse { PanActionResult.Error(-1, it.message ?: "取消分享失败") }
  }

  suspend fun copyShareFiles(
    shareKey: String,
    sharePwd: String,
    targetParentId: Long,
    files: List<PanFile>,
  ): PanActionResult {
    return runCatching { getActiveService().copyShareFiles(shareKey, sharePwd, targetParentId, files) }
      .getOrElse { PanActionResult.Error(-1, it.message ?: "转存失败") }
  }
}