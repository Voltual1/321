// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.

package me.voltual.pyrolysis.ui.user

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.data.unified.FollowStatus
import me.voltual.pyrolysis.data.unified.UnifiedUserDetail
import me.voltual.pyrolysis.core.ui.theme.*
import me.voltual.pyrolysis.core.utils.formatTimestamp

@Composable
fun UserDetailScreen(
    viewModel: UserDetailViewModel,
    onPostsClick: () -> Unit,
    onResourcesClick: (Long, AppStore) -> Unit,
    onImagePreview: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState
) {
    // 从 ViewModel 获取状态
    val userData by viewModel.userData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // 下拉刷新状态
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // 监听 ViewModel 状态变化以结束刷新状态
    LaunchedEffect(isLoading, errorMessage, userData) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
        }
    }

    // 使用 MD3 的 PullToRefreshBox
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refresh()
        },
        state = pullRefreshState,
        indicator = {
            BBQPullRefreshIndicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        ScreenContent(
            modifier = Modifier.fillMaxSize(),
            userData = userData,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage,
            onPostsClick = onPostsClick,
            onResourcesClick = onResourcesClick,
            onImagePreview = onImagePreview,
            snackbarHostState = snackbarHostState,
            viewModel = viewModel
        )
    }
}

@Composable
private fun ScreenContent(
    modifier: Modifier = Modifier,
    userData: UnifiedUserDetail?,
    isLoading: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    onPostsClick: () -> Unit,
    onResourcesClick: (Long, AppStore) -> Unit,
    onImagePreview: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: UserDetailViewModel
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when {
            isLoading && !isRefreshing -> LoadingState(Modifier.align(Alignment.Center))
            !errorMessage.isNullOrEmpty() -> ErrorState(message = errorMessage, modifier = Modifier.align(Alignment.Center))
            userData == null -> EmptyState(modifier = Modifier.align(Alignment.Center))
            else -> {
                BBQSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
                when (userData.store) {
                    AppStore.XIAOQU_SPACE -> XiaoQuProfileContent(
                        userData = userData,
                        onPostsClick = onPostsClick,
                        onResourcesClick = onResourcesClick,
                        onImagePreview = onImagePreview,
                        snackbarHostState = snackbarHostState,
                        viewModel = viewModel,
                        isRefreshing = isRefreshing
                    )
                    AppStore.SIENE_SHOP -> SieneShopProfileContent(
                        userData = userData,
                        onResourcesClick = onResourcesClick,
                        onImagePreview = onImagePreview,
                        snackbarHostState = snackbarHostState
                    )
                    else -> Text("不支持的应用商店")
                }
            }
        }
    }
}

@Composable
private fun XiaoQuProfileContent(
    userData: UnifiedUserDetail,
    onPostsClick: () -> Unit,
    onResourcesClick: (Long, AppStore) -> Unit,
    onImagePreview: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: UserDetailViewModel,
    isRefreshing: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderCard(
            userData = userData,
            onAvatarClick = {
                if (!userData.avatarUrl.isNullOrEmpty()) {
                    onImagePreview(userData.avatarUrl)
                }
            }
        )
        ActionButtonsRow(
            userData = userData,
            onResourcesClick = { userId ->
                onResourcesClick(userId, userData.store)
            },
            snackbarHostState = snackbarHostState,
            viewModel = viewModel,
            isRefreshing = isRefreshing
        )
        StatsCard(
            userData = userData,
            onPostsClick = onPostsClick
        )
        DetailsCard(userData = userData)
    }
}

@Composable
private fun SieneShopProfileContent(
    userData: UnifiedUserDetail,
    onResourcesClick: (Long, AppStore) -> Unit,
    onImagePreview: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BBQCard {
            Box(modifier = Modifier.fillMaxWidth()) {
                ProfileBackground()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(userData.avatarUrl ?: "https://static.sineshop.xin/images/user_avatar/default_avatar.png")
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .build(),
                        contentDescription = "用户头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (!userData.avatarUrl.isNullOrEmpty()) {
                                    onImagePreview(userData.avatarUrl)
                                }
                            }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = userData.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "ID: ${userData.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        BBQCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "详细信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                userData.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                InfoItem(label = "上传数量:", value = userData.uploadCount?.toString() ?: "0")
                InfoItem(label = "评论数量:", value = userData.replyCount?.toString() ?: "0")
                InfoItem(label = "加入时间:", value = userData.joinTime?.let { formatTimestamp(it) } ?: "无")
                InfoItem(label = "上次登录设备:", value = userData.lastLoginDevice ?: "无")
                InfoItem(label = "上次在线:", value = userData.lastOnlineTime?.let { formatTimestamp(it) } ?: "无")
                InfoItem(label = "绑定QQ:", value = userData.bindQq?.toString() ?: "无")
            }
        }

        BBQOutlinedButton(
            onClick = { onResourcesClick(userData.id, userData.store) },
            modifier = Modifier.fillMaxWidth(),
            text = { Text("${userData.displayName}的资源") }
        )
    }
}

@Composable
private fun HeaderCard(
    userData: UnifiedUserDetail,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BBQCard(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ProfileBackground()
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(
                        avatarUrl = userData.avatarUrl ?: "",
                        onAvatarClick = onAvatarClick
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    UserBasicInfo(userData = userData)
                }
            }
        }
    }
}

@Composable
private fun ProfileBackground() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
private fun UserAvatar(
    avatarUrl: String,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(avatarUrl)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build(),
        contentDescription = "用户头像",
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.ic_menu_profile),
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .clickable(onClick = onAvatarClick)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
    )
}

@Composable
private fun UserBasicInfo(userData: UnifiedUserDetail) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = userData.displayName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            userData.hierarchy?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = AppShapes.small
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "ID: ${userData.username}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionButtonsRow(
    userData: UnifiedUserDetail,
    onResourcesClick: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: UserDetailViewModel,
    isRefreshing: Boolean
) {
    val coroutineScope = rememberCoroutineScope()

    val followStatus = userData.followStatus
    val isProcessingFollowAction by viewModel.isLoading.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (userData.store == AppStore.XIAOQU_SPACE && followStatus != null) {
            when (followStatus) {
                FollowStatus.NotFollowed -> {
                    BBQButton(
                        onClick = {
                            if (!isRefreshing) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "已关注 ${userData.displayName}，稍后将会刷新数据",
                                        duration = SnackbarDuration.Short
                                    )
                                    viewModel.followUser(userData.id)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        text = {
                            if (!isRefreshing && isProcessingFollowAction) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("关注")
                            }
                        },
                        enabled = !isRefreshing && !isProcessingFollowAction
                    )
                }

                FollowStatus.YouFollowed -> {
                    BBQOutlinedButton(
                        onClick = {
                            if (!isRefreshing) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "已取消关注 ${userData.displayName}，稍后将会刷新数据",
                                        duration = SnackbarDuration.Short
                                    )
                                    viewModel.unfollowUser(userData.id)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        text = {
                            if (!isRefreshing && isProcessingFollowAction) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("已关注")
                            }
                        },
                        enabled = !isRefreshing && !isProcessingFollowAction
                    )
                }

                FollowStatus.FollowedYou -> {
                    BBQButton(
                        onClick = {
                            if (!isRefreshing) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "已回关 ${userData.displayName}，稍后将会刷新数据",
                                        duration = SnackbarDuration.Short
                                    )
                                    viewModel.followUser(userData.id)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        text = {
                            if (!isRefreshing && isProcessingFollowAction) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("回关")
                            }
                        },
                        enabled = !isRefreshing && !isProcessingFollowAction
                    )
                }

                FollowStatus.MutualFollow -> {
                    BBQOutlinedButton(
                        onClick = {
                            if (!isRefreshing) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "已取消关注 ${userData.displayName}，稍后将会刷新数据",
                                        duration = SnackbarDuration.Short
                                    )
                                    viewModel.unfollowUser(userData.id)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        text = {
                            if (!isRefreshing && isProcessingFollowAction) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("互相关注")
                            }
                        },
                        enabled = !isRefreshing && !isProcessingFollowAction
                    )
                }
            }
        }

        BBQOutlinedButton(
            onClick = { onResourcesClick(userData.id) },
            modifier = Modifier.weight(1f),
            text = { Text("${userData.displayName}的资源") }
        )
    }
}

@Composable
private fun StatsCard(
    userData: UnifiedUserDetail,
    onPostsClick: () -> Unit
) {
    BBQCard {
        UserStats(
            userData = userData,
            onPostsClick = onPostsClick,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun UserStats(
    userData: UnifiedUserDetail,
    onPostsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        userData.followersCount?.let {
            StatItem(
                count = it,
                label = "关注",
                modifier = Modifier.weight(1f)
            )
            VerticalDivider()
        }
        userData.fansCount?.let {
            StatItem(
                count = it,
                label = "粉丝",
                modifier = Modifier.weight(1f)
            )
            VerticalDivider()
        }
        userData.postCount?.let {
            StatItem(
                count = it,
                label = "帖子",
                onClick = onPostsClick,
                modifier = Modifier.weight(1f)
            )
            VerticalDivider()
        }
        userData.likeCount?.let {
            StatItem(
                count = it,
                label = "获赞",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DetailsCard(userData: UnifiedUserDetail) {
    BBQCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "详细信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            userData.money?.let {
                InfoItem(label = "硬币:", value = it.toString())
            }
            userData.commentCount?.let {
                InfoItem(label = "评论数:", value = it.toString())
            }
            userData.seriesDays?.let {
                InfoItem(label = "总签到:", value = "$it 天")
            }
            userData.lastActivityTime?.let {
                InfoItem(label = "上次在线:", value = it)
            }
        }
    }
}

@Composable
private fun VerticalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(1.dp)
            .height(32.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    )
}

@Composable
private fun StatItem(
    count: String,
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = abbreviateNumber(count),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun abbreviateNumber(count: String): String {
    return try {
        val number = count.toLong()
        when {
            number >= 1_000_000 -> "%.1fM".format(number / 1_000_000.0)
            number >= 10_000 -> "%.1fW".format(number / 10_000.0)
            number >= 1_000 -> "%.1fK".format(number / 1_000.0)
            else -> count
        }
    } catch (e: NumberFormatException) {
        count
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "未找到用户数据",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}