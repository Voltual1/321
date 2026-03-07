//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.


package me.voltual.pyrolysis.core.database

import me.voltual.pyrolysis.BBQApplication
import me.voltual.pyrolysis.ui.community.BrowseHistory
import org.koin.core.annotation.Single

@Single
class BrowseHistoryRepository {
    private val browseHistoryDao = BBQApplication.instance.database.browseHistoryDao()
    private val MAX_HISTORY = 100

    val allHistory = browseHistoryDao.getAllHistory()

    suspend fun addHistory(history: BrowseHistory) {
        // 先插入新记录
        browseHistoryDao.insert(history)

        // 检查总量是否超限，如果超限则删除最旧的
        val count = browseHistoryDao.getCount()
        if (count > MAX_HISTORY) {
            val toDeleteCount = count - MAX_HISTORY
            browseHistoryDao.deleteOldest(toDeleteCount)
        }
    }

    suspend fun deleteHistories(postIds: List<Long>) {
        browseHistoryDao.deleteHistoriesByIds(postIds)
    }

    suspend fun clearAll() {
        browseHistoryDao.clearAll()
    }
}