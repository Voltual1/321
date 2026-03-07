//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.

package me.voltual.pyrolysis.ui.update

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.voltual.pyrolysis.data.unified.UnifiedAppItem
import me.voltual.pyrolysis.core.ui.components.BaseListScreen
import me.voltual.pyrolysis.core.ui.theme.AppGridItem

@Composable
fun UpdateScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = viewModel() // 注入 ViewModel
) {
    // 从 ViewModel 收集 UI 状态
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BaseListScreen(
        items = uiState.apps,
        isLoading = uiState.isLoading,
        error = uiState.error,
        currentPage = 1,
        totalPages = 1, // 暂时不分页
        onRetry = { viewModel.refresh() }, // 重试时调用 ViewModel 的刷新方法
        onLoadMore = { /* 暂时不需要加载更多 */ },
        emptyMessage = "未找到已安装的用户应用", // 更新空消息
        itemContent = { appItem ->
            AppGridItem(
                app = appItem,
                onClick = {
                    // TODO: 实现点击应用项的逻辑，例如查看详情或开始更新检查
                }
            )
        },
        modifier = modifier.fillMaxSize()
    )
}