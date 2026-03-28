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
import me.voltual.a321.core.utils.extension.text.formatSize
import me.voltual.a321.data.unified.PanFile

@Composable
fun FilePropertyDialog(
    file: PanFile,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (file.category == 10) "分享详情" else "文件属性",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                PropertyItem("名称", file.name)
                
                if (file.category == 10) {
                    // 分享专属信息
                    if (file.rawDownloadUrl != "") {
                        PropertyItem("分享链接", file.rawDownloadUrl, isMonospace = true, canWrap = true)
                    }
                    PropertyItem("提取码", if (file.sharePwd.isNullOrBlank()) "无" else file.sharePwd, isMonospace = true)
                    PropertyItem("过期时间", file.expiration ?: "永久有效")
                    PropertyItem("分享 ID", file.id.toString(), isMonospace = true)
                } else {
                    // 普通文件信息
                    PropertyItem("大小", file.size.formatSize())
                    PropertyItem("类型", if (file.isDirectory) "文件夹" else "${file.extension.uppercase()} 文件")
                    PropertyItem("修改时间", file.updateTime)
                    PropertyItem("文件 ID", file.id.toString(), isMonospace = true)
                    if (file.etag != null) {
                        PropertyItem("Etag (Hash)", file.etag, isMonospace = true)
                    }
                    if (file.rawDownloadUrl != "") {
                        PropertyItem("DownloadUrl", file.rawDownloadUrl, isMonospace = true, canWrap = true)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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