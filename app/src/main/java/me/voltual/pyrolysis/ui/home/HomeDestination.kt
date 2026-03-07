// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.AuthManager
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.SineShopClient
import me.voltual.pyrolysis.restartMainActivity
import me.voltual.pyrolysis.ui.*
import me.voltual.pyrolysis.core.ui.theme.BBQTheme
import me.voltual.pyrolysis.core.ui.theme.ThemeManager

@Composable
fun HomeDestination(
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel()
    val uiState by viewModel.uiState

    // Navigation 3 导航器
    val navigator = LocalNavigator.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val userCredentialsFlow = AuthManager.getCredentials(context)
        val userCredentials = userCredentialsFlow.first()
        val isLoggedIn = userCredentials.token.isNotEmpty()

        viewModel.checkAndUpdateLingMarketLoginState(context)
        viewModel.updateLoginState(isLoggedIn)
        if (isLoggedIn && uiState.dataLoadState == DataLoadState.NotLoaded) {
            viewModel.loadUserData(context)
        }
        viewModel.checkAndUpdateSineShopLoginState(context)
    }

    val onLingMarketLoginClick = remember {
        { navigator.navigate(Login) }
    }

    val onAvatarClick = remember {
        {
            if (!uiState.showLoginPrompt) {
                viewModel.toggleDarkMode()
                val modeName = if (ThemeManager.isAppDarkTheme) "深色" else "亮色"
                viewModel.showSnackbar(context.getString(R.string.theme_changed, modeName))
            } else {
                navigator.navigate(Login)
            }
        }
    }

    val onAvatarLongClick = remember {
        {
            if (!uiState.showLoginPrompt) {
                viewModel.refreshUserData(context)
                viewModel.checkAndUpdateSineShopLoginState(context)
            }
            restartMainActivity(context)
        }
    }

    val onLoginClick = remember {
        { navigator.navigate(Login) }
    }

    val onSineShopLoginClick = remember {
        { navigator.navigate(Login) }
    }

    val userIdFlow = AuthManager.getUserId(context)

    BBQTheme(appDarkTheme = ThemeManager.isAppDarkTheme) {
        HomeScreen(
            state = HomeState(
                showLoginPrompt = uiState.showLoginPrompt,
                isLoading = uiState.isLoading,
                avatarUrl = uiState.avatarUrl,
                nickname = uiState.nickname,
                level = uiState.level,
                coins = uiState.coins,
                exp = uiState.exp,
                userId = uiState.userId,
                followersCount = uiState.followersCount,
                fansCount = uiState.fansCount,
                postsCount = uiState.postsCount,
                likesCount = uiState.likesCount,
                seriesDays = uiState.seriesDays,
                signStatusMessage = uiState.signStatusMessage,
                displayDaysDiff = uiState.displayDaysDiff
            ),
            sineShopUserInfo = uiState.sineShopUserInfo,
            sineShopLoginPrompt = uiState.sineShopLoginPrompt,
            lingMarketUserInfo = uiState.lingMarketUserInfo,
            lingMarketLoginPrompt = uiState.lingMarketLoginPrompt,
            onSineShopLoginClick = onSineShopLoginClick,
            onLingMarketLoginClick = onLingMarketLoginClick,
            onPaymentCenterClick = { navigator.navigate(PaymentCenterAdvanced) },
            onAvatarClick = onAvatarClick,
            onAvatarLongClick = onAvatarLongClick,
            onMessageCenterClick = { navigator.navigate(MessageCenter) },
            onBrowseHistoryClick = { navigator.navigate(BrowseHistory) },
            onMyLikesClick = { navigator.navigate(MyLikes) },
            onFollowersClick = { navigator.navigate(FollowList) },
            onFansClick = { navigator.navigate(FanList) },
            onPostsClick = {
                coroutineScope.launch {
                    val userId = userIdFlow.first()
                    if (userId > 0) {
                        val nickname = uiState.nickname ?: "用户"
                        // 类型安全导航，直接传递路由对象
                        navigator.navigate(MyPosts(userId, nickname))
                    } else {
                        viewModel.showSnackbar(context.getString(R.string.unable_to_get_userid))
                    }
                }
            },
            onMyResourcesClick = {
                coroutineScope.launch {
                    val userId = userIdFlow.first()
                    if (userId > 0) {
                        // 类型安全导航
                        navigator.navigate(ResourcePlaza(isMyResource = true, userId = userId))
                    } else {
                        viewModel.showSnackbar(context.getString(R.string.login_first_my_resources))
                        navigator.navigate(Login)
                    }
                }
            },
            onBillingClick = { navigator.navigate(Billing) },
            onLoginClick = onLoginClick,
            onSettingsClick = { navigator.navigate(ThemeCustomize) },
            onSignClick = { viewModel.signIn(context) },
            onAboutClick = { navigator.navigate(About) },
            onAccountProfileClick = { navigator.navigate(AccountProfile(AppStore.XIAOQU_SPACE)) },
            onRecalculateDays = { viewModel.recalculateDaysDiff() },
            onNavigateToUpdate = { navigator.navigate(Update) },
            onNavigateToMyReviews = { navigator.navigate(MyReviews) },
            onNavigateToMyComments = { navigator.navigate(MyComments) },
            onNavigateToCreateAppRelease = { navigator.navigate(CreateAppRelease) },
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel,
            snackbarHostState = snackbarHostState
            // navController 参数已移除
        )
    }
}