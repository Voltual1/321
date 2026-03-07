// Copyright (C) 2025 Voltual
package me.voltual.pyrolysis.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.navigation3.runtime.NavBackStack
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.*
import androidx.navigation3.scene.DialogSceneStrategy
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.ui.auth.LoginScreen
import me.voltual.pyrolysis.ui.auth.LoginViewModel
import me.voltual.pyrolysis.ui.billing.BillingScreen
import me.voltual.pyrolysis.ui.billing.BillingViewModel
import me.voltual.pyrolysis.ui.community.*
import me.voltual.pyrolysis.ui.community.compose.PostDetailScreen
import me.voltual.pyrolysis.core.ui.components.IDMTransferDialog
import me.voltual.pyrolysis.ui.home.*
import me.voltual.pyrolysis.ui.log.LogScreen
import me.voltual.pyrolysis.ui.log.LogViewModel
import me.voltual.pyrolysis.ui.message.MessageCenterScreen
import me.voltual.pyrolysis.ui.message.MessageViewModel
import me.voltual.pyrolysis.ui.payment.PaymentCenterScreen
import me.voltual.pyrolysis.ui.payment.PaymentType
import me.voltual.pyrolysis.ui.payment.PaymentViewModel
import me.voltual.pyrolysis.ui.player.PlayerScreen
import me.voltual.pyrolysis.ui.player.PlayerViewModel
import me.voltual.pyrolysis.ui.settings.repos.PrefsReposPage
import me.voltual.pyrolysis.ui.plaza.*
import me.voltual.pyrolysis.ui.rank.RankingListScreen
import me.voltual.pyrolysis.ui.search.SearchScreen
import me.voltual.pyrolysis.ui.search.SearchViewModel
import me.voltual.pyrolysis.ui.settings.signin.SignInSettingsScreen
import me.voltual.pyrolysis.ui.settings.storage.StoreManagerScreen
import me.voltual.pyrolysis.ui.settings.update.UpdateSettingsScreen
import me.voltual.pyrolysis.core.ui.theme.ThemeCustomizeScreen
import me.voltual.pyrolysis.ui.update.UpdateScreen
import me.voltual.pyrolysis.ui.update.UpdateViewModel
import me.voltual.pyrolysis.ui.user.*
import me.voltual.pyrolysis.ui.user.compose.UserListScreen
import androidx.compose.foundation.*
import androidx.navigation3.ui.NavDisplay
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.compose.material3.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.*
import me.voltual.pyrolysis.core.ui.animation.*

@Composable
fun BBQNavDisplay(
    backStack: List<NavKey>,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val mySceneStrategy = remember { DialogSceneStrategy<NavKey>() }
    val slideDistance = rememberSlideDistance() // 获取 30dp 对应的像素值
    
    val decorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(), // 保持 UI 状态（如滚动位置）
        rememberViewModelStoreNavEntryDecorator<NavKey>()      // 核心：为每个 Entry 提供独立的 ViewModel 存储
    )

    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        entryDecorators = decorators, // 传入装饰器
        modifier = modifier.fillMaxSize(),
        sceneStrategy = mySceneStrategy,
        
        //  前进动画：当新页面入栈时触发
        transitionSpec = {
            materialSharedAxisX(
                forward = true, 
                slideDistance = slideDistance
            )
        },

        //  返回动画：当页面出栈（Pop）时触发
        popTransitionSpec = {
            materialSharedAxisX(
                forward = false, 
                slideDistance = slideDistance
            )
        },
        // 使用手动实现的 entryProvider 闭包
        entryProvider = { key ->
            when (key) {
                is Home -> NavEntry(key) {
                    HomeDestination(snackbarHostState = snackbarHostState)
                }

                is Login -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    val viewModel: LoginViewModel = koinViewModel()
                    LoginScreen(
                        viewModel = viewModel,
                        onLoginSuccess = { navigator.goBack() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is About -> NavEntry(key) {
                    AboutScreen(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHostState = snackbarHostState
                    )
                }

                is Search -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    val viewModel: SearchViewModel = koinViewModel()
                    val userIdLong = key.userId?.toLongOrNull()
                    val nickname = key.nickname

                    LaunchedEffect(userIdLong, nickname) {
                        if (userIdLong != null && nickname != null) {
                            viewModel.initFromNavArgs(userIdLong, nickname)
                        }
                    }
                    SearchScreen(
                        viewModel = viewModel,
                        onPostClick = { postId -> navigator.navigate(PostDetail(postId)) },
                        onLogClick = { navigator.navigate(LogViewer) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is SignInSettings -> NavEntry(key) {
                    SignInSettingsScreen(snackbarHostState = snackbarHostState)
                }

                is ThemeCustomize -> NavEntry(key) {
                    ThemeCustomizeScreen(modifier = Modifier.fillMaxSize())
                }

                is StoreManager -> NavEntry(key) {
                    StoreManagerScreen()
                }

                is PostDetail -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    val viewModel: PostDetailViewModel = koinViewModel()
                    PostDetailScreen(
                        postId = key.postId,
                        onPostDeleted = { navigator.goBack() },
                        viewModel = viewModel,   
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                is PrefsReposPage -> NavEntry(key) {
                    PrefsReposPage()                    
                }

                is CreatePost -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    val viewModel: PostCreateViewModel = koinViewModel()
                    PostCreateScreen(
                        viewModel = viewModel,
                        onBackClick = { navigator.goBack() },
                        mode = "create",
                        refundAppName = "",
                        refundAppId = 0L,
                        refundVersionId = 0L,
                        refundPayMoney = 0,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is CreateRefundPost -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    val viewModel: PostCreateViewModel = koinViewModel()
                    PostCreateScreen(
                        viewModel = viewModel,
                        onBackClick = { navigator.goBack() },
                        mode = "refund",
                        refundAppName = key.appName,
                        refundAppId = key.appId,
                        refundVersionId = key.versionId,
                        refundPayMoney = key.payMoney,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is BrowseHistory -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    val viewModel: BrowseHistoryViewModel = koinViewModel() // 显式获取 ViewModel
                    BrowseHistoryScreen(
                        onPostClick = { postId -> navigator.navigate(PostDetail(postId)) },
                        viewModel = viewModel, // 传递已获取的实例
                        modifier = Modifier.fillMaxSize(),
                        snackbarHostState = snackbarHostState
                    )
                }

                is ImagePreview -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    ImagePreviewScreen(
                        imageUrl = key.imageUrl,
                        snackbarHostState = snackbarHostState,
                        onClose = { navigator.goBack() }
                    )
                }

                is Download -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    DownloadHandler(onBack = { navigator.goBack() })
                }

                is MyComments -> NavEntry(key) {
                    MyCommentsScreen(modifier = Modifier.fillMaxSize())
                }

                is MyReviews -> NavEntry(key) {
                    MyReviewsScreen(modifier = Modifier.fillMaxSize())
                }

                is UserDetail -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    val viewModel: UserDetailViewModel = koinViewModel()
                    LaunchedEffect(key.userId, key.store) {
                        viewModel.loadUserDetails(key.userId, key.store)
                    }
                    UserDetailScreen(
                        viewModel = viewModel,
                        onPostsClick = {
                            val userData = viewModel.userData.value
                            val nickname = userData?.displayName ?: "用户"
                            navigator.navigate(MyPosts(key.userId, nickname))
                        },
                        onResourcesClick = { uid, targetStore ->
                            navigator.navigate(
                                ResourcePlaza(
                                    isMyResource = false,
                                    userId = uid,
                                    mode = "public",
                                    storeName = targetStore.name
                                )
                            )
                        },
                        onImagePreview = { imageUrl ->
                            navigator.navigate(ImagePreview(imageUrl))
                        },
                        modifier = Modifier.fillMaxSize(),
                        snackbarHostState = snackbarHostState
                    )
                }

                is MyPosts -> NavEntry(key) {
                    val viewModel: MyPostsViewModel = koinViewModel()
                    MyPostsScreen(
                        viewModel = viewModel,
                        userId = key.userId,
                        nickname = key.nickname,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is FollowList -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    val viewModel: UserListViewModel = koinViewModel()
                    LaunchedEffect(Unit) {
                        viewModel.setListType(UserListType.FOLLOWERS)
                        viewModel.loadInitialData()
                    }
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    UserListScreen(
                        users = state.users,
                        isLoading = state.isLoading,
                        errorMessage = state.errorMessage,
                        onLoadMore = { viewModel.loadNextPage() },
                        onUserClick = { userId -> navigator.navigate(UserDetail(userId)) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is FanList -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    val viewModel: UserListViewModel = koinViewModel()
                    LaunchedEffect(Unit) {
                        viewModel.setListType(UserListType.FANS)
                        viewModel.loadInitialData()
                    }
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    UserListScreen(
                        users = state.users,
                        isLoading = state.isLoading,
                        errorMessage = state.errorMessage,
                        onLoadMore = { viewModel.loadNextPage() },
                        onUserClick = { userId -> navigator.navigate(UserDetail(userId)) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is AccountProfile -> NavEntry(key) {
                    val viewModel: UserProfileViewModel = koinViewModel()
                    AccountProfileScreen(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHostState = snackbarHostState,
                        viewModel = viewModel,
                        store = key.store
                    )
                }

                is ResourcePlaza -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    ResourcePlazaScreen(
                        isMyResourceMode = key.isMyResource,
                        mode = key.mode,
                        storeName = key.storeName,
                        navigateToAppDetail = { appId, versionId, store ->
                            navigator.navigate(AppDetail(appId, versionId, store))
                        },
                        userId = if (key.userId != -1L) key.userId.toString() else null,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is AppDetail -> NavEntry(key) {
                    AppDetailScreen(
                        appId = key.appId,
                        versionId = key.versionId,
                        storeName = key.storeName,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                is AppPage -> NavEntry(key) {
                    AppPage(
                        packageName = key.packageName,
                        onDismiss = onBack
                    )
                }
                
                is SearchPage -> NavEntry(key) {
                    SearchPage(
                        onDismiss = onBack
                    )
                }

                is CreateAppRelease -> NavEntry(key) {
                    val viewModel: AppReleaseViewModel = koinViewModel()
                    AppReleaseScreen(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is UpdateAppRelease -> NavEntry(key) {
                    val viewModel: AppReleaseViewModel = koinViewModel()
                    if (key.appDetailJson.isNotBlank()) {
                        val appDetail = KtorClient.JsonConverter.fromJson(key.appDetailJson)
                        if (appDetail != null) {
                            viewModel.populateFromAppDetail(appDetail)
                        }
                    }
                    AppReleaseScreen(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is LogViewer -> NavEntry(key) {
                    val viewModel: LogViewModel = koinViewModel()
                    LogScreen(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is MessageCenter -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    val viewModel: MessageViewModel = koinViewModel()
                    MessageCenterScreen(
                        viewModel = viewModel,
                        onMessageClick = { postId -> navigator.navigate(PostDetail(postId)) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is Billing -> NavEntry(key) {
                    val viewModel: BillingViewModel = koinViewModel()
                    LaunchedEffect(Unit) { viewModel.loadBilling() }
                    BillingScreen(viewModel = viewModel)
                }

                is PaymentCenterAdvanced -> NavEntry(key) {
                    val viewModel: PaymentViewModel = koinViewModel()
                    viewModel.setPaymentInfo(type = PaymentType.POST_REWARD, locked = false)
                    PaymentCenterScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is PaymentForApp -> NavEntry(key) {
                    val viewModel: PaymentViewModel = koinViewModel()
                    viewModel.setPaymentInfo(
                        type = PaymentType.APP_PURCHASE,
                        appId = key.appId,
                        appName = key.appName,
                        versionId = key.versionId,
                        price = key.price,
                        iconUrl = key.iconUrl,
                        previewContent = key.previewContent,
                        locked = true
                    )
                    PaymentCenterScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is PaymentForPost -> NavEntry(key) {
                    val viewModel: PaymentViewModel = koinViewModel()
                    viewModel.setPaymentInfo(
                        type = PaymentType.POST_REWARD,
                        postId = key.postId,
                        postTitle = key.postTitle,
                        previewContent = key.previewContent,
                        authorName = key.authorName,
                        authorAvatar = key.authorAvatar,
                        postTime = key.postTime,
                        locked = false
                    )
                    PaymentCenterScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is UpdateSettings -> NavEntry(key) {
                    UpdateSettingsScreen(snackbarHostState = snackbarHostState)
                }

                is Community -> NavEntry(key) {
                    CommunityScreen(snackbarHostState = snackbarHostState)
                }

                is MyLikes -> NavEntry(key) {
                    MyLikesScreen(snackbarHostState = snackbarHostState)
                }

                is HotPosts -> NavEntry(key) {
                    HotPostsScreen(snackbarHostState = snackbarHostState)
                }

                is FollowingPosts -> NavEntry(key) {
                    FollowingPostsScreen(snackbarHostState = snackbarHostState)
                }

                is RankingList -> NavEntry(key) {
                    RankingListScreen()
                }

                is Player -> NavEntry(key) {
                    val navigator = LocalNavigator.current
                    val viewModel: PlayerViewModel = koinViewModel()
                    LaunchedEffect(key.bvid) { viewModel.loadVideoData(key.bvid) }
                    PlayerScreen(
                        viewModel = viewModel,
                        onBack = { navigator.goBack() }
                    )
                }

                is Update -> NavEntry(key) {
                    val viewModel: UpdateViewModel = koinViewModel()
                    UpdateScreen(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                is Explore -> NavEntry(key) {
                    ExplorePage()                    
                }
                
                is SortFilterSheet -> NavEntry(key) {
                    SortFilterSheet(onDismiss = onBack)
//                    backStack.removeLastOrNull()                    
                }

                // 保底逻辑
                else -> NavEntry(key) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("Unknown Key: ${key::class.simpleName}", color = Color.Red)
                    }
                }
            }
        }
    )
}
@Composable
 fun DownloadHandler(onBack: () -> Unit) {
    val context = LocalContext.current
    var showInstallDialog by remember { mutableStateOf(false) }

    val idmPackages = listOf(
        "idm.internet.download.manager.plus",
        "idm.internet.download.manager",
        "idm.internet.download.manager.adm.lite"
    )

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        var targetIntent: Intent? = null

        for (pkg in idmPackages) {
            targetIntent = pm.getLaunchIntentForPackage(pkg)
            if (targetIntent != null) break
        }

        if (targetIntent != null) {
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(targetIntent) } catch (_: Exception) {}
            onBack()
        } else {
            showInstallDialog = true
        }
    }

    if (showInstallDialog) {
        IDMTransferDialog(onDismiss = onBack)
    }
}