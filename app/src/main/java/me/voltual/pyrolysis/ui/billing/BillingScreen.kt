// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.pyrolysis.ui.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.core.ui.components.BaseListScreen
import me.voltual.pyrolysis.core.ui.components.BillingItem

@Composable
fun BillingScreen(
    viewModel: BillingViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    
    BaseListScreen(
        items = state.billings,
        isLoading = state.isLoading,
        error = state.error,
        currentPage = state.currentPage,
        totalPages = state.totalPages,
        onRetry = { viewModel.loadBilling() },
        onLoadMore = { viewModel.loadNextPage() },
        emptyMessage = "暂无账单记录",
        itemContent = { billing ->
            BillingItem(billing = billing)
        },
        autoLoadMode = true,
        modifier = modifier 
    )
}