// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.

package me.voltual.pyrolysis.ui.plaza

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.data.unified.*
import me.voltual.pyrolysis.ui.*
import me.voltual.pyrolysis.ui.community.compose.CommentDialog
import me.voltual.pyrolysis.ui.community.compose.CommentItem
import me.voltual.pyrolysis.core.ui.components.LinkifyText
import me.voltual.pyrolysis.core.ui.theme.*
import me.voltual.pyrolysis.core.utils.DownloadManager
import me.voltual.pyrolysis.core.utils.formatTimestamp
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppDetailScreen(
    appId: String,
    versionId: Long,
    storeName: String,
    modifier: Modifier = Modifier,
    viewModel: AppDetailComposeViewModel = koinViewModel()
) {
    val context = LocalContext.current
    // Navigation 3 导航器
    val navigator = LocalNavigator.current

    val appDetail by viewModel.appDetail.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val showCommentDialog by viewModel.showCommentDialog.collectAsState()
    val showReplyDialog by viewModel.showReplyDialog.collectAsState()
    val currentReplyComment by viewModel.currentReplyComment.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showDownloadDrawer by viewModel.showDownloadDrawer.collectAsState()
    val downloadSources by viewModel.downloadSources.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 应用删除确认对话框
    var showDeleteAppDialog by remember { mutableStateOf(false) }
    // 评论删除确认对话框
    var showDeleteCommentDialog by remember { mutableStateOf(false) }
    var commentToDeleteId by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.downloadEvent.collectLatest { downloadEvent ->
            val activity = context as? Activity
            if (activity != null) {
                DownloadManager.download(
                    activity = activity,
                    url = downloadEvent.url,
                    fileName = downloadEvent.fileName,
                    headers = downloadEvent.headers
                )

                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "任务已发送至 1DM: ${downloadEvent.fileName}",
                        actionLabel = "管理下载",
                        withDismissAction = true,
                        duration = SnackbarDuration.Indefinite
                    )

                    when (result) {
                        SnackbarResult.ActionPerformed -> {
                            // 类型安全导航到下载管理
                            navigator.navigate(Download)
                        }
                        SnackbarResult.Dismissed -> { /* 忽略 */ }
                    }
                }
            }
        }
    }

    LaunchedEffect(appId, versionId, storeName) {
        viewModel.initializeData(appId, versionId, storeName)
    }

    LaunchedEffect(Unit) {
        viewModel.openUrlEvent.collectLatest { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("无法打开链接: $url")
            }
        }
    }

    // 监听 ViewModel 中的 snackbarEvent
    LaunchedEffect(viewModel.snackbarEvent) {
        viewModel.snackbarEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // 监听更新事件
    LaunchedEffect(viewModel.updateEvent) {
        viewModel.updateEvent.collectLatest { jsonString ->
            // 类型安全导航到更新应用页面
            navigator.navigate(UpdateAppRelease(jsonString))
        }
    }

    // 监听退款事件
    LaunchedEffect(Unit) {
        viewModel.refundEvent.collectLatest { refundInfo ->
            navigator.navigate(
                CreateRefundPost(
                    appId = refundInfo.appId.toLongOrNull() ?: 0L,
                    versionId = refundInfo.versionId,
                    appName = refundInfo.appName,
                    payMoney = refundInfo.payMoney
                )
            )
        }
    }

    // 监听支付导航事件
    LaunchedEffect(Unit) {
        viewModel.navigateToPaymentEvent.collectLatest { paymentInfo ->
            navigator.navigate(
                PaymentForApp(
                    appId = paymentInfo.appId,
                    appName = paymentInfo.appName,
                    versionId = paymentInfo.versionId,
                    price = paymentInfo.price,
                    iconUrl = paymentInfo.iconUrl,
                    previewContent = paymentInfo.previewContent
                )
            )
        }
    }

    // 监听 ViewModel 中的 navigateToDownloadEvent
    LaunchedEffect(viewModel.navigateToDownloadEvent) {
        viewModel.navigateToDownloadEvent.collectLatest { navigate ->
            if (navigate) {
                navigator.navigate(Download)
            }
        }
    }

    // 下拉刷新状态
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotEmpty()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(errorMessage)
            }
        }
    }

    // 处理分享功能
    fun handleShare() {
        appDetail?.let { detail ->
            when (detail.store) {
                AppStore.XIAOQU_SPACE -> {
                    val raw = detail.raw as? me.voltual.pyrolysis.KtorClient.AppDetail
                    val shareUrl = raw?.posturl
                    if (!shareUrl.isNullOrBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("应用链接", shareUrl)
                        clipboard.setPrimaryClip(clip)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("已复制分享链接: $shareUrl")
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("分享链接无效")
                        }
                    }
                }
                AppStore.SIENE_SHOP -> {
                    val shareUrl = "sinemarket://app/${detail.id}"
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("应用链接", shareUrl)
                    clipboard.setPrimaryClip(clip)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("已复制分享链接: $shareUrl")
                    }
                }
                else -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("暂不支持该商店的分享功能")
                    }
                }
            }
        }
    }

    // 监听 ViewModel 状态变化以结束刷新状态
    LaunchedEffect(isRefreshing, isLoading, appDetail, errorMessage) {
        if (!isLoading && (appDetail != null || errorMessage.isNotEmpty()) && isRefreshing) {
            isRefreshing = false
        }
    }

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
        if (isLoading && appDetail == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (appDetail != null) {
            val detail = appDetail!!
            val pageCount = when (detail.store) {
                AppStore.SIENE_SHOP -> 2
                else -> 1
            }
            val pagerState = rememberPagerState(pageCount = { pageCount })

            Column(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {
                        0 -> {
                            AppDetailContent(
                                appDetail = detail,
                                comments = comments,
                                onCommentReply = { comment -> viewModel.openReplyDialog(comment) },
                                onDownloadClick = { viewModel.handleDownloadClick() },
                                onCommentLongClick = { id: String ->
                                    commentToDeleteId = id
                                    showDeleteCommentDialog = true
                                },
                                onDeleteAppClick = { showDeleteAppDialog = true },
                                onShareClick = { handleShare() },
                                onMoreMenuClick = { },
                                onImagePreview = { url: String ->
                                    navigator.navigate(ImagePreview(url))
                                },
                                onRefundClick = { viewModel.requestRefund() },
                                onUpdateClick = { viewModel.requestUpdate() },
                                onFavoriteToggle = { viewModel.toggleFavorite() }
                            )
                        }
                        1 -> {
                            val packageName = detail.packageName
                            if (packageName.isNotEmpty()) {
                                VersionListScreen(
                                    packageName = packageName,
                                    storeName = detail.store.name,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("该应用无包名，无法获取版本列表")
                                }
                            }
                        }
                    }
                }
            }

            // FAB
            FloatingActionButton(
                onClick = { viewModel.openCommentDialog() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.AutoMirrored.Filled.Comment, "评论")
            }

            BBQSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }

    // 删除应用确认对话框
    if (showDeleteAppDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAppDialog = false },
            title = { Text("确认删除应用") },
            shape = AppShapes.medium,
            text = { Text("确定要删除此应用吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAppDialog = false
                        viewModel.deleteApp {
                            navigator.goBack()
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAppDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 删除评论确认对话框
    if (showDeleteCommentDialog && commentToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteCommentDialog = false },
            title = { Text("确认删除评论") },
            shape = AppShapes.medium,
            text = { Text("确定要删除这条评论吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteCommentDialog = false
                        commentToDeleteId?.let { viewModel.deleteComment(it) }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCommentDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    DownloadSourceDrawer(
        show = showDownloadDrawer,
        onDismissRequest = { viewModel.closeDownloadDrawer() },
        sources = downloadSources,
        onSourceSelected = { source ->
            viewModel.startDownload(source.url)
            viewModel.closeDownloadDrawer()
        }
    )

    if (showCommentDialog) {
        CommentDialog(
            hint = "输入评论...",
            onDismiss = { viewModel.closeCommentDialog() },
            context = context,
            onSubmit = { content, _ ->
                viewModel.submitComment(content)
            }
        )
    }

    if (showReplyDialog && currentReplyComment != null) {
        CommentDialog(
            hint = "回复 @${currentReplyComment!!.sender.displayName}",
            onDismiss = { viewModel.closeReplyDialog() },
            context = context,
            onSubmit = { content, _ ->
                viewModel.submitComment(content)
            }
        )
    }
}

/**
 * 应用详情内容组件 - 内部使用 LocalNavigator 进行导航，无需传递 NavController
 */
@Composable
fun AppDetailContent(
    appDetail: UnifiedAppDetail,
    comments: List<UnifiedComment>,
    onCommentReply: (UnifiedComment) -> Unit,
    onDownloadClick: () -> Unit,
    onCommentLongClick: (String) -> Unit,
    onDeleteAppClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreMenuClick: () -> Unit,
    onImagePreview: (String) -> Unit,
    onRefundClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    // 获取 Navigation 3 导航器（子组件内部使用）
    val navigator = LocalNavigator.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 应用头部信息 ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                when (appDetail.store) {
                    AppStore.XIAOQU_SPACE -> {
                        XiaoquSpaceAppHeader(
                            appDetail = appDetail,
                            onImagePreview = onImagePreview,
                            onDownloadClick = onDownloadClick,
                            onMoreMenuClick = { },
                            onShareClick = onShareClick,
                            onUpdateClick = onUpdateClick,
                            onRefundClick = onRefundClick,
                            onDeleteAppClick = onDeleteAppClick
                        )
                    }
                    else -> {
                        DefaultAppHeader(
                            appDetail = appDetail,
                            onImagePreview = onImagePreview,
                            onDownloadClick = onDownloadClick,
                            onMoreMenuClick = { },
                            onShareClick = onShareClick
                        )
                    }
                }
            }
        }

        if (appDetail.store == AppStore.SIENE_SHOP || appDetail.store == AppStore.LING_MARKET) {
            item {
                val favoriteState = UnifiedFavoriteState(
                    isFavorite = appDetail.isFavorite,
                    favoriteCount = appDetail.favoriteCount
                )
                AppFavoriteCard(
                    state = favoriteState,
                    onToggle = { onFavoriteToggle() }
                )
            }
        }

        // --- 更新日志 ---
        val updateLog = when (appDetail.store) {
            AppStore.SIENE_SHOP, AppStore.LING_MARKET -> appDetail.updateLog
            else -> null
        }
        if (!updateLog.isNullOrEmpty()) {
            item {
                UpdateLogSection(
                    appDetail = appDetail,
                    navigator = navigator  // 传递 navigator
                )
            }
        }

        // --- 适配说明（小趣空间） ---
        item {
            XiaoquSpaceExplainSection(
                appDetail = appDetail,
                navigator = navigator  // 传递 navigator
            )
        }

        // --- 应用信息卡片 ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "应用信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    when (appDetail.store) {
                        AppStore.XIAOQU_SPACE -> XiaoquSpaceAppInfo(appDetail = appDetail)
                        AppStore.SIENE_SHOP -> SineShopAppInfo(appDetail = appDetail)
                        AppStore.LING_MARKET -> LingMarketAppInfo(appDetail = appDetail)
                        else -> Text(
                            text = "⚠️什么都没有(||๐_๐)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // --- 应用介绍 ---
        item {
            AppDescriptionSection(
                appDetail = appDetail,
                navigator = navigator  // 传递 navigator
            )
        }

        // --- 应用截图 ---
        item {
            AppPreviewsSection(
                appDetail = appDetail,
                onImagePreview = onImagePreview
            )
        }

        // --- 作者信息 ---
        item {
            AppAuthorSection(
                appDetail = appDetail,
                navigator = navigator 
            )
        }

        // --- 评论列表 ---
        item {
            CommentsHeader(appDetail = appDetail)
        }

        if (comments.isEmpty()) {
            item {
                NoCommentsMessage()
            }
        } else {
            items(comments) { comment ->
                UnifiedCommentItem(
                    comment = comment,
                    onReply = { onCommentReply(comment) },
                    onLongClick = { onCommentLongClick(comment.id) },
                    onUserClick = {
                        val userId = comment.sender.id.toLongOrNull()
                        if (userId != null) {
                            navigator.navigate(UserDetail(userId, appDetail.store))
                        }
                    }
                )
            }
        }
    }
}