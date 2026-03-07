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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.preferencesDataStore
import me.voltual.pyrolysis.BBQApplication

private val Context.signInSettingsDataStore by preferencesDataStore(name = "sign_in_settings")

object SignInSettingsDataStore {
    
    private val AUTO_SIGN_IN = booleanPreferencesKey("auto_sign_in")
    
    val autoSignIn: Flow<Boolean>
        get() = BBQApplication.instance.applicationContext.signInSettingsDataStore.data.map { preferences ->
            preferences[AUTO_SIGN_IN] ?: false // 默认关闭自动签到
        }
    
    suspend fun setAutoSignIn(value: Boolean) {
        BBQApplication.instance.applicationContext.signInSettingsDataStore.edit { 
            it[AUTO_SIGN_IN] = value
        }
    }
}