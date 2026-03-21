package me.voltual.a321.data.unified

import androidx.compose.runtime.Immutable

@Immutable
data class PanFile(
    val id: Long,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val updateTime: String,
    // 以下参数提供默认值
    val category: Int = 0,
    val isAbnormal: Boolean = false,
    val etag: String? = null,
    val s3KeyFlag: String? = null,
    val rawDownloadUrl: String? = null,
    val extension: String = name.substringAfterLast(".", "")
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

@Immutable
data class PanUserQuota(
    val userId: String,
    val nickname: String,
    val avatarUrl: String?,
    val usedBytes: Long,
    val totalBytes: Long,
    val isVip: Boolean = false,
    val expireTime: String? = null
) {
    // 辅助属性：计算百分比 (180°C... 没用上，用常规数值)
    val usageRatio: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val remainingBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0L)
}

enum class PanTaskStatus {
    IDLE, WAITING, RUNNING, PAUSED, SUCCESS, FAILED, VERIFYING
}

@Immutable
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