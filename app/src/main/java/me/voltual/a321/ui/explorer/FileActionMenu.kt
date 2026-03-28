package me.voltual.a321.ui.explorer

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.voltual.a321.data.unified.PanFile

@Composable
fun FileActionMenu(
    isVisible: Boolean,
    file: PanFile?,
    selectedCount: Int, 
    isRecycleBin: Boolean,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
    val isBatch = selectedCount > 1

    AnimatedVisibility(
        visible = isVisible && (file != null || isBatch),
        enter = fadeIn(animationSpec = tween(200)) + scaleIn(transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center),
        exit = fadeOut(animationSpec = tween(150)) + scaleOut(transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(280.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    // 标题栏：如果是批量则显示数量，否则显示文件名
                    Text(
                        text = if (isBatch) "已选择 $selectedCount 项" else (file?.name ?: ""),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    if (isRecycleBin) {
    // 回收站批量/单项操作
    ActionMenuItem(Icons.Default.Restore, if (isBatch) "批量恢复" else "恢复并回到原处") { onAction("restore") }
    if (!isBatch) {
        ActionMenuItem(Icons.Default.DriveFileMove, "移动并恢复") { onAction("move") }
        ActionMenuItem(Icons.Default.Edit, "重命名并恢复") { onAction("rename") }
    }
    
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp))
    
    // 回收站中统一使用 delete 操作，但内部逻辑会判断并彻底删除
    ActionMenuItem(
        icon = Icons.Default.DeleteForever, 
        label = if (isBatch) "批量彻底删除" else "彻底删除", 
        textColor = MaterialTheme.colorScheme.error
    ) { onAction("delete") }
} else {
    // 常规批量/单项操作
    if (!isBatch) {
        ActionMenuItem(Icons.Default.Share, "分享") { onAction("share") }
    } else {
        // TODO: 批量分享逻辑
        ActionMenuItem(Icons.Default.Share, "批量分享 (未实现)") { /* TODO */ }
    }
    
    ActionMenuItem(Icons.Default.DriveFileMove, if (isBatch) "批量移动" else "移动") { onAction("move") }
    
    if (!isBatch) {
        ActionMenuItem(Icons.Default.Edit, "重命名") { onAction("rename") }
    }

    ActionMenuItem(
        icon = Icons.Default.Delete, 
        label = if (isBatch) "批量删除" else "删除", 
        textColor = MaterialTheme.colorScheme.error
    ) { onAction("delete") }
}

                    if (!isBatch) {
                        ActionMenuItem(Icons.Default.Info, "属性") { onAction("info") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    label: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = if (textColor == MaterialTheme.colorScheme.error) textColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
        }
    }
}