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
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import org.koin.core.annotation.Single

private val Context.storageSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "storage_settings")

@Single
class StorageSettingsDataStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.storageSettingsDataStore

    companion object {
        private val IS_SUPER_CACHE_ENABLED = booleanPreferencesKey("is_super_cache_enabled")
    }

    val isSuperCacheEnabledFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[IS_SUPER_CACHE_ENABLED] ?: false
        }

    suspend fun updateSuperCacheEnabled(isEnabled: Boolean) {
        dataStore.edit { it[IS_SUPER_CACHE_ENABLED] = isEnabled }
    }
}