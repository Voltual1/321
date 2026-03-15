package me.voltual.a321.data.unified

import androidx.compose.runtime.Immutable

@Immutable
data class PanFile(
    val id: Long,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val updateTime: String,
    val category: Int,
    val isAbnormal: Boolean, // 审核是否违规
    // 新增：用于获取下载直链的必要元数据
    val etag: String?,
    val s3KeyFlag: String?,
    val rawDownloadUrl: String?, // 列表自带的 url
    val extension: String = name.substringAfterLast(".", "")
)

data class PanPath(
    val id: Long,
    val name: String
)