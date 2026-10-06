// Copyright (C) 2025 Voltual
//
// Portions of this file are derived from cloud139 (https://github.com/Cnotech/cloud139)
// Copyright (c) 2026 Cnotech
// Licensed under the MIT License.
//
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
// （或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
@file:OptIn(
  kotlinx.serialization.ExperimentalSerializationApi::class,
  kotlin.io.encoding.ExperimentalEncodingApi::class
)

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
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object Cloud139Client {

  // ===== 常量定义 =====
  private const val ROUTE_POLICY_URL = "https://user-njs.yun.139.com/user/route/qryRoutePolicy"
  private const val SEARCH_URL = "https://search-njs.yun.139.com/search/SearchFile"
  private const val CREATE_OUT_LINK_URL = "https://yun.139.com/orchestration/personalCloud-rebuild/outlink/v1.0/getOutLink"
  private const val GET_OUT_LINK_INFO_URL = "https://share-kd-njs.yun.139.com/yun-share/richlifeApp/devapp/IOutLink/getOutLinkInfoV6"
  private const val GET_OUT_LINK_LIST_URL = "https://yun.139.com/orchestration/personalCloud-rebuild/outlink/v1.0/getOutLinkList"
  private const val DEL_OUT_LINK_URL = "https://yun.139.com/orchestration/personalCloud-rebuild/outlink/v1.0/delOutLink"
  private const val MCLOUD_VERSION = "7.14.0"
  private const val MCLOUD_CLIENT = "10701"
  private const val MCLOUD_CHANNEL = "1000101"
  private const val MCLOUD_CHANNEL_SRC = "10000034"
  private const val DEVICE_INFO = "||9|7.14.0|chrome|120.0.0.0|||windows 10||zh-CN|||"
  private const val CLIENT_INFO = "||9|7.14.0|chrome|120.0.0.0|||windows 10||zh-CN|||dW5kZWZpbmVk||"

  // ===== 客户端配置与状态 =====
  data class Config(
    var authorization: String = "",
    var account: String = "",
    var personalCloudHost: String? = null,
    var refreshToken: String? = null,
    var tokenExpireTime: Long? = null
  )

  var currentConfig = Config()

  val httpClient =
    HttpClient(OkHttp) {
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

      install(HttpTimeout) {
        requestTimeoutMillis = 30000
        connectTimeoutMillis = 15000
      }

      install(Logging) { level = LogLevel.INFO }

      followRedirects = false
    }

  // ===== 签名与工具函数 =====

  private fun encodeUriComponent(s: String): String {
    val sb = StringBuilder()
    for (c in s) {
      when (c) {
        '!' -> sb.append("%21")
        '\'' -> sb.append("%27")
        '(' -> sb.append("%28")
        ')' -> sb.append("%29")
        '*' -> sb.append("%2A")
        ' ' -> sb.append("%20")
        else -> {
          if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
            sb.append(c)
          } else {
            val bytes = c.toString().toByteArray(Charsets.UTF_8)
            for (b in bytes) {
              sb.append(String.format("%%%02X", b))
            }
          }
        }
      }
    }
    return sb.toString()
  }

  /**
   * 签名算法 (使用 Kotlin 标准库 Base64)
   */
  fun calcSign(body: String, ts: String, randStr: String): String {
    val encoded = encodeUriComponent(body)
    val sorted = encoded.toCharArray().sorted().joinToString("")
    val bodyBase64 = Base64.encode(sorted.toByteArray(Charsets.UTF_8))

    val hash1 = md5Hash(bodyBase64)
    val hash2 = md5Hash("$ts:$randStr")
    val combined = hash1 + hash2
    return md5Hash(combined).uppercase()
  }

  private fun md5Hash(data: String): String {
    val md = MessageDigest.getInstance("MD5")
    val bytes = md.digest(data.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { String.format("%02x", it) }
  }

  fun generateRandStr(len: Int = 16): String {
    val charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..len).map { charset.random() }.joinToString("")
  }

  fun getCurrentTimestamp(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return LocalDateTime.now().format(formatter)
  }

  // ===== 请求头构建 =====

  private fun buildBaseHeaders(builder: HttpRequestBuilder) {
    builder.header("Accept", "application/json, text/plain, */*")
    builder.header("Content-Type", "application/json;charset=UTF-8")
    builder.header("mcloud-channel", MCLOUD_CHANNEL)
    builder.header("mcloud-client", MCLOUD_CLIENT)
    builder.header("mcloud-version", MCLOUD_VERSION)
    builder.header("Origin", "https://yun.139.com")
    builder.header("Referer", "https://yun.139.com/w/")
    builder.header("x-DeviceInfo", DEVICE_INFO)
    builder.header("x-huawei-channelSrc", MCLOUD_CHANNEL_SRC)
    builder.header("x-inner-ntwk", "2")
    builder.header("x-m4c-caller", "PC")
    builder.header("x-m4c-src", "10002")
    builder.header("Inner-Hcy-Router-Https", "1")
  }

  private fun buildCommonHeaders(builder: HttpRequestBuilder) {
    builder.header("Accept", "application/json, text/plain, */*")
    builder.header("Caller", "web")
    builder.header("CMS-DEVICE", "default")
    builder.header("mcloud-channel", MCLOUD_CHANNEL)
    builder.header("mcloud-client", MCLOUD_CLIENT)
    builder.header("mcloud-route", "001")
    builder.header("mcloud-version", MCLOUD_VERSION)
    builder.header("Origin", "https://yun.139.com")
    builder.header("Referer", "https://yun.139.com/w/")
    builder.header("x-DeviceInfo", DEVICE_INFO)
    builder.header("x-huawei-channelSrc", MCLOUD_CHANNEL_SRC)
    builder.header("x-inner-ntwk", "2")
    builder.header("x-m4c-caller", "PC")
    builder.header("x-m4c-src", "10002")
    builder.header("x-yun-api-version", "v1")
    builder.header("x-yun-app-channel", MCLOUD_CHANNEL_SRC)
    builder.header("x-yun-channel-source", MCLOUD_CHANNEL_SRC)
    builder.header("x-yun-client-info", CLIENT_INFO)
    builder.header("x-yun-module-type", "100")
    builder.header("Inner-Hcy-Router-Https", "1")
  }

  private fun applySignedHeaders(
    builder: HttpRequestBuilder,
    bodyStr: String,
    svcType: String = "1",
    isRoute: Boolean = false
  ) {
    if (isRoute) {
      buildBaseHeaders(builder)
    } else {
      buildCommonHeaders(builder)
      builder.header("x-yun-svc-type", svcType)
    }

    val ts = getCurrentTimestamp()
    val randStr = generateRandStr(16)
    val sign = calcSign(bodyStr, ts, randStr)

    val authValue = if (currentConfig.authorization.startsWith("Basic ")) {
      currentConfig.authorization
    } else {
      "Basic ${currentConfig.authorization}"
    }

    builder.header("Authorization", authValue)
    builder.header("mcloud-sign", "$ts,$randStr,$sign")
    builder.header("x-SvcType", svcType)
  }

  // ===== 内嵌数据模型 (Data Models) =====

  @Serializable
  data class BaseResp(
    val success: Boolean,
    val code: String? = null,
    val message: String? = null
  )

  @Serializable
  data class RoutePolicyResp(
    val success: Boolean,
    val code: String,
    val message: String,
    val data: RoutePolicyData
  )

  @Serializable
  data class RoutePolicyData(
    val routePolicyList: List<RoutePolicy>
  )

  @Serializable
  data class RoutePolicy(
    val siteID: String? = null,
    val siteCode: String? = null,
    val modName: String? = null,
    val httpUrl: String? = null,
    val httpsUrl: String? = null
  )

  @Serializable
  data class PersonalListResp(
    val success: Boolean? = null,
    val code: String? = null,
    val message: String? = null,
    val data: PersonalListData? = null
  )

  @Serializable
  data class PersonalListData(
    val items: List<PersonalFileItem> = emptyList(),
    val nextPageCursor: String? = null
  )

  @Serializable
  data class PersonalFileItem(
    val fileId: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val type: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val createDate: String? = null,
    val updateDate: String? = null,
    val lastModified: String? = null,
    val contentHash: String? = null,
    val contentHashAlgorithm: String? = null
  ) {
    val isFolder: Boolean
      get() = type == "folder" || type == "dir" || type == "1" || type == "2"
  }

  @Serializable
  data class PersonalDetailResp(
    val success: Boolean? = null,
    val code: String? = null,
    val message: String? = null,
    val data: PersonalFileItem? = null
  )

  @Serializable
  data class SearchResp(
    val resultCode: Int? = null,
    val count: Int? = null,
    val total: Int? = null,
    val rows: List<SearchRow> = emptyList(),
    val success: Boolean? = null
  )

  @Serializable
  data class SearchRow(
    val fileId: String? = null,
    val name: String? = null,
    val parentFileId: String? = null,
    val type: String? = null,
    val size: Long? = null,
    val extension: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val contentHash: String? = null
  ) {
    fun toPersonalFileItem(): PersonalFileItem {
      val mappedType = if (type == "2") "folder" else "file"
      return PersonalFileItem(
        fileId = fileId,
        name = name,
        size = size,
        type = mappedType,
        createdAt = createdAt,
        updatedAt = updatedAt,
        contentHash = contentHash
      )
    }
  }

  @Serializable
  data class PersonalUploadResp(
    val success: Boolean? = null,
    val code: String? = null,
    val message: String? = null,
    val data: PersonalUploadData? = null
  )

  @Serializable
  data class PersonalUploadData(
    val fileId: String? = null,
    val fileName: String? = null,
    val partInfos: List<PersonalPartInfo>? = null,
    val exist: Boolean? = null,
    val rapidUpload: Boolean? = null,
    val uploadId: String? = null
  )

  @Serializable
  data class PersonalPartInfo(
    val partNumber: Int,
    val uploadUrl: String
  )

  @Serializable
  data class DownloadUrlResp(
    val success: Boolean? = null,
    val code: String? = null,
    val message: String? = null,
    val data: DownloadUrlData? = null
  )

  @Serializable
  data class DownloadUrlData(
    val url: String? = null,
    val cdnUrl: String? = null,
    val fileName: String? = null
  )

  @Serializable
  data class CreateOutLinkResp(
    val success: Boolean? = null,
    val code: String? = null,
    val message: String? = null,
    val data: CreateOutLinkData? = null
  )

  @Serializable
  data class CreateOutLinkData(
    val getOutLinkRes: OutLinkResContainer? = null
  )

  @Serializable
  data class OutLinkResContainer(
    val getOutLinkResSet: List<OutLinkResSet> = emptyList()
  )

  @Serializable
  data class OutLinkResSet(
    val linkID: String? = null,
    val linkUrl: String? = null,
    val passwd: String? = null
  )

  @Serializable
  data class OutLinkInfoResp(
    val resultCode: String? = null,
    val desc: String? = null,
    val success: Boolean? = null,
    val code: String? = null,
    val data: OutLinkInfoData? = null
  )

  @Serializable
  data class OutLinkInfoData(
    val nodNum: Int? = null,
    val caLst: List<OutLinkFolder>? = null,
    val coLst: List<OutLinkFile>? = null,
    val lkName: String? = null
  )

  @Serializable
  data class OutLinkFolder(
    val caID: String? = null,
    val caName: String? = null,
    val udTime: String? = null
  ) {
    fun toPersonalFileItem(): PersonalFileItem {
      return PersonalFileItem(
        fileId = caID,
        name = caName,
        size = 0L,
        type = "folder",
        updatedAt = udTime
      )
    }
  }

  @Serializable
  data class OutLinkFile(
    val coID: String? = null,
    val coName: String? = null,
    val coSize: Long? = null,
    val udTime: String? = null,
    val path: String? = null
  ) {
    fun toPersonalFileItem(): PersonalFileItem {
      return PersonalFileItem(
        fileId = coID,
        name = coName,
        size = coSize,
        type = "file",
        updatedAt = udTime
      )
    }
  }

  @Serializable
  data class GetOutLinkListResp(
    val success: Boolean? = null,
    val code: String? = null,
    val message: String? = null,
    val data: GetOutLinkListData? = null
  )

  @Serializable
  data class GetOutLinkListData(
    val getOutLinkLstRes: GetOutLinkLstRes? = null
  )

  @Serializable
  data class GetOutLinkLstRes(
    val count: String? = null,
    val outLinks: List<OutLinkItem> = emptyList()
  )

  @Serializable
  data class OutLinkItem(
    val linkID: String? = null,
    val passwd: String? = null,
    val url: String? = null,
    val lkName: String? = null,
    val ctTime: String? = null,
    val lastUdTime: String? = null,
    val expireTime: String? = null
  )

  // ===== 核心 API 实现 =====

  /**
   * 解析 Token 字符串 (使用 Kotlin 标准库 Base64.decode)
   */
  fun parseToken(rawToken: String): Result<Config> = runCatching {
    val token = rawToken.removePrefix("Basic ").trim()
    val decoded = Base64.decode(token).decodeToString()
    val parts = decoded.split(":")
    if (parts.size < 3) throw IllegalArgumentException("Token 格式不完整")

    val account = parts[1]
    val tokenInfo = parts.subList(2, parts.size).joinToString(":")
    val tokenParts = tokenInfo.split("|")
    if (tokenParts.size < 4) throw IllegalArgumentException("Token 信息损坏")

    val expireTime = tokenParts[3].toLong()

    Config(
      authorization = token,
      account = account,
      refreshToken = tokenInfo,
      tokenExpireTime = expireTime
    )
  }

  fun login(token: String): Result<Config> {
    return parseToken(token).onSuccess { config ->
      currentConfig = config
    }
  }

  suspend fun getPersonalCloudHost(): Result<String> = runCatching {
    currentConfig.personalCloudHost?.let { return Result.success(it) }

    val bodyJson = buildJsonObject {
      put("userInfo", buildJsonObject {
        put("userType", 1)
        put("accountType", 1)
        put("accountName", currentConfig.account)
      })
      put("modAddrType", 1)
    }

    val bodyStr = bodyJson.toString()
    val response: RoutePolicyResp = httpClient.post(ROUTE_POLICY_URL) {
      applySignedHeaders(this, bodyStr, svcType = "1", isRoute = true)
      contentType(ContentType.Application.Json)
      setBody(bodyJson)
    }.body()

    if (!response.success) {
      throw IOException("获取路由策略失败: ${response.message}")
    }

    val host = response.data.routePolicyList
      .find { it.modName == "personal" }
      ?.httpsUrl
      ?: throw IOException("未能找到个人云 Host 节点")

    currentConfig.personalCloudHost = host
    host
  }

  private suspend inline fun <reified T> personalApiPost(url: String, bodyJson: JsonObject): Result<T> = runCatching {
    val bodyStr = bodyJson.toString()
    val response: HttpResponse = httpClient.post(url) {
      applySignedHeaders(this, bodyStr, svcType = "1", isRoute = false)
      contentType(ContentType.Application.Json)
      setBody(bodyJson)
    }
    response.body<T>()
  }

  suspend fun getRecycleBinList(
    pageCursor: String? = null,
    pageSize: Int = 100
  ): Result<PersonalListData> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/recyclebin/list"

    val bodyJson = buildJsonObject {
      put("pageInfo", buildJsonObject {
        put("pageSize", pageSize)
        put("pageCursor", pageCursor ?: "")
      })
    }

    val resp = personalApiPost<PersonalListResp>(url, bodyJson).getOrThrow()
    resp.data ?: PersonalListData()
  }

  suspend fun restoreRecycleBinFiles(fileIds: List<String>): Result<Unit> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/recyclebin/batchRestore"

    val bodyJson = buildJsonObject {
      putJsonArray("fileIds") {
        fileIds.forEach { add(JsonPrimitive(it)) }
      }
    }

    val resp = personalApiPost<BaseResp>(url, bodyJson).getOrThrow()
    if (!resp.success) {
      throw IOException("恢复文件失败: ${resp.message}")
    }
  }

  suspend fun deleteRecycleBinFilesPermanently(fileIds: List<String>): Result<Unit> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/file/batchDelete"

    val bodyJson = buildJsonObject {
      putJsonArray("fileIds") {
        fileIds.forEach { add(JsonPrimitive(it)) }
      }
    }

    val resp = personalApiPost<BaseResp>(url, bodyJson).getOrThrow()
    if (!resp.success) {
      throw IOException("彻底删除文件失败: ${resp.message}")
    }
  }

  suspend fun searchFiles(
    keyword: String,
    startNum: Int = 1,
    stopNum: Int = 100
  ): Result<SearchResp> = runCatching {
    val bodyJson = buildJsonObject {
      put("conditions", buildJsonObject {
        put("type", 0)
        put("keyword", keyword)
        put("owner", currentConfig.account)
      })
      put("showInfo", buildJsonObject {
        put("returnTotalCountFlag", true)
        putJsonArray("sortInfos") {}
        put("startNum", startNum)
        put("stopNum", stopNum)
      })
    }

    val bodyStr = bodyJson.toString()
    val response: HttpResponse = httpClient.post(SEARCH_URL) {
      applySignedHeaders(this, bodyStr, svcType = "1", isRoute = false)
      contentType(ContentType.Application.Json)
      setBody(bodyJson)
    }
    response.body<SearchResp>()
  }

  suspend fun getOutLinkInfo(
    linkId: String,
    passwd: String,
    parentCaId: String = "root",
    bNum: Int = 1,
    eNum: Int = 200
  ): Result<OutLinkInfoResp> = runCatching {
    val bodyJson = buildJsonObject {
      put("getOutLinkInfoReq", buildJsonObject {
        put("account", currentConfig.account)
        put("linkID", linkId)
        put("passwd", passwd)
        put("caSrt", 1)
        put("coSrt", 1)
        put("srtDr", 0)
        put("bNum", bNum)
        put("pCaID", parentCaId)
        put("eNum", eNum)
      })
    }

    val bodyStr = bodyJson.toString()
    val response: HttpResponse = httpClient.post(GET_OUT_LINK_INFO_URL) {
      buildCommonHeaders(this)
      val ts = getCurrentTimestamp()
      val randStr = generateRandStr(16)
      val sign = calcSign(bodyStr, ts, randStr)
      val authValue = if (currentConfig.authorization.startsWith("Basic ")) currentConfig.authorization else "Basic ${currentConfig.authorization}"

      header("Authorization", authValue)
      header("mcloud-sign", "$ts,$randStr,$sign")
      header("x-SvcType", "1")
      contentType(ContentType.Application.Json)
      setBody(bodyJson)
    }

    val resp = response.body<OutLinkInfoResp>()
    if (resp.resultCode != "0" && resp.code != "0") {
      throw IOException("获取外链内容失败: ${resp.desc}")
    }
    resp
  }

  suspend fun createOutLink(
    fileIds: List<String>,
    title: String,
    days: Int? = null
  ): Result<OutLinkResSet> = runCatching {
    val bodyJson = buildJsonObject {
      put("getOutLinkReq", buildJsonObject {
        put("subLinkType", 0)
        put("encrypt", 1)
        putJsonArray("coIDLst") {
          fileIds.forEach { add(JsonPrimitive(it)) }
        }
        putJsonArray("caIDLst") {}
        put("pubType", 1)
        put("dedicatedName", title)
        if (days != null && days > 0) {
          put("period", days)
        }
        put("periodUnit", 1)
        putJsonArray("viewerLst") {}
        put("extInfo", buildJsonObject {
          put("isWatermark", 0)
          put("shareChannel", "3001")
        })
        put("commonAccountInfo", buildJsonObject {
          put("account", currentConfig.account)
          put("accountType", 1)
        })
      })
    }

    val bodyStr = bodyJson.toString()
    val response: HttpResponse = httpClient.post(CREATE_OUT_LINK_URL) {
      applySignedHeaders(this, bodyStr, svcType = "1", isRoute = false)
      contentType(ContentType.Application.Json)
      setBody(bodyJson)
    }

    val resp = response.body<CreateOutLinkResp>()
    if (resp.success != true) {
      throw IOException("创建分享失败: ${resp.message}")
    }

    resp.data?.getOutLinkRes?.getOutLinkResSet?.firstOrNull()
      ?: throw IOException("未能获取到返回的分享结果")
  }

  suspend fun getOutLinkList(
    bNum: Int = 1,
    eNum: Int = 100
  ): Result<GetOutLinkLstRes> = runCatching {
    val bodyJson = buildJsonObject {
      put("getOutLinkLstReq", buildJsonObject {
        put("needSetAccount", true)
        put("srt", 0)
        put("srtDr", 0)
        put("bNum", bNum)
        put("eNum", eNum)
        put("qryType", 1)
        put("commonAccountInfo", buildJsonObject {
          put("account", currentConfig.account)
          put("accountType", 1)
        })
      })
    }

    val bodyStr = bodyJson.toString()
    val response: HttpResponse = httpClient.post(GET_OUT_LINK_LIST_URL) {
      applySignedHeaders(this, bodyStr, svcType = "1", isRoute = false)
      contentType(ContentType.Application.Json)
      setBody(bodyJson)
    }

    val resp = response.body<GetOutLinkListResp>()
    if (resp.success != true) {
      throw IOException("获取分享列表失败: ${resp.message}")
    }

    resp.data?.getOutLinkLstRes ?: GetOutLinkLstRes()
  }

  suspend fun delOutLink(linkIds: List<String>): Result<Unit> = runCatching {
    val bodyJson = buildJsonObject {
      put("delOutLinkReq", buildJsonObject {
        putJsonArray("linkIDs") {
          linkIds.forEach { add(JsonPrimitive(it)) }
        }
        put("commonAccountInfo", buildJsonObject {
          put("account", currentConfig.account)
          put("accountType", 1)
        })
      })
    }

    val bodyStr = bodyJson.toString()
    val response: HttpResponse = httpClient.post(DEL_OUT_LINK_URL) {
      applySignedHeaders(this, bodyStr, svcType = "1", isRoute = false)
      contentType(ContentType.Application.Json)
      setBody(bodyJson)
    }

    val resp = response.body<BaseResp>()
    if (!resp.success) {
      throw IOException("取消分享失败: ${resp.message}")
    }
  }

  suspend fun getFileIdByPath(path: String): Result<String> = runCatching {
    val trimmed = path.trim()
    if (trimmed.isEmpty() || trimmed == "/") return Result.success("")

    val host = getPersonalCloudHost().getOrThrow()
    val parts = trimmed.trimStart('/').split('/').filter { it.isNotEmpty() }
    var currentParentId = ""

    for ((index, part) in parts.withIndex()) {
      val isLast = index == parts.size - 1
      val parentId = if (currentParentId.isEmpty()) "/" else currentParentId
      val url = "$host/file/list"

      val bodyJson = buildJsonObject {
        put("parentFileId", parentId)
        put("pageInfo", buildJsonObject {
          put("pageCursor", "")
          put("pageSize", 100)
        })
        put("orderBy", "updated_at")
        put("orderDirection", "DESC")
      }

      val listResp = personalApiPost<PersonalListResp>(url, bodyJson).getOrThrow()
      val items = listResp.data?.items ?: emptyList()
      val matched = items.find { it.name == part }
        ?: throw IOException("路径节点不存在: $part")

      val fileId = matched.fileId ?: ""
      if (isLast) {
        return Result.success(fileId)
      }
      currentParentId = fileId
    }

    currentParentId
  }

  suspend fun listPersonalFiles(
    parentFileId: String,
    pageCursor: String = "",
    pageSize: Int = 100
  ): Result<PersonalListData> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/file/list"

    val bodyJson = buildJsonObject {
      putJsonArray("imageThumbnailStyleList") {
        add(JsonPrimitive("Small"))
        add(JsonPrimitive("Large"))
      }
      put("orderBy", "updated_at")
      put("orderDirection", "DESC")
      put("pageInfo", buildJsonObject {
        put("pageCursor", pageCursor)
        put("pageSize", pageSize)
      })
      put("parentFileId", if (parentFileId.isEmpty()) "/" else parentFileId)
    }

    val resp = personalApiPost<PersonalListResp>(url, bodyJson).getOrThrow()
    resp.data ?: PersonalListData()
  }

  suspend fun getFileDetail(fileId: String): Result<PersonalFileItem> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/file/get"

    val bodyJson = buildJsonObject {
      put("fileId", fileId)
    }

    val resp = personalApiPost<PersonalDetailResp>(url, bodyJson).getOrThrow()
    if (resp.success != true || resp.data == null) {
      throw IOException("获取文件详情失败: ${resp.message}")
    }
    resp.data
  }

  suspend fun createFolder(parentFileId: String, name: String): Result<PersonalUploadData> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/file/create"

    val bodyJson = buildJsonObject {
      put("parentFileId", if (parentFileId.isEmpty()) "/" else parentFileId)
      put("name", name)
      put("description", "")
      put("type", "folder")
      put("fileRenameMode", "force_rename")
    }

    val resp = personalApiPost<PersonalUploadResp>(url, bodyJson).getOrThrow()
    if (resp.success != true) {
      throw IOException("创建目录失败: ${resp.message}")
    }
    resp.data ?: PersonalUploadData()
  }

  suspend fun deleteFiles(fileIds: List<String>): Result<Unit> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/recyclebin/batchTrash"

    val bodyJson = buildJsonObject {
      putJsonArray("fileIds") {
        fileIds.forEach { add(JsonPrimitive(it)) }
      }
    }

    val resp = personalApiPost<BaseResp>(url, bodyJson).getOrThrow()
    if (!resp.success) {
      throw IOException("删除文件失败: ${resp.message}")
    }
  }

  suspend fun renameFile(fileId: String, newName: String): Result<Unit> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/file/update"

    val bodyJson = buildJsonObject {
      put("fileId", fileId)
      put("name", newName)
      put("description", "")
    }

    val resp = personalApiPost<PersonalUploadResp>(url, bodyJson).getOrThrow()
    if (resp.success != true) {
      throw IOException("重命名失败: ${resp.message}")
    }
  }

  suspend fun moveFiles(fileIds: List<String>, toParentFileId: String): Result<Unit> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/file/batchMove"

    val bodyJson = buildJsonObject {
      putJsonArray("fileIds") {
        fileIds.forEach { add(JsonPrimitive(it)) }
      }
      put("toParentFileId", if (toParentFileId.isEmpty()) "/" else toParentFileId)
    }

    val resp = personalApiPost<BaseResp>(url, bodyJson).getOrThrow()
    if (!resp.success) {
      throw IOException("移动失败: ${resp.message}")
    }
  }

  suspend fun copyFiles(fileIds: List<String>, toParentFileId: String): Result<Unit> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/file/batchCopy"

    val bodyJson = buildJsonObject {
      putJsonArray("fileIds") {
        fileIds.forEach { add(JsonPrimitive(it)) }
      }
      put("toParentFileId", if (toParentFileId.isEmpty()) "/" else toParentFileId)
    }

    val resp = personalApiPost<BaseResp>(url, bodyJson).getOrThrow()
    if (!resp.success) {
      throw IOException("复制失败: ${resp.message}")
    }
  }

  suspend fun getDownloadUrl(fileId: String): Result<String> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/file/getDownloadUrl"

    val bodyJson = buildJsonObject {
      put("fileId", fileId)
    }

    val resp = personalApiPost<DownloadUrlResp>(url, bodyJson).getOrThrow()
    if (resp.success != true) {
      throw IOException("获取下载链接失败: ${resp.message}")
    }

    resp.data?.cdnUrl ?: resp.data?.url ?: throw IOException("返回的下载链接为空")
  }

  suspend fun initUpload(
    parentFileId: String,
    fileName: String,
    fileSize: Long,
    contentHash: String,
    partSize: Long = 104857600L
  ): Result<PersonalUploadResp> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/file/create"
    val partCount = (fileSize + partSize - 1) / partSize

    val bodyJson = buildJsonObject {
      put("contentHash", contentHash)
      put("contentHashAlgorithm", "SHA256")
      put("contentType", "application/octet-stream")
      put("parallelUpload", false)
      putJsonArray("partInfos") {
        val limit = minOf(partCount, 100)
        for (i in 0 until limit) {
          val start = i * partSize
          val byteSize = if (fileSize - start > partSize) partSize else fileSize - start
          add(buildJsonObject {
            put("partNumber", i + 1)
            put("partSize", byteSize)
            put("parallelHashCtx", buildJsonObject {
              put("partOffset", start)
            })
          })
        }
      }
      put("size", fileSize)
      put("parentFileId", if (parentFileId.isEmpty()) "/" else parentFileId)
      put("name", fileName)
      put("type", "file")
      put("fileRenameMode", "auto_rename")
    }

    personalApiPost<PersonalUploadResp>(url, bodyJson).getOrThrow()
  }

  suspend fun getUploadUrls(
    fileId: String,
    uploadId: String,
    partCount: Int,
    partSize: Long,
    fileSize: Long
  ): Result<Map<Int, String>> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/file/getUploadUrl"
    val resultMap = mutableMapOf<Int, String>()

    var batchStart = 0
    while (batchStart < partCount) {
      val batchEnd = minOf(batchStart + 100, partCount)
      val bodyJson = buildJsonObject {
        put("fileId", fileId)
        put("uploadId", uploadId)
        putJsonArray("partInfos") {
          for (i in batchStart until batchEnd) {
            val start = i.toLong() * partSize
            val byteSize = if (fileSize - start > partSize) partSize else fileSize - start
            add(buildJsonObject {
              put("partNumber", i + 1)
              put("partSize", byteSize)
            })
          }
        }
        put("commonAccountInfo", buildJsonObject {
          put("account", currentConfig.account)
          put("accountType", 1)
        })
      }

      val resp = personalApiPost<PersonalUploadResp>(url, bodyJson).getOrThrow()
      resp.data?.partInfos?.forEach { info ->
        resultMap[info.partNumber] = info.uploadUrl
      }
      batchStart += 100
    }

    resultMap
  }

  suspend fun confirmUpload(
    fileId: String,
    uploadId: String,
    contentHash: String
  ): Result<Unit> = runCatching {
    val host = getPersonalCloudHost().getOrThrow()
    val url = "$host/file/complete"

    val bodyJson = buildJsonObject {
      put("contentHash", contentHash)
      put("contentHashAlgorithm", "SHA256")
      put("uploadId", uploadId)
      put("fileId", fileId)
    }

    val resp = personalApiPost<BaseResp>(url, bodyJson).getOrThrow()
    if (!resp.success) {
      throw IOException("确认完成上传失败: ${resp.message}")
    }
  }

  fun close() {
    httpClient.close()
  }
}