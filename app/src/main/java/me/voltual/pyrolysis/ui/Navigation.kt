// Copyright (C) 2025 Voltual
package me.voltual.pyrolysis.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 的类型安全目的地契约。
 * 移除了所有冗余的 route 字符串定义，完全依赖 Kotlinx Serialization 进行类型匹配。
 */
sealed interface AppDestination : NavKey

// --- 核心导航 ---
@Serializable
data object Home : AppDestination

@Serializable
data object Login : AppDestination

@Serializable
data object About : AppDestination

@Serializable
data object LogViewer : AppDestination

@Serializable
data object ThemeCustomize : AppDestination

@Serializable
data object Download : AppDestination

/** 图片预览 */
@Serializable
data class ImagePreview(val imageUrl: String) : AppDestination

/** 搜索 */
@Serializable
data class Search(
    val userId: String? = null,
    val nickname: String? = null
) : AppDestination