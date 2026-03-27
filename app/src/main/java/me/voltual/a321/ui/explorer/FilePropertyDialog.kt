package me.voltual.a321.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.voltual.a321.data.unified.PanFile

@Composable
fun FilePropertyDialog(
    file: PanFile,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(300.dp) // 略宽于菜单，给长文本留空间
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp), // 保持一致的圆角
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 标题栏
                Text(
                    text = "文件属性",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // 属性列表
                PropertyItem("文件名", file.name)
                PropertyItem("大小", formatFileSize(file.size))
                PropertyItem("类型", if (file.isDirectory) "文件夹" else "${file.extension.uppercase()} 文件")
                PropertyItem("修改时间", file.updateTime)
                
                // 针对 MT 风格的高级属性：使用 SelectionContainer 允许用户复制 ID 或路径
                PropertyItem("文件 ID", file.id.toString(), isMonospace = true)
                
                if (file.etag != null) {
                    PropertyItem("Etag (Hash)", file.etag, isMonospace = true)
                }

                if (file.rawDownloadUrl != null) {
                    PropertyItem("原始路径", file.rawDownloadUrl, isMonospace = true, canWrap = true)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部按钮
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 16.dp)
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
private fun PropertyItem(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    canWrap: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        
        // 允许长按选择/复制
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = if (isMonospace) FontWeight.Normal else FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (canWrap) 5 else 1,
                overflow = if (canWrap) TextOverflow.Clip else TextOverflow.Ellipsis
            )
        }
    }
}

// 简单的文件大小格式化辅助函数
private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}