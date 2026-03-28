package me.voltual.a321.ui.explorer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import me.voltual.a321.core.utils.extension.text.formatSize
import me.voltual.a321.core.ui.components.BaseListScreen
import me.voltual.a321.core.ui.theme.BBQIconButton
import me.voltual.a321.data.unified.PanFile
import me.voltual.a321.data.unified.PanPath
import me.voltual.a321.ui.dialog.ActionsDialogUI
import me.voltual.a321.ui.dialog.StringInputPrefDialogUI
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel = koinViewModel(),
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    val leftElevation by animateDpAsState(
        targetValue = if (viewModel.activePane == PaneIndex.LEFT) 6.dp else 0.dp,
        label = "LeftPaneElevation"
    )
    val rightElevation by animateDpAsState(
        targetValue = if (viewModel.activePane == PaneIndex.RIGHT) 6.dp else 0.dp,
        label = "RightPaneElevation"
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExplorerEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed && event.actionLabel == "复制") {
                        val url = event.message.substringAfter("：")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("123Pan Share", url)
                        clipboard.setPrimaryClip(clip)
                    }
                }
            }
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
        val activeState = if (viewModel.activePane == PaneIndex.LEFT) viewModel.leftPane else viewModel.rightPane
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("A321") },
                    actions = {
                        BBQIconButton(
                            onClick = { viewModel.toggleRecycleBin(viewModel.activePane) },
                            icon = if (activeState.isRecycleBin) Icons.Default.CloudQueue else Icons.Default.DeleteSweep,
                            contentDescription = "回收站",
                            tint = if (activeState.isRecycleBin) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
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
            },
            bottomBar = {
    BottomAppBar(
        actions = {
            BBQIconButton(
                onClick = { viewModel.showCreateFolderDialog() },
                icon = Icons.Default.CreateNewFolder,
                contentDescription = "新建文件夹"
            )
            // 预留：后退、前进、新建、同步、跳转等按钮
        }
    )
}
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                UploadProgressBanner(
                    isUploading = viewModel.isUploading,
                    progress = viewModel.uploadProgress,
                    message = viewModel.uploadMessage
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    PaneContainer(
                        modifier = Modifier.weight(1f),
                        elevation = leftElevation,
                        isActive = viewModel.activePane == PaneIndex.LEFT,
                        onPointerPress = { viewModel.setActive(PaneIndex.LEFT) }
                    ) {
                        FilePane(
                            state = viewModel.leftPane,
                            viewModel = viewModel,
                            paneIndex = PaneIndex.LEFT,
                            isActive = viewModel.activePane == PaneIndex.LEFT,
                            onFileClick = { file ->
                                viewModel.setActive(PaneIndex.LEFT)
                                if (file.isDirectory) viewModel.enterFolder(PaneIndex.LEFT, file)
                                else activity?.let { viewModel.downloadFile(it, file) }
                            },
                            onFileLongClick = { file ->
                                viewModel.setActive(PaneIndex.LEFT)
                                viewModel.showActionMenu(file, PaneIndex.LEFT)
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

                    PaneContainer(
                        modifier = Modifier.weight(1f),
                        elevation = rightElevation,
                        isActive = viewModel.activePane == PaneIndex.RIGHT,
                        onPointerPress = { viewModel.setActive(PaneIndex.RIGHT) }
                    ) {
                        FilePane(
                            state = viewModel.rightPane,
                            viewModel = viewModel,
                            paneIndex = PaneIndex.RIGHT,
                            isActive = viewModel.activePane == PaneIndex.RIGHT,
                            onFileClick = { file ->
                                viewModel.setActive(PaneIndex.RIGHT)
                                if (file.isDirectory) viewModel.enterFolder(PaneIndex.RIGHT, file)
                                else activity?.let { viewModel.downloadFile(it, file) }
                            },
                            onFileLongClick = { file ->
                                viewModel.setActive(PaneIndex.RIGHT)
                                viewModel.showActionMenu(file, PaneIndex.RIGHT)
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

        ExplorerDialogs(viewModel = viewModel, activePaneState = activeState)
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
fun ExplorerDialogs(
    viewModel: ExplorerViewModel,
    activePaneState: ExplorerViewModel.PaneState
) {
    val selectedFile = viewModel.selectedFileForAction
    val activePaneIndex = viewModel.activePane
    val selectedCount = if (viewModel.isBatchActionMenu) activePaneState.selectedIds.size else 1

    FileActionMenu(
        isVisible = viewModel.isActionMenuVisible,
        file = selectedFile,
        selectedCount = selectedCount,
        isRecycleBin = activePaneState.isRecycleBin,
        onDismiss = { viewModel.hideActionMenu() },
        onAction = { action -> viewModel.performAction(action, activePaneIndex) }
    )

    if (viewModel.isShareSheetVisible && selectedFile != null) {
        ShareFileSheet(
            fileName = selectedFile.name,
            onDismiss = { viewModel.hideShareSheet() },
            onConfirm = { password, expiration -> viewModel.confirmShare(password, expiration) }
        )
    }
    
    // --- 新建文件夹对话框 ---
    if (viewModel.isCreateFolderDialogVisible) {
        Dialog(onDismissRequest = { viewModel.hideCreateFolderDialog() }) {
            StringInputPrefDialogUI(
                title = "新建文件夹",
                initialValue = "", // 初始名称为空
                onDismiss = { viewModel.hideCreateFolderDialog() },
                onConfirm = { folderName ->
                    if (folderName.isNotBlank()) {
                        viewModel.confirmCreateFolder(folderName, activePaneIndex)
                    } else {
                        viewModel.hideCreateFolderDialog()
                    }
                }
            )
        }
    }

    if (viewModel.isRenameDialogVisible && selectedFile != null) {
        Dialog(onDismissRequest = { viewModel.hideRenameDialog() }) {
            StringInputPrefDialogUI(
                title = if (activePaneState.isRecycleBin) "重命名并恢复" else "重命名",
                initialValue = selectedFile.name,
                onDismiss = { viewModel.hideRenameDialog() },
                onConfirm = { newName ->
                    if (newName.isNotBlank() && newName != selectedFile.name) {
                        viewModel.confirmRename(newName, activePaneIndex)
                    } else {
                        viewModel.hideRenameDialog()
                    }
                }
            )
        }
    }

    if (viewModel.isDeleteDialogVisible) {
        val isRecycle = activePaneState.isRecycleBin
        val isBatch = viewModel.isBatchActionMenu && activePaneState.selectedIds.size > 1

        Dialog(onDismissRequest = { viewModel.hideDeleteDialog() }) {
            ActionsDialogUI(
                titleText = if (isRecycle) "彻底删除" else "删除",
                messageText = when {
                    isBatch -> "确定要操作这 ${activePaneState.selectedIds.size} 个文件吗？"
                    isRecycle -> "文件将从回收站永久移除，不可恢复！"
                    else -> "是否将 ${selectedFile?.name} 移入回收站？"
                },
                primaryText = if (isRecycle) "永久删除" else "放入回收站",
                primaryIcon = if (isRecycle) Icons.Default.DeleteForever else Icons.Default.Delete,
                primaryAction = {
                    if (isRecycle) {
                        viewModel.confirmDeletePermanently(activePaneIndex)
                    } else {
                        viewModel.confirmDelete(activePaneIndex)
                    }
                },
                onDismiss = { viewModel.hideDeleteDialog() }
            )
        }
    }

    if (viewModel.isPropertyDialogVisible && selectedFile != null) {
        FilePropertyDialog(file = selectedFile, onDismiss = { viewModel.hidePropertyDialog() })
    }
}

@Composable
fun FilePane(
    state: ExplorerViewModel.PaneState,
    viewModel: ExplorerViewModel,
    paneIndex: PaneIndex,
    isActive: Boolean,
    onFileClick: (PanFile) -> Unit,
    onFileLongClick: (PanFile) -> Unit,
    onBreadcrumbClick: (PanPath) -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(listState, state.fileList) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= state.fileList.size - 2 && !state.isLoading && state.currentPage < state.totalPages) {
                    viewModel.loadFiles(pane = paneIndex, isNextPage = true)
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BreadcrumbsBar(pathStack = state.pathStack, onPathClick = onBreadcrumbClick)

        AnimatedVisibility(visible = state.isSelectionMode && state.selectedIds.size > 1) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("已选择 ${state.selectedIds.size} 项", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { state.clearSelection() }) {
                Text("取消")
            }
        }
    }
}

        if (state.isLoading && state.fileList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.error != null && state.fileList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = state.error ?: "未知错误", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = state.fileList,
                    key = { it.id }
                ) { file ->
                    val index = state.fileList.indexOf(file)
                    FileListItem(
                        file = file,
                        index = index,
                        isSelected = state.selectedIds.contains(file.id),
                        isHighlighted = viewModel.recentlyModifiedIds.contains(file.id),
                        onClick = {
                            if (state.isSelectionMode) {
                                viewModel.toggleSelection(paneIndex, index)
                            } else {
                                onFileClick(file)
                            }
                        },
                        onLongClick = { onFileLongClick(file) },
                        onSwipeToSelect = { idx ->
                            viewModel.toggleSelection(paneIndex, idx)
                        }
                    )
                }

                if (state.isLoading && state.fileList.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BreadcrumbsBar(pathStack: List<PanPath>, onPathClick: (PanPath) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
    index: Int,
    isSelected: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeToSelect: (Int) -> Unit
) {
    val isUpFolder = file.name == ".." && file.id == -1L
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        isHighlighted -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        if (kotlin.math.abs(dragAmount) > 15f && !isUpFolder) {
                            onSwipeToSelect(index)
                        }
                    }
                )
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (!isUpFolder) onLongClick else null
            ),
        headlineContent = {
            Text(
                text = file.name,
                maxLines = 1,
                color = if (isSelected || isHighlighted) MaterialTheme.colorScheme.primary else Color.Unspecified,
                fontWeight = if (isUpFolder || isSelected || isHighlighted) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 2000)
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
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (isSelected || isHighlighted) MaterialTheme.colorScheme.primary
                else if (file.isDirectory) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            )
        }
    )
}

@Composable
fun UploadProgressBanner(isUploading: Boolean, progress: Float, message: String) {
    AnimatedVisibility(visible = isUploading) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}