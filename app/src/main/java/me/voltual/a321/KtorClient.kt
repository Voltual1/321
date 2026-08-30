// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
// （或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
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
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.voltual.a321.data.UpdateInfo
import me.voltual.a321.utils.PanUtils

object KtorClient {
  const val BASE_URL = "https://www.123pan.com"
  private const val MAX_RETRIES = 3
  private const val RETRY_DELAY = 1000L
  private const val ANDROID_APP_VERSION = "61"
  private const val ANDROID_X_APP_VERSION = "2.4.0"

  val httpClient =
    HttpClient(OkHttp) {
      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            encodeDefaults = true // 关键修复：强制序列化默认值
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
        requestTimeoutMillis = 30000
        connectTimeoutMillis = 15000
      }

      install(Logging) { level = LogLevel.INFO }

      // 处理重定向：获取下载直链时我们需要手动处理 302
      followRedirects = false
    }

  // ===== 数据模型 =====

  // ===== 请求模型定义 (用于 POST Body) =====

  @Serializable
  data class LoginRequest(val type: Int = 1, val passport: String, val password: String)

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
  data class CompleteS3UploadRequest(
    val bucket: String,
    val key: String,
    val uploadId: String,
    val StorageNode: String,
  )

  @Serializable data class ConfirmUploadRequest(val fileId: String)

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
      get() = code == 0
  }

  @Serializable data class FileListData(val Total: Int, val InfoList: List<FileInfo>)

  @Serializable
  data class UploadRequestData(
    val Reuse: Boolean, // 是否秒传成功
    val FileId: String? = null,
    val UploadId: String? = null,
    val Bucket: String? = null,
    val Key: String? = null,
    val StorageNode: String? = null,
  )

  @Serializable
  data class S3PartUrlsData(
    val presignedUrls: Map<String, String> // PartNumber -> URL
  )

  @Serializable
  data class FileInfo(
    val FileId: Long,
    val FileName: String,
    val Type: Int, // 1: 文件夹, 0: 文件
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

    // 补全此逻辑：123网盘 Status > 100 通常表示文件异常（被封禁或审核不通过）
    val isAbnormal: Boolean
      get() = Status > 100
  }

  @Serializable data class DownloadData(val DownloadUrl: String)

  @Serializable data class LoginData(val token: String)

  // ===== 下载相关的请求模型 =====

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

  // 用户信息
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

  // 分享请求
  @Serializable
  data class ShareCreateRequest(
    val driveId: Int = 0,
    val expiration: String,
    val fileIdList: String, // 逗号分隔的ID字符串
    val shareName: String = "分享文件",
    val sharePwd: String = "",
    val event: String = "shareCreate",
  )

  // 分享响应数据
  @Serializable data class ShareCreateData(val ShareKey: String)

  // 文件夹详情数据
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

  // 获取分享信息响应的 data 部分
  @Serializable
  data class ShareGetResponseData(
    val Next: String?,
    val Len: Int,
    val IsFirst: Boolean,
    val Expired: Boolean,
    val IsPaidPreview: Boolean,
    val InfoList: List<FileInfo>,
  )

  // 取消分享的请求体
  @Serializable data class ShareDeleteItem(val shareid: Long)

  @Serializable
  data class ShareDeleteRequest(val driveId: Int = 0, val shareInfoList: List<ShareDeleteItem>)

  // 取消分享响应的 data 部分
  @Serializable data class ShareDeleteResponseData(val InfoList: List<ShareDeleteItemResult>)

  @Serializable data class ShareDeleteItemResult(val ShareId: Long)

  // ===== 复制分享文件相关数据类 =====

  /**
   * 复制分享文件时单个文件的信息
   *
   * @param driveId 网盘ID，默认为0
   * @param duplicate 重复处理策略（2 表示重命名）
   * @param etag 文件ETag
   * @param fileId 分享中的文件ID
   * @param fileName 文件名
   * @param parentFileId 目标文件夹ID（用户自己的网盘）
   * @param size 文件大小
   * @param type 类型：0 文件，1 文件夹
   */
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

  /** 复制分享文件的请求体 */
  @Serializable
  data class CopyShareRequest(
    val SharePwd: String = "",
    val shareKey: String,
    val fileInfoList: List<CopyFileInfo>,
  )

  // ===== API 接口定义 =====

  interface ApiService {
    // 兼容 UpdateChecker.kt
    suspend fun getLatestRelease(url: String): Result<UpdateInfo>

    // 兼容 PanRepository.kt
    suspend fun getFileList(
      token: String,
      page: Int,
      parentId: Long = 0,
    ): Result<PanResponse<FileListData>>

    // 新增功能
    suspend fun login(passport: String, password: String): Result<PanResponse<LoginData>>

    suspend fun getDownloadUrl(token: String, file: FileInfo): Result<String>

    suspend fun createFolder(token: String, name: String, parentId: Long): Result<PanResponse<Unit>>

    suspend fun deleteFiles(token: String, fileIds: List<Long>): Result<PanResponse<Unit>>

    // 1. 请求上传（含秒传校验）
    suspend fun requestUpload(
      token: String,
      parentId: Long,
      fileName: String,
      size: Long,
      md5: String,
    ): Result<PanResponse<UploadRequestData>>

    // 2. 获取分块上传的预签名 URL
    suspend fun getS3PartUrls(
      token: String,
      bucket: String,
      key: String,
      uploadId: String,
      storageNode: String,
      partNumber: Int,
    ): Result<PanResponse<S3PartUrlsData>>

    // 3. 合并 S3 分块
    suspend fun completeS3Upload(
      token: String,
      bucket: String,
      key: String,
      uploadId: String,
      storageNode: String,
    ): Result<PanResponse<Unit>>

    // 4. 最终确认上传完成
    suspend fun confirmUpload(token: String, fileId: String): Result<PanResponse<Unit>>

    // 获取用户信息
    suspend fun getUserInfo(token: String): Result<PanResponse<UserInfo>>

    // 创建分享
    suspend fun createShare(
      token: String,
      fileIds: List<Long>,
      sharePwd: String = "",
      expiration: String = "2099-12-12T08:00:00+08:00",
    ): Result<PanResponse<ShareCreateData>>

    // 获取回收站列表
    suspend fun listRecycle(token: String, page: Int = 1): Result<PanResponse<FileListData>>

    // 恢复文件（从回收站）
    suspend fun restoreFiles(token: String, fileIds: List<Long>): Result<PanResponse<Unit>>

    // 彻底删除文件（不可恢复）
    suspend fun deleteFilesPermanently(
      token: String,
      fileIds: List<Long>,
    ): Result<PanResponse<Unit>>

    // 获取文件夹详情（支持多个ID）
    suspend fun getFolderDetails(
      token: String,
      folderIds: List<Long>,
    ): Result<PanResponse<List<FolderDetailsData>>>

    /**
     * 移动文件或文件夹
     *
     * @param fileIds 需要移动的文件/文件夹 ID 列表
     * @param targetParentId 目标目录的 ID
     */
    suspend fun moveFiles(
      token: String,
      fileIds: List<Long>,
      targetParentId: Long,
    ): Result<PanResponse<Unit>>

    /**
     * 重命名文件或文件夹
     *
     * @param token 用户授权 Token
     * @param fileId 文件或文件夹的 ID
     * @param newName 新的文件名（需包含后缀名）
     */
    suspend fun renameFile(token: String, fileId: Long, newName: String): Result<PanResponse<Unit>>

    suspend fun searchFiles(
      token: String,
      keyword: String,
      page: Int = 1,
      limit: Int = 100,
      parentFileId: Long = 0,
    ): Result<PanResponse<FileListData>>

    /**
     * 获取分享列表
     *
     * @param token 用户授权 Token
     * @param next 分页标识，首次请求传 null，后续使用响应中的 Next 字段
     * @param limit 每页数量，默认 100
     * @param orderBy 排序字段，默认 "fileId"
     * @param orderDirection 排序方向，默认 "desc"
     * @param searchData 搜索关键词，可选
     */
    suspend fun listShares(
      token: String,
      next: String? = null,
      limit: Int = 100,
      orderBy: String = "fileId",
      orderDirection: String = "desc",
      searchData: String? = null,
    ): Result<PanResponse<ShareListData>>

    /**
     * 获取分享链接中的文件列表
     *
     * @param shareKey 分享标识
     * @param next
     * @param limit 每页数量
     * @param parentFileId 文件夹ID（0表示根目录）
     * @param sharePwd 提取码（可选）
     */
    suspend fun getShareInfo(
      token: String,
      shareKey: String,
      next: String? = "1", // 新增参数，默认值为 "1"
      page: Int = 1,
      limit: Int = 200,
      parentFileId: Long = 0,
      sharePwd: String? = null,
    ): Result<PanResponse<ShareGetResponseData>>

    /**
     * 删除分享（取消分享）
     *
     * @param shareIds 要删除的分享ID列表
     */
    suspend fun deleteShare(
      token: String,
      shareIds: List<Long>,
    ): Result<PanResponse<ShareDeleteResponseData>>

    /**
     * 从分享链接复制文件到自己的网盘
     *
     * @param token 用户授权Token
     * @param shareKey 分享标识
     * @param sharePwd 分享提取码（可选）
     * @param targetParentId 目标文件夹ID（自己的网盘）
     * @param files 要复制的文件信息列表（从分享文件列表获取）
     */
    suspend fun copyShareFile(
      token: String,
      shareKey: String,
      sharePwd: String,
      targetParentId: Long,
      files: List<CopyFileInfo>,
    ): Result<PanResponse<Unit>>
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

    override suspend fun getS3PartUrls(
      token: String,
      bucket: String,
      key: String,
      uploadId: String,
      storageNode: String,
      partNumber: Int,
    ) =
      safeApiCall<PanResponse<S3PartUrlsData>> {
        httpClient.post("/b/api/file/s3_repare_upload_parts_batch") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(
            S3PartUrlsRequest(
              bucket = bucket,
              key = key,
              partNumberStart = partNumber,
              // 关键修复：End 必须比 Start 大 1 才能获取到当前块的 URL
              partNumberEnd = partNumber + 1,
              uploadId = uploadId,
              StorageNode = storageNode,
            )
          )
        }
      }

    override suspend fun completeS3Upload(
      token: String,
      bucket: String,
      key: String,
      uploadId: String,
      storageNode: String,
    ) =
      safeApiCall<PanResponse<Unit>> {
        httpClient.post("/b/api/file/s3_complete_multipart_upload") {
          bearerAuth(token)
          contentType(ContentType.Application.Json)
          setBody(
            CompleteS3UploadRequest(
              bucket = bucket,
              key = key,
              uploadId = uploadId,
              StorageNode = storageNode,
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

    /** 获取下载直链 参考 Python 原型：如果是文件夹走 batch_download_info，文件走 download_info */
    override suspend fun getDownloadUrl(token: String, file: FileInfo): Result<String> {
      val endpoint =
        if (file.isDirectory) "/a/api/file/batch_download_info" else "/a/api/file/download_info"

      // 使用具体的 Serializable 对象替代 mapOf
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
          // 彻底删除！
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
              operation = false, // false 表示恢复
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
          parameters.append("orderBy", "update_at") // 按更新时间排序
          parameters.append("orderDirection", "desc")
          parameters.append("trashed", "false")
          parameters.append("SearchData", keyword) // 搜索关键词
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
      // 确保每个文件的 parentFileId 使用用户指定的目标文件夹
      val adjustedFiles = files.map { it.copy(parentFileId = targetParentId) }
      httpClient.post("/api/file/copy") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(
          CopyShareRequest(SharePwd = sharePwd, shareKey = shareKey, fileInfoList = adjustedFiles)
        )
      }
    }
  }

  /** 安全地执行 Ktor 请求 */
  private suspend inline fun <reified T> safeApiCall(block: suspend () -> HttpResponse): Result<T> {
    var attempts = 0
    while (attempts < MAX_RETRIES) {
      try {
        val response = block()
        if (response.status.value in 300..399 && T::class == String::class) {
          // 特殊处理重定向返回
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
