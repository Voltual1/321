package me.voltual.a321.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anggrayudi.storage.file.DocumentFileCompat
import me.voltual.a321.core.ui.components.BaseListScreen
import me.voltual.a321.core.ui.theme.BBQIconButton
import me.voltual.a321.data.unified.PanFile
import me.voltual.a321.data.unified.PanPath
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // 处理物理返回键
    BackHandler(enabled = viewModel.pathStack.size > 1) {
        viewModel.navigateBack()
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val file = DocumentFileCompat.fromUri(context, it)
            if (file != null) {
                viewModel.uploadFile(it, file.name ?: "unknown", file.length())
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("123pan") },
                    navigationIcon = {
                        if (viewModel.pathStack.size > 1) {
                            BBQIconButton(
                                onClick = { viewModel.navigateBack() },
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        BBQIconButton(
                            onClick = { viewModel.loadFiles() },
                            icon = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                )
                // 面包屑导航
                BreadcrumbsBar(
                    pathStack = viewModel.pathStack,
                    onPathClick = { path ->
                        // 实现点击面包屑跳转逻辑（假设 ViewModel 有此方法）
                        // viewModel.navigateToPath(path) 
                    }
                )
                // 上传进度条
                UploadProgressBanner(
                    isUploading = viewModel.isUploading,
                    progress = viewModel.uploadProgress,
                    message = viewModel.uploadMessage
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { filePickerLauncher.launch("*/*") }) {
                Icon(Icons.Default.Add, contentDescription = "上传文件")
            }
        }
    ) { paddingValues ->
        BaseListScreen(
            items = viewModel.fileList,
            isLoading = viewModel.isLoading,
            error = viewModel.error,
            currentPage = 1,
            totalPages = 1,
            onRetry = { viewModel.loadFiles() },
            onLoadMore = { },
            emptyMessage = "这里空空如也",
            modifier = Modifier.padding(paddingValues),
            itemContent = { file ->
                FileListItem(
                    file = file,
                    onClick = {
                        if (file.isDirectory) {
                            viewModel.enterFolder(file)
                        } else {
                            // 触发下载
                            activity?.let {
                                viewModel.downloadFile(it, file)
                            }
                        }
                    }
                )
            }
        )
    }
} // 闭合 ExplorerScreen

// --- 下方组件保持不变 ---

@Composable
fun BreadcrumbsBar(
    pathStack: List<PanPath>,
    onPathClick: (PanPath) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(pathStack) { path ->
            Text(
                text = path.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onPathClick(path) }
            )
            if (path != pathStack.last()) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun FileListItem(
    file: PanFile,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val sizeInfo = if (file.isDirectory) "" else " · ${formatSize(file.size)}"
            Text("${file.updateTime}$sizeInfo")
        },
        leadingContent = {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        },
        trailingContent = {
            if (file.isAbnormal) {
                Icon(Icons.Default.Warning, contentDescription = "违规", tint = MaterialTheme.colorScheme.error)
            } else {
                IconButton(onClick = { /* TODO: 更多操作菜单 */ }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
            }
        }
    )
}

@Composable
fun UploadProgressBanner(
    isUploading: Boolean,
    progress: Float,
    message: String
) {
    AnimatedVisibility(visible = isUploading) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}