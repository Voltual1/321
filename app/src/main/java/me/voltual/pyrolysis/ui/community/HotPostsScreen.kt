package me.voltual.pyrolysis.ui.community

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.voltual.pyrolysis.ui.*
import me.voltual.pyrolysis.ui.community.compose.BaseComposeListScreen
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotPostsScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: HotPostsViewModel = koinViewModel()
) {
    val navigator = LocalNavigator.current
    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (posts.isEmpty()) {
            viewModel.loadInitialData()
        }
    }

    BaseComposeListScreen(
        title = "热点",
        posts = posts,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onItemClick = { post -> navigator.navigate(PostDetail(post.postid)) },
        onLoadMore = { viewModel.loadNextPage() },
        onRefresh = { viewModel.refresh() },
        onSearchClick = { navigator.navigate(Search(userId = null, nickname = null)) },
        onCreateClick = { navigator.navigate(CreatePost) },
        snackbarHostState = snackbarHostState,
        historyClick = { navigator.navigate(BrowseHistory) },
        totalPages = totalPages,
        onJumpToPage = { page -> viewModel.jumpToPage(page) },
        onMessageClick = { navigator.navigate(MessageCenter) },
        onNavigate = { route ->
            when {
                route == "community" -> navigator.navigate(Community)
                route == "hot_posts" -> navigator.navigate(HotPosts)
                route == "following_posts" -> navigator.navigate(FollowingPosts)
                route == "my_likes" -> navigator.navigate(MyLikes)
                route.startsWith("my_posts/") -> {
                    val parts = route.removePrefix("my_posts/").split("/")
                    val userIdStr = parts.firstOrNull()
                    val nickname = if (parts.size > 1) parts[1] else "我"
                    userIdStr?.toLongOrNull()?.let { userId ->
                        navigator.navigate(MyPosts(userId, nickname))
                    }
                }
            }
        },
        onBackClick = { navigator.goBack() }
    )
}