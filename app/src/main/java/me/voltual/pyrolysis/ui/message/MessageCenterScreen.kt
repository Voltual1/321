//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.pyrolysis.ui.message

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.voltual.pyrolysis.core.ui.theme.BBQPullRefreshIndicator
import me.voltual.pyrolysis.core.ui.components.MessageItem
import me.voltual.pyrolysis.core.ui.components.PageJumpDialog
import me.voltual.pyrolysis.core.ui.components.PaginationControls

@Composable
fun MessageCenterScreen(
    viewModel: MessageViewModel,
    onMessageClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var showPageDialog by remember { mutableStateOf(false) }
    val dialogShape = remember { RoundedCornerShape(4.dp) }

    // 下拉刷新状态
    var isRefreshing by remember { mutableStateOf(false) }

    // 修复：只在真正需要时初始化，不强制重置
    LaunchedEffect(Unit) {
        viewModel.initializeIfNeeded()
    }

    val pullRefreshState = rememberPullToRefreshState()

    // 使用 MD3 的 PullToRefreshBox
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            // 触发 ViewModel 重置和加载第一页
            viewModel.reset()
            // 结束刷新状态的逻辑由 LaunchedEffect 处理
        },
        state = pullRefreshState,
        // 使用我们自定义的指示器
        indicator = {
            BBQPullRefreshIndicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
                // 颜色和形状将使用我们在 Components.kt 中定义的默认值（语义颜色）
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        // 内容区域
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 消息列表
            Box(modifier = Modifier.weight(1f)) {
                when {
                    // 显示加载指示条 - 仅在非刷新状态下且没有消息时显示
                    state.isLoading && state.messages.isEmpty() && !isRefreshing -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("加载中...")
                        }
                    }
                    state.messages.isEmpty() && state.isInitialized -> {
                        Text(
                            text = "暂无消息",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    // 处理初始化但尚未加载的情况
                    state.messages.isEmpty() && !state.isInitialized && !isRefreshing -> {
                         Text(
                            text = "加载中...",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.messages) { message ->
                                MessageItem(
                                    message = message,
                                    onClick = {
                                        if (message.postid != null) {
                                            onMessageClick(message.postid)
                                        }
                                    }
                                )
                            }

                            // 分页加载时显示底部加载指示 - 仅在非刷新状态下显示
                            if (state.isLoading && state.messages.isNotEmpty() && !isRefreshing) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                }

                // 显示错误信息 - 通常不在刷新时显示
                state.error?.let { error ->
                    if (state.messages.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                viewModel.reset()
                            }) {
                                Text("重试")
                            }
                        }
                    }
                    // 如果有消息且加载出错，可以考虑用 Snackbar 显示错误
                }
            }

            // 分页控制栏 - 只在有数据且不是加载中时显示
            // (可选) 不在刷新时显示分页控件
            if (state.messages.isNotEmpty() && !state.isLoading /* && !isRefreshing */) {
                PaginationControls(
                    currentPage = state.currentPage,
                    totalPages = state.totalPages,
                    onPrevClick = { viewModel.prevPage() },
                    onNextClick = { viewModel.nextPage() },
                    onPageClick = { showPageDialog = true },
                    isPrevEnabled = state.currentPage > 1,
                    isNextEnabled = state.currentPage < state.totalPages
                )
            }
        }
    } 

    // 分页跳转对话框
    if (showPageDialog) {
        PageJumpDialog(
            currentPage = state.currentPage,
            totalPages = state.totalPages,
            onDismiss = { showPageDialog = false },
            onConfirm = { page ->
                viewModel.goToPage(page)
                showPageDialog = false
            },
            shape = dialogShape
        )
    }

    // 监听 ViewModel 状态变化以结束刷新状态
    // 这个 LaunchedEffect 放在 PullToRefreshBox 之外，与 PullToRefreshBox 同级
    LaunchedEffect(state.isLoading, state.isInitialized, state.error) {
        // 当加载完成（isLoading 变为 false）且初始化完成（isInitialized 为 true）时，结束刷新
        if (!state.isLoading && state.isInitialized && isRefreshing) {
            isRefreshing = false
        }
        // 如果加载失败并且正在刷新，也应该结束刷新状态
        if (state.error != null && isRefreshing) {
             isRefreshing = false
        }
        // 额外检查：如果 ViewModel 的状态已经是初始化完成且非加载状态，但 UI 仍认为在刷新，也应结束
        if (state.isInitialized && !state.isLoading && isRefreshing) {
             isRefreshing = false
        }
    }
}