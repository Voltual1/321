// Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
// 本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.voltual.pyrolysis.AppStore
import me.voltual.pyrolysis.LingMarketClient
import me.voltual.pyrolysis.ui.*
import me.voltual.pyrolysis.core.ui.theme.BBQCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LingMarketProfileScreen(
    userInfo: LingMarketClient.LingMarketUser?,
    modifier: Modifier = Modifier
) {
    // Navigation 3 导航器
    val navigator = LocalNavigator.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 头像和用户信息
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            // 头像
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(72.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(userInfo?.avatarUrl ?: "https://static.sineshop.xin/images/user_avatar/default_avatar.png")
                        .crossfade(true)
                        .build(),
                    contentDescription = "用户头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 用户信息
            Column {
                Text(
                    text = userInfo?.nickname ?: "灵应用商店用户",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "@${userInfo?.username ?: "unknown"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "身份: ${userInfo?.role ?: "用户"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "⚠️ 本第三方客户端的灵应用商店相关功能仍在完善中",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    lineHeight = 14.sp
                )
            }
        }

        // 用户详情卡片
        BBQCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "账户详情",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                InfoRow(label = "用户ID", value = userInfo?.id ?: "未知")
                InfoRow(label = "用户名", value = userInfo?.username ?: "未知")
                InfoRow(label = "昵称", value = userInfo?.nickname ?: "未知")
                InfoRow(label = "邮箱", value = userInfo?.email ?: "未设置")
                InfoRow(label = "注册时间", value = userInfo?.createdAt?.substringBefore("T") ?: "未知")
                InfoRow(label = "账户状态", value = userInfo?.status ?: "未知")
            }
        }

        // 功能列表
        FunctionCard(
            icon = Icons.Filled.Edit,
            label = "编辑个人资料",
            onClick = {
                // 类型安全导航：传递 AccountProfile 路由对象
                navigator.navigate(AccountProfile(AppStore.LING_MARKET))
            }
        )

        // 我的收藏
        FunctionCard(
            icon = Icons.Filled.Favorite,
            label = "我的收藏",
            onClick = {
                // 类型安全导航：传递 ResourcePlaza 路由对象
                navigator.navigate(
                    ResourcePlaza(
                        isMyResource = true,
                        userId = -1, // 灵应用商店看的是 Authorization 头
                        mode = "my_favourite",
                        storeName = AppStore.LING_MARKET.name
                    )
                )
            }
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FunctionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    BBQCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            headlineContent = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}