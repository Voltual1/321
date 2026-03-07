//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.

package me.voltual.pyrolysis.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

@Single
class SearchHistoryDataStore(context: Context) {

    private val dataStore = context.applicationContext.searchHistoryDataStore

    companion object {
        private val HISTORY_KEY = stringSetPreferencesKey("search_history_set")
        private const val MAX_HISTORY_SIZE = 50
    }

    val historyFlow: Flow<List<String>> = dataStore.data
        .map { preferences ->
            preferences[HISTORY_KEY]?.toList()?.reversed() ?: emptyList()
        }

    suspend fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        dataStore.edit { settings ->
            val currentHistory = settings[HISTORY_KEY]?.toMutableSet() ?: mutableSetOf()
            currentHistory.remove(query)
            currentHistory.add(query)
            
            val historyList = currentHistory.toList().takeLast(MAX_HISTORY_SIZE)
            settings[HISTORY_KEY] = historyList.toSet()
        }
    }

    suspend fun removeSearchQuery(query: String) {
        dataStore.edit { settings ->
            val currentHistory = settings[HISTORY_KEY]?.toMutableSet() ?: return@edit
            currentHistory.remove(query)
            settings[HISTORY_KEY] = currentHistory
        }
    }

    suspend fun clearAllHistory() {
        dataStore.edit { settings ->
            settings.remove(HISTORY_KEY)
        }
    }
}