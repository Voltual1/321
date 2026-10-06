// Copyright (C) 2025 Voltual
// Portions of 123Pan upload logic derived from OpenList (https://github.com/OpenListTeam/OpenList)
//
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 阿拉伯数字通用公共许可证第3版
// （AGPLv3 或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 强通用公共许可证。
//
// 你应该已经收到了一份 GNU 强通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package me.voltual.a321

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.voltual.a321.data.UpdateInfo
import me.voltual.a321.utils.PanUtils

object KtorClient {
  const val BASE_URL = "https://api.123278.com"
  private const val MAX_RETRIES = 3
  private const val RETRY_DELAY = 1000L
  private const val ANDROID_APP_VERSION = "61"
  private const val ANDROID_X_APP_VERSION = "2.4.0"

  val httpClient =
    HttpClient(OkHttp) {
      engine {
        config {
          writeTimeout(10, TimeUnit.MINUTES)
          readTimeout(5, TimeUnit.MINUTES)
          connectTimeout(30, TimeUnit.SECONDS)
        }
      }

      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            encodeDefaults = true
          }
        )
      }

      defaultRequest {
        url(BASE_URL)
        header(
          "user-agent",
          "123pan/v$ANDROID_X_APP_VERSION(${PanUtils.getFormattedOsVersion()};Android)",
        )
        header("platform", "android")
        header("app-version", ANDROID_APP_VERSION)
        header("x-app-version", ANDROID_X_APP_VERSION)
        header("devicetype", PanUtils.getDeviceModel())
        header("devicename", PanUtils.getDeviceBrand())
        header("osversion", PanUtils.getFormattedOsVersion())
        header("loginuuid", PanUtils.generateLoginUuid())
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
      }

      install(HttpTimeout) {
        requestTimeoutMillis = 10 * 60 * 1000L // 提升全局请求超时至 10 分钟
        connectTimeoutMillis = 30000L
        socketTimeoutMillis = 120000L
      }

      install(Logging) { level = LogLevel.INFO }

      followRedirects = false
    }

  // ===== 请求与响应模型定义 =====

  @Serializable
  data class LoginRequest(val type: Int = 1, val passport: String, val password: String)

  @Serializable
  data class WechatLoginRequest(
    val from: String = "web",
    val wechat_code: String,
    val type: Int = 4,
    val remember: Boolean = true,
    val gray: Boolean = true
  )

  @Serializable
  data class UploadRequest(
    val driveId: Int = 0,
    val parentFileId: Long,
    val fileName: String,
    val size: Long,
    val etag: String,
    val type: Int = 0,
    val duplicate: Int = 2,
  )

  @Serializable
  data class S3PartUrlsRequest(
    val bucket: String,
    val key: String,
    val partNumberStart: Int,
    val partNumberEnd: Int,
    val uploadId: String,
    val StorageNode: String,
  )

  @Serializable
  data class CompleteS3UploadV2Request(
    val StorageNode: String,
    val bucket: String,
    val fileId: Long,
    val fileSize: Long,
    val isMultipart: Boolean,
    val key: String,
    val uploadId: String,
  )

  @Serializable
  data class ConfirmUploadRequest(val fileId: String)

  @Serializable
  data class TrashRequest(
    val driveId: Int = 0,
    val fileTrashInfoList: List<TrashItem>,
    val operation: Boolean,
  )

  @Serializable data class TrashItem(val FileId: Long)

  @Serializable
  data class CreateFolderRequest(
    val driveId: Int = 0,
    val duplicate: Int = 1,
    val NotReuse: Boolean = true,
    val etag: String = "",
    val fileName: String,
    val parentFileId: Long,
    val size: Int = 0,
    val type: Int = 1,
  )

  @Serializable
  data class PanResponse<T>(val code: Int, val message: String, val data: T? = null) {
    val isSuccess: Boolean
      get() = code == 0 || code == 200
  }

  @Serializable data class FileListData(val Total: Int, val InfoList: List<FileInfo>)

  @Serializable
  data class UploadRequestData(
    val Reuse: Boolean,
    val FileId: String? = null,
    val UploadId: String? = null,
    val Bucket: String? = null,
    val Key: String? = null,
    val StorageNode: String? = null,
    val AccessKeyId: String? = null,
    val SecretAccessKey: String? = null,
    val SessionToken: String? = null,
    val EndPoint: String? = null,
  )

  @Serializable
  data class S3PartUrlsData(
    val presignedUrls: Map<String, String>
  )

  @Serializable
  data class FileInfo(
    val FileId: Long,
    val FileName: String,
    val Type: Int,
    val Size: Long,
    val Etag: String,
    val S3KeyFlag: String? = null,
    val Category: Int = 0,
    val DownloadUrl: String = "",
    val Status: Int = 0,
    val UpdateAt: String = "",
  ) {
    val isDirectory: Boolean
      get() = Type == 1

    val isAbnormal: Boolean
      get() = Status > 100
  }

  @Serializable data class DownloadData(val DownloadUrl: String)

  @Serializable data class LoginData(val token: String)

  @Serializable
  data class QrGenerateData(
    val url: String,
    val uniID: String
  )

  @Serializable
  data class QrResultData(
    val loginStatus: Int,
    val scanPlatform: Int,
    val login_type: Int,
    val token: String? = null
  )

  @Serializable
  data class WxCodeRequest(
    val uniID: String
  )

  @Serializable
  data class WxCodeData(
    val wxCode: String
  )

  @Serializable
  data class DownloadInfoRequest(
    val driveId: Int = 0,
    val fileId: Long,
    val etag: String,
    val fileName: String,
    val size: Long,
    val s3keyFlag: String?,
    val type: Int = 0,
  )

  @Serializable data class BatchDownloadRequest(val fileIdList: List<BatchDownloadItem>)

  @Serializable data class BatchDownloadItem(val fileId: Long)

  @Serializable
  data class UserInfo(
    val UID: Long,
    val Nickname: String,
    val SpaceUsed: Long,
    val SpacePermanent: Long,
    val SpaceTemp: Long = 0,
    val FileCount: Int,
    val SpaceTempExpr: String = "",
    val Mail: String = "",
    val Passport: String = "",
    val HeadImage: String = "",
  )

  @Serializable
  data class ShareCreateRequest(
    val driveId: Int = 0,
    val expiration: String,
    val fileIdList: String,
    val shareName: String = "分享文件",
    val sharePwd: String = "",
    val event: String = "shareCreate",
  )

  @Serializable data class ShareCreateData(val ShareKey: String)

  @Serializable
  data class FolderDetailsData(
    val FileId: Long,
    val FileName: String,
    val FileCount: Int? = null,
    val FolderCount: Int? = null,
    val Size: Long? = null,
  )

  @Serializable
  data class ShareListData(
    val Next: String,
    val Len: Int,
    val IsFirst: Boolean,
    val InfoList: List<ShareInfo>,
    val Total: Int,
  )

  @Serializable
  data class ShareInfo(
    val ShareId: Long,
    val ShareKey: String,
    val DriveId: Int,
    val FileIdList: String,
    val DownloadCount: Int,
    val PreviewCount: Int,
    val SaveCount: Int,
    val ShareName: String,
    val Expiration: String,
    val Expired: Boolean,
    val SharePwd: String,
    val Status: Int,
    val CreateAt: String,
    val UpdateAt: String,
    val bytesCharge: Long,
    val bytesTotal: Long,
    val isPayShare: Int,
    val isReward: Int,
    val auditStatus: Int,
    val amount: Int,
    val ShareUrl: String,
    val shareLinkList: ShareLinkList,
    val trafficSwitch: Int,
    val trafficLimitSwitch: Int,
    val trafficLimit: Int,
    val noLoginStdAmount: Int,
    val noLoginStdAmountDesc: String,
    val fillPwdSwitch: Int,
    val payAmount: Int,
    val shareMessage: String,
    val createStatus: Int,
    val createMsg: String,
    val isViolation: Int,
  )

  @Serializable data class ShareLinkList(val list: List<String>, val standBy: String)

  @Serializable
  data class MoveFileRequest(val fileIdList: List<MoveFileItem>, val parentFileId: Long)

  @Serializable data class MoveFileItem(val FileId: Long)

  @Serializable
  data class RenameRequest(val driveId: Int = 0, val fileName: String, val fileId: Long)

  @Serializable data class DeleteFileRequest(val fileIdList: List<DeleteFileItem>)

  @Serializable data class DeleteFileItem(val fileId: Long)

  @Serializable
  data class ShareGetResponseData(
    val Next: String?,
    val Len: Int,
    val IsFirst: Boolean,
    val Expired: Boolean,
    val IsPaidPreview: Boolean,
    val InfoList: List<FileInfo>,
  )

  @Serializable data class ShareDeleteItem(val shareid: Long)

  @Serializable
  data class ShareDeleteRequest(val driveId: Int = 0, val shareInfoList: List<ShareDeleteItem>)

  @Serializable data class ShareDeleteResponseData(val InfoList: List<ShareDeleteItemResult>)

  @Serializable data class ShareDeleteItemResult(val ShareId: Long)

  @Serializable
  data class CopyFileInfo(
    val driveId: Int = 0,
    val duplicate: Int = 2,
    val etag: String,
    val fileId: Long,
    val fileName: String,
    val parentFileId: Long,
    val size: Long,
    val type: Int,
  )

  @Serializable
  data class CopyShareRequest(
    val SharePwd: String = "",
    val shareKey: String,
    val fileInfoList: List<CopyFileInfo>,
  )

  // ===== API 接口定义 =====

  interface ApiService {
    suspend fun getLatestRelease(url: String): Result<UpdateInfo>

    suspend fun getFileList(
      token: String,
      page: Int,
      parentId: Long = 0,
    ): Result<PanResponse<FileListData>>

    suspend fun login(passport: String, password: String): Result<PanResponse<LoginData>>

    suspend fun getDownloadUrl(token: String, file: FileInfo): Result<String>

    suspend fun createFolder(token: String, name: String, parentId: Long): Result<PanResponse<Unit>>

    suspend fun deleteFiles(token: String, fileIds: List<Long>): Result<PanResponse<Unit>>

    suspend fun requestUpload(
      token: String,
      parentId: Long,
      fileName: String,
      size: Long,
      md5: String,
    ): Result<PanResponse<UploadRequestData>>

    suspend fun getS3Auth(
      token: String,
      bucket: String,
      key: String,
      uploadId: String,
      storageNode: String,
      start: Int = 1,
      end: Int = 2,
    ): Result<PanResponse<S3PartUrlsData>>

    suspend fun getS3PartUrls(
      token: String,
      bucket: String,
      key: String,
      uploadId: String,
      storageNode: String,
      start: Int,
      end: Int,
    ): Result<PanResponse<S3PartUrlsData>>

    suspend fun completeS3V2(
      token: String,
      bucket: String,
      key: String,
      uploadId: String,
      storageNode: String,
      fileId: Long,
      fileSize: Long,
      isMultipart: Boolean,
    ): Result<PanResponse<Unit>>

    suspend fun confirmUpload(token: String, fileId: String): Result<PanResponse<Unit>>

    suspend fun getUserInfo(token: String): Result<PanResponse<UserInfo>>

    suspend fun createShare(
      token: String,
      fileIds: List<Long>,
      sharePwd: String = "",
      expiration: String = "2099-12-12T08:00:00+08:00",
    ): Result<PanResponse<ShareCreateData>>

    suspend fun listRecycle(token: String, page: Int = 1): Result<PanResponse<FileListData>>

    suspend fun restoreFiles(token: String, fileIds: List<Long>): Result<PanResponse<Unit>>

    suspend fun deleteFilesPermanently(
      token: String,
      fileIds: List<Long>,
    ): Result<PanResponse<Unit>>

    suspend fun getFolderDetails(
      token: String,
      folderIds: List<Long>,
    ): Result<PanResponse<List<FolderDetailsData>>>

    suspend fun moveFiles(
      token: String,
      fileIds: List<Long>,
      targetParentId: Long,
    ): Result<PanResponse<Unit>>

    suspend fun renameFile(token: String, fileId: Long, newName: String): Result<PanResponse<Unit>>

    suspend fun searchFiles(
      token: String,
      keyword: String,
      page: Int = 1,
      limit: Int = 100,
      parentFileId: Long = 0,
    ): Result<PanResponse<FileListData>>

    suspend fun listShares(
      token: String,
      next: String? = null,
      limit: Int = 100,
      orderBy: String = "fileId",
      orderDirection: String = "desc",
      searchData: String? = null,
    ): Result<PanResponse<ShareListData>>

    suspend fun getShareInfo(
      token: String,
      shareKey: String,
      next: String? = "1",
      page: Int = 1,
      limit: Int = 200,
      parentFileId: Long = 0,
      sharePwd: String? = null,
    ): Result<PanResponse<ShareGetResponseData>>

    suspend fun deleteShare(
      token: String,
      shareIds: List<Long>,
    ): Result<PanResponse<ShareDeleteResponseData>>

    suspend fun copyShareFile(
      token: String,
      shareKey: String,
      sharePwd: String,
      targetParentId: Long,
      files: List<CopyFileInfo>,
    ): Result<PanResponse<Unit>>

    suspend fun generateQrCode(): Result<PanResponse<QrGenerateData>>

    suspend fun getQrCodeResult(uniID: String): Result<PanResponse<QrResultData>>

    suspend fun getWxCode(uniID: String): Result<PanResponse<WxCodeData>>

    suspend fun loginWithWechatCode(wxCode: String): Result<PanResponse<LoginData>>
  }

  object ApiServiceImpl : ApiService {

    override suspend fun getLatestRelease(url: String): Result<UpdateInfo> {
      return safeApiCall { httpClient.get(url) }
    }

    override suspend fun login(passport: String, password: String) =
      safeApiCall<PanResponse<LoginData>> {
        httpClient.post("/b/api/user/sign_in") {
          contentType(ContentType.Application.Json)
          setBody(LoginRequest(passport = passport, password = password))
        }
      }

    override suspend fun requestUpload(
      token: String,
      parentId: Long,
      fileName: String,
      size: Long,
      md5: String,
    ) =
      safeApiCall<PanResponse<UploadRequestData>> {
        httpClient.post("/b/api/file/upload_request") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(
            UploadRequest(parentFileId = parentId, fileName = fileName, size = size, etag = md5)
          )
        }
      }

    override suspend fun getS3Auth(
      token: String,
      bucket: String,
      key: String,
      uploadId: String,
      storageNode: String,
      start: Int,
      end: Int,
    ) =
      safeApiCall<PanResponse<S3PartUrlsData>> {
        httpClient.post("/b/api/file/s3_upload_object/auth") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(
            S3PartUrlsRequest(
              bucket = bucket,
              key = key,
              partNumberStart = start,
              partNumberEnd = end,
              uploadId = uploadId,
              StorageNode = storageNode,
            )
          )
        }
      }

    override suspend fun getS3PartUrls(
      token: String,
      bucket: String,
      key: String,
      uploadId: String,
      storageNode: String,
      start: Int,
      end: Int,
    ) =
      safeApiCall<PanResponse<S3PartUrlsData>> {
        httpClient.post("/b/api/file/s3_repare_upload_parts_batch") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(
            S3PartUrlsRequest(
              bucket = bucket,
              key = key,
              partNumberStart = start,
              partNumberEnd = end,
              uploadId = uploadId,
              StorageNode = storageNode,
            )
          )
        }
      }

    override suspend fun completeS3V2(
      token: String,
      bucket: String,
      key: String,
      uploadId: String,
      storageNode: String,
      fileId: Long,
      fileSize: Long,
      isMultipart: Boolean,
    ) =
      safeApiCall<PanResponse<Unit>> {
        httpClient.post("/b/api/file/upload_complete/v2") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(
            CompleteS3UploadV2Request(
              StorageNode = storageNode,
              bucket = bucket,
              fileId = fileId,
              fileSize = fileSize,
              isMultipart = isMultipart,
              key = key,
              uploadId = uploadId,
            )
          )
        }
      }

    override suspend fun confirmUpload(token: String, fileId: String) =
      safeApiCall<PanResponse<Unit>> {
        httpClient.post("/b/api/file/upload_complete") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(ConfirmUploadRequest(fileId = fileId))
        }
      }

    override suspend fun getFileList(
      token: String,
      page: Int,
      parentId: Long,
    ): Result<PanResponse<FileListData>> {
      return safeApiCall {
        httpClient.get("/api/file/list/new") {
          bearerAuth(token)
          url {
            parameters.append("driveId", "0")
            parameters.append("limit", "100")
            parameters.append("Page", page.toString())
            parameters.append("parentFileId", parentId.toString())
            parameters.append("orderBy", "file_id")
            parameters.append("orderDirection", "desc")
            parameters.append("trashed", "false")
          }
        }
      }
    }

    override suspend fun getDownloadUrl(token: String, file: FileInfo): Result<String> {
      val endpoint =
        if (file.isDirectory) "/a/api/file/batch_download_info" else "/a/api/file/download_info"

      val requestBody: Any =
        if (file.isDirectory) {
          BatchDownloadRequest(listOf(BatchDownloadItem(file.FileId)))
        } else {
          DownloadInfoRequest(
            fileId = file.FileId,
            etag = file.Etag,
            fileName = file.FileName,
            size = file.Size,
            s3keyFlag = file.S3KeyFlag,
          )
        }

      val responseResult: Result<PanResponse<DownloadData>> = safeApiCall {
        httpClient.post(endpoint) {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(requestBody)
        }
      }

      return responseResult.mapCatching { response ->
        val rawUrl = response.data?.DownloadUrl ?: throw IOException("未获取到下载链接")

        val headResponse = httpClient.get(rawUrl)
        if (headResponse.status == HttpStatusCode.Found) {
          headResponse.headers[HttpHeaders.Location] ?: rawUrl
        } else {
          rawUrl
        }
      }
    }

    override suspend fun createFolder(token: String, name: String, parentId: Long) =
      safeApiCall<PanResponse<Unit>> {
        httpClient.post("/b/api/file/upload_request") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(CreateFolderRequest(fileName = name, parentFileId = parentId))
        }
      }

    override suspend fun deleteFiles(token: String, fileIds: List<Long>) =
      safeApiCall<PanResponse<Unit>> {
        httpClient.post("/a/api/file/trash") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(TrashRequest(fileTrashInfoList = fileIds.map { TrashItem(it) }, operation = true))
        }
      }

    override suspend fun deleteFilesPermanently(token: String, fileIds: List<Long>) =
      safeApiCall<PanResponse<Unit>> {
        httpClient.post("/api/file/delete") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(DeleteFileRequest(fileIdList = fileIds.map { DeleteFileItem(it) }))
        }
      }

    override suspend fun getUserInfo(token: String) =
      safeApiCall<PanResponse<UserInfo>> {
        httpClient.get("/b/api/user/info") { bearerAuth(token) }
      }

    override suspend fun createShare(
      token: String,
      fileIds: List<Long>,
      sharePwd: String,
      expiration: String,
    ) =
      safeApiCall<PanResponse<ShareCreateData>> {
        httpClient.post("/a/api/share/create") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(
            ShareCreateRequest(
              expiration = expiration,
              fileIdList = fileIds.joinToString(","),
              sharePwd = sharePwd,
            )
          )
        }
      }

    override suspend fun listRecycle(token: String, page: Int) =
      safeApiCall<PanResponse<FileListData>> {
        httpClient.get("/api/file/list/new") {
          bearerAuth(token)
          url {
            parameters.append("driveId", "0")
            parameters.append("limit", "100")
            parameters.append("Page", page.toString())
            parameters.append("parentFileId", "0")
            parameters.append("orderBy", "fileId")
            parameters.append("orderDirection", "desc")
            parameters.append("trashed", "true")
          }
        }
      }

    override suspend fun restoreFiles(token: String, fileIds: List<Long>) =
      safeApiCall<PanResponse<Unit>> {
        httpClient.post("/a/api/file/trash") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(
            TrashRequest(
              fileTrashInfoList = fileIds.map { TrashItem(FileId = it) },
              operation = false,
            )
          )
        }
      }

    override suspend fun getFolderDetails(token: String, folderIds: List<Long>) =
      safeApiCall<PanResponse<List<FolderDetailsData>>> {
        httpClient.post("/b/api/restful/goapi/v1/file/details") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(mapOf("file_ids" to folderIds))
        }
      }

    override suspend fun moveFiles(token: String, fileIds: List<Long>, targetParentId: Long) =
      safeApiCall<PanResponse<Unit>> {
        httpClient.post("/api/file/mod_pid") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(
            MoveFileRequest(
              fileIdList = fileIds.map { MoveFileItem(it) },
              parentFileId = targetParentId,
            )
          )
        }
      }

    override suspend fun renameFile(token: String, fileId: Long, newName: String) =
      safeApiCall<PanResponse<Unit>> {
        httpClient.post("/api/file/rename") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(RenameRequest(driveId = 0, fileName = newName, fileId = fileId))
        }
      }

    override suspend fun searchFiles(
      token: String,
      keyword: String,
      page: Int,
      limit: Int,
      parentFileId: Long,
    ): Result<PanResponse<FileListData>> = safeApiCall {
      httpClient.get("/api/file/list/new") {
        bearerAuth(token)
        url {
          parameters.append("driveId", "0")
          parameters.append("limit", limit.toString())
          parameters.append("Page", page.toString())
          parameters.append("parentFileId", parentFileId.toString())
          parameters.append("orderBy", "update_at")
          parameters.append("orderDirection", "desc")
          parameters.append("trashed", "false")
          parameters.append("SearchData", keyword)
        }
      }
    }

    override suspend fun listShares(
      token: String,
      next: String?,
      limit: Int,
      orderBy: String,
      orderDirection: String,
      searchData: String?,
    ): Result<PanResponse<ShareListData>> = safeApiCall {
      httpClient.get("/api/share/list") {
        bearerAuth(token)
        url {
          parameters.append("driveId", "0")
          parameters.append("limit", limit.toString())
          parameters.append("next", next ?: "0")
          parameters.append("orderBy", orderBy)
          parameters.append("orderDirection", orderDirection)
          if (searchData != null) parameters.append("SearchData", searchData)
        }
      }
    }

    override suspend fun getShareInfo(
      token: String,
      shareKey: String,
      next: String?,
      page: Int,
      limit: Int,
      parentFileId: Long,
      sharePwd: String?,
    ): Result<PanResponse<ShareGetResponseData>> = safeApiCall {
      httpClient.get("/api/share/get") {
        bearerAuth(token)
        url {
          parameters.append("shareKey", shareKey)
          parameters.append("next", next ?: "1")
          parameters.append("Page", page.toString())
          parameters.append("limit", limit.toString())
          parameters.append("ParentFileId", parentFileId.toString())
          if (!sharePwd.isNullOrEmpty()) {
            parameters.append("SharePwd", sharePwd)
          }
          parameters.append("orderBy", "share_id")
          parameters.append("orderDirection", "desc")
        }
      }
    }

    override suspend fun deleteShare(
      token: String,
      shareIds: List<Long>,
    ): Result<PanResponse<ShareDeleteResponseData>> = safeApiCall {
      httpClient.post("/api/share/delete") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(ShareDeleteRequest(shareInfoList = shareIds.map { ShareDeleteItem(it) }))
      }
    }

    override suspend fun copyShareFile(
      token: String,
      shareKey: String,
      sharePwd: String,
      targetParentId: Long,
      files: List<CopyFileInfo>,
    ): Result<PanResponse<Unit>> = safeApiCall {
      val adjustedFiles = files.map { it.copy(parentFileId = targetParentId) }
      httpClient.post("/api/file/copy") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(
          CopyShareRequest(SharePwd = sharePwd, shareKey = shareKey, fileInfoList = adjustedFiles)
        )
      }
    }

    override suspend fun generateQrCode(): Result<PanResponse<QrGenerateData>> = safeApiCall {
      httpClient.get("https://user.123pan.cn/api/user/qr-code/generate")
    }

    override suspend fun getQrCodeResult(uniID: String): Result<PanResponse<QrResultData>> = safeApiCall {
      httpClient.get("https://user.123pan.cn/api/user/qr-code/result") {
        url {
          parameters.append("uniID", uniID)
          parameters.append("remember", "true")
          parameters.append("gray", "true")
        }
      }
    }

    override suspend fun getWxCode(uniID: String): Result<PanResponse<WxCodeData>> = safeApiCall {
      httpClient.post("https://user.123pan.cn/api/user/qr-code/wx_code") {
        contentType(ContentType.Application.Json)
        setBody(WxCodeRequest(uniID = uniID))
      }
    }

    override suspend fun loginWithWechatCode(wxCode: String): Result<PanResponse<LoginData>> = safeApiCall {
      httpClient.post("https://user.123pan.cn/api/user/sign_in") {
        contentType(ContentType.Application.Json)
        setBody(WechatLoginRequest(wechat_code = wxCode))
      }
    }
  }

  private suspend inline fun <reified T> safeApiCall(block: suspend () -> HttpResponse): Result<T> {
    var attempts = 0
    while (attempts < MAX_RETRIES) {
      try {
        val response = block()
        if (response.status.value in 300..399 && T::class == String::class) {
          return Result.success(response as T)
        }
        if (!response.status.isSuccess() && response.status != HttpStatusCode.Found) {
          throw IOException("HTTP Error: ${response.status}")
        }
        return Result.success(response.body())
      } catch (e: Exception) {
        attempts++
        if (attempts >= MAX_RETRIES) return Result.failure(e)
        delay(RETRY_DELAY)
      }
    }
    return Result.failure(IOException("Request failed after retries"))
  }

  private fun HttpRequestBuilder.bearerAuth(token: String) {
    header(HttpHeaders.Authorization, "Bearer $token")
  }

  fun close() {
    httpClient.close()
  }
}