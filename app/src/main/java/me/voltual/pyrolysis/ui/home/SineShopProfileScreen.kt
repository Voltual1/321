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
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import me.voltual.pyrolysis.SineShopClient
import me.voltual.pyrolysis.core.ui.theme.BBQCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SineShopProfileScreen(
    userInfo: SineShopClient.SineShopUserInfo?,
    modifier: Modifier = Modifier,
    onNavigateToResourcePlaza: (String, String) -> Unit,
    onNavigateToUpdate: () -> Unit = {},
    onNavigateToMyComments: () -> Unit = {},
    onNavigateToMyReviews: () -> Unit = {},
    onNavigateToCreateAppRelease: () -> Unit = {},
    onNavigateToAccountProfile: () -> Unit = {}  // 新增回调，用于编辑账号信息
) {
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
                        .data(userInfo?.userAvatar ?: "https://static.sineshop.xin/images/user_avatar/default_avatar.png")
                        .diskCachePolicy(CachePolicy.DISABLED)
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
                    text = userInfo?.displayName ?: "弦应用商店用户",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = userInfo?.userDescribe ?: "暂无签名",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "⚠️ 本第三方客户端的弦应用商店相关功能仍在完善中，部分功能可能暂时不可用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    lineHeight = 14.sp
                )
            }
        }

        // 功能列表
        FunctionCard(
            icon = Icons.Filled.CloudUpload,
            label = "上传应用",
            onClick = onNavigateToCreateAppRelease
        )
        FunctionCard(
            icon = Icons.AutoMirrored.Filled.List,
            label = "我的上传",
            onClick = { onNavigateToResourcePlaza("my_upload", AppStore.SIENE_SHOP.name) }
        )
        FunctionCard(
            icon = Icons.Filled.Favorite,
            label = "我的收藏",
            onClick = { onNavigateToResourcePlaza("my_favourite", AppStore.SIENE_SHOP.name) }
        )
        FunctionCard(
            icon = Icons.Filled.Star,
            label = "我的评价",
            onClick = onNavigateToMyReviews
        )
        FunctionCard(
            icon = Icons.AutoMirrored.Filled.Comment,
            label = "我的评论",
            onClick = onNavigateToMyComments
        )
        FunctionCard(
            icon = Icons.Filled.History,
            label = "我的历史足迹",
            onClick = { onNavigateToResourcePlaza("my_history", AppStore.SIENE_SHOP.name) }
        )
        /*        FunctionCard(
                    icon = Icons.Filled.Report,
                    label = "我的举报",
                    onClick = {  TODO: Implement my reports  }
                )*/
        FunctionCard(
            icon = Icons.Filled.Update,
            label = "应用更新",
            onClick = onNavigateToUpdate
        )
        FunctionCard(
            icon = Icons.Filled.Edit,
            label = "编辑账号信息",
            onClick = onNavigateToAccountProfile  // 使用新增回调
        )
        /*        FunctionCard(
                    icon = Icons.Filled.Security,
                    label = "账号安全",
                    onClick = {  TODO: Implement account security  }
                )
                FunctionCard(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    label = "退出登录",
                    onClick = {  TODO: Implement logout  }
                )
                FunctionCard(
                    icon = Icons.Filled.Download,
                    label = "本地下载",
                    onClick = {  TODO: Implement local downloads  }
                )
              FunctionCard(
                    icon = Icons.Filled.Devices,
                    label = "我的设备",
                    onClick = {  TODO: Implement my devices  }
                )
                FunctionCard(
                    icon = Icons.Filled.Info,
                    label = "关于弦",
                    onClick = {  TODO: Implement about SineShop  }
                )*/
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FunctionCard(
    icon: ImageVector,
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