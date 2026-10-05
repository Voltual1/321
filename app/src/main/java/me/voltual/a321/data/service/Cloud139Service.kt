package me.voltual.a321.data.service

import android.content.Context
import android.net.Uri
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import java.io.IOException
import me.voltual.a321.Cloud139Client
import me.voltual.a321.data.unified.*
import me.voltual.a321.utils.PanUtils
import okio.buffer
import okio.source

class Cloud139Service(private val token: String) : PanService {
  override val platform: PanPlatform = PanPlatform.CLOUD139
  private val CHUNK_SIZE = 104857600L // 100MB 默认分片

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

  override suspend fun uploadFile(
    context: Context,
    uri: Uri,
    fileName: String,
    fileSize: Long,
    parentId: Long,
    onProgress: (Float) -> Unit
  ): Result<String> = runCatching {
    val sha256 = PanUtils.calcSha256(context, uri)
    val parentStr = if (parentId == 0L) "/" else parentId.toString()

    // 1. 初始化上传任务
    val initResp = Cloud139Client.initUpload(parentStr, fileName, fileSize, sha256, CHUNK_SIZE).getOrThrow()
    if (initResp.data == null) {
      onProgress(1.0f)
      return@runCatching "上传完成"
    }

    val data = initResp.data
    val fileId = data.fileId ?: ""
    val uploadId = data.uploadId ?: ""

    // 秒传或无需上传分片
    if (data.exist == true || data.rapidUpload == true || data.partInfos.isNullOrEmpty()) {
      onProgress(1.0f)
      return@runCatching "秒传成功"
    }

    val partCount = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()

    // 2. 获取分片上传预签名 URL 映射
    val uploadUrls = Cloud139Client.getUploadUrls(fileId, uploadId, partCount, CHUNK_SIZE, fileSize).getOrThrow()

    // 3. 读取本地分片并通过 HTTP PUT 上传
    context.contentResolver.openInputStream(uri)?.source()?.buffer()?.use { source ->
      for (partNum in 1..partCount) {
        val readSize = minOf(CHUNK_SIZE, fileSize - (partNum - 1) * CHUNK_SIZE)
        val chunk = source.readByteArray(readSize)
        val uploadUrl = uploadUrls[partNum] ?: throw IOException("缺失分片 $partNum 的上传链接")

        val putResp = Cloud139Client.httpClient.put(uploadUrl) {
          header("Content-Type", "application/octet-stream")
          header("Content-Length", readSize.toString())
          setBody(chunk)
        }

        if (!putResp.status.isSuccess()) {
          throw IOException("分片 $partNum 上传失败: HTTP ${putResp.status.value}")
        }

        onProgress(partNum.toFloat() / partCount * 0.9f)
      }
    } ?: throw IOException("无法读取文件内容")

    // 4. 确认上传完成
    Cloud139Client.confirmUpload(fileId, uploadId, sha256).getOrThrow()
    onProgress(1.0f)
    "上传成功"
  }

  override suspend fun searchFiles(
    keyword: String,
    page: Int,
    parentId: Long,
    limit: Int
  ): Result<PanPageResult> = runCatching {
    val startNum = (page - 1) * limit + 1
    val stopNum = page * limit
    val resp = Cloud139Client.searchFiles(keyword, startNum, stopNum).getOrThrow()
    val files = resp.rows.map { it.toPersonalFileItem().toUnifiedFile() }
    val total = resp.total ?: files.size

    PanPageResult(
      files = files,
      totalCount = total,
      hasMore = stopNum < total
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

  override suspend fun copyShareFiles(
    shareKey: String,
    sharePwd: String,
    targetParentId: Long,
    files: List<PanFile>
  ): PanActionResult {
    val ids = files.map { it.shareKey.takeIf { k -> !k.isNullOrEmpty() } ?: it.id.toString() }
    val targetStr = if (targetParentId == 0L) "/" else targetParentId.toString()
    return Cloud139Client.copyFiles(ids, targetStr).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "复制失败") }
    )
  }
}