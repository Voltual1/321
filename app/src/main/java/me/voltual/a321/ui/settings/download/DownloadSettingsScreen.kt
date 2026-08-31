// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
// （或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.a321.ui.settings.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.voltual.a321.data.DownloadSettingsDataStore
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
  viewModel: DownloadSettingsViewModel = koinViewModel(),
  modifier: Modifier = Modifier
) {
  Scaffold(
    topBar = {
      TopAppBar(
      )
    }
  ) { paddingValues ->
    Column(
      modifier = modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Text(
        text = "默认下载方式选项",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
      )

      Card(
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(8.dp)) {
          DownloadModeOption(
            title = "使用 1DM 下载",
            description = "调起外部 1DM 下载工具进行下载",
            selected = viewModel.downloadMode == DownloadSettingsDataStore.MODE_1DM,
            onClick = { viewModel.updateDownloadMode(DownloadSettingsDataStore.MODE_1DM) }
          )

          HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

          DownloadModeOption(
            title = "仅复制直链链接",
            description = "静默或点击下载时将下载直链复制到剪贴板，不开始实际下载",
            selected = viewModel.downloadMode == DownloadSettingsDataStore.MODE_COPY_LINK,
            onClick = { viewModel.updateDownloadMode(DownloadSettingsDataStore.MODE_COPY_LINK) }
          )

          HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

          DownloadModeOption(
            title = "调起默认浏览器跳转",
            description = "跳转到直链链接并依赖浏览器内置机制或外部工具下载",
            selected = viewModel.downloadMode == DownloadSettingsDataStore.MODE_BROWSER,
            onClick = { viewModel.updateDownloadMode(DownloadSettingsDataStore.MODE_BROWSER) }
          )
        }
      }
    }
  }
}

@Composable
fun DownloadModeOption(
  title: String,
  description: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.bodyLarge)
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    RadioButton(
      selected = selected,
      onClick = onClick
    )
  }
}