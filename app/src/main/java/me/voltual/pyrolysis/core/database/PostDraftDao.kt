//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.


package me.voltual.pyrolysis.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDraftDao {

    // 插入或替换草稿，因为 id 总是 0
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(draft: PostDraft)

    // 获取草稿，返回 Flow 以便观察
    @Query("SELECT * FROM post_draft WHERE id = 0")
    fun getDraft(): Flow<PostDraft?>

    // 清空草稿
    @Query("DELETE FROM post_draft")
    suspend fun clear()
}