// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
// （或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.a321.ui.settings.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import me.voltual.a321.data.DownloadSettingsDataStore

class DownloadSettingsViewModel(private val dataStore: DownloadSettingsDataStore) : ViewModel() {
  var downloadMode by mutableIntStateOf(DownloadSettingsDataStore.MODE_1DM)
    private set

  init {
    viewModelScope.launch {
      dataStore.downloadModeFlow.collect {
        downloadMode = it
      }
    }
  }

  // 将方法重命名为 updateDownloadMode 以解决 JVM 签名冲突
  fun updateDownloadMode(mode: Int) {
    viewModelScope.launch {
      dataStore.saveDownloadMode(mode)
    }
  }
}