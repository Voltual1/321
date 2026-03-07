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
import me.voltual.pyrolysis.ui.community.BrowseHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowseHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: BrowseHistory)

    @Query("SELECT * FROM browse_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<BrowseHistory>>

    @Query("DELETE FROM browse_history WHERE postId IN (:postIds)")
    suspend fun deleteHistoriesByIds(postIds: List<Long>)

    @Query("DELETE FROM browse_history")
    suspend fun clearAll()

    // 一个用于清理旧记录的查询
    @Query("DELETE FROM browse_history WHERE postId IN (SELECT postId FROM browse_history ORDER BY timestamp ASC LIMIT :limit)")
    suspend fun deleteOldest(limit: Int)

    @Query("SELECT COUNT(*) FROM browse_history")
    suspend fun getCount(): Int
    // 按关键字搜索历史
    @Query("SELECT * FROM browse_history WHERE title LIKE :query OR previewContent LIKE :query ORDER BY timestamp DESC")
    fun searchHistory(query: String): Flow<List<BrowseHistory>>
}