// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
// （或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.a321.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.downloadSettingsDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "download_settings")

class DownloadSettingsDataStore(private val context: Context) {
  companion object {
    private val DOWNLOAD_MODE_KEY = intPreferencesKey("download_mode")

    // 下载模式常量
    const val MODE_1DM = 0
    const val MODE_COPY_LINK = 1
    const val MODE_BROWSER = 2
  }

  val downloadModeFlow: Flow<Int> = context.downloadSettingsDataStore.data.map { preferences ->
    preferences[DOWNLOAD_MODE_KEY] ?: MODE_COPY_LINK
  }

  suspend fun saveDownloadMode(mode: Int) {
    context.downloadSettingsDataStore.edit { preferences ->
      preferences[DOWNLOAD_MODE_KEY] = mode
    }
  }

  fun loadDownloadMode(): Int {
    return runBlocking {
      context.downloadSettingsDataStore.data.first()[DOWNLOAD_MODE_KEY] ?: MODE_1DM
    }
  }
}
