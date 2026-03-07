// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.home

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.voltual.pyrolysis.LingMarketClient
import me.voltual.pyrolysis.R
import me.voltual.pyrolysis.SineShopClient
import me.voltual.pyrolysis.ui.*
import me.voltual.pyrolysis.core.ui.theme.AppShapes
import me.voltual.pyrolysis.core.ui.theme.BBQBackgroundCard

@Composable
fun HomeScreen(
    state: HomeState,
    sineShopUserInfo: SineShopClient.SineShopUserInfo?,
    sineShopLoginPrompt: Boolean,
    lingMarketUserInfo: LingMarketClient.LingMarketUser?,
    lingMarketLoginPrompt: Boolean,
    onSineShopLoginClick: () -> Unit,
    onLingMarketLoginClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit,
    onMessageCenterClick: () -> Unit,
    onBrowseHistoryClick: () -> Unit,
    onMyLikesClick: () -> Unit,
    onFollowersClick: () -> Unit,
    onFansClick: () -> Unit,
    onPostsClick: () -> Unit,
    onMyResourcesClick: () -> Unit,
    onBillingClick: () -> Unit,
    onLoginClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPaymentCenterClick: () -> Unit,
    onSignClick: () -> Unit,
    onRecalculateDays: () -> Unit,
    onAboutClick: () -> Unit,
    onAccountProfileClick: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    onNavigateToMyComments: () -> Unit,
    onNavigateToMyReviews: () -> Unit,
    onNavigateToCreateAppRelease: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    snackbarHostState: SnackbarHostState
) {
    // Navigation 3 导航器
    val navigator = LocalNavigator.current

    val pagerState = rememberPagerState(pageCount = { 3 })

    LaunchedEffect(Unit) {
        viewModel.setSnackbarHostState(snackbarHostState)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> {
                XiaoquSpaceHomePage(
                    state = state,
                    onAvatarClick = onAvatarClick,
                    onAvatarLongClick = onAvatarLongClick,
                    onMessageCenterClick = onMessageCenterClick,
                    onBrowseHistoryClick = onBrowseHistoryClick,
                    onMyLikesClick = onMyLikesClick,
                    onFollowersClick = onFollowersClick,
                    onFansClick = onFansClick,
                    onPostsClick = onPostsClick,
                    onMyResourcesClick = onMyResourcesClick,
                    onBillingClick = onBillingClick,
                    onLoginClick = onLoginClick,
                    onSettingsClick = onSettingsClick,
                    onPaymentCenterClick = onPaymentCenterClick,
                    onSignClick = onSignClick,
                    onRecalculateDays = onRecalculateDays,
                    onAboutClick = onAboutClick,
                    onAccountProfileClick = onAccountProfileClick,
                    onSineShopLoginClick = onSineShopLoginClick,
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState
                )
            }
            1 -> {
                if (sineShopLoginPrompt) {
                    SineShopLoginPrompt(onSineShopLoginClick = onSineShopLoginClick)
                } else {
                    SineShopProfileScreen(
                        userInfo = sineShopUserInfo,
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToResourcePlaza = { mode, targetStoreName ->
                            // 使用 Navigation 3 类型安全导航
                            when (mode) {
                                "my_upload" -> {
                                    navigator.navigate(
                                        ResourcePlaza(
                                            isMyResource = true,
                                            mode = "my_upload",
                                            storeName = targetStoreName
                                        )
                                    )
                                }
                                "my_favourite" -> {
                                    navigator.navigate(
                                        ResourcePlaza(
                                            isMyResource = true,
                                            mode = "my_favourite",
                                            storeName = targetStoreName
                                        )
                                    )
                                }
                                "my_history" -> {
                                    navigator.navigate(
                                        ResourcePlaza(
                                            isMyResource = true,
                                            mode = "my_history",
                                            storeName = targetStoreName
                                        )
                                    )
                                }
                                else -> {
                                    navigator.navigate(
                                        ResourcePlaza(
                                            isMyResource = false,
                                            storeName = targetStoreName
                                        )
                                    )
                                }
                            }
                        },
                        onNavigateToUpdate = onNavigateToUpdate,
                        onNavigateToMyComments = onNavigateToMyComments,
                        onNavigateToMyReviews = onNavigateToMyReviews,
                        onNavigateToCreateAppRelease = onNavigateToCreateAppRelease
                        // navController 参数已移除
                    )
                }
            }
            2 -> {
                if (lingMarketLoginPrompt) {
                    LingMarketLoginPrompt(onLingMarketLoginClick = onLingMarketLoginClick)
                } else {
                    LingMarketProfileScreen(
                        userInfo = lingMarketUserInfo,
                        modifier = Modifier.fillMaxSize()
                        // navController 参数已移除，该屏幕内部应使用 LocalNavigator
                        // 若需要导航回调，可在此处添加，例如 onNavigateToDetail = { ... }
                    )
                }
            }
        }
    }
}

@Composable
fun LingMarketLoginPrompt(onLingMarketLoginClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "您尚未登录灵应用商店，请登录后使用完整功能",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        TextButton(
            onClick = onLingMarketLoginClick,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text("立即登录", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun XiaoquSpaceHomePage(
    state: HomeState,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit,
    onMessageCenterClick: () -> Unit,
    onBrowseHistoryClick: () -> Unit,
    onMyLikesClick: () -> Unit,
    onFollowersClick: () -> Unit,
    onFansClick: () -> Unit,
    onPostsClick: () -> Unit,
    onMyResourcesClick: () -> Unit,
    onBillingClick: () -> Unit,
    onLoginClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPaymentCenterClick: () -> Unit,
    onSignClick: () -> Unit,
    onRecalculateDays: () -> Unit,
    onAboutClick: () -> Unit,
    onAccountProfileClick: () -> Unit,
    onSineShopLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    snackbarHostState: SnackbarHostState
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (state.showLoginPrompt) {
                LoginPromptSection(
                    onLoginClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PersonalCenterSection(
                    state = state,
                    onAvatarClick = onAvatarClick,
                    onAvatarLongClick = onAvatarLongClick,
                    onFollowersClick = onFollowersClick,
                    onFansClick = onFansClick,
                    onPostsClick = onPostsClick,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                FunctionCardSection(
                    onPaymentCenterClick = onPaymentCenterClick,
                    onMessageCenterClick = onMessageCenterClick,
                    onBrowseHistoryClick = onBrowseHistoryClick,
                    onMyLikesClick = onMyLikesClick,
                    onSettingsClick = onSettingsClick,
                    onMyResourcesClick = onMyResourcesClick,
                    onBillingClick = onBillingClick,
                    onAboutClick = onAboutClick,
                    onAccountProfileClick = onAccountProfileClick,
                    state = state,
                    onSignClick = onSignClick,
                    onRecalculateDays = onRecalculateDays,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun LoginPromptSection(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "您尚未登录，请登录后使用完整功能",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        TextButton(
            onClick = onLoginClick,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text("立即登录", style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun PersonalCenterSection(
    state: HomeState,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit,
    onFollowersClick: () -> Unit,
    onFansClick: () -> Unit,
    onPostsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BBQBackgroundCard(
        shape = AppShapes.medium,
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 背景部分
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                // 渐变叠加层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )
            }

            // 头像
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 10.dp)
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onAvatarClick() },
                            onLongPress = { onAvatarLongClick() }
                        )
                    }
            ) {
                AsyncImage(
                    model = state.avatarUrl,
                    contentDescription = "用户头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_menu_profile)
                )
            }

            // 用户信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 112.dp, end = 16.dp, bottom = 16.dp)
            ) {
                // 昵称和等级
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = state.nickname,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = state.level,
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

                // 硬币和ID信息
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "硬币:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = state.coins,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "用户名:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = state.userId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // 统计信息
                StatsRow(
                    followersCount = state.followersCount,
                    fansCount = state.fansCount,
                    postsCount = state.postsCount,
                    likesCount = state.likesCount,
                    onFollowersClick = onFollowersClick,
                    onFansClick = onFansClick,
                    onPostsClick = onPostsClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StatsRow(
    followersCount: String,
    fansCount: String,
    postsCount: String,
    likesCount: String,
    onFollowersClick: () -> Unit,
    onFansClick: () -> Unit,
    onPostsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(
            count = followersCount,
            label = "关注",
            onClick = onFollowersClick,
            modifier = Modifier.weight(1f)
        )

        StatItem(
            count = fansCount,
            label = "粉丝",
            onClick = onFansClick,
            modifier = Modifier.weight(1f)
        )

        StatItem(
            count = postsCount,
            label = "帖子",
            onClick = onPostsClick,
            modifier = Modifier.weight(1f)
        )

        StatItem(
            count = likesCount,
            label = "获赞",
            modifier = Modifier.weight(1f)
        )
    }
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FunctionCardSection(
    onMessageCenterClick: () -> Unit,
    onBrowseHistoryClick: () -> Unit,
    onMyLikesClick: () -> Unit,
    onMyResourcesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBillingClick: () -> Unit,
    onPaymentCenterClick: () -> Unit,
    onAboutClick: () -> Unit,
    onAccountProfileClick: () -> Unit,
    state: HomeState,
    onSignClick: () -> Unit,
    onRecalculateDays: () -> Unit,
    modifier: Modifier = Modifier
) {
    BBQBackgroundCard(
        shape = AppShapes.medium,
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 签到区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onSignClick() },
                            onLongPress = { onRecalculateDays() }
                        )
                    }
            ) {
                Text(
                    text = state.signStatusMessage ?: "已签到${state.seriesDays}天",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "账号已使用${state.displayDaysDiff}天 经验：${state.exp}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 功能区列表
            FunctionItem(
                icon = R.drawable.ic_menu_message,
                label = "消息",
                onClick = onMessageCenterClick
            )

            Divider()

            FunctionItem(
                icon = R.drawable.ic_visibility,
                label = "看过的",
                onClick = onBrowseHistoryClick
            )

            Divider()

            FunctionItem(
                icon = R.drawable.heart_favorites_open,
                label = "喜欢的",
                onClick = onMyLikesClick
            )

            Divider()

            FunctionItem(
                icon = R.drawable.ic_menu_settings,
                label = "主题",
                onClick = onSettingsClick
            )

            Divider()

            FunctionItem(
                icon = R.drawable.google_cloud_search,
                label = "资源",
                onClick = onMyResourcesClick
            )

            Divider()

            FunctionItem(
                icon = R.drawable.mobills,
                label = "账单",
                onClick = onBillingClick
            )

            Divider()

            FunctionItem(
                icon = R.drawable.banking_4a,
                label = "投币",
                onClick = onPaymentCenterClick
            )

            Divider()
            
            FunctionItem(
                icon = Icons.Filled.Person,
                label = "资料",
                onClick = onAccountProfileClick
            )
            
            Divider()           

            FunctionItem(
                icon = R.drawable.ic_info_outline,
                label = "关于",
                onClick = onAboutClick
            )            
        }
    }
}

@Composable
private fun FunctionItem(
    icon: Any,
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val itemHeight = 48.dp
    val iconSize = 45.dp
    val iconMarginEnd = 16.dp
    val arrowSize = 24.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight)
            .padding(horizontal = 16.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (icon) {
            is Int -> {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .padding(end = iconMarginEnd),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            is ImageVector -> {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .padding(end = iconMarginEnd),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            else -> throw IllegalArgumentException("Unsupported icon type: ${icon::class.java}")
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(arrowSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Divider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    startIndent: Dp = 56.dp
) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .padding(start = startIndent)
            .background(MaterialTheme.colorScheme.outline)
    )
}

data class HomeState(
    val showLoginPrompt: Boolean = true,
    val isLoading: Boolean = false,
    val avatarUrl: String? = null,
    val nickname: String = "BBQ用户",
    val level: String = "LV0",
    val coins: String = "未知",
    val userId: String = "未知",
    val followersCount: String = "0",
    val fansCount: String = "0",
    val postsCount: String = "0",
    val likesCount: String = "0",
    val seriesDays: Int = 0,
    val signToday: Boolean = false,
    val signStatusMessage: String? = null,
    val exp: Int = 0,
    val displayDaysDiff: Int = 0
)

@Composable
fun SineShopLoginPrompt(onSineShopLoginClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "您尚未登录弦应用商店，请登录后使用完整功能",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        TextButton(
            onClick = onSineShopLoginClick,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text("立即登录", style = MaterialTheme.typography.bodyLarge)
        }
    }
}