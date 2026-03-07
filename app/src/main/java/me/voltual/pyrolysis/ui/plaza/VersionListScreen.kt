// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.plaza

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.data.unified.UnifiedAppItem
import me.voltual.pyrolysis.ui.*
import me.voltual.pyrolysis.core.ui.theme.AppList
import org.koin.androidx.compose.koinViewModel

@Composable
fun VersionListScreen(
    packageName: String,
    storeName: String,
    modifier: Modifier = Modifier,
    viewModel: VersionListViewModel = koinViewModel()
) {
    // Navigation 3 导航器
    val navigator = LocalNavigator.current

    val versions by viewModel.versions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(packageName, storeName) {
        val store = try {
            AppStore.valueOf(storeName)
        } catch (e: IllegalArgumentException) {
            AppStore.XIAOQU_SPACE
        }
        viewModel.loadVersions(packageName, store)
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (errorMessage != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Error: $errorMessage", color = MaterialTheme.colorScheme.error)
        }
    } else if (versions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No versions found")
        }
    } else {
        VersionListContent(
            versions = versions,
            onItemClick = { appItem ->
                // 类型安全导航：直接传递 AppDetail 路由对象
                navigator.navigate(
                    AppDetail(
                        appId = appItem.navigationId,
                        versionId = appItem.navigationVersionId,
                        storeName = storeName
                    )
                )
            },
            modifier = modifier
        )
    }
}

@Composable
fun VersionListContent(
    versions: List<UnifiedAppItem>,
    onItemClick: (UnifiedAppItem) -> Unit,
    modifier: Modifier = Modifier
) {
    AppList(
        apps = versions,
        onItemClick = onItemClick,
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)
    )
}