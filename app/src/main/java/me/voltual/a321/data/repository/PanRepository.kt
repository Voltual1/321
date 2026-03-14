//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.a321.data.repository

import android.content.Context
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import me.voltual.a321.AuthManager
import me.voltual.a321.KtorClient
import me.voltual.a321.data.UpdateInfo
import me.voltual.a321.utils.PanUtils
import me.voltual.a321.data.unified.PanFile
import me.voltual.a321.data.unified.toUnifiedList
import java.io.File
import java.io.IOException

class PanRepository(private val context: Context) {
    private val apiService = KtorClient.ApiServiceImpl
    private val CHUNK_SIZE = 5 * 1024 * 1024L // 5MB 分块

    /**
     * 获取文件列表并转换为统一模型
     */
    suspend fun getFiles(parentId: Long = 0, page: Int = 1): Result<List<PanFile>> = runCatching {
        val credentials = AuthManager.getCredentials(context).first()
        val token = credentials.token
        
        if (token.isEmpty()) throw Exception("Login required")
        
        val response = apiService.getFileList(token, page, parentId).getOrThrow()
        // 使用 Mapper 转换
        response.data?.InfoList?.toUnifiedList() ?: emptyList()
    }

    /**
     * 完整上传流程
     * @param file 本地文件 (java.io.File)
     * @param parentId 目标目录 ID
     * @param onProgress 进度回调 (0.0 ~ 1.0)
     */
    suspend fun uploadFile(
        file: File,
        parentId: Long,
        onProgress: (Float) -> Unit
    ): Result<String> = runCatching {
        val token = AuthManager.getCredentials(context).first().token
        if (token.isEmpty()) throw Exception("Login required")

        val fileName = file.name
        val fileSize = file.length()
        val md5 = PanUtils.calcFileMd5(file)

        // 1. 申请上传
        val reqRes = apiService.requestUpload(token, parentId, fileName, fileSize, md5).getOrThrow()
        val uploadInfo = reqRes.data ?: throw Exception("Upload request failed")

        // 秒传判断
        if (uploadInfo.Reuse) {
            onProgress(1.0f)
            return@runCatching "秒传成功"
        }

        // 2. 开始分块上传
        val bucket = uploadInfo.Bucket ?: throw Exception("Missing Bucket")
        val key = uploadInfo.Key ?: throw Exception("Missing Key")
        val uploadId = uploadInfo.UploadId ?: throw Exception("Missing UploadId")
        val storageNode = uploadInfo.StorageNode ?: throw Exception("Missing StorageNode")
        val fileId = uploadInfo.FileId ?: throw Exception("Missing FileId")

        val totalParts = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
        
        file.inputStream().use { fis ->
            val buffer = ByteArray(CHUNK_SIZE.toInt())
            for (partNumber in 1..totalParts) {
                val bytesRead = fis.read(buffer)
                if (bytesRead == -1) break
                
                // 获取当前块的上传 URL
                val urlRes = apiService.getS3PartUrls(token, bucket, key, uploadId, storageNode, partNumber).getOrThrow()
                val uploadUrl = urlRes.data?.presignedUrls?.get(partNumber.toString()) 
                    ?: throw Exception("Failed to get S3 URL for part $partNumber")

                // 直接 PUT 字节数组到 S3
                // 注意：这里需要 import io.ktor.client.request.put 和 setBody
                val putResponse = KtorClient.httpClient.put(uploadUrl) {
                    setBody(if (bytesRead < CHUNK_SIZE) buffer.copyOfRange(0, bytesRead) else buffer)
                }
                
                if (!putResponse.status.isSuccess()) {
                    throw IOException("S3 Part upload failed: ${putResponse.status}")
                }

                // 更新进度 (前 90%)
                onProgress(partNumber.toFloat() / totalParts * 0.9f)
            }
        }

        // 3. 合并分块
        apiService.completeS3Upload(token, bucket, key, uploadId, storageNode).getOrThrow()
        
        // 参考 Python 原型，合并后稍作延迟让服务端同步
        delay(1000)

        // 4. 最终确认上传完成
        apiService.confirmUpload(token, fileId).getOrThrow()
        
        onProgress(1.0f)
        "上传成功"
    }

    /**
     * 兼容原有的更新检查逻辑
     */
    suspend fun getLatestRelease(url: String): Result<UpdateInfo> {
        return apiService.getLatestRelease(url)
    }
}