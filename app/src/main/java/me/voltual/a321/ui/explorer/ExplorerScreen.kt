package me.voltual.a321.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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

    BackHandler(enabled = viewModel.leftPane.pathStack.size > 1 || viewModel.rightPane.pathStack.size > 1) {
        if (!viewModel.navigateBack(viewModel.activePane)) {
            val otherPane = if (viewModel.activePane == PaneIndex.LEFT) PaneIndex.RIGHT else PaneIndex.LEFT
            viewModel.navigateBack(otherPane)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val file = DocumentFileCompat.fromUri(context, it)
            if (file != null) {
                viewModel.uploadFile(it, file.name ?: "unknown", file.length(), viewModel.activePane)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("123pan Geek") },
                actions = {
                    BBQIconButton(
                        onClick = { 
                            viewModel.loadFiles(PaneIndex.LEFT)
                            viewModel.loadFiles(PaneIndex.RIGHT)
                        },
                        icon = Icons.Default.Refresh,
                        contentDescription = "全部刷新"
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { filePickerLauncher.launch("*/*") }) {
                Icon(Icons.Default.Add, contentDescription = "上传")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // 全局进度条放在 TopBar 下方
            UploadProgressBanner(
                isUploading = viewModel.isUploading,
                progress = viewModel.uploadProgress,
                message = viewModel.uploadMessage
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // 左侧窗口
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(interactionSource = null, indication = null) { 
                        viewModel.activePane = PaneIndex.LEFT 
                    }
                ) {
                    FilePane(
                        state = viewModel.leftPane,
                        isActive = viewModel.activePane == PaneIndex.LEFT,
                        onFileClick = { file ->
                            if (file.isDirectory) viewModel.enterFolder(PaneIndex.LEFT, file)
                            else activity?.let { viewModel.downloadFile(it, file) }
                        },
                        onBreadcrumbClick = { viewModel.navigateToPath(PaneIndex.LEFT, it) },
                        onRetry = { viewModel.loadFiles(PaneIndex.LEFT) }
                    )
                }

                // 中间分割线：模仿 MT 管理器的纵深阴影感
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .shadow(elevation = 2.dp) // 增加微弱阴影
                )

                // 右侧窗口
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(interactionSource = null, indication = null) { 
                        viewModel.activePane = PaneIndex.RIGHT 
                    }
                ) {
                    FilePane(
                        state = viewModel.rightPane,
                        isActive = viewModel.activePane == PaneIndex.RIGHT,
                        onFileClick = { file ->
                            if (file.isDirectory) viewModel.enterFolder(PaneIndex.RIGHT, file)
                            else activity?.let { viewModel.downloadFile(it, file) }
                        },
                        onBreadcrumbClick = { viewModel.navigateToPath(PaneIndex.RIGHT, it) },
                        onRetry = { viewModel.loadFiles(PaneIndex.RIGHT) }
                    )
                }
            }
        }
    }
}

@Composable
fun FilePane(
    state: ExplorerViewModel.PaneState,
    isActive: Boolean,
    onFileClick: (PanFile) -> Unit,
    onBreadcrumbClick: (PanPath) -> Unit,
    onRetry: () -> Unit
) {
    // 这里的 isActive 可以用来微调背景色，或者面包屑文字颜色
    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) // 非激活状态稍暗
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        BreadcrumbsBar(
            pathStack = state.pathStack,
            onPathClick = onBreadcrumbClick
        )
        
        BaseListScreen(
            items = state.fileList,
            isLoading = state.isLoading,
            error = state.error,
            currentPage = 1,
            totalPages = 1,
            onRetry = onRetry,
            onLoadMore = { },
            emptyMessage = "无文件",
            itemContent = { file ->
                FileListItem(
                    file = file,
                    onClick = { onFileClick(file) }
                )
            }
        )
    }
}

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