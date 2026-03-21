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