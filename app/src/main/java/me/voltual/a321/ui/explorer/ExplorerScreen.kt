package me.voltual.a321.ui.explorer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import me.voltual.a321.core.utils.extension.text.formatSize
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.anggrayudi.storage.file.DocumentFileCompat
import me.voltual.a321.core.ui.components.BaseListScreen
import me.voltual.a321.core.ui.theme.BBQIconButton
import me.voltual.a321.data.unified.PanFile
import me.voltual.a321.data.unified.PanPath
import me.voltual.a321.ui.dialog.ActionsDialogUI
import me.voltual.a321.ui.dialog.StringInputPrefDialogUI
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel = koinViewModel(),
    snackbarHostState: SnackbarHostState
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

    // 监听 ViewModel 事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExplorerEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        duration = SnackbarDuration.Short
                    )
                    // 如果用户点击了“复制”按钮
                    if (result == SnackbarResult.ActionPerformed && event.actionLabel == "复制") {
                        val url = event.message.substringAfter("：")
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("123Pan Share", url)
                        clipboard.setPrimaryClip(clip)
                    }
                }
            }
        }
    }

    BackHandler(enabled = viewModel.leftPane.pathStack.size > 1 || viewModel.rightPane.pathStack.size > 1) {
        if (!viewModel.navigateBack(viewModel.activePane)) {
            val otherPane =
                if (viewModel.activePane == PaneIndex.LEFT) PaneIndex.RIGHT else PaneIndex.LEFT
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                    PaneContainer(
                        modifier = Modifier.weight(1f),
                        elevation = leftElevation,
                        isActive = viewModel.activePane == PaneIndex.LEFT,
                        onPointerPress = { viewModel.setActive(PaneIndex.LEFT) }
                    ) {
                        FilePane(
                            state = viewModel.leftPane,
                            viewModel = viewModel,
                            isActive = viewModel.activePane == PaneIndex.LEFT,
                            onFileClick = { file ->
                                viewModel.setActive(PaneIndex.LEFT)
                                if (file.isDirectory) viewModel.enterFolder(PaneIndex.LEFT, file)
                                else activity?.let { viewModel.downloadFile(it, file) }
                            },
                            onFileLongClick = { file ->
                                viewModel.setActive(PaneIndex.LEFT)
                                viewModel.showActionMenu(file)
                            },
                            onBreadcrumbClick = {
                                viewModel.setActive(PaneIndex.LEFT)
                                viewModel.navigateToPath(PaneIndex.LEFT, it)
                            },
                            onRetry = {
                                viewModel.setActive(PaneIndex.LEFT)
                                viewModel.loadFiles(PaneIndex.LEFT)
                            }
                        )
                    }

                    // 右侧窗口
                    PaneContainer(
                        modifier = Modifier.weight(1f),
                        elevation = rightElevation,
                        isActive = viewModel.activePane == PaneIndex.RIGHT,
                        onPointerPress = { viewModel.setActive(PaneIndex.RIGHT) }
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
                            onFileLongClick = { file ->
                                viewModel.setActive(PaneIndex.RIGHT)
                                viewModel.showActionMenu(file)
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

        // 弹窗管理部分
        ExplorerDialogs(viewModel)
    }
}

@Composable
private fun PaneContainer(
    modifier: Modifier,
    elevation: androidx.compose.ui.unit.Dp,
    isActive: Boolean,
    onPointerPress: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .zIndex(if (isActive) 1f else 0f)
            .shadow(elevation = elevation)
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            onPointerPress()
                        }
                    }
                }
            }
    ) {
        content()
    }
}

@Composable
fun ExplorerDialogs(viewModel: ExplorerViewModel) {
    val selectedFile = viewModel.selectedFileForAction

    FileActionMenu(
        isVisible = viewModel.isActionMenuVisible,
        file = selectedFile,
        onDismiss = { viewModel.hideActionMenu() },
        onAction = { action ->
            viewModel.performAction(action, viewModel.activePane)
        }
    )

    if (viewModel.isShareSheetVisible && selectedFile != null) {
        ShareFileSheet(
            fileName = selectedFile.name,
            onDismiss = { viewModel.hideShareSheet() },
            onConfirm = { password, expiration ->
                viewModel.confirmShare(password, expiration)
            }
        )
    }

    if (viewModel.isRenameDialogVisible && selectedFile != null) {
        Dialog(onDismissRequest = { viewModel.hideRenameDialog() }) {
            StringInputPrefDialogUI(
                title = "重命名",
                initialValue = selectedFile.name,
                onDismiss = { viewModel.hideRenameDialog() },
                onConfirm = { newName ->
                    if (newName.isNotBlank() && newName != selectedFile.name) {
                        viewModel.confirmRename(newName, viewModel.activePane)
                    } else {
                        viewModel.hideRenameDialog()
                    }
                }
            )
        }
    }

    if (viewModel.isPropertyDialogVisible && selectedFile != null) {
        FilePropertyDialog(
            file = selectedFile,
            onDismiss = { viewModel.hidePropertyDialog() }
        )
    }

    if (viewModel.isDeleteDialogVisible && selectedFile != null) {
        Dialog(onDismissRequest = { viewModel.hideDeleteDialog() }) {
            ActionsDialogUI(
                titleText = "删除",
                messageText = "是否删除 ${selectedFile.name}？",
                primaryText = "删除",
                primaryIcon = Icons.Default.Delete,
                primaryAction = {
                    viewModel.confirmDelete(viewModel.activePane)
                },
                onDismiss = { viewModel.hideDeleteDialog() }
            )
        }
    }
}

@Composable
fun FilePane(
    state: ExplorerViewModel.PaneState,
    isActive: Boolean,
    viewModel: ExplorerViewModel,
    onFileClick: (PanFile) -> Unit,
    onFileLongClick: (PanFile) -> Unit,
    onBreadcrumbClick: (PanPath) -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
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
                val targetPane = if (state === viewModel.leftPane) PaneIndex.LEFT else PaneIndex.RIGHT
                viewModel.loadFiles(pane = targetPane, isNextPage = true)
            },
            emptyMessage = "无文件",
            itemContent = { file ->
                FileListItem(
                    file = file,
                    onClick = { onFileClick(file) },
                    onLongClick = { onFileLongClick(file) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: PanFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isUpFolder = file.name == ".." && file.id == -1L

    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = if (!isUpFolder) onLongClick else null
        ),
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
            if (!isUpFolder) {
                val sizeInfo = if (file.isDirectory) "" else " · ${file.size.formatSize()}"
                Text(
                    text = "${file.updateTime}$sizeInfo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder 
                              else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.outline
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
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
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