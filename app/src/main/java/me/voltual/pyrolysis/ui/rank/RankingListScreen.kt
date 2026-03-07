// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.pyrolysis.ui.rank

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.voltual.pyrolysis.ui.*
import me.voltual.pyrolysis.core.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingListScreen(
    viewModel: RankingListViewModel = viewModel()
) {
    // Navigation 3 导航器
    val navigator = LocalNavigator.current

    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // 下拉菜单状态
    var expanded by remember { mutableStateOf(false) }

    // 下拉刷新状态
    // 注意：新版 M3 PullToRefresh 的 isRefreshing 通常由外部传入，
    // state 仅负责动画。如果你的版本报错，请确保导入了正确的 pulltorefresh 包。
    val pullRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    // 监听 ViewModel 状态变化以结束刷新状态
    LaunchedEffect(state.isRefreshing, state.isLoading, state.error, state.rankingList) {
        if (!state.isLoading && (state.rankingList.isNotEmpty() || state.error != null) && isRefreshing) {
            isRefreshing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 排序选择器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BBQExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = state.sortType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                )

                BBQExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    SortType.entries.forEach { sortType -> // 改用 entries 替代 values() 是 Kotlin 建议
                        DropdownMenuItem(
                            text = { Text(sortType.displayName) },
                            onClick = {
                                viewModel.changeSortType(sortType)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // 使用 MD3 的 PullToRefreshBox
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refreshRankingList()
            },
            state = pullRefreshState,
            indicator = {
                // 显式指定 Alignment 类型以解决编译器的歧义
                BBQPullRefreshIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.isLoading && state.rankingList.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.error != null) {
                val errorMessage = state.error
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally, // 修复此处可能的类型匹配
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "加载失败",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "未知错误",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.refreshRankingList() }) {
                        Text("重试")
                    }
                }
            } else if (state.rankingList.isEmpty()) {
                Text(
                    text = "暂无排名数据",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    itemsIndexed(
                        items = state.rankingList,
                        key = { _, user -> user.id } // 建议加上 key 提高重绘性能
                    ) { index, user ->
                        RankingListItem(
                            ranking = index + 1,
                            user = user,
                            sortType = state.sortType,
                            onClick = {
                                // Navigation 3 类型安全导航
                                navigator.navigate(UserDetail(user.id))
                            }
                        )

                        if (index == state.rankingList.size - 1 && state.hasMore && !state.isLoading) {
                            SideEffect {
                                viewModel.loadNextRankingList()
                            }
                        }
                    }

                    if (state.isLoading && state.rankingList.isNotEmpty() && !isRefreshing) {
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
    }
}