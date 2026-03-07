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
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import me.voltual.pyrolysis.ui.player.PlayerSettings
import me.voltual.pyrolysis.ui.player.VideoScaleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

// 使用属性委托创建 DataStore 实例
private val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_settings")

@Single
class PlayerDataStore(context: Context) {

    private val dataStore = context.playerDataStore

    companion object {
        val SCALE_MODE_KEY = stringPreferencesKey("scale_mode")
        val DANMAKU_SIZE_KEY = floatPreferencesKey("danmaku_size")
    }

    // 读取所有设置，返回一个包含 PlayerSettings 的 Flow
    val settingsFlow: Flow<PlayerSettings> = dataStore.data
        .map { preferences ->
            val scaleMode = VideoScaleMode.valueOf(
                preferences[SCALE_MODE_KEY] ?: VideoScaleMode.FIT.name
            )
            val danmakuSize = preferences[DANMAKU_SIZE_KEY] ?: 1.2f

            PlayerSettings(scaleMode = scaleMode, danmakuSize = danmakuSize)
        }

    // 保存设置
    suspend fun saveSettings(settings: PlayerSettings) {
        dataStore.edit { preferences ->
            preferences[SCALE_MODE_KEY] = settings.scaleMode.name
            preferences[DANMAKU_SIZE_KEY] = settings.danmakuSize
        }
    }
}