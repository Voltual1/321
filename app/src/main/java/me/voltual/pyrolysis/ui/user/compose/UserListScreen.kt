//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.

package me.voltual.pyrolysis.ui.user.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.ui.user.UserListViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
// 导入自定义的指示器
import me.voltual.pyrolysis.core.ui.theme.BBQPullRefreshIndicator

@Composable
fun UserListScreen(
    users: List<KtorClient.UserItem>,
    isLoading: Boolean,
    errorMessage: String?, 
    onLoadMore: () -> Unit,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserListViewModel = viewModel()
) {
    // 修复：使用更简单的状态管理
    val safeUsers by remember(users) { mutableStateOf(users) }
    val safeIsLoading by remember(isLoading) { mutableStateOf(isLoading) }
    val safeErrorMessage by remember(errorMessage) { mutableStateOf(errorMessage) }

    // 下拉刷新状态
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // 监听 ViewModel 状态变化以结束刷新状态
    LaunchedEffect(isLoading, errorMessage, users) {
        if (!isLoading && (users.isNotEmpty() || !errorMessage.isNullOrEmpty()) && isRefreshing) {
            isRefreshing = false
        }
    }

    // 使用 MD3 的 PullToRefreshBox
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refresh()
            // 结束刷新状态的逻辑由 LaunchedEffect 处理
        },
        state = pullRefreshState,
        // 使用我们自定义的指示器
        indicator = {
            BBQPullRefreshIndicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
                // 颜色和形状将使用在 Components.kt 中定义的默认值（语义颜色）
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        // 主内容区域
        Box(modifier = Modifier.fillMaxSize()) {
            // 使用 when 语句来处理不同的状态，避免在 Column 内部使用 return
            when {
                // 错误状态显示 - 优先级最高
                !safeErrorMessage.isNullOrEmpty() -> {
                    ErrorState(
                        message = safeErrorMessage ?: "未知错误",
                        onRetry = { viewModel.refresh() } // 错误重试也调用 viewModel.refresh()
                    )
                }
                // 空状态显示 - 在没有错误且没有数据时显示
                safeUsers.isEmpty() && !safeIsLoading -> {
                    EmptyState()
                }
                // 加载中且没有数据时显示加载指示器
                safeIsLoading && safeUsers.isEmpty() -> {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         CircularProgressIndicator()
                     }
                }
                // 有数据时显示列表
                else -> {
                    SafeLazyColumn(
                        users = safeUsers,
                        isLoading = safeIsLoading,
                        onLoadMore = onLoadMore,
                        onUserClick = onUserClick
                    )
                }
            }
        }
    } 
}

@Composable
private fun SafeLazyColumn(
    users: List<KtorClient.UserItem>,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    onUserClick: (Long) -> Unit
) {
    val listState = rememberLazyListState()

    // 修复：使用更保守的加载更多检测
    var lastLoadMoreIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo

            if (visibleItems.isNotEmpty() && totalItems > 0) {
                val lastVisibleIndex = visibleItems.last().index

                // 只有当滚动到接近底部且不是正在加载时才触发
                if (lastVisibleIndex >= totalItems - 2 && lastVisibleIndex != lastLoadMoreIndex && !isLoading) {
                    lastLoadMoreIndex = lastVisibleIndex
                    onLoadMore()
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        itemsIndexed(
            items = users,
            key = { index, user -> user.id }
        ) { index, user ->
            StableUserListItem(user = user, onClick = { onUserClick(user.id) })

            if (index < users.size - 1) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        if (isLoading) {
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

@Composable
private fun StableUserListItem(
    user: KtorClient.UserItem,
    onClick: () -> Unit
) {
    // 修复：完全稳定的状态管理
    val stableUser = remember(user) { user }
    val avatarUrl = remember(stableUser.usertx) { stableUser.usertx }
    val nickname = remember(stableUser.nickname) { stableUser.nickname }
    val hierarchy = remember(stableUser.hierarchy) { stableUser.hierarchy }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    // .crossfade(true)
                    .build(),
                placeholder = painterResource(R.drawable.ic_menu_profile),
                contentDescription = "用户头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = nickname,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = hierarchy,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.no_users_found),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}