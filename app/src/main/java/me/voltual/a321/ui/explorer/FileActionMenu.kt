package me.voltual.a321.ui.explorer

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.voltual.a321.data.unified.PanFile

@Composable
fun FileActionMenu(
    isVisible: Boolean,
    file: PanFile?,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
    // 使用 AnimatedVisibility 实现类似弹出效果
    AnimatedVisibility(
        visible = isVisible && file != null,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(initialScale = 0.9f),
        modifier = Modifier.fillMaxSize()
    ) {
        // 这是一个透明的 Box，覆盖全屏，点击非菜单区域则关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // 真正的菜单体
            Surface(
                modifier = Modifier
                    .width(280.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp, // 这里的 elevation 提供纵深感
                shadowElevation = 8.dp  // 核心：MT 管理器那种阴影
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    // 头部：显示当前操作的文件名
                    Text(
                        text = file?.name ?: "",
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    
                    Divider(modifier = Modifier.padding(horizontal = 8.dp))

                    // 菜单项
                    ActionMenuItem(Icons.Default.Share, "分享") { onAction("share") }
                    ActionMenuItem(Icons.Default.DriveFileMove, "移动") { onAction("move") }
                    ActionMenuItem(Icons.Default.Edit, "重命名") { onAction("rename") }
                    ActionMenuItem(Icons.Default.Delete, "删除", color = MaterialTheme.colorScheme.error) { onAction("delete") }
                    ActionMenuItem(Icons.Default.Info, "属性") { onAction("info") }
                }
            }
        }
    }
}

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
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
                tint = color.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = color
            )
        }
    }
}