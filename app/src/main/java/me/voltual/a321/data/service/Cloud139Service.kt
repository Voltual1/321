package me.voltual.a321.data.service

import android.content.Context
import android.net.Uri
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import me.voltual.a321.Cloud139Client
import me.voltual.a321.data.unified.*
import me.voltual.a321.utils.PanUtils
import okio.buffer
import okio.source

class Cloud139Service(private val token: String) : PanService {
  override val platform: PanPlatform = PanPlatform.CLOUD139
  private val CHUNK_SIZE = 104857600L

  init {
    Cloud139Client.login(token)
  }

  private fun Cloud139Client.PersonalFileItem.toUnifiedFile(): PanFile {
    val rawId = this.fileId ?: ""
    return PanFile(
      id = rawId,
      name = this.name ?: "",
      size = this.size ?: 0L,
      isDirectory = this.isFolder,
      updateTime = this.updatedAt ?: this.updateDate ?: "",
      etag = this.contentHash ?: "",
      rawDownloadUrl = "",
      shareKey = rawId
    )
  }

  private fun Cloud139Client.OutLinkItem.toUnifiedFile(): PanFile {
    val linkId = this.linkID ?: ""
    val shareUrlStr = this.url ?: "https://yun.139.com/shareweb/#/w/i/$linkId"
    return PanFile(
      id = linkId,
      name = this.lkName ?: "文件分享",
      size = 0L,
      isDirectory = false,
      updateTime = this.ctTime ?: this.lastUdTime ?: "",
      category = 10,
      etag = "",
      rawDownloadUrl = shareUrlStr,
      shareKey = linkId,
      sharePwd = this.passwd,
      expiration = this.expireTime?.ifEmpty { "永久有效" } ?: "永久有效",
      shareUrl = shareUrlStr
    )
  }

  override suspend fun getFiles(parentId: String, page: Int): Result<List<PanFile>> = runCatching {
    val parentStr = if (parentId == "0" || parentId.isEmpty()) "/" else parentId
    val data = Cloud139Client.listPersonalFiles(parentStr).getOrThrow()
    data.items.map { it.toUnifiedFile() }
  }

  override suspend fun getFilesWithTotal(parentId: String, page: Int): Result<PanPageResult> = runCatching {
    val parentStr = if (parentId == "0" || parentId.isEmpty()) "/" else parentId
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
    parentId: String,
    onProgress: (Float) -> Unit
  ): Result<String> = runCatching {
    val sha256 = PanUtils.calcSha256(context, uri)
    val parentStr = if (parentId == "0" || parentId.isEmpty()) "/" else parentId

    val initResp = Cloud139Client.initUpload(parentStr, fileName, fileSize, sha256, CHUNK_SIZE).getOrThrow()
    if (initResp.data == null) {
      onProgress(1.0f)
      return@runCatching "上传完成"
    }

    val data = initResp.data
    val fileId = data.fileId ?: ""
    val uploadId = data.uploadId ?: ""

    if (data.exist == true || data.rapidUpload == true || data.partInfos.isNullOrEmpty()) {
      onProgress(1.0f)
      return@runCatching "秒传成功"
    }

    val partCount = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()

    val uploadUrls = Cloud139Client.getUploadUrls(fileId, uploadId, partCount, CHUNK_SIZE, fileSize).getOrThrow()

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

    Cloud139Client.confirmUpload(fileId, uploadId, sha256).getOrThrow()
    onProgress(1.0f)
    "上传成功"
  }

  override suspend fun shareFiles(
    fileIds: List<String>,
    password: String,
    expiration: String
  ): Result<String> = runCatching {
    val title = if (fileIds.size > 1) "已选择 ${fileIds.size} 个文件" else "文件分享"
    
    val days = if (expiration == "PERMANENT") {
      null
    } else {
      runCatching {
        val expireDateTime = LocalDateTime.parse(expiration.substringBefore("+").substringBefore("Z"))
        val now = LocalDateTime.now()
        val diff = Duration.between(now, expireDateTime).toDays().toInt()
        maxOf(1, diff)
      }.getOrNull()
    }

    val outLinkSet = Cloud139Client.createOutLink(fileIds, title, days).getOrThrow()
    val url = outLinkSet.linkUrl ?: throw IOException("生成的分享链接为空")
    val code = outLinkSet.passwd

    if (!code.isNullOrEmpty()) {
      "$url 提取码: $code"
    } else {
      url
    }
  }

  override suspend fun getShareList(next: String?, limit: Int): Result<PanPageResult> = runCatching {
    val page = next?.toIntOrNull() ?: 1
    val startNum = (page - 1) * limit + 1
    val stopNum = page * limit
    val res = Cloud139Client.getOutLinkList(bNum = startNum, eNum = stopNum).getOrThrow()

    val files = res.outLinks.map { it.toUnifiedFile() }
    val totalCount = res.count?.toIntOrNull() ?: files.size

    PanPageResult(
      files = files,
      totalCount = totalCount,
      hasMore = stopNum < totalCount,
      nextMarker = if (stopNum < totalCount) (page + 1).toString() else "-1"
    )
  }

  override suspend fun deleteShare(shareId: String): PanActionResult {
    return Cloud139Client.delOutLink(listOf(shareId)).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "取消分享失败") }
    )
  }

  override suspend fun getRecycleBinFiles(page: Int): Result<PanPageResult> = runCatching {
    val data = Cloud139Client.getRecycleBinList().getOrThrow()
    val files = data.items.map { it.toUnifiedFile() }

    PanPageResult(
      files = files,
      totalCount = files.size,
      hasMore = false
    )
  }

  override suspend fun restoreFiles(fileIds: List<String>): PanActionResult {
    return Cloud139Client.restoreRecycleBinFiles(fileIds).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "恢复文件失败") }
    )
  }

  override suspend fun deleteFilesPermanently(fileIds: List<String>): PanActionResult {
    return Cloud139Client.deleteRecycleBinFilesPermanently(fileIds).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "彻底删除失败") }
    )
  }

  override suspend fun getShareInfo(
    shareKey: String,
    next: String?,
    parentId: String,
    page: Int,
    sharePwd: String?
  ): Result<PanPageResult> = runCatching {
    val parentCaId = if (parentId == "0" || parentId.isEmpty()) "root" else parentId
    val pwd = sharePwd ?: ""
    val resp = Cloud139Client.getOutLinkInfo(shareKey, pwd, parentCaId).getOrThrow()
    val data = resp.data ?: throw IOException("返回的外链数据为空")

    val folderFiles = data.caLst?.map { it.toPersonalFileItem().toUnifiedFile() } ?: emptyList()
    val regularFiles = data.coLst?.map { it.toPersonalFileItem().toUnifiedFile() } ?: emptyList()
    val allFiles = folderFiles + regularFiles

    PanPageResult(
      files = allFiles,
      totalCount = data.nodNum ?: allFiles.size,
      hasMore = false
    )
  }

  override suspend fun searchFiles(
    keyword: String,
    page: Int,
    parentId: String,
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
    val fileId = file.shareKey.takeIf { !it.isNullOrEmpty() } ?: file.id
    return Cloud139Client.getDownloadUrl(fileId)
  }

  override suspend fun createFolder(name: String, parentId: String): PanActionResult {
    val parentStr = if (parentId == "0" || parentId.isEmpty()) "/" else parentId
    return Cloud139Client.createFolder(parentStr, name).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "创建目录失败") }
    )
  }

  override suspend fun deleteFiles(fileIds: List<String>): PanActionResult {
    return Cloud139Client.deleteFiles(fileIds).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "删除文件失败") }
    )
  }

  override suspend fun renameFile(fileId: String, newName: String): PanActionResult {
    return Cloud139Client.renameFile(fileId, newName).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "重命名失败") }
    )
  }

  override suspend fun moveFiles(fileIds: List<String>, targetParentId: String): PanActionResult {
    val targetStr = if (targetParentId == "0" || targetParentId.isEmpty()) "/" else targetParentId
    return Cloud139Client.moveFiles(fileIds, targetStr).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "移动失败") }
    )
  }

  override suspend fun copyShareFiles(
    shareKey: String,
    sharePwd: String,
    targetParentId: String,
    files: List<PanFile>
  ): PanActionResult {
    val ids = files.map { it.shareKey.takeIf { k -> !k.isNullOrEmpty() } ?: it.id }
    val targetStr = if (targetParentId == "0" || targetParentId.isEmpty()) "/" else targetParentId
    return Cloud139Client.copyFiles(ids, targetStr).fold(
      onSuccess = { PanActionResult.Success },
      onFailure = { PanActionResult.Error(-1, it.message ?: "复制失败") }
    )
  }
  
  override fun parseExternalShareKey(url: String): String? {
    val key = url.substringAfterLast("/i/", "").substringBefore("?").substringBefore("/").trim()
    return key.ifEmpty { null }
  }
}