// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.user

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.data.unified.UnifiedComment
import me.voltual.pyrolysis.ui.*
import me.voltual.pyrolysis.core.ui.theme.AppShapes
import me.voltual.pyrolysis.core.ui.theme.BBQCard
import me.voltual.pyrolysis.core.utils.formatTimestamp
import org.koin.androidx.compose.koinViewModel

@Composable
fun MyCommentsScreen(
    modifier: Modifier = Modifier,
    viewModel: MyCommentsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    // Navigation 3 导航器
    val navigator = LocalNavigator.current

    val comments by viewModel.comments.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedStore by viewModel.selectedStore.collectAsState()
    val showDeleteCommentDialog by viewModel.showDeleteCommentDialog.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 标题栏 - 移除商店选择器，只显示标题
        Spacer(Modifier.height(8.dp))

        // 评论列表
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading && comments.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (comments.isEmpty() && !isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Comment,
                        contentDescription = "暂无评论",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (error != null) "加载失败" else "暂无评论",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (error != null) {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(comments) { comment ->
                        MyCommentItem(
                            comment = comment,
                            onUserClick = { userId ->
                                // 类型安全导航：传递 UserDetail 路由对象
                                navigator.navigate(
                                    UserDetail(
                                        userId = userId.toLong(),
                                        store = selectedStore
                                    )
                                )
                            },
                            onOpenApp = { appId, versionId ->
                                // 类型安全导航：传递 AppDetail 路由对象
                                navigator.navigate(
                                    AppDetail(
                                        appId = appId,
                                        versionId = versionId,
                                        storeName = selectedStore.name
                                    )
                                )
                            },
                            onOpenUrl = { url ->
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // 错误处理
                                }
                            },
                            onDeleteComment = { commentId ->
                                viewModel.showDeleteCommentDialog(commentId)
                            }
                        )
                    }
                }
            }

            // 加载更多指示器
            if (isLoading && comments.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("加载更多...")
                    }
                }
            }
        }
    }

    // 删除评论确认对话框
    if (showDeleteCommentDialog != null) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteCommentDialog() },
            title = { Text("确认删除评论") },
            shape = AppShapes.medium,
            text = { Text("确定要删除这条评论吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteCommentDialog?.let { commentId ->
                            viewModel.deleteComment(commentId)
                        }
                    }
                ) {
                    Text(
                        "删除",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteCommentDialog() }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyCommentItem(
    comment: UnifiedComment,
    onUserClick: (String) -> Unit,
    onOpenApp: (String, Long) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDeleteComment: (String) -> Unit // 新增：删除评论回调
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 评论头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(comment.sender.avatarUrl ?: "https://static.sineshop.xin/images/user_avatar/default_avatar.png")
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build(),
                    contentDescription = "用户头像",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onUserClick(comment.sender.id) },
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = comment.sender.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatTimestamp(comment.sendTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 评论内容
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium
            )

            // 被删除的评论提示
            val appIdToCheck = comment.appId ?: comment.fatherReply?.appId
            if (appIdToCheck == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "评论的应用已被删除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // 回复的评论
            if (comment.fatherReply != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "回复 @${comment.fatherReply.sender.displayName}：",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = comment.fatherReply.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 如果评论关联了应用，显示查看应用按钮
                val appIdToShow = comment.appId?.takeIf { it != "-1" } ?: comment.fatherReply?.appId
                appIdToShow?.let { appId ->
                    Button(
                        onClick = {
                            val versionId = comment.versionId ?: 0L
                            onOpenApp(appId, versionId)
                        },
                        shape = AppShapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("查看应用")
                    }
                }

                // 删除评论按钮
                Button(
                    onClick = { onDeleteComment(comment.id) },
                    shape = AppShapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("删除评论")
                }
            }
        }
    }
}