package me.voltual.a321.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.anggrayudi.storage.file.DocumentFileCompat
import me.voltual.a321.core.ui.components.BaseListScreen
import me.voltual.a321.core.ui.theme.BBQIconButton
import me.voltual.a321.data.unified.PanFile
import me.voltual.a321.data.unified.PanPath
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // 阴影动画：激活时 6dp 产生明显的投影，未激活时 0dp
    val leftElevation by animateDpAsState(
        targetValue = if (viewModel.activePane == PaneIndex.LEFT) 6.dp else 0.dp,
        label = "LeftPaneElevation"
    )
    val rightElevation by animateDpAsState(
        targetValue = if (viewModel.activePane == PaneIndex.RIGHT) 6.dp else 0.dp,
        label = "RightPaneElevation"
    )

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
                title = { Text("A321") },
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
                    .zIndex(if (viewModel.activePane == PaneIndex.LEFT) 1f else 0f)
                    .shadow(elevation = leftElevation)
                    .background(MaterialTheme.colorScheme.surface)
                    // 使用 pointerInput 或者在点击事件中明确调用 setActive
// 关键改进：监听按下事件，无论随后是滑动还是点击
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) {
                                    viewModel.setActive(PaneIndex.LEFT)
                                }
                            }
                        }
                    }                ) {
                    FilePane(
                        state = viewModel.leftPane,
                        viewModel = viewModel,
                        isActive = viewModel.activePane == PaneIndex.LEFT,
                        onFileClick = { file ->
                            viewModel.setActive(PaneIndex.LEFT) // 点击文件时也激活该侧
                            if (file.isDirectory) viewModel.enterFolder(PaneIndex.LEFT, file)
                            else activity?.let { viewModel.downloadFile(it, file) }
                        },
                        onBreadcrumbClick = { 
                            viewModel.setActive(PaneIndex.LEFT) // 点击路径激活
                            viewModel.navigateToPath(PaneIndex.LEFT, it) 
                        },
                        onRetry = { 
                            viewModel.setActive(PaneIndex.LEFT)
                            viewModel.loadFiles(PaneIndex.LEFT) 
                        }
                    )
                }

                // 右侧窗口
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .zIndex(if (viewModel.activePane == PaneIndex.RIGHT) 1f else 0f)
                    .shadow(elevation = rightElevation)
                    .background(MaterialTheme.colorScheme.surface)
                    // 关键改进：监听按下事件，无论随后是滑动还是点击
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) {
                                    viewModel.setActive(PaneIndex.RIGHT)
                                }
                            }
                        }
                    }
                ) {
                    FilePane(
                        state = viewModel.rightPane,
                        isActive = viewModel.activePane == PaneIndex.RIGHT,
                        viewModel = viewModel,
                        onFileClick = { file ->
                            viewModel.setActive(PaneIndex.RIGHT)
                            if (file.isDirectory) viewModel.enterFolder(PaneIndex.RIGHT, file)
                            else activity?.let { viewModel.downloadFile(it, file) }
                        },
                        onBreadcrumbClick = { 
                            viewModel.setActive(PaneIndex.RIGHT)
                            viewModel.navigateToPath(PaneIndex.RIGHT, it) 
                        },
                        onRetry = { 
                            viewModel.setActive(PaneIndex.RIGHT)
                            viewModel.loadFiles(PaneIndex.RIGHT) 
                        }
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
    viewModel: ExplorerViewModel,
    onFileClick: (PanFile) -> Unit,
    onBreadcrumbClick: (PanPath) -> Unit,
    onRetry: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        BreadcrumbsBar(
            pathStack = state.pathStack,
            onPathClick = onBreadcrumbClick
        )
        
BaseListScreen(
    items = state.fileList,
    isLoading = state.isLoading,
    error = state.error,
    currentPage = state.currentPage,
    autoLoadMode = true,
    totalPages = state.totalPages,
    onRetry = onRetry,
    onLoadMore = {
        // 要加载哪一侧（可以通过判断 state 是不是 viewModel.leftPane）
        val targetPane = if (state === viewModel.leftPane) PaneIndex.LEFT else PaneIndex.RIGHT
        viewModel.loadFiles(pane = targetPane, isNextPage = true)
    },
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
            if (path != pathStack.last() && path.name != "/") {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 2.dp)
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
    val isUpFolder = file.name == ".." && file.id == -1L

    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = {
            Text(
                text = file.name,
                maxLines = 1,
                fontWeight = if (isUpFolder) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    repeatDelayMillis = 2000
                )
            )
        },
        supportingContent = {
            // 如果是 ".."，不显示更新时间和文件大小
            if (!isUpFolder) {
                val sizeInfo = if (file.isDirectory) "" else " · ${formatSize(file.size)}"
                Text(
                    text = "${file.updateTime}$sizeInfo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            velocity = 30.dp,
                            repeatDelayMillis = 3000
                        )
                )
            }
        },
        leadingContent = {
    Icon(
        imageVector = if (file.isDirectory) {
            Icons.Default.Folder 
        } else {
            Icons.AutoMirrored.Filled.InsertDriveFile
        },
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = if (file.isDirectory) {
            if (isUpFolder) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            // ".." 文件夹可以使用稍微淡一点的颜色以示区分
            else MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        }
    )
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