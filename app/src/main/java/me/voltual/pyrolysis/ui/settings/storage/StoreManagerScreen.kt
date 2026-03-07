//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.

package me.voltual.pyrolysis.ui.settings.storage

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.voltual.pyrolysis.core.ui.theme.BBQCard
import me.voltual.pyrolysis.core.ui.theme.BBQOutlinedButton
import coil3.SingletonImageLoader  
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.core.utils.extension.koinPyrolysisViewModel
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalPlatformContext  

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
fun StoreManagerScreen(
    viewModel: StoreManagerViewModel = koinPyrolysisViewModel() 
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageLoader = SingletonImageLoader.get(context)
    
    val cacheSize by viewModel.cacheSize.collectAsState()
    val isSuperCacheEnabled by viewModel.isSuperCacheEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 图片缓存卡片 ---
        BBQCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "图片缓存 (Coil)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "清理由图片加载引擎生成的内存与磁盘缓存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BBQOutlinedButton(
                    onClick = {
                        scope.launch {
                            imageLoader.diskCache?.clear()
                            imageLoader.memoryCache?.clear()
                            // 清理图片后刷新总大小
                            viewModel.updateCacheSize()
                        }
                    },
                    text = { Text("清除图片缓存") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // --- 应用数据缓存卡片 (接入 Cache 类) ---
        BBQCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "下载与临时缓存",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = cacheSize,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Text(
                    text = "包含已下载的更新包、临时文件及索引数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                BBQOutlinedButton(
                    onClick = {
                        viewModel.clearAppCache {
                            // 这里可以添加一个 Toast 提示
                        }
                    },
                    text = { Text("清理所有缓存文件") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
    }
}