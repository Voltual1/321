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
    // 原始模型中需要的，但 UI 可能不需要的字段可以排除
    // 也可以在这里添加 UI 专用的辅助属性
    val extension: String = name.substringAfterLast(".", "")
)