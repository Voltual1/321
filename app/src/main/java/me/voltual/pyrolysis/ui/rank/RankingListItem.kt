//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。
package me.voltual.pyrolysis.ui.rank

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.voltual.pyrolysis.KtorClient

@Composable
fun RankingListItem(
    ranking: Int,
    user: KtorClient.RankingUser,
    sortType: SortType,
    onClick: () -> Unit
) {
    // 使用 MaterialTheme 颜色
    val rankColor = when (ranking) {
        1 -> MaterialTheme.colorScheme.primary // 冠军色
        2 -> MaterialTheme.colorScheme.secondary // 亚军色
        3 -> MaterialTheme.colorScheme.tertiary // 季军色
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large // 使用大圆角，更柔和
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 排名数字
            Text(
                text = "#$ranking",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = rankColor,
                modifier = Modifier.width(55.dp) // 调整宽度以适应更大的字体
            )

            // 用户头像 - 已修复加载逻辑
            AsyncImage(
                model = user.usertx,
                contentDescription = "${user.nickname} 的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp) // 稍大的头像
                    .clip(CircleShape)
                    .border(BorderStroke(2.dp, rankColor), CircleShape) // 添加与排名颜色匹配的边框
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 用户信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (sortType == SortType.MONEY) Icons.Filled.MonetizationOn else Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = if (sortType == SortType.MONEY) "硬币" else "经验",
                        tint = MaterialTheme.colorScheme.secondary, // 使用主题中的强调色
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (sortType) {
                            SortType.MONEY -> (user.money ?: 0).toString()
                            SortType.EXP -> (user.exp ?: 0).toString()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}