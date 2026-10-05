package me.voltual.a321.data.service

import android.content.Context
import android.net.Uri
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.coroutines.delay
import me.voltual.a321.KtorClient
import me.voltual.a321.data.unified.*
import me.voltual.a321.utils.PanUtils
import okio.buffer
import okio.source

class Pan123Service(private val token: String) : PanService {
  override val platform: PanPlatform = PanPlatform.PAN123
  private val apiService = KtorClient.ApiServiceImpl
  private val CHUNK_SIZE = 5 * 1024 * 1024L

  override suspend fun getFiles(parentId: String, page: Int): Result<List<PanFile>> = runCatching {
    val idLong = parentId.toLongOrNull() ?: 0L
    val response = apiService.getFileList(token, page, idLong).getOrThrow()
    response.data?.InfoList?.toUnifiedList() ?: emptyList()
  }

  override suspend fun getFilesWithTotal(parentId: String, page: Int): Result<PanPageResult> = runCatching {
    val idLong = parentId.toLongOrNull() ?: 0L
    val response = apiService.getFileList(token, page, idLong).getOrThrow()
    val total = response.data?.Total ?: 0
    val files = response.data?.InfoList?.toUnifiedList() ?: emptyList()

    PanPageResult(
      files = files,
      totalCount = total,
      hasMore = files.size >= 100,
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
    val idLong = parentId.toLongOrNull() ?: 0L
    val md5 = PanUtils.calcMd5(context, uri)
    val reqRes = apiService.requestUpload(token, idLong, fileName, fileSize, md5).getOrThrow()
    val uploadInfo = reqRes.data ?: throw Exception("Upload request failed")

    if (uploadInfo.Reuse) {
      onProgress(1.0f)
      return@runCatching "秒传成功"
    }

    val bucket = uploadInfo.Bucket!!
    val key = uploadInfo.Key!!
    val uploadId = uploadInfo.UploadId!!
    val storageNode = uploadInfo.StorageNode!!
    val fileId = uploadInfo.FileId!!
    val totalParts = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()

    context.contentResolver.openInputStream(uri)?.source()?.buffer()?.use { source ->
      for (partNumber in 1..totalParts) {
        val chunk = source.readByteArray(minOf(CHUNK_SIZE, fileSize - (partNumber - 1) * CHUNK_SIZE))
        val urlRes = apiService.getS3PartUrls(token, bucket, key, uploadId, storageNode, partNumber).getOrThrow()
        val uploadUrl = urlRes.data?.presignedUrls?.get(partNumber.toString())
          ?: throw Exception("Failed to get S3 URL")

        val putResponse = KtorClient.httpClient.put(uploadUrl) { setBody(chunk) }
        if (!putResponse.status.isSuccess()) {
          throw IOException("S3 Upload failed at part $partNumber")
        }

        onProgress(partNumber.toFloat() / totalParts * 0.9f)
      }
    } ?: throw Exception("Failed to open Uri source")

    apiService.completeS3Upload(token, bucket, key, uploadId, storageNode).getOrThrow()
    delay(1000)
    apiService.confirmUpload(token, fileId).getOrThrow()

    onProgress(1.0f)
    "上传成功"
  }

  override suspend fun getDownloadUrl(file: PanFile): Result<String> = runCatching {
    val tempInfo = KtorClient.FileInfo(
      FileId = file.id.toLongOrNull() ?: 0L,
      FileName = file.name,
      Type = if (file.isDirectory) 1 else 0,
      Size = file.size,
      Etag = file.etag,
      S3KeyFlag = file.s3KeyFlag,
    )
    apiService.getDownloadUrl(token, tempInfo).getOrThrow()
  }

  override suspend fun getUserQuota(): Result<PanUserQuota> = runCatching {
    val response = apiService.getUserInfo(token).getOrThrow()
    val data = response.data ?: throw Exception("Failed to get user info")
    data.toUnifiedQuota()
  }

  override suspend fun deleteFiles(fileIds: List<String>): PanActionResult {
    val idsLong = fileIds.mapNotNull { it.toLongOrNull() }
    return apiService.deleteFiles(token, idsLong).toActionResult()
  }

  override suspend fun createFolder(name: String, parentId: String): PanActionResult {
    return runCatching {
      val idLong = parentId.toLongOrNull() ?: 0L
      val response = apiService.createFolder(token, name, idLong).getOrThrow()
      if (response.isSuccess) {
        PanActionResult.Success
      } else {
        PanActionResult.Error(response.code, response.message)
      }
    }.getOrElse { PanActionResult.Error(-1, it.message ?: "创建目录失败") }
  }

  override suspend fun moveFiles(fileIds: List<String>, targetParentId: String): PanActionResult {
    return runCatching {
      val idsLong = fileIds.mapNotNull { it.toLongOrNull() }
      val targetLong = targetParentId.toLongOrNull() ?: 0L
      val response = apiService.moveFiles(token, idsLong, targetLong).getOrThrow()
      if (response.isSuccess) {
        PanActionResult.Success
      } else {
        PanActionResult.Error(response.code, response.message)
      }
    }.getOrElse { PanActionResult.Error(-1, it.message ?: "移动文件失败") }
  }

  override suspend fun renameFile(fileId: String, newName: String): PanActionResult {
    return runCatching {
      val idLong = fileId.toLongOrNull() ?: 0L
      val response = apiService.renameFile(token, idLong, newName).getOrThrow()
      if (response.isSuccess) {
        PanActionResult.Success
      } else {
        PanActionResult.Error(response.code, response.message)
      }
    }.getOrElse { PanActionResult.Error(-1, it.message ?: "重命名失败") }
  }

  override suspend fun shareFiles(
    fileIds: List<String>,
    password: String,
    expiration: String
  ): Result<String> = runCatching {
    val idsLong = fileIds.mapNotNull { it.toLongOrNull() }
    val response = apiService.createShare(token, idsLong, password, expiration).getOrThrow()
    val shareKey = response.data?.ShareKey ?: throw Exception("分享失败：未获取到 Key")
    val link = "https://www.123pan.com/s/$shareKey"
    if (password.isNotBlank()) {
      "$link 提取码: $password"
    } else {
      link
    }
  }

  override suspend fun getRecycleBinFiles(page: Int): Result<PanPageResult> = runCatching {
    val response = apiService.listRecycle(token, page).getOrThrow()
    val data = response.data ?: throw Exception("Empty recycle bin")
    data.toPageResult()
  }

  override suspend fun restoreFiles(fileIds: List<String>): PanActionResult {
    return runCatching {
      val idsLong = fileIds.mapNotNull { it.toLongOrNull() }
      val response = apiService.restoreFiles(token, idsLong).toActionResult()
      if (response is PanActionResult.Success) {
        PanActionResult.Success
      } else {
        response
      }
    }.getOrElse { PanActionResult.Error(-1, it.message ?: "恢复文件失败") }
  }

  override suspend fun deleteFilesPermanently(fileIds: List<String>): PanActionResult {
    return runCatching {
      val idsLong = fileIds.mapNotNull { it.toLongOrNull() }
      val response = apiService.deleteFilesPermanently(token, idsLong).getOrThrow()
      if (response.code == 0 || response.code == 7301) {
        PanActionResult.Success
      } else {
        PanActionResult.Error(response.code, response.message)
      }
    }.getOrElse { PanActionResult.Error(-1, it.message ?: "彻底删除失败") }
  }

  override suspend fun searchFiles(
    keyword: String,
    page: Int,
    parentId: String,
    limit: Int
  ): Result<PanPageResult> = runCatching {
    val idLong = parentId.toLongOrNull() ?: 0L
    val response = apiService.searchFiles(token, keyword, page, limit, idLong).getOrThrow()
    val total = response.data?.Total ?: 0
    val files = response.data?.InfoList?.toUnifiedList() ?: emptyList()
    PanPageResult(files = files, totalCount = total, hasMore = files.size >= limit)
  }

  override suspend fun getShareList(next: String?, limit: Int): Result<PanPageResult> = runCatching {
    val nextParam = if (next.isNullOrEmpty()) "0" else next
    val response = apiService.listShares(token, nextParam, limit).getOrThrow()
    val data = response.data ?: throw Exception("获取分享列表失败")
    data.toPageResult()
  }

  override suspend fun getShareInfo(
    shareKey: String,
    next: String?,
    parentId: String,
    page: Int,
    sharePwd: String?
  ): Result<PanPageResult> = runCatching {
    val nextParam = if (next.isNullOrEmpty()) "1" else next
    val idLong = parentId.toLongOrNull() ?: 0L
    val response = apiService.getShareInfo(token, shareKey, nextParam, page, 200, idLong, sharePwd).getOrThrow()
    val data = response.data ?: throw Exception("获取分享信息失败")
    data.toPageResult()
  }

  override suspend fun deleteShare(shareId: String): PanActionResult = runCatching {
    val idLong = shareId.toLongOrNull() ?: 0L
    val response = apiService.deleteShare(token, listOf(idLong)).getOrThrow()
    if (response.isSuccess) {
      PanActionResult.Success
    } else {
      PanActionResult.Error(response.code, response.message)
    }
  }.getOrElse { PanActionResult.Error(-1, it.message ?: "取消分享失败") }

  override suspend fun copyShareFiles(
    shareKey: String,
    sharePwd: String,
    targetParentId: String,
    files: List<PanFile>
  ): PanActionResult = runCatching {
    val copyInfos = files.map { it.toCopyFileInfo(targetParentId) }
    val response = apiService.copyShareFile(token, shareKey, sharePwd, targetParentId.toLongOrNull() ?: 0L, copyInfos).getOrThrow()
    if (response.isSuccess) {
      PanActionResult.Success
    } else {
      PanActionResult.Error(response.code, response.message)
    }
  }.getOrElse { PanActionResult.Error(-1, it.message ?: "复制文件失败") }
  
  override fun parseExternalShareKey(url: String): String? {
    val key = url.substringAfterLast("/s/", "").substringBefore("?").substringBefore("/").trim()
    return key.ifEmpty { null }
  }
}