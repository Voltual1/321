package me.voltual.a321.data.service

import android.content.Context
import android.net.Uri
import java.io.IOException
import me.voltual.a321.Cloud139Client
import me.voltual.a321.data.unified.*

class Cloud139Service(private val token: String) : PanService {
  override val platform: PanPlatform = PanPlatform.CLOUD139

  init {
    Cloud139Client.login(token)
  }

  private fun fileIdToLong(fileId: String?): Long {
    if (fileId.isNullOrEmpty() || fileId == "/") return 0L
    return fileId.toLongOrNull() ?: fileId.hashCode().toLong()
  }

  private fun Cloud139Client.PersonalFileItem.toUnifiedFile(): PanFile {
    val rawId = this.fileId ?: ""
    return PanFile(
      id = fileIdToLong(rawId),
      name = this.name ?: "",
      size = this.size ?: 0L,
      isDirectory = this.isFolder,
      updateTime = this.updatedAt ?: this.updateDate ?: "",
      etag = this.contentHash ?: "",
      rawDownloadUrl = "",
      shareKey = rawId
    )
  }

  override suspend fun getFiles(parentId: Long, page: Int): Result<List<PanFile>> = runCatching {
    val parentStr = if (parentId == 0L) "/" else parentId.toString()
    val data = Cloud139Client.listPersonalFiles(parentStr).getOrThrow()
    data.items.map { it.toUnifiedFile() }
  }

  override suspend fun getFilesWithTotal(parentId: Long, page: Int): Result<PanPageResult> = runCatching {
    val parentStr = if (parentId == 0L) "/" else parentId.toString()
    val data = Cloud139Client.listPersonalFiles(parentStr).getOrThrow()
    val files = data.items.map { it.toUnifiedFile() }
    PanPageResult(
      files = files,
      totalCount = files.size,
      hasMore = !data.nextPageCursor.isNullOrEmpty()
    )
  }

  override suspend fun getDownloadUrl(file: PanFile): Result<String> {
    val fileId = file.shareKey.takeIf { !it.isNullOrEmpty() } ?: file.id.toString()
    return Cloud139Client.getDownloadUrl(fileId)
  }

  override suspend fun createFolder(name: String, parentId: Long): PanActionResult {
    val parentStr = if (parentId == 0L) "/" else parentId.toString()
    return Cloud139Client.createFolder(parentStr, name).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "创建目录失败") }
    )
  }

  override suspend fun deleteFiles(fileIds: List<Long>): PanActionResult {
    val ids = fileIds.map { it.toString() }
    return Cloud139Client.deleteFiles(ids).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "删除文件失败") }
    )
  }

  override suspend fun renameFile(fileId: Long, newName: String): PanActionResult {
    return Cloud139Client.renameFile(fileId.toString(), newName).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "重命名失败") }
    )
  }

  override suspend fun moveFiles(fileIds: List<Long>, targetParentId: Long): PanActionResult {
    val ids = fileIds.map { it.toString() }
    val targetStr = if (targetParentId == 0L) "/" else targetParentId.toString()
    return Cloud139Client.moveFiles(ids, targetStr).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "移动失败") }
    )
  }
}