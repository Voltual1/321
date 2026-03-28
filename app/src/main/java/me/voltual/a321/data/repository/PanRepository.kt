package me.voltual.a321.data.repository

import android.content.Context
import android.net.Uri
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import me.voltual.a321.AuthManager
import me.voltual.a321.KtorClient
import me.voltual.a321.data.UpdateInfo
import me.voltual.a321.data.unified.*
import me.voltual.a321.utils.PanUtils
import okio.buffer
import okio.source
import java.io.IOException

class PanRepository(private val context: Context) {
    private val apiService = KtorClient.ApiServiceImpl
    private val CHUNK_SIZE = 5 * 1024 * 1024L // 5MB

    /**
     * 获取文件列表
     */
    suspend fun getFiles(parentId: Long = 0, page: Int = 1): Result<List<PanFile>> = runCatching {
        val credentials = AuthManager.getCredentials(context).first()
        val token = credentials.token
        if (token.isEmpty()) throw Exception("Login required")
        
        val response = apiService.getFileList(token, page, parentId).getOrThrow()
        response.data?.InfoList?.toUnifiedList() ?: emptyList()
    }

    /**
     * 基于 Uri 和 Okio 的流式分块上传
     * @param uri 文件的 Uri (来自 SimpleStorage 或系统选择器)
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param parentId 目标目录 ID
     */
    suspend fun uploadFile(
        uri: Uri,
        fileName: String,
        fileSize: Long,
        parentId: Long,
        onProgress: (Float) -> Unit
    ): Result<String> = runCatching {
        val token = AuthManager.getCredentials(context).first().token
        if (token.isEmpty()) throw Exception("Login required")

        // 1. 计算 MD5 (Okio 流式读取)
        val md5 = PanUtils.calcMd5(context, uri)

        // 2. 申请上传
        val reqRes = apiService.requestUpload(token, parentId, fileName, fileSize, md5).getOrThrow()
        val uploadInfo = reqRes.data ?: throw Exception("Upload request failed")

        if (uploadInfo.Reuse) {
            onProgress(1.0f)
            return@runCatching "秒传成功"
        }

        // 3. 准备分块上传参数
        val bucket = uploadInfo.Bucket!!
        val key = uploadInfo.Key!!
        val uploadId = uploadInfo.UploadId!!
        val storageNode = uploadInfo.StorageNode!!
        val fileId = uploadInfo.FileId!!
        val totalParts = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()

        // 4. 开始分块读取并上传
        context.contentResolver.openInputStream(uri)?.source()?.buffer()?.use { source ->
            for (partNumber in 1..totalParts) {
                // 读取一块数据
                val chunk = source.readByteArray(
                    minOf(CHUNK_SIZE, fileSize - (partNumber - 1) * CHUNK_SIZE)
                )

                // 获取 S3 预签名 URL
                val urlRes = apiService.getS3PartUrls(token, bucket, key, uploadId, storageNode, partNumber).getOrThrow()
                val uploadUrl = urlRes.data?.presignedUrls?.get(partNumber.toString()) 
                    ?: throw Exception("Failed to get S3 URL")

                // PUT 到 S3
                val putResponse = KtorClient.httpClient.put(uploadUrl) {
                    setBody(chunk)
                }
                
                if (!putResponse.status.isSuccess()) {
                    throw IOException("S3 Upload failed at part $partNumber")
                }

                onProgress(partNumber.toFloat() / totalParts * 0.9f)
            }
        } ?: throw Exception("Failed to open Uri source")

        // 5. 合并与确认
        apiService.completeS3Upload(token, bucket, key, uploadId, storageNode).getOrThrow()
        delay(1000)
        apiService.confirmUpload(token, fileId).getOrThrow()
        
        onProgress(1.0f)
        "上传成功"
    }
    
    /**
 * 获取文件列表及总条数
 */
suspend fun getFilesWithTotal(parentId: Long = 0, page: Int = 1): Result<Pair<Int, List<PanFile>>> = runCatching {
    val credentials = AuthManager.getCredentials(context).first()
    val token = credentials.token
    if (token.isEmpty()) throw Exception("Login required")
    
    val response = apiService.getFileList(token, page, parentId).getOrThrow()
    val total = response.data?.Total ?: 0
    val files = response.data?.InfoList?.toUnifiedList() ?: emptyList()
    
    total to files
}
    
    /**
 * 获取文件的真实下载直链
 */
suspend fun getDownloadUrl(file: PanFile): Result<String> = runCatching {
    val token = AuthManager.getCredentials(context).first().token
    
    // 将统一模型转回 FileInfo 传给 API 层（或者直接在 API 层接受参数）
    // 这里我们构造一个临时的 FileInfo
    val tempInfo = KtorClient.FileInfo(
        FileId = file.id,
        FileName = file.name,
        Type = if (file.isDirectory) 1 else 0,
        Size = file.size,
        Etag = file.etag,
        S3KeyFlag = file.s3KeyFlag
    )
    
    apiService.getDownloadUrl(token, tempInfo).getOrThrow()
}

/**
 * 获取统一的用户信息模型
 */
suspend fun getUserQuota(): Result<PanUserQuota> = runCatching {
    val token = AuthManager.getCredentials(context).first().token
    if (token.isEmpty()) throw Exception("Login required")
    
    val response = apiService.getUserInfo(token).getOrThrow()
    val data = response.data ?: throw Exception("Failed to get user info")
    
    data.toUnifiedQuota()
}

/**
 * 批量删除文件到回收站
 */
suspend fun deleteFiles(fileIds: List<Long>): PanActionResult {
    val token = AuthManager.getCredentials(context).first().token
    if (token.isEmpty()) return PanActionResult.Error(-1, "Login required")
    
    // 直接使用 apiService 返回的 Result<PanResponse<Unit>>
    return apiService.deleteFiles(token, fileIds).toActionResult()
}

/**
 * 创建文件夹
 */
suspend fun createFolder(name: String, parentId: Long): PanActionResult {
    val token = AuthManager.getCredentials(context).first().token
    if (token.isEmpty()) return PanActionResult.Error(-1, "Login required")
    
    return apiService.createFolder(token, name, parentId).toActionResult()
}

/**
 * 获取回收站文件列表
 */
suspend fun getRecycleBinFiles(page: Int = 1): Result<PanPageResult> = runCatching {
    val token = AuthManager.getCredentials(context).first().token
    val response = apiService.listRecycle(token, page).getOrThrow()
    
    val data = response.data ?: throw Exception("Empty recycle bin")
    data.toPageResult() // 映射为统一分页模型
}

/**
 * 移动文件或文件夹到指定目录
 * @param fileIds 需要移动的文件/文件夹 ID 列表
 * @param targetParentId 目标文件夹的 ID (根目录为 0)
 */
suspend fun moveFiles(fileIds: List<Long>, targetParentId: Long): PanActionResult {
    return runCatching {
        // 1. 获取 Token
        val credentials = AuthManager.getCredentials(context).first()
        val token = credentials.token
        if (token.isEmpty()) return PanActionResult.Error(-1, "Login required")

        // 2. 调用 API 层
        val response = apiService.moveFiles(token, fileIds, targetParentId).getOrThrow()
        
        // 3. 转换为统一结果模型
        if (response.isSuccess) {
            PanActionResult.Success
        } else {
            PanActionResult.Error(response.code, response.message)
        }
    }.getOrElse { throwable ->
        // 异常处理
        PanActionResult.Error(-1, throwable.message ?: "Unknown Error during move")
    }
}

/**
 * 创建文件分享
 * @param expiration ISO 8601 格式字符串，例如 "2099-12-12T08:00:00+08:00"
 */
suspend fun shareFiles(
    fileIds: List<Long>, 
    password: String = "", 
    expiration: String = ""
): Result<String> = runCatching {
    val token = AuthManager.getCredentials(context).first().token
    val response = apiService.createShare(token, fileIds, password, expiration).getOrThrow()
    
    response.data?.ShareKey ?: throw Exception("分享失败：未获取到 Key")
}

/**
 * 彻底删除文件（不可恢复）
 * 对应 API: /api/file/delete
 */
suspend fun deleteFilesPermanently(fileIds: List<Long>): PanActionResult {
    return runCatching {
        val token = AuthManager.getCredentials(context).first().token
        if (token.isEmpty()) return PanActionResult.Error(-1, "Login required")

        val response = apiService.deleteFilesPermanently(token, fileIds).getOrThrow()
        
        // 123云盘彻底删除可能返回 0 或 7301 (表示正在异步释放空间)
        if (response.code == 0 || response.code == 7301) {
            PanActionResult.Success
        } else {
            PanActionResult.Error(response.code, response.message)
        }
    }.getOrElse { throwable ->
        PanActionResult.Error(-1, throwable.message ?: "彻底删除失败")
    }
}

/**
 * 从回收站恢复文件
 * 对应 API: /a/api/file/trash (operation = false)
 */
suspend fun restoreFiles(fileIds: List<Long>): PanActionResult {
    return runCatching {
        val token = AuthManager.getCredentials(context).first().token
        if (token.isEmpty()) return PanActionResult.Error(-1, "Login required")

        // 调用 ApiService 中的 restoreFiles (内部 operation 为 false)
        val response = apiService.restoreFiles(token, fileIds).getOrThrow()
        
        if (response.isSuccess) {
            PanActionResult.Success
        } else {
            PanActionResult.Error(response.code, response.message)
        }
    }.getOrElse { throwable ->
        PanActionResult.Error(-1, throwable.message ?: "恢复文件失败")
    }
}

/**
 * 重命名文件或文件夹
 * @param fileId 文件/文件夹的唯一 ID
 * @param newName 新的文件名（如果是文件，应包含后缀名）
 */
suspend fun renameFile(fileId: Long, newName: String): PanActionResult {
    return runCatching {
        val credentials = AuthManager.getCredentials(context).first()
        val token = credentials.token
        if (token.isEmpty()) return PanActionResult.Error(-1, "Login required")
        val response = apiService.renameFile(token, fileId, newName).getOrThrow()
        if (response.isSuccess) {
            PanActionResult.Success
        } else {
            PanActionResult.Error(response.code, response.message)
        }
    }.getOrElse { throwable ->
        // 异常捕获
        PanActionResult.Error(-1, throwable.message ?: "Unknown Error during rename")
    }
}

    suspend fun getLatestRelease(url: String): Result<UpdateInfo> {
        return apiService.getLatestRelease(url)
    }
}