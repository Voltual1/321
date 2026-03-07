//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.


package me.voltual.pyrolysis.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "post_draft")
data class PostDraft(
    // 只有一个草稿，所以主键是固定的
    @PrimaryKey val id: Int = 0,
    val title: String,
    val content: String,
    // 我们将 List<Uri> 存储为逗号分隔的字符串，与旧逻辑保持一致
    val imageUris: String,
    val imageUrls: String,
    val subsectionId: Int
)