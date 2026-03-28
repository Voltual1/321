package me.voltual.a321.data.unified

import androidx.compose.runtime.Immutable

@Immutable
data class PanFile(
    val id: Long,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val updateTime: String,
    val category: Int = 0, // 0:普通, 1:文件夹, 10:分享项
    val isAbnormal: Boolean = false,
    val etag: String? = null,
    val s3KeyFlag: String? = null,
    val rawDownloadUrl: String? = null,
    val extension: String = name.substringAfterLast(".", ""),
    // --- 分享相关字段 ---
    val shareKey: String? = null,
    val sharePwd: String? = null,
    val expiration: String? = null,
    val shareUrl: String? = null
)

data class PanPath(
    val id: Long,
    val name: String
)

@Immutable
data class PanPageResult(
    val files: List<PanFile>,
    val totalCount: Int,
    val hasMore: Boolean,
    val nextMarker: String? = null // 适配 OSS 或其他网盘的流式分页
)

data class PanUserQuota(
    val userId: String,
    val nickname: String,
    val avatarUrl: String?,
    val usedBytes: Long,
    val totalBytes: Long,
    val isVip: Boolean = false,
    val expireTime: String? = null
) {
    val usageRatio: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val remainingBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0L)
}

enum class PanTaskStatus {
    IDLE, WAITING, RUNNING, PAUSED, SUCCESS, FAILED, VERIFYING
}

data class PanTransferTask(
    val taskId: String,
    val fileId: String?,
    val localPath: String,
    val fileName: String,
    val totalSize: Long,
    val currentSize: Long,
    val status: PanTaskStatus,
    val speed: Long, // 每秒字节数
    val errorMsg: String? = null,
    val isUpload: Boolean = true
) {
    val progress: Float get() = if (totalSize > 0) currentSize.toFloat() / totalSize else 0f
}

sealed class PanActionResult {
    object Success : PanActionResult()
    data class Error(val code: Int, val message: String) : PanActionResult()
    data class PartialSuccess(val successIds: List<Long>, val failedIds: List<Long>) : PanActionResult()
}