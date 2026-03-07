//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.

package me.voltual.pyrolysis.ui.community.compose

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.core.ui.theme.BBQDropdownMenu
import me.voltual.pyrolysis.AuthManager
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.first
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import me.voltual.pyrolysis.core.ui.theme.BBQPullRefreshIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseComposeListScreen(
    title: String = "",
    posts: List<KtorClient.Post>,
    isLoading: Boolean,
    errorMessage: String,
    onItemClick: (KtorClient.Post) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit = {},
    onCreateClick: () -> Unit = {},
    historyClick: () -> Unit = {},
    onMessageClick: () -> Unit = {},
    totalPages: Int = 1,
    onJumpToPage: (Int) -> Unit = {},
    onNavigate: (String) -> Unit,
    onBackClick: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    var showJumpDialog by remember { mutableStateOf(false) }
    var inputPage by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // 下拉刷新状态
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // 监听刷新状态
    // 更新 LaunchedEffect 以更准确地反映刷新结束的条件
    LaunchedEffect(isLoading, errorMessage, posts) {
        // 当内容加载完成（无论成功还是失败）且正在刷新时，结束刷新状态
        if (!isLoading && isRefreshing) {
             isRefreshing = false
        }
    }

    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text("从哪页开始呢~_~") },
            text = {
                Column {
                    Text("总页数: $totalPages", color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = inputPage,
                        onValueChange = { inputPage = it },
                        label = { Text("输入页码 (1-$totalPages)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val page = inputPage.toIntOrNull() ?: 1
                        if (page in 1..totalPages) {
                            onJumpToPage(page)
                        }
                        showJumpDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 自定义TopBar，使用可水平滚动的Row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 返回按钮
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 标题区域 - 可水平滚动
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(scrollState),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 标题下拉菜单按钮
                        Box {
                            Button(
                                onClick = { expanded = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                elevation = ButtonDefaults.buttonElevation(0.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            // 下拉菜单
                            BBQDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.width(200.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("社区") },
                                    onClick = {
                                        expanded = false
                                        onNavigate("community")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("热点") },
                                    onClick = {
                                        expanded = false
                                        onNavigate("hot_posts")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("关注的人") },
                                    onClick = {
                                        expanded = false
                                        onNavigate("following_posts")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("我喜欢的") },
                                    onClick = {
                                        expanded = false
                                        onNavigate("my_likes")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("我的帖子") },
                                    onClick = {
                                        expanded = false
                                        scope.launch {
                                            val currentUserId = AuthManager.getUserId(context).first()
                                            if (currentUserId > 0) {
                                                onNavigate("my_posts/$currentUserId")
                                            } else {
                                                snackbarHostState.showSnackbar(
                                                    message = context.getString(R.string.login_first),
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // 操作按钮区域 - 固定宽度，使用可水平滚动的Row
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.End
                    ) {
                        // 消息中心按钮
                        IconButton(
                            onClick = onMessageClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_menu_message),
                                contentDescription = "消息中心",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 跳页按钮
                        IconButton(
                            onClick = { showJumpDialog = true; inputPage = "" },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.kakao_page),
                                contentDescription = "跳页",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 搜索按钮
                        IconButton(
                            onClick = onSearchClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 发帖按钮
                        IconButton(
                            onClick = onCreateClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "发帖",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 历史记录按钮
                        IconButton(
                            onClick = historyClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "浏览历史",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // 使用 MD3 的 PullToRefreshBox
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                onRefresh() // 调用传入的刷新回调
                // 结束刷新状态的逻辑由 LaunchedEffect 处理
            },
            state = pullRefreshState,
            indicator = {
                BBQPullRefreshIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            },
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            // 主内容区域
            Box(modifier = Modifier.fillMaxSize()) {
                // 内容显示逻辑
                when {
                    // 错误状态显示 - 优先级最高
                    errorMessage.isNotEmpty() -> {
                        ErrorView(
                            message = errorMessage,
                            onRetry = onRefresh,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // 加载中且没有帖子数据时显示加载指示器
                    isLoading && posts.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    // 没有帖子且不是加载中时显示空状态
                    posts.isEmpty() -> {
                        EmptyView(
                            onRefresh = onRefresh,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // 有帖子数据时显示列表
                    else -> {
                        PostListComposable(
                            posts = posts,
                            isLoading = isLoading,
                            onItemClick = onItemClick,
                            onLoadMore = onLoadMore,
                            modifier = Modifier.fillMaxSize(),
                            // 传递 isRefreshing 参数
                            isRefreshing = isRefreshing
                        )
                    }
                }

                // 底部加载更多指示器 - 仅在有数据且正在加载更多时显示，且不在下拉刷新时显示
                if (isLoading && posts.isNotEmpty() && !isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }
            } 
        } 
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(16.dp), contentAlignment = Alignment.Center) {
         Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                style = TextStyle(color = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(16.dp)
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(8.dp)
            ) {
                Text(text = "重试")
            }
        }
    }
}

@Composable
private fun EmptyView(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "暂无内容",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRefresh,
                modifier = Modifier.padding(8.dp)
            ) {
                Text(text = "刷新")
            }
        }
    }
}