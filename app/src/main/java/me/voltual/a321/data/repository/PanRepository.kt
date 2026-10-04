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

    val activePlatformId = credentials.activePlatform.ifEmpty {
      if (rawToken.startsWith("cloud139|") || rawToken.startsWith("Basic ")) PanPlatform.CLOUD139.id else PanPlatform.PAN123.id
    }

    val platform = PanPlatform.fromId(activePlatformId)

    val tokenValue = when (platform) {
      PanPlatform.CLOUD139 -> credentials.tokenCloud139.ifEmpty { rawToken.removePrefix("cloud139|") }
      PanPlatform.PAN123 -> credentials.token123Pan.ifEmpty { rawToken.removePrefix("123pan|") }
    }

    if (tokenValue.isEmpty()) throw Exception(LOGIN_REQUIRED)

    return when (platform) {
      PanPlatform.CLOUD139 -> Cloud139Service(tokenValue)
      PanPlatform.PAN123 -> Pan123Service(tokenValue)
    }
  }

  suspend fun getFiles(parentId: Long = 0, page: Int = 1): Result<List<PanFile>> = runCatching {
    getActiveService().getFiles(parentId, page).getOrThrow()
  }

  suspend fun getFilesWithTotal(parentId: Long = 0, page: Int = 1): Result<PanPageResult> = runCatching {
    getActiveService().getFilesWithTotal(parentId, page).getOrThrow()
  }

  suspend fun uploadFile(
    uri: Uri,
    fileName: String,
    fileSize: Long,
    parentId: Long,
    onProgress: (Float) -> Unit,
  ): Result<String> = runCatching {
    getActiveService().uploadFile(context, uri, fileName, fileSize, parentId, onProgress).getOrThrow()
  }

  suspend fun getDownloadUrl(file: PanFile): Result<String> = runCatching {
    getActiveService().getDownloadUrl(file).getOrThrow()
  }

  suspend fun getUserQuota(): Result<PanUserQuota> = runCatching {
    getActiveService().getUserQuota().getOrThrow()
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
  ): Result<String> = runCatching {
    getActiveService().shareFiles(fileIds, password, expiration).getOrThrow()
  }

  suspend fun getRecycleBinFiles(page: Int = 1): Result<PanPageResult> = runCatching {
    getActiveService().getRecycleBinFiles(page).getOrThrow()
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
  ): Result<PanPageResult> = runCatching {
    getActiveService().searchFiles(keyword, page, parentId, limit).getOrThrow()
  }

  suspend fun getShareList(next: String? = null, limit: Int = 100): Result<PanPageResult> = runCatching {
    getActiveService().getShareList(next, limit).getOrThrow()
  }

  suspend fun getShareInfo(
    shareKey: String,
    next: String? = "1",
    parentId: Long = 0,
    page: Int = 1,
    sharePwd: String? = null,
  ): Result<PanPageResult> = runCatching {
    getActiveService().getShareInfo(shareKey, next, parentId, page, sharePwd).getOrThrow()
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