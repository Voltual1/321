//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>。

package me.voltual.pyrolysis.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error 
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color // 导入 Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.voltual.pyrolysis.KtorClient
import me.voltual.pyrolysis.core.ui.theme.ThemeManager
import me.voltual.pyrolysis.core.ui.theme.billing_expense
import me.voltual.pyrolysis.core.ui.theme.billing_expense_dark
import me.voltual.pyrolysis.core.ui.theme.billing_income
import me.voltual.pyrolysis.core.ui.theme.billing_income_dark
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.ui.graphics.vector.ImageVector
import me.voltual.pyrolysis.core.utils.cleanUrl

@Composable
fun SharedPostItem(
    post: KtorClient.Post, 
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = rememberAsyncImagePainter(model = post.usertx),
                    contentDescription = "用户头像",
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.nickname, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(post.create_time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(post.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            Text(post.content, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            
            
            post.img_url?.takeIf { it.isNotEmpty() }?.firstOrNull()?.let { imageUrl ->
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    painter = rememberAsyncImagePainter(model = imageUrl.cleanUrl()),
                    contentDescription = "帖子图片",
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row {
                    Text("浏览: ${post.view}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                    Text("点赞: ${post.thumbs}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                    Text("评论: ${post.comment}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(post.sub_section_name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
fun SharedLogListItem(log: me.voltual.pyrolysis.core.database.LogEntry, modifier: Modifier = Modifier) {
    val isDarkTheme = ThemeManager.isAppDarkTheme
    val statusColor = if (log.status == "SUCCESS") {
        if (isDarkTheme) billing_income_dark else billing_income
    } else {
        if (isDarkTheme) billing_expense_dark else billing_expense
    }
   
    val statusIcon: ImageVector = if (log.status == "SUCCESS") Icons.Default.CheckCircle else Icons.Default.Error

    ListItem(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        headlineContent = { Text("[${log.type}] - ${log.status}") },
        supportingContent = { Text(log.responseBody, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(statusIcon, null, tint = statusColor) },
        trailingContent = { Text(log.formattedTime()) }
    )
}