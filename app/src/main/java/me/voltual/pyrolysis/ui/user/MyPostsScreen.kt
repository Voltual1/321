// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.user

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
//import androidx.navigation.NavControllerNav2拜拜了
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.ui.*
import me.voltual.pyrolysis.ui.community.compose.BaseComposeListScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPostsScreen(
    viewModel: MyPostsViewModel,
    userId: Long, // 目标用户的ID
    nickname: String?,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    // Navigation 3 导航器
    val navigator = LocalNavigator.current

    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()

    LaunchedEffect(userId, nickname) {
        if (nickname != null) {
            viewModel.setUserInfo(userId, nickname)
        } else {
            viewModel.setUserId(userId)
        }
    }

    val onSearchClick: () -> Unit = {
        // 类型安全导航：传递 Search 路由对象
        navigator.navigate(
            Search(
                userId = userId.toString(),
                nickname = nickname
            )
        )
    }

    BaseComposeListScreen(
        title = if (nickname != null) "$nickname 的帖子" else "用户帖子",
        posts = posts,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onItemClick = { post ->
            // 类型安全导航：传递 PostDetail 路由对象
            navigator.navigate(PostDetail(post.postid))
        },
        onLoadMore = { viewModel.loadNextPage() },
        onRefresh = { viewModel.refresh() },
        onSearchClick = onSearchClick,
        snackbarHostState = snackbarHostState,
        onCreateClick = { navigator.navigate(CreatePost) },
        historyClick = { navigator.navigate(BrowseHistory) },
        totalPages = totalPages,
        onJumpToPage = { page -> viewModel.jumpToPage(page) },
        onMessageClick = { navigator.navigate(MessageCenter) },
        // 将字符串路由映射为类型安全的路由对象
        onNavigate = { route ->
            when {
                route == "community" -> navigator.navigate(Community)
                route == "hot_posts" -> navigator.navigate(HotPosts)
                route == "following_posts" -> navigator.navigate(FollowingPosts)
                route == "my_likes" -> navigator.navigate(MyLikes)
                route.startsWith("my_posts/") -> {
                    val parts = route.removePrefix("my_posts/").split("/")
                    val targetUserId = parts.first().toLongOrNull()
                    val targetNickname = if (parts.size > 1) parts[1] else null
                    if (targetUserId != null && targetUserId != userId) {
                        navigator.navigate(MyPosts(targetUserId, targetNickname))
                    }
                }
                else -> {
                    // 对于其他字符串路由，目前没有对应类型安全对象，可根据需求扩展
                    // 此处仅作保留，实际不应发生
                }
            }
        },
        onBackClick = { navigator.goBack() }
    )
}